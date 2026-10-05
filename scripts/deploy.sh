#!/usr/bin/env bash
# Deploy do Gomo — executado na VPS via forced command do SSH.
# Instalado pelo bootstrap em /srv/gomo/bin/deploy.sh (dono root) com os caminhos já ajustados.
# Execução manual na VPS:  sudo -u deploy /srv/gomo/bin/deploy.sh <sha>
set -Eeuo pipefail

# ─── Configuração (o bootstrap reescreve estas linhas) ────────
APP_ROOT="/srv/gomo"
APP_DIR="$APP_ROOT/app"
VENV_DIR="$APP_ROOT/venv"
ENV_FILE="$APP_ROOT/.env"
BACKUP_DIR="$APP_ROOT/backups"
LOCK_FILE="$APP_ROOT/run/deploy.lock"
BACKEND_DIR="$APP_DIR/backend"
FRONTEND_DIR="$APP_DIR/frontend"
BRANCH="main"
SERVICE="gomo"
WEB_SERVICE=""
HEALTH_URL="http://127.0.0.1:8000/"
KEEP_BACKUPS=10

export PATH="/usr/local/bin:/usr/bin:/bin:$HOME/.local/bin"

log() { printf '[%(%F %T)T] %s\n' -1 "$*"; }
die() { log "ERRO: $*" >&2; exit 1; }

# ─── Gate: aceita apenas "deploy <sha de 40 hex>" ─────────────
REQ="${SSH_ORIGINAL_COMMAND:-deploy ${1:-}}"
read -r CMD SHA EXTRA <<<"$REQ" || true
[[ "${CMD:-}" == "deploy" && "${SHA:-}" =~ ^[0-9a-f]{40}$ && -z "${EXTRA:-}" ]] || die "comando rejeitado"

exec 9>"$LOCK_FILE"
flock -n 9 || die "outro deploy em andamento"

cd "$APP_DIR"
PREV_SHA="$(git rev-parse HEAD)"
git fetch --quiet --prune origin "$BRANCH"
git merge-base --is-ancestor "$SHA" "origin/$BRANCH" || die "$SHA não pertence a origin/$BRANCH"

# ─── Funções ──────────────────────────────────────────────────
LAST_BACKUP=""

has_frontend() { [[ -n "$FRONTEND_DIR" && -f "$FRONTEND_DIR/package.json" ]]; }

install_python() {
  if [[ -f "$BACKEND_DIR/requirements.txt" ]]; then
    "$VENV_DIR/bin/pip" install --quiet --disable-pip-version-check -r "$BACKEND_DIR/requirements.txt"
  fi
}

build_frontend() {
  has_frontend || return 0
  cd "$FRONTEND_DIR"
  if [[ -f package-lock.json ]]; then npm ci --no-audit --no-fund --silent; else npm install --no-audit --no-fund --silent; fi
  npm run build --silent
}

restart_services() {
  sudo -n /usr/bin/systemctl restart "$SERVICE"
  if [[ -n "$WEB_SERVICE" ]]; then sudo -n /usr/bin/systemctl restart "$WEB_SERVICE"; fi
}

# Considera saudável qualquer resposta 2xx/3xx/4xx (5xx ou sem resposta = falha)
healthcheck() {
  [[ -z "$HEALTH_URL" ]] && return 0
  local code
  for _ in {1..20}; do
    code="$(curl -s -o /dev/null -w '%{http_code}' --max-time 3 "$HEALTH_URL" || true)"
    [[ "$code" =~ ^[234] ]] && return 0
    sleep 2
  done
  return 1
}

rollback() {
  local rc=$?
  trap - ERR
  set +e
  [[ $# -gt 0 ]] && rc=$1
  log "falha (exit $rc) — revertendo código para ${PREV_SHA:0:7}"
  git -C "$APP_DIR" reset --quiet --hard "$PREV_SHA"
  install_python
  build_frontend
  restart_services
  log "código revertido. Migrations NÃO foram desfeitas — backup: ${LAST_BACKUP:-nenhum}"
  exit "$rc"
}

# ─── Deploy ───────────────────────────────────────────────────
trap rollback ERR

log "deploy ${PREV_SHA:0:7} → ${SHA:0:7}"
git reset --quiet --hard "$SHA"

set +u; source "$VENV_DIR/bin/activate"; set -u
set -a; source "$ENV_FILE"; set +a

log "backend: dependências"
install_python

if [[ -n "${DATABASE_URL:-}" ]]; then
  mkdir -p "$BACKUP_DIR"
  LAST_BACKUP="$BACKUP_DIR/gomo-$(date +%Y%m%d-%H%M%S)-${PREV_SHA:0:7}.dump"
  log "backup do banco → $LAST_BACKUP"
  pg_dump --format=custom --no-owner --file="$LAST_BACKUP" \
    "$(sed -E 's#^postgres(ql)?\+[^:]+://#postgresql://#' <<<"$DATABASE_URL")"
  ls -1t "$BACKUP_DIR"/gomo-*.dump | tail -n +$((KEEP_BACKUPS + 1)) | xargs -r rm -f --
fi

cd "$BACKEND_DIR"
if [[ -f manage.py ]]; then
  log "migrations (Django)"
  python manage.py migrate --noinput
  python manage.py collectstatic --noinput -v 0 || log "collectstatic falhou (STATIC_ROOT definido?) — seguindo"
elif [[ -f alembic.ini ]]; then
  log "migrations (Alembic)"
  alembic upgrade head
else
  log "nenhuma ferramenta de migration detectada"
fi

if has_frontend; then log "frontend: build"; build_frontend; fi

log "reiniciando serviços"
restart_services

if ! healthcheck; then
  log "health check falhou em $HEALTH_URL"
  rollback 1
fi

log "deploy concluído: ${SHA:0:7}"
