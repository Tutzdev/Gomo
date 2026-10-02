package br.com.supermercados.prices.collection.nagumo;

import java.io.IOException;
import java.io.InputStream;
import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import br.com.supermercados.prices.collection.CatalogCheckpoint;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Component
class NagumoClient {

    private static final Logger LOGGER = LoggerFactory.getLogger(NagumoClient.class);

    private static final String STORES_PATH =
            "/on/demandware.store/Sites-Nagumo-Site/pt_BR/Stores-AvailableStores";
    private static final String SELECT_STORE_PATH =
            "/on/demandware.store/Sites-Nagumo-Site/pt_BR/Stores-SelectStore";
    private static final String SEARCH_PATH =
            "/on/demandware.store/Sites-Nagumo-Site/pt_BR/Search-UpdateGrid";
    private static final Set<Integer> RETRYABLE_STATUS_CODES = Set.of(502, 503, 504);

    private final NagumoProperties properties;
    private final ObjectMapper objectMapper;

    NagumoClient(NagumoProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        validateProperties();
    }

    List<NagumoCatalogResponse> fetch(CatalogCheckpoint checkpoint, List<String> warnings) {
        CookieManager cookies = new CookieManager(null, CookiePolicy.ACCEPT_ORIGINAL_SERVER);
        HttpClient httpClient = HttpClient.newBuilder()
                .cookieHandler(cookies)
                .connectTimeout(properties.getConnectTimeout())
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
        RequestSession session = new RequestSession(httpClient, properties.getRequestDelay());

        session.get(properties.getBaseUrl(), false);
        requireExpectedStore(session.get(resolve(STORES_PATH), true), false);
        selectStore(session);
        JsonNode store = requireExpectedStore(session.get(resolve(STORES_PATH), true), true);
        List<String> categories = new ArrayList<>();
        categories.add(properties.getCategoryId());
        categories.addAll(properties.getAdditionalCategoryIds());
        List<NagumoCatalogResponse> catalogs = new ArrayList<>();
        for (String category : categories.stream().distinct().toList()) {
            try {
                catalogs.add(checkpoint.category(category, NagumoCatalogResponse.class,
                        () -> fetchCatalog(session, store, category)));
            } catch (RuntimeException exception) {
                warnings.add("Categoria Nagumo " + category + " incompleta: " + exception.getMessage());
            }
        }
        requireExpectedStore(session.get(resolve(STORES_PATH), true), true);
        if (catalogs.isEmpty()) throw new IllegalStateException("Nenhuma categoria Nagumo foi coletada: " + String.join("; ", warnings));
        if (warnings.isEmpty()) checkpoint.complete();
        return List.copyOf(catalogs);
    }

    private NagumoCatalogResponse fetchCatalog(RequestSession session, JsonNode store, String categoryId) {
        List<JsonNode> products = new ArrayList<>();
        String categoryName = null;
        int sourceCount = -1;
        Set<String> pageSignatures = new HashSet<>();

        for (int pageNumber = 0, start = 0; ; pageNumber++, start += properties.getPageSize()) {
            if (pageNumber >= 10000) {
                throw new IllegalStateException("Paginação da Nagumo excedeu o limite esperado");
            }
            URI uri = resolve(SEARCH_PATH + "?cgid=" + encode(categoryId)
                    + "&start=" + start + "&sz=" + properties.getPageSize());
            JsonNode response = session.get(uri, true);
            int responseCount = response.path("productSearch").path("count").asInt(-1);
            if (responseCount < 0) {
                throw new IllegalStateException("Resposta da Nagumo sem quantidade total do catálogo");
            }
            if (sourceCount < 0) {
                sourceCount = responseCount;
                categoryName = responseCount == 0 && response.path("productsSearchResult").isEmpty()
                        ? null : requiredText(response.path("productSearch").path("category"), "name");
            }

            JsonNode page = response.path("productsSearchResult");
            if (!page.isArray()) {
                throw new IllegalStateException("Resposta da Nagumo sem lista de produtos");
            }
            if (page.isEmpty()) {
                break;
            }
            // The declared count excludes some unavailable products; it is not a pagination bound.
            if (!pageSignatures.add(page.toString())) {
                throw new IllegalStateException("A Nagumo repetiu uma página durante a coleta");
            }
            page.forEach(products::add);
            if ("finished".equals(response.path("productSearch").path("showMoreUrl").asString())
                    || (response.path("productSearch").path("showMoreUrl").isMissingNode()
                        && page.size() < properties.getPageSize())) {
                break;
            }
        }

        // The source counts indexed hits before branch filtering; follow its explicit end marker.
        LOGGER.info("Nagumo: departamento {} concluído, {} registros recebidos", categoryId, products.size());
        return new NagumoCatalogResponse(store, categoryName, products.size(), products);
    }

    private JsonNode requireExpectedStore(JsonNode response, boolean selectionRequired) {
        JsonNode stores = response.path("stores");
        if (!stores.isArray()) {
            throw new IllegalStateException("Resposta da Nagumo sem lista de lojas");
        }

        JsonNode match = null;
        for (JsonNode store : stores) {
            if (properties.getStoreId().equals(store.path("storeId").asString())) {
                if (match != null) {
                    throw new IllegalStateException("A Nagumo retornou a loja configurada mais de uma vez");
                }
                match = store;
            }
        }
        if (match == null) {
            throw new IllegalStateException("A loja Nagumo configurada não está disponível");
        }
        requireEquals(match, "storeName", properties.getStoreName());
        requireEquals(match, "city", properties.getExpectedCity());
        requireEquals(match, "district", properties.getExpectedDistrict());
        requireEquals(match, "address", properties.getExpectedSourceAddress());
        if (!match.path("operation").asBoolean(false)) {
            throw new IllegalStateException("A loja Nagumo de Volta Redonda não está em operação");
        }
        if (selectionRequired && !match.path("active").asBoolean(false)) {
            throw new IllegalStateException("A Nagumo não manteve a loja de Volta Redonda selecionada");
        }
        return match;
    }

