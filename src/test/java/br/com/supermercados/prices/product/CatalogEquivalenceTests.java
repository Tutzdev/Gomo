package br.com.supermercados.prices.product;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Timestamp;
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
import org.springframework.test.context.jdbc.Sql;
import org.springframework.transaction.annotation.Transactional;

import br.com.supermercados.prices.support.PostgresTestDatabase;

/**
 * A shopping list must price an item with the same SKUs the product page shows. When the generic catalog grouped
 * a product, its decision wins over the older name matching, which lacks the catalog's safety checks.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
@Sql("/fixtures/catalog.sql")
class CatalogEquivalenceTests {

    private static final UUID SOURCE_ID = UUID.fromString("00000000-0000-0000-0000-000000000101");
    private static final String NAME = "Refrigerante Coca-Cola Pet 2L";

    @Autowired private ProductEquivalenceService equivalence;
    @Autowired private JdbcTemplate jdbc;

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        PostgresTestDatabase.register(registry);
    }

    @Test
    void productsTheCatalogKeptApartAreOnlyPossibleMatches() {
        UUID first = sku(NAME);
        UUID second = sku(NAME);
        // e.g. one store sells both descriptions, so the catalog treats them as different products.
        catalogItem("COCA COLA|1x2000ML", first);
        catalogItem("COCA COLA|1x2000ML#2", second);

        var matches = equivalence.find(List.of(first)).get(first);

        assertThat(matches.confirmed()).extracting(Product::getId).containsExactly(first);
        assertThat(matches.possible()).extracting(Product::getId).contains(second);
    }

    @Test
    void membersOfTheSameCatalogItemAreConfirmedEvenWithDifferentNames() {
        UUID cut = sku("Refr Coca-cola 2lt Pet");
        UUID complete = sku(NAME);
        catalogItem("COCA COLA|1x2000ML", cut, complete);

        var matches = equivalence.find(List.of(cut)).get(cut);

        assertThat(matches.confirmed()).extracting(Product::getId).containsExactlyInAnyOrder(cut, complete);
        assertThat(matches.identityConfirmed()).isTrue();
    }

    @Test
    void productsOutsideTheCatalogKeepTheNameMatching() {
        UUID first = sku(NAME);
        UUID second = sku(NAME);

        var matches = equivalence.find(List.of(first)).get(first);

        assertThat(matches.confirmed()).extracting(Product::getId).contains(first, second);
    }

    private UUID sku(String name) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO products (id, name, brand, unit, quantity, comparison_family, comparison_identity_version,
                    source_id, source_reference, collected_at, updated_at)
                VALUES (?, ?, 'Coca-Cola', 'L', 2, ?, 4, ?, ?, ?, ?)
                """, id, name, ProductComparisonIdentity.searchFamily(name), SOURCE_ID, "synthetic-sku-" + id,
                recently(), recently());
        return id;
    }

    private void catalogItem(String key, UUID... members) {
        UUID item = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO catalog_items (id, catalog_key, display_name, size_label, representative_product_id,
                    search_text, store_count, active, updated_at)
                VALUES (?, ?, 'Coca-Cola 2 L', '2 L', ?, 'COCA COLA', 2, TRUE, ?)
                """, item, key, members[0], recently());
        for (UUID member : members) {
            jdbc.update("INSERT INTO catalog_item_products (catalog_item_id, product_id) VALUES (?, ?)", item, member);
        }
    }

    private static Timestamp recently() {
        return Timestamp.from(Instant.now().minusSeconds(5));
    }
}
