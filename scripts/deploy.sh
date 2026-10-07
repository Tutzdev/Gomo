#!/usr/bin/env bash
# Deploy do Gomo na VPS — Spring Boot (Maven) + frontend Vite.
# Instalado em /opt/gomo/bin/deploy.sh (dono root) e executado via forced command do SSH.
# Manual:  sudo -u deploy /opt/gomo/bin/deploy.sh <sha>
set -Eeuo pipefail

APP_DIR="/var/www/gomo"
JAR_DIR="/opt/gomo"
BRANCH="main"
SERVICE="gomo"
PORT=8087
HEALTH="http://127.0.0.1:$PORT/actuator/health"
export PATH="/usr/local/bin:/usr/bin:/bin"
export MAVEN_OPTS="-Xmx1g"

log() { printf '[%(%F %T)T] %s\n' -1 "$*"; }
die() { log "ERRO: $*" >&2; exit 1; }

# Aceita apenas "deploy <sha de 40 hex>"
REQ="${SSH_ORIGINAL_COMMAND:-deploy ${1:-}}"
read -r CMD SHA EXTRA <<<"$REQ" || true
[[ "${CMD:-}" == "deploy" && "${SHA:-}" =~ ^[0-9a-f]{40}$ && -z "${EXTRA:-}" ]] || die "comando rejeitado"

exec 9>"$JAR_DIR/.deploy.lock"
flock -n 9 || die "outro deploy em andamento"

cd "$APP_DIR"
PREV_SHA="$(git rev-parse HEAD)"
git fetch --quiet --prune origin "$BRANCH"
git merge-base --is-ancestor "$SHA" "origin/$BRANCH" || die "$SHA não pertence a origin/$BRANCH"

# Guarda o jar atual para rollback
[[ -f "$JAR_DIR/gomo.jar" ]] && cp -f "$JAR_DIR/gomo.jar" "$JAR_DIR/gomo.jar.prev"

health_ok() {
  for _ in {1..45}; do
    curl -fsS -o /dev/null --max-time 3 "$HEALTH" && return 0
    sleep 2
  done
  return 1
}

rollback() {
  local rc=${1:-$?}
  trap - ERR; set +e
  log "falha (exit $rc) — revertendo para ${PREV_SHA:0:7}"
  git -C "$APP_DIR" reset --quiet --hard "$PREV_SHA"
  [[ -f "$JAR_DIR/gomo.jar.prev" ]] && cp -f "$JAR_DIR/gomo.jar.prev" "$JAR_DIR/gomo.jar"
  sudo -n /usr/bin/systemctl restart "$SERVICE"
  log "revertido. Migrations do Flyway NÃO são desfeitas."
  exit "$rc"
}
trap rollback ERR

log "deploy ${PREV_SHA:0:7} → ${SHA:0:7}"
git reset --quiet --hard "$SHA"

log "backend: build Maven"
./mvnw -q -B -ntp -DskipTests clean package
NEW_JAR="$(ls -1t target/*.jar | grep -v -- '-plain\.jar$' | head -1)"
[[ -n "$NEW_JAR" ]] || die "jar não gerado"

log "frontend: build Vite"
cd "$APP_DIR/frontend"
if [[ -f package-lock.json ]]; then npm ci --no-audit --no-fund --silent; else npm install --no-audit --no-fund --silent; fi
# A API é servida no mesmo domínio pelo Nginx (location /api/ → 127.0.0.1:$PORT).
VITE_API_BASE_URL="${VITE_API_BASE_URL:-/api/v1}" npm run build --silent
[[ -f dist/index.html ]] || die "build do frontend não gerou dist/"

# As migrations não são desfeitas no rollback: com o backup instalado, nenhuma versão sobe sem uma cópia do banco.
if [[ -x "$JAR_DIR/bin/backup.sh" ]]; then
  "$JAR_DIR/bin/backup.sh" || die "backup do banco falhou; deploy interrompido antes das migrations"
else
  log "AVISO: $JAR_DIR/bin/backup.sh não instalado; publicando sem backup do banco"
fi

log "publicando jar e reiniciando (Flyway roda as migrations no boot)"
install -m 644 "$APP_DIR/$NEW_JAR" "$JAR_DIR/gomo.jar"
sudo -n /usr/bin/systemctl restart "$SERVICE"

health_ok || { log "health check falhou"; rollback 1; }
rm -f "$JAR_DIR/gomo.jar.prev"
log "deploy concluído: ${SHA:0:7}"
