# Comparação por identidade de produto

## Comportamento

O ID escolhido identifica o cadastro de partida, não restringe a consulta ao seu mercado. Pesquisa de ofertas, detalhes, comparação de listas e recomendação usam `ProductEquivalenceService` e `EquivalentPriceService`.

- A identidade de comparação considera nome, marca declarada ou reconhecida no nome, variante, conteúdo normalizado e apresentação. Litros/mililitros e quilos/gramas são convertidos. Acentos, pontuação, ordem dos termos e abreviações contempladas não impedem equivalências.
- O GTIN continua vinculando os cadastros já identificados pela ingestão. A comparação também funciona entre IDs e GTINs diferentes quando os atributos confirmam a identidade; não funde entidades nem transfere históricos.
- Original e Zero, volumes distintos, sabores e contagens de embalagem distintas permanecem separados. Kits mistos, descrições genéricas sem marca confirmada e variantes não informadas não são equivalências automáticas.
- Embalagens não informadas não podem criar uma ligação entre apresentações conhecidas incompatíveis. Correspondências incompletas aparecem separadas e não compõem preços, totais ou recomendações.
- Cada observação mantém o produto e mercado de origem, referência da observação, código local do produto, fonte, URL quando fornecida, preço e data. A migração de referências históricas só preenche códigos quando existe uma única referência possível na fonte.
- Para cadastros equivalentes na mesma loja, a coleta mais recente prevalece sobre duplicatas antigas, inclusive quando informa indisponibilidade. Em observações da mesma coleta, desempata pelo menor preço comparável. Descontos condicionados a clube/quantidade não vencem pelo valor condicionado. Preços expirados continuam sendo históricos.
- `sourceProductName` preserva a descrição original da oferta, independentemente do nome do cadastro compartilhado. O preenchimento de observações antigas usa apenas o arquivo daquela coleta e mantém seu preço e data; não inventa descrições históricas.
- `comparison-attributes.json` registra evidências públicas revisadas para atributos omitidos, com GTIN, fontes e data. Não fornece preços, não sobrescreve variantes conflitantes e não cria produtos.
- A lista mantém suas linhas e quantidades, mas consulta os equivalentes de cada linha em todos os mercados selecionados. O cálculo de cobertura e a exigência de cesta completa para recomendar um único mercado foram preservados.

## Consulta completa e filtros

`V24` cria um índice por família de nomes. A chave é calculada na ingestão e preenchida para o catálogo existente antes de a aplicação começar a servir requisições. Essa atualização não altera datas de coleta, preços, IDs, referências ou versões comerciais dos produtos. A busca de candidatos da família não possui limite de página.

`storeIds` é opcional nos endpoints de comparação de produtos, listas, recomendação e ofertas em lote. Ausente/vazio significa todos. IDs inexistentes, inativos ou de outra cidade geram erro; não são silenciosamente descartados. O filtro é aplicado antes da paginação. A interface percorre todas as páginas de mercados na comparação de produto e na seleção de lojas. A recomendação da lista continua avaliando todas as lojas elegíveis, independentemente da página de resultados exibida.

O filtro antigo de catálogo passou a se chamar **Catálogo de origem**. Ele encontra cadastros daquela origem; a seleção **Mercados da comparação** controla quais ofertas comparar. Escolher um cadastro de uma origem não impõe essa origem à comparação.

`GET /api/v1/products/discovery` reúne os candidatos relevantes completos, confirma os grupos e busca suas ofertas antes de paginar. A pesquisa e os seletores de produto usam esse endpoint. O card conta lojas distintas com preço atual, prioriza a medida pesquisada e apresenta ofertas disponíveis antes de cadastros sem preço. A busca textual pode sugerir outros tamanhos, claramente separados.

O índice atual também reconhece abreviações de limpeza e alimentos e evita usar o distribuidor como marca quando outra marca declarada aparece no próprio nome. Similaridade encontra candidatos de famílias próximas; somente os atributos confirmados autorizam sua inclusão no total. Pacotes sem contagem confirmada não recebem cálculo de preço por litro/quilo.

Para atualizar apenas o catálogo existente, use a coleta com `existingOnly=true` ou o comando operacional `refresh:<coletor>`. `replay-existing:<coletor>` reaplica o arquivo daquela coleta, sem renovar sua data. Referências desconhecidas são ignoradas antes da ingestão. Reiniciar normalmente não inicia downloads.

A coleta diária usa esse mesmo modo por padrão (`PRICE_COLLECTION_EXISTING_ONLY=true`). O operador pode desativá-lo explicitamente quando quiser importar novos produtos; esta tarefa manteve a atualização restrita aos existentes.

## Interface e revisão

O dashboard, o menu lateral e os tokens visuais existentes foram preservados. A composição reutiliza `ProductPicker`, `StoreOffers`, `PriceDetails` e `ComparisonItems`, acrescentando `ComparisonStoreFilter` e `PossibleProductMatches`. A referência de componentes consultada foi a [categoria Comparison do 21st.dev](https://21st.dev/community/components/s/comparison); nenhum código externo ou nova biblioteca visual foi incorporado.

A hierarquia é produto/cidade → escopo de mercados → ofertas e menor preço comparável → lacunas e possíveis correspondências. Carregamento, falha recuperável, nenhuma loja, falta de observação, histórico vencido e promoção condicionada têm estados distintos. Os controles mantêm rótulos, agrupamento semântico e uso por teclado; as colunas se empilham no celular.

Riscos revisados: agrupamento indevido (atributos e confirmação), melhor preço limitado à primeira página (paginação completa), mistura de desconto condicionado (política mantida), falsa economia de cesta incompleta (cobertura explícita) e falsa indisponibilidade (ausência de observação distinta de estoque).

## Verificação em 20/09/2026

- Maven Wrapper `verify`: 269 testes, zero falhas/erros/ignorados. A execução final usou uma cópia isolada de `src` e `pom.xml` em `.local/validation`, para não disputar `target/classes` com o compilador da IDE, e o banco exclusivo `gomo_comparison_tests` com esquemas descartáveis. Comando: `mvnw.cmd -q -f .local/validation/pom.xml -Dspring.test.context.cache.maxSize=3 verify`, pool de teste limitado a quatro conexões.
- Cenários: códigos locais diferentes, descrições/unidades equivalentes, variantes/volumes/pacotes incompatíveis, clique seguido de expansão para outras lojas, filtro exato, lista de origens distintas, lacunas sem vencedor incompleto, promoção condicionada e candidato além dos primeiros 100 resultados.
- O preenchimento do índice é testado preservando datas e versões. O cadastro do Supermarket é testado sem observações de preço.
- Dados sintéticos ficam apenas nos testes transacionais, em esquema PostgreSQL isolado.
- A validação atual com “coca cola 1 litro”, limpeza, alimentos, filtros e lista de origens distintas está detalhada em [Auditoria de Volta Redonda](volta-redonda-comparison-audit.md).
- Frontend: `npm run lint` e `npm run build` concluídos. Os avisos de anotação PURE vêm do Zod instalado, sem erro de compilação.

## Limites

A equivalência é conservadora e determinística, não uma promessa de identificar todas as abreviações de todas as fontes. Quando a informação não confirma marca, variante ou apresentação, o cadastro permanece separado. Preços ausentes ou vencidos não são preenchidos por estimativas. Uma loja com cadastro confirmado não implica catálogo de preços integrado.
