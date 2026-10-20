package br.com.supermercados.prices;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import br.com.supermercados.prices.support.PostgresTestDatabase;

/** Synthetic catalog items in a rolled-back, isolated PostgreSQL schema. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
@Sql("/fixtures/catalog.sql")
@TestPropertySource(properties = "app.site-url=https://gomo.example.test/")
class SitemapIntegrationTests {

    private static final UUID PRODUCT_A = UUID.fromString("00000000-0000-0000-0000-000000000301");
    private static final UUID PRODUCT_B = UUID.fromString("00000000-0000-0000-0000-000000000302");

    @Autowired private MockMvc mvc;
    @Autowired private JdbcTemplate jdbc;

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        PostgresTestDatabase.register(registry);
    }

    @Test
    void visitorsGetTheHomeAndEveryActiveProductPage() throws Exception {
        UUID coffee = catalogItem("Café Pilão & Leite 500 g", PRODUCT_A, true, "2026-10-18T23:30:00Z");
        UUID retired = catalogItem("Biscoito retirado", PRODUCT_B, false, "2026-10-18T10:00:00Z");

        mvc.perform(get("/api/v1/sitemap.xml"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_XML))
                .andExpect(header().string("Cache-Control", containsString("max-age=3600")))
                .andExpect(content().string(containsString(
                        "<urlset xmlns=\"http://www.sitemaps.org/schemas/sitemap/0.9\">")))
                .andExpect(content().string(containsString("<loc>https://gomo.example.test/</loc>")))
                .andExpect(content().string(containsString("<loc>https://gomo.example.test/precos/cafe-pilao-leite-500-g-"
                        + coffee + "</loc><lastmod>2026-10-18</lastmod>")))
                .andExpect(content().string(not(containsString(retired.toString()))));
    }

    private UUID catalogItem(String name, UUID representativeProduct, boolean active, String updatedAt) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO catalog_items (id, catalog_key, display_name, size_label, representative_product_id,
                                           search_text, store_count, active, updated_at)
                VALUES (?, ?, ?, '1 UN', ?, ?, 2, ?, ?)""",
                id, "test|" + id, name, representativeProduct, name.toLowerCase(), active,
                Timestamp.from(Instant.parse(updatedAt)));
        return id;
    }
}
