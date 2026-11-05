package br.com.supermercados.prices.common;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.supermercados.prices.support.PostgresTestDatabase;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

@SpringBootTest
@ActiveProfiles("test")
class FlywayConfigurationTests {

    @Autowired Flyway flyway;
    @Autowired JdbcTemplate jdbc;

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        PostgresTestDatabase.register(registry);
    }

    @Test
    void realignsTheChecksumOfARewrittenMigration() {
        int original = checksum("54");
        try {
            setChecksum("54", original + 1);

            FlywayConfiguration.realignRewrittenChecksums(flyway);

            assertThat(checksum("54")).isEqualTo(original);
            assertThat(flyway.validateWithResult().validationSuccessful).isTrue();
        } finally {
            setChecksum("54", original);
        }
    }

    @Test
    void leavesAnyOtherMismatchForValidationToReport() {
        int original = checksum("53");
        try {
            setChecksum("53", original + 1);

            FlywayConfiguration.realignRewrittenChecksums(flyway);

            assertThat(checksum("53")).isEqualTo(original + 1);
        } finally {
            setChecksum("53", original);
        }
    }

    private int checksum(String version) {
        return jdbc.queryForObject("SELECT checksum FROM flyway_schema_history WHERE version = ?", Integer.class,
                version);
    }

    private void setChecksum(String version, int checksum) {
        jdbc.update("UPDATE flyway_schema_history SET checksum = ? WHERE version = ?", checksum, version);
    }
}
