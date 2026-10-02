package br.com.supermercados.prices.collection.royal;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import java.time.Clock;
import java.time.Instant;

import br.com.supermercados.prices.collection.CatalogCheckpoint;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Component
class RoyalClient {

    private static final Logger LOGGER = LoggerFactory.getLogger(RoyalClient.class);

    private static final String ORGANIZATION_PATH = "/api-admin/v1/org/255";
    private static final String STORE_PATH = ORGANIZATION_PATH + "/filial/2/centro_distribuicao/1/loja";
    private static final String DOMAIN = "royaleemporio.com.br";
    private static final Set<Integer> RETRYABLE_STATUS_CODES = Set.of(502, 503, 504);

    private final RoyalProperties properties;
    private final ObjectMapper mapper;

    RoyalClient(RoyalProperties properties, ObjectMapper mapper) {
        this.properties = properties;
        this.mapper = mapper;
        if (properties.getMaxAttempts() < 1 || properties.getMaxAttempts() > 3
                || properties.getMaxResponseBytes() < 1000
                || properties.getRequestDelay().isNegative()
                || properties.getConnectTimeout().isNegative() || properties.getConnectTimeout().isZero()
                || properties.getRequestTimeout().isNegative() || properties.getRequestTimeout().isZero()
                || (!properties.isFullCatalog() && properties.getSearchTerms().isEmpty())) {
            throw new IllegalArgumentException("Configuração de coleta Royal inválida");
        }
    }

    Catalog fetch(Clock clock) {
        Session session = new Session();
        JsonNode organization = session.get("/api-admin/v1/organizacoes/filiais/dominio/royalsupermercados.com.br")
                .path("data");
        require(organization, "id", "290");
        require(organization, "nome", "Royal Supermercados");
        require(organization.path("organizacao"), "id", "255");
        require(organization.path("organizacao"), "enderecoServidor", DOMAIN);
        if (!organization.path("ativo").asBoolean() || organization.path("em_manutencao").asBoolean()) {
            throw new IllegalStateException("E-commerce Royal inativo ou em manutenção");
        }

        // The storefront itself downloads this anonymous-session configuration. No customer login is used.
        String configuration = session.text(properties.getBaseUrl().resolve(properties.getPublicConfigurationPath()));
        String publicKey = configurationValue(configuration, "lojaAuthJWT");
        String publicUser = configurationValue(configuration, "lojaUser");
        var login = mapper.createObjectNode().put("domain", DOMAIN)
                .put("username", publicUser).put("key", publicKey);
        JsonNode response = session.json(properties.getApiUrl().resolve(ORGANIZATION_PATH + "/auth/loja/login"),
                mapper.writeValueAsString(login));
        session.token = response.path("data").asString("");
        if (session.token.isBlank()) {
            throw new IllegalStateException("Royal não iniciou a sessão pública");
        }

        JsonNode pickups = session.get(ORGANIZATION_PATH + "/filial/2/loja/centros_distribuicoes/retiradas")
                .path("data");
        if (!pickups.isArray()) {
            throw new IllegalStateException("Royal não retornou as lojas de retirada");
        }
        List<JsonNode> matches = new ArrayList<>();
        pickups.forEach(store -> {
            if (store.path("id").asInt(-1) == 1) {
                matches.add(store);
            }
        });
        if (matches.size() != 1) {
            throw new IllegalStateException("Royal Retiro ausente ou duplicado nas lojas de retirada");
        }
        validateStore(matches.getFirst());
        JsonNode store = selectedStore(session);
        List<JsonNode> products = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        CatalogCheckpoint checkpoint = new CatalogCheckpoint("royal_retiro", clock, mapper);
        if (properties.isFullCatalog()) {
            JsonNode departments = session.get(STORE_PATH + "/classificacoes_mercadologicas/departamentos/arvore")
                    .path("data");
            if (!departments.isArray() || departments.isEmpty()) {
                throw new IllegalStateException("Royal não retornou os departamentos do catálogo");
            }
            for (JsonNode department : departments) {
                int id = department.path("classificacao_mercadologica_id").asInt(-1);
                if (id <= 0) {
                    throw new IllegalStateException("Departamento Royal sem identificador válido");
                }
                String path = STORE_PATH + "/classificacoes_mercadologicas/departamentos/" + id + "/produtos";
                try {
                    products.addAll(checkpoint.category(path, CatalogCheckpoint.Entries.class,
                            () -> new CatalogCheckpoint.Entries(paginate(session, path))).products());
                } catch (RuntimeException exception) {
                    warnings.add("Categoria Royal " + id + " incompleta: " + exception.getMessage());
                }
            }
        } else {
            for (String term : properties.getSearchTerms()) {
                products.addAll(paginate(session, STORE_PATH + "/buscas/produtos/termo/"
                        + URLEncoder.encode(term, StandardCharsets.UTF_8)));
            }
        }
        selectedStore(session);
        if (warnings.isEmpty()) checkpoint.complete();
        if (products.isEmpty() && !warnings.isEmpty()) {
            throw new IllegalStateException("Nenhuma categoria Royal foi coletada: " + String.join("; ", warnings));
        }
        return new Catalog(store, List.copyOf(products), checkpoint.startedAt(), List.copyOf(warnings));
    }

    private JsonNode selectedStore(Session session) {
        JsonNode store = session.get(ORGANIZATION_PATH + "/loja/centros_distribuicoes/1").path("data");
        validateStore(store);
        return store;
    }

