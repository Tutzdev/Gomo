# Auditoria da comparação — Volta Redonda

Verificação em 20/09/2026, horário de Brasília. Catálogo mantido em **47.305 produtos**. Nenhuma oferta sintética foi inserida no banco da aplicação.

## Causas corrigidas

- A busca antiga paginava cadastros antes de reunir equivalências. Agora os grupos são formados sobre todos os candidatos relevantes antes da paginação; a escolha do ID de uma origem expande a comparação para os demais cadastros confirmados.
- Abreviações, atributos na marca e distribuidor no campo de marca fragmentavam produtos existentes. O índice foi recalculado no catálogo atual sem alterar IDs de listas ou transferir históricos. Há confirmação por atributos e candidatos aproximados separados.
- Um preço baixo de uma duplicata antiga podia prevalecer sobre uma coleta posterior. A coleta mais recente por mercado agora prevalece; a política de validade e promoções condicionadas continua aplicada.
- A descrição do cadastro compartilhado não preservava o texto de cada oferta. A observação agora guarda descrição original, código local, fonte, preço, condições e data. Arquivos de coletas anteriores preencheram apenas descrições comprovadas, sem renovar os preços.
- URLs públicas com espaços Unicode impediam a atualização de alguns produtos do Hortifruti; o tratamento de URLs foi corrigido.
- A atualização operacional ignora referências novas antes da ingestão. Ela atualiza ofertas existentes e não aumenta o catálogo.

## Cobertura das 11 unidades

“Cadastros observados” conta produtos com algum registro histórico por loja, não grupos equivalentes. “Preço utilizável” considera a última observação, limite de 48 horas, validade explícita e disponibilidade informada. Estoque desconhecido é sinalizado; ausência de dados não é falta de estoque.

