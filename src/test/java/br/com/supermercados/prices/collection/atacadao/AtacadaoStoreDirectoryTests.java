package br.com.supermercados.prices.collection.atacadao;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Transactional;

import br.com.supermercados.prices.collection.CollectionCatalogService;
import br.com.supermercados.prices.support.PostgresTestDatabase;

/** A fresh database: the online collector only finds São Geraldo once the directory has registered it. */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class AtacadaoStoreDirectoryTests {

    private static final UUID VOLTA_REDONDA = UUID.fromString("5c4cb935-52e1-4bf8-8d17-902dc0837c66");

    @Autowired CollectionCatalogService catalogs;
    @Autowired AtacadaoCollector collector;
    @Autowired Clock clock;
    @Autowired JdbcTemplate jdbc;

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        PostgresTestDatabase.register(registry);
    }

    @Test
    void onlineCollectorFindsTheUnitRegisteredByTheDirectoryWithoutDuplicatingIt() {
        assertThatThrownBy(() -> catalogs.ensureCatalog(collector.metadata(), null, clock.instant()))
                .hasMessageContaining("Diretório verificado da unidade ausente");

        var directory = new AtacadaoStoreDirectory(catalogs, clock);
        directory.run(null);
        directory.run(null);
        var catalog = catalogs.ensureCatalog(collector.metadata(), null, clock.instant());

        var unit = jdbc.queryForMap("""
                SELECT s.name, s.city_id, s.active, s.source_reference, d.code
                FROM stores s JOIN data_sources d ON d.id = s.source_id WHERE s.id = ?""", catalog.storeId());
        assertThat(unit).containsEntry("name", "Atacadão São Geraldo").containsEntry("city_id", VOLTA_REDONDA)
                .containsEntry("active", true).containsEntry("source_reference", "atacadao:store:815")
                .containsEntry("code", "atacadao_reviewed_flyers");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM stores WHERE source_reference = 'atacadao:store:815'",
                Integer.class)).isOne();
    }
}
