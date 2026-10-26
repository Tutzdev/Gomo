package br.com.supermercados.prices;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.supermercados.prices.support.PostgresTestDatabase;
import br.com.supermercados.prices.support.SignedInAccounts;
import br.com.supermercados.prices.support.SignedInAccounts.Account;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

/** A list shared by the household through an invite link, with fictional accounts. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
@Sql("/fixtures/catalog.sql")
class SharedListIntegrationTests {

    private static final UUID PRODUCT_A = UUID.fromString("00000000-0000-0000-0000-000000000301");
    private static final UUID PRODUCT_B = UUID.fromString("00000000-0000-0000-0000-000000000302");

    @Autowired private MockMvc mvc;
    @Autowired private ObjectMapper json;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private EntityManager entityManager;

    private SignedInAccounts accounts;

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        PostgresTestDatabase.register(registry);
    }

    @BeforeEach
    void setUp() {
        accounts = new SignedInAccounts(mvc, json, jdbc, entityManager);
    }

    @Test
    void invitedPersonEditsAndChecksTheListButOnlyTheOwnerRenamesOrDeletesIt() throws Exception {
        Account owner = accounts.premium();
        String list = list(owner);
        String rice = item(owner, list, PRODUCT_A);
        String token = invite(owner, list);
        Account member = accounts.free();

        accounts.perform(post("/api/v1/list-invites/preview").content(tokenBody(token)), member)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.listName").value("Casa"))
                .andExpect(jsonPath("$.ownerName").value("Pessoa sintética"))
                .andExpect(jsonPath("$.items").value(1))
                .andExpect(jsonPath("$.alreadyIn").value(false));
        accounts.perform(post("/api/v1/list-invites/acceptance").content(tokenBody(token)), member)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.listId").value(list));
        accounts.perform(post("/api/v1/list-invites/acceptance").content(tokenBody(token)), member)
                .andExpect(jsonPath("$.listId").value(list));

        accounts.perform(get("/api/v1/shopping-lists"), member)
                .andExpect(jsonPath("$.content[0].id").value(list))
                .andExpect(jsonPath("$.content[0].owned").value(false))
                .andExpect(jsonPath("$.content[0].ownerName").value("Pessoa sintética"));
        accounts.perform(get("/api/v1/shopping-lists/" + list), member)
                .andExpect(jsonPath("$.owned").value(false))
                .andExpect(jsonPath("$.members[0].userId").value(member.id().toString()));
        item(member, list, PRODUCT_B);
        accounts.perform(put("/api/v1/shopping-lists/" + list + "/items/" + rice + "/check"), member)
                .andExpect(status().isOk());
        accounts.perform(get("/api/v1/shopping-lists/" + list), owner)
                .andExpect(jsonPath("$.owned").value(true))
                .andExpect(jsonPath("$.items.length()").value(2))
                .andExpect(jsonPath("$.items[0].checkedBy").value(member.id().toString()))
                .andExpect(jsonPath("$.items[0].checkedByName").value("Pessoa sintética"));
        accounts.perform(get("/api/v1/comparisons/shopping-lists/" + list + "/recommendation")
                .param("cityId", rioCityId()), member).andExpect(status().isOk());

        accounts.perform(put("/api/v1/shopping-lists/" + list).content(
                "{\"name\":\"Minha agora\",\"shoppingType\":\"CUSTOM\"}"), member).andExpect(status().isForbidden());
        accounts.perform(delete("/api/v1/shopping-lists/" + list), member).andExpect(status().isForbidden());
        accounts.perform(post("/api/v1/shopping-lists/" + list + "/invites"), member).andExpect(status().isForbidden());

        accounts.perform(delete("/api/v1/shopping-lists/" + list + "/members/" + member.id()), member)
                .andExpect(status().isNoContent());
        accounts.perform(get("/api/v1/shopping-lists/" + list), member).andExpect(status().isNotFound());
    }

    @Test
    void sharingIsPremiumAndANewOrStoppedLinkEndsTheOldOne() throws Exception {
        Account free = accounts.free();
        String freeList = list(free);
        accounts.perform(post("/api/v1/shopping-lists/" + freeList + "/invites"), free)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.limit").value("SHARED_LISTS"));

        Account owner = accounts.premium();
        String list = list(owner);
        String first = invite(owner, list);
        String second = invite(owner, list);
        Account guest = accounts.free();
        accounts.perform(post("/api/v1/list-invites/acceptance").content(tokenBody(first)), guest)
                .andExpect(status().isNotFound());
        accounts.perform(delete("/api/v1/shopping-lists/" + list + "/invites"), owner).andExpect(status().isNoContent());
        accounts.perform(post("/api/v1/list-invites/acceptance").content(tokenBody(second)), guest)
                .andExpect(status().isNotFound());
        assertThat(jdbc.queryForObject("select count(*) from shopping_list_invites where token_hash = ?",
                Integer.class, second)).isZero();
    }

    @Test
    void aListIsSharedWithAtMostFivePeople() throws Exception {
        Account owner = accounts.premium();
        String list = list(owner);
        String token = invite(owner, list);
        for (int person = 0; person < 5; person++) {
            accounts.perform(post("/api/v1/list-invites/acceptance").content(tokenBody(token)), accounts.free())
                    .andExpect(status().isOk());
        }

        accounts.perform(post("/api/v1/list-invites/acceptance").content(tokenBody(token)), accounts.free())
                .andExpect(status().isUnprocessableContent());
    }

    private String invite(Account owner, String list) throws Exception {
        return accounts.read(accounts.perform(post("/api/v1/shopping-lists/" + list + "/invites"), owner)
                .andExpect(status().isCreated())).path("token").asString();
    }

    private String tokenBody(String token) {
        return accounts.write(Map.of("token", token));
    }

    private String list(Account account) throws Exception {
        return accounts.read(accounts.perform(post("/api/v1/shopping-lists")
                .content("{\"name\":\"Casa\",\"shoppingType\":\"WEEKLY\"}"), account)
                .andExpect(status().isCreated())).path("id").asString();
    }

    private String item(Account account, String list, UUID product) throws Exception {
        return accounts.read(accounts.perform(post("/api/v1/shopping-lists/" + list + "/items").content(
                accounts.write(Map.of("productId", product, "quantity", BigDecimal.ONE))), account)
                .andExpect(status().isCreated())).path("id").asString();
    }

    private String rioCityId() {
        return jdbc.queryForObject("select id from cities where name = 'Rio de Janeiro'", UUID.class).toString();
    }
}
