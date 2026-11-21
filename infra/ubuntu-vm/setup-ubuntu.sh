#!/usr/bin/env bash
# =============================================================================
# Projet DevOps — Aymen Melki — 3C-ACC2
# One-shot setup of the VMware Ubuntu VM shown in the architecture of the brief:
#   Ubuntu VM  ->  Git + Maven + Jenkins (+ SonarQube, Prometheus, Grafana)
#              ->  Docker + Kubernetes (k3s) hosting the PODs Spring / MySQL / Angular / Monitoring
#
# Usage (inside the Ubuntu VM, as your normal user, NOT root):
#   curl -fsSL https://raw.githubusercontent.com/aymenmelk/3C-ACC2-AymenMelki/main/infra/ubuntu-vm/setup-ubuntu.sh -o setup-ubuntu.sh
#   bash setup-ubuntu.sh
# Re-running it is safe.
# =============================================================================
set -euo pipefail

REPO_URL="${REPO_URL:-https://github.com/aymenmelk/3C-ACC2-AymenMelki.git}"
REPO_DIR="${REPO_DIR:-$HOME/3C-ACC2-AymenMelki}"

if [ "$(id -u)" -eq 0 ]; then
  echo "Run this script as your normal user (it uses sudo when needed)." >&2
  exit 1
fi

step() { printf '\n\033[1;34m==> %s\033[0m\n' "$*"; }

step "1/7 System packages: Git, Java (17, or 21 on newer Ubuntu), Maven, curl"
sudo apt-get update -y
sudo apt-get install -y git curl ca-certificates
if apt-cache show openjdk-17-jdk >/dev/null 2>&1; then JDK=openjdk-17-jdk; else JDK=openjdk-21-jdk; fi
sudo apt-get install -y "$JDK" maven
java -version && mvn -version

step "2/7 Docker Engine + compose plugin"
if ! command -v docker >/dev/null 2>&1; then
  curl -fsSL https://get.docker.com | sudo sh
fi
sudo usermod -aG docker "$USER"
sudo systemctl enable --now docker

step "3/7 Kubernetes (k3s, using Docker as runtime so Jenkins-built images are visible to the PODs)"
if ! command -v k3s >/dev/null 2>&1; then
  curl -sfL https://get.k3s.io | sudo INSTALL_K3S_EXEC="--docker --tls-san kubernetes.docker.internal --write-kubeconfig-mode 644" sh -
fi
mkdir -p "$HOME/.kube"
cp /etc/rancher/k3s/k3s.yaml "$HOME/.kube/config"
chmod 600 "$HOME/.kube/config"
until sudo k3s kubectl get nodes 2>/dev/null | grep -q ' Ready'; do echo "waiting for Kubernetes..."; sleep 5; done
sudo k3s kubectl get nodes

step "4/7 SonarQube kernel settings"
echo 'vm.max_map_count=524288' | sudo tee /etc/sysctl.d/99-sonarqube.conf >/dev/null
sudo sysctl --system >/dev/null

step "5/7 Project source code"
if [ -d "$REPO_DIR/.git" ]; then
  git -C "$REPO_DIR" pull --ff-only
else
  git clone "$REPO_URL" "$REPO_DIR"
fi

step "6/7 Maven on the command line (steps 3 of the brief: clean, install, package)"
(cd "$REPO_DIR/backend" && mvn -B clean install)

step "7/7 DevOps toolchain: Jenkins, SonarQube, Prometheus, Grafana"
cd "$REPO_DIR/infra"
if [ ! -f .env ]; then
  cp .env.example .env
fi
# k3s names its kubeconfig context "default" (Docker Desktop uses "docker-desktop")
grep -q '^KUBE_CONTEXT=' .env || echo 'KUBE_CONTEXT=default' >> .env
# "sg docker" = docker group without re-login, and keeps $HOME so ~/.kube is mounted into Jenkins
sg docker -c "docker compose up -d --build"

VM_IP="$(hostname -I | awk '{print $1}')"
cat <<MSG

==============================================================================
 Done. From Windows (or inside the VM), open:
   Jenkins     http://$VM_IP:8081   (admin / admin)  -> gestionprojets-pipeline -> Build Now
   SonarQube   http://$VM_IP:9000   (admin / Admin_DevOps_2026!)
   Grafana     http://$VM_IP:3000   (admin / admin)

 After the first pipeline run, the Kubernetes PODs:
   Angular     http://$VM_IP:30420
   Spring      http://$VM_IP:30080/entreprise/all
   Grafana POD http://$VM_IP:30300   Prometheus POD http://$VM_IP:30090
   kubectl -n gestionprojets get pods,svc

 Put your Docker Hub token in $REPO_DIR/infra/.env (DOCKERHUB_TOKEN=...)
 then: cd $REPO_DIR/infra && sg docker -c "docker compose up -d jenkins"
 Log out and back in once so you can use "docker" without sudo.
==============================================================================
MSG
