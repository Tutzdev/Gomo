# Operação na VPS

O que precisa estar configurado no servidor para o Gomo funcionar de verdade: o Nginx com limites e cabeçalhos de segurança, o backup do banco e um monitor que avise quando um mercado para de atualizar. A configuração do Nginx e os scripts ficam no repositório, mas são instalados à mão no servidor, uma vez (o deploy não mexe neles).

## 1. Nginx

A configuração está em [`deploy/nginx/gomo.conf`](../deploy/nginx/gomo.conf) (o site) e [`deploy/nginx/gomo-proxy.conf`](../deploy/nginx/gomo-proxy.conf) (o repasse para a API). O domínio de produção não fica no repositório: os arquivos usam `gomo.example.com`, trocado na instalação.

O que ela faz:

- **Repassa o IP real e o endereço público para a API**, que roda em `127.0.0.1:8087`. O backend usa `server.forward-headers-strategy=native` e só confia nesses cabeçalhos quando eles vêm do próprio servidor. **Sem eles, todo acesso parece vir de `127.0.0.1`**: os limites por pessoa viram limites do site inteiro (5 cadastros por hora e 10 logins por minuto para todo mundo somado), e sem `X-Forwarded-Proto`/`X-Forwarded-Host` o cookie `__Host-` e a checagem de `Origin` recusam os pedidos do site (item 7). `X-Forwarded-For` recebe só o IP de quem conectou, então ninguém consegue se passar por outro IP mandando o cabeçalho.
- **Limita pedidos por IP.** `/api/` inteira: 10 por segundo com rajada de 60, o que cobre a navegação normal e deixa lento quem tenta baixar o catálogo todo. `/api/v1/auth/` (entrar, criar conta, e-mails de conta): 20 por minuto com rajada de 10. A conferência de sessão (`/api/v1/auth/session`, chamada a cada página) segue o limite geral, e o webhook da AbacatePay não tem limite. Quem passa do limite recebe 429. Muita gente atrás do mesmo IP (rede móvel com CGNAT, wi-fi de empresa) divide o limite; se aparecer 429 em uso normal no log (`limiting requests`), aumente `burst` antes de `rate`.
- **Cabeçalhos de segurança:** `Content-Security-Policy`, `Strict-Transport-Security` (1 ano, só o domínio), `X-Content-Type-Options`, `X-Frame-Options`, `Referrer-Policy` e `Permissions-Policy` (só a localização, usada pela rota entre mercados, fica liberada).
- Serve o frontend de `/var/www/gomo/frontend/dist`, com cache longo em `/assets/` e o `index.html` sempre novo; `www` e `http` redirecionam para `https://SEU_DOMINIO`.

A CSP libera só o que o site usa: scripts próprios, Google Fonts (`fonts.googleapis.com` e `fonts.gstatic.com`), fotos de produto em qualquer endereço https (vêm dos sites dos mercados, em domínios que mudam) e os domínios de ingestão do Sentry. **Ligou o analytics** (item 8)? Acrescente o domínio do script (por exemplo `https://plausible.io`) em `script-src` e `connect-src`, senão o navegador o bloqueia. Se as fontes passarem a ser servidas pelo próprio site, os dois domínios do Google podem sair.

Instalar (como root, a partir do clone em `/var/www/gomo`), trocando `SEU_DOMINIO`:

```bash
sed 's/gomo.example.com/SEU_DOMINIO/g' deploy/nginx/gomo.conf > /etc/nginx/sites-available/gomo
install -m 644 deploy/nginx/gomo-proxy.conf /etc/nginx/gomo-proxy.conf
ln -sf /etc/nginx/sites-available/gomo /etc/nginx/sites-enabled/gomo
mkdir -p /var/www/letsencrypt
nginx -t && systemctl reload nginx
```

Antes, confira: o certificado precisa cobrir `SEU_DOMINIO` e `www.SEU_DOMINIO` (`certbot certonly --webroot -w /var/www/letsencrypt -d SEU_DOMINIO -d www.SEU_DOMINIO`), e o arquivo antigo do site em `sites-enabled` sai, para não haver dois `server` com o mesmo nome. Guarde uma cópia dele: se algo der errado, é só voltar o link e recarregar.

