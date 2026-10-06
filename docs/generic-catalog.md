# Catálogo genérico

O usuário escolhe **"Coca-Cola 2 L"**, não o anúncio de um mercado. Cada item do catálogo genérico agrupa os anúncios equivalentes de todas as lojas, e as listas e comparações usam esse grupo.

## Como os itens são formados

`CatalogKey` reduz a descrição de cada anúncio à identidade do produto:

| Anúncio do mercado | Chave |
| --- | --- |
| `Refr. Coca-cola 2lt Pet` | `COCA COLA\|1x2000ML` |
| `REF COCA COLA 2L` | `COCA COLA\|1x2000ML` |
| `Coca-Cola Sem Açúcar 2L` | `COCA COLA ZERO\|1x2000ML` |
| `ENERG MONSTER MANGO LOCO 473ML` | `LOCO MANGO MONSTER\|1x473ML` |

- **Removidas:** palavras genéricas e de embalagem (refrigerante, bebida, energético, lata, pet, gelado, original, tradicional) e abreviações.
- **Mantidos:** marca, variante (Zero, sabor) e tamanho exato, já convertido para ml/g e quantidade de unidades.
- **Descartados:** kits, combos, "leve X pague Y" e descrições com mais de um tamanho.
- **Marca obrigatória:** a chave precisa conter uma marca conhecida, aprendida dos campos de marca publicados pelos mercados, ou uma palavra rara na base inteira. Sem isso, dois produtos genéricos de lojas diferentes poderiam ser fundidos por engano.

- **Abreviações de ERP:** `IOG`, `REQ`, `MARG`, `AMAC`, `DESOD`, `ACHOC`, `TRAD`, `SB` (sabão), `SC` (sachê) e outras viram a palavra completa. "Torrado e moído" é "em pó".
- **Variante padrão:** "Tipo 1" (arroz e feijão) e "sem gás" (água) podem ser omitidos; "Tipo 2" e "com gás" são outro produto.
- **Tamanho repetido:** "200M 0.2 lt" e "600 Grama(s)" são lidos como um tamanho só. Marcas com número ("3 Corações") são reconhecidas.

### Descrições diferentes do mesmo produto

`CatalogMatcher` une chaves diferentes que descrevem o mesmo produto, sempre com o **mesmo tamanho**:

| Regra | Exemplo |
| --- | --- |
| Mesma marca e diferença só em palavras de forma ou tipo (pó, líquido, biscoito, cremoso, congelado, suco/refresco…) | `Gelatina em Pó Sol Abacaxi 20g` = `Gelatina Sol 20g Abacaxi` |
| Palavra cortada no limite de 30 caracteres das lojas VIP (Bramil, Pérola, Royal, Spani) | `Acucar Granulado Uniao 1kg Pre` = `Açúcar Granulado União Premium 1kg` |
| Mesmo SKU descrito de outro jeito por outra loja (a descrição mais recente de cada loja, guardada em cada preço) | `Leite Po Ninho 380g Integral I` liga `Leite em Pó Ninho Integral Instantâneo 380g` |

- **Nunca une:** sabor, ingrediente ou variante diferentes (zero, light, chocolate, milho), nem embalagens diferentes (sachê/refil, pote, squeeze), que têm preços diferentes.
- **Trava:** se uma mesma loja vende SKUs nas duas descrições, elas são produtos diferentes e não são unidas. Assim, uma união feita aqui nunca coloca dois SKUs da mesma loja no mesmo item.

`CatalogBuilder` agrupa pelo resultado e publica somente os itens vendidos em pelo menos `CATALOG_MIN_STORES` mercados (padrão 2). Em 02/10/2026 eram cerca de 9,3 mil itens (8,5 mil antes destas regras). Os demais anúncios continuam no banco, com o histórico, mas não aparecem na busca.

## Quando o catálogo é reconstruído

- Ao iniciar a aplicação, quando `CATALOG_REBUILD_ON_START=true` (padrão).
- Ao fim de cada coleta, por meio de `CollectionCompletedListener`.
- Manualmente, por um administrador: `POST /api/v1/admin/catalog/rebuild`.

O ID de cada item é estável por chave. Um item que deixa de cumprir o mínimo de mercados é desativado, nunca apagado, para não quebrar as listas. Quando uma regra nova muda a chave de um item, o item antigo é desativado e os itens de lista que o salvaram passam para o item que agora contém o mesmo SKU.

## Atualização dos preços

- **Coleta agendada:** quatro vezes ao dia (`PRICE_COLLECTION_CRON`, padrão 06h, 11h, 16h e 21h).
- **Coleta ao iniciar:** os coletores sem execução concluída nas últimas `PRICE_REFRESH_WHEN_OLDER_THAN` (padrão 8 h) são atualizados em segundo plano, e os que estão em dia são pulados. Assim, uma máquina que ficou desligada não passa o dia exibindo preços vencidos.
- **Coleta em paralelo:** os coletores baixam ao mesmo tempo (`PRICE_COLLECTION_PARALLELISM`, padrão 3), pois cada um consulta o site do próprio mercado. A gravação no banco continua sequencial.
- **Páginas maiores na Nagumo:** a loja responde 200 itens por página no mesmo tempo que 50 (`NAGUMO_PAGE_SIZE`, padrão 200), o que reduz as requisições em quatro vezes.
- **Validade dos preços:** continua definida por `PRICE_MAX_AGE` (2 dias). Preço vencido nunca é exibido como atual.
- **Mercados sem preços:** um mercado que nunca publicou preço não entra na comparação nem na lista de mercados.

## O que a pessoa vê

- **Busca:** só retorna itens com preço atual em pelo menos um mercado da cidade. Se a busca informar um tamanho ("coca 1 litro"), só aparecem itens desse tamanho, salvo quando nenhum mercado o vende.
- **Comparação de um item** (`/app/comparar/:id`): lista apenas os mercados com preço atual, do mais barato ao mais caro. Os demais aparecem somente como contagem.
- **Comparação da lista:** cada mercado mostra apenas os itens com preço atual, e os faltantes são nomeados numa linha. Mercados sem nenhum preço atual não aparecem.

## API

- `GET /api/v1/catalog/items?query=coca 2 litros&cityId=…`: busca por palavras (prefixo e grafia aproximada), priorizando o tamanho digitado. Retorna o menor e o maior preço atuais e o mercado mais barato.
- `GET /api/v1/catalog/items/{id}?cityId=…`: apenas os mercados com preço atual (`offers`), com o nome que cada loja usa, e a quantidade de mercados sem preço atual (`storesWithoutPrice`).
- `POST /api/v1/shopping-lists/{id}/items` com `{ "catalogItemId": "…", "quantity": 2 }`. Um `productId` que pertença a um item também é gravado como o item genérico.
