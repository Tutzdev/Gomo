package br.com.supermercados.prices;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
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
import br.com.supermercados.prices.collection.CollectionResult;
import br.com.supermercados.prices.collection.CollectorMetadata;
import br.com.supermercados.prices.price.StockAvailability;
import br.com.supermercados.prices.support.PostgresTestDatabase;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class CollectionPersistenceTests {

    private static final UUID VOLTA_REDONDA_ID =
            UUID.fromString("5c4cb935-52e1-4bf8-8d17-902dc0837c66");
    private static final Instant COLLECTED_AT = Instant.parse("2025-09-17T10:00:00Z");
    private static final Instant VALID_UNTIL = Instant.parse("2025-09-18T03:00:00Z");

    @Autowired
    CollectedCatalogIngestionService ingestion;

    @Autowired
    JdbcTemplate jdbc;

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        PostgresTestDatabase.register(registry);
    }

    @Test
    void collectionIsIdempotentPreservesHistoryAndRejectsSuspiciousChange() {
        CollectionResult first = ingestion.ingest(metadata(), catalog("10.00", "8.00", COLLECTED_AT));
        CollectionResult repeated = ingestion.ingest(metadata(), catalog("10.00", "8.00", COLLECTED_AT));
        CollectionResult changed = ingestion.ingest(
                metadata(), catalog("9.50", "7.50", COLLECTED_AT.plusSeconds(60)));
        CollectionResult reverted = ingestion.ingest(
                metadata(), catalog("10.00", "8.00", COLLECTED_AT.plusSeconds(90)));
        CollectionResult suspicious = ingestion.ingest(
                metadata(), catalog("100.00", null, COLLECTED_AT.plusSeconds(120)));

        assertThat(first.createdCount()).isEqualTo(1);
        assertThat(first.updatedCount()).isEqualTo(1);
        assertThat(repeated.createdCount()).isZero();
        assertThat(repeated.updatedCount()).isZero();
        assertThat(repeated.skippedCount()).isEqualTo(1);
        assertThat(changed.updatedCount()).isEqualTo(1);
        assertThat(reverted.updatedCount()).isEqualTo(1);
        assertThat(suspicious.updatedCount()).isZero();
        assertThat(suspicious.skippedCount()).isEqualTo(1);
        // Held for review by the rules, not a collection error: the run stays successful.
        assertThat(suspicious.errorCount()).isZero();
        assertThat(suspicious.skipSummary()).isEqualTo("variação suspeita de preço: 1");

        assertThat(count("data_sources", "code = 'synthetic_collection'")).isEqualTo(1);
        assertThat(count("supermarket_chains", "name = 'Rede sintética de coleta'")).isEqualTo(1);
        assertThat(count("stores", "name = 'Loja sintética de Volta Redonda'")).isEqualTo(1);
        assertThat(count("products", "source_reference = 'synthetic:product:1'")).isEqualTo(1);
        assertThat(count("price_records", "promotion_condition = 'Preço de clube controlado'")).isEqualTo(3);
        assertThat(jdbc.queryForObject("""
                select normalized_name from products where source_reference = 'synthetic:product:1'
                """, String.class)).isEqualTo("CAFE SINTETICO 500G");
        assertThat(jdbc.queryForObject("""
                select quantity from products where source_reference = 'synthetic:product:1'
                """, BigDecimal.class)).isEqualByComparingTo("500");
    }

    @Test
    void productsLeftOutByTheRulesAreSkipsWhileAnUnfinishedCategoryIsAnError() {
        CollectedCatalog collected = catalog("10.00", null, COLLECTED_AT.plusSeconds(600));
        CollectionResult result = ingestion.ingest(metadata(), new CollectedCatalog(collected.store(),
                collected.products(), 4, collected.collectedAt(), List.of(
                        "Produto Nagumo 1 ignorado: peso médio ausente ou inválido",
                        "Produto Nagumo 2 ignorado: peso médio ausente ou inválido",
                        "Produto Atacadão 3: Preço inválido",
                        "Categoria Nagumo bebidas incompleta: tempo esgotado")));

        assertThat(result.errorCount()).isEqualTo(1);
        assertThat(result.errorMessage()).isEqualTo("Categoria Nagumo bebidas incompleta: tempo esgotado");
        assertThat(result.skipSummary()).isEqualTo("peso médio ausente ou inválido: 2 | Preço inválido: 1");
    }

    private int count(String table, String condition) {
        return jdbc.queryForObject("select count(*) from " + table + " where " + condition, Integer.class);
    }

    private CollectorMetadata metadata() {
        return new CollectorMetadata(
                "synthetic_collection",
                "Supermercado sintético",
                "Loja sintética de Volta Redonda",
                "synthetic_collection",
                "Fonte sintética para teste",
                "https://collection.example.test",
                Instant.parse("2025-01-01T00:00:00Z"),
                "Rede sintética de coleta",
                "synthetic:chain",
                "synthetic:store:1",
                VOLTA_REDONDA_ID);
    }

    private CollectedCatalog catalog(String regularPrice, String promotionalPrice, Instant collectedAt) {
        CollectedStore store = new CollectedStore(
                "Loja sintética de Volta Redonda",
                "Endereço sintético, Volta Redonda/RJ",
                new BigDecimal("-22.5000000"),
                new BigDecimal("-44.1000000"),
                true);
        CollectedProduct product = new CollectedProduct(
                "synthetic:product:1",
                "Café Sintético 500g",
                null,
                "Marca Sintética",
                "Produto exclusivamente sintético para teste",
                "Mercearia sintética",
                new BigDecimal(regularPrice),
                promotionalPrice == null ? null : new BigDecimal(promotionalPrice),
                promotionalPrice == null ? null : "Preço de clube controlado",
                VALID_UNTIL,
                promotionalPrice == null ? null : VALID_UNTIL,
                StockAvailability.AVAILABLE);
        return new CollectedCatalog(store, List.of(product), 1, collectedAt, List.of());
    }
}
