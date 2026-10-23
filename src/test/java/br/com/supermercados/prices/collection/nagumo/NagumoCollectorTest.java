package br.com.supermercados.prices.collection.nagumo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import br.com.supermercados.prices.collection.CollectedCatalog;
import br.com.supermercados.prices.collection.CollectedProduct;
import tools.jackson.databind.json.JsonMapper;

class NagumoCollectorTest {

    private static final Instant NOW = Instant.parse("2026-09-17T12:00:00Z");
    private static final String STORES_PATH =
            "/on/demandware.store/Sites-Nagumo-Site/pt_BR/Stores-AvailableStores";
    private static final String SELECT_PATH =
            "/on/demandware.store/Sites-Nagumo-Site/pt_BR/Stores-SelectStore";
    private static final String SEARCH_PATH =
            "/on/demandware.store/Sites-Nagumo-Site/pt_BR/Search-UpdateGrid";

    private final AtomicInteger storeRequests = new AtomicInteger();
    private final AtomicBoolean sessionCookieReceived = new AtomicBoolean();
    private final AtomicBoolean storeSelected = new AtomicBoolean();
    private HttpServer server;
    private NagumoProperties properties;
    private String catalogResponse;

    @BeforeEach
    void setUp() throws IOException {
        catalogResponse = catalogJson();
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", this::home);
        server.createContext(STORES_PATH, this::stores);
        server.createContext(SELECT_PATH, this::selectStore);
        server.createContext(SEARCH_PATH, this::catalog);
        server.start();

        properties = new NagumoProperties();
        properties.setBaseUrl(URI.create("http://127.0.0.1:" + server.getAddress().getPort()));
        properties.setRequestDelay(Duration.ZERO);
        properties.setMaxAttempts(2);
    }

    @AfterEach
    void tearDown() {
        server.stop(0);
    }

    @Test
    void selectsTheExactStoreRetriesTransientFailureAndParsesControlledCatalog() {
        NagumoCollector collector = collector();

        CollectedCatalog catalog = collector.collect();

        assertThat(storeRequests).hasValue(4);
        assertThat(sessionCookieReceived).isTrue();
        assertThat(storeSelected).isTrue();
        assertThat(catalog.store().name()).isEqualTo("Nagumo Ponte Alta");
        assertThat(catalog.foundCount()).isEqualTo(3);
        assertThat(catalog.products()).hasSize(2);
        assertThat(catalog.warnings()).singleElement().asString().contains("266999", "preço normal");

        CollectedProduct memberPrice = product(catalog, "nagumo:product:266016");
        assertThat(memberPrice.regularPrice()).isEqualByComparingTo("29.98");
        assertThat(memberPrice.promotionalPrice()).isEqualByComparingTo("19.98");
        assertThat(memberPrice.promotionCondition()).contains("Meu Nagumo");
        assertThat(memberPrice.validUntil()).isNull();

        CollectedProduct publicPromotion = product(catalog, "nagumo:product:264455");
        assertThat(publicPromotion.regularPrice()).isEqualByComparingTo("35.95");
        assertThat(publicPromotion.promotionalPrice()).isEqualByComparingTo("28.95");
        assertThat(publicPromotion.promotionCondition()).isNull();
    }

    @Test
    void rejectsInvalidJsonInsteadOfTreatingTheSourceAsAnEmptyCatalog() {
        catalogResponse = "{invalid-json";

        assertThatThrownBy(() -> collector().collect())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("JSON inválido");
    }

    @Test
    void rejectsUnexpectedStoreIdentityBeforeCollectingPrices() {
        properties.setStoreName("Outra filial");

        assertThatThrownBy(() -> collector().collect())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Identidade inesperada");
        assertThat(storeSelected).isFalse();
    }

    private NagumoCollector collector() {
        var objectMapper = JsonMapper.builder().build();
        NagumoClient client = new NagumoClient(properties, objectMapper);
        NagumoProductParser parser = new NagumoProductParser(properties);
        Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
        return new NagumoCollector(properties, client, parser, clock);
    }

    private CollectedProduct product(CollectedCatalog catalog, String reference) {
        return catalog.products().stream()
                .filter(product -> reference.equals(product.sourceReference()))
                .findFirst()
                .orElseThrow();
    }

    private void home(HttpExchange exchange) throws IOException {
        exchange.getResponseHeaders().add("Set-Cookie", "nagumo-session=test; Path=/");
        respond(exchange, 200, "text/html", "<html><body>Nagumo</body></html>");
    }

    private void stores(HttpExchange exchange) throws IOException {
        sessionCookieReceived.set(exchange.getRequestHeaders().getFirst("Cookie") != null);
        if (storeRequests.incrementAndGet() == 1) {
            respond(exchange, 503, "application/json", "{}");
            return;
        }
        respond(exchange, 200, "application/json", storesJson());
    }

    private void selectStore(HttpExchange exchange) throws IOException {
        String query = exchange.getRequestURI().getQuery();
        storeSelected.set(query != null && query.contains("storeId=36")
                && query.contains("lat=-22.5295573") && query.contains("lng=-44.1360020"));
        respond(exchange, 200, "application/json", "{\"success\":true}");
    }

    private void catalog(HttpExchange exchange) throws IOException {
        respond(exchange, 200, "application/json", catalogResponse);
    }

    private void respond(HttpExchange exchange, int status, String contentType, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", contentType + "; charset=UTF-8");
        exchange.sendResponseHeaders(status, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }

    private String storesJson() {
        return """
                {
                  "stores": [{
                    "storeName": "036-V.REDONDA",
                    "storeId": "36",
                    "active": true,
                    "operation": true,
                    "address": "VIA SERGIO BRAGA, PONTE ALTA, VOLTA REDONDA, RJ",
                    "city": "VOLTA REDONDA",
                    "district": "PONTE ALTA"
                  }]
                }
                """;
    }

    private String catalogJson() {
        return """
                {
                  "productSearch": {
                    "count": 2,
                    "category": {"name": "Produtos Nagumo", "id": "MP-GERAL"}
                  },
                  "productsSearchResult": [
                    {
                      "id": "266016",
                      "productName": "Papel Higiênico Folha Dupla Nagumo 30M 16Un",
                      "brand": "NAGUMO",
                      "available": true,
                      "price": {"sales": {"value": 29.98}, "list": null},
                      "flagtypes": [{"flagType": "NGM_36_M", "valueFlag": 19.98}]
                    },
                    {
                      "id": "264455",
                      "productName": "Lava Roupas em Pó Nagumo 4kg",
                      "brand": "NAGUMO",
                      "available": true,
                      "price": {"sales": {"value": 28.95}, "list": {"value": 35.95}},
                      "flagtypes": []
                    },
                    {
                      "id": "266999",
                      "productName": "Produto com preço inválido",
                      "available": true,
                      "price": {"sales": {"value": 0}},
                      "flagtypes": []
                    }
                  ]
                }
                """;
    }
}
