package br.com.supermercados.prices;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.transaction.annotation.Transactional;

import br.com.supermercados.prices.catalog.CatalogBuilder;
import br.com.supermercados.prices.common.ApiException;
import br.com.supermercados.prices.datasource.DataSourceService;
import br.com.supermercados.prices.datasource.SourceObservation;
import br.com.supermercados.prices.datasource.SourceRegistration;
import br.com.supermercados.prices.product.ProductIngestionService;
import br.com.supermercados.prices.product.ProductObservation;
import br.com.supermercados.prices.product.ProductResponse;
import br.com.supermercados.prices.product.ProductSearch;
import br.com.supermercados.prices.product.ProductService;
import br.com.supermercados.prices.support.PostgresTestDatabase;
import jakarta.persistence.EntityManager;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
@Sql("/fixtures/catalog.sql")
class CatalogPersistenceTests {

    private static final UUID SOURCE_ID = UUID.fromString("00000000-0000-0000-0000-000000000101");
    private static final UUID STORE_A = UUID.fromString("00000000-0000-0000-0000-000000000201");
    private static final UUID STORE_B = UUID.fromString("00000000-0000-0000-0000-000000000202");
    private static final Instant COLLECTED_AT = Instant.parse("2025-01-10T12:00:00Z");
    private static final PageRequest PAGE = PageRequest.of(0, 20, Sort.by("name", "id"));

    @Autowired
    ProductService products;

    @Autowired
    ProductIngestionService ingestion;

    @Autowired
    DataSourceService sources;

