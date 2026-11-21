// Projet DevOps — Aymen Melki — 3C-ACC2
// Maven -> Tests -> SonarQube -> Docker (Spring + MySQL + Angular) -> Deploy -> Monitoring (Prometheus/Grafana)
pipeline {
    agent any

    options {
        timestamps()
        disableConcurrentBuilds()
        buildDiscarder(logRotator(numToKeepStr: '10'))
        timeout(time: 60, unit: 'MINUTES')
    }

    triggers {
        // GitHub webhook (needs ngrok when Jenkins runs locally) + polling fallback
        githubPush()
        pollSCM('H/5 * * * *')
    }

    parameters {
        booleanParam(name: 'PUSH_IMAGES', defaultValue: true, description: 'Push the images to Docker Hub')
        booleanParam(name: 'DEPLOY', defaultValue: true, description: 'Deploy with docker compose after the build')
    }

    environment {
        CI           = 'true'
        // Image naming rule of the brief: nomprenom-classe-nomprojet (Docker requires lowercase)
        IMAGE_PREFIX = 'aymenmelki-3c-acc2-gestionprojets'
        HUB_USER     = "${env.DOCKERHUB_USER ?: 'aymenmelk'}"
        BACKEND_IMG  = "${HUB_USER}/${IMAGE_PREFIX}-backend"
        FRONTEND_IMG = "${HUB_USER}/${IMAGE_PREFIX}-frontend"
        IMAGE_TAG    = "${env.BUILD_NUMBER}"
        APP_URL      = "http://${env.APP_HOST ?: 'localhost'}"
    }

    stages {
        stage('Checkout') {
            steps {
                checkout scm
                sh 'git log -1 --oneline'
            }
        }

        stage('Check Tools') {
            steps {
                sh '''
                    java -version
                    chmod +x backend/mvnw
                    ./backend/mvnw -v
                    node -v && npm -v
                    docker version --format 'Docker {{.Server.Version}}'
                '''
            }
        }

        stage('Maven: clean') {
            steps { dir('backend') { sh './mvnw -B clean' } }
        }

        stage('Maven: compile') {
            steps { dir('backend') { sh './mvnw -B compile' } }
        }

        stage('Maven: test') {
            steps { dir('backend') { sh './mvnw -B test' } }
            post {
                always {
                    junit allowEmptyResults: false, testResults: 'backend/target/surefire-reports/*.xml'
                }
            }
        }

        stage('Maven: package') {
            steps { dir('backend') { sh './mvnw -B package -DskipTests' } }
        }

        stage('Maven: install') {
            steps {
                dir('backend') { sh './mvnw -B install -DskipTests' }
                archiveArtifacts artifacts: 'backend/target/*.jar', fingerprint: true
            }
        }

        stage('Frontend: install, test, build') {
            steps {
                dir('frontend') {
                    sh 'npm ci --no-audit --no-fund'
                    sh 'npm run test:ci'
                    sh 'npm run build'
                }
            }
        }

        stage('SonarQube Analysis') {
            steps {
                withSonarQubeEnv('SonarQube') {
                    // @sonar/scan reads sonar-project.properties (Java binaries + JaCoCo + lcov coverage)
                    sh 'npx --yes @sonar/scan -Dsonar.host.url=$SONAR_HOST_URL -Dsonar.token=$SONAR_AUTH_TOKEN'
                }
            }
        }

        stage('Quality Gate') {
            steps {
                timeout(time: 5, unit: 'MINUTES') {
                    waitForQualityGate abortPipeline: true
                }
            }
        }

        stage('Docker: build images') {
            steps {
                sh '''
                    docker build -t "$BACKEND_IMG:$IMAGE_TAG"  -t "$BACKEND_IMG:latest"  ./backend
                    docker build -t "$FRONTEND_IMG:$IMAGE_TAG" -t "$FRONTEND_IMG:latest" ./frontend
                    docker images | grep "$IMAGE_PREFIX"
                '''
            }
        }

        stage('Docker: push to Docker Hub') {
            when { expression { params.PUSH_IMAGES } }
            steps {
                // A missing/invalid Docker Hub token marks the build UNSTABLE instead of failing it
                catchError(buildResult: 'UNSTABLE', stageResult: 'FAILURE') {
                    withCredentials([usernamePassword(credentialsId: 'dockerhub-creds',
                                                      usernameVariable: 'DH_USER', passwordVariable: 'DH_PASS')]) {
                        sh '''
                            echo "$DH_PASS" | docker login -u "$DH_USER" --password-stdin
                            for img in "$BACKEND_IMG" "$FRONTEND_IMG"; do
                                docker push "$img:$IMAGE_TAG"
                                docker push "$img:latest"
                            done
                        '''
                    }
                }
            }
            post { always { sh 'docker logout || true' } }
        }

        stage('Deploy (docker compose: Spring + MySQL + Angular)') {
            when { expression { params.DEPLOY } }
            steps {
                sh '''
                    export DOCKERHUB_USER="$HUB_USER" IMAGE_TAG="$IMAGE_TAG"
                    docker compose -f docker-compose.yml up -d --no-build --force-recreate --remove-orphans
                    docker compose -f docker-compose.yml ps
                '''
            }
        }

        stage('Smoke test') {
            when { expression { params.DEPLOY } }
            steps {
                sh '''
                    for i in $(seq 1 40); do
                        if curl -fs "$APP_URL:8080/actuator/health" | grep -q '"status":"UP"'; then
                            echo "Backend UP"; break
                        fi
                        echo "waiting for backend ($i/40)..."; sleep 5
                    done
                    curl -fs "$APP_URL:8080/actuator/health"
                    curl -fs "$APP_URL:8080/entreprise/all"
                    curl -fs -o /dev/null "$APP_URL:4200" && echo "Frontend UP"
                    curl -fs "$APP_URL:8080/actuator/prometheus" | grep -q http_server_requests && echo "Prometheus metrics OK"
                '''
            }
        }
    }

    post {
        always {
            sh 'docker image prune -f || true'
        }
        success {
            script { notify('SUCCESS') }
        }
        unstable {
            script { notify('UNSTABLE') }
        }
        failure {
            script { notify('FAILURE') }
        }
    }
}

// E-mail notification (configured through SMTP_* in infra/.env); never fails the build
def notify(String status) {
    if (!env.NOTIFY_EMAIL?.trim()) {
        echo "NOTIFY_EMAIL not set, skipping e-mail (${status})"
        return
    }
    try {
        mail to: env.NOTIFY_EMAIL,
             subject: "[Jenkins] ${env.JOB_NAME} #${env.BUILD_NUMBER} : ${status}",
             body: "Build ${status}\n\nJob: ${env.JOB_NAME}\nBuild: ${env.BUILD_URL}\nImages: ${env.BACKEND_IMG}:${env.IMAGE_TAG}, ${env.FRONTEND_IMG}:${env.IMAGE_TAG}\n"
    } catch (err) {
        echo "E-mail notification failed: ${err}"
    }
}
