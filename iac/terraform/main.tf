# =============================================================================
# IaC (Terraform): the Docker containers of the DevOps toolchain.
# Same infrastructure as infra/docker-compose.yml, declared with the Docker provider.
#   cd iac/terraform && terraform init && terraform apply
# Stop the docker compose version first (same container names): cd infra && docker compose down
# =============================================================================

locals {
  infra     = abspath("${path.module}/../../infra")
  sonar_url = var.sonar_in_docker ? "http://sonarqube:9000" : var.sonar_url
  kube_dir  = pathexpand(var.kube_dir)
  sonar     = var.sonar_in_docker ? 1 : 0

  jenkins_files = fileset("${local.infra}/jenkins", "**")
  jenkins_hash  = sha1(join("", [for f in local.jenkins_files : filesha1("${local.infra}/jenkins/${f}")]))
}

# ---------------------------------------------------------------- network + volumes
resource "docker_network" "devops" {
  name = "devops-tf"
}

resource "docker_volume" "jenkins_home" { name = "devops-tf_jenkins_home" }
resource "docker_volume" "jenkins_secrets" { name = "devops-tf_jenkins_secrets" }
resource "docker_volume" "prometheus_data" { name = "devops-tf_prometheus_data" }
resource "docker_volume" "grafana_data" { name = "devops-tf_grafana_data" }

resource "docker_volume" "sonar_db" {
  count = local.sonar
  name  = "devops-tf_sonar_db"
}

resource "docker_volume" "sonar_data" {
  count = local.sonar
  name  = "devops-tf_sonar_data"
}

# ---------------------------------------------------------------- images
resource "docker_image" "jenkins" {
  name = "aymenmelki-3c-acc2-jenkins:latest"
  build {
    context = "${local.infra}/jenkins"
  }
  triggers     = { dir_sha1 = local.jenkins_hash }
  keep_locally = true
}

resource "docker_image" "alpine" {
  name         = "alpine:3.22"
  keep_locally = true
}

resource "docker_image" "prometheus" {
  name         = "prom/prometheus:v3.5.0"
  keep_locally = true
}

resource "docker_image" "grafana" {
  name         = "grafana/grafana:12.1.0"
  keep_locally = true
}

resource "docker_image" "postgres" {
  count        = local.sonar
  name         = "postgres:16-alpine"
  keep_locally = true
}

resource "docker_image" "sonarqube" {
  count        = local.sonar
  name         = "sonarqube:community"
  keep_locally = true
}

# ---------------------------------------------------------------- SonarQube (optional, else Kubernetes POD)
resource "docker_container" "sonar_db" {
  count   = local.sonar
  name    = "sonar-db"
  image   = docker_image.postgres[0].image_id
  restart = "unless-stopped"
  env     = ["POSTGRES_USER=sonar", "POSTGRES_PASSWORD=sonar", "POSTGRES_DB=sonar"]

  volumes {
    volume_name    = docker_volume.sonar_db[0].name
    container_path = "/var/lib/postgresql/data"
  }
  networks_advanced {
    name    = docker_network.devops.id
    aliases = ["sonar-db"]
  }
  healthcheck {
    test     = ["CMD-SHELL", "pg_isready -U sonar -d sonar"]
    interval = "5s"
    timeout  = "5s"
    retries  = 20
  }
  wait = true
}

resource "docker_container" "sonarqube" {
  count   = local.sonar
  name    = "sonarqube"
  image   = docker_image.sonarqube[0].image_id
  restart = "unless-stopped"
  env = [
    "SONAR_JDBC_URL=jdbc:postgresql://sonar-db:5432/sonar",
    "SONAR_JDBC_USERNAME=sonar",
    "SONAR_JDBC_PASSWORD=sonar",
    "SONAR_SEARCH_JAVAADDITIONALOPTS=-Dnode.store.allow_mmap=false",
  ]
  ports {
    internal = 9000
    external = 9000
  }
  volumes {
    volume_name    = docker_volume.sonar_data[0].name
    container_path = "/opt/sonarqube/data"
  }
  networks_advanced {
    name    = docker_network.devops.id
    aliases = ["sonarqube"]
  }
  depends_on = [docker_container.sonar_db]
}

# ---------------------------------------------------------------- sonar-init (one-shot bootstrap)
resource "docker_container" "sonar_init" {
  name       = "sonar-init"
  image      = docker_image.alpine.image_id
  entrypoint = ["sh", "/sonar-init.sh"]
  env = [
    "SONAR_URL=${local.sonar_url}",
    "SONAR_ADMIN_PASSWORD=${var.sonar_admin_password}",
  ]
  upload {
    file    = "/sonar-init.sh"
    content = file("${local.infra}/sonar/sonar-init.sh")
  }
  volumes {
    volume_name    = docker_volume.jenkins_secrets.name
    container_path = "/var/jenkins_secrets"
  }
  host {
    host = "host.docker.internal"
    ip   = "host-gateway"
  }
  networks_advanced {
    name = docker_network.devops.id
  }

  # Run once and wait for the end of the bootstrap
  must_run = false
  attach   = true
  logs     = true

  lifecycle {
    postcondition {
      condition     = self.exit_code == 0
      error_message = "sonar-init failed: is SonarQube reachable at ${local.sonar_url}? (kubectl apply -k k8s/sonarqube)"
    }
  }
  depends_on = [docker_container.sonarqube]
}

