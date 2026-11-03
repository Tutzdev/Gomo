package br.com.supermercados.prices.catalog;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.com.supermercados.prices.common.ApiException;
import br.com.supermercados.prices.price.PricePolicy;
import br.com.supermercados.prices.price.PriceQuote;
import br.com.supermercados.prices.price.PriceRecord;
import br.com.supermercados.prices.price.PriceRecordRepository;
import br.com.supermercados.prices.price.UsualPrice;
import br.com.supermercados.prices.product.Product;
import br.com.supermercados.prices.product.ProductRepository;
import br.com.supermercados.prices.store.StoreResponse;
import br.com.supermercados.prices.store.StoreService;
import br.com.supermercados.prices.subscription.ComparisonAccess;
import br.com.supermercados.prices.subscription.FreePlan;
import br.com.supermercados.prices.subscription.PlanLimit;
import br.com.supermercados.prices.subscription.PlanLimitException;

@Service
@Transactional(readOnly = true)
public class CatalogService {

    private static final int MAXIMUM_STORES = 5000;

    private final CatalogItemRepository items;
    private final NamedParameterJdbcTemplate jdbc;
    private final PriceRecordRepository prices;
    private final ProductRepository products;
    private final StoreService stores;
    private final PricePolicy policy;
    private final Clock clock;
    private final java.time.Duration maxPriceAge;
    private final java.time.Duration inStoreMaxPriceAge;

    public CatalogService(CatalogItemRepository items, NamedParameterJdbcTemplate jdbc, PriceRecordRepository prices,
            ProductRepository products, StoreService stores, PricePolicy policy, Clock clock,
            @org.springframework.beans.factory.annotation.Value("${app.prices.max-age:P2D}") java.time.Duration maxPriceAge,
            @org.springframework.beans.factory.annotation.Value("${app.prices.in-store-max-age:P7D}") java.time.Duration inStoreMaxPriceAge) {
        this.inStoreMaxPriceAge = inStoreMaxPriceAge;
        this.maxPriceAge = maxPriceAge;
        this.items = items;
        this.jdbc = jdbc;
        this.prices = prices;
        this.products = products;
        this.stores = stores;
        this.policy = policy;
        this.clock = clock;
    }

    public Page<CatalogItemResponse> search(String query, UUID cityId, List<UUID> storeIds, Pageable pageable) {
        CatalogKey.SearchTerms terms = CatalogKey.searchTerms(query);
        List<String> exact = new ArrayList<>(List.of("item.active"));
        List<String> approximate = new ArrayList<>(List.of("item.active"));
        MapSqlParameterSource parameters = new MapSqlParameterSource();
        for (int index = 0; index < terms.tokens().size(); index++) {
            String token = terms.tokens().get(index);
            parameters.addValue("prefix" + index, "% " + token + "%");
            parameters.addValue("token" + index, token);
            // Word prefix ("coc" finds "COCA"); a close spelling ("refrigerente") only when nothing matches exactly,
            // otherwise "coca cola" would also bring "Olá Coco".
            exact.add("' ' || item.search_text LIKE :prefix" + index);
            approximate.add("(' ' || item.search_text LIKE :prefix" + index
                    + (token.length() >= 4 ? " OR :token" + index + " <% item.search_text" : "") + ")");
        }
        List<StoreResponse> selected = comparisonStores(cityId, storeIds);
        Instant now = clock.instant();
        String priced = "SELECT NULL::uuid AS catalog_item_id, 0 AS priced_stores WHERE FALSE";
        if (cityId != null) {
            if (selected.isEmpty()) return Page.empty(pageable);
            // Per item, the stores whose newest observation of a linked SKU is a usable price right now (same
            // freshness, validity and stock rules as PricePolicy). Read from latest_prices, one row per store and
            // SKU, so the cost does not grow with the price history.
            priced = """
                    SELECT link.catalog_item_id, count(DISTINCT latest.store_id) AS priced_stores
                    FROM latest_prices latest
                    JOIN catalog_item_products link ON link.product_id = latest.product_id
                    WHERE latest.store_id IN (:stores) AND latest.collected_at > :visitFreshAfter
                      AND (latest.origin_type = 'STORE_VISIT' OR latest.collected_at > :freshAfter)
                      AND latest.collected_at <= :now AND latest.availability <> 'UNAVAILABLE'
                      AND (latest.valid_until IS NULL OR latest.valid_until > :now)
                    GROUP BY link.catalog_item_id""";
            parameters.addValue("stores", selected.stream().map(StoreResponse::id).toList())
                    .addValue("now", java.sql.Timestamp.from(now))
                    .addValue("freshAfter", java.sql.Timestamp.from(now.minus(maxPriceAge)))
                    .addValue("visitFreshAfter", java.sql.Timestamp.from(now.minus(inStoreMaxPriceAge)));
        }
        if (terms.size() != null) parameters.addValue("size", "%|1" + terms.size());
        String found = foundSql(priced, exact, approximate, cityId != null, terms.size() != null);
        parameters.addValue("limit", pageable.getPageSize()).addValue("offset", pageable.getOffset());
        // Searching: the plainest variant first ("Coca-Cola" before "Coca-Cola Zero"). Browsing: the products
        // with current prices in the most stores, i.e. the most useful comparisons.
        String order = terms.tokens().isEmpty() ? "" : "array_length(string_to_array(split_part(catalog_key, '|', 1), ' '), 1), ";
        List<UUID> ids = new ArrayList<>();
        long[] total = {0};
        jdbc.query(found + " SELECT id, count(*) OVER () AS total FROM found ORDER BY " + order
                + "priced_stores DESC, store_count DESC, length(display_name), id LIMIT :limit OFFSET :offset",
                parameters, row -> {
                    ids.add(row.getObject("id", UUID.class));
                    total[0] = row.getLong("total");
                });
        // A page past the end has no row to carry the total.
        if (ids.isEmpty() && pageable.getOffset() > 0) {
            Long counted = jdbc.queryForObject(found + " SELECT count(*) FROM found", parameters, Long.class);
            total[0] = counted == null ? 0 : counted;
        }
        Map<UUID, CatalogItem> byId = items.findAllById(ids).stream()
                .collect(Collectors.toMap(CatalogItem::getId, Function.identity()));
        List<CatalogItem> ordered = ids.stream().map(byId::get).toList();
        return new PageImpl<>(respond(ordered, selected), pageable, total[0]);
    }

