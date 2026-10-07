#!/usr/bin/env bash
# Backup do banco do Gomo (pg_dump em formato custom), com rotação.
# Instalado em /opt/gomo/bin/backup.sh. O deploy.sh o executa antes de publicar uma versão nova
# (as migrations do Flyway não são desfeitas no rollback) e o cron o executa uma vez por dia.
#   Restaurar: pg_restore --clean --if-exists -d <banco> /opt/gomo/backups/<arquivo>.dump
set -Eeuo pipefail

ENV_FILE="${GOMO_ENV_FILE:-/etc/gomo/gomo.env}"
BACKUP_DIR="${GOMO_BACKUP_DIR:-/opt/gomo/backups}"
KEEP="${GOMO_BACKUP_KEEP:-14}"

log() { printf '[%(%F %T)T] backup: %s\n' -1 "$*"; }
die() { log "ERRO: $*" >&2; exit 1; }

[[ -r "$ENV_FILE" ]] || die "arquivo de ambiente $ENV_FILE ilegível (defina GOMO_ENV_FILE)"
# Lê só as variáveis do banco, sem executar o arquivo.
value() { sed -n "s/^[[:space:]]*\(export[[:space:]]\+\)\?$1=//p" "$ENV_FILE" | tail -1 | sed "s/^['\"]//; s/['\"]$//"; }
URL="$(value DATABASE_URL)"
USER_NAME="$(value DATABASE_USERNAME)"
PASSWORD="$(value DATABASE_PASSWORD)"
[[ "$URL" =~ ^jdbc:postgresql://([^:/]+)(:([0-9]+))?/([^?]+) ]] || die "DATABASE_URL não é jdbc:postgresql://host:porta/banco"
HOST="${BASH_REMATCH[1]}"; PORT="${BASH_REMATCH[3]:-5432}"; DATABASE="${BASH_REMATCH[4]}"

mkdir -p "$BACKUP_DIR"
chmod 700 "$BACKUP_DIR" 2>/dev/null || true
FILE="$BACKUP_DIR/gomo-$(date +%Y%m%d-%H%M%S).dump"
log "gerando $FILE"
PGPASSWORD="$PASSWORD" pg_dump -h "$HOST" -p "$PORT" -U "$USER_NAME" -d "$DATABASE" -Fc -f "$FILE.partial" \
    || { rm -f "$FILE.partial"; die "pg_dump falhou"; }
mv "$FILE.partial" "$FILE"
chmod 600 "$FILE"
log "ok ($(du -h "$FILE" | cut -f1))"

# Mantém só os $KEEP mais recentes.
ls -1t "$BACKUP_DIR"/gomo-*.dump 2>/dev/null | tail -n +"$((KEEP + 1))" | xargs -r rm -f
