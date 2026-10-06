package br.com.supermercados.prices.collection;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import tools.jackson.databind.json.JsonMapper;

class CollectedCatalogArchiveTest {

    @TempDir
    Path directory;

    @Test
    void replayPreservesTimestampAndRefusesAnotherStoreOrPathTraversal() {
        var archive = new CollectedCatalogArchive(directory.toString(), JsonMapper.builder().findAndAddModules().build());
        var metadata = metadata("example", "store-1");
        var catalog = new CollectedCatalog(new CollectedStore("Test", "Test address", null, null, true),
                List.of(), 0, Instant.parse("2026-09-18T10:00:00Z"), List.of("Original warning"));

        archive.save(metadata, catalog);

        assertThat(archive.read(metadata)).isEqualTo(catalog);
        assertThatThrownBy(() -> archive.read(metadata("example", "store-2")))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("outra fonte ou unidade");
        assertThatThrownBy(() -> archive.read(metadata("../outside", "store-1")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void bundledSeedIsReadFromTheClasspathAndRefusesAnotherStore() {
        var archive = new CollectedCatalogArchive("", JsonMapper.builder().findAndAddModules().build());

        assertThat(archive.hasSeed(metadata("seed_test", "store-1"))).isTrue();
        assertThat(archive.hasSeed(metadata("no_seed", "store-1"))).isFalse();
        var catalog = archive.readSeed(metadata("seed_test", "store-1"));
        assertThat(catalog.collectedAt()).isEqualTo(Instant.parse("2026-09-18T10:00:00Z"));
        assertThat(catalog.warnings()).containsExactly("Original warning");
        assertThatThrownBy(() -> archive.readSeed(metadata("seed_test", "store-2")))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("outra fonte ou unidade");
    }

    private CollectorMetadata metadata(String code, String reference) {
        return new CollectorMetadata(code, "Test", "Test", "test", "Test", "https://example.test",
                Instant.EPOCH, "Test", "chain-1", reference, UUID.randomUUID());
    }
}
