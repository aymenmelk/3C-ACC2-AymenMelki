variable "docker_host" {
  description = "Docker daemon to manage"
  type        = string
  default     = "unix:///var/run/docker.sock"
}

variable "jenkins_admin_id" {
  type    = string
  default = "admin"
}

variable "jenkins_admin_password" {
  type      = string
  default   = "admin"
  sensitive = true
}

variable "jenkins_url" {
  description = "Public URL of Jenkins (ngrok URL when the GitHub webhook is used)"
  type        = string
  default     = "http://localhost:8081/"
}

variable "git_repo_url" {
  type    = string
  default = "https://github.com/aymenmelk/3C-ACC2-AymenMelki.git"
}

variable "dockerhub_user" {
  type    = string
  default = "aymenbay"
}

variable "dockerhub_token" {
  type      = string
  default   = "changeme"
  sensitive = true
}

variable "sonar_in_docker" {
  description = "true = SonarQube as Docker containers; false = SonarQube POD in Kubernetes (k8s/sonarqube)"
  type        = bool
  default     = false
}

variable "sonar_url" {
  description = "SonarQube URL seen from the containers when sonar_in_docker = false (POD NodePort)"
  type        = string
  default     = "http://host.docker.internal:30900"
}

variable "sonar_admin_password" {
  type      = string
  default   = "Admin_DevOps_2026!"
  sensitive = true
}

variable "kube_dir" {
  description = "Directory holding the kubeconfig given (read-only) to Jenkins"
  type        = string
  default     = "~/.kube"
}

variable "kube_context" {
  description = "docker-desktop (Docker Desktop) or default (k3s)"
  type        = string
  default     = "default"
}

variable "grafana_admin_password" {
  type      = string
  default   = "admin"
  sensitive = true
}

variable "notify_email" {
  type    = string
  default = ""
}

variable "smtp" {
  description = "Gmail SMTP settings for Jenkins e-mails"
  type = object({
    host     = string
    port     = string
    user     = string
    password = string
    from     = string
  })
  default = {
    host     = "smtp.gmail.com"
    port     = "465"
    user     = ""
    password = ""
    from     = ""
  }
  sensitive = true
}
