#!/bin/sh
# One-shot SonarQube bootstrap (runs in the "sonar-init" container):
#  1. waits for SonarQube, 2. changes the default admin/admin password,
#  3. generates an analysis token for Jenkins, 4. allows + creates the Quality Gate webhook -> Jenkins.
# Idempotent: safe to run again.
set -eu
SONAR=http://sonarqube:9000
NEW_PASS="${SONAR_ADMIN_PASSWORD:-Admin_DevOps_2026!}"
TOKEN_FILE=/var/jenkins_secrets/sonar-token

apk add --no-cache curl jq >/dev/null

echo "[sonar-init] waiting for SonarQube..."
until [ "$(curl -s "$SONAR/api/system/status" | jq -r .status 2>/dev/null)" = "UP" ]; do sleep 5; done
echo "[sonar-init] SonarQube is UP"

if curl -sf -u admin:admin "$SONAR/api/authentication/validate" | jq -e '.valid == true' >/dev/null; then
  echo "[sonar-init] changing default admin password"
  curl -sf -u admin:admin -X POST "$SONAR/api/users/change_password" \
    --data-urlencode "login=admin" --data-urlencode "previousPassword=admin" --data-urlencode "password=$NEW_PASS"
fi
AUTH="admin:$NEW_PASS"
curl -sf -u "$AUTH" "$SONAR/api/authentication/validate" | jq -e '.valid == true' >/dev/null \
  || { echo "[sonar-init] ERROR: cannot log in as admin (check SONAR_ADMIN_PASSWORD)"; exit 1; }

if [ ! -s "$TOKEN_FILE" ] || [ "$(cat "$TOKEN_FILE")" = "pending" ]; then
  curl -s -u "$AUTH" -X POST "$SONAR/api/user_tokens/revoke" --data-urlencode "name=jenkins" >/dev/null || true
  TOKEN=$(curl -sf -u "$AUTH" -X POST "$SONAR/api/user_tokens/generate" --data-urlencode "name=jenkins" | jq -r .token)
  [ -n "$TOKEN" ] && [ "$TOKEN" != "null" ] || { echo "[sonar-init] ERROR: token generation failed"; exit 1; }
  printf '%s' "$TOKEN" > "$TOKEN_FILE"
  echo "[sonar-init] token written for Jenkins"
else
  echo "[sonar-init] token already present"
fi

# Jenkins lives on the private Docker network (http://jenkins:8080). Recent SonarQube versions block
# webhooks to private addresses by default (anti-SSRF); allow them for this local lab setup.
curl -sf -u "$AUTH" -X POST "$SONAR/api/settings/set" \
  --data-urlencode "key=sonar.validateWebhooks" --data-urlencode "value=false" \
  && echo "[sonar-init] local webhooks allowed (sonar.validateWebhooks=false)"

if ! curl -sf -u "$AUTH" "$SONAR/api/webhooks/list" | jq -e '.webhooks[] | select(.name=="jenkins")' >/dev/null; then
  curl -sf -u "$AUTH" -X POST "$SONAR/api/webhooks/create" \
    --data-urlencode "name=jenkins" --data-urlencode "url=http://jenkins:8080/sonarqube-webhook/" >/dev/null
  echo "[sonar-init] webhook created -> http://jenkins:8080/sonarqube-webhook/"
fi
echo "[sonar-init] done"
