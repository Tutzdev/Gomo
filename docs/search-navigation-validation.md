# Busca, seleção de produtos e navegação

Validação local em 18/09/2026. Banco persistente `gomo_acceptance`, catálogo com 16.122 produtos reais. Nenhum produto ou preço demonstrativo foi inserido na aplicação. As fixtures dos testes rodam em schemas isolados.

## Resultado

- Um único `ProductPicker` substitui selects de produtos em comparação, listas, alertas e contribuições. A página Produtos também usa o componente para localizar e abrir um produto.
- Busca com debounce de 300 ms, cancelamento de requisições, oito resultados por página, teclado, nomes legíveis, embalagem, destaque dos termos e estados inicial/carregando/vazio/erro/repetir.
- Filtros opcionais de marca, categoria, tamanho e supermercado extraídos do catálogo correspondente à consulta. Ordenação por relevância, A–Z e preço atual.
- Identificadores internos continuam na API e no banco; não aparecem nas opções, na grade do catálogo ou nos detalhes comuns do produto.
- Listas mantêm quantidade editável, remoção e persistência. Produtos já adicionados ficam indisponíveis para nova seleção, com indicação “Na lista”. A inclusão devolve foco à busca e anuncia o produto adicionado.
- Dashboard original preservado: barra lateral vermelha com recolhimento, cabeçalho com nome e assinatura e navegação original. No mobile, a mesma barra lateral abre em Sheet, com retorno explícito de foco ao fechar. A navegação horizontal foi removida após esclarecimento do usuário; as melhorias ficam na busca de produtos.

## API e busca

`GET /api/v1/products` foi evoluído sem remover os parâmetros ou o formato paginado existentes. Parâmetros: `query`, `brand`, `gtin`, `category`, `storeId`, `unit`, `quantity`, `sort`, `page`, `size`. Valores de `sort`: `relevance`, `name`, `price_asc`, `price_desc`. O limite máximo de página continua sendo 100; o autocomplete pede oito.

Novo `GET /api/v1/products/filters?query=...`: marcas, categorias, pares unidade/quantidade e mercados presentes nos resultados, até 40 opções por grupo. Não é uma lista fixa de marcas ou produtos.

`GET /api/v1/stores/{id}/products` reutiliza a mesma busca, com restrição de loja. Não foi criado um mecanismo de busca separado por tela.

`ProductSearchRepository` consulta o PostgreSQL com parâmetros vinculados, ordenação validada e paginação no banco. Novos produtos ingeridos entram automaticamente nas colunas de busca geradas. Não há download do catálogo inteiro para o navegador.

### Normalização, aliases e unidades

As funções SQL `gomo_search_text` e `gomo_product_search_text` concentram a normalização de acentos, caixa, pontuação, hífens, separação entre números e letras, plurais selecionados e aliases. Exemplos: `cocacola`, `ipe/ypê`, `refri/refr.`, `deterg./detergentes`, `carne de boi/bovina/bovino`. Alterações dessa configuração são versionadas pelo Flyway e recalculam os campos derivados.

Aliases de cortes bovinos são aplicados somente quando o texto ou a categoria da fonte indica carne/açougue; produtos identificados como suínos, aves, cordeiro ou vegetais não recebem essa expansão. Essas regras não alteram a identidade comercial dos produtos.

`ProductSearchTerms` reconhece uma medida explícita e converte apenas dentro da mesma dimensão: L para ML, KG para G. Por exemplo, 1 litro = 1000 ml e 0,5 kg = 500 g. Uma consulta com múltiplas medidas mantém os termos sem escolher arbitrariamente um tamanho de kit.

### Relevância e erros pequenos

Os termos são pesquisados independentemente da ordem. Correspondências exatas, marca, início do nome e tamanho solicitado têm prioridade. Kits, combos e bebidas mistas não passam à frente do produto simples em consultas sem esses termos. Empates usam nome normalizado e ID, garantindo páginas estáveis.

Trigramas (`pg_trgm`) restringem candidatos aproximados; `levenshtein_less_equal` (`fuzzystrmatch`) aceita no máximo uma inserção, remoção ou substituição por palavra. A busca com múltiplos termos permite no máximo um termo aproximado. Isso permite “coca coala”, sem aceitar “lagartinho” como “patinho”. Descritores genéricos “carne” e “refrigerante” são sinais opcionais quando acompanhados de um nome mais específico, porque algumas fontes os omitem.

**Busca só pelo tipo (21/11/2026).** No catálogo genérico, quem digitava só "refrigerante", "refri" ou "energético" recebia o catálogo inteiro (açúcar, óleo, maionese), porque essas palavras saem da chave do produto e a busca ficava sem termo. Agora o `search_text` de cada item guarda o tipo que algum mercado usa no nome (refrigerante, energético, repositor, bebida), e a busca usa o tipo só quando ele é tudo o que a pessoa digitou. "Refrigerante coca cola" continua procurando só coca cola.

**Desempenho.** Cada termo vira `search_text LIKE '%TERMO%' AND ' ' || search_text LIKE '% TERMO%'`: a primeira parte usa o índice trigram, a segunda garante o início de palavra. A contagem de mercados com preço é feita só para os itens que casam com a busca. Em produção: busca aproximada ("macarao") 235 → 16 ms no catálogo e contagem de preços 133 → 31 ms.

O preço para ordenação é o menor preço atual do produto nas lojas consultadas. A observação mais nova decide disponibilidade; registros vencidos, futuros ou sem estoque não entram. Promoções condicionais ou sem validade confirmada não entram. Ausência de preço vai ao final tanto na ordem crescente quanto na decrescente.

