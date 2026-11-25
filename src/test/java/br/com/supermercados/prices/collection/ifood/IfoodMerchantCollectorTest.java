package br.com.supermercados.prices.collection.ifood;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import br.com.supermercados.prices.collection.ServedCity;
import tools.jackson.databind.ObjectMapper;

class IfoodMerchantCollectorTest {

    private static final Instant NOW = Instant.parse("2026-11-25T12:00:00Z");

    @TempDir
    Path checkpoints;

    private HttpServer server;
    private final List<String> requests = new CopyOnWriteArrayList<>();
    private int categoryStatus = 200;
    private String previousCheckpoints;

    @BeforeEach
    void start() throws IOException {
        previousCheckpoints = System.setProperty("gomo.collection.checkpoints", checkpoints.toString());
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", this::respond);
        server.start();
    }

    @AfterEach
    void stop() {
        server.stop(0);

        if (previousCheckpoints == null) {
            System.clearProperty("gomo.collection.checkpoints");

            return;
        }

        System.setProperty("gomo.collection.checkpoints", previousCheckpoints);
    }

    @Test
    void collectsEveryCorridorExceptCombosAndKeepsSkippedItemsAsWarnings() {
        var catalog = collector().collect();

        assertThat(catalog.products())
                .extracting(product -> product.name())
                .containsExactly(
                    "Pizza Frango com Requeijão e Mussarela Sadia 460g",
                    "Barra de Chocolate Branco e Ao Leite Lacta Diamante Negro e Laka 80g");

        assertThat(catalog.foundCount()).isEqualTo(4);
        assertThat(catalog.warnings()).hasSize(2).allMatch(warning -> warning.startsWith("Produto iFood "));
        assertThat(catalog.store().name()).isEqualTo("Supermarket Floresta Aterrado");
        assertThat(catalog.store().address()).contains("Paulo de Frontin, 1096");

        assertThat(requests).containsExactly(
            "/site-api/v1/merchants/multicategory/964260c7-c1e2-4a1f-8f2b-5a053c9e09b1/catalog"
                    + "?latitude=-22.512595&longitude=-44.088170&category_items_size=1",
            "/site-api/v1/merchants/multicategory/964260c7-c1e2-4a1f-8f2b-5a053c9e09b1/catalog"
                    + "/969e9e24-401f-4045-96fd-45a0e8ae54ad?items_page=1&items_size=50");
    }

    @Test
    void failsWhenTheSiteBlocksTheCatalog() {
        categoryStatus = 403;

        assertThatThrownBy(() -> collector().collect())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("HTTP 403");
    }

    @Test
    void identifiesTheStoreAsAnIfoodSourceInVoltaRedonda() {
        var metadata = collector().metadata();

        assertThat(metadata.code()).isEqualTo("supermarket_floresta_aterrado");
        assertThat(metadata.sourceCode()).isEqualTo("ifood_delivery");
        assertThat(metadata.storeSourceReference()).isEqualTo("ifood:merchant:964260c7-c1e2-4a1f-8f2b-5a053c9e09b1");
        assertThat(metadata.cityId()).isEqualTo(ServedCity.VOLTA_REDONDA.id());
    }

    private IfoodMerchantCollector collector() {
        return new IfoodMerchantCollector(
            IfoodCollectorsConfiguration.SUPERMARKET_FLORESTA,
            URI.create("http://127.0.0.1:" + server.getAddress().getPort()),
            new ObjectMapper(),
            Clock.fixed(NOW, ZoneOffset.UTC),
            Duration.ZERO);
    }

    private void respond(HttpExchange exchange) throws IOException {
        String path = exchange.getRequestURI().getPath();
        requests.add(path + "?" + exchange.getRequestURI().getQuery());

        if (path.endsWith("/catalog")) {
            write(exchange, 200, fixture("ifood-floresta-menu.json"));

            return;
        }

        if (path.endsWith("/catalog/969e9e24-401f-4045-96fd-45a0e8ae54ad")) {
            write(exchange, categoryStatus, categoryStatus == 200 ? fixture("ifood-floresta-category.json") : "{}");

            return;
        }

        write(exchange, 404, "{}");
    }

    private String fixture(String name) throws IOException {
        try (InputStream input = getClass().getResourceAsStream("/collectors/regional/" + name)) {
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private void write(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }
}