    private void selectStore(RequestSession session) {
        URI uri = resolve(SELECT_STORE_PATH + "?storeId=" + encode(properties.getStoreId())
                + "&lat=" + properties.getLatitude().toPlainString()
                + "&lng=" + properties.getLongitude().toPlainString());
        JsonNode response = session.get(uri, true);
        if (!response.path("success").asBoolean(false)) {
            throw new IllegalStateException("A Nagumo não confirmou a seleção da loja de Volta Redonda");
        }
    }

    private void requireEquals(JsonNode object, String field, String expected) {
        String actual = requiredText(object, field);
        if (!expected.equals(actual)) {
            throw new IllegalStateException("Identidade inesperada da loja Nagumo no campo " + field);
        }
    }

    private String requiredText(JsonNode object, String field) {
        String value = object.path(field).asString("").strip();
        if (value.isEmpty()) {
            throw new IllegalStateException("Resposta da Nagumo sem o campo " + field);
        }
        return value;
    }

    private URI resolve(String path) {
        return properties.getBaseUrl().resolve(path);
    }

    private String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private void validateProperties() {
        URI baseUrl = properties.getBaseUrl();
        if (baseUrl == null || baseUrl.getHost() == null
                || !("http".equalsIgnoreCase(baseUrl.getScheme())
                || "https".equalsIgnoreCase(baseUrl.getScheme()))) {
            throw new IllegalArgumentException("URL base da Nagumo inválida");
        }
        if (properties.getPageSize() < 1 || properties.getPageSize() > 250) {
            throw new IllegalArgumentException("Tamanho de página da Nagumo deve estar entre 1 e 250");
        }
        if (properties.getMaxAttempts() < 1 || properties.getMaxAttempts() > 3) {
            throw new IllegalArgumentException("Quantidade de tentativas da Nagumo deve estar entre 1 e 3");
        }
        if (properties.getMaxResponseBytes() < 1_000) {
            throw new IllegalArgumentException("Limite de resposta da Nagumo é muito pequeno");
        }
        requirePositive(properties.getConnectTimeout(), "Timeout de conexão");
        requirePositive(properties.getRequestTimeout(), "Timeout de requisição");
        if (properties.getRequestDelay().isNegative()) {
            throw new IllegalArgumentException("Intervalo entre requisições não pode ser negativo");
        }
    }

    private void requirePositive(Duration duration, String name) {
        if (duration == null || duration.isZero() || duration.isNegative()) {
            throw new IllegalArgumentException(name + " da Nagumo deve ser positivo");
        }
    }

    private final class RequestSession {

        private final HttpClient httpClient;
        private final Duration delay;
        private boolean requested;

        private RequestSession(HttpClient httpClient, Duration delay) {
            this.httpClient = httpClient;
            this.delay = delay;
        }

        private JsonNode get(URI uri, boolean jsonExpected) {
            RuntimeException lastFailure = null;
            for (int attempt = 1; attempt <= properties.getMaxAttempts(); attempt++) {
                waitBeforeRequest(attempt);
                try {
                    HttpRequest request = HttpRequest.newBuilder(uri)
                            .timeout(properties.getRequestTimeout())
                            .header("Accept", jsonExpected ? "application/json" : "text/html,application/xhtml+xml")
                            .header("User-Agent", "SupermarketPrices/1.0 public-price-collector")
                            .GET()
                            .build();
                    HttpResponse<InputStream> response = httpClient.send(
                            request, HttpResponse.BodyHandlers.ofInputStream());
                    int status = response.statusCode();
                    byte[] body = readLimited(response.body());
                    if (status >= 200 && status < 300) {
                        return jsonExpected ? parseJson(body) : objectMapper.createObjectNode();
                    }
                    lastFailure = new IllegalStateException(
                            "Nagumo respondeu HTTP " + status + " em " + uri.getPath());
                    if (!RETRYABLE_STATUS_CODES.contains(status)) {
                        throw lastFailure;
                    }
                } catch (IOException exception) {
                    lastFailure = new IllegalStateException(
                            "Falha de comunicação com a Nagumo em " + uri.getPath(), exception);
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException("Coleta da Nagumo interrompida", exception);
                }
            }
            throw lastFailure == null
                    ? new IllegalStateException("Falha desconhecida ao consultar a Nagumo")
                    : lastFailure;
        }

        private byte[] readLimited(InputStream body) throws IOException {
            try (body) {
                byte[] bytes = body.readNBytes(properties.getMaxResponseBytes() + 1);
                if (bytes.length > properties.getMaxResponseBytes()) {
                    throw new IOException("Resposta da Nagumo excede o limite configurado");
                }
                return bytes;
            }
        }

        private JsonNode parseJson(byte[] body) {
            if (body.length == 0) {
                throw new IllegalStateException("A Nagumo retornou uma resposta vazia");
            }
            try {
                return objectMapper.readTree(body);
            } catch (RuntimeException exception) {
                throw new IllegalStateException("A Nagumo retornou JSON inválido", exception);
            }
        }

        private void waitBeforeRequest(int attempt) {
            if (!requested) {
                requested = true;
                return;
            }
            try {
                Thread.sleep(delay.toMillis() * attempt);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("Coleta da Nagumo interrompida", exception);
            }
        }
    }
}