| Unidade / fonte pública | Cadastros observados | Preço utilizável | Última coleta | Evidência da filial |
|---|---:|---:|---|---|
| [Atacadão São Geraldo](https://www.atacadao.com.br) | 45 | 45 | 18/09/2026 21:20 | Loja 815; Rodovia dos Metalúrgicos, 1085. Encartes oficiais revisados, sem feed de estoque. |
| [Atacadão Vila Rica](https://www.atacadao.com.br) | 45 | 45 | 18/09/2026 21:20 | Loja 289; Avenida Dois, 10. Abrangência das duas lojas registrada no arquivo revisado. |
| [Bramil Santo Agostinho](https://www.bramilemcasa.com.br) | 16168 | 8147 | 20/09/2026 19:04 | VIP organização 53, CD 24; CNPJ 32296378004753. Identidade validada antes da coleta. |
| [Hortifruti Aterrado](https://www.hortifruti.com.br) | 2287 | 2287 | 20/09/2026 19:06 | Seller hortifrutibraterradohf, pickup 1119, Paulo de Frontin 874 e região do CEP 27213270. |
| [Nagumo Ponte Alta (036-V.REDONDA)](https://www.nagumo.com.br) | 7636 | 7157 | 20/09/2026 18:19 | Unidade 36 / 036-V.REDONDA; Via Sérgio Braga, 951. |
| [Pame Santo Agostinho](https://www.pamesupermercados.com.br/loja-01) | 3419 | 3419 | 20/09/2026 18:59 | Mercafácil 6647c2d35aa7f3f538ab875b, loja-01; CNPJ 26185052000173. |
| [Pérola Água Limpa](https://www.perolasupermercados.com.br) | 10444 | 5880 | 20/09/2026 18:59 | VIP organização 329, CD 1; CNPJ 07954309000240. |
| [Royal Retiro](https://www.royalsupermercados.com.br) | 8506 | 5566 | 20/09/2026 18:32 | VIP organização 255, filial 2, CD 1; CNPJ 39553144000100. |
| [Spani Volta Redonda](https://www.spanionline.com.br) | 5087 | 4143 | 20/09/2026 18:39 | VIP organização 67, CD 6; CNPJ 05868574001171. |
| [Supermarket Aterrado](https://redesupermarket.com.br/) | 0 | 0 | — | Diretório oficial: Paulo de Frontin, 1096. iFood HTTP 403; encarte exclui Sul Fluminense. |
| [Ville Sessenta](https://www.villesupermercado.com.br/loja) | 6049 | 6049 | 20/09/2026 18:54 | Mercafácil 655662fde8f60d001eedf70c, loja; CNPJ 32877857000180. |

## Atualização das fontes existentes

Foram gravadas **59.262 novas observações de preço**, com zero produtos criados. Atacadão reaplicou 45 referências por unidade sem renovar a data original. A coleta diária agora atualiza apenas referências existentes por padrão (`PRICE_COLLECTION_EXISTING_ONLY=true`).

| Coletor | Observações novas | Avisos/rejeições | Resultado |
|---|---:|---:|---|
| atacadao_sao_geraldo_flyer | 0 | 0 | SUCCESS |
| atacadao_vila_rica_flyer | 0 | 0 | SUCCESS |
| bramil_santo_agostinho | 16168 | 904 | PARTIAL |
| hortifruti_aterrado | 2259 | 0 | SUCCESS |
| nagumo_volta_redonda | 7369 | 11 | PARTIAL |
| pame_santo_agostinho | 3402 | 241 | PARTIAL |
| perola_agua_limpa | 10424 | 500 | PARTIAL |
| royal_retiro | 8495 | 1069 | PARTIAL |
| spani_volta_redonda | 5086 | 147 | PARTIAL |
| ville_sessenta | 6059 | 0 | SUCCESS |

Avisos são rejeições explícitas da fonte ou da validação; referências novas ignoradas não são produtos importados. As limitações abaixo explicam as causas.

## Exemplos reais exibidos

Preços comparáveis por embalagem, sem aplicar valores condicionados a clube ou quantidade mínima. Cada linha abaixo veio da resposta real da API utilizada pela interface, com a descrição original quando a observação a fornece.

### Coca Cola Original 1L

| Mercado | Descrição da oferta | Preço | Coleta |
|---|---|---:|---|
| Spani Volta Redonda | Refrigerante Coca Cola 1l | R$ 6,49 | 20/09/2026 18:39 |
| Ville Sessenta | REF COCA COLA  PET 1L | R$ 7,19 | 20/09/2026 18:54 |
| Nagumo Ponte Alta (036-V.REDONDA) | Coca Cola Original 1L | R$ 7,29 | 20/09/2026 18:19 |

Sem preço comparável confirmado nesse grupo: Atacadão São Geraldo; Atacadão Vila Rica; Bramil Santo Agostinho; Hortifruti Aterrado; Pame Santo Agostinho; Pérola Água Limpa; Royal Retiro; Supermarket Aterrado.

### DETERGENTE YPE 500ML NEUTRO

| Mercado | Descrição da oferta | Preço | Coleta |
|---|---|---:|---|
| Spani Volta Redonda | Detergente Líquido Ypê 500ml Neutro | R$ 2,88 | 20/09/2026 18:39 |
| Bramil Santo Agostinho | Lava Louças Ypê Neutro 500ml | R$ 2,89 | 20/09/2026 19:04 |
| Pérola Água Limpa | Detergente Líquido Ypê Neutro 500ml | R$ 2,99 | 20/09/2026 18:59 |
| Royal Retiro | Deterg. Ype 500ml Neutro | R$ 2,99 | 20/09/2026 18:32 |
| Pame Santo Agostinho | DETERGENTE YPE 500ML NEUTRO | R$ 3,29 | 20/09/2026 18:59 |
| Ville Sessenta | DET YPE NEUTRO 500ML | R$ 3,39 | 20/09/2026 18:54 |
| Hortifruti Aterrado | Detergente Líquido Ypê Neutro 500ml | R$ 3,49 | 20/09/2026 19:06 |

Sem preço comparável confirmado nesse grupo: Atacadão São Geraldo; Atacadão Vila Rica; Nagumo Ponte Alta (036-V.REDONDA); Supermarket Aterrado.

### Açúcar União Refinado 1Kg

| Mercado | Descrição da oferta | Preço | Coleta |
|---|---|---:|---|
| Nagumo Ponte Alta (036-V.REDONDA) | Açúcar União Refinado 1Kg | R$ 3,69 | 20/09/2026 18:19 |
| Spani Volta Redonda | Açúcar Refinado União 1kg | R$ 3,95 | 20/09/2026 18:39 |
| Bramil Santo Agostinho | Açúcar Refinado União 1kg | R$ 4,39 | 20/09/2026 19:04 |
| Hortifruti Aterrado | Açúcar Refinado União 1kg | R$ 4,99 | 20/09/2026 19:06 |
| Pame Santo Agostinho | ACUCAR REFINADO UNIAO 1KG | R$ 4,99 | 20/09/2026 18:59 |
| Royal Retiro | Acucar Ref. Uniao 1kg | R$ 4,99 | 20/09/2026 18:32 |
| Pérola Água Limpa | Açúcar Refinado União 1kg | R$ 5,69 | 20/09/2026 18:59 |
| Ville Sessenta | ACUCAR UNIAO REFINADO 1KG | R$ 5,69 | 20/09/2026 18:54 |

Sem preço comparável confirmado nesse grupo: Atacadão São Geraldo; Atacadão Vila Rica; Supermarket Aterrado.

A Coca-Cola Original 1 L reúne os IDs `7c1f6fa5-1485-4f82-bd27-2a2b47bc191e` (Nagumo), `eeb2fb8e-4b76-45f6-ab12-433a80f0dab4` (Ville) e `2b5b7a3f-0258-4093-b6dc-ededf6b4b45d` (Spani). Consultar qualquer um retornou os mesmos mercados e preços. Os dois primeiros não exigem GTIN compartilhado. No Spani, a variante omitida foi confirmada por evidências públicas do GTIN 7894900027044 registradas em `comparison-attributes.json`; preços dessas fontes auxiliares não foram importados.

A versão sem açúcar de 1 L tem seu próprio grupo. A retornável de 1 L, identificada pelo GTIN 7894900015119, fica separada e explicita a apresentação no nome. O preço R$ 5,49 do Pérola não é tratado como desconto sobre a descartável Original. Clear, Coco, packs e outros volumes do detergente, assim como Doçúcar no açúcar, não foram misturados.

## Fluxo comprovado

- Navegador: pesquisar “coca cola 1 litro” → card consolidado com três mercados → clicar Comparar no cadastro Nagumo → ver também Spani e Ville, além das lacunas das outras oito unidades.
- Selecionar Nagumo + Ville retornou exatamente duas lojas; o menor valor mudou para R$ 7,19. Sem seleção, todas as 11 unidades são consultadas.
- Repetido por pesquisa e clique com “detergente ype neutro 500ml” e “acucar uniao refinado 1kg”. A busca genérica por Coca mantém volumes e variantes separados.
- Lista temporária: uma Coca-Cola Original 1 L escolhida do cadastro Nagumo e um detergente Ypê neutro 500 ml do Pame. Spani: 2/2 itens, R$ 9,37; Ville: 2/2, R$ 10,58. Mercados com somente detergente tiveram cobertura 1/2 e subtotal parcial, sem vencer a recomendação.
- A lista avaliou 11 mercados mesmo exibindo dez por página; Ville apareceu na segunda página. A lista temporária e seus dois itens foram removidos após o teste.
- Seleção por teclado (seta + Enter), movimento reduzido e larguras 390, 768 e 1280 px verificados. Sem transbordamento horizontal da página; tabelas têm rolagem própria. Dashboard e navegação preservados.
- `verify`: 269 testes passaram, zero falhas/erros/ignorados. Inclui equivalência além de 100 cadastros, grupos antes da paginação, diferença de variante/volume/pack, filtro exato, lista de origens diferentes, lacunas, preço histórico duplicado e pacote sem contagem sem cálculo enganoso por litro. Dados sintéticos ficaram no banco exclusivo de testes.
- `npm run lint` e `npm run build` passaram. Capturas locais em `output/playwright/coca-1l-390.png`, `coca-1l-1280.png` e `list-comparison-real-desktop.png`.

## Limitações reais

- **Supermarket Aterrado:** unidade confirmada, zero preços integrados. A página oficial aponta iFood, cuja consulta respondeu 403. O encarte de 18–21/09 exclui explicitamente o Sul Fluminense. Não foram atribuídos preços de outra região. [Evidências](supermarket-aterrado.md).
- **Atacadão (duas unidades):** cobertura restrita a 45 produtos de encartes oficialmente revisados. A reaplicação preserva a data da revisão de 18/09; não representa uma coleta online nova nem estoque confirmado. O catálogo completo de e-commerce não está integrado.
- **VIP (Royal, Spani, Bramil e Pérola):** a unidade é validada por organização/CD/CNPJ/cidade. Itens cuja unidade de venda exige conversão não comprovada são rejeitados. Divergências de identidade não alteram vínculos antigos. O produto de sal em 1.000 sachês de 0,8 g teve atualização rejeitada por quantidade inválida; nenhum preço por quantidade presumida foi usado.
- **Pame:** variações sem identidade/preço individual suficientemente determinado permanecem rejeitadas. **Nagumo:** ofertas por peso sem base de cálculo confirmada também não entram.
- **Hortifruti:** dados da região/seller Aterrado; não representa toda a rede nem todo o estoque físico. **Ville:** páginas verificadas por loja, CNPJ e paginação; novas referências ignoradas nesta atualização.
- As lacunas dos três exemplos não comprovam que o produto não exista fisicamente. Indicam que a fonte consultada não forneceu uma oferta utilizável cuja equivalência foi confirmada. Candidatos ambíguos aparecem separados e exigem mais evidência.

Detalhes técnicos e comandos de validação: [Identidade de produto](product-equivalence.md).
