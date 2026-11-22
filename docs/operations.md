# Operação na VPS

O que precisa estar configurado no servidor para o Gomo funcionar de verdade: o Nginx com limites e cabeçalhos de segurança, o backup do banco e um monitor que avise quando um mercado para de atualizar. A configuração do Nginx e os scripts ficam no repositório, mas são instalados à mão no servidor, uma vez (o deploy não mexe neles).

## 1. Nginx

A configuração está em [`deploy/nginx/gomo.conf`](../deploy/nginx/gomo.conf) (o site) e [`deploy/nginx/gomo-proxy.conf`](../deploy/nginx/gomo-proxy.conf) (o repasse para a API). O domínio de produção não fica no repositório: os arquivos usam `gomo.example.com`, trocado na instalação.

O que ela faz:

- **Repassa o IP real e o endereço público para a API**, que roda em `127.0.0.1:8087`. O backend usa `server.forward-headers-strategy=native` e só confia nesses cabeçalhos quando eles vêm do próprio servidor. **Sem eles, todo acesso parece vir de `127.0.0.1`**: os limites por pessoa viram limites do site inteiro (5 cadastros por hora e 10 logins por minuto para todo mundo somado), e sem `X-Forwarded-Proto`/`X-Forwarded-Host` o cookie `__Host-` e a checagem de `Origin` recusam os pedidos do site (item 7). `X-Forwarded-For` recebe só o IP de quem conectou, então ninguém consegue se passar por outro IP mandando o cabeçalho.
- **Limita pedidos por IP.** `/api/` inteira: 10 por segundo com rajada de 60, o que cobre a navegação normal e deixa lento quem tenta baixar o catálogo todo. `/api/v1/auth/` (entrar, criar conta, e-mails de conta): 20 por minuto com rajada de 10. A conferência de sessão (`/api/v1/auth/session`, chamada a cada página) segue o limite geral, e o webhook da AbacatePay não tem limite. Quem passa do limite recebe 429. Muita gente atrás do mesmo IP (rede móvel com CGNAT, wi-fi de empresa) divide o limite; se aparecer 429 em uso normal no log (`limiting requests`), aumente `burst` antes de `rate`.
- **Cabeçalhos de segurança:** `Content-Security-Policy`, `Strict-Transport-Security` (1 ano, só o domínio), `X-Content-Type-Options`, `X-Frame-Options`, `Referrer-Policy` e `Permissions-Policy` (só a localização, usada pela rota entre mercados, fica liberada).
- Serve o frontend de `/var/www/gomo/frontend/dist`, com cache longo em `/assets/` e o `index.html` sempre novo; `www` e `http` redirecionam para `https://SEU_DOMINIO`.

A CSP libera só o que o site usa: scripts e fontes próprios (as fontes vêm dos pacotes `@fontsource`, servidas pelo próprio site), fotos de produto em qualquer endereço https (vêm dos sites dos mercados, em domínios que mudam) e os domínios de ingestão do Sentry. **Ligou o analytics** (item 8)? Acrescente o domínio do script (por exemplo `https://plausible.io`) em `script-src` e `connect-src`, senão o navegador o bloqueia.

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

### Ajuste do PostgreSQL

Os valores de fábrica do Postgres (128 MB de cache, JIT ligado, consulta paralela) foram feitos para qualquer máquina, não para uma VPS de 1 vCPU com um banco de ~15 GB. Na VPS atual (1 vCPU, 3,8 GB de RAM) eles ficam em `/etc/postgresql/16/main/conf.d/90-desempenho.conf`:

```
shared_buffers = 512MB                 # exige reiniciar o PostgreSQL
effective_cache_size = 2GB
work_mem = 16MB                        # a consulta de ofertas derramava em disco com 4MB
maintenance_work_mem = 256MB           # VACUUM depois da compactação do histórico
jit = off                              # compilar o plano custava mais que executá-lo
max_parallel_workers_per_gather = 0    # 1 vCPU: consulta paralela só disputa o mesmo processador
```

