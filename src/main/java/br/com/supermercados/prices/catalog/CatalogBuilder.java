package br.com.supermercados.prices.catalog;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Groups every collected retailer SKU into generic catalog items and keeps only the items sold by at least
 * {@code app.catalog.min-stores} stores. Item IDs are stable per key, so shopping lists survive rebuilds.
 */
@Service
public class CatalogBuilder {

    private static final Logger LOGGER = LoggerFactory.getLogger(CatalogBuilder.class);
    /** Sizes and pack counts as retailers write them ("2lt", "1,5 L", "C/6", "6x350ml", "30 unidades"). */
    private static final java.util.regex.Pattern SIZE_TEXT = java.util.regex.Pattern.compile(
            "(?iu)\\b\\d+\\s*x\\s*(?=\\d)|\\b(?:c/|com|pack)\\s*\\d+\\s*(?:unidades?|unids?\\.?|un\\.?|und)?(?![\\p{L}\\d])"
                    + "|\\b\\d+(?:[.,]\\d+)?\\s*(?:mililitros?|ml|litros?|lts?|l|quilos?|kg|gramas?|grs?|g|unidades?"
                    + "|unids?\\.?|un\\.?|und)(?![\\p{L}\\d])");
    /** Retailer abbreviations spelled out in generic names ("Leite Po" → "Leite em Pó", "Tp 1" → "Tipo 1"). */
    private static final String[][] ABBREVIATIONS = {
            {"(?iu)\\bleite\\s+(?:em\\s+)?p[oó]\\b", "Leite em Pó"},
            {"(?iu)\\btp\\.?\\s*(?=\\d)", "Tipo "},
            {"(?iu)\\bliq\\.?(?!\\p{L})", "Líquido"},
            {"(?iu)\\bdesn\\.?(?!\\p{L})", "Desnatado"},
            {"(?iu)\\bsemidesnatad(?!o)", "Semidesnatado"},
            {"(?iu)\\bjoao\\b", "João"},
    };
    /** Product-type and packaging words that the generic name does not need. */
    private static final java.util.regex.Pattern GENERIC_WORDS = java.util.regex.Pattern.compile(
            "(?iu)(?<!\\p{L})(?:refrigerante|refrig\\.?|refri|refr\\.?|ref\\.?|(?:bebida|beb\\.?)(?!\\s*l[aá]ctea)|energ[ée]tic[oa]|energ\\.?"
                    + "|ener|repositor|lv|uht|lata|lt|pet|garrafa|gfa|vidro|descart[áa]vel|original|tradicional|gelad[oa]|pct|pacote)(?!\\p{L})");

    private final JdbcTemplate jdbc;
    private final Clock clock;
    private final int minimumStores;

    public CatalogBuilder(JdbcTemplate jdbc, Clock clock, @Value("${app.catalog.min-stores:2}") int minimumStores) {
        if (minimumStores < 1) throw new IllegalArgumentException("app.catalog.min-stores must be positive");
        this.jdbc = jdbc;
        this.clock = clock;
        this.minimumStores = minimumStores;
    }

