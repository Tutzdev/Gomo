package br.com.supermercados.prices.common;

import java.util.Set;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.CoreErrorCode;
import org.flywaydb.core.api.output.ValidateOutput;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.flyway.autoconfigure.FlywayMigrationStrategy;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
class FlywayConfiguration {

    private static final Logger log = LoggerFactory.getLogger(FlywayConfiguration.class);

    /**
     * Migrations rewritten after release without changing what they leave in the database. V54 only got a faster
     * backfill: a database that already ran the first text has the same table, so its stored checksum is realigned
     * instead of failing validation. Any other mismatch still stops the boot.
     */
    static final Set<String> REWRITTEN_VERSIONS = Set.of("54");

    @Bean
    FlywayMigrationStrategy flywayMigrationStrategy() {
        return flyway -> {
            realignRewrittenChecksums(flyway);
            flyway.migrate();
        };
    }

    static void realignRewrittenChecksums(Flyway flyway) {
        var mismatches = flyway.validateWithResult().invalidMigrations.stream()
                .filter(FlywayConfiguration::isChecksumMismatch)
                .toList();
        if (mismatches.isEmpty()) {
            return;
        }
        if (!mismatches.stream().allMatch(output -> REWRITTEN_VERSIONS.contains(output.version))) {
            return; // migrate() validates again and reports the unexpected mismatch
        }
        log.warn("Realinhando checksum das migrations reescritas: {}",
                mismatches.stream().map(output -> output.version).toList());
        flyway.repair();
    }

    private static boolean isChecksumMismatch(ValidateOutput output) {
        return output.errorDetails != null && output.errorDetails.errorCode == CoreErrorCode.CHECKSUM_MISMATCH;
    }
}