`random_page_cost` fica no padrão (4): com 1.1 o planejador trocou a leitura sequencial de `daily_lowest_prices` por milhares de buscas no índice e a consulta de ofertas foi de 0,7 s para 4 s nesse disco. Com o ajuste, ela levava ~0,5 s, mas com 2,4 milhões de preços e o servidor usando swap passou de 60 s e a página `/app/ofertas` deu 504 (22/11/2026). Desde a V58 o cálculo tem duas partes: o preço atual de cada produto vem só de `latest_prices` (que agora guarda o preço, sem ir em `price_records`), e o histórico de cada item (preço de sempre e menor preço em 90 dias, ~8 s em produção) é lido uma vez por dia por cidade, porque só usa os dias antes de hoje. O visitante nunca espera pelo cálculo: as ofertas de cada cidade são calculadas quando a API sobe e depois de cada coleta, e qualquer página já calculada é servida na hora enquanto a nova é calculada em segundo plano (`DealService`).

## 2. Backup do banco

`scripts/backup.sh` gera um `pg_dump` (formato custom) e mantém os 7 mais recentes (`GOMO_BACKUP_KEEP`, no ambiente ou no próprio `/etc/gomo/gomo.env`). O `deploy.sh` o executa antes de publicar cada versão. Se o backup falhar, o deploy para antes das migrations, que não são desfeitas no rollback.

```bash
# Como root, a partir do clone em /var/www/gomo
install -o root -g root -m 755 scripts/backup.sh /opt/gomo/bin/backup.sh
install -o root -g root -m 755 scripts/deploy.sh /opt/gomo/bin/deploy.sh
install -d -o root -g root -m 700 /opt/gomo/backups
# O deploy (usuário deploy) chama o backup com sudo, sem senha e só esse comando:
echo "deploy ALL=(root) NOPASSWD: /opt/gomo/bin/backup.sh" > /etc/sudoers.d/gomo-backup
chmod 440 /etc/sudoers.d/gomo-backup && visudo -c
```

O script roda como root e lê `DATABASE_URL`, `DATABASE_USERNAME` e `DATABASE_PASSWORD` de `/etc/gomo/gomo.env`, o mesmo arquivo de ambiente do serviço (que só o root lê). Se o arquivo estiver em outro lugar, crie um link (`ln -s /etc/gomo.env /etc/gomo/gomo.env`): o sudo não repassa `GOMO_ENV_FILE`. O `pg_dump` vem do pacote `postgresql-client`.

Backup diário às 3h30 (`crontab -e` como root):

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
| Todo dia, 10h | Recalcula as listas semanais e mensais de quem tem cidade preferida, para o Início comparar com a semana passada; apaga os dias com mais de 60 dias | `RECURRING_LISTS_CRON` (`-` desliga) |

A importação ao iniciar existe porque um deploy pode ensinar um coletor a ler ofertas que ele descartava (o hortifrúti pesado dos mercados VIP, os ~4.500 produtos do Atacadão). As coletas do dia só atualizam produtos conhecidos, então sem ela esses produtos esperariam até domingo. Cada execução grava em `collection_runs.full_import` se foi completa.

Fora as coletas, uma limpeza diária às 04h30 (`ACCOUNT_CLEANUP_CRON`, `-` desliga) apaga sessões e links de confirmação/redefinição vencidos, o contador do plano grátis com mais de 7 dias (`COMPARISON_USAGE_RETENTION`, padrão `P7D`) e as contas que nunca confirmaram o e-mail e não entram há 30 dias (`UNVERIFIED_ACCOUNT_RETENTION`, padrão `P30D`). Conta com contribuição, registro de auditoria ou pagamento nunca é apagada por essa rotina. O resultado vai para o log (`Account cleanup: ...`).

O menor preço de cada produto em cada mercado por dia fica em `daily_lowest_prices`, mantida por um trigger em `price_records` (nenhum caminho que grava preço esquece de atualizar). É dela que saem o histórico, o "preço de sempre" (média dos 30 dias antes de hoje, com pelo menos 7 dias de preço) usado pelo veredito, pelo alerta "abaixo da média", pela página de ofertas e pela "Hora de estocar". A migração V48 preenche a tabela com todo o histórico existente: o primeiro deploy com ela demora um pouco mais na etapa das migrations (o `deploy.sh` faz o backup antes). A página pública `/ofertas` é calculada uma vez por cidade e recalculada em segundo plano quando passa de 30 minutos (`DEALS_CACHE_TTL`).

