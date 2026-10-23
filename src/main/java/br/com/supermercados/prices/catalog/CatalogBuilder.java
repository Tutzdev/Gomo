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
            // ERP abbreviations of the VIP stores ("Amac. Roupa Conc. Baby Soft", "Bisc. Rech. Piraque").
            {"(?iu)\\bcreme\\s+dent\\.?(?!\\p{L})", "Creme Dental"},
            {"(?iu)\\bapar\\.?\\s+barb\\.?(?!\\p{L})", "Aparelho de Barbear"},
            {"(?iu)\\banti?\\.?\\s+bucal\\b", "Antisséptico Bucal"},
            {"(?iu)\\bamac\\.?(?!\\p{L})", "Amaciante"},
            {"(?iu)\\bbisc\\.?(?!\\p{L})", "Biscoito"},
            {"(?iu)\\brech\\.?(?!\\p{L})", "Recheado"},
            {"(?iu)\\bdesod\\.?(?!\\p{L})", "Desodorante"},
            {"(?iu)\\babs\\.(?!\\p{L})", "Absorvente"},
            {"(?iu)\\bachoc\\.?(?!\\p{L})", "Achocolatado"},
            {"(?iu)\\bcondic\\.?(?!\\p{L})", "Condicionador"},
            {"(?iu)\\bsh\\.(?!\\p{L})", "Shampoo"},
            {"(?iu)\\bconc\\.?(?!\\p{L})", "Concentrado"},
            {"(?iu)\\blimp\\.(?!\\p{L})", "Limpador"},
            {"(?iu)\\btint\\.?(?!\\p{L})", "Tintura"},
            {"(?iu)\\bchoc\\.?(?!\\p{L})", "Chocolate"},
            {"(?iu)\\bsabon\\.?(?!\\p{L})", "Sabonete"},
            {"(?iu)\\bdeterg\\.?(?!\\p{L})", "Detergente"},
            {"(?iu)\\bmarg\\.?(?!\\p{L})", "Margarina"},
            {"(?iu)\\breq\\.?(?!\\p{L})", "Requeijão"},
            {"(?iu)\\biog\\.?(?!\\p{L})", "Iogurte"},
            {"(?iu)\\bsalg\\.?(?!\\p{L})", "Salgadinho"},
            {"(?iu)\\bdesinf\\.?(?!\\p{L})", "Desinfetante"},
            {"(?iu)\\bcreme\\s+trat\\.?(?!\\p{L})", "Creme de Tratamento"},
            {"(?iu)\\bcreme\\s+pent\\.?(?!\\p{L})", "Creme de Pentear"},
            {"(?iu)\\bpapel\\s+hig\\.?(?!\\p{L})", "Papel Higiênico"},
            {"(?iu)\\bf\\.\\s*dupla\\b", "Folha Dupla"},
            {"(?iu)\\binset\\.?(?!\\p{L})", "Inseticida"},
            {"(?iu)\\brepel\\.?(?!\\p{L})", "Repelente"},
            {"(?iu)\\bodoriz\\.?(?!\\p{L})", "Odorizador"},
            {"(?iu)\\belet\\.(?!\\p{L})", "Elétrico"},
            {"(?iu)\\bcerv\\.?(?!\\p{L})", "Cerveja"},
            {"(?iu)\\bling\\.(?!\\p{L})", "Linguiça"},
            {"(?iu)\\bhambur\\.?(?!\\p{L})", "Hambúrguer"},
            {"(?iu)\\bbov\\.?(?!\\p{L})", "Bovino"},
            {"(?iu)\\bapar\\.(?!\\p{L})", "Aparelho"},
            {"(?iu)\\bshampo(?!\\p{L})", "Shampoo"},
    };
    /** A number left before the size by a description cut short: "Geleia Queensberry Classic 320" + "320 g". */
    private static final java.util.regex.Pattern TRAILING_NUMBER = java.util.regex.Pattern.compile("^(.*\\S)\\s+(\\d+(?:[.,]\\d+)?)$");
    /** Product-type and packaging words that the generic name does not need. */
    private static final java.util.regex.Pattern GENERIC_WORDS = java.util.regex.Pattern.compile(
            "(?iu)(?<!\\p{L})(?:refrigerante|refrig\\.?|refri|refr\\.?|ref\\.?|(?:bebida|beb\\.?)(?!\\s*l[aá]ctea)|energ[ée]tic[oa]|energ\\.?"
                    + "|ener|repositor|lv|uht|lata|lt|pet|garrafa|gfa|vidro|descart[áa]vel|original|tradicional|tradic\\.?"
                    + "|trad\\.?|gelad[oa]|pct|pacote)(?!\\p{L})");

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
        Map<String, String> spellings = Group.spellings(offers.stream().map(Offer::name).distinct().toList());
        Map<Group, String> names = Group.distinctNames(published, spellings);
        Map<String, String> brandSpellings = Group.brandSpellings(offers.stream().map(Offer::brand).toList());

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
            String displayName = names.get(group);
            statement.setObject(1, UUID.randomUUID());
            statement.setString(2, group.identity.key());
            statement.setString(3, displayName);
            statement.setString(4, group.brandLabel(keys, brandSpellings));
            statement.setString(5, CatalogKey.sizeLabel(group.identity.size()));
            statement.setString(6, CatalogCategory.of(displayName, group.retailerCategories()));
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
        String displayName(Map<String, String> spellings) {
            if (identity.genericName() != null) {
                return accented(identity.genericName(), spellings) + " " + CatalogKey.sizeLabel(identity.size());
            }
            return CatalogSpelling.accented(genericName(displaySource(), identity));
        }

        /**
         * The text the name is made from. VIP stores cut the SKU name at 30 characters ("Geleia Queensberry Classic
         * 320") but describe it in full on the shelf ("Geleia Queensberry Clássica 320g Morango"): the full
         * description is preferred, then mixed case, accents and few abbreviations.
         */
        String displaySource() {
            Comparator<String> quality = Comparator
                    .<String, Boolean>comparing(CatalogMatcher::cutAtErpLimit)
                    .thenComparing(text -> text.equals(text.toUpperCase(java.util.Locale.ROOT)))
                    .thenComparing(text -> text.chars().allMatch(character -> character < 128))
                    .thenComparingLong(text -> text.chars().filter(character -> character == '.').count())
                    .thenComparingInt(text -> cleanName(text).length())
                    .thenComparing(Comparator.naturalOrder());
            return products.values().stream().map(offer -> {
                String description = offer.storeDescription();
                boolean better = CatalogMatcher.cutAtErpLimit(offer.name()) && description != null
                        && !description.isBlank() && !CatalogMatcher.cutAtErpLimit(description)
                        && !CatalogKey.isBundle(description);
                return better ? description.strip() : offer.name();
            }).min(quality).orElseThrow();
        }

        /**
         * Two items must not look the same in a list. When their names collide (a flavour the chosen description
         * leaves out), each gets the words of its own key that the name lacks: "… Morango 320 g", "… Amora 320 g".
         */
        static Map<Group, String> distinctNames(List<Group> groups, Map<String, String> spellings) {
            Map<Group, String> names = new HashMap<>();
            groups.forEach(group -> names.put(group, group.displayName(spellings)));
            Map<String, List<Group>> sameName = groups.stream().collect(Collectors.groupingBy(names::get));
            for (List<Group> colliding : sameName.values()) {
                if (colliding.size() < 2) continue;
                for (Group group : colliding) {
                    String name = names.get(group);
                    String size = CatalogKey.sizeLabel(group.identity.size());
                    List<String> nameWords = List.of(CatalogKey.words(name).split(" "));
                    List<String> missing = java.util.Arrays.stream(group.identity.key()
                                    .substring(0, group.identity.key().indexOf('|')).split(" "))
                            .filter(word -> word.length() >= 2 && nameWords.stream().noneMatch(
                                    present -> present.equals(word) || (present.length() >= 3 && word.startsWith(present))))
                            .map(word -> CatalogSpelling.accented(spellings.getOrDefault(word, titleCase(word))))
                            .toList();
                    if (missing.isEmpty() || !name.endsWith(size)) continue;
                    String base = name.substring(0, name.length() - size.length()).strip();
                    names.put(group, truncate(base + " " + String.join(" ", missing) + " " + size, 200));
                }
            }
            return names;
        }

        /**
         * How each declared brand is best written, by its words: with accents and in mixed case when some store
         * writes it so ("Nestlé", "Ypê", "3 Corações"), otherwise the most common spelling.
         */
        static Map<String, String> brandSpellings(java.util.Collection<String> declaredBrands) {
            Map<String, Map<String, Integer>> counts = new HashMap<>();
            for (String declared : declaredBrands) {
                if (declared == null || declared.isBlank()) continue;
                counts.computeIfAbsent(CatalogKey.words(declared), ignored -> new HashMap<>())
                        .merge(declared.strip().replaceAll("\\s+", " "), 1, Integer::sum);
            }
            Map<String, String> spellings = new HashMap<>();
            counts.forEach((brand, spelled) -> spellings.put(brand, spelled.entrySet().stream()
                    .max(Comparator.comparing((Map.Entry<String, Integer> entry) -> !entry.getKey().chars()
                                    .allMatch(character -> character < 128))
                            .thenComparing(entry -> !entry.getKey().equals(entry.getKey().toUpperCase(java.util.Locale.ROOT)))
                            .thenComparing(Map.Entry::getValue)
                            .thenComparing(Map.Entry::getKey, Comparator.reverseOrder()))
                    .orElseThrow().getKey()));
            return spellings;
        }

        /**
         * The brand found in the description, or else the one the stores declare for these SKUs (VIP stores
         * declare none, and a description may not name it). A product word declared as brand ("Óleo") is not one.
         */
        String brandLabel(CatalogKey keys, Map<String, String> brandSpellings) {
            String brand = identity.brand();
            if (brand == null && identity.genericName() == null) {
                brand = products.values().stream().map(Offer::brand).filter(keys::knownBrand)
                        .collect(Collectors.groupingBy(CatalogKey::words, Collectors.counting())).entrySet().stream()
                        .max(Map.Entry.<String, Long>comparingByValue().thenComparing(Map.Entry::getKey))
                        .map(Map.Entry::getKey).orElse(null);
            }
            if (brand == null) return null;
            String spelled = brandSpellings.getOrDefault(CatalogKey.words(brand), brand);
            boolean oneCase = spelled.equals(spelled.toUpperCase(java.util.Locale.ROOT))
                    || spelled.equals(spelled.toLowerCase(java.util.Locale.ROOT));
            return CatalogSpelling.accented(oneCase ? titleCase(spelled) : spelled);
        }

        /**
         * How the markets spell each word, for generic names: the most common accented spelling ("Maçã"
         * beats a rarer "Maça"), or the most common plain one when no market writes the accents.
         */
        static Map<String, String> spellings(java.util.Collection<String> retailerNames) {
            Map<String, Map<String, Integer>> counts = new HashMap<>();
            for (String retailerName : retailerNames) {
                for (String word : retailerName.split("[\\s/()-]+")) {
                    String key = CatalogKey.words(word);
                    if (key.isEmpty() || key.contains(" ")) continue;
                    counts.computeIfAbsent(key, ignored -> new HashMap<>()).merge(titleCase(word), 1, Integer::sum);
                }
            }
            Map<String, String> spellings = new HashMap<>();
            counts.forEach((key, spelled) -> spellings.put(key, spelled.entrySet().stream()
                    .max(Comparator.comparing((Map.Entry<String, Integer> entry) -> !entry.getKey().chars()
                                    .allMatch(character -> character < 128))
                            .thenComparing(Map.Entry::getValue).thenComparing(Map.Entry::getKey, Comparator.reverseOrder()))
                    .orElseThrow().getKey()));
            return spellings;
        }

        /** "BANANA MACA" → "Banana Maçã": each key word as the markets spell it. */
        static String accented(String genericName, Map<String, String> spellings) {
            StringBuilder name = new StringBuilder();
            for (String word : genericName.split(" ")) {
                if (!name.isEmpty()) name.append(' ');
                boolean keyWord = word.equals(CatalogKey.words(word));
                name.append(keyWord ? spellings.getOrDefault(word, titleCase(word)) : word);
            }
            return name.toString();
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
            name = withoutCutSize(name, identity.size());
            if (name.isBlank()) name = titleCase(identity.key().substring(0, identity.key().indexOf('|')));
            return truncate(name + " " + CatalogKey.sizeLabel(identity.size()), 200);
        }

        /** "Gatorade Limão 5" + 500 ml, "Leite Cond Itambé 39" + 395 g: the cut number goes, the size stays. */
        static String withoutCutSize(String name, String size) {
            int separator = size.indexOf('x');
            String measure = size.substring(separator + 1);
            Set<String> forms = new HashSet<>();
            if (measure.equals("UN")) {
                forms.add(size.substring(0, separator));
            } else {
                java.math.BigDecimal amount = new java.math.BigDecimal(measure.replaceAll("[A-Z]+$", ""));
                forms.add(amount.stripTrailingZeros().toPlainString());
                forms.add(amount.movePointLeft(3).stripTrailingZeros().toPlainString());
            }
            java.util.regex.Matcher trailing = TRAILING_NUMBER.matcher(name);
            while (trailing.matches()) {
                String number = trailing.group(2).replace(',', '.');
                if (forms.stream().noneMatch(form -> form.startsWith(number))) break;
                name = trailing.group(1);
                trailing = TRAILING_NUMBER.matcher(name);
            }
            return name;
        }

        /** The stores' own categories for these SKUs, most used first. */
        List<String> retailerCategories() {
            return products.values().stream().map(Offer::category).filter(category -> category != null && !category.isBlank())
                    .collect(Collectors.groupingBy(Function.identity(), Collectors.counting())).entrySet().stream()
                    .sorted(Map.Entry.<String, Long>comparingByValue().reversed().thenComparing(Map.Entry::getKey))
                    .map(Map.Entry::getKey).toList();
        }

        private static String cleanName(String name) {
            return name.replaceAll("(?i)\\(pre[çc]o de 1 kg\\)", "1 kg")
                    // "CAFE FARAO VACUO 500 (s)": the plural marker of a cut "Grama(s)".
                    .replaceAll("(?i)\\s*\\(s\\)", " ")
                    .replaceAll("(?i)\\b(gelad[oa]|geladinho)\\b", " ")
                    .replaceAll("\\s+", " ").strip();
        }
    }
}
