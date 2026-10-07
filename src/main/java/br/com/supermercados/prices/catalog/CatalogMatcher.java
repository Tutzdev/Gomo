package br.com.supermercados.prices.catalog;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Decides which retailer SKUs describe the same real-world product. Descriptions with the same
 * {@link CatalogKey} always match. Two different keys also match when the size agrees and their words differ
 * only by optional words (form, generic product type) or by a word a store cut short, either because a
 * single SKU is described by both or because the brand agrees too. A store that sells SKUs published under
 * both descriptions proves they are different products, so such keys are never joined: one store never has
 * two SKUs in the same item because of a match made here. A matcher holds the state of one rebuild.
 */
final class CatalogMatcher {

    /** VIP stores (Bramil, Pérola, Royal, Spani) publish ERP descriptions cut at 30 characters. */
    private static final int ERP_DESCRIPTION_LENGTH = 30;
    /**
     * Words about the form or generic type of product, never about which product it is. Packaging words
     * (sachê, pote, refil, squeeze) are deliberately absent: a refill and a box of the same powder are
     * different SKUs with different prices.
     */
    private static final Set<String> OPTIONAL_WORDS = Set.of(("PO LIQUIDO BISCOITO BOLACHA SALGADINHO TEMPERO "
            + "ROUPA ROUPAS CREMOSO CREMOSA SALGADO PERFUMADO PRONTO CONGELADO CONGELADA RESFRIADO RESFRIADA "
            + "ALIMENTO CHILENO ARGENTINO PORTUGUES ALCOOLICO MACARRAO MASSA BATATA SUCO REFRESCO CERVEJA "
            + "ISOTONICO REFINADO COZINHA LACTEA WAFER RECHEADO RECHEIO").split(" "));
    /**
     * Words that only say how the item is packed or sold ("Biscoito Cookie Chocolate Piraquê Pacote 80G",
     * "Rum Nacional Montilla"), allowed as the only difference besides abbreviations. Flavours, variants and
     * packaging types that change the price (zero, light, refil, sachê) are deliberately absent.
     */
    private static final Set<String> DESCRIPTIVE_WORDS = Set.of(("EMBALAGEM CAIXA PACOTE ECONOMICA ESPECIAL "
            + "PROMOCIONAL MATINAL INSTANTANEO LONG NECK VINHO NACIONAL IMPORTADO ITALIANO FRANCES ESPANHOL "
            + "URUGUAIO FORTIFICADO IQF ESPECIAIS").split(" "));

    private final CatalogKey keys;
    private final Map<String, Description> descriptions = new HashMap<>();
    private final Map<String, String> parents = new HashMap<>();
    private final Map<String, Set<UUID>> storesByRoot = new HashMap<>();

    CatalogMatcher(CatalogKey keys) {
        this.keys = keys;
    }

    /** The identity each product is published under; products no description identifies are left out. */
    Map<UUID, CatalogKey.Identity> match(Collection<Listing> listings) {
        Map<UUID, Description> productDescriptions = describeProducts(listings);
        linkStoreDescriptions(listings, productDescriptions);
        joinSameBrandAndSize();
        joinAbbreviations();
        return publishedIdentities(productDescriptions);
    }

    /** A SKU is published under its own name; a store's description stands in only when that name has no identity. */
    private Map<UUID, Description> describeProducts(Collection<Listing> listings) {
        Map<UUID, Description> productDescriptions = new HashMap<>();
        for (Listing listing : listings) {
            if (!productDescriptions.containsKey(listing.productId())) {
                productDescriptions.put(listing.productId(), describe(listing.productName()));
            }
            if (productDescriptions.get(listing.productId()) == null && listing.storeDescription() != null) {
                productDescriptions.put(listing.productId(), describe(listing.storeDescription()));
            }
        }
        for (Listing listing : listings) {
            Description product = productDescriptions.get(listing.productId());
            if (product == null) continue;
            product.stores.add(listing.storeId());
            product.products.add(listing.productId());
        }
        return productDescriptions;
    }

    /** One SKU described differently by two stores links both descriptions. */
    private void linkStoreDescriptions(Collection<Listing> listings, Map<UUID, Description> productDescriptions) {
        for (Listing listing : listings) {
            Description product = productDescriptions.get(listing.productId());
            Description store = listing.storeDescription() == null ? null : describe(listing.storeDescription());
            if (product != null && store != null && compatible(product, store)) join(product, store);
        }
    }