    /**
     * One statement, so the current prices are worked out once per search. Exact word matches win; a close
     * spelling only counts when nothing matches exactly, otherwise "coca cola" would also bring "Olá Coco".
     * "coca 1 litro" means the 1 L bottle: other sizes appear only when that size is not sold anywhere.
     */
    private static String foundSql(String priced, List<String> exact, List<String> approximate, boolean requirePrice,
            boolean sized) {
        // Without a current price in the city there is nothing real to compare.
        String priceFilter = requirePrice ? " AND coalesce(priced.priced_stores, 0) > 0" : "";
        String columns = "SELECT item.id, item.catalog_key, item.store_count, item.display_name,"
                + " coalesce(priced.priced_stores, 0) AS priced_stores"
                + " FROM catalog_items item LEFT JOIN priced ON priced.catalog_item_id = item.id WHERE ";
        return "WITH priced AS MATERIALIZED (" + priced + "),"
                + " exact AS (" + columns + String.join(" AND ", exact) + priceFilter + "),"
                + " matched AS (SELECT * FROM exact UNION ALL " + columns + String.join(" AND ", approximate)
                + priceFilter + " AND NOT EXISTS (SELECT 1 FROM exact)),"
                + (sized
                        ? " sized AS (SELECT * FROM matched WHERE catalog_key LIKE :size),"
                                + " found AS (SELECT * FROM sized UNION ALL SELECT * FROM matched"
                                + " WHERE NOT EXISTS (SELECT 1 FROM sized))"
                        : " found AS (SELECT * FROM matched)");
    }

    /**
     * Stores with a current price for the item, cheapest first. A free plan sees only the cheapest stores of the
     * whole city: a store filter narrows that view but never reveals a store outside it.
     */
    public CatalogItemDetailResponse find(UUID itemId, UUID cityId, List<UUID> storeIds, ComparisonAccess access) {
        CatalogItem item = require(itemId);
        List<StoreResponse> selected = comparisonStores(cityId, storeIds);
        List<StoreResponse> ranked = access.premium() ? selected : comparisonStores(cityId, null);
        List<CatalogItemDetailResponse.StoreOffer> rankedOffers = rankOffers(itemId, ranked);

        Set<UUID> selectedIds = selected.stream().map(StoreResponse::id).collect(Collectors.toSet());
        List<CatalogItemDetailResponse.StoreOffer> selectedOffers = rankedOffers.stream()
                .filter(offer -> selectedIds.contains(offer.storeId())).toList();
        List<CatalogItemDetailResponse.StoreOffer> visibleOffers = access.visibleOf(rankedOffers).stream()
                .filter(offer -> selectedIds.contains(offer.storeId())).toList();

        return new CatalogItemDetailResponse(respond(List.of(item), selected).getFirst(), visibleOffers,
                selected.size() - selectedOffers.size(),
                access.withLockedStores(selectedOffers.size() - visibleOffers.size()));
    }

