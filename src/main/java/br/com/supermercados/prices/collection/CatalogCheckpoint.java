package br.com.supermercados.prices.collection;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.HexFormat;
import java.util.List;
import java.util.function.Supplier;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Completed categories survive retries for four hours, retaining their original observation time.
 *
 * <p>Each category is streamed to its own file and read back only when a retry needs it, so a large catalog
 * (Nagumo: ~47 thousand raw products) is never held twice in memory nor rewritten as one string per category.
 */
public final class CatalogCheckpoint {

    private static final Duration LIFETIME = Duration.ofHours(4);
    private static final String STARTED_AT = "started-at";

    private final Path directory;
    private final ObjectMapper mapper;
    private final Instant startedAt;

    public CatalogCheckpoint(String code, Clock clock, ObjectMapper mapper) {
        this(Path.of(System.getProperty("gomo.collection.checkpoints",
                System.getenv().getOrDefault("PRICE_COLLECTION_CHECKPOINT_DIRECTORY", ".local/collection-checkpoints"))),
                code, clock, mapper);
    }

    CatalogCheckpoint(Path base, String code, Clock clock, ObjectMapper mapper) {
        if (!code.matches("[a-z0-9_]+")) throw new IllegalArgumentException("Código de coletor inválido");
        this.directory = base.resolve(code);
        this.mapper = mapper;
        Instant now = clock.instant();
        Instant saved = savedStart();
        if (saved != null && !saved.isAfter(now) && saved.plus(LIFETIME).isAfter(now)) {
            startedAt = saved;
        } else {
            clear();
            startedAt = now;
        }
    }

    public <T> T category(String key, Class<T> type, Supplier<T> collect) {
        Path file = directory.resolve(fileName(key));
        if (Files.exists(file)) return mapper.readValue(file.toFile(), type);
        T result = collect.get();
        try {
            Files.createDirectories(directory);
            Path start = directory.resolve(STARTED_AT);
            if (!Files.exists(start)) Files.writeString(start, startedAt.toString(), StandardCharsets.UTF_8);
            Path staging = Files.createTempFile(directory, "category-", ".tmp");
            try {
                mapper.writeValue(staging.toFile(), result);
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

    /** The collection finished: the next one starts from scratch. */
    public void complete() {
        clear();
    }

    private Instant savedStart() {
        Path start = directory.resolve(STARTED_AT);
        if (!Files.exists(start)) return null;
        try {
            return Instant.parse(Files.readString(start, StandardCharsets.UTF_8).strip());
        } catch (IOException | DateTimeParseException exception) {
            return null;
        }
    }

    private void clear() {
        if (!Files.isDirectory(directory)) return;
        try (var files = Files.list(directory)) {
            for (Path file : files.toList()) Files.deleteIfExists(file);
        } catch (IOException exception) {
            throw new IllegalStateException("Não foi possível limpar o checkpoint de coleta", exception);
        }
    }

    /** Category keys are URLs and API paths; the file name only needs to be stable and safe. */
    private static String fileName(String key) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(key.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest, 0, 16) + ".json";
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }

    public record Entries(List<JsonNode> products) { }
    public record Category(List<CollectedProduct> products, int found, List<String> warnings) { }
}