Conferir:

```bash
curl -sI https://SEU_DOMINIO/ | grep -iE 'content-security|strict-transport|referrer|permissions'
curl -s https://SEU_DOMINIO/api/v1/status/collections | head -c 300
# 429 depois da rajada:
for i in $(seq 1 15); do curl -s -o /dev/null -w '%{http_code} ' -X POST https://SEU_DOMINIO/api/v1/auth/login; done
```

Depois abra o site com o console do navegador aberto: nenhuma mensagem "Refused to load ... Content Security Policy" deve aparecer.

### Limite de tentativas da API (em memória)

Além do Nginx, a API limita cadastros, logins, e-mails de conta e contribuições por IP e por conta (`AttemptRateLimiter`). Esse limite fica na memória da API e zera quando ela reinicia, a cada deploy. Foi mantido assim de propósito no MVP:

- há uma única instância da API, então a memória enxerga todos os pedidos;
- as janelas são curtas (1 minuto para login, 1 hora para cadastro e e-mails); um deploy dá a quem ataca, no máximo, uma janela a mais, e ninguém de fora consegue provocar um deploy;
- o limite do Nginx em `/api/v1/auth/` não zera com o deploy da API e cobre esse intervalo; a senha ainda passa pelo BCrypt (custo 12) a cada tentativa;
- a política de privacidade diz que o IP usado nesses limites fica só na memória do servidor, por no máximo uma hora. Guardar no Postgres exigiria mudar esse texto e gravar IPs no banco, além de uma escrita por tentativa.

Vale rever se a API passar a rodar em mais de uma instância: aí o limite precisa ficar num lugar comum (uma tabela no Postgres ou Redis).

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

Fora as coletas, uma limpeza diária às 04h30 (`ACCOUNT_CLEANUP_CRON`, `-` desliga) apaga sessões e links de confirmação/redefinição vencidos, o contador do plano grátis com mais de 7 dias (`COMPARISON_USAGE_RETENTION`, padrão `P7D`) e as contas que nunca confirmaram o e-mail e não entram há 30 dias (`UNVERIFIED_ACCOUNT_RETENTION`, padrão `P30D`). Conta com contribuição, registro de auditoria ou pagamento nunca é apagada por essa rotina. O resultado vai para o log (`Account cleanup: ...`).

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

## 7. Sessão em cookie

O site guarda a sessão no cookie `__Host-gomo_session` (HttpOnly, Secure, SameSite=Lax). Em produção não há nada para configurar: o cookie exige HTTPS, e o Nginx do item 1 já repassa `X-Forwarded-Proto` e `X-Forwarded-Host`, que a API usa para conferir o `Origin` dos pedidos que mudam dados. Sem esses cabeçalhos, todo POST do site é recusado com 403 "Pedido de outra origem recusado.". Só o ambiente local em http usa `AUTH_COOKIE_SECURE=false`, que o perfil `development` já liga.

## 8. Medição de uso e de erros (opcional)

Tudo fica desligado até as variáveis existirem. Sem elas, nenhum script de terceiros é carregado.

- **Erros do backend:** `SENTRY_DSN` (e, se quiser, `SENTRY_ENVIRONMENT`) em `/etc/gomo/gomo.env`. Só os erros 500 inesperados são enviados, sem a mensagem da exceção, sem dados da requisição e sem usuário.
- **Erros do site:** `VITE_SENTRY_DSN` (e `VITE_SENTRY_ENVIRONMENT`) no `frontend/.env` do build. Use um projeto JavaScript separado no Sentry.
- **Uso do site, sem cookie:** `VITE_ANALYTICS_PROVIDER` (`plausible` ou `umami`), `VITE_ANALYTICS_SCRIPT_URL` (o `script.js` do serviço, em https) e `VITE_ANALYTICS_SITE_ID` (o domínio no Plausible ou o website id no Umami), também no build.

No Sentry, ative **Settings > Security & Privacy > Prevent Storing of IP Addresses** nos dois projetos. A Política de Privacidade diz que o IP não é guardado. As variáveis `VITE_*` entram no build, então é preciso gerar o frontend de novo depois de mudá-las. A página de privacidade passa a descrever o que estiver ligado.
