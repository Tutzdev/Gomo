package br.com.supermercados.prices;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Transactional;

import br.com.supermercados.prices.collection.CollectedCatalog;
import br.com.supermercados.prices.collection.CollectedCatalogIngestionService;
import br.com.supermercados.prices.collection.CollectedProduct;
import br.com.supermercados.prices.collection.CollectedStore;
import br.com.supermercados.prices.collection.CollectorMetadata;
import br.com.supermercados.prices.price.StockAvailability;
import br.com.supermercados.prices.support.PostgresTestDatabase;

/** A synthetic source and store in a rolled-back, isolated PostgreSQL schema. */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class PriceChangeConfirmationIntegrationTests {

    private static final UUID VOLTA_REDONDA = UUID.fromString("5c4cb935-52e1-4bf8-8d17-902dc0837c66");
    private static final CollectorMetadata METADATA = new CollectorMetadata("synthetic_market", "Mercado Sintético",
            "Mercado Sintético Centro", "synthetic_source", "Fonte sintética", "https://source.example.test",
            Instant.parse("2026-10-01T00:00:00Z"), "Mercado Sintético", "synthetic:chain", "synthetic:store:1", VOLTA_REDONDA);

    @Autowired private CollectedCatalogIngestionService ingestion;
    @Autowired private JdbcTemplate jdbc;

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        PostgresTestDatabase.register(registry);
    }

    @Test
    void aPriceChangeSeenByTwoCollectionsIsPublished() {
        Instant first = Instant.now().minus(Duration.ofHours(10));
        ingestion.ingest(METADATA, catalog(first, "10.00"));

        var held = ingestion.ingest(METADATA, catalog(first.plus(Duration.ofHours(5)), "100.00"));
        assertThat(held.errorMessage()).contains("Variação suspeita");
        assertThat(latestPrice()).isEqualByComparingTo("10.00");

        var confirmed = ingestion.ingest(METADATA, catalog(first.plus(Duration.ofHours(10)), "100.00"));
        assertThat(confirmed.errorMessage()).isNull();
        assertThat(latestPrice()).isEqualByComparingTo("100.00");
    }

    @Test
    void aTypoTheStoreFixesIsNeverPublished() {
        Instant first = Instant.now().minus(Duration.ofHours(10));
        ingestion.ingest(METADATA, catalog(first, "10.00"));
        ingestion.ingest(METADATA, catalog(first.plus(Duration.ofHours(5)), "100.00"));

        ingestion.ingest(METADATA, catalog(first.plus(Duration.ofHours(10)), "10.50"));

        assertThat(jdbc.queryForList("SELECT regular_price FROM price_records ORDER BY collected_at", BigDecimal.class))
                .extracting(BigDecimal::toPlainString).containsExactly("10.00", "10.50");
    }

    private BigDecimal latestPrice() {
        return jdbc.queryForObject("SELECT regular_price FROM price_records ORDER BY collected_at DESC LIMIT 1", BigDecimal.class);
    }

    private static CollectedCatalog catalog(Instant collectedAt, String price) {
        var product = new CollectedProduct("synthetic:product:1", "Café Sintético Tradicional 500g", null, "Sintético",
                null, "Mercearia", new BigDecimal(price), null, null, null, null, StockAvailability.AVAILABLE);
        return new CollectedCatalog(new CollectedStore("Mercado Sintético Centro", "Rua Sintética, 1 - Centro - Volta Redonda/RJ",
                null, null, true), List.of(product), 1, collectedAt, List.of());
    }
}