    @Transactional
    public synchronized Summary rebuild() {
        Instant startedAt = clock.instant();
        // Each store's latest description of the SKU: older ones may describe a product the code no longer sells.
        List<Offer> offers = jdbc.query("""
                SELECT p.id, p.name, p.brand, p.category, p.image_url, latest.store_id, latest.source_product_name
                FROM (SELECT DISTINCT ON (r.product_id, r.store_id) r.product_id, r.store_id, r.source_product_name
                      FROM price_records r
                      ORDER BY r.product_id, r.store_id, r.collected_at DESC, r.recorded_at DESC, r.id DESC) latest
                JOIN products p ON p.id = latest.product_id
                JOIN stores s ON s.id = latest.store_id AND s.active
                """, (row, index) -> new Offer(row.getObject(1, UUID.class), row.getString(2), row.getString(3),
                row.getString(4), row.getString(5), row.getObject(6, UUID.class), row.getString(7)));
        CatalogKey keys = CatalogKey.learn(offers.stream().map(Offer::brand).toList(),
                offers.stream().map(Offer::name).distinct().toList());

        Map<UUID, CatalogKey.Identity> identities = new CatalogMatcher(keys).match(offers.stream()
                .map(offer -> new CatalogMatcher.Listing(offer.productId(), offer.storeId(), offer.name(),
                        offer.storeDescription()))
                .toList());
        Map<String, Group> groups = new LinkedHashMap<>();
        for (Offer offer : offers) {
            CatalogKey.Identity identity = identities.get(offer.productId());
            if (identity == null) continue;
            groups.computeIfAbsent(identity.key(), key -> new Group(identity)).add(offer);
        }
        List<Group> published = groups.values().stream().filter(group -> group.stores.size() >= minimumStores).toList();

        Timestamp now = Timestamp.from(startedAt);
        jdbc.batchUpdate("""
                INSERT INTO catalog_items (id, catalog_key, display_name, brand, size_label, category, image_url,
                    representative_product_id, search_text, store_count, active, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, TRUE, ?)
                ON CONFLICT (catalog_key) DO UPDATE SET display_name = EXCLUDED.display_name,
                    brand = EXCLUDED.brand, size_label = EXCLUDED.size_label, category = EXCLUDED.category,
                    image_url = EXCLUDED.image_url, representative_product_id = EXCLUDED.representative_product_id,
                    search_text = EXCLUDED.search_text, store_count = EXCLUDED.store_count, active = TRUE,
                    updated_at = EXCLUDED.updated_at
                """, published, 500, (statement, group) -> {
            Offer representative = group.representative();
            String displayName = group.displayName();
            statement.setObject(1, UUID.randomUUID());
            statement.setString(2, group.identity.key());
            statement.setString(3, displayName);
            statement.setString(4, group.identity.brand() == null ? null : titleCase(group.identity.brand()));
            statement.setString(5, CatalogKey.sizeLabel(group.identity.size()));
            statement.setString(6, group.category());
            statement.setString(7, representative.imageUrl());
            statement.setObject(8, representative.productId());
            statement.setString(9, truncate(CatalogKey.words(displayName) + " "
                    + group.identity.key().substring(0, group.identity.key().indexOf('|')), 1200));
            statement.setInt(10, group.stores.size());
            statement.setTimestamp(11, now);
        });
        int deactivated = jdbc.update("UPDATE catalog_items SET active = FALSE WHERE active AND updated_at < ?", now);

        Map<String, UUID> ids = new HashMap<>();
        jdbc.query("SELECT catalog_key, id FROM catalog_items WHERE active",
                row -> { ids.put(row.getString(1), row.getObject(2, UUID.class)); });
        jdbc.update("DELETE FROM catalog_item_products");
        List<Object[]> links = new ArrayList<>();
        for (Group group : published) {
            UUID itemId = ids.get(group.identity.key());
            group.products.keySet().forEach(productId -> links.add(new Object[] {productId, itemId}));
        }
        jdbc.batchUpdate("INSERT INTO catalog_item_products (product_id, catalog_item_id) VALUES (?, ?)", links);
        int relinked = relinkShoppingListItems();

        Summary summary = new Summary(offers.size(), published.size(), links.size(), deactivated);
        LOGGER.info("Catálogo genérico reconstruído: {} itens em {}+ mercados, {} SKUs vinculados, {} desativados, "
                + "{} itens de lista religados", summary.items(), minimumStores, summary.linkedProducts(),
                summary.deactivated(), relinked);
        return summary;
    }

    /**
     * When better matching changes an item's key, the old item is deactivated. Lists that saved it move to the
     * item that now holds the same SKU, unless the list already has that item.
     */
    private int relinkShoppingListItems() {
        return jdbc.update("""
                UPDATE shopping_list_items item SET catalog_item_id = link.catalog_item_id
                FROM catalog_items previous, catalog_item_products link
                WHERE item.catalog_item_id = previous.id AND NOT previous.active
                  AND link.product_id = item.product_id
                  AND NOT EXISTS (SELECT 1 FROM shopping_list_items other
                                  WHERE other.shopping_list_id = item.shopping_list_id
                                    AND other.catalog_item_id = link.catalog_item_id)
                """);
    }