## Comparação e dados preservados

Revisados `ShoppingComparisonService`, `ShoppingPriceCalculator`, `PricePolicy`, histórico por produto/loja e proteção contra itens duplicados. A busca flexível não mescla produtos nem aplica equivalência automática entre marcas, embalagens ou IDs de fontes. Preços continuam ligados ao produto canônico validado por GTIN/referências verificadas.

Teste pelo navegador na lista `Compra - busca e comparacao` (`a91685b2-dab5-4314-afbf-52c840ec06a8`):

- Coca Cola Original 1L, quantidade 3: R$ 7,29 por unidade, R$ 21,87.
- Carne Patinho Bife, preço de 1 kg, quantidade 1: R$ 54,98.
- Nagumo: cobertura 2/2, total R$ 76,85 e recomendação completa.
- Royal: sem preços atuais desses IDs; cobertura 0/2, sem total inventado.

A lista foi criada para exercitar o fluxo e ficou disponível na conta. Os valores são das observações coletadas em 17/09/2026, não uma promessa de preço futuro.

## Arquivos principais

Backend alterado: `product/ProductController.java`, `ProductService.java`, `ProductSearch.java`.

Backend criado: `product/ProductSearchRepository.java`, `ProductSearchTerms.java`, `ProductSearchFacets.java`; migrações V15, V16 e V17; `product/ProductSearchPersistenceTests.java`.

Frontend reutilizado/refatorado: `features/catalog/product-picker.tsx`, `components/layout/app-shell.tsx`, `components/ui/sheet.tsx`, `SelectField`, `NativeButton`, `BrandLogo`, paginação e feedback existentes. Atualizados tipos/API e páginas de comparação, listas, produtos, detalhes, catálogo da loja, alertas e contribuições.

Frontend criado: `features/catalog/product-search-filters.tsx`, `product-label.ts`, `highlighted-name.tsx`. O dashboard reutiliza `DashboardSidebar` original. O componente de navegação horizontal e suas dependências NavigationMenu/Accordion foram removidos. O Sheet continua usando o Dialog já instalado. Sem biblioteca de animação adicional.

## Verificação executada

- `mvnw.cmd verify`: **207 testes, zero falhas, zero erros, zero ignorados**. Inclui 31 casos novos de busca, ranking, aliases, medidas, filtros, paginação, dados recém-ingeridos e preços válidos/indisponíveis.
- `npm run lint`: sem erros ou avisos de lint.
- `npm run build`: TypeScript e build de produção aprovados. Permanecem avisos de anotações de tree shaking no Zod, já presentes na dependência.
- Flyway V15–V17 aplicado e validado no banco persistente.
- Playwright: seleção com setas/Enter/Escape; filtros reais; consultas rápidas; falha de rede e recuperação; vazio; adição, proteção contra duplicata, quantidade, remoção e recarga; comparação real; seleção em alertas/contribuições; abertura de detalhes pelo catálogo.
- Busca verificada em 390×844, 768×1024 e 1280×800; sem rolagem horizontal da página. Após restaurar o dashboard, nova conferência da barra lateral e busca em desktop e do menu lateral em Sheet no celular. Lint e build executados novamente com sucesso.
- `EXPLAIN (ANALYZE, BUFFERS)` da busca por patinho confirmou `Bitmap Index Scan` em `idx_products_search_text`: execução de 0,811 ms nessa consulta isolada e nessa base. Isso não representa toda a latência HTTP nem uma medição de 100 mil produtos.
- API local medida em consultas representativas do catálogo: aproximadamente 18–178 ms antes da última expansão de abreviações. Sem compromisso de latência fora deste ambiente.

Capturas e roteiros locais de interação ficam em `output/playwright/` e `.local/`, ignorados pelo Git. Testes Java ficam versionados no repositório.

## Limites explícitos

- Não foram inventadas imagens, classificação de produtos ausentes, popularidade ou disponibilidade. Sem imagem real validada, usa-se ícone discreto.
- Fuzzy search é conservador, limitado a um erro por palavra; não é correção linguística irrestrita. Aliases e plurais são um conjunto pequeno, centralizado e versionado.
- Não há campo confiável de variante/tipo para todo o catálogo. Refinamento de variante é feito pelos termos da busca; filtros estruturados usam somente campos existentes.
- A precisão de embalagem e categoria depende da fonte. Dados não confirmados não são completados por suposição. Identidades diferentes sem prova não são fundidas para fabricar cobertura entre mercados.
- Busca ampla continua paginada; grupos de filtros são limitados a 40 opções. Consultas mais específicas refinam essas opções.
- A arquitetura não carrega 100 mil produtos no navegador, mas não foi executado ensaio de carga de 100 mil produtos nesta entrega.
- Semântica ARIA e navegação por teclado verificadas no navegador; não foi executada sessão com NVDA/VoiceOver ou teclado virtual de aparelho físico.

## Referências de interface

O dashboard mantém a estrutura original do repositório, conforme esclarecimento do usuário. As referências de navegação horizontal pesquisadas anteriormente não fazem parte da implementação final.

Referência do seletor de produtos: [WAI-ARIA Combobox](https://www.w3.org/WAI/ARIA/apg/patterns/combobox/). Consulta técnica de busca: [PostgreSQL pg_trgm](https://www.postgresql.org/docs/current/pgtrgm.html).