# ---------------------------------------------------------------- Jenkins
resource "docker_container" "jenkins" {
  name    = "jenkins"
  image   = docker_image.jenkins.image_id
  restart = "unless-stopped"
  user    = "root"
  env = [
    "JENKINS_ADMIN_ID=${var.jenkins_admin_id}",
    "JENKINS_ADMIN_PASSWORD=${var.jenkins_admin_password}",
    "JENKINS_URL=${var.jenkins_url}",
    "GIT_REPO_URL=${var.git_repo_url}",
    "DOCKERHUB_USER=${var.dockerhub_user}",
    "DOCKERHUB_TOKEN=${var.dockerhub_token}",
    "SONAR_URL=${local.sonar_url}",
    "KUBECONFIG_SRC=/root/.kube-host/config",
    "KUBE_CONTEXT=${var.kube_context}",
    "NOTIFY_EMAIL=${var.notify_email}",
    "SMTP_HOST=${var.smtp.host}",
    "SMTP_PORT=${var.smtp.port}",
    "SMTP_USER=${var.smtp.user}",
    "SMTP_PASSWORD=${var.smtp.password}",
    "SMTP_FROM=${var.smtp.from}",
  ]
  ports {
    internal = 8080
    external = 8081
  }
  ports {
    internal = 50000
    external = 50000
  }
  volumes {
    volume_name    = docker_volume.jenkins_home.name
    container_path = "/var/jenkins_home"
  }
  volumes {
    volume_name    = docker_volume.jenkins_secrets.name
    container_path = "/var/jenkins_secrets"
    read_only      = true
  }
  volumes {
    host_path      = "/var/run/docker.sock"
    container_path = "/var/run/docker.sock"
  }
  volumes {
    host_path      = local.kube_dir
    container_path = "/root/.kube-host"
    read_only      = true
  }
  host {
    host = "host.docker.internal"
    ip   = "host-gateway"
  }
  host {
    host = "kubernetes.docker.internal"
    ip   = "host-gateway"
  }
  networks_advanced {
    name    = docker_network.devops.id
    aliases = ["jenkins"]
  }
  depends_on = [docker_container.sonar_init]
}

# ---------------------------------------------------------------- Prometheus + Grafana
resource "docker_container" "prometheus" {
  name    = "prometheus"
  image   = docker_image.prometheus.image_id
  restart = "unless-stopped"
  command = ["--config.file=/etc/prometheus/prometheus.yml", "--storage.tsdb.retention.time=15d"]
  upload {
    file    = "/etc/prometheus/prometheus.yml"
    content = file("${local.infra}/prometheus/prometheus.yml")
  }
  ports {
    internal = 9090
    external = 9090
  }
  volumes {
    volume_name    = docker_volume.prometheus_data.name
    container_path = "/prometheus"
  }
  host {
    host = "host.docker.internal"
    ip   = "host-gateway"
  }
  networks_advanced {
    name    = docker_network.devops.id
    aliases = ["prometheus"]
  }
}

resource "docker_container" "grafana" {
  name    = "grafana"
  image   = docker_image.grafana.image_id
  restart = "unless-stopped"
  env = [
    "GF_SECURITY_ADMIN_USER=admin",
    "GF_SECURITY_ADMIN_PASSWORD=${var.grafana_admin_password}",
    "GF_USERS_ALLOW_SIGN_UP=false",
  ]
  upload {
    file    = "/etc/grafana/provisioning/datasources/prometheus.yml"
    content = file("${local.infra}/grafana/provisioning/datasources/prometheus.yml")
  }
  upload {
    file    = "/etc/grafana/provisioning/dashboards/dashboards.yml"
    content = replace(file("${local.infra}/grafana/provisioning/dashboards/dashboards.yml"), "/var/lib/grafana/dashboards", "/etc/grafana/dashboards")
  }
  upload {
    file    = "/etc/grafana/dashboards/gestionprojets-devops.json"
    content = file("${local.infra}/grafana/dashboards/gestionprojets-devops.json")
  }
  ports {
    internal = 3000
    external = 3000
  }
  volumes {
    volume_name    = docker_volume.grafana_data.name
    container_path = "/var/lib/grafana"
  }
  networks_advanced {
    name    = docker_network.devops.id
    aliases = ["grafana"]
  }
  depends_on = [docker_container.prometheus]
}
