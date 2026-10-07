package br.com.supermercados.prices;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import br.com.supermercados.prices.datasource.SourceObservation;
import br.com.supermercados.prices.price.PriceObservation;
import br.com.supermercados.prices.price.PriceService;
import br.com.supermercados.prices.price.StockAvailability;
import br.com.supermercados.prices.product.ProductComparisonIndex;
import br.com.supermercados.prices.product.ProductIngestionService;
import br.com.supermercados.prices.product.ProductObservation;
import br.com.supermercados.prices.product.ProductResponse;
import br.com.supermercados.prices.support.PostgresTestDatabase;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/** Synthetic observations in a rolled-back, isolated PostgreSQL schema; never app catalog data. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
@Sql("/fixtures/catalog.sql")
class ProductEquivalenceIntegrationTests {

    private static final String CITY = "623614c4-674b-4e6d-a252-2eafbd98be97";
    private static final UUID SOURCE = UUID.fromString("00000000-0000-0000-0000-000000000101");
    private static final String STORE_A = "00000000-0000-0000-0000-000000000201";
    private static final String STORE_B = "00000000-0000-0000-0000-000000000202";

    @Autowired private MockMvc mvc;
    @Autowired private ObjectMapper json;
    @Autowired private ProductIngestionService products;
    @Autowired private PriceService prices;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private ProductComparisonIndex index;
    @Autowired private jakarta.persistence.EntityManager entityManager;
    private String premiumToken;

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        PostgresTestDatabase.register(registry);
    }

    @Test
    void clickingOneStoresProductFindsOtherLocalIdsAndPreservesOfferProvenance() throws Exception {
        var selected = product("Refrigerante Coca Cola Original PET 2L");
        var other = product("COCA COLA ORIGINAL 2000 ML");
        price(selected, STORE_A, "9.00");
        price(other, STORE_B, "7.00");
        assertThat(selected.id()).isNotEqualTo(other.id());
        assertThat(selected.sourceReference()).isNotEqualTo(other.sourceReference());

        mvc.perform(get("/api/v1/comparisons/products").param("productId", selected.id().toString())
                        .param("cityId", CITY).param("page", "1").param("size", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.stores.totalElements").value(2))
                .andExpect(jsonPath("$.stores.content[0].storeId").value(STORE_B))
                .andExpect(jsonPath("$.stores.content[0].price.unitPrice").value(7))
                .andExpect(jsonPath("$.stores.content[0].matchedProduct.id").value(other.id().toString()))
                .andExpect(jsonPath("$.stores.content[0].price.observation.productId").value(other.id().toString()))
                .andExpect(jsonPath("$.stores.content[0].price.observation.sourceProductReference").value(other.sourceReference()))
                .andExpect(jsonPath("$.stores.content[0].price.observation.originUrl").value("https://source.example.test/item"));

        mvc.perform(get("/api/v1/products/offers").param("ids", selected.id().toString()).param("cityId", CITY))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].offers", hasSize(2)))
                .andExpect(jsonPath("$[0].offers[0].storeId").value(STORE_B));
        mvc.perform(get("/api/v1/products/" + selected.id())).andExpect(status().isOk());
    }

    @Test
    void cheaperIncompatibleVariantsVolumesAndPacksNeverEnterTheComparison() throws Exception {
        var selected = product("Coca-Cola Original 2 litros");
        price(selected, STORE_A, "9.00");
        for (String name : new String[]{"Coca-Cola Zero 2L", "Coca-Cola Original 1,5L",
                "Pack Coca-Cola Original 6 unidades 2L", "Coca-Cola Original 2L + Fanta 2L"}) {
            price(product(name), STORE_B, "1.00");
        }
        var unknown = product("Coca-Cola 2L");
        price(unknown, STORE_B, "0.50");

        compare(selected).andExpect(jsonPath("$.stores.content[1].price.status").value("NO_OBSERVATION"))
                .andExpect(jsonPath("$.stores.content[1].price.unitPrice").isEmpty())
                .andExpect(jsonPath("$.stores.content[1].price.availability").value("UNKNOWN"))
                .andExpect(jsonPath("$.possibleMatches", hasSize(1)))
                .andExpect(jsonPath("$.possibleMatches[0].id").value(unknown.id().toString()));
    }

    @Test
    void selectedMarketsAreAppliedExactlyAndInvalidSelectionsAreRejected() throws Exception {
        var selected = product("Coca-Cola Original 2L");
        price(selected, STORE_A, "9.00");
        price(product("Coca Cola Original 2000ml"), STORE_B, "7.00");
        mvc.perform(get("/api/v1/comparisons/products").param("productId", selected.id().toString())
                        .param("cityId", CITY).param("storeIds", STORE_B))
                .andExpect(status().isOk()).andExpect(jsonPath("$.stores.totalElements").value(1))
                .andExpect(jsonPath("$.stores.content[0].storeId").value(STORE_B));
        mvc.perform(get("/api/v1/comparisons/products").param("productId", selected.id().toString())
                        .param("cityId", CITY).param("storeIds", STORE_B + "," + STORE_A))
                .andExpect(status().isOk()).andExpect(jsonPath("$.stores.totalElements").value(2));
        mvc.perform(get("/api/v1/products/offers").param("ids", selected.id().toString()).param("cityId", CITY)
                        .param("storeIds", STORE_A))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].offers", hasSize(1)))
                .andExpect(jsonPath("$[0].offers[0].storeId").value(STORE_A));
        mvc.perform(get("/api/v1/comparisons/products").param("productId", selected.id().toString())
                        .param("cityId", CITY).param("storeIds", UUID.randomUUID().toString()))
                .andExpect(status().isBadRequest());
    }

    @Test
    void mixedOriginListUsesEquivalentOffersAndNeverRecommendsCheaperIncompleteBasket() throws Exception {
        var colaA = product("Coca-Cola Original PET 2L");
        var colaB = product("COCA COLA ORIGINAL 2000ML");
        var riceA = product("Arroz Branco Camil 1kg");
        var riceB = product("Arroz Camil Branco 1000 gr");
        price(colaA, STORE_A, "1.00");
        price(colaB, STORE_B, "3.00");
        price(riceB, STORE_B, "4.00");
        String token = premiumToken();
        String listId = read(mvc.perform(post("/api/v1/shopping-lists")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Synthetic mixed-origin list\",\"shoppingType\":\"CUSTOM\"}"))
                .andExpect(status().isCreated())).path("id").asString();
        for (ProductResponse item : new ProductResponse[]{colaA, riceB}) {
            mvc.perform(post("/api/v1/shopping-lists/" + listId + "/items")
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + token).contentType(MediaType.APPLICATION_JSON)
                            .content(json.writeValueAsString(Map.of("productId", item.id(), "quantity", 2))))
                    .andExpect(status().isCreated());
        }
        String path = "/api/v1/comparisons/shopping-lists/" + listId;
        mvc.perform(get(path).param("cityId", CITY).header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.stores.content[0].pricedItems").value(1))
                .andExpect(jsonPath("$.stores.content[0].missingItems").value(1))
                .andExpect(jsonPath("$.stores.content[0].completeShoppingList").value(false))
                .andExpect(jsonPath("$.stores.content[1].subtotalKnown").value(14))
                .andExpect(jsonPath("$.stores.content[1].pricedItems").value(2));
        mvc.perform(get(path + "/recommendation").param("cityId", CITY)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.recommendation.storeId").value(STORE_B))
                .andExpect(jsonPath("$.combination.subtotalKnown").value(10));

        price(riceA, STORE_A, "2.00");
        mvc.perform(get(path).param("cityId", CITY).param("storeIds", STORE_A)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk()).andExpect(jsonPath("$.stores.content", hasSize(1)))
                .andExpect(jsonPath("$.stores.content[0].pricedItems").value(2))
                .andExpect(jsonPath("$.stores.content[0].subtotalKnown").value(6));
        mvc.perform(get(path + "/recommendation").param("cityId", CITY).param("storeIds", STORE_B)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk()).andExpect(jsonPath("$.evaluatedStores").value(1))
                .andExpect(jsonPath("$.recommendation.storeId").value(STORE_B));
    }

    @Test
    void fullCandidateCatalogIsQueriedBeyondOneHundredRows() throws Exception {
        var selected = product("Coca-Cola Original 2L");
        ProductResponse last = selected;
        for (int i = 0; i < 110; i++) last = productWithoutBrand("Coca-Cola Original 2L");
        price(last, STORE_B, "7.00");
        mvc.perform(get("/api/v1/products").param("query", "Coca-Cola 2 litros").param("size", "1"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(111));
        // Store A never published a price, so store B is the only one compared.
        compare(selected).andExpect(jsonPath("$.stores.content", hasSize(1)))
                .andExpect(jsonPath("$.stores.content[0].price.unitPrice").value(7));
    }

    @Test
    void conditionalDiscountIsVisibleButDoesNotBeatAnUnconditionalComparablePrice() throws Exception {
        var selected = product("Coca-Cola Original 2L");
        Instant collected = Instant.now().minusSeconds(2);
        prices.appendObservation(new PriceObservation(selected.id(), UUID.fromString(STORE_A), SOURCE,
                "synthetic-club", new BigDecimal("10"), new BigDecimal("2"), "BRL", collected,
                null, collected.plusSeconds(3600), "Clube e mínimo de 6 unidades", StockAvailability.UNKNOWN));
        price(product("Coca Cola Original 2000ml"), STORE_B, "6.00");
        compare(selected).andExpect(jsonPath("$.stores.content[0].price.unitPrice").value(10))
                .andExpect(jsonPath("$.stores.content[0].price.promotionApplied").value(false))
                .andExpect(jsonPath("$.stores.content[0].price.observation.promotionalPrice").value(2))
                .andExpect(jsonPath("$.stores.content[1].price.unitPrice").value(6));
    }

    @Test
    void backfillPreservesObservationDatesAndVerifiedDirectoryDoesNotPretendToHavePrices() throws Exception {
        var before = jdbc.queryForMap("select collected_at, updated_at, version from products where source_reference = 'test-product-a'");
        index.rebuild();
        var after = jdbc.queryForMap("select collected_at, updated_at, version from products where source_reference = 'test-product-a'");
        assertThat(after).isEqualTo(before);
        assertThat(jdbc.queryForObject("select count(*) from products where comparison_identity_version = 0", Integer.class)).isZero();
        String storeId = jdbc.queryForObject("select id::text from stores where name = 'Supermarket Aterrado'", String.class);
        mvc.perform(get("/api/v1/stores/" + storeId)).andExpect(status().isOk())
                .andExpect(jsonPath("$.priceSourceNote").isNotEmpty())
                .andExpect(jsonPath("$.lastPriceCollectedAt").isEmpty());
        assertThat(jdbc.queryForObject("select count(*) from price_records where store_id = ?", Integer.class,
                UUID.fromString(storeId))).isZero();
    }

    private ProductResponse product(String name) {
        String brand = name.contains("Camil") ? "Camil" : name.toUpperCase().contains("COCA") ? "Coca-Cola" : name.contains("Ypê") ? "Ypê" : null;
        return products.ingest(new ProductObservation(null, name, brand, null, null, null, null,
                new SourceObservation(SOURCE, "synthetic-sku-" + UUID.randomUUID(), Instant.now().minusSeconds(3))));
    }

    @Test
    void discoveryGroupsBeforePaginationAndRanksUsableLocalPrices() throws Exception {
        var first = product("Refrigerante Coca Cola Original PET 1L");
        var second = product("COCA COLA ORIG 1000 ML");
        price(first, STORE_A, "8.00");
        price(second, STORE_B, "6.00");
        product("Coca Cola Original 2L");
        product("Coca Cola Zero 1L");
        for (int i = 0; i < 105; i++) productWithoutBrand("Coca Cola Original PET 1000 ml - ref");

        mvc.perform(get("/api/v1/products/discovery").param("query", "coca cola 1 litro")
                        .param("cityId", CITY).param("size", "1"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(3))
                .andExpect(jsonPath("$.content[0].catalogEntries").value(107))
                .andExpect(jsonPath("$.content[0].offers", hasSize(2)))
                .andExpect(jsonPath("$.content[0].offers[0].price.unitPrice").value(6));
        mvc.perform(get("/api/v1/products/discovery").param("query", "coca cola 1 litro")
                        .param("cityId", CITY).param("storeIds", STORE_A).param("size", "1"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.content[0].offers", hasSize(1)))
                .andExpect(jsonPath("$.content[0].offers[0].storeId").value(STORE_A));
    }

    @Test
    void matchesCleaningProductsWithAbbreviationsAndKeepsMissingVariantsSeparate() throws Exception {
        var detergent = product("Detergente Ypê Neutro 500ml");
        price(detergent, STORE_A, "3.00");
        price(product("DET Ypê NEUT 0,5 litros"), STORE_B, "2.50");
        price(product("Detergente Ypê Coco 500ml"), STORE_B, "1.00");
        compare(detergent).andExpect(jsonPath("$.stores.content[1].price.unitPrice").value(2.5));

        var original = product("Suco TestBrand Original 1L");
        var missing = product("Suco TestBrand 1L");
        price(original, STORE_A, "4.00");
        price(missing, STORE_B, "3.00");
        compare(original).andExpect(jsonPath("$.stores.content[1].price.status").value("NO_OBSERVATION"))
                .andExpect(jsonPath("$.possibleMatches", hasSize(1)));
    }

    @Test
    void reviewedGtinAttributesFillAnOmittedVariantWithoutOverridingConflicts() throws Exception {
        var selected = product("Coca Cola Original 1L");
        var omitted = products.ingest(new ProductObservation("7894900027044", "Refrigerante Coca Cola 1l", null,
                null, null, null, null, new SourceObservation(SOURCE, "synthetic-reviewed", Instant.now().minusSeconds(3))));
        price(selected, STORE_A, "8.00");
        price(omitted, STORE_B, "6.00");
        compare(selected).andExpect(jsonPath("$.stores.content[1].price.unitPrice").value(6));
        compare(omitted).andExpect(jsonPath("$.stores.content[0].price.unitPrice").value(8));
        products.ingest(new ProductObservation("7894900027044", "Coca Cola Zero PET 1L", "Coca-Cola",
                null, null, null, null, new SourceObservation(SOURCE, "synthetic-reviewed", Instant.now().minusSeconds(1))));
        compare(selected).andExpect(jsonPath("$.stores.content[1].price.status").value("NO_OBSERVATION"));
    }

    @Test
    void unknownPackCountDoesNotProduceMisleadingPricePerLitre() throws Exception {
        var pack = product("Detergente Ypê Neutro 500ml Pack");
        price(pack, STORE_A, "15.60");
        compare(pack).andExpect(jsonPath("$.stores.content[0].price.unitPrice").value(15.6))
                .andExpect(jsonPath("$.stores.content[0].measurementPrice").isEmpty());
        mvc.perform(get("/api/v1/products/discovery").param("query", "detergente ype neutro")
                        .param("cityId", CITY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].identityConfirmed").value(false))
                .andExpect(jsonPath("$.content[0].offers[0].measurementPrice").isEmpty());
    }

    @Test
    void newerDuplicateSupersedesOldLowPriceWithoutCountingAnotherMarket() throws Exception {
        var first = product("Coca Cola Original 1L");
        var duplicate = productWithoutBrand("REF COCA COLA ORIG PET 1000ML");
        prices.appendObservation(new PriceObservation(first.id(), UUID.fromString(STORE_A), SOURCE,
                "synthetic-older-price", new BigDecimal("1.00"), null, "BRL", Instant.now().minusSeconds(3600),
                null, null, null, StockAvailability.AVAILABLE));
        price(duplicate, STORE_A, "8.00");
        compare(first).andExpect(jsonPath("$.stores.content[0].price.unitPrice").value(8));
        mvc.perform(get("/api/v1/products/offers").param("ids", first.id().toString()).param("cityId", CITY))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].offers", hasSize(1)));
    }

    private void price(ProductResponse product, String storeId, String amount) {
        prices.appendObservation(new PriceObservation(product.id(), UUID.fromString(storeId), SOURCE,
                "synthetic-price-" + UUID.randomUUID(), new BigDecimal(amount), null, "BRL",
                Instant.now().minusSeconds(2), null, null, null, StockAvailability.UNKNOWN,
                "https://source.example.test/item", product.sourceReference()));
    }

    private ProductResponse productWithoutBrand(String name) {
        return products.ingest(new ProductObservation(null, name, null, null, null, null, null,
                new SourceObservation(SOURCE, "synthetic-local-" + UUID.randomUUID(), Instant.now().minusSeconds(3))));
    }

    /** The complete comparison (every store with its price status) is the Premium view. */
    private ResultActions compare(ProductResponse selected) throws Exception {
        return mvc.perform(get("/api/v1/comparisons/products").param("productId", selected.id().toString())
                .param("cityId", CITY).header(HttpHeaders.AUTHORIZATION, "Bearer " + premiumToken()))
                .andExpect(status().isOk());
    }

    private String premiumToken() throws Exception {
        if (premiumToken == null) {
            premiumToken = registerAndLogin();
            mvc.perform(post("/api/v1/subscription/trial").header(HttpHeaders.AUTHORIZATION, "Bearer " + premiumToken)
                            .contentType(MediaType.APPLICATION_JSON).content("{\"billingCycle\":\"MONTHLY\"}"))
                    .andExpect(status().isOk());
            // The whole test is one transaction: reload the token so it carries the account's new plan.
            entityManager.flush();
            entityManager.clear();
        }
        return premiumToken;
    }

    private String registerAndLogin() throws Exception {
        String email = UUID.randomUUID() + "@example.test";
        mvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("name", "Synthetic user", "email", email,
                                "password", "synthetic-test-password"))))
                .andExpect(status().isCreated());
        return read(mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("email", email, "password", "synthetic-test-password"))))
                .andExpect(status().isOk())).path("accessToken").asString();
    }

    private JsonNode read(ResultActions result) throws Exception {
        return json.readTree(result.andReturn().getResponse().getContentAsByteArray());
    }
}
