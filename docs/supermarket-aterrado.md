# Supermarket Aterrado — verificação da fonte

Verificação realizada em 20/09/2026.

## Unidade confirmada

- Nome: Supermarket Aterrado (Floresta).
- Endereço: Avenida Paulo de Frontin, 1096, Aterrado, Volta Redonda/RJ.
- Fonte primária: https://redesupermarket.com.br/lojas/supermarket-aterrado/
- Diretório oficial: https://redesupermarket.com.br/nossas-lojas/

Não havia uma unidade Supermarket/Floresta no banco local. A migração `V25` cadastra somente a unidade e sua fonte oficial. Antes da inserção, verifica cidade, nome e endereço para evitar duplicação. Não informa coordenadas não verificadas, produtos, estoque ou preços.

## Por que os preços não foram integrados

1. A página oficial não disponibiliza catálogo de compra online próprio para essa unidade.
2. O iFood indicado pela página oficial — https://www.ifood.com.br/delivery/volta-redonda-rj/supermarket-volta-redonda-aterrado/964260c7-c1e2-4a1f-8f2b-5a053c9e09b1 — respondeu HTTP 403 na consulta. Não houve tentativa de contornar o bloqueio.
3. O encarte oficial https://redesupermarket.com.br/wp-content/uploads/2026/09/encarte-18-21.pdf tem validade de 18 a 21/09/2026. O rodapé da quarta página exclui explicitamente o **Sul Fluminense**. Seus preços não podem ser atribuídos a Volta Redonda.
4. A página pública do Facebook consultada exigiu acesso/autenticação. Agregadores encontrados não forneceram confirmação suficiente de ofertas atuais para a unidade; anúncios de lojas próximas não foram reutilizados.

O mercado aparece no catálogo com a indicação **Preços não integrados** e a explicação da limitação. As comparações informam a ausência de preço, sem concluir ausência de estoque. Não existe um coletor fictício nem importação de preços de outras regiões.

Para integrar ofertas posteriormente, é necessária uma fonte acessível que confirme essa unidade e a validade/condições de cada oferta, ou uma integração autorizada pela loja.
