# Expansão do catálogo de Volta Redonda

> **Retirado em 10/2026:** o Atacadão Vila Rica e o importador de encartes revisados do Atacadão saíram do Gomo, que agora só compara mercados com loja online. Este documento registra a expansão de 18/09; a situação atual está em [data-sources.md](data-sources.md#situação-das-fontes-em-16102026).

Verificação em 18/09/2026. O dashboard e a navegação existentes foram preservados. Esta entrega amplia os dados e as telas de produtos, comparação e listas. Os dados comerciais vieram de consultas públicas reais, não de seeds de demonstração.

## Resultado gravado e validado

Consulta ao banco persistente às 22:08 de 18/09/2026: **10 lojas com preços**, **47.305 produtos canônicos**, **25.923 com imagem pública** e **8.031 produtos com observações em mais de uma loja**. As contagens abaixo usam produtos distintos por loja, não número de páginas, registros repetidos ou históricos.

| Loja | Produtos com observação de preço | Preços utilizáveis na comparação agora |
| --- | ---: | ---: |
| Nagumo Ponte Alta | 7.636 | 7.327 |
| Royal Retiro | 8.506 | 5.580 |
| Bramil Santo Agostinho | 16.168 | 8.205 |
| Spani Volta Redonda | 5.087 | 4.163 |
| Pérola Água Limpa | 10.444 | 5.894 |
| Pame Santo Agostinho | 3.419 | 3.419 |
| Ville Sessenta | 6.049 | 6.049 |
| Hortifruti Aterrado | 2.287 | 2.287 |
| Atacadão São Geraldo | 45 | 45 |
| Atacadão Vila Rica | 45 | 45 |

“Utilizáveis” exclui a última observação vencida e estoque explicitamente indisponível. Inclui estoque não informado, que aparece identificado na interface. Não garante disponibilidade futura. Produtos com vários IDs de origem podem compartilhar um canônico; por isso a coleta pode receber mais registros do que a contagem distinta desta tabela. São 59.686 vínculos produto–loja com preço registrado e 43.014 preços atualmente utilizáveis, compartilhando o catálogo canônico.

Cinco registros do Hortifruti foram rejeitados por URLs inválidas na fonte. Pesos/variantes não confirmados também foram descartados nas demais fontes: por exemplo, Bramil encontrou 17.072 registros e aceitou 16.168. Os motivos ficam nas execuções de coleta. As coletas completas são arquivadas antes de persistir; foram reaplicadas para verificar idempotência e preencher metadados de origem. Os 90 preços dos dois Atacadões mantiveram IDs, datas e quantidades, sem inserir novos históricos.

## Fontes e identidade das unidades

| Unidade | Fonte pública | Coleta |
| --- | --- | --- |
| Nagumo Ponte Alta, loja 36 | [Nagumo](https://www.nagumo.com.br), sessão pública Salesforce Commerce e departamentos | Automática |
| Royal Retiro | [Royal](https://www.royalsupermercados.com.br), VipCommerce, organização 255 / filial 2 / CD 1 | Automática |
| Bramil Santo Agostinho | [Bramil](https://www.bramilemcasa.com.br), VipCommerce, organização 53 / filial 1 / CD 24, CNPJ 32296378004753 | Automática |
| Spani Volta Redonda | [Spani](https://www.spanionline.com.br), VipCommerce, organização 67 / filial 1 / CD 6, CNPJ 05868574001171 | Automática |
| Pérola Água Limpa | [Pérola](https://www.perolasupermercados.com.br), VipCommerce, organização 329 / filial 1 / CD 1, CNPJ 07954309000240 | Automática |
| Pame Santo Agostinho | [Pame](https://www.pamesupermercados.com.br/loja-01), HTML estruturado Mercafácil, loja 6647c2d35aa7f3f538ab875b, CNPJ 26185052000173 | Automática |
| Ville Sessenta | [Ville](https://www.villesupermercado.com.br/loja), HTML estruturado Mercafácil, loja 655662fde8f60d001eedf70c, CNPJ 32877857000180 | Automática |
| Hortifruti Aterrado | [Hortifruti](https://www.hortifruti.com.br), GraphQL público utilizado pelo site, vendedor hortifrutibraterradohf e retirada 1119, Av. Paulo de Frontin, 874 | Automática |
| Atacadão São Geraldo, loja 815 | [Diretório oficial](https://www.atacadao.com.br/institucional/nossas-lojas), Rodovia dos Metalúrgicos, 1085; encartes oficiais com abrangência explícita | Revisão manual de encartes |
| Atacadão Vila Rica, loja 289 | Mesmo diretório oficial, Avenida Dois, 10, Jardim Vila Rica; encartes oficiais com abrangência explícita | Revisão manual de encartes |

Existem oito integrações automáticas e duas unidades alimentadas por encartes revisados. Reexecutar o importador de encartes não baixa nem revisa uma edição nova e não torna um preço antigo atual.

Os coletores conferem a identidade da unidade antes de importar. VipCommerce usa a sessão anônima que o próprio storefront fornece; nenhum token de consumidor ou chave secreta foi incorporado. Mercafácil é lido pelo HTML público, pois a API direta recusou as consultas. Hortifruti exige o vendedor local e o identificador de região: a resposta sem região pode trazer preços de outra unidade. Sua paginação percorre as categorias públicas porque a busca global recusa posições acima de 2.500.

## Encartes do Atacadão

O arquivo `src/main/resources/collectors/atacadao-reviewed-2026-09.json` registra 45 ofertas específicas, URLs, SHA-256 dos PDFs, unidades abrangidas, validade e revisão. Os PDFs oficiais foram lidos visualmente, incluindo rodapés e condições:

- [Ofertão, 15 a 21/09](https://apigw.cloud.carrefour.com.br/api-middleware-flyer-services/api/v2/Flyer/?id=114D7g56iOZi4sMN7uXxyQGaRi4yyjlMf).
- [Super ofertas, 11 a 21/09](https://apigw.cloud.carrefour.com.br/api-middleware-flyer-services/api/v2/Flyer/?id=18Py2Ht1djmE-a6vjX-OHj14vFi8DapV-).
- [Boteco, 17 a 20/09](https://apigw.cloud.carrefour.com.br/api-middleware-flyer-services/api/v2/Flyer/?id=1tS82MCRXD7G9kVb8sqQu00zVwGDB9MVU).

Ofertas diárias, tamanhos/variantes ambíguos e preços de peças inteiras sem base confirmada foram excluídos. Preços exclusivos do aplicativo permanecem condicionais e não reduzem automaticamente o total. Estoque é **não informado**, não disponibilidade garantida. As duas lojas aparecem explicitamente na abrangência; os valores não foram atribuídos a elas por mera proximidade geográfica.

Para atualizar, obtenha a nova edição oficial, confira visualmente unidades, datas, produto, tamanho, preço e condições, registre seu hash e substitua o recurso revisado e a data de verificação do coletor. Não altere apenas `reviewedAt`. Edições vencidas são recusadas. A política geral de idade máxima também se aplica.

## Funcionamento e conservação dos dados

- Cada observação preserva fonte, referência do produto, coleta, validade, estoque e URL específica quando fornecida. Produtos preservam imagem pública, marca e quantidade quando disponíveis.
- O matching prioriza vínculos revisados e GTIN válido. A alternativa automática exige marca, mesma medida convertida e identidade textual estrita, sem GTIN conflitante nem ambiguidade. Tolerância a erros pertence à pesquisa; não funde produtos diferentes. Canônicos antigos separados não são mesclados automaticamente.
- Normalização reconhece equivalências como 1 L / 1.000 ml e 1 kg / 1.000 g. Multipacks explícitos, como 12 × 300 ml, preservam quantidade de unidades. Embalagens de volumes diferentes não são tratadas como o mesmo produto.
- A pesquisa existente usa a busca normalizada, aliases e similaridade PostgreSQL, com paginação sobre o catálogo inteiro. O frontend apresenta imagens quando existem, metadados, melhor oferta atual, mercado, atualização, comparação e inclusão na lista.
- A comparação apresenta preço por embalagem e por kg/litro/unidade, diferença entre lojas e economia. Listas preservam quantidades inteiras no controle e aceitam cobertura parcial, mostram menor preço por item, total por mercado e combinação de lojas. Ausência de observação não significa estoque zerado.
- Fontes são isoladas: falha não apaga o último catálogo nem interrompe as demais. Dados antigos permanecem no histórico, mas só entram no total se a política de preço ainda os considerar atuais. Não existe fallback que apresente preço vencido como atual.
- HTTP tem limites de tamanho, tempo de conexão/resposta incluindo corpo e duas tentativas controladas para falhas transitórias. Não há repetição para contornar HTTP 403/429. As requisições das novas fontes são espaçadas em um segundo.
- A ingestão reutiliza referências e canônicos, acrescenta observações ao histórico e impede duplicação na reaplicação da mesma coleta. Itens temporariamente ausentes não são apagados.

## Execução local e atualização

Use o banco persistente descrito em `.local/runtime.json`. Os scripts recusam criar outro banco silenciosamente. Conta, assinatura e listas existentes são conservadas.

```powershell
.\scripts\start-local.ps1 -Service Backend
.\scripts\start-local.ps1 -Service Frontend
```

O agendamento existente roda às 05:30 em `America/Sao_Paulo`, com a aplicação em execução. Um administrador pode usar os endpoints de coleta descritos em [data-sources.md](data-sources.md). O operador também pode definir um lote apenas na inicialização desejada:

```powershell
$env:APP_COLLECTION_RUN_ON_START = 'hortifruti_aterrado,bramil_santo_agostinho'
.\scripts\start-local.ps1 -Service Backend
```

Códigos: `nagumo_volta_redonda`, `royal_retiro`, `bramil_santo_agostinho`, `spani_volta_redonda`, `perola_agua_limpa`, `pame_santo_agostinho`, `ville_sessenta`, `hortifruti_aterrado`, `atacadao_sao_geraldo_online`.

`APP_COLLECTION_ARCHIVE_DIRECTORY` habilita arquivos locais dos catálogos completos antes da ingestão. O script configura `.local/catalog-snapshots`. Para reaplicar um arquivo completo sem nova consulta, use `APP_COLLECTION_RUN_ON_START=replay:spani_volta_redonda`. A identidade da fonte é conferida e a data original é preservada. Não se inclui o arquivo local com dados em commits. Remova a variável de lote nas próximas inicializações normais.

`APP_COLLECTION_REGIONAL_ENABLED=false` desativa as seis novas integrações automáticas. As flags antigas de Nagumo e Royal continuam funcionando.

## Limitações e expansão

- Quantidade de itens não equivale a estoque garantido. Preços sem disponibilidade explícita são identificados como tal; indisponíveis e vencidos não recebem preço utilizável no total.
- Os dois Atacadões possuem um recorte de 45 ofertas, não o catálogo completo. Sua renovação ainda depende de revisão humana. Os demais coletores dependem dos contratos públicos atuais; mudanças de bundles, estrutura ou hashes GraphQL exigem manutenção.
- Pesos variáveis sem conversão comprovada, GTIN inválido, variantes ambíguas e unidades alternativas não confirmadas são descartados com motivo. Algumas fontes não publicam marca, imagem ou categoria para todos os itens.
- Matching conservador deixa alguns equivalentes separados para evitar preços de embalagens/variantes diferentes no mesmo produto. Ampliações devem incluir evidência, não similaridade textual isolada.
- Floresta/Supermarket é candidato futuro, mas o encarte investigado excluía Sul Fluminense. Não foi importado. O storefront encontrado para Floresta em Anápolis também foi rejeitado.
- Empório Brasil, Diga e Paiva são candidatos mediante catálogo público verificável ou integração autorizada. Outras filiais do Royal exigem seus próprios preços e escopo confirmado. Nenhuma unidade vazia foi incluída para inflar a contagem.

## Áreas alteradas

`collection` contém HTTP, coletores, parsing e arquivo local; `product` contém mídia, matching e ofertas em lote; `price`/`comparison` contêm origem da observação e preço por medida. As migrations V18–V23 acrescentam mídia, índice de identidade, correção de multipacks explícitos, origem do preço, quantidade recebida em embalagens “leve mais, pague menos” e correção de números de variantes de velas indevidamente interpretados como quantidade. No frontend, `features/catalog`, `features/shopping-lists` e as páginas de produtos/comparação integram os dados. Os favicons e controles de quantidade que já estavam em edição foram preservados.

## Verificação executada

- `./mvnw.cmd verify`: **233 testes, zero falhas e zero erros**, compilação e empacotamento concluídos. Os testes cobrem normalização, matching, pesquisa, conversão, comparação, preço por medida, persistência, idempotência, concorrência, autenticação, principais endpoints, contratos dos coletores, limites HTTP e arquivos de coleta. A regressão de URL de origem cobre gravação real e enriquecimento de observação antiga sem alterar preço/data.
- `npm run lint` e `npm run build` no frontend: aprovados. O build emite os avisos existentes de anotações `PURE` do Zod, sem falha.
- Flyway: 23 migrations aplicadas e validadas no banco local, sem recriar o banco nem as contas.
- Playwright em navegador real: login da conta existente, catálogo de mercado, busca, imagem carregada, detalhe e preço por kg, inclusão via seletor de produto e painel “À lista”, quantidade 1 → 2, comparação completa/parcial, origem dos encartes, desktop e viewport de 390 px. O menu móvel mediu 342 px de largura e rolagem, sem exceder o viewport. Conta e assinatura continuaram ativas depois dos reinícios.
- Lista temporária: duas unidades de Coca-Cola Original 2 L e duas de Leite Hércules Integral 1 L. Dez lojas avaliadas; menor loja com a lista completa: Nagumo, R$ 35,34. Combinação de Nagumo e Pérola: R$ 32,96; economia de R$ 2,38. Hortifruti também apresentou cobertura parcial legítima. A lista temporária foi excluída após a validação, preservando as listas do usuário.

Consultas HTTP reais, com até 100 candidatos por consulta; “lojas” conta as que tinham ofertas atuais nesses candidatos, não todas as possíveis páginas:

| Pesquisa | Resultados no catálogo | Lojas com ofertas na primeira página de 100 |
| --- | ---: | ---: |
| coca cola 1 litro | 223 | 8 |
| carne bovina | 176 | 8 |
| arroz 5kg | 468 | 8 |
| leite | 1.418 | 8 |
| detergente | 124 | 8 |
| feijao | 194 | 8 |
| coca 1l | 1.238 | 8 |
| papel higienico | 94 | 7 |
| cerveja lata | 82 | 9 |
| leitte integral | 109 | 10 |

A busca ordena por relevância e admite resultados mais amplos; a contagem não significa que todos têm exatamente a medida digitada. A comparação exige o produto canônico selecionado e preserva sua medida.