    /**
     * Daily lowest price per store over the Premium history window, for the price-variation chart, read from
     * {@code daily_lowest_prices} (a promotion counts under the same rule as {@link PricePolicy}). The verdict
     * reads that history against the cheapest current offer of the same stores.
     */
    public CatalogItemHistoryResponse history(UUID itemId, UUID cityId, List<UUID> storeIds, boolean premium) {
        if (!premium) {
            throw new PlanLimitException(PlanLimit.PRICE_HISTORY, "O histórico de preços faz parte do Premium.");
        }
        require(itemId);
        Instant since = clock.instant().minus(FreePlan.PREMIUM_HISTORY);
        LocalDate today = UsualPrice.today(clock);
        List<StoreResponse> selected = comparisonStores(cityId, storeIds);
        List<UUID> productIds = items.findProductIds(itemId);
        if (selected.isEmpty() || productIds.isEmpty()) {
            return new CatalogItemHistoryResponse(itemId, since, List.of(), PriceVerdict.of(List.of(), null, today));
        }

        Map<UUID, List<CatalogItemHistoryResponse.PricePoint>> pointsByStore = new LinkedHashMap<>();
        jdbc.query("""
                SELECT store_id, price_day, MIN(lowest_price) AS lowest_price
                FROM daily_lowest_prices
                WHERE product_id IN (:productIds) AND store_id IN (:storeIds) AND price_day >= :since
                GROUP BY store_id, price_day
                ORDER BY store_id, price_day
                """, new MapSqlParameterSource()
                .addValue("productIds", productIds)
                .addValue("storeIds", selected.stream().map(StoreResponse::id).toList())
                .addValue("since", LocalDate.ofInstant(since, UsualPrice.ZONE)), row -> {
            pointsByStore.computeIfAbsent(row.getObject("store_id", UUID.class), ignored -> new ArrayList<>())
                    .add(new CatalogItemHistoryResponse.PricePoint(
                            row.getObject("price_day", LocalDate.class), row.getBigDecimal("lowest_price")));
        });

        List<CatalogItemHistoryResponse.StoreSeries> series = selected.stream()
                .filter(store -> pointsByStore.containsKey(store.id()))
                .sorted(Comparator.comparing(StoreResponse::name, String.CASE_INSENSITIVE_ORDER))
                .map(store -> new CatalogItemHistoryResponse.StoreSeries(store.id(), store.name(),
                        pointsByStore.get(store.id())))
                .toList();
        BigDecimal currentPrice = rankOffers(itemId, selected).stream().findFirst()
                .map(offer -> offer.price().unitPrice()).orElse(null);
        return new CatalogItemHistoryResponse(itemId, since, series, PriceVerdict.of(series, currentPrice, today));
    }

    // Only real, current prices are compared; a store without one is counted, never listed as a fake row.
    private List<CatalogItemDetailResponse.StoreOffer> rankOffers(UUID itemId, List<StoreResponse> stores) {
        Instant now = clock.instant();
        List<UUID> productIds = items.findProductIds(itemId);
        Map<UUID, Product> byId = products.findAllById(productIds).stream()
                .collect(Collectors.toMap(Product::getId, Function.identity()));
        Map<UUID, PriceRecord> best = bestByStore(productIds, stores, now);
        return stores.stream()
                .filter(store -> policy.quote(best.get(store.id()), now).unitPrice() != null).map(store -> {
            PriceRecord record = best.get(store.id());
            PriceQuote quote = policy.quote(record, now);
            Product product = record == null ? null : byId.get(record.getProductId());
            String storeName = record == null ? null
                    : record.getSourceProductName() != null ? record.getSourceProductName()
                    : product == null ? null : product.getName();
            return new CatalogItemDetailResponse.StoreOffer(store.id(), store.name(),
                    record == null ? null : record.getProductId(), storeName,
                    record == null ? null : record.getOriginUrl(), quote);
        }).sorted(Comparator.comparing((CatalogItemDetailResponse.StoreOffer offer) -> offer.price().unitPrice(),
                Comparator.nullsLast(Comparator.naturalOrder())).thenComparing(CatalogItemDetailResponse.StoreOffer::storeName))
                .toList();
    }

