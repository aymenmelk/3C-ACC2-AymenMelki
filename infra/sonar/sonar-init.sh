#!/bin/sh
# One-shot SonarQube bootstrap (runs in the "sonar-init" container), idempotent:
#  1. waits for SonarQube (Kubernetes POD by default, or the Docker container),
#  2. changes the default admin/admin password,
#  3. creates (or re-creates if invalid) the analysis token used by Jenkins,
#  4. allows + (re)creates the Quality Gate webhook SonarQube -> Jenkins.
set -eu
SONAR="${SONAR_URL:-http://host.docker.internal:30900}"
NEW_PASS="${SONAR_ADMIN_PASSWORD:-Admin_DevOps_2026!}"
TOKEN_FILE=/var/jenkins_secrets/sonar-token

apk add --no-cache curl jq >/dev/null

echo "[sonar-init] waiting for SonarQube at $SONAR ..."
i=0
until [ "$(curl -s "$SONAR/api/system/status" | jq -r .status 2>/dev/null)" = "UP" ]; do
  i=$((i+1))
  if [ "$i" -gt 180 ]; then
    echo "[sonar-init] ERROR: SonarQube not reachable at $SONAR after 15 min."
    echo "[sonar-init] Deploy the SonarQube POD first: kubectl apply -k k8s/sonarqube"
    exit 1
  fi
  sleep 5
done
echo "[sonar-init] SonarQube is UP"

if curl -sf -u admin:admin "$SONAR/api/authentication/validate" | jq -e '.valid == true' >/dev/null; then
  echo "[sonar-init] changing default admin password"
  curl -sf -u admin:admin -X POST "$SONAR/api/users/change_password" \
    --data-urlencode "login=admin" --data-urlencode "previousPassword=admin" --data-urlencode "password=$NEW_PASS"
fi
AUTH="admin:$NEW_PASS"
curl -sf -u "$AUTH" "$SONAR/api/authentication/validate" | jq -e '.valid == true' >/dev/null \
  || { echo "[sonar-init] ERROR: cannot log in as admin (check SONAR_ADMIN_PASSWORD)"; exit 1; }

# Keep the existing token only if this SonarQube still accepts it (e.g. not after a SonarQube reinstall)
TOKEN_OK=false
if [ -s "$TOKEN_FILE" ]; then
  if curl -sf -u "$(cat "$TOKEN_FILE"):" "$SONAR/api/authentication/validate" | jq -e '.valid == true' >/dev/null; then
    TOKEN_OK=true
  fi
fi
if [ "$TOKEN_OK" = true ]; then
  echo "[sonar-init] existing token is valid"
else
  curl -s -u "$AUTH" -X POST "$SONAR/api/user_tokens/revoke" --data-urlencode "name=jenkins" >/dev/null || true
  TOKEN=$(curl -sf -u "$AUTH" -X POST "$SONAR/api/user_tokens/generate" --data-urlencode "name=jenkins" | jq -r .token)
  [ -n "$TOKEN" ] && [ "$TOKEN" != "null" ] || { echo "[sonar-init] ERROR: token generation failed"; exit 1; }
  printf '%s' "$TOKEN" > "$TOKEN_FILE"
  echo "[sonar-init] new token written for Jenkins"
fi

# Where SonarQube must send the Quality Gate result:
#  - SonarQube in Docker (same network as Jenkins): http://jenkins:8080
#  - SonarQube POD: the Docker host, where Jenkins publishes port 8081
WEBHOOK_URL="${JENKINS_WEBHOOK_URL:-}"
if [ -z "$WEBHOOK_URL" ]; then
  case "$SONAR" in
    *://sonarqube:9000*) WEBHOOK_URL="http://jenkins:8080/sonarqube-webhook/" ;;
    *)
      HOST_IP=$(awk '/host.docker.internal/ {print $1; exit}' /etc/hosts)
      WEBHOOK_URL="http://${HOST_IP:-jenkins}:8081/sonarqube-webhook/" ;;
  esac
fi

# Jenkins lives on a private network; recent SonarQube versions block such webhooks by default (anti-SSRF)
curl -sf -u "$AUTH" -X POST "$SONAR/api/settings/set" \
  --data-urlencode "key=sonar.validateWebhooks" --data-urlencode "value=false" \
  && echo "[sonar-init] local webhooks allowed (sonar.validateWebhooks=false)"

for KEY in $(curl -sf -u "$AUTH" "$SONAR/api/webhooks/list" | jq -r '.webhooks[] | select(.name=="jenkins") | .key'); do
  curl -sf -u "$AUTH" -X POST "$SONAR/api/webhooks/delete" --data-urlencode "webhook=$KEY" >/dev/null
done
curl -sf -u "$AUTH" -X POST "$SONAR/api/webhooks/create" \
  --data-urlencode "name=jenkins" --data-urlencode "url=$WEBHOOK_URL" >/dev/null
echo "[sonar-init] webhook -> $WEBHOOK_URL"
echo "[sonar-init] done"
