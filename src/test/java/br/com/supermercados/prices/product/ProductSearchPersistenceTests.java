package br.com.supermercados.prices.product;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.transaction.annotation.Transactional;

import br.com.supermercados.prices.common.ApiException;
import br.com.supermercados.prices.datasource.SourceObservation;
import br.com.supermercados.prices.support.PostgresTestDatabase;
import jakarta.persistence.EntityManager;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
@Sql("/fixtures/catalog.sql")
class ProductSearchPersistenceTests {

    private static final UUID SOURCE = UUID.fromString("00000000-0000-0000-0000-000000000101");
    private static final UUID STORE = UUID.fromString("00000000-0000-0000-0000-000000000201");
    private static final PageRequest PAGE = PageRequest.of(0, 20);

    @Autowired ProductService products;
    @Autowired ProductIngestionService ingestion;
    @Autowired EntityManager entityManager;
    @Autowired JdbcTemplate jdbc;

    private ProductResponse original;
    private ProductResponse zero;
    private ProductResponse twoLiters;
    private ProductResponse patinho;

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        PostgresTestDatabase.register(registry);
    }

    @BeforeEach
    void catalog() {
        // Transactional fixtures only; nothing is inserted in the user's database.
        original = product("Coca-Cola Original 1L", "Coca-Cola", "Bebidas");
        zero = product("Coca Cola Sem Açúcar 1L", "Coca-Cola", "Refrigerantes");
        twoLiters = product("Coca-Cola Original 2L", "Coca-Cola", "Refrigerantes");
        product("Kit Coca-Cola Original 1L + Fanta 1L", "Coca-Cola", "Bebidas");
        product("Bebida mista alcoólica Coca Cola Jack Daniels 269ml", "Jack Daniels", "Bebidas");
        patinho = product("Patinho Resfriado 1kg", null, "Carne Bovina");
        product("Arroz Branco Teste 5kg", "Teste", "Arroz");
        product("Detergente Ypê Neutro 500ml", "Ypê", "Limpeza");
        product("Café Teste 500g", "Teste", "Mercearia");
        entityManager.flush();
        entityManager.clear();
    }

    @ParameterizedTest
    @ValueSource(strings = {"coca cola", "coca-cola", "COCA COLA", "cocacola", "coca coala", "coca-col"})
    void findsProductsWithPunctuationCaseAndSmallTypos(String query) {
        assertThat(search(query)).extracting(ProductResponse::id).contains(original.id(), zero.id());
    }

    @ParameterizedTest
    @ValueSource(strings = {"coca cola 1l", "coca cola 1 litro", "1 litro coca cola", "coca 1000ml",
            "coca cola 1 l", "coca 1litro", "coca cola 1000 ml", "refrigerante coca cola 1l"})
    void ranksRequestedSizeBeforeOtherSizesAndKits(String query) {
        List<ProductResponse> found = search(query);
        assertThat(found).hasSizeGreaterThanOrEqualTo(3);
        assertThat(found.subList(0, 2)).extracting(ProductResponse::id).containsExactlyInAnyOrder(original.id(), zero.id());
        assertThat(found).extracting(ProductResponse::id).contains(twoLiters.id());
    }

    @ParameterizedTest
    @ValueSource(strings = {"carne bovina", "carne de boi", "bovino", "patinho", "carne patinho", "patinho bovino"})
    void searchesClassifiedBeefWithoutGuessingIdentity(String query) {
        assertThat(search(query)).extracting(ProductResponse::id).contains(patinho.id());
    }

    @ParameterizedTest
    @ValueSource(strings = {"detergente ype", "detergente ipê", "detergentes ipe"})
    void normalizesAccentsAndCentralAliases(String query) {
        assertThat(search(query)).extracting(ProductResponse::name).containsExactly("Detergente Ypê Neutro 500ml");
    }

    @Test
    void recognizesMassAndVolumeWithoutMixingThem() {
        assertThat(search("arroz 5kg")).extracting(ProductResponse::name).containsExactly("Arroz Branco Teste 5kg");
        assertThat(search("cafe 0,5kg")).extracting(ProductResponse::name).containsExactly("Café Teste 500g");
        assertThat(search("coca 2000ml").getFirst().id()).isEqualTo(twoLiters.id());
        assertThat(ProductSearchTerms.parse("500ml").unit()).isEqualTo("ML");
        assertThat(ProductSearchTerms.parse("500g").unit()).isEqualTo("G");
        assertThat(ProductSearchTerms.parse("kit 1l 2l").quantity()).isNull();
    }

    @Test
    void filtersAndPaginatesTheSameRankedCatalog() {
        var filter = new ProductSearch("coca", "Coca-Cola", null, null, null, "ML", new BigDecimal("1000"), "relevance");
        var page = products.search(filter, PageRequest.of(0, 1));
        var second = products.search(filter, PageRequest.of(1, 1));
        assertThat(page.getTotalElements()).isEqualTo(3);
        assertThat(page.getContent().getFirst().id()).isEqualTo(original.id());
        assertThat(second.getContent().getFirst().id()).isEqualTo(zero.id());
        assertThat(search("zzzzxyzsemproduto")).isEmpty();
    }

    @Test
    void exactProductNameBeatsANameMerelyContainingThoseWords() {
        var exact = product("Café Teste", "Teste", "Mercearia");
        product("Biscoito recheado café Teste 100g", "Teste", "Biscoitos");
        entityManager.flush();
        assertThat(search("cafe teste").getFirst().id()).isEqualTo(exact.id());
    }

    @Test
    void rejectsDistantSpellingAndDoesNotClassifyOtherAnimalsAsBeef() {
        var otherCut = product("Carne Bovino Lagartinho 1kg", null, "Açougue");
        var pork = product("Carne Alcatra Suína 1kg", null, "Açougue");
        entityManager.flush();
        assertThat(search("patinho bovino")).extracting(ProductResponse::id).doesNotContain(otherCut.id(), pork.id());
        assertThat(search("carne bovina")).extracting(ProductResponse::id).doesNotContain(pork.id());
    }

    @Test
    void findsRetailerAbbreviationsUsingOrdinaryWords() {
        var detergent = product("Deterg. Ype 500ml Neutro", null, "Limpeza");
        var soda = product("Refr. Coca-Cola 1L Pet", null, "Bebidas");
        entityManager.flush();
        assertThat(search("detergente ipe")).extracting(ProductResponse::id).contains(detergent.id());
        assertThat(search("refrigerante coca cola")).extracting(ProductResponse::id).contains(soda.id());
    }

    @Test
    void returnsFacetsFromMatchingDataAndPicksUpNewIngestions() {
        var facets = products.searchFacets(new ProductSearch("coca", null, null, null));
        assertThat(facets.brands()).contains("Coca-Cola").doesNotContain("Ypê");
        assertThat(facets.measurements()).anySatisfy(size -> {
            assertThat(size.unit()).isEqualTo("ML");
            assertThat(size.quantity()).isEqualByComparingTo("1000");
        });
        var newProduct = product("Produto recém coletado pesquisável", "Nova marca", "Nova categoria");
        entityManager.flush();
        assertThat(search("recem pesquisavel")).extracting(ProductResponse::id).containsExactly(newProduct.id());
    }

    @Test
    void priceSortingUsesNewestValidObservationAndKeepsMissingPricesLast() {
        price(original.id(), "10.00", "AVAILABLE", Instant.now().minusSeconds(60));
        price(zero.id(), "1.00", "AVAILABLE", Instant.now().minusSeconds(120));
        price(zero.id(), "2.00", "UNAVAILABLE", Instant.now().minusSeconds(30));
        price(twoLiters.id(), "15.00", "AVAILABLE", Instant.now().minusSeconds(60));
        var ascending = new ProductSearch("coca", null, null, null, STORE, null, null, "price_asc");
        var descending = new ProductSearch("coca", null, null, null, STORE, null, null, "price_desc");
        assertThat(products.search(ascending, PAGE).getContent()).extracting(ProductResponse::id)
                .containsExactly(original.id(), twoLiters.id(), zero.id());
        assertThat(products.search(descending, PAGE).getContent()).extracting(ProductResponse::id)
                .containsExactly(twoLiters.id(), original.id(), zero.id());
    }

    @Test
    void rejectsUnsupportedSortAndNeverInterpolatesUserTextAsSql() {
        assertThatThrownBy(() -> new ProductSearch(null, null, null, null, null, null, null, "id; drop table products"))
                .isInstanceOf(ApiException.class);
        long count = jdbc.queryForObject("SELECT count(*) FROM products", Long.class);
        assertThat(search("' OR 1=1 --")).hasSizeLessThan((int) count);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM products", Long.class)).isEqualTo(count);
    }

    private List<ProductResponse> search(String query) {
        return products.search(new ProductSearch(query, null, null, null), PAGE).getContent();
    }

    private ProductResponse product(String name, String brand, String category) {
        return ingestion.ingest(new ProductObservation(null, name, brand, null, null, null, category,
                new SourceObservation(SOURCE, UUID.randomUUID().toString(), Instant.now())));
    }

    private void price(UUID product, String price, String availability, Instant collectedAt) {
        jdbc.update("""
                INSERT INTO price_records (id, product_id, store_id, source_id, source_reference,
                    regular_price, currency, collected_at, recorded_at, availability)
                VALUES (?, ?, ?, ?, ?, ?, 'BRL', ?, CURRENT_TIMESTAMP, ?)
                """, UUID.randomUUID(), product, STORE, SOURCE, UUID.randomUUID().toString(),
                new BigDecimal(price), java.sql.Timestamp.from(collectedAt), availability);
    }
}
