package br.com.supermercados.prices.collection.vip;

import java.net.URI;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Finds the script where a VipCommerce storefront publishes its anonymous-browsing configuration.
 *
 * <p>The file name carries a build hash ({@code chunk-CA3SU4MX.js}) that changes whenever the store redeploys,
 * and a missing file answers 200 with the home page. The known path is tried first and checked by content;
 * when it no longer holds the configuration, the current build is crawled from the home page and the
 * discovered path is remembered for the next collections.
 */
public final class VipStorefrontConfiguration {

    private static final Logger LOGGER = LoggerFactory.getLogger(VipStorefrontConfiguration.class);
    private static final Pattern ENTRY_SCRIPT = Pattern.compile("(?:src|href)=\"\\.?/?((?:main|chunk|scripts)-[A-Za-z0-9]+\\.js)\"");
    private static final Pattern CHUNK = Pattern.compile("chunk-[A-Za-z0-9]+\\.js");
    private static final int MAX_SCRIPTS = 400;
    private static final Map<String, String> DISCOVERED = new ConcurrentHashMap<>();

    private VipStorefrontConfiguration() {
    }

    /** Returns the configuration script; {@code fetch} must throw for a failed request. */
    public static String load(URI website, String knownPath, Function<URI, String> fetch) {
        String host = website.getHost();
        Set<String> candidates = new LinkedHashSet<>();
        if (DISCOVERED.containsKey(host)) candidates.add(DISCOVERED.get(host));
        candidates.add(knownPath);
        for (String path : candidates) {
            String script = tryFetch(website, path, fetch);
            if (script != null && holdsConfiguration(script)) return script;
        }
        LOGGER.warn("Configuração pública de {} saiu de {}; procurando na versão atual do site", host, knownPath);
        String home = fetch.apply(website.resolve("/"));
        Deque<String> queue = new ArrayDeque<>();
        Set<String> seen = new LinkedHashSet<>(candidates);
        var entries = ENTRY_SCRIPT.matcher(home);
        while (entries.find()) {
            String name = entries.group(1);
            // main-*.js imports every lazy chunk, so it goes first.
            if (seen.add("/" + name)) {
                if (name.startsWith("main-")) queue.addFirst("/" + name);
                else queue.addLast("/" + name);
            }
        }
        int fetched = 0;
        while (!queue.isEmpty() && fetched < MAX_SCRIPTS) {
            String path = queue.pollFirst();
            fetched++;
            String script = tryFetch(website, path, fetch);
            if (script == null) continue;
            if (holdsConfiguration(script)) {
                DISCOVERED.put(host, path);
                LOGGER.warn("Configuração pública de {} encontrada em {}; atualize o caminho conhecido", host, path);
                return script;
            }
            var chunks = CHUNK.matcher(script);
            while (chunks.find()) {
                if (seen.add("/" + chunks.group())) queue.addLast("/" + chunks.group());
            }
        }
        throw new IllegalStateException("Configuração pública de " + host + " não encontrada em " + fetched + " scripts do site");
    }

    static boolean holdsConfiguration(String script) {
        return script.contains("lojaUser:\"") && script.contains("lojaAuthJWT:\"");
    }

    static void forget() {
        DISCOVERED.clear();
    }

    private static String tryFetch(URI website, String path, Function<URI, String> fetch) {
        try {
            return fetch.apply(website.resolve(path));
        } catch (RuntimeException exception) {
            if (Thread.currentThread().isInterrupted()) throw exception;
            return null;
        }
    }
}