    private List<JsonNode> paginate(Session session, String path) {
        List<JsonNode> products = new ArrayList<>();
        int totalPages = 1;
        int totalItems = -1;
        for (int page = 1; page <= totalPages; page++) {
            JsonNode response = session.get(path + "?page=" + page);
            JsonNode pagination = response.path("paginator");
            int count = pagination.path("total_items").asInt(-1);
            int pages = pagination.path("total_pages").asInt(-1);
            if (count < 0 || pages < 0 || pages > 500 || pagination.path("page").asInt(-1) != page
                    || (totalItems >= 0 && (count != totalItems || pages != totalPages))) {
                throw new IllegalStateException("Paginação Royal inválida ou catálogo alterado durante a coleta");
            }
            totalItems = count;
            totalPages = pages;
            JsonNode entries = response.path("data").isArray()
                    ? response.path("data") : response.path("data").path("produtos");
            if (!entries.isArray() || (entries.isEmpty() && count > 0)) {
                throw new IllegalStateException("Royal não retornou a página de produtos esperada");
            }
            entries.forEach(products::add);
        }
        if (products.size() != totalItems) {
            throw new IllegalStateException("Royal retornou quantidade diferente do total declarado");
        }
        LOGGER.info("Royal: {} concluído, {} registros recebidos", path, products.size());
        return products;
    }

    static void validateStore(JsonNode store) {
        require(store, "id", "1");
        require(store, "vipcommerce_centro_distribuicao_id", "959");
        require(store, "nome", "Royal Supermercados - Retiro");
        require(store, "cnpj", "39553144000100");
        JsonNode address = store.path("endereco");
        require(address, "logradouro", "Avenida Antônio de Almeida");
        require(address, "numero", "1477");
        require(address, "bairro", "Retiro");
        require(address, "cidade", "Volta Redonda");
        require(address, "estado", "Rio de Janeiro");
        require(address, "cep", "27277330");
        if (!store.path("ativo").asBoolean() || store.path("exclusivo_televendas").asBoolean()) {
            throw new IllegalStateException("Royal Retiro não está disponível para a loja pública");
        }
    }

    private static void require(JsonNode node, String field, String expected) {
        if (!expected.equals(node.path(field).asString(""))) {
            throw new IllegalStateException("Identidade inesperada do Royal no campo " + field);
        }
    }

    private String configurationValue(String script, String field) {
        var matcher = Pattern.compile(Pattern.quote(field) + ":\"([^\"]+)\"").matcher(script);
        if (!matcher.find()) {
            throw new IllegalStateException("Configuração pública Royal mudou: campo " + field + " ausente");
        }
        return matcher.group(1);
    }

    record Catalog(JsonNode store, List<JsonNode> products, Instant collectedAt, List<String> warnings) {
    }

    private final class Session {

        private final HttpClient http = HttpClient.newBuilder()
                .connectTimeout(properties.getConnectTimeout()).build();
        private String token;
        private boolean requested;

        private JsonNode get(String path) {
            return json(properties.getApiUrl().resolve(path), null);
        }

        private JsonNode json(URI uri, String body) {
            JsonNode response;
            try {
                response = mapper.readTree(request(uri, body));
            } catch (tools.jackson.core.JacksonException exception) {
                throw new IllegalStateException("Royal retornou JSON inválido", exception);
            }
            if (response == null || !response.path("success").asBoolean()) {
                throw new IllegalStateException("Royal não confirmou o sucesso da requisição em " + uri.getPath());
            }
            return response;
        }

        private String text(URI uri) {
            return request(uri, null);
        }

        private String request(URI uri, String body) {
            RuntimeException failure = null;
            for (int attempt = 1; attempt <= properties.getMaxAttempts(); attempt++) {
                try {
                    if (requested) {
                        Thread.sleep(properties.getRequestDelay().toMillis() * attempt);
                    }
                    requested = true;
                    HttpRequest.Builder request = HttpRequest.newBuilder(uri)
                            .timeout(properties.getRequestTimeout())
                            .header("Accept", "application/json")
                            .header("User-Agent", "SupermarketPrices/1.0 public-price-collector");
                    if (uri.getAuthority().equals(properties.getApiUrl().getAuthority())) {
                        request.header("organizationid", "255").header("domainkey", DOMAIN);
                        if (token != null) {
                            request.header("Authorization", "Bearer " + token);
                        }
                    }
                    if (body == null) {
                        request.GET();
                    } else {
                        request.header("Content-Type", "application/json")
                                .POST(HttpRequest.BodyPublishers.ofString(body));
                    }
                    HttpResponse<byte[]> response = http.send(request.build(), HttpResponse.BodyHandlers.ofByteArray());
                    if (response.body().length > properties.getMaxResponseBytes()) {
                        throw new IllegalStateException("Resposta Royal excede o limite configurado");
                    }
                    if (response.statusCode() >= 200 && response.statusCode() < 300) {
                        return new String(response.body(), StandardCharsets.UTF_8);
                    }
                    failure = new IllegalStateException("Royal respondeu HTTP " + response.statusCode()
                            + " em " + uri.getPath());
                    if (!RETRYABLE_STATUS_CODES.contains(response.statusCode())) {
                        throw failure;
                    }
                } catch (IOException exception) {
                    failure = new IllegalStateException("Falha de comunicação com Royal em " + uri.getPath(), exception);
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException("Coleta Royal interrompida", exception);
                }
            }
            throw failure;
        }
    }
}