    @Autowired
    EntityManager entityManager;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    CatalogBuilder catalogBuilder;

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        PostgresTestDatabase.register(registry);
    }

    @Test
    void accentInsensitiveSearchFindsProductsBeyondInitialPages() {
        var coffee = ingestion.ingest(observation("Z Café torrado", null, SOURCE_ID, "coffee", COLLECTED_AT));
        flushAndClear();

        var result = products.search(new ProductSearch("cafe", null, null, null), PageRequest.of(0, 1));

        assertThat(result.getContent()).extracting(ProductResponse::id).containsExactly(coffee.id());
        assertThat(result.getTotalElements()).isEqualTo(1);
    }

    @Test
    void textSearchIgnoresCaseAndRespectsPagination() {
        var result = products.search(new ProductSearch("aLpHa", null, null, null), PAGE);

        assertThat(result.getContent()).extracting(ProductResponse::name).containsExactly("Produto FICTÍCIO Alpha");
        assertThat(result.getTotalElements()).isEqualTo(1);
        var paginated = products.search(new ProductSearch(null, null, null, null), PageRequest.of(0, 1));
        assertThat(paginated.getNumberOfElements()).isEqualTo(1);
        assertThat(paginated.getTotalElements()).isEqualTo(2);
    }

    @Test
    void treatsSqlWildcardAndEscapeCharactersAsLiteralSearchText() {
        var literal = ingestion.ingest(observation("SYNTHETIC 50%_! item", null, SOURCE_ID, "literal-item", COLLECTED_AT));
        ingestion.ingest(observation("SYNTHETIC 50abc item", null, SOURCE_ID, "wildcard-decoy", COLLECTED_AT));
        flushAndClear();

        for (String query : new String[] {"%", "_", "!", "50%_!"}) {
            var result = products.search(new ProductSearch(query, null, null, null), PAGE);
            assertThat(result.getContent()).extracting(ProductResponse::id).containsExactly(literal.id());
        }
    }

    @Test
    void combinesCaseInsensitiveBrandCategoryAndCanonicalGtinFilters() {
        var created = ingestion.ingest(observation("Synthetic filtered item", "0000000000017",
                SOURCE_ID, "filtered-item", COLLECTED_AT));
        flushAndClear();

        var matching = products.search(
                new ProductSearch("FILTERED", "sYnThEtIc bRaNd", "00000000000017", "synthetic category"), PAGE);
        var differentBrand = products.search(
                new ProductSearch(null, "Another synthetic brand", "0000000000017", null), PAGE);

        assertThat(matching.getContent()).extracting(ProductResponse::id).containsExactly(created.id());
        assertThat(differentBrand).isEmpty();
    }

    @Test
    void sharesIdentityAcrossSourcesOnlyWithTheSameValidatedGtin() {
        UUID otherSource = registerOtherSource();
        var first = ingestion.ingest(observation("Synthetic name at first source", "0000000000017",
                SOURCE_ID, "first-source-item", COLLECTED_AT));
        flushAndClear();

        var second = ingestion.ingest(observation("Different synthetic name at second source", "00000000000017",
                otherSource, "second-source-item", COLLECTED_AT.plusSeconds(60)));
        flushAndClear();

        assertThat(second.id()).isEqualTo(first.id());
        assertThat(jdbc.queryForObject("select count(*) from products where gtin = ?", Integer.class,
                "00000000000017")).isEqualTo(1);
        assertThat(jdbc.queryForObject("select count(*) from product_source_references where product_id = ?",
                Integer.class, first.id())).isEqualTo(2);
        assertThat(products.findProduct(first.id()).sourceId()).isEqualTo(SOURCE_ID);
    }

    @Test
    void identicalNamesWithoutGtinRemainSeparateProducts() {
        UUID otherSource = registerOtherSource();
        var first = ingestion.ingest(observation("Same synthetic name", null, SOURCE_ID, "same-name-a", COLLECTED_AT));
        var second = ingestion.ingest(observation("Same synthetic name", null, otherSource, "same-name-b", COLLECTED_AT));
        flushAndClear();

        assertThat(second.id()).isNotEqualTo(first.id());
        assertThat(products.search(new ProductSearch("Same synthetic name", null, null, null), PAGE)
                .getTotalElements()).isEqualTo(2);
    }

    @Test
    void updatesVerifiedReferenceOnlyWhenObservationIsNewer() {
        var first = ingestion.ingest(observation("Synthetic initial name", null, SOURCE_ID, "temporal-item", COLLECTED_AT));
        ingestion.ingest(observation("Synthetic latest name", null, SOURCE_ID,
                "temporal-item", COLLECTED_AT.plusSeconds(60)));
        flushAndClear();
        ingestion.ingest(observation("Synthetic stale name", null, SOURCE_ID,
                "temporal-item", COLLECTED_AT.minusSeconds(60)));
        flushAndClear();

        var persisted = products.findProduct(first.id());
        assertThat(persisted.name()).isEqualTo("Synthetic latest name");
        assertThat(persisted.collectedAt()).isEqualTo(COLLECTED_AT.plusSeconds(60));
    }

    @Test
    void rejectsConflictingGtinForExistingSourceReference() {
        ingestion.ingest(observation("Synthetic identified item", "0000000000017",
                SOURCE_ID, "conflicting-item", COLLECTED_AT));
        flushAndClear();

        assertThatThrownBy(() -> ingestion.ingest(observation("Synthetic conflicting item", "0000000000024",
                SOURCE_ID, "conflicting-item", COLLECTED_AT.plusSeconds(60))))
                .isInstanceOf(ApiException.class).hasMessageContaining("Identidade");
    }

    private UUID registerOtherSource() {
        return sources.registerVerifiedSource(new SourceRegistration("synthetic-secondary", "Synthetic test source",
                "https://secondary.example.test", COLLECTED_AT)).getId();
    }

    private ProductObservation observation(String name, String gtin, UUID sourceId, String reference, Instant collectedAt) {
        return new ProductObservation(gtin, name, "Synthetic Brand", "Synthetic test description", "UN",
                BigDecimal.ONE, "Synthetic Category", new SourceObservation(sourceId, reference, collectedAt));
    }

    private void flushAndClear() {
        entityManager.flush();
        entityManager.clear();
    }

    @Test
    void storesWritingTheSameProductDifferentlyShareOneCatalogItem() {
        UUID cut = sku("Acucar Granulado Uniao 1kg Pre");
        UUID complete = sku("Açúcar Granulado União Premium 1kg");
        price(cut, STORE_A, "Acucar Granulado Uniao 1kg Pre");
        price(complete, STORE_B, "Açúcar Granulado União Premium 1kg");

        catalogBuilder.rebuild();

        assertThat(catalogItemOf(cut)).isNotNull().isEqualTo(catalogItemOf(complete));
    }

    @Test
    void savedListItemFollowsItsSkuWhenTheCatalogKeyChanges() {
        UUID sugar = sku("Açúcar Granulado União Premium 1kg");
        price(sugar, STORE_A, "Açúcar Granulado União Premium 1kg");
        price(sugar, STORE_B, "Acucar Granulado Uniao 1kg Pre");
        UUID previousItem = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO catalog_items (id, catalog_key, display_name, size_label, representative_product_id,
                    search_text, store_count, active, updated_at)
                VALUES (?, 'KEY FROM AN OLDER RULE|1x1000G', 'Açúcar União Premium 1 kg', '1 kg', ?, 'ACUCAR', 2, TRUE, ?)
                """, previousItem, sugar, Timestamp.from(Instant.now().minusSeconds(60)));
        UUID listItem = listItem(sugar, previousItem);

        catalogBuilder.rebuild();

        assertThat(jdbc.queryForObject("SELECT active FROM catalog_items WHERE id = ?", Boolean.class, previousItem)).isFalse();
        assertThat(jdbc.queryForObject("SELECT catalog_item_id FROM shopping_list_items WHERE id = ?", UUID.class, listItem))
                .isEqualTo(catalogItemOf(sugar)).isNotEqualTo(previousItem);
    }

    private UUID sku(String name) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO products (id, name, brand, source_id, source_reference, collected_at, updated_at)
                VALUES (?, ?, 'União', ?, ?, ?, ?)
                """, id, name, SOURCE_ID, "synthetic-sku-" + id, recently(), recently());
        return id;
    }

    private void price(UUID product, UUID store, String storeDescription) {
        jdbc.update("""
                INSERT INTO price_records (id, product_id, store_id, source_id, source_reference, regular_price,
                    collected_at, recorded_at, source_product_name)
                VALUES (?, ?, ?, ?, ?, 5.49, ?, ?, ?)
                """, UUID.randomUUID(), product, store, SOURCE_ID, "synthetic-price-" + UUID.randomUUID(),
                recently(), recently(), storeDescription);
    }

    private UUID listItem(UUID product, UUID catalogItem) {
        UUID user = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO app_users (id, name, email, password_hash, created_at, updated_at)
                VALUES (?, 'Pessoa fictícia', ?, 'hash', ?, ?)
                """, user, "fictitious-" + user + "@example.test", recently(), recently());
        UUID list = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO shopping_lists (id, user_id, name, shopping_type, created_at, updated_at)
                VALUES (?, ?, 'Lista fictícia', 'WEEKLY', ?, ?)
                """, list, user, recently(), recently());
        UUID item = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO shopping_list_items (id, shopping_list_id, product_id, catalog_item_id, quantity)
                VALUES (?, ?, ?, ?, 1)
                """, item, list, product, catalogItem);
        return item;
    }

    private UUID catalogItemOf(UUID product) {
        return jdbc.query("SELECT catalog_item_id FROM catalog_item_products WHERE product_id = ?",
                (row, index) -> row.getObject(1, UUID.class), product).stream().findFirst().orElse(null);
    }

    private static Timestamp recently() {
        return Timestamp.from(Instant.now().minusSeconds(5));
    }
}
