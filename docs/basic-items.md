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

1. **Taxonomia de itens básicos** (`basicitem/BasicItems.java`): 37 itens com nome, setor, unidade (kg, L, un),
   tamanho de referência e regras de inclusão/exclusão (o pão de hambúrguer não pega "sem glúten"; o acém não pega
   "moído" nem "pescoço", que viram outros itens).
2. **Tamanho da embalagem** (`PackageSize`): lê "420g", "1,05kg", "(preço de 1 kg)", "C/12", "dúzia", os rolos do
   papel higiênico ("L12P11", "Pague 15 Leve 16", "4x30m", "12 rolos") e, sem tamanho
   no nome, a quantidade declarada pela loja. Sem tamanho nenhum, o produto entra, mas aparece como
   "tamanho não informado" e vai para o fim.
3. **Ligação depois de cada coleta** (`BasicItemLinker`, tabela `basic_item_products`, V56).
4. **Escolha por mercado** (`BasicItemOffers`): entre as embalagens com preço atual e em estoque, a mais barata por
   kg/L/un, preferindo as que têm de 0,5× a 1,5× o tamanho de referência. Isso evita que o total de uma lista seja
   inflado por um pacote de 2 kg num mercado e de 500 g em outro.
5. **Por que falta** (`StoreFreshnessService`): cada mercado sem preço aparece com o motivo — não vende no site na
   última leitura, vende mas está em falta no site, preços desatualizados, ou mercado sem loja online ainda não
   conferido.

## Onde aparece

- **Busca** e **seletor de produtos**: um bloco "Qualquer marca" acima dos produtos exatos.
- **Página `/app/basicos/:slug`**: o mais barato de cada mercado por kg, com o produto escolhido, o preço da
  embalagem e os mercados sem preço com o motivo.
- **Produto exato**: se o básico está em mais mercados, um aviso leva para "Comparar marcas".
- **Lista de compras**: o item pode ser "qualquer marca" (`shopping_list_items.basic_item`); a comparação, a
  recomendação e o modo "No mercado" usam a opção escolhida em cada mercado. Um produto exato que é um básico
  ("Pão de Hambúrguer Seven Boys 300g") mostra "Trocar por qualquer marca", que mantém a quantidade.
- **Admin → Cesta básica**: matriz item × mercado. Um buraco costuma ser um setor que o coletor não lê.

## Resultado no snapshot local (Volta Redonda)

| Item | Antes (produto exato) | Depois (qualquer marca) |
| --- | --- | --- |
| Pão de hambúrguer | 3 de 9 | **9 de 9** |
| Patinho | 3 de 9 | 5 de 9 |
| Acém | 3 de 9 | 3 de 9 (os outros 6 dizem "não vende no site") |
| Arroz, feijão, açúcar, óleo, leite, macarrão, margarina | — | 9 de 9 |

## Revisão com dados de produção (20/11/2026)

Os 30 itens foram conferidos contra os ~88 mil produtos com preço em produção. Os agrupamentos errados mais
graves eram de hortifrúti e mercearia: batata juntava batata congelada e chips, tomate juntava tomate pelado,
cebola juntava cebola caramelizada e granulada, manteiga juntava manteiga capilar, vegana e sem sal, ovos brancos
juntavam vermelhos e caipiras, arroz juntava parboilizado, arbório e preto, e café juntava solúvel, descafeinado e
drip. Num mercado sem batata in natura, a batata frita congelada virava "a batata" dele.

Entraram 7 itens: muçarela, presunto cozido, iogurte natural, refrigerante 2 L (sem zero/diet), detergente
líquido, sabão em pó e papel higiênico folha dupla (preço por rolo).

**Indisponíveis.** Em produção, 45% dos últimos preços vinham como `UNAVAILABLE` (Nagumo 79%, Bramil 50%). A
conferência ao vivo mostrou que o dado está certo: os sites listam o sortimento da rede inteira e marcam o que falta
na loja (VipCommerce `disponivel=false` com `quantidade_maxima` 0; Nagumo `available=false`, "Não disponível"). O que
estava errado era a explicação: um mercado com o item em falta aparecia como "não vende no site". Agora aparece
como "em falta no site".

O teste `BasicItemsTest` guarda 75 nomes reais dos mercados que precisam cair no item certo e 76 parecidos que não
podem (pipoca de manteiga, ovo de Páscoa, Mr. Músculo, batata congelada, café solúvel...). Ao mexer numa regra,
rode-o primeiro.

## Nomes de cada mercado e tamanho em promoção (21/11/2026)

A cobertura de produção (12 mercados com loja online) mostrou itens que existiam no mercado, mas com outro nome:

| Item | Antes | Depois | Nome que faltava |
| --- | --- | --- | --- |
| Sabão em pó | 5 | **12** | "Lava Roupa Omo 1,6kg", "Lava-Roupas Em Pó Surf 800G" (líquidos continuam fora) |
| Muçarela | 10 | **12** | "Qjo Mussarela" (Spani) |
| Detergente | 11 | **12** | "Deterg. Ype 500ml" (Royal) |
| Feijão carioca | 11 | **12** | "FEIJAO GRANFINO CARI 1kg" (o Ville corta o nome em 20 letras) |
| Sal refinado | 11 | **12** | "SAL CISNE 1KG" (sem a palavra "refinado") |
| Pão francês | 5 | 6 | "Pão De Sal" (Royal) |

O tamanho da embalagem também ignorava a promoção: "Grátis 200G Omo 800G" virava 200 g (o preço por kg ficava 4×
maior) e "Leve 500 Pague 450G" virava 450 g. Agora vale o que a pessoa leva: o brinde ("grátis", "gts") e o
"pague" só contam quando não há outro tamanho no nome.

**Carnes.** Acém e patinho estão em 6 dos 12 mercados, e a regra já pega todos os nomes. Os outros mercados de fato
não vendem esses cortes no site: o Atacadão não tem carne fresca online, e Pame e Pérola vendem outros cortes. O
caminho para esses buracos é o preço conferido na loja, não a regra.
