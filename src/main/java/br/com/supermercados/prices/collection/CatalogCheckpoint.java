package br.com.supermercados.prices.collection;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/** Completed categories survive retries for four hours, retaining their original observation time. */
public final class CatalogCheckpoint {

    private final Path file;
    private final ObjectMapper mapper;
    private final Instant startedAt;
    private final Map<String, JsonNode> categories = new LinkedHashMap<>();

    public CatalogCheckpoint(String code, Clock clock, ObjectMapper mapper) {
        this(Path.of(System.getProperty("gomo.collection.checkpoints",
                System.getenv().getOrDefault("PRICE_COLLECTION_CHECKPOINT_DIRECTORY", ".local/collection-checkpoints"))),
                code, clock, mapper);
    }

    CatalogCheckpoint(Path directory, String code, Clock clock, ObjectMapper mapper) {
        if (!code.matches("[a-z0-9_]+")) throw new IllegalArgumentException("Código de coletor inválido");
        this.file = directory.resolve(code + ".json");
        this.mapper = mapper;
        Instant observedAt = clock.instant();
        try {
            if (Files.exists(file)) {
                Snapshot saved = mapper.readValue(Files.readString(file), Snapshot.class);
                if (!saved.startedAt().isAfter(observedAt)
                        && saved.startedAt().plus(Duration.ofHours(4)).isAfter(observedAt)) {
                    observedAt = saved.startedAt();
                    categories.putAll(saved.categories());
                }
            }
        } catch (IOException exception) {
            throw new IllegalStateException("Não foi possível ler o checkpoint de coleta", exception);
        }
        startedAt = observedAt;
    }

    public <T> T category(String key, Class<T> type, Supplier<T> collect) {
        if (categories.containsKey(key)) return mapper.treeToValue(categories.get(key), type);
        T result = collect.get();
        categories.put(key, mapper.valueToTree(result));
        try {
            Files.createDirectories(file.getParent());
            Path staging = Files.createTempFile(file.getParent(), "category-", ".tmp");
            try {
                Files.writeString(staging, mapper.writeValueAsString(new Snapshot(startedAt, categories)));
                Files.move(staging, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } finally {
                Files.deleteIfExists(staging);
            }
        } catch (IOException exception) {
            throw new IllegalStateException("Não foi possível salvar o checkpoint de coleta", exception);
        }
        return result;
    }

    public Instant startedAt() { return startedAt; }

    public void complete() {
        try {
            if (Files.exists(file)) {
                Files.move(file, file.resolveSibling(file.getFileName() + ".completed"), StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException exception) {
            throw new IllegalStateException("Não foi possível concluir o checkpoint", exception);
        }
    }

    public record Snapshot(Instant startedAt, Map<String, JsonNode> categories) { }
    public record Entries(List<JsonNode> products) { }
    public record Category(List<CollectedProduct> products, int found, List<String> warnings) { }
}
