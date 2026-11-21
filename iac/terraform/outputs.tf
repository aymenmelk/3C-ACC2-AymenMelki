output "jenkins" {
  value = "http://localhost:8081 (job gestionprojets-pipeline)"
}

output "sonarqube" {
  value = var.sonar_in_docker ? "http://localhost:9000" : "SonarQube POD: http://localhost:30900"
}

output "prometheus" {
  value = "http://localhost:9090"
}

output "grafana" {
  value = "http://localhost:3000 (dashboard DevOps)"
}