Mercado sem preço não aparece para o público: `GET /api/v1/stores` (página de mercados, landing, cidade padrão) e as comparações só trazem mercados com preço usável. Um mercado online com a coleta atrasada continua na lista como desatualizado; um mercado sem loja online só aparece depois da primeira visita e sai de novo quando os preços da prateleira vencem (`PRICE_IN_STORE_MAX_AGE`). O painel "Preço na loja" do admin usa `GET /api/v1/admin/stores`, que lista todos.

As coletas gravam todos os produtos de todos os mercados 4 vezes por dia, e quase sempre o preço é o mesmo da vez anterior: em 11/2026 `price_records` crescia ~480 MB por dia, 92% disso repetido, e enchia o disco da VPS em semanas. Toda noite às 2h (`PRICE_HISTORY_COMPACTION_CRON`, `-` desliga) saem as observações com mais de 3 dias (`PRICE_HISTORY_DETAIL_RETENTION`, padrão `P3D`) que repetem a anterior. Ficam a primeira de cada preço (o momento em que ele mudou, incluindo começo e fim de promoção e falta de estoque), a mais recente de cada mercado e produto, as anotadas na loja, as contribuições e as que um alerta cita. Nada do que o site mostra muda: o histórico guarda cada mudança e o "preço de sempre" vem de `daily_lowest_prices`, que o trigger preenche na gravação. Apagar linhas não devolve espaço ao disco, só o libera para as próximas gravações; para devolver de uma vez depois de uma limpeza grande, `VACUUM FULL price_records` (trava a tabela por alguns minutos, faça de madrugada).

As fotos do Mercafacil (Pame, Ville) são originais de até 220 KB. O site as pede em `/api/v1/images/products?src=…&w=…`: a API baixa a foto uma vez por largura (96, 160, 240 ou 480 px), reduz para um JPEG de poucos KB e guarda em `.local/image-cache` (`APP_IMAGES_CACHE_DIRECTORY`; cerca de 200 MB no pior caso). Só os sites de `APP_IMAGES_RESIZE_HOSTS` são buscados; se a foto não puder ser lida, a resposta redireciona para a original. As lojas VTEX já reduzem no próprio CDN e as demais mandam arquivos pequenos.

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

## 9. Avisos de preço no WhatsApp (opcional)

Desligado enquanto `WHATSAPP_ENABLED=false`: o cartão "Avisos no WhatsApp" nem aparece na página de alertas e a API responde 503 ao salvar um número. Ligado, quem cadastra o celular (com DDD e consentimento marcado) recebe cada aviso novo também no WhatsApp, pela API oficial da Meta (Cloud API).

1. **Crie o app e o número** no [WhatsApp Business Platform](https://developers.facebook.com/docs/whatsapp/cloud-api/get-started). O número de teste da Meta serve para conferir o fluxo; em produção, o número precisa de uma conta comercial verificada.
2. **Crie o template** de categoria *Utility*, idioma `pt_BR`, com o nome `alerta_de_preco` (ou outro, em `WHATSAPP_ALERT_TEMPLATE`) e três variáveis no corpo, nesta ordem: produto, preço e mercado. Exemplo:

   > O preço que você esperava chegou! **{{1}}** está **{{2}}** no **{{3}}**. Veja no Gomo.

   Mensagem que o Gomo manda primeiro só pode sair por template aprovado.
3. **Gere um token permanente** de um usuário do sistema com a permissão `whatsapp_business_messaging`.
4. **Configure** em `/etc/gomo/gomo.env` e reinicie o serviço:

   ```bash
   WHATSAPP_ENABLED=true
   WHATSAPP_PHONE_NUMBER_ID=...   # o "Phone number ID", não o número em si
   WHATSAPP_ACCESS_TOKEN=...
   WHATSAPP_ALERT_TEMPLATE=alerta_de_preco
   ```

   Faltando o id ou o token, a aplicação não sobe e o log diz o que falta.

Como a entrega funciona: o aviso continua nascendo no sininho, dentro da coleta. Um job (`WhatsAppAlertDispatcher`, a cada minuto) manda os avisos novos de quem autorizou o número e grava cada tentativa em `whatsapp_deliveries`. Uma recusa espera 10 minutos para a próxima tentativa, até 3; aviso com mais de 6 horas não sai mais, porque o preço já pode ter mudado. Trocar o número renova o consentimento, então nada antigo vai para o número novo. O número entra na exportação de dados e sai junto com a conta.
