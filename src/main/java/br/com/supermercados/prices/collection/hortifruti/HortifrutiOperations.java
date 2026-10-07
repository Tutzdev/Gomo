package br.com.supermercados.prices.collection.hortifruti;

import java.net.URI;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The storefront (VTEX FastStore) only accepts persisted GraphQL queries, identified by a hash that changes when
 * the store redeploys its site; an unknown hash answers HTTP 400. The known hashes are tried first; when one is
 * refused, the current ones are read from the site's own scripts ({@code operationName:"…",operationHash:"…"}).
 */
final class HortifrutiOperations {

    private static final Logger LOGGER = LoggerFactory.getLogger(HortifrutiOperations.class);
    private static final Pattern SCRIPT = Pattern.compile("(?:src|href)=\"(/_next/static/[^\"]+\\.js)\"");
    private static final Map<String, String> KNOWN = Map.of(
            "ClientPickupPointsQuery", "3fa04e88c811fcb5ece7206fd5aa745bdbc143a8",
            "GetSellersByPostalCodeQuery", "285e40ec689755393866a7c3f72e64319f84a06e",
            "ClientManyProductsQuery", "20c118554f23873d9e238fcafe59f23ff7f0f7aa");
    private static final Map<String, String> DISCOVERED = new ConcurrentHashMap<>();

    private HortifrutiOperations() {
    }

    static String hash(String operation) {
        String hash = DISCOVERED.getOrDefault(operation, KNOWN.get(operation));
        if (hash == null) throw new IllegalArgumentException("Operação Hortifruti desconhecida: " + operation);
        return hash;
    }

    /**
     * Reads the current hash of {@code operation} from the scripts of {@code page}; returns whether it differs
     * from the one in use, i.e. whether retrying can help.
     */
    static boolean refresh(String operation, URI website, String page, Function<URI, String> fetch) {
        String html = fetch.apply(website.resolve(page));
        Pattern declaration = Pattern.compile("operationName:\"" + Pattern.quote(operation) + "\",operationHash:\"([0-9a-f]{40})\"");
        Set<String> scripts = new LinkedHashSet<>();
        var matcher = SCRIPT.matcher(html);
        while (matcher.find()) scripts.add(matcher.group(1));
        for (String script : scripts) {
            String source;
            try {
                source = fetch.apply(website.resolve(script));
            } catch (RuntimeException exception) {
                continue;
            }
            var found = declaration.matcher(source);
            if (found.find()) {
                String current = found.group(1);
                if (current.equals(hash(operation))) return false;
                DISCOVERED.put(operation, current);
                LOGGER.warn("Hortifruti mudou a consulta {}: hash {}; atualize o valor conhecido", operation, current);
                return true;
            }
        }
        return false;
    }

    static void forget() {
        DISCOVERED.clear();
    }
}
