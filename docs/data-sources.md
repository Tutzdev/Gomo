# Fontes de dados e ingestão

A expansão de 18/09/2026, com novas unidades, protocolos, limites e operação, está documentada em [volta-redonda-expansion.md](volta-redonda-expansion.md). O recorte de duas fontes abaixo é o registro do MVP anterior.

O banco inicial contém somente o estado e as quatro cidades autorizadas pelo escopo. Não há fontes comerciais, lojas, produtos, estoques ou preços pré-cadastrados. Uma fonte, rede e loja são criadas somente depois de uma coleta real bem-sucedida. Os registros sintéticos usados nos testes estão exclusivamente em `src/test`.

As interfaces `ProductDataProvider`, `StoreDataProvider` e `PriceDataProvider` continuam definindo consultas paginadas. Os coletores automáticos implementam `SupermarketCollector` e entregam um catálogo normalizado ao mesmo domínio de ingestão. A ausência de uma integração não é convertida em dados inventados.

## Situação das fontes em 07/10/2026

| Unidade | Fonte | Situação |
| --- | --- | --- |
| Nagumo Ponte Alta | Site Nagumo (Salesforce Commerce) | Coletado. Não publica EAN; o pareamento é pela descrição. Itens indisponíveis vêm sem preço e são ignorados. |
| Bramil, Pérola, Royal, Spani | VipCommerce | Coletados, inclusive itens vendidos por peso. O script de configuração é descoberto sozinho quando o site muda. |
| Pame, Ville | Mercafácil | Coletados. Não publicam EAN; o tamanho extra da Mercafácil às vezes contradiz a descrição, e esses itens ficam de fora. |
| Hortifruti Aterrado | Site Hortifruti | Coletado. |
| Atacadão São Geraldo | E-commerce Atacadão (VTEX) | Coletado. O campo `gtin` traz código interno, não EAN. |
| Atacadão Vila Rica | — | **Sem fonte.** Entre as 220 lojas do e-commerce Atacadão, a única de Volta Redonda é São Geraldo (`atacadaobr815`). Os encartes revisados à mão venceram em 21/09; o coletor de encarte só roda quando houver encarte revisado válido. |
| Supermarket Aterrado | — | **Sem fonte** ([supermarket-aterrado.md](supermarket-aterrado.md)). |

