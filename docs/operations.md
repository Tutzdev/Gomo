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

O mesmo relatório, com o último erro de cada coletor e quantos produtos cada mercado tem com preço atual, está em **Administração → Coletas** (`GET /api/v1/admin/collections/health`). Um mercado ativo sem coletor agendado aparece como `NO_SOURCE` e não dispara alerta. Mercados desativados (como os retirados em 10/2026, veja [data-sources.md](data-sources.md#mercados-retirados-em-102026)) não aparecem no relatório.

## 4. Agenda das coletas

| Quando | O quê | Variável |
| --- | --- | --- |
| 06h, 11h, 16h e 21h | Atualiza os preços dos produtos já conhecidos | `PRICE_COLLECTION_CRON` |
| Domingo, 03h | Importa tudo, inclusive os produtos novos que os mercados lançaram | `PRICE_COLLECTION_FULL_CRON` (`-` desliga) |
| Ao iniciar | Importa tudo nos mercados sem importação completa nos últimos 7 dias; atualiza quem está sem coleta há mais de 8 h | `PRICE_REFRESH_WHEN_OLDER_THAN` |

A importação ao iniciar existe porque um deploy pode ensinar um coletor a ler ofertas que ele descartava (o hortifrúti pesado dos mercados VIP, os ~4.500 produtos do Atacadão). As coletas do dia só atualizam produtos conhecidos, então sem ela esses produtos esperariam até domingo. Cada execução grava em `collection_runs.full_import` se foi completa.

Uma coleta manual de um mercado, pela API de admin: `POST /api/v1/admin/collections?collectorCode=royal_retiro` (com `existingOnly=true` para só atualizar preços).

## 5. Termos e Privacidade

As páginas `/termos` e `/privacidade` mostram quem responde pelo Gomo e o e-mail para pedidos sobre dados pessoais. Esses dados entram no build do frontend. Crie uma vez, na VPS, o arquivo `/var/www/gomo/frontend/.env.production.local`. Ele é ignorado pelo Git e sobrevive ao `git reset` do deploy, e o Vite o lê em todo `npm run build`:

```bash
VITE_LEGAL_RESPONSIBLE="Nome completo ou razão social (e CNPJ, se houver)"
VITE_LEGAL_CONTACT_EMAIL="contato@seudominio.com.br"
```

Sem eles, as páginas omitem essas linhas. Os textos descrevem o que o código realmente coleta e guarda. Vale a revisão de um advogado antes de escalar o produto.

No mesmo arquivo, informe o endereço público do site. Ele entra nas tags Open Graph (a prévia com imagem quando alguém cola o link no WhatsApp) e nos links do botão "Compartilhar". Sem ele, a prévia aparece sem imagem:

```bash
VITE_SITE_URL="https://seudominio.com.br"
```


## 6. Pagamento do Premium (AbacatePay)

O Premium pago usa o checkout hospedado de assinatura da AbacatePay (API v2): o Gomo nunca vê dados de cartão. Só o webhook do provedor libera o acesso; voltar da página de pagamento não prova nada. Enquanto `ABACATEPAY_ENABLED=false`, a página de planos continua oferecendo só o teste grátis.

1. **Teste primeiro em Dev mode.** A conta nova da AbacatePay já começa nele. Em produção, a AbacatePay exige CNPJ e a análise leva até 3 dias úteis.
2. **Crie os dois produtos** no painel, ou por `POST /products/create`, com `currency` `BRL`:

   | Plano | `externalId` | `price` (centavos) | `cycle` |
   | --- | --- | --- | --- |
   | Mensal | `gomo-premium-mensal` | `1490` | `MONTHLY` |
   | Anual | `gomo-premium-anual` | `11990` | `ANNUALLY` |

   Os ids (`prod_...`) vão em `ABACATEPAY_MONTHLY_PRODUCT_ID` e `ABACATEPAY_ANNUAL_PRODUCT_ID`. O preço da landing (`frontend/src/lib/brand.ts`) precisa ser o mesmo dos produtos.
3. **Crie uma chave de API** só com `SUBSCRIPTION:CREATE` e `SUBSCRIPTION:DELETE`. As permissões não mudam depois que a chave é criada.
4. **Cadastre o webhook** com a URL `https://SEU_DOMINIO/api/v1/billing/abacatepay/webhook`, um secret longo e aleatório (`openssl rand -hex 32`) e os eventos `subscription.completed`, `subscription.renewed`, `subscription.payment_failed` e `subscription.cancelled`. A AbacatePay manda o secret no parâmetro `webhookSecret` da URL; o Gomo também confere a assinatura HMAC do corpo.
5. **Configure** em `/etc/gomo/gomo.env` e reinicie o serviço:

   ```bash
   ABACATEPAY_ENABLED=true
   ABACATEPAY_API_KEY=...
   ABACATEPAY_WEBHOOK_SECRET=...
   ABACATEPAY_MONTHLY_PRODUCT_ID=prod_...
   ABACATEPAY_ANNUAL_PRODUCT_ID=prod_...
   ABACATEPAY_SITE_URL=https://SEU_DOMINIO
   ABACATEPAY_DEV_MODE=true    # false com a chave de produção
   ```

   Faltando qualquer um, a aplicação não sobe e o log diz o que falta. `ABACATEPAY_DEV_MODE` precisa combinar com a chave: um evento de Dev mode nunca libera Premium em produção.
6. **Teste o fluxo** com o cartão `4242 4242 4242 4242` (validade futura e qualquer CVV): assinar, voltar para `/assinar?pagamento=concluido`, ver o Premium ativo e cancelar no perfil. Os logs de entrega do webhook ficam no painel da AbacatePay.

Como o acesso funciona: cada pagamento confirmado (`subscription.completed` ou `subscription.renewed`) estende o Premium por um ciclo, contado no horário de Brasília, mais 3 dias, que cobrem as retentativas de uma renovação recusada. Cancelar pelo perfil para as cobranças e mantém o período já pago. Se as retentativas esgotarem, a AbacatePay cancela a assinatura e o Premium termina na hora. Excluir a conta cancela a assinatura antes; se a AbacatePay não confirmar, a exclusão espera.
