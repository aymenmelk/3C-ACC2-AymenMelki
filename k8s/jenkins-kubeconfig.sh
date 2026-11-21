#!/bin/sh
# Makes the Docker Desktop kubeconfig usable from inside the Jenkins container:
# the API server address 127.0.0.1/localhost is the container itself there, so it is
# rewritten to kubernetes.docker.internal (mapped to the host by docker compose).
set -e
SRC="${KUBECONFIG_SRC:-/root/.kube-host/config}"
OUT="${1:-$WORKSPACE/.kubeconfig}"
if [ ! -s "$SRC" ]; then
  echo "No kubeconfig at $SRC: is Kubernetes enabled in Docker Desktop?" >&2
  exit 1
fi
sed -e 's#https://127.0.0.1:#https://kubernetes.docker.internal:#' \
    -e 's#https://localhost:#https://kubernetes.docker.internal:#' "$SRC" > "$OUT"