    /** Different SKUs of the same brand and size whose descriptions say the same thing. */
    private void joinSameBrandAndSize() {
        Map<String, List<Description>> sameBrandAndSize = new HashMap<>();
        for (Description description : descriptions.values()) {
            if (description.identity.brand() == null || description.core.isEmpty()) continue;
            sameBrandAndSize.computeIfAbsent(description.identity.brand() + "|" + description.identity.size(),
                    ignored -> new ArrayList<>()).add(description);
        }
        for (List<Description> candidates : sameBrandAndSize.values()) {
            candidates.sort(Comparator.comparingInt((Description description) -> description.stores.size()).reversed()
                    .thenComparing(description -> description.identity.key()));
            for (int first = 0; first < candidates.size(); first++) {
                for (int second = first + 1; second < candidates.size(); second++) {
                    if (compatible(candidates.get(first), candidates.get(second))) {
                        join(candidates.get(first), candidates.get(second));
                    }
                }
            }
        }
    }

    /**
     * The same product written in full by one store and with ERP abbreviations by another, same brand and
     * size: "LING PERDIGAO CALABR 400gr" is "Ling. Calabresa Perdigão 400g", "Bisc. Cookies Piraque 80g Choc"
     * is "Biscoito Cookie Chocolate Piraquê Pacote 80G". Every word of the shorter description must stand for
     * exactly one word of the longer one (equal, or a prefix of it), and what is left over may only be a
     * descriptive word. When a store sells two products the description could stand for, it is ambiguous
     * there and nothing is joined.
     */
    private void joinAbbreviations() {
        Map<String, List<Description>> sameBrandAndSize = new HashMap<>();
        for (Description description : descriptions.values()) {
            if (description.identity.brand() == null) continue;
            sameBrandAndSize.computeIfAbsent(description.identity.brand() + "|" + description.identity.size(),
                    ignored -> new ArrayList<>()).add(description);
        }
        for (List<Description> candidates : sameBrandAndSize.values()) {
            if (candidates.size() < 2) continue;
            candidates.sort(Comparator.comparing(description -> description.identity.key()));
            for (Description description : candidates) {
                Map<UUID, List<Description>> matchesByStore = new HashMap<>();
                for (Description other : candidates) {
                    if (other == description || other.stores.stream().anyMatch(description.stores::contains)) continue;
                    if (!abbreviates(description, other)) continue;
                    other.stores.forEach(store -> matchesByStore.computeIfAbsent(store, ignored -> new ArrayList<>()).add(other));
                }
                for (List<Description> matches : matchesByStore.values()) {
                    if (matches.stream().map(match -> root(match.identity.key())).distinct().count() == 1) {
                        join(description, matches.getFirst());
                    }
                }
            }
        }
    }

    private static boolean abbreviates(Description first, Description second) {
        Set<String> brand = new HashSet<>(List.of(CatalogKey.words(first.identity.brand()).split(" ")));
        List<String> a = first.core.stream().filter(word -> !brand.contains(word)).toList();
        List<String> b = second.core.stream().filter(word -> !brand.contains(word)).toList();
        if (new HashSet<>(a).equals(new HashSet<>(b))) return false;
        List<String> shorter = a.size() <= b.size() ? a : b;
        List<String> longer = a.size() <= b.size() ? b : a;
        Set<String> used = new HashSet<>();
        List<String> ordered = new ArrayList<>(shorter);
        ordered.sort(Comparator.comparingInt(String::length).reversed());
        for (String word : ordered) {
            if (longer.contains(word) && !used.contains(word)) {
                used.add(word);
                continue;
            }
            // "AA" never abbreviates "AAA": a battery size, not a cut word.
            List<String> candidates = longer.stream().filter(full -> !used.contains(full) && !repeatsOneLetter(full)
                    && ((word.length() >= 2 && full.startsWith(word)) || (full.length() >= 4 && word.startsWith(full)))).toList();
            if (candidates.size() != 1) return false;
            used.add(candidates.getFirst());
        }
        return longer.stream().filter(word -> !used.contains(word)).allMatch(DESCRIPTIVE_WORDS::contains);
    }

