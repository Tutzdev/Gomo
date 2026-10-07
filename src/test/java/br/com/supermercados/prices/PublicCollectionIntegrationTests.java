package br.com.supermercados.prices;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import br.com.supermercados.prices.collection.CollectedCatalog;
import br.com.supermercados.prices.collection.CollectedCatalogIngestionService;
import br.com.supermercados.prices.collection.nagumo.NagumoCollector;
import br.com.supermercados.prices.collection.royal.RoyalCollector;
import br.com.supermercados.prices.support.CollectorFixtureServer;
import br.com.supermercados.prices.support.PostgresTestDatabase;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class PublicCollectionIntegrationTests {

    private static final CollectorFixtureServer SOURCE = startSource();

    @Autowired NagumoCollector nagumo;
    @Autowired RoyalCollector royal;
    @Autowired CollectedCatalogIngestionService ingestion;
    @Autowired JdbcTemplate jdbc;
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;

    @DynamicPropertySource
    static void configuration(DynamicPropertyRegistry registry) {
        PostgresTestDatabase.register(registry);
        registry.add("app.collection.nagumo.enabled", () -> true);
        registry.add("app.collection.nagumo.base-url", SOURCE::baseUrl);
        registry.add("app.collection.nagumo.category-id", () -> "PARCEIRO-COCA-COLA");
        registry.add("app.collection.nagumo.additional-category-ids", () -> "");
        registry.add("app.collection.nagumo.request-delay", () -> "PT0S");
        registry.add("app.collection.royal.enabled", () -> true);
        registry.add("app.collection.royal.base-url", SOURCE::baseUrl);
        registry.add("app.collection.royal.api-url", SOURCE::baseUrl);
        registry.add("app.collection.royal.public-configuration-path", () -> "/public-config.js");
        registry.add("app.collection.royal.search-terms", () -> "coca");
        registry.add("app.collection.royal.request-delay", () -> "PT0S");
    }

    @AfterAll
    static void closeSource() {
        SOURCE.close();
    }

    @Test
    void cleanMigratedDatabaseCollectsTwoPublicSnapshotsAndComparesOnlyVerifiedProducts() throws Exception {
        assertThat(jdbc.queryForObject("select count(*) from products", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("select count(*) from flyway_schema_history where success", Integer.class))
                .isGreaterThanOrEqualTo(12);
        CollectedCatalog nagumoCatalog = nagumo.collect();
        var first = ingestion.ingest(nagumo.metadata(), nagumoCatalog);
        CollectedCatalog royalCatalog = royal.collect();
        var second = ingestion.ingest(royal.metadata(), royalCatalog);
        // The two items the store lists without a price are unavailable: neither offers nor collection errors.
        assertThat(first.errorCount()).isZero();
        assertThat(second.errorCount()).isZero();
        assertThat(second.createdCount()).isEqualTo(26);
        assertThat(jdbc.queryForObject("select count(*) from stores", Integer.class)).isEqualTo(3);
        int prices = jdbc.queryForObject("select count(*) from price_records", Integer.class);
        assertThat(ingestion.ingest(nagumo.metadata(), nagumoCatalog).skippedCount())
                .isEqualTo(first.updatedCount() + first.errorCount());
        assertThat(ingestion.ingest(royal.metadata(), royalCatalog).skippedCount()).isEqualTo(second.updatedCount());
        assertThat(jdbc.queryForObject("select count(*) from price_records", Integer.class)).isEqualTo(prices);

        UUID productId = jdbc.queryForObject("select product_id from product_source_references "
                + "where source_reference = 'royal:product:7508'", UUID.class);
        String comparison = mvc.perform(get("/api/v1/comparisons/products")
                        .param("productId", productId.toString())
                        .param("cityId", "5c4cb935-52e1-4bf8-8d17-902dc0837c66"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        var stores = mapper.readTree(comparison).path("stores").path("content");
        // Supermarket Aterrado has no public price source, so it is not part of the comparison.
        assertThat(stores.size()).isEqualTo(2);
        stores.forEach(store -> {
            assertThat(store.path("storeName").asString()).isNotEqualTo("Supermarket Aterrado");
            assertThat(store.path("price").path("status").asString()).isEqualTo("KNOWN");
        });
        mvc.perform(get("/api/v1/products").param("query", "7894900027013")).andExpect(status().isOk());
        mvc.perform(get("/api/v1/stores")).andExpect(status().isOk());
        // Price history is part of Premium (freemium, V31); a visitor gets the plan limit, not an error.
        mvc.perform(get("/api/v1/prices").param("productId", productId.toString())
                .param("storeId", first.storeId().toString())).andExpect(status().isForbidden());

        Instant observedAgain = Instant.now();
        var fresh = new CollectedCatalog(nagumoCatalog.store(), nagumoCatalog.products(),
                nagumoCatalog.foundCount(), observedAgain, nagumoCatalog.warnings());
        assertThat(ingestion.ingest(nagumo.metadata(), fresh).updatedCount()).isEqualTo(first.updatedCount());
        assertThat(ingestion.ingest(nagumo.metadata(), nagumoCatalog).updatedCount()).isZero();
    }

    private static CollectorFixtureServer startSource() {
        try {
            var server = new CollectorFixtureServer();
            server.cocaOnly = true;
            return server;
        } catch (java.io.IOException exception) {
            throw new IllegalStateException(exception);
        }
    }

    @Test
    void refreshingPricesDoesNotCreateProductsAndPreservesOriginalSourceNames() {
        var collected = nagumo.collect();
        var existing = new CollectedCatalog(collected.store(), collected.products().subList(0, 1),
                1, collected.collectedAt(), java.util.List.of());
        ingestion.ingest(nagumo.metadata(), existing);
        int before = jdbc.queryForObject("select count(*) from products", Integer.class);
        var refreshed = ingestion.refreshExisting(nagumo.metadata(), new CollectedCatalog(collected.store(),
                collected.products(), collected.foundCount(), Instant.now(), collected.warnings()));
        assertThat(refreshed.createdCount()).isZero();
        assertThat(jdbc.queryForObject("select count(*) from products", Integer.class)).isEqualTo(before);
        assertThat(refreshed.updatedCount()).isEqualTo(1);
        assertThat(jdbc.queryForObject("select count(*) from price_records where source_product_name is not null", Integer.class))
                .isGreaterThan(0);
    }

    @Test
    void refreshLinksNewRetailerReferenceAndKeepsIncompatibleOffersForReview() {
        var snapshot = nagumo.collect();
        var original = collectedProduct("original-a", "Coca Cola Original PET 2L");
        var first = ingestion.ingest(nagumo.metadata(), new CollectedCatalog(snapshot.store(),
                java.util.List.of(original), 1, Instant.now(), java.util.List.of()));
        var equivalent = collectedProduct("different-local-code", "Refrigerante Coca Cola Original PET 2000 ML");
        var incompatible = collectedProduct("zero-local-code", "Coca Cola Zero PET 2L");
        var secondSnapshot = new CollectedCatalog(royal.collect().store(),
                java.util.List.of(equivalent, incompatible), 2, Instant.now(), java.util.List.of());
        var second = ingestion.refreshExisting(royal.metadata(), secondSnapshot);

        assertThat(first.createdCount()).isEqualTo(1);
        assertThat(second.createdCount()).isZero();
        assertThat(second.updatedCount()).isEqualTo(1);
        assertThat(jdbc.queryForObject("select count(*) from products", Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("select count(distinct store_id) from price_records", Integer.class)).isEqualTo(2);
        assertThat(jdbc.queryForObject("select count(*) from collection_review_items", Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("select offer->>'name' from collection_review_items", String.class)).contains("Zero");
        assertThat(ingestion.refreshExisting(royal.metadata(), secondSnapshot).updatedCount()).isZero();
        assertThat(jdbc.queryForObject("select count(*) from price_records", Integer.class)).isEqualTo(2);
    }

    private br.com.supermercados.prices.collection.CollectedProduct collectedProduct(String reference, String name) {
        return new br.com.supermercados.prices.collection.CollectedProduct(reference, name, null, "Coca Cola",
                null, "Bebidas", new java.math.BigDecimal("8.99"), null, null, null, null,
                br.com.supermercados.prices.price.StockAvailability.AVAILABLE);
    }
}
