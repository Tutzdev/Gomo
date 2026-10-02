package br.com.supermercados.prices.catalog;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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
import br.com.supermercados.prices.product.Product;
import br.com.supermercados.prices.product.ProductRepository;
import br.com.supermercados.prices.store.StoreResponse;
import br.com.supermercados.prices.store.StoreService;

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

    public CatalogService(CatalogItemRepository items, NamedParameterJdbcTemplate jdbc, PriceRecordRepository prices,
            ProductRepository products, StoreService stores, PricePolicy policy, Clock clock,
            @org.springframework.beans.factory.annotation.Value("${app.prices.max-age:P2D}") java.time.Duration maxPriceAge) {
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
            // Per item, the stores whose newest observation of a linked SKU is a usable price right now
            // (same freshness, validity and stock rules as PricePolicy). Computed as one set, not per item.
            priced = """
                    SELECT link.catalog_item_id, count(DISTINCT fresh.store_id) AS priced_stores
                    FROM (SELECT DISTINCT ON (r.store_id, r.product_id) r.store_id, r.product_id,
                                r.availability, r.valid_until
                            FROM price_records r
                            WHERE r.store_id IN (:stores) AND r.collected_at > :freshAfter AND r.collected_at <= :now
                            ORDER BY r.store_id, r.product_id, r.collected_at DESC, r.recorded_at DESC, r.id DESC) fresh
                    JOIN catalog_item_products link ON link.product_id = fresh.product_id
                    WHERE fresh.availability <> 'UNAVAILABLE' AND (fresh.valid_until IS NULL OR fresh.valid_until > :now)
                    GROUP BY link.catalog_item_id""";
            parameters.addValue("stores", selected.stream().map(StoreResponse::id).toList())
                    .addValue("now", java.sql.Timestamp.from(now))
                    .addValue("freshAfter", java.sql.Timestamp.from(now.minus(maxPriceAge)));
        }
        String matched = matchedSql(priced, exact, cityId != null);
        Long exactMatches = jdbc.queryForObject(matched.replace("{columns}", "count(*)"), parameters, Long.class);
        if (exactMatches == null || exactMatches == 0) matched = matchedSql(priced, approximate, cityId != null);
        // "coca 1 litro" means the 1 L bottle: other sizes appear only when that size is not sold anywhere.
        if (terms.size() != null) {
            parameters.addValue("size", "%|1" + terms.size());
            Long sized = jdbc.queryForObject(matched.replace("{columns}", "count(*)") + " AND catalog_key LIKE :size",
                    parameters, Long.class);
            if (sized != null && sized > 0) matched += " AND catalog_key LIKE :size";
        }
        Long total = jdbc.queryForObject(matched.replace("{columns}", "count(*)"), parameters, Long.class);
        parameters.addValue("limit", pageable.getPageSize()).addValue("offset", pageable.getOffset());
        // Searching: the plainest variant first ("Coca-Cola" before "Coca-Cola Zero"). Browsing: the products
        // with current prices in the most stores, i.e. the most useful comparisons.
        String order = terms.tokens().isEmpty() ? "" : "array_length(string_to_array(split_part(catalog_key, '|', 1), ' '), 1), ";
        List<UUID> ids = jdbc.queryForList(matched.replace("{columns}", "id") + " ORDER BY " + order
                + "priced_stores DESC, store_count DESC, length(display_name), id LIMIT :limit OFFSET :offset",
                parameters, UUID.class);
        Map<UUID, CatalogItem> found = items.findAllById(ids).stream()
                .collect(Collectors.toMap(CatalogItem::getId, Function.identity()));
        List<CatalogItem> ordered = ids.stream().map(found::get).toList();
        return new PageImpl<>(respond(ordered, selected), pageable, total == null ? 0 : total);
    }

    private static String matchedSql(String priced, List<String> conditions, boolean requirePrice) {
        return "WITH priced AS (" + priced + "), matched AS (SELECT item.id, item.catalog_key,"
                + " item.store_count, item.display_name, coalesce(priced.priced_stores, 0) AS priced_stores"
                + " FROM catalog_items item LEFT JOIN priced ON priced.catalog_item_id = item.id WHERE "
                + String.join(" AND ", conditions) + ") SELECT {columns} FROM matched WHERE "
                // Without a current price in the city there is nothing real to compare.
                + (requirePrice ? "priced_stores > 0" : "TRUE");
    }

    public CatalogItemDetailResponse find(UUID itemId, UUID cityId, List<UUID> storeIds) {
        CatalogItem item = require(itemId);
        List<StoreResponse> selected = comparisonStores(cityId, storeIds);
        Instant now = clock.instant();
        List<UUID> productIds = items.findProductIds(itemId);
        Map<UUID, Product> byId = products.findAllById(productIds).stream()
                .collect(Collectors.toMap(Product::getId, Function.identity()));
        Map<UUID, PriceRecord> best = bestByStore(productIds, selected, now);
        // Only real, current prices are compared; a store without one is counted, never listed as a fake row.
        List<CatalogItemDetailResponse.StoreOffer> offers = selected.stream()
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
        return new CatalogItemDetailResponse(respond(List.of(item), selected).getFirst(), offers,
                selected.size() - offers.size());
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