    private static boolean repeatsOneLetter(String word) {
        return word.chars().allMatch(character -> character == word.charAt(0));
    }

    /** Every joined description is published under the one most stores use. */
    private Map<UUID, CatalogKey.Identity> publishedIdentities(Map<UUID, Description> productDescriptions) {
        Map<String, Description> published = new HashMap<>();
        for (Description description : descriptions.values()) {
            published.merge(root(description.identity.key()), description,
                    (current, next) -> preferred(current, next) ? current : next);
        }
        Map<UUID, CatalogKey.Identity> identities = new HashMap<>();
        productDescriptions.forEach((productId, description) -> {
            if (description != null) identities.put(productId, published.get(root(description.identity.key())).identity);
        });
        return identities;
    }

    private Description describe(String name) {
        CatalogKey.Identity identity = keys.identify(name);
        if (identity == null) return null;
        Description description = descriptions.computeIfAbsent(identity.key(), ignored -> new Description(identity));
        String[] words = CatalogKey.words(name).split(" ");
        String lastWord = words[words.length - 1];
        if (cutAtErpLimit(name) && description.core.contains(lastWord)) description.truncatedWords.add(lastWord);
        return description;
    }

    /** Whether the description may end in a word the store's ERP cut short. */
    static boolean cutAtErpLimit(String name) {
        return name.strip().length() == ERP_DESCRIPTION_LENGTH;
    }

    private static boolean compatible(Description first, Description second) {
        if (!first.identity.size().equals(second.identity.size())) return false;
        if (first.core.isEmpty() || second.core.isEmpty()) return false;
        return first.core.equals(second.core) || completesCutWord(first, second) || completesCutWord(second, first);
    }

    /** "ACUCAR GRANULADO UNIAO PRE" (cut at 30 characters) is "ACUCAR GRANULADO UNIAO PREMIUM". */
    private static boolean completesCutWord(Description cut, Description complete) {
        for (String word : cut.truncatedWords) {
            Set<String> kept = new HashSet<>(cut.core);
            kept.remove(word);
            Set<String> added = new HashSet<>(complete.core);
            added.removeAll(kept);
            if (complete.core.containsAll(kept) && added.size() == 1) {
                String completion = added.iterator().next();
                if (completion.length() > word.length() && completion.startsWith(word)) return true;
            }
        }
        return false;
    }

    private void join(Description first, Description second) {
        String firstRoot = root(first.identity.key());
        String secondRoot = root(second.identity.key());
        if (firstRoot.equals(secondRoot)) return;
        Set<UUID> firstStores = storesByRoot.computeIfAbsent(firstRoot, root -> new HashSet<>(descriptions.get(root).stores));
        Set<UUID> secondStores = storesByRoot.computeIfAbsent(secondRoot, root -> new HashSet<>(descriptions.get(root).stores));
        if (firstStores.stream().anyMatch(secondStores::contains)) return;
        parents.put(secondRoot, firstRoot);
        firstStores.addAll(secondStores);
        storesByRoot.remove(secondRoot);
    }

    private String root(String key) {
        String parent = parents.getOrDefault(key, key);
        if (parent.equals(key)) return key;
        String root = root(parent);
        parents.put(key, root);
        return root;
    }

    /** The description most stores use names the published item. */
    private static boolean preferred(Description current, Description next) {
        if (current.stores.size() != next.stores.size()) return current.stores.size() > next.stores.size();
        if (current.products.size() != next.products.size()) return current.products.size() > next.products.size();
        return current.identity.key().compareTo(next.identity.key()) <= 0;
    }

    record Listing(UUID productId, UUID storeId, String productName, String storeDescription) {
    }

    private static final class Description {
        private final CatalogKey.Identity identity;
        private final Set<String> core = new HashSet<>();
        private final Set<String> truncatedWords = new HashSet<>();
        private final Set<UUID> stores = new HashSet<>();
        private final Set<UUID> products = new HashSet<>();

        Description(CatalogKey.Identity identity) {
            this.identity = identity;
            for (String word : identity.key().substring(0, identity.key().indexOf('|')).split(" ")) {
                if (!OPTIONAL_WORDS.contains(word)) core.add(word);
            }
        }
    }
}
