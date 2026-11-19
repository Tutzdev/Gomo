# Itens básicos ("qualquer marca")

## O problema

Em 17/11/2026, comparando **acém, patinho e pão de hambúrguer** em Volta Redonda, os três apareciam em só **3 dos 9
mercados**. O público lê isso como "o Gomo não tem nada desses mercados" e vai embora.

O diagnóstico com os dados reais (snapshot local de 26/10/2026, 9 mercados de Volta Redonda) mostrou duas causas
diferentes:

| Item | Mercados com o produto coletado | Causa |
| --- | --- | --- |
| Pão de hambúrguer | **9 de 9** | O catálogo agrupa só o produto exato (nome + marca + tamanho). "Wickbold 200g" e "Plusvita 420g" são itens diferentes, então comparar um deles achava 3 mercados. |
| Acém | 4 de 9 | Atacadão, Pame, Pérola, Royal (sem estoque), Ville e Hortifruti não vendem esse corte no site. |
| Patinho | 5 de 9 | Idem. E itens sem tamanho no nome ("Patinho Extra Limpo", Hortifruti) nem entravam no catálogo. |

Ou seja: o pão estava coletado e não era agrupado; as carnes de fato não estão à venda online em vários mercados.

## A solução

1. **Taxonomia de itens básicos** (`basicitem/BasicItems.java`): 30 itens com nome, setor, unidade (kg, L, un),
   tamanho de referência e regras de inclusão/exclusão (o pão de hambúrguer não pega "sem glúten"; o acém não pega
   "moído" nem "pescoço", que viram outros itens).
2. **Tamanho da embalagem** (`PackageSize`): lê "420g", "1,05kg", "(preço de 1 kg)", "C/12", "dúzia" e, sem tamanho
   no nome, a quantidade declarada pela loja. Sem tamanho nenhum, o produto entra, mas aparece como
   "tamanho não informado" e vai para o fim.
3. **Ligação depois de cada coleta** (`BasicItemLinker`, tabela `basic_item_products`, V56).
4. **Escolha por mercado** (`BasicItemOffers`): entre as embalagens com preço atual e em estoque, a mais barata por
   kg/L/un, preferindo as que têm de 0,5× a 1,5× o tamanho de referência. Isso evita que o total de uma lista seja
   inflado por um pacote de 2 kg num mercado e de 500 g em outro.
5. **Por que falta** (`StoreFreshnessService`): cada mercado sem preço aparece com o motivo — não vende no site na
   última leitura, preços desatualizados, ou mercado sem loja online ainda não conferido.

## Onde aparece

- **Busca** e **seletor de produtos**: um bloco "Qualquer marca" acima dos produtos exatos.
- **Página `/app/basicos/:slug`**: o mais barato de cada mercado por kg, com o produto escolhido, o preço da
  embalagem e os mercados sem preço com o motivo.
- **Produto exato**: se o básico está em mais mercados, um aviso leva para "Comparar marcas".
- **Lista de compras**: o item pode ser "qualquer marca" (`shopping_list_items.basic_item`); a comparação, a
  recomendação e o modo "No mercado" usam a opção escolhida em cada mercado.
- **Admin → Cesta básica**: matriz item × mercado. Um buraco costuma ser um setor que o coletor não lê.

## Resultado no snapshot local (Volta Redonda)

| Item | Antes (produto exato) | Depois (qualquer marca) |
| --- | --- | --- |
| Pão de hambúrguer | 3 de 9 | **9 de 9** |
| Patinho | 3 de 9 | 5 de 9 |
| Acém | 3 de 9 | 3 de 9 (os outros 6 dizem "não vende no site") |
| Arroz, feijão, açúcar, óleo, leite, macarrão, margarina | — | 9 de 9 |

O teste `BasicItemsTest` guarda 46 nomes reais dos mercados que precisam cair no item certo e 26 parecidos que não
podem (pipoca de manteiga, ovo de Páscoa, Mr. Músculo...). Ao mexer numa regra, rode-o primeiro.
