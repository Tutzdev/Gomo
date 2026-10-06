package br.com.supermercados.prices;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.supermercados.prices.support.PostgresTestDatabase;
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
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class ApiIntegrationTests {

    private static final String PASSWORD = "integration-test-password";
    private static final String PRODUCT_ID = "00000000-0000-0000-0000-000000000301";
    private static final String SECOND_PRODUCT_ID = "00000000-0000-0000-0000-000000000302";
    private static final String LISTS_PATH = "/api/v1/shopping-lists";

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbc;

    @DynamicPropertySource
    static void configureDatabase(DynamicPropertyRegistry registry) {
        PostgresTestDatabase.register(registry);
    }

    @Test
    void registrationLoginProfileAndLogoutWorkWithHashedCredentials() throws Exception {
        String email = uniqueEmail();
        JsonNode user = read(register(email.toUpperCase())
                .andExpect(status().isCreated())
                .andExpect(header().string(HttpHeaders.LOCATION, "/api/v1/users/me"))
                .andExpect(jsonPath("$.email").value(email))
                .andExpect(jsonPath("$.passwordHash").doesNotExist())
                .andExpect(jsonPath("$.password").doesNotExist()));
        String storedHash = jdbc.queryForObject("select password_hash from app_users where email = ?", String.class, email);
        assertThat(storedHash).startsWith("$2").isNotEqualTo(PASSWORD);

        JsonNode login = read(login(email, PASSWORD)
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"))
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.expiresAt").isString()));
        String token = login.path("accessToken").asString();

        perform(get("/api/v1/users/me"), token)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(user.path("id").asString()))
                .andExpect(jsonPath("$.passwordHash").doesNotExist());
        String storedToken = jdbc.queryForObject("select token_hash from auth_tokens where user_id = ?", String.class,
                UUID.fromString(user.path("id").asString()));
        assertThat(storedToken).hasSize(64).isNotEqualTo(token);

        perform(patch("/api/v1/users/me").content(json(Map.of("name", "Updated Test User"))), token)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Updated Test User"));
        perform(get("/api/v1/users/me"), token)
                .andExpect(jsonPath("$.name").value("Updated Test User"));

        perform(post("/api/v1/auth/logout"), token).andExpect(status().isNoContent());
        perform(get("/api/v1/users/me"), token)
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    void duplicateEmailAndInvalidCredentialsHaveConsistentErrors() throws Exception {
        String email = uniqueEmail();
        register(email).andExpect(status().isCreated());

        register(email.toUpperCase())
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONFLICT"))
                .andExpect(jsonPath("$.path").value("/api/v1/auth/register"));
        String wrongPasswordDetail = read(login(email, "incorrect-test-password")
                .andExpect(status().isUnauthorized())).path("detail").asString();
        login(uniqueEmail(), PASSWORD)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.detail").value(wrongPasswordDetail));
    }

    @Test
    void expiredTokenCannotAuthenticate() throws Exception {
        String token = registerAndLogin();
        perform(get("/api/v1/users/me"), token).andExpect(status().isOk());
        jdbc.update("update auth_tokens set created_at = current_timestamp - interval '2 hours', "
                + "expires_at = current_timestamp - interval '1 hour'");

        perform(get("/api/v1/users/me"), token)
                .andExpect(status().isUnauthorized())
                .andExpect(header().string(HttpHeaders.WWW_AUTHENTICATE, "Bearer"));
    }

    @Test
    void validationRejectsInvalidAndOversizedPasswordsWithoutEchoingThem() throws Exception {
        String validationError = mvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("name", "", "email", "invalid-email", "password", "short"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("BAD_REQUEST"))
                .andExpect(jsonPath("$.errors").isArray())
                .andReturn().getResponse().getContentAsString();
        assertThat(validationError).doesNotContain("short", "invalid-email");
        String password = "é".repeat(37);
        String error = mvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("name", "Test User", "email", uniqueEmail(), "password", password))))
                .andExpect(status().isBadRequest()).andReturn().getResponse().getContentAsString();
        assertThat(error).doesNotContain(password);
    }

    @Test
    void unknownJsonPropertiesAndNumericEnumsAreRejected() throws Exception {
        mvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("name", "Test User", "email", uniqueEmail(), "password", PASSWORD,
                                "unexpectedField", "unused"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("BAD_REQUEST"));

        String token = registerAndLogin();
        perform(post(LISTS_PATH).content("{\"name\":\"Invalid Test List\",\"shoppingType\":0}"), token)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("BAD_REQUEST"));
    }

    @Test
    @Sql("/fixtures/catalog.sql")
    void ownerCanCreateEditAndDeleteListAndItems() throws Exception {
        String token = registerAndLogin();
        String listId = createList(token);
        String listPath = LISTS_PATH + "/" + listId;

        perform(get(LISTS_PATH), token)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.totalElements").value(1));
        perform(put(listPath).content(listRequest("Updated Test List", "WEEKLY")), token)
                .andExpect(status().isOk()).andExpect(jsonPath("$.name").value("Updated Test List"));
        String itemId = addItem(token, listId, PRODUCT_ID, "2.5");
        perform(get(listPath), token)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items", hasSize(1)))
                .andExpect(jsonPath("$.items[0].quantity").value(2.5));
        perform(put(listPath + "/items/" + itemId).content("{\"quantity\":3.125}"), token)
                .andExpect(status().isOk()).andExpect(jsonPath("$.quantity").value(3.125));
        perform(delete(listPath + "/items/" + itemId), token).andExpect(status().isNoContent());
        perform(get(listPath), token).andExpect(jsonPath("$.items").isEmpty());
        perform(delete(listPath), token).andExpect(status().isNoContent());
        perform(get(listPath), token).andExpect(status().isNotFound());
    }

    @Test
    @Sql("/fixtures/catalog.sql")
    void anotherUserCannotReadMutateDeleteOrCompareOwnersList() throws Exception {
        String ownerToken = registerAndLogin();
        String listId = createList(ownerToken);
        String itemId = addItem(ownerToken, listId, PRODUCT_ID, "2");
        String outsiderToken = registerAndLogin();
        String listPath = LISTS_PATH + "/" + listId;
        String itemPath = listPath + "/items/" + itemId;

        perform(get(LISTS_PATH), outsiderToken).andExpect(jsonPath("$.content").isEmpty());
        perform(get(listPath), outsiderToken).andExpect(status().isNotFound());
        perform(put(listPath).content(listRequest("Unauthorized Edit", "DAILY")), outsiderToken)
                .andExpect(status().isNotFound());
        perform(delete(listPath), outsiderToken).andExpect(status().isNotFound());
        perform(post(listPath + "/items").content(itemRequest(SECOND_PRODUCT_ID, "1")), outsiderToken)
                .andExpect(status().isNotFound());
        perform(put(itemPath).content("{\"quantity\":9}"), outsiderToken).andExpect(status().isNotFound());
        perform(delete(itemPath), outsiderToken).andExpect(status().isNotFound());
        perform(get("/api/v1/comparisons/shopping-lists/" + listId).param("cityId", rioCityId()), outsiderToken)
                .andExpect(status().isNotFound());

        perform(get(listPath), ownerToken)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Test Shopping List"))
                .andExpect(jsonPath("$.items[0].quantity").value(2));
    }

    @Test
    @Sql("/fixtures/catalog.sql")
    void itemValidationRejectsZeroPrecisionAndUnknownProduct() throws Exception {
        String token = registerAndLogin();
        String listId = createList(token);
        String itemsPath = LISTS_PATH + "/" + listId + "/items";

        perform(post(itemsPath).content(itemRequest(PRODUCT_ID, "0")), token)
                .andExpect(status().isBadRequest());
        perform(post(itemsPath).content(itemRequest(PRODUCT_ID, "1.0001")), token)
                .andExpect(status().isBadRequest());
        perform(post(itemsPath).content(itemRequest(UUID.randomUUID().toString(), "1")), token)
                .andExpect(status().isNotFound());
        addItem(token, listId, PRODUCT_ID, "1");
        perform(post(itemsPath).content(itemRequest(PRODUCT_ID, "1")), token)
                .andExpect(status().isConflict());
    }

    @Test
    void publicCatalogPrivateRoutesAndWrongMethodsHaveExpectedStatus() throws Exception {
        mvc.perform(get("/api/v1/states"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.content[0].code").value("RJ"));
        mvc.perform(get(LISTS_PATH))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"))
                .andExpect(jsonPath("$.timestamp").isString());
        mvc.perform(post("/api/v1/products")).andExpect(status().isUnauthorized());
        String token = registerAndLogin();
        perform(post("/api/v1/products"), token)
                .andExpect(status().isMethodNotAllowed())
                .andExpect(header().exists(HttpHeaders.ALLOW))
                .andExpect(jsonPath("$.code").value("METHOD_NOT_ALLOWED"))
                .andExpect(jsonPath("$.status").value(405));
        mvc.perform(get("/api/v1/products").param("size", "101")).andExpect(status().isBadRequest());
        mvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON).content("{"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("BAD_REQUEST"));
    }

    @Test
    @Sql("/fixtures/catalog.sql")
    void batchOffersExcludeUnavailablePricesAndCalculatePricePerKilo() throws Exception {
        jdbc.update("update products set quantity = 500, unit = 'G' where id = ?", UUID.fromString(PRODUCT_ID));
        for (int store = 201; store <= 202; store++) {
            jdbc.update("""
                    insert into price_records (id, product_id, store_id, source_id, source_reference,
                        origin_type, regular_price, currency, collected_at, recorded_at, availability)
                    values (?, ?, ?, '00000000-0000-0000-0000-000000000101', ?, 'SOURCE', 8,
                        'BRL', current_timestamp, current_timestamp, ?)
                    """, UUID.randomUUID(), UUID.fromString(PRODUCT_ID),
                    UUID.fromString("00000000-0000-0000-0000-000000000" + store), "batch-test-" + store,
                    store == 201 ? "AVAILABLE" : "UNAVAILABLE");
        }

        mvc.perform(get("/api/v1/products/offers").param("ids", PRODUCT_ID, SECOND_PRODUCT_ID).param("cityId", rioCityId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].offers", hasSize(1)))
                .andExpect(jsonPath("$[0].offers[0].price.unitPrice").value(8))
                .andExpect(jsonPath("$[0].offers[0].measurementPrice.amount").value(16))
                .andExpect(jsonPath("$[0].offers[0].measurementPrice.unit").value("KG"))
                .andExpect(jsonPath("$[1].offers").isEmpty());
        mvc.perform(get("/api/v1/products/offers").param("cityId", rioCityId()))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/products/offers").param("ids",
                        java.util.Collections.nCopies(101, PRODUCT_ID).toArray(String[]::new)).param("cityId", rioCityId()))
                .andExpect(status().isBadRequest());
    }

    @Test
    void regularUserCannotAccessAdministrativeOperations() throws Exception {
        String token = registerAndLogin();

        perform(post("/api/v1/admin/sources").content("{}"), token)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    void healthAndReadinessArePublicButMetricsRequireAdministrator() throws Exception {
        mvc.perform(get("/actuator/health")).andExpect(status().isOk());
        mvc.perform(get("/actuator/health/readiness")).andExpect(status().isOk());
        mvc.perform(get("/actuator/metrics")).andExpect(status().isUnauthorized());
    }

    @Test
    void corsAcceptsConfiguredFrontendAndRejectsUnknownOrigin() throws Exception {
        mvc.perform(options("/api/v1/users/me")
                        .header(HttpHeaders.ORIGIN, "https://frontend.example.test")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "PATCH")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, "authorization,content-type"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "https://frontend.example.test"))
                .andExpect(header().doesNotExist(HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS));
        mvc.perform(options("/api/v1/users/me")
                        .header(HttpHeaders.ORIGIN, "https://untrusted.example.test")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "PATCH"))
                .andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("FORBIDDEN"))
                .andExpect(jsonPath("$.path").value("/api/v1/users/me"))
                .andExpect(header().doesNotExist(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN));
    }

    @Test
    void siteBehindProxyCanLogInWhileOtherSitesStayBlockedByPreflight() throws Exception {
        // The site's own login through Nginx: the browser sends its public origin, which is not listed.
        mvc.perform(post("/api/v1/auth/login")
                        .header(HttpHeaders.ORIGIN, "https://gomo.example.test")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + uniqueEmail() + "\",\"password\":\"" + PASSWORD + "\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().doesNotExist(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN));
        mvc.perform(options("/api/v1/auth/login")
                        .header(HttpHeaders.ORIGIN, "https://gomo.example.test")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, "content-type"))
                .andExpect(status().isForbidden())
                .andExpect(header().doesNotExist(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN));
    }

    @Test
    void openApiDocumentsBearerAuthAndProtectedOperationsWithoutUi() throws Exception {
        mvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.components.securitySchemes.bearerAuth.type").value("http"))
                .andExpect(jsonPath("$.components.securitySchemes.bearerAuth.scheme").value("bearer"))
                .andExpect(jsonPath("$.paths['/api/v1/users/me'].get.security[0].bearerAuth").isArray())
                .andExpect(jsonPath("$.paths['/api/v1/auth/logout'].post.security[0].bearerAuth").isArray())
                .andExpect(jsonPath("$.paths['/api/v1/auth/login'].post.security").doesNotExist());
    }

    private ResultActions register(String email) throws Exception {
        return mvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("name", "Test User", "email", email, "password", PASSWORD))));
    }

    private ResultActions login(String email, String password) throws Exception {
        return mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("email", email, "password", password))));
    }

    private String registerAndLogin() throws Exception {
        String email = uniqueEmail();
        register(email).andExpect(status().isCreated());
        return read(login(email, PASSWORD).andExpect(status().isOk())).path("accessToken").asString();
    }

    private String createList(String token) throws Exception {
        return read(perform(post(LISTS_PATH).content(listRequest("Test Shopping List", "CUSTOM")), token)
                .andExpect(status().isCreated()).andExpect(jsonPath("$.items").isEmpty())).path("id").asString();
    }

    private String addItem(String token, String listId, String productId, String quantity) throws Exception {
        return read(perform(post(LISTS_PATH + "/" + listId + "/items").content(itemRequest(productId, quantity)), token)
                .andExpect(status().isCreated())).path("id").asString();
    }

    private ResultActions perform(MockHttpServletRequestBuilder request, String token) throws Exception {
        return mvc.perform(request.header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON));
    }

    private JsonNode read(ResultActions result) throws Exception {
        return objectMapper.readTree(result.andReturn().getResponse().getContentAsByteArray());
    }

    private String json(Object value) {
        return objectMapper.writeValueAsString(value);
    }

    private String listRequest(String name, String shoppingType) {
        return json(Map.of("name", name, "shoppingType", shoppingType));
    }

    private String itemRequest(String productId, String quantity) {
        return json(Map.of("productId", productId, "quantity", new java.math.BigDecimal(quantity)));
    }

    private String rioCityId() {
        return jdbc.queryForObject("select id from cities where name = 'Rio de Janeiro'", UUID.class).toString();
    }

    private String uniqueEmail() {
        return UUID.randomUUID() + "@example.test";
    }
}
