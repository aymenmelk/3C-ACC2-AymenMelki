// Projet DevOps — Aymen Melki — 3C-ACC2
// Maven -> Tests -> SonarQube -> Docker -> Kubernetes PODs (Spring, MySQL, Angular, Eureka, Monitoring Prometheus+Grafana)
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
        choice(name: 'DEPLOY_TARGET', choices: ['auto', 'kubernetes', 'docker-compose', 'none'],
               description: 'auto = Kubernetes (Docker Desktop) when reachable, otherwise docker compose')
    }

    environment {
        CI           = 'true'
        // Image naming rule of the brief: nomprenom-classe-nomprojet (Docker requires lowercase)
        IMAGE_PREFIX = 'aymenmelki-3c-acc2-gestionprojets'
        HUB_USER     = "${env.DOCKERHUB_USER ?: 'aymenbay'}"
        BACKEND_IMG  = "${HUB_USER}/${IMAGE_PREFIX}-backend"
        FRONTEND_IMG = "${HUB_USER}/${IMAGE_PREFIX}-frontend"
        DISCOVERY_IMG = "${HUB_USER}/${IMAGE_PREFIX}-discovery"
        IMAGE_TAG    = "${env.BUILD_NUMBER}"
        APP_URL      = "http://${env.APP_HOST ?: 'localhost'}"
        K8S_NS       = 'gestionprojets'
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

        stage('Microservice: discovery-server (Maven)') {
            steps {
                dir('discovery-server') {
                    sh 'chmod +x mvnw && ./mvnw -B clean install'
                }
                archiveArtifacts artifacts: 'discovery-server/target/*.jar', fingerprint: true
            }
            post {
                always {
                    junit allowEmptyResults: false, testResults: 'discovery-server/target/surefire-reports/*.xml'
                }
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
                    # Package the artifacts built and tested above (no re-download, takes seconds)
                    docker build -f backend/Dockerfile.jenkins  -t "$BACKEND_IMG:$IMAGE_TAG"  -t "$BACKEND_IMG:latest"  ./backend
                    docker build -f frontend/Dockerfile.jenkins -t "$FRONTEND_IMG:$IMAGE_TAG" -t "$FRONTEND_IMG:latest" ./frontend
                    docker build -f discovery-server/Dockerfile.jenkins -t "$DISCOVERY_IMG:$IMAGE_TAG" -t "$DISCOVERY_IMG:latest" ./discovery-server
                    docker images | grep "$IMAGE_PREFIX"
                '''
            }
        }

        stage('Docker: push to Docker Hub') {
            when { expression { params.PUSH_IMAGES != false } }
            steps {
                // A missing/invalid Docker Hub token marks the build UNSTABLE instead of failing it
                catchError(buildResult: 'UNSTABLE', stageResult: 'FAILURE') {
                    withCredentials([usernamePassword(credentialsId: 'dockerhub-creds',
                                                      usernameVariable: 'DH_USER', passwordVariable: 'DH_PASS')]) {
                        sh '''
                            echo "$DH_PASS" | docker login -u "$DH_USER" --password-stdin
                            for img in "$BACKEND_IMG" "$FRONTEND_IMG" "$DISCOVERY_IMG"; do
                                docker push "$img:$IMAGE_TAG"
                                docker push "$img:latest"
                            done
                        '''
                    }
                }
            }
            post { always { sh 'docker logout || true' } }
        }

        stage('Select deploy target') {
            when { expression { (params.DEPLOY_TARGET ?: 'auto') != 'none' } }
            steps {
                script {
                    def target = params.DEPLOY_TARGET ?: 'auto'
                    def k8sOk = sh(returnStatus: true, script: '''
                        sh k8s/jenkins-kubeconfig.sh "$WORKSPACE/.kubeconfig" &&
                        kubectl --kubeconfig "$WORKSPACE/.kubeconfig" --context "${KUBE_CONTEXT:-docker-desktop}" \
                                --request-timeout=10s get nodes
                    ''') == 0
                    if (target == 'auto') {
                        target = k8sOk ? 'kubernetes' : 'docker-compose'
                    } else if (target == 'kubernetes' && !k8sOk) {
                        error 'Kubernetes is not reachable: enable it in Docker Desktop (Settings > Kubernetes)'
                    }
                    env.DEPLOY_MODE = target
                    echo "Deploy target: ${env.DEPLOY_MODE}"
                }
            }
        }

        stage('Deploy: Kubernetes (PODs)') {
            when { expression { env.DEPLOY_MODE == 'kubernetes' } }
            steps {
                sh '''
                    export KUBECONFIG="$WORKSPACE/.kubeconfig"
                    K="kubectl --context ${KUBE_CONTEXT:-docker-desktop}"

                    # Free the ports/RAM of a previous docker compose delivery (its data volume is kept)
                    docker compose -f docker-compose.yml down || true

                    # Deploy exactly the images built (and pushed) by this build
                    sed -i "s#aymenbay/aymenmelki#${HUB_USER}/aymenmelki#g; s#newTag: latest#newTag: \\"${IMAGE_TAG}\\"#" k8s/kustomization.yaml
                    grep -A1 "name: .*gestionprojets" k8s/kustomization.yaml
                    $K apply -k k8s

                    $K -n "$K8S_NS" rollout status deployment/mysql      --timeout=300s
                    $K -n "$K8S_NS" rollout status deployment/discovery  --timeout=300s
                    $K -n "$K8S_NS" rollout status deployment/backend    --timeout=600s
                    $K -n "$K8S_NS" rollout status deployment/frontend   --timeout=300s
                    $K -n "$K8S_NS" rollout status deployment/monitoring --timeout=300s
                    $K -n "$K8S_NS" get pods,svc -o wide
                '''
                script {
                    env.BACKEND_URL   = "${env.APP_URL}:30080"
                    env.FRONTEND_URL  = "${env.APP_URL}:30420"
                    env.DISCOVERY_URL = "${env.APP_URL}:30761"
                }
            }
        }

        stage('Deploy: docker compose') {
            when { expression { env.DEPLOY_MODE == 'docker-compose' } }
            steps {
                sh '''
                    export DOCKERHUB_USER="$HUB_USER" IMAGE_TAG="$IMAGE_TAG"
                    docker compose -f docker-compose.yml up -d --no-build --force-recreate --remove-orphans
                    docker compose -f docker-compose.yml ps
                '''
                script {
                    env.BACKEND_URL   = "${env.APP_URL}:8080"
                    env.FRONTEND_URL  = "${env.APP_URL}:4200"
                    env.DISCOVERY_URL = "${env.APP_URL}:8761"
                }
            }
        }

        stage('Smoke test') {
            when { expression { ['kubernetes', 'docker-compose'].contains(env.DEPLOY_MODE) } }
            steps {
                sh '''
                    echo "Testing the $DEPLOY_MODE deployment: backend $BACKEND_URL, frontend $FRONTEND_URL"
                    for i in $(seq 1 40); do
                        if curl -fs "$BACKEND_URL/actuator/health" | grep -q '"status":"UP"'; then
                            echo "Backend UP"; break
                        fi
                        echo "waiting for backend ($i/40)..."; sleep 5
                    done
                    curl -fs "$BACKEND_URL/actuator/health"; echo
                    curl -fs "$BACKEND_URL/entreprise/all"; echo
                    curl -fs -o /dev/null "$FRONTEND_URL" && echo "Frontend UP"
                    curl -fs -o /dev/null "$FRONTEND_URL/api/entreprise/all" && echo "Frontend -> backend proxy OK"
                    curl -fs "$BACKEND_URL/actuator/prometheus" | grep -q http_server_requests && echo "Prometheus metrics OK"
                    # Microservices: the backend must be registered in Eureka (registration can take ~30 s)
                    for i in $(seq 1 24); do
                        if curl -fs -H 'Accept: application/json' "$DISCOVERY_URL/eureka/apps/BACKEND" | grep -q '"status":"UP"'; then
                            echo "Backend registered in Eureka (discovery-server)"; break
                        fi
                        echo "waiting for Eureka registration ($i/24)..."; sleep 5
                    done
                    curl -fs -H 'Accept: application/json' "$DISCOVERY_URL/eureka/apps/BACKEND" | grep -q '"status":"UP"'
                    if [ "$DEPLOY_MODE" = "kubernetes" ]; then
                        curl -fs "$APP_URL:30300/api/health" && echo " Grafana POD OK"
                        curl -fs "$APP_URL:30090/-/ready" && echo " Prometheus POD OK"
                    fi
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