Mercados sem preço atual não aparecem na lista de supermercados nem nas comparações. O monitoramento de cada coletor está em [operations.md](operations.md#3-monitor-das-coletas).

## Fontes ativas do MVP

O recorte inicial foi validado em **17/09/2026**; suas evidências históricas estão em [mvp-data-check.md](mvp-data-check.md). A configuração atual percorre os departamentos públicos das duas fontes e preserva o banco existente. Contagens variam conforme a loja publica seu estoque.

### Nagumo Ponte Alta

- Rede Nagumo, unidade **036-V.REDONDA**, external ID **36**.
- Endereço cadastrado: Via Sérgio Braga, 951, Ponte Alta, Volta Redonda/RJ. A resposta pública identifica via, bairro e cidade, mas **não informa o número 951**; ele é o endereço configurado da unidade, não um campo extraído do JSON.
- Fonte: <https://www.nagumo.com.br>, JSON público do próprio e-commerce.
- `NagumoCollector`, código `nagumo_volta_redonda`, fonte `nagumo_delivery`. Flag: `NAGUMO_COLLECTION_ENABLED`.
- Abre sessão pública; consulta `Stores-AvailableStores`; exige ID, nome, cidade, bairro, endereço e operação; seleciona por `Stores-SelectStore?storeId=36`; confirma a seleção antes e depois do catálogo. Nunca seleciona a primeira loja da cidade.
- Consulta `Search-UpdateGrid?cgid=<categoria>&start=<posição>&sz=50`, sob `/on/demandware.store/Sites-Nagumo-Site/pt_BR/`. Percorre MP-GERAL e 15 departamentos verificados nos links públicos: açougue, básicos e matinais, bazar, bebidas, frios e laticínios, hortifruti, limpeza, padaria, peixaria, pet shop, mercearia doce, mercearia salgada, higiene, jardinagem e orientais. `NAGUMO_ADDITIONAL_CATEGORY_IDS` configura esse escopo. Não afirma cobrir produtos que a fonte não publica.
- A contagem de busca e as páginas podem divergir por filtragem da filial e atualização do índice. A coleta segue `showMoreUrl` até `finished`, sem parar prematuramente numa página curta; detecta páginas repetidas e falhas HTTP. Cada produto observado mantém seu próprio identificador.
- Produtos pesáveis recebem a indicação “preço de 1 kg” somente quando preço por kg × peso médio reproduz o preço da porção declarado pela fonte. A quantidade da lista representa kg nesses itens. Casos sem base de cálculo confirmada são descartados com motivo.
- Preserva ID, nome, marca, categoria, conteúdo descritivo, preço normal, preço promocional e disponibilidade. O total declarado conta disponíveis, mas as páginas também trazem indisponíveis; `foundCount` conta todos os registros recebidos.
- **GTIN não fornecido.** Quando um canônico recebe GTIN do Royal por vínculo revisado, isso não significa que o Nagumo o forneceu.
- Estoque: `available=true/false` vira `AVAILABLE/UNAVAILABLE`; ausência vira `UNKNOWN`.
- Bandeira `NGM_36_M`: preço condicionado a **Meu Nagumo**, preservado separadamente. Não há validade explícita: `validUntil` e `promotionValidUntil` ficam nulos. Não se fabrica prazo diário.
- No recorte inicial: 472 registros, 462 preços válidos, 10 descartados por preço normal ausente/inválido. Esses números são históricos; consulte as execuções administrativas para a coleta atual.

### Royal Retiro

- Rede Royal Supermercados, **Royal Supermercados - Retiro**, Avenida Antônio de Almeida, **1477**, Retiro, Volta Redonda/RJ, CEP 27277-330.
- Identidade: organização **255**, aplicação pública **290**, filial **2**, centro de distribuição **1**, ID VipCommerce **959**, CNPJ **39553144000100**. **Não é Aterrado.**
- Loja oficial: <https://www.royalsupermercados.com.br>. API contratada e utilizada pelo próprio frontend: <https://services.vipcommerce.com.br>. Não é agregador de preços.
- `RoyalCollector`, código `royal_retiro`, fonte `royal_delivery`, referência `royal:store:255:2:1`. Flag: `ROYAL_COLLECTION_ENABLED`.
- Consulta `/api-admin/v1/organizacoes/filiais/dominio/royalsupermercados.com.br`; valida organização, aplicação, nome, operação e domínio técnico `royaleemporio.com.br`.
- O storefront inicia uma **sessão anônima de loja**, sem conta de consumidor: baixa o JavaScript público de configuração e envia os mesmos campos `lojaUser`/`lojaAuthJWT` em `POST /api-admin/v1/org/255/auth/loja/login`. A chave pública é lida a cada coleta, nunca gravada no código. Não usa credenciais de cliente nem contorna proteção.
- Consulta `/api-admin/v1/org/255/filial/2/loja/centros_distribuicoes/retiradas`, exige exatamente o CD 1 e valida nome, CNPJ, IDs, endereço completo e operação. Revalida `/api-admin/v1/org/255/loja/centros_distribuicoes/1` antes e depois do catálogo.
- A seleção é explícita no caminho `/api-admin/v1/org/255/filial/2/centro_distribuicao/1/loja/`. Com `ROYAL_FULL_CATALOG=true` (padrão), lê `classificacoes_mercadologicas/departamentos/arvore` e percorre todas as páginas de cada departamento em `classificacoes_mercadologicas/departamentos/<id>/produtos?page=<n>`.
- Confere `paginator.page`, `total_pages` e `total_items`. O modo limitado por `ROYAL_SEARCH_TERMS` permanece disponível apenas quando `ROYAL_FULL_CATALOG=false`; não há fallback silencioso para o recorte antigo.
- ID estável: **produto_id**, não o identificador da linha de estoque. Descrição pode vir abreviada; marca/categoria nem sempre são declaradas. `codigo_barras` é aceito somente como GTIN válido; códigos internos curtos permanecem sem GTIN. Estoque explícito preservado; ausência = `UNKNOWN`.
- Preço e `oferta` fornecem regular/promocional, tipo e quantidade mínima quando existentes. Condições preservadas. Sem validade comprovada, a promoção não substitui o preço regular na comparação. Unidades fracionárias/alternativas não suportadas são rejeitadas, sem conversão presumida.
- No recorte inicial: 225 registros/preços válidos; 179 disponíveis e 46 indisponíveis. Consulte as execuções administrativas para a coleta atual.
- `ROYAL_PUBLIC_CONFIGURATION_PATH` aponta para o bundle público observado (`/chunk-TLHOSSIT.js`). Um deploy do fornecedor pode alterar esse caminho ou contrato; a coleta falha explicitamente e exige revalidar a configuração pública. Timeouts, intervalos, tentativas e limites são configuráveis. Bloqueios não são contornados.

### Identidade canônica revisada

Os vínculos persistidos continuam em `product_source_references`. [`nagumo-royal.json`](../src/main/resources/product-mappings/nagumo-royal.json) contém **20 pares revisados**, IDs de ambas as fontes, nomes exatos, GTIN fornecido pelo Royal, data, páginas e imagens públicas usadas na revisão de marca, variante, conteúdo e embalagem. Não contém preços nem seeds comerciais.

`VerifiedProductMappings` aceita apenas os IDs/fontes revisados e confere nomes/GTIN. Mudança exige revisão e gera erro; não há fuzzy match. Produtos sem correspondência continuam separados. Dois canônicos já existentes separadamente exigem revisão dos históricos, sem mesclagem automática. O fluxo limpo é Nagumo seguido de Royal. Pares com divergência de peso na imagem ou de variante foram excluídos.

As duas coletas reais produziram 20 canônicos compartilhados; **14 tinham preço utilizável nas duas lojas** naquele momento. Nos outros seis, pelo menos uma fonte informou indisponibilidade.

### Outras investigações

Aterrado não integra este MVP. O encarte oficial Supermarket anteriormente investigado excluía Sul Fluminense; o e-commerce Royal não oferecia Aterrado para retirada. Não se atribuem a essas unidades os preços de Retiro. O fallback não foi necessário: Royal Retiro foi coletado ao vivo.

## Execução automática e manual

A agenda atual (quatro atualizações por dia e uma importação completa semanal) está em [operations.md](operations.md#4-agenda-das-coletas). O cron, fuso, intervalo entre coletores, timeouts, tentativas e intervalo entre requisições estão em `application.properties` e podem ser substituídos pelas variáveis documentadas em `.env.example`. Use `PRICE_COLLECTION_ENABLED=false` para desativar apenas o agendamento ou as flags `NAGUMO_COLLECTION_ENABLED=false` / `ROYAL_COLLECTION_ENABLED=false` para desativar cada coletor.

Um administrador pode iniciar a mesma rotina sem reiniciar a aplicação:

```http
POST /api/v1/admin/collections
Authorization: Bearer <token-administrativo>
```

As execuções podem ser consultadas em `GET /api/v1/admin/collections` e `GET /api/v1/admin/collections/{id}`. Nagumo (`@Order(10)`) roda antes de Royal (`@Order(20)`); uma falha é finalizada como `FAILED` e não impede o próximo coletor.

Contagens: `createdCount` = produtos criados; `updatedCount` / `observationsInsertedCount` = observações inseridas; `skippedCount` = descartes com erro + observações idempotentes; `observationsSkippedCount` = observações idempotentes; `productsUpdatedCount` = cadastros/referências temporais atualizados. `availableCount`, `unavailableCount` e `unknownAvailabilityCount` contam os produtos normalizados com preço válido, não os registros rejeitados pelo parser. Vincular outro ID de fonte ao canônico existente não cria produto. `errorMessage` identifica descartes; falha externa não vira catálogo vazio.

## Adicionar uma fonte real

1. Confirme a existência da fonte, a autorização de acesso e o que seus dados realmente representam. Registre a documentação e a URL da origem. Uma rede conhecida não comprova a existência de uma unidade em determinada cidade.
2. Para uma integração manual, cadastre a fonte por `POST /api/v1/admin/sources`. Um coletor automático pode registrá-la somente depois que a resposta real e a unidade forem validadas, como faz `CollectionCatalogService`.
3. Implemente `SupermarketCollector` em um pacote próprio da integração. Mantenha o protocolo externo, a seleção de filial e o parsing fora do domínio principal.
4. Configure credenciais externamente e defina timeouts de conexão e leitura. Valide a resposta da origem antes de transformá-la em observações. Falhas técnicas devem ser propagadas ou representadas explicitamente como indisponibilidade pelo contrato do provider, sem convertê-las em uma consulta vazia bem-sucedida.
5. Persista primeiro os registros necessários de catálogo, pelos serviços internos correspondentes, e somente depois envie observações de preços a `PriceService.appendObservation`. Não faça chamadas externas dentro da transação de persistência.
6. Execute testes de contrato com respostas controladas e verificadas da fonte, cobrindo falhas, paginação, identidade da loja, duplicação e dados inválidos. Adicione o coletor à rotina existente; não crie um scheduler específico para cada rede.

`ProviderResult` distingue `AVAILABLE` e `UNAVAILABLE`. Uma resposta disponível e vazia significa que a consulta foi concluída sem resultados. Uma resposta indisponível exige uma explicação e não pode conter itens ou cursor.

Não associe produtos automaticamente por semelhança de nome. Um GTIN válido e os identificadores já verificados da fonte permitem identificação conservadora; diferenças de marca, embalagem ou quantidade exigem conferência antes de vincular registros.

## Identidade e rastreabilidade do preço

Cada `PriceObservation` exige produto, loja, fonte, referência da observação, preço regular positivo, moeda `BRL`, data de coleta e disponibilidade declarada. Quando a fonte não informa estoque, use `UNKNOWN`.

`sourceReference` identifica **uma observação imutável dentro da fonte**. Não use apenas a URL de uma página que muda diariamente. Uma fonte que oferece um identificador de evento pode usá-lo diretamente. Para páginas mutáveis, o adapter precisa de uma referência estável da coleta, preservada em tentativas repetidas, vinculada à URL e ao instante realmente observado. Uma coleta nova deve ter uma referência nova. Não gere uma referência aleatória diferente a cada retry.

A combinação `(sourceId, sourceReference)` é única. Repetir o mesmo conteúdo retorna o registro original; repetir a referência com conteúdo diferente gera conflito. Correções devem ser novas observações com sua própria origem e referência, sem sobrescrever o histórico.

Os campos temporais têm significados distintos:

| Campo | Significado |
| --- | --- |
| `collectedAt` | Instante observado pela coleta; não pode estar no futuro. |
| `recordedAt` | Instante em que o backend registrou a observação. |
| `validUntil` | Validade explícita da observação de preço, quando fornecida. |
| `promotionValidUntil` | Fim conhecido da promoção, quando fornecido. |
| `promotionCondition` | Clube, aplicativo, CPF ou outra condição necessária para obter o preço. |

Os timestamps são persistidos em UTC com precisão de microssegundos, compatível com PostgreSQL. A normalização também participa da verificação de idempotência.

`regularPrice` e `promotionalPrice` usam `BigDecimal` com até duas casas decimais. Promoção, quando informada, deve ser positiva e menor que o preço regular. A ingestão não arredonda silenciosamente um valor com precisão inválida.

## Preços atuais e comparação

O endpoint de histórico retorna observações, inclusive preços antigos e promoções encerradas. Ele não afirma que esses valores continuam vigentes.

A comparação seleciona exatamente uma observação por produto e loja, ordenando por `collectedAt DESC`, `recordedAt DESC` e `id DESC`. Uma observação recebida mais tarde, mas coletada antes, não substitui a mais recente. Se a observação escolhida estiver expirada, não há recuo para um preço histórico mais conveniente.

O prazo configurado em `app.prices.max-age` limita a idade aceita do preço; o padrão é `P2D`. Quando `validUntil` informa um prazo menor, prevalece esse prazo. A expiração ocorre no próprio instante limite. O frescor é uma política operacional e não uma garantia de que o estabelecimento ainda praticará aquele preço.

Uma promoção só compõe o preço atual quando `promotionValidUntil` é conhecido, está no futuro e `promotionCondition` não está preenchido. Sem validade ou com uma condição especial, o valor promocional permanece visível no histórico e a comparação usa o valor regular. `promotionApplied` informa a decisão e `expiresAt` informa o limite do preço efetivamente utilizado.

O coletor gera uma referência determinística com coletor, filial, ID externo do produto e instante UTC da coleta normalizado a microssegundos. Reprocessar o mesmo catálogo com o mesmo instante não duplica observações; conteúdo diferente para a mesma referência gera conflito. Uma nova consulta temporal gera outra observação, mesmo no mesmo dia e sem mudança de valor. Não há validade diária inventada. `collectedAt`, `status` e `expiresAt` permitem ao frontend diferenciar preço atual, expirado e ausente; `PRICE_MAX_AGE` controla o limite de stale quando a fonte não fornece prazo menor.

O contrato de `PriceQuote` diferencia:

| `status` | Efeito na comparação |
| --- | --- |
| `KNOWN` | Existe um preço aceito pela política temporal. Estoque ainda pode ser `UNKNOWN`. |
| `NO_OBSERVATION` | Não há observação para o produto/loja; preço e total da linha são `null`. |
| `EXPIRED` | A última observação perdeu validade; preço e total da linha são `null`. |
| `OUT_OF_STOCK` | A última observação válida informa indisponibilidade; a linha não compõe o subtotal. |

Ausência ou expiração de preço não afirmam falta de estoque. Nesses casos, a disponibilidade atual é `UNKNOWN`; a disponibilidade originalmente observada continua visível nos metadados históricos.

Nas listas, cada preço conhecido é multiplicado pela quantidade solicitada. O valor de cada linha é arredondado para centavos com `HALF_UP`, e o subtotal soma as linhas já arredondadas. `requestedItems`, `pricedItems` e `missingItems` contam linhas de produtos distintos, não a soma das quantidades.

`subtotalKnown` é `null` quando nenhuma linha tem preço. `completeShoppingList` indica que todas as linhas de uma lista não vazia possuem preços utilizáveis; não garante estoque quando a origem o declara desconhecido. Uma lista vazia tem subtotal `null` e nunca é marcada completa.

As lojas da comparação detalhada são paginadas por nome e ID, com até 100 lojas por requisição. Todos os preços da página são consultados em um único lote, limitado às lojas da página e aos produtos da lista.

A recomendação de compra usa um endpoint separado e consulta em lote todas as lojas elegíveis da cidade. Se a quantidade ultrapassar `RECOMMENDATION_MAX_STORES`, a API retorna 422 em vez de declarar uma vencedora parcial. Somente uma loja com preço utilizável para todos os itens pode ser recomendada; candidatos incompletos são apresentados separadamente como cobertura, nunca como vencedor.

## Catálogo por loja e compra dividida

`GET /api/v1/stores/{id}/products?query=...&page=0&size=30` consulta somente produtos com observações naquela loja. A paginação ocorre no banco; um `EXISTS` evita duplicar produtos com vários registros históricos. Os preços da página são carregados em lote e passam pela mesma `PricePolicy` das comparações. O catálogo inclui itens indisponíveis ou desatualizados, identificados pelo status.

A busca geral e por loja aceita texto sem acentos. As migrations V13 e V14 criam índices de ordenação e trigramas para as colunas pesquisadas. O limite por página continua 100; não existe um limite global de produtos da lista de seleção. O frontend solicita 30 resultados por página e permite pesquisar o catálogo inteiro.

O campo `combination` da recomendação escolhe a menor linha válida de cada produto, já multiplicada pela quantidade e arredondada. Agrupa os itens por loja e informa os produtos ausentes. Somente uma combinação completa pode ter `savingsAgainstCompleteStore`, calculada contra a loja mais barata com a mesma lista completa. Não se compara subtotal parcial com total completo; frete e deslocamento não entram no cálculo. Empates favorecem a loja vencedora completa para evitar divisões sem economia.

É possível repetir uma única fonte por `POST /api/v1/admin/collections?collectorCode=nagumo_volta_redonda` ou `royal_retiro`. O bloqueio de coleta simultânea continua valendo; código desconhecido retorna 404. Sem parâmetro, ambas são executadas sequencialmente.

## Contribuições de usuários

Uma contribuição aprovada também precisa de uma origem rastreável. Cadastre uma fonte administrativa que represente a própria plataforma, usando a URL real do ambiente, e configure seu UUID em `CONTRIBUTION_SOURCE_ID`. Não crie uma fonte fictícia em migration.

A aprovação publica a observação por `PriceService`, com `originType=USER_CONTRIBUTION`, uma referência determinística derivada do UUID da contribuição e vínculo único com o preço persistido. Repetir a aprovação não cria uma nova observação. Contribuições pendentes ou rejeitadas nunca chegam a `price_records` e, portanto, não participam das comparações ou alertas.
