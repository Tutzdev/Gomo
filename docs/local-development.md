# Desenvolvimento local e operação

Guia de uso diário do ambiente de desenvolvimento e da coleta de preços. Para a visão geral do projeto, veja o [README](../README.md).

## Reiniciar o ambiente local existente

Para o uso diário nesta máquina Windows, use a configuração persistente em `.local/runtime.json`. Ela aponta para o mesmo PostgreSQL que contém as contas, assinaturas e coletas. O exemplo de formato está em `scripts/local-runtime.example.json`; caminhos e portas devem corresponder ao banco existente. A senha fica no arquivo local indicado por `passwordFile`, nunca no Git.

Em dois terminais na raiz do repositório:

```powershell
.\scripts\start-local.ps1 -Service Backend
```

```powershell
.\scripts\start-local.ps1 -Service Frontend
```

Abra `http://localhost:5173`. O script inicia o PostgreSQL já configurado se estiver parado e mantém a API na porta configurada (8081 nesta máquina). Ele não cria banco novo nem redefine contas/assinaturas. O frontend exige a porta 5173 livre para evitar trocar a origem que armazena a sessão. Pare a instância anterior antes de iniciar outra. O SMTP de desenvolvimento continua em `localhost:1025` para os fluxos de confirmação/recuperação por e-mail.

**Ao receber um pedido para iniciar ou reiniciar a aplicação, reutilize esse ambiente.** O roteiro de banco limpo em `docs/mvp-data-check.md` serve para validação isolada; não deve substituir o banco de uso diário. Uma falha temporária de rede permite tentar novamente sem apagar a sessão; um token efetivamente expirado continua exigindo novo login. Contas e assinaturas permanecem no banco, independentemente da duração do token.

O inicializador Windows usa os certificados confiáveis do sistema para validar HTTPS nas fontes públicas, mantendo a verificação TLS. O reinício automático do Java fica desativado nesse ambiente persistente: após alterar o backend, reinicie seu processo pelo mesmo script. Isso evita interromper uma coleta quando o Maven compila testes.

## Frontend Gomo

Com a API disponível em `http://localhost:8080`, execute:

```powershell
cd frontend
Copy-Item .env.example .env
npm install
npm run dev
```

A interface fica em `http://localhost:5173`. Para permitir as chamadas locais, configure `CORS_ALLOWED_ORIGINS=http://localhost:5173` no backend. A URL da API pode ser alterada em `frontend/.env` por meio de `VITE_API_BASE_URL`.

O checkout e o paywall visual não simulam pagamento. Enquanto a integração de cobrança não estiver disponível, uma conta existente pode receber acesso de assinante por meio de `SUBSCRIBER_BOOTSTRAP_EMAIL`; o Docker Compose encaminha essa variável ao backend. Checkout, portal, cancelamento e confirmação de pagamentos por webhook ainda precisam ser integrados.

## Coleta de preços em produção

* **Servidor novo:** se o banco não tem nenhum preço, o backend carrega na subida as coletas incluídas no app (`src/main/resources/seed/catalog-snapshots`, uma por mercado) e monta o catálogo de comparação. Leva uns 15 minutos e cabe em 512 MB de heap; o site já abre enquanto isso. Desligue com `PRICE_COLLECTION_SEED_WHEN_EMPTY=false`.
* **Depois:** 4 vezes por dia (`PRICE_COLLECTION_CRON`, padrão 6h, 11h, 16h e 21h) a coleta atualiza os preços dos produtos que já existem. Produto novo nos mercados só entra numa coleta completa: a de domingo, a da subida (mercados sem importação completa nos últimos 7 dias), `PRICE_COLLECTION_EXISTING_ONLY=false` ou `POST /api/v1/admin/collections` como administrador.
* **Atualizar as sementes:** depois de uma coleta completa local com `APP_COLLECTION_ARCHIVE_DIRECTORY` configurado, comprima cada `.local/catalog-snapshots/<mercado>.json` para `<mercado>.json.gz` nessa pasta.
* **Atacadão:** as unidades revisadas (São Geraldo e Vila Rica) são registradas na subida, então a coleta online funciona mesmo com os encartes revisados vencidos; os coletores de encarte só voltam a publicar ofertas depois de uma nova revisão.

O MVP coleta os departamentos públicos de Nagumo Ponte Alta e Royal Retiro, com vínculos explícitos entre produtos revisados. A busca da lista percorre todo o catálogo por páginas; cada mercado possui seu catálogo com preços e datas. A comparação mostra cobertura, faltantes, total completo ou subtotal parcial e a menor combinação por item. O roteiro inicial está em [`docs/mvp-data-check.md`](docs/mvp-data-check.md), e a ampliação com validação de 20 produtos em [`docs/catalog-list-validation.md`](docs/catalog-list-validation.md). Se a porta 8080 estiver ocupada por outro serviço, use `PORT=8081` no backend e `VITE_API_BASE_URL=http://localhost:8081/api/v1` em `frontend/.env.local`.
