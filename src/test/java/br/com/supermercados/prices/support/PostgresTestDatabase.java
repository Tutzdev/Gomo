package br.com.supermercados.prices.support;

import java.io.IOException;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.SecureRandom;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.springframework.test.context.DynamicPropertyRegistry;

/** A real PostgreSQL instance or an isolated schema in the explicitly configured test database. */
public final class PostgresTestDatabase {

    private static final PostgresTestDatabase INSTANCE = new PostgresTestDatabase();
    private final String schema = "prices_test_" + UUID.randomUUID().toString().replace("-", "");
    private final String baseUrl;
    private final String username;
    private final String password;
    private Path bin;
    private Path cluster;

    private PostgresTestDatabase() {
        String configuredUrl = System.getenv("TEST_DATABASE_URL");
        username = configuredUrl == null ? "prices_test" : requiredEnvironment("TEST_DATABASE_USERNAME");
        password = configuredUrl == null ? randomPassword() : requiredEnvironment("TEST_DATABASE_PASSWORD");
        try {
            baseUrl = configuredUrl == null ? startLocalPostgres() : configuredUrl;
            try (var connection = DriverManager.getConnection(baseUrl, username, password);
                    var statement = connection.createStatement()) {
                statement.execute("CREATE SCHEMA " + schema);
            }
            Runtime.getRuntime().addShutdownHook(new Thread(this::stop, "postgres-test-cleanup"));
        } catch (IOException | SQLException | InterruptedException exception) {
            if (exception instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            throw new IllegalStateException("Could not initialize PostgreSQL tests. Set PG_BIN or dedicated "
                    + "TEST_DATABASE_URL/USERNAME/PASSWORD. No tests have been skipped.", exception);
        }
    }

    public static void register(DynamicPropertyRegistry registry) {
        String separator = INSTANCE.baseUrl.contains("?") ? "&" : "?";
        registry.add("spring.datasource.url", () -> INSTANCE.baseUrl + separator + "currentSchema=" + INSTANCE.schema);
        registry.add("spring.datasource.username", () -> INSTANCE.username);
        registry.add("spring.datasource.password", () -> INSTANCE.password);
        registry.add("spring.flyway.schemas", () -> INSTANCE.schema);
        registry.add("spring.flyway.default-schema", () -> INSTANCE.schema);
        registry.add("spring.jpa.properties.hibernate.default_schema", () -> INSTANCE.schema);
    }

    private String startLocalPostgres() throws IOException, InterruptedException {
        bin = findPostgresBin();
        Path work = Path.of("target", "postgres-tests").toAbsolutePath().normalize();
        Files.createDirectories(work);
        cluster = Files.createTempDirectory(work, "cluster-");
        Path passwordFile = Files.createTempFile(work, "password-", ".tmp");
        try {
            Files.writeString(passwordFile, password, StandardCharsets.UTF_8);
            run("initdb", "-D", cluster.toString(), "-U", username, "--auth=scram-sha-256",
                    "--encoding=UTF8", "--no-locale", "--pwfile=" + passwordFile);
        } finally {
            Files.deleteIfExists(passwordFile);
        }
        int port;
        try (var socket = new ServerSocket(0)) {
            port = socket.getLocalPort();
        }
        String options = "-h 127.0.0.1 -p " + port;
        if (!System.getProperty("os.name").toLowerCase(java.util.Locale.ROOT).startsWith("windows")) {
            // Distribution builds (Ubuntu, the CI runner) put the socket in /var/run/postgresql, which only the
            // postgres user may write; a short private directory also stays under the socket path length limit.
            options += " -k " + Files.createTempDirectory("pgsock");
        }
        run("pg_ctl", "-D", cluster.toString(), "-l", cluster.resolve("server.log").toString(),
                "-o", options, "-w", "-t", "30", "start");
        return "jdbc:postgresql://127.0.0.1:" + port + "/postgres";
    }

    private static Path findPostgresBin() throws IOException {
        String configured = System.getenv("PG_BIN");
        if (configured != null && !configured.isBlank()) {
            return Path.of(configured);
        }
        String executable = executable("initdb");
        for (String directory : System.getenv("PATH").split(java.io.File.pathSeparator)) {
            if (Files.isRegularFile(Path.of(directory).resolve(executable))) {
                return Path.of(directory);
            }
        }
        Path windowsInstall = Path.of(System.getenv().getOrDefault("ProgramFiles", "C:/Program Files"), "PostgreSQL");
        if (Files.isDirectory(windowsInstall)) {
            try (var versions = Files.list(windowsInstall)) {
                return versions.sorted(Comparator.reverseOrder()).map(path -> path.resolve("bin"))
                        .filter(path -> Files.isRegularFile(path.resolve(executable)))
                        .findFirst().orElseThrow(() -> new IOException("PostgreSQL binaries not found"));
            }
        }
        throw new IOException("Set PG_BIN to the PostgreSQL bin directory or configure TEST_DATABASE_URL");
    }

    private void run(String executable, String... arguments) throws IOException, InterruptedException {
        List<String> command = new ArrayList<>();
        command.add(bin.resolve(executable(executable)).toString());
        command.addAll(List.of(arguments));
        Path output = cluster.getParent().resolve(cluster.getFileName() + "-" + executable + ".log");
        Process process = new ProcessBuilder(command).redirectErrorStream(true).redirectOutput(output.toFile()).start();
        if (!process.waitFor(45, TimeUnit.SECONDS)) {
            process.destroyForcibly();
            throw new IOException("PostgreSQL command timed out; see " + output);
        }
        if (process.exitValue() != 0) {
            throw new IOException("PostgreSQL command failed; see " + output);
        }
    }

    private void stop() {
        try {
            if (cluster != null) {
                run("pg_ctl", "-D", cluster.toString(), "-m", "fast", "-w", "stop");
            } else {
                try (var connection = DriverManager.getConnection(baseUrl, username, password);
                        var statement = connection.createStatement()) {
                    statement.execute("DROP SCHEMA " + schema + " CASCADE");
                }
            }
        } catch (IOException | SQLException | InterruptedException exception) {
            if (exception instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            org.slf4j.LoggerFactory.getLogger(PostgresTestDatabase.class)
                    .error("Could not clean up isolated test database: {}", exception.getClass().getSimpleName());
        }
    }

    private static String requiredEnvironment(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("Missing " + name);
        }
        return value;
    }

    private static String randomPassword() {
        byte[] bytes = new byte[32];
        new SecureRandom().nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static String executable(String name) {
        return System.getProperty("os.name").startsWith("Windows") ? name + ".exe" : name;
    }
}
