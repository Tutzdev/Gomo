# Operação na VPS

O que precisa estar configurado no servidor para o Gomo funcionar de verdade: o Nginx repassando o IP de quem acessa, o backup do banco e um monitor que avise quando um mercado para de atualizar. Nada disso fica no repositório. São arquivos do servidor, configurados uma vez.

## 1. Nginx: repassar o IP real

A API roda atrás do Nginx, em `127.0.0.1:8087`. O backend usa `server.forward-headers-strategy=native` e só confia nesses cabeçalhos quando eles vêm do próprio servidor. **Sem eles, todo acesso parece vir de `127.0.0.1`**. Aí os limites por pessoa viram limites do site inteiro: 5 cadastros por hora e 10 logins por minuto para todo mundo somado.

No bloco `server` do domínio:

```nginx
location /api/ {
    proxy_pass http://127.0.0.1:8087;
    proxy_set_header Host              $host;
    proxy_set_header X-Real-IP         $remote_addr;
    proxy_set_header X-Forwarded-For   $proxy_add_x_forwarded_for;
    proxy_set_header X-Forwarded-Proto $scheme;
    proxy_set_header X-Forwarded-Host  $host;
}
```

Aplicar e conferir:

```bash
sudo nginx -t && sudo systemctl reload nginx
curl -s https://SEU_DOMINIO/api/v1/status/collections | head -c 300
```

## 2. Backup do banco

`scripts/backup.sh` gera um `pg_dump` (formato custom) e mantém os 14 mais recentes. O `deploy.sh` o executa antes de publicar cada versão. Se o backup falhar, o deploy para antes das migrations, que não são desfeitas no rollback.

```bash
# Como root, a partir do clone em /var/www/gomo
install -o root -g root -m 755 scripts/backup.sh /opt/gomo/bin/backup.sh
install -o root -g root -m 755 scripts/deploy.sh /opt/gomo/bin/deploy.sh
install -d -o deploy -g deploy -m 700 /opt/gomo/backups
```

O script lê `DATABASE_URL`, `DATABASE_USERNAME` e `DATABASE_PASSWORD` de `/etc/gomo/gomo.env`, o mesmo arquivo de ambiente do serviço. Se o arquivo estiver em outro lugar, defina `GOMO_ENV_FILE`. O usuário `deploy` precisa conseguir ler esse arquivo e ter o `pg_dump` instalado (pacote `postgresql-client`).

Backup diário às 3h30 (`crontab -u deploy -e`):

```cron
30 3 * * * /opt/gomo/bin/backup.sh >> /opt/gomo/backups/backup.log 2>&1
```

Restaurar:

```bash
pg_restore --clean --if-exists -h 127.0.0.1 -U <usuario> -d <banco> /opt/gomo/backups/gomo-AAAAMMDD-HHMMSS.dump
```

Vale copiar os backups para fora da VPS de vez em quando. Se o disco da VPS falhar, os backups vão junto.

## 3. Monitor das coletas

`GET /api/v1/status/collections` é público e responde:

- **200** quando todos os mercados com fonte de preços têm preço novo;
- **503** quando algum está atrasado (`STALE`, sem coleta nova há mais de `PRICE_COLLECTION_STALE_AFTER`, padrão 12 h) ou fora do ar (`DOWN`, sem preço atual e já fora das comparações).

Cadastre esse endereço num monitor de uptime gratuito (UptimeRobot, Better Stack…) para receber um e-mail no 503. Quando um mercado muda o próprio site, o coletor dele quebra. O alerta chega enquanto os preços ainda valem, ou seja, antes de o mercado sumir das comparações.

O mesmo relatório, com o último erro de cada coletor e quantos produtos cada mercado tem com preço atual, está em **Administração → Coletas** (`GET /api/v1/admin/collections/health`). Mercados sem fonte pública de preços aparecem como `NO_SOURCE` e não disparam alerta. Hoje são o Supermarket Aterrado e o Atacadão Vila Rica: veja [supermarket-aterrado.md](supermarket-aterrado.md) e a seção do Atacadão em [data-sources.md](data-sources.md).

## 4. Agenda das coletas

| Quando | O quê | Variável |
| --- | --- | --- |
| 06h, 11h, 16h e 21h | Atualiza os preços dos produtos já conhecidos | `PRICE_COLLECTION_CRON` |
| Domingo, 03h | Importa tudo, inclusive os produtos novos que os mercados lançaram | `PRICE_COLLECTION_FULL_CRON` (`-` desliga) |
| Ao iniciar | Atualiza quem está sem coleta há mais de 8 h | `PRICE_REFRESH_WHEN_OLDER_THAN` |

Uma coleta manual de um mercado, pela API de admin: `POST /api/v1/admin/collections?collectorCode=royal_retiro` (com `existingOnly=true` para só atualizar preços).