    private static String truncate(String value, int length) {
        return value.length() <= length ? value : value.substring(0, length);
    }

    static String titleCase(String value) {
        StringBuilder result = new StringBuilder();
        for (String word : value.toLowerCase(java.util.Locale.ROOT).split(" ")) {
            if (word.isEmpty()) continue;
            if (!result.isEmpty()) result.append(' ');
            boolean keepLower = word.length() <= 2 && Set.of("de", "da", "do", "e", "em", "c/").contains(word);
            boolean measure = word.matches("\\d.*");
            result.append(keepLower || measure ? word : Character.toUpperCase(word.charAt(0)) + word.substring(1));
        }
        return result.toString();
    }

    public record Summary(int offers, int items, int linkedProducts, int deactivated) {
    }

    private record Offer(UUID productId, String name, String brand, String category, String imageUrl, UUID storeId,
            String storeDescription) {
    }

    static final class Group {
        private final CatalogKey.Identity identity;
        private final Map<UUID, Offer> products = new LinkedHashMap<>();
        private final Set<UUID> stores = new HashSet<>();

        Group(CatalogKey.Identity identity) {
            this.identity = identity;
        }

        void add(Offer offer) {
            products.putIfAbsent(offer.productId(), offer);
            stores.add(offer.storeId());
        }

        /** The cleanest retailer description: complete, mixed case, few abbreviations, short. */
        Offer representative() {
            Comparator<Offer> quality = Comparator
                    .comparing((Offer offer) -> CatalogMatcher.cutAtErpLimit(offer.name()))
                    .thenComparing(offer -> offer.name().equals(offer.name().toUpperCase(java.util.Locale.ROOT)))
                    .thenComparing(offer -> offer.imageUrl() == null)
                    .thenComparingLong(offer -> offer.name().chars().filter(character -> character == '.').count())
                    .thenComparingInt(offer -> cleanName(offer.name()).length())
                    .thenComparing(Offer::name);
            return products.values().stream().min(quality).orElseThrow();
        }

        /** "Refr. Coca-cola 2lt Pet" → "Coca-Cola 2 L": retailer noise removed, standard size appended. */
        String displayName() {
            return genericName(representative().name(), identity);
        }

        static String genericName(String retailerName, CatalogKey.Identity identity) {
            String name = SIZE_TEXT.matcher(cleanName(retailerName)).replaceAll(" ");
            // Checked after removing sizes: "ARROZ TIO JOAO 5kg" is all caps despite its lowercase unit.
            if (name.equals(name.toUpperCase(java.util.Locale.ROOT))) name = titleCase(name);
            name = GENERIC_WORDS.matcher(name).replaceAll(" ");
            for (String[] abbreviation : ABBREVIATIONS) {
                name = name.replaceAll(abbreviation[0], abbreviation[1]);
            }
            name = name.replaceAll("(?i)\\bcoca[\\s-]*cola\\b", "Coca-Cola")
                    .replaceAll("\\s+", " ").replaceAll("^[\\s.,/-]+|[\\s.,/(-]+$", "").strip();
            if (name.isBlank()) name = titleCase(identity.key().substring(0, identity.key().indexOf('|')));
            return truncate(name + " " + CatalogKey.sizeLabel(identity.size()), 200);
        }

        String category() {
            return products.values().stream().map(Offer::category).filter(category -> category != null && !category.isBlank())
                    .collect(Collectors.groupingBy(Function.identity(), Collectors.counting())).entrySet().stream()
                    .max(Map.Entry.comparingByValue()).map(Map.Entry::getKey).orElse(null);
        }

        private static String cleanName(String name) {
            return name.replaceAll("(?i)\\(pre[çc]o de 1 kg\\)", "1 kg")
                    .replaceAll("(?i)\\b(gelad[oa]|geladinho)\\b", " ")
                    .replaceAll("\\s+", " ").strip();
        }
    }
}
