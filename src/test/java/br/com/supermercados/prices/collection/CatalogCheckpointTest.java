package br.com.supermercados.prices.collection;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import tools.jackson.databind.ObjectMapper;

class CatalogCheckpointTest {

    @TempDir Path directory;
    private final ObjectMapper mapper = new ObjectMapper();
    private final Instant start = Instant.parse("2026-09-20T12:00:00Z");

    @Test
    void resumesCompletedCategoriesWithoutRefreshingTheirObservationTime() {
        var first = checkpoint(start);
        first.category("drinks", CatalogCheckpoint.Entries.class, () -> new CatalogCheckpoint.Entries(List.of(mapper.readTree("{\"sku\":1}"))));
        assertThatThrownBy(() -> first.category("food", String.class, () -> { throw new IllegalStateException("offline"); }))
                .isInstanceOf(IllegalStateException.class);
        var retry = checkpoint(start.plusSeconds(60));
        assertThat(retry.startedAt()).isEqualTo(start);
        assertThat(retry.category("drinks", CatalogCheckpoint.Entries.class, () -> { throw new AssertionError("Must resume"); })
                .products()).hasSize(1);
        retry.category("food", String.class, () -> "received");
        retry.complete();
        assertThat(checkpoint(start.plusSeconds(120)).startedAt()).isEqualTo(start.plusSeconds(120));
    }

    @Test
    void staleCheckpointsAreCollectedAgain() {
        checkpoint(start).category("food", String.class, () -> "old");
        var retry = checkpoint(start.plusSeconds(18000));
        assertThat(retry.category("food", String.class, () -> "new")).isEqualTo("new");
    }

    private CatalogCheckpoint checkpoint(Instant instant) {
        return new CatalogCheckpoint(directory, "test_store", Clock.fixed(instant, ZoneOffset.UTC), mapper);
    }
}
