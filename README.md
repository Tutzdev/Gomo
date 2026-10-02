# Gomo

API REST desenvolvida para comparar preços de produtos entre supermercados e ajudar o usuário a encontrar a melhor opção de compra com base na sua cidade e lista de compras.

O projeto foi construído com foco em organização, segurança, regras de negócio bem definidas e facilidade de manutenção.

Stack do backend: Java 21, Spring Boot 4.1.1, Maven Wrapper, Spring MVC, Data JPA, Security, Bean Validation, Actuator, PostgreSQL, Flyway e OpenAPI. A autenticação usa tokens opacos persistidos somente por hash. A interface Gomo fica em `frontend/` e utiliza React, TypeScript, Vite, Tailwind CSS e TanStack Query.

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

## Funcionalidades

* Cadastro e autenticação de usuários
* Verificação de e-mail e recuperação de senha
* Catálogo de produtos, redes e supermercados
* Histórico e comparação de preços
* Coleta automática diária de fontes públicas verificadas
* Criação e gerenciamento de listas de compras
* Comparação completa de uma lista entre supermercados
* Recomendação da melhor opção de compra
* Preferência de cidade e lojas favoritas
* Alertas de preço
* Notificações internas
* Envio de preços pela comunidade
* Moderação de contribuições
* Controle de acesso para administradores
* Auditoria de operações administrativas

A busca, as listas e as comparações usam o catálogo genérico: um item por produto real ("Coca-Cola 2 L"), vinculado aos anúncios equivalentes de cada mercado. Veja [`docs/generic-catalog.md`](docs/generic-catalog.md).

As fontes comerciais atualmente verificadas, suas limitações e a operação dos coletores estão descritas em [`docs/data-sources.md`](docs/data-sources.md).

O MVP coleta os departamentos públicos de Nagumo Ponte Alta e Royal Retiro, com vínculos explícitos entre produtos revisados. A busca da lista percorre todo o catálogo por páginas; cada mercado possui seu catálogo com preços e datas. A comparação mostra cobertura, faltantes, total completo ou subtotal parcial e a menor combinação por item. O roteiro inicial está em [`docs/mvp-data-check.md`](docs/mvp-data-check.md), e a ampliação com validação de 20 produtos em [`docs/catalog-list-validation.md`](docs/catalog-list-validation.md). Se a porta 8080 estiver ocupada por outro serviço, use `PORT=8081` no backend e `VITE_API_BASE_URL=http://localhost:8081/api/v1` em `frontend/.env.local`.

## Tecnologias

* Java 21
* Spring Boot 4
* Spring MVC
* Spring Data JPA
* Spring Security
* PostgreSQL
* Flyway
* Maven
* Docker
* OpenAPI
* Bean Validation
* Spring Boot Actuator

## Arquitetura

O projeto é organizado por funcionalidades, mantendo responsabilidades bem definidas entre as diferentes camadas da aplicação.

```text
Controller
    |
Service
    |
Repository
    |
PostgreSQL
```

Os controllers são responsáveis pela comunicação HTTP, os services concentram as regras de negócio e transações, enquanto os repositories realizam a persistência dos dados.

DTOs são utilizados para evitar a exposição direta das entidades da aplicação.

## Segurança

A API possui autenticação baseada em tokens opacos.

Os tokens são armazenados somente através de hash e possuem tempo de expiração.

Também estão implementados:

* BCrypt para armazenamento de senhas
* Controle de acesso por roles
* Rate limiting
* Revogação de sessões
* Tokens de uso único para recuperação de senha
* Verificação de e-ma