    public CatalogItem require(UUID itemId) {
        return items.findById(itemId).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Produto não encontrado."));
    }

    private List<CatalogItemResponse> respond(List<CatalogItem> page, List<StoreResponse> selected) {
        if (page.isEmpty()) return List.of();
        Instant now = clock.instant();
        Map<UUID, List<UUID>> productsByItem = new HashMap<>();
        for (Object[] link : items.findLinks(page.stream().map(CatalogItem::getId).toList())) {
            productsByItem.computeIfAbsent(UUID.fromString((String) link[0]), ignored -> new ArrayList<>())
                    .add(UUID.fromString((String) link[1]));
        }
        List<UUID> allProducts = productsByItem.values().stream().flatMap(List::stream).toList();
        Map<UUID, String> storeNames = selected.stream().collect(Collectors.toMap(StoreResponse::id, StoreResponse::name));
        Map<UUID, List<PriceRecord>> recordsByProduct = selected.isEmpty() || allProducts.isEmpty() ? Map.of()
                : prices.findLatestForStoresAndProducts(storeNames.keySet(), allProducts).stream()
                        .collect(Collectors.groupingBy(PriceRecord::getProductId));
        return page.stream().map(item -> {
            Map<UUID, Quote> byStore = new LinkedHashMap<>();
            Instant collectedAt = null;
            for (UUID productId : productsByItem.getOrDefault(item.getId(), List.of())) {
                for (PriceRecord record : recordsByProduct.getOrDefault(productId, List.of())) {
                    BigDecimal price = policy.quote(record, now).unitPrice();
                    if (price == null) continue;
                    byStore.merge(record.getStoreId(), new Quote(price, record),
                            (current, next) -> next.price().compareTo(current.price()) < 0 ? next : current);
                    if (collectedAt == null || record.getCollectedAt().isAfter(collectedAt)) collectedAt = record.getCollectedAt();
                }
            }
            Quote lowest = byStore.values().stream().min(Comparator.comparing(Quote::price)).orElse(null);
            Quote highest = byStore.values().stream().max(Comparator.comparing(Quote::price)).orElse(null);
            return new CatalogItemResponse(item.getId(), item.getDisplayName(), item.getBrand(), item.getSizeLabel(),
                    item.getCategory(), item.getImageUrl(), item.getRepresentativeProductId(), item.getStoreCount(),
                    byStore.size(), lowest == null ? null : lowest.price(), highest == null ? null : highest.price(),
                    lowest == null ? null : storeNames.get(lowest.record().getStoreId()), collectedAt);
        }).toList();
    }

    /** Most recent observation per store wins (a stock-out supersedes an older price); ties go to the cheaper SKU. */
    private Map<UUID, PriceRecord> bestByStore(List<UUID> productIds, List<StoreResponse> selected, Instant now) {
        if (productIds.isEmpty() || selected.isEmpty()) return Map.of();
        Map<UUID, PriceRecord> best = new HashMap<>();
        for (PriceRecord record : prices.findLatestForStoresAndProducts(
                selected.stream().map(StoreResponse::id).toList(), productIds)) {
            best.merge(record.getStoreId(), record, (current, next) -> {
                BigDecimal currentPrice = policy.quote(current, now).unitPrice();
                BigDecimal nextPrice = policy.quote(next, now).unitPrice();
                if (currentPrice == null) return nextPrice != null || next.getCollectedAt().isAfter(current.getCollectedAt()) ? next : current;
                if (nextPrice == null) return current;
                return nextPrice.compareTo(currentPrice) < 0 ? next : current;
            });
        }
        return best;
    }

    private List<StoreResponse> comparisonStores(UUID cityId, List<UUID> storeIds) {
        if (cityId == null) return List.of();
        return stores.selectComparisonStores(cityId, storeIds, MAXIMUM_STORES);
    }

    private record Quote(BigDecimal price, PriceRecord record) {
    }
}
