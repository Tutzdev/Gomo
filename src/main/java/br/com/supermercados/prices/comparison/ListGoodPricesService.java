package br.com.supermercados.prices.comparison;

import br.com.supermercados.prices.catalog.CatalogItem;
import br.com.supermercados.prices.catalog.CatalogItemHistoryResponse;
import br.com.supermercados.prices.catalog.CatalogItemRepository;
import br.com.supermercados.prices.catalog.PriceVerdict;
import br.com.supermercados.prices.catalog.StockUpAisles;
import br.com.supermercados.prices.location.LocationService;
import br.com.supermercados.prices.price.PricePolicy;
import br.com.supermercados.prices.price.PriceRecord;
import br.com.supermercados.prices.price.PriceRecordRepository;
import br.com.supermercados.prices.price.UsualPrice;
import br.com.supermercados.prices.shoppinglist.ShoppingListItemResponse;
import br.com.supermercados.prices.shoppinglist.ShoppingListService;
import br.com.supermercados.prices.store.StoreResponse;
import br.com.supermercados.prices.store.StoreService;
import br.com.supermercados.prices.subscription.FreePlan;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The price verdict of every item of a list at once, for "Hora de estocar" and "itens da sua lista abaixo do
 * preço de sempre". Each item is read like its price page: the cheapest current offer in the city against the
 * usual price and the lowest of the last 90 days, over every SKU of the generic item.
 */
@Service
@Transactional(readOnly = true)
public class ListGoodPricesService {

    private static final int MAXIMUM_STORES = 500;
    /** A pantry item is worth stocking up when it is the lowest of the period and at least this much under usual. */
    static final int STOCK_UP_MINIMUM_DISCOUNT = 10;

    private final ShoppingListService shoppingLists;
    private final LocationService locations;
    private final StoreService stores;
    private final CatalogItemRepository catalog;
    private final PriceRecordRepository prices;
    private final PricePolicy policy;
    private final NamedParameterJdbcTemplate jdbc;
    private final Clock clock;

    public ListGoodPricesService(ShoppingListService shoppingLists, LocationService locations, StoreService stores,
            CatalogItemRepository catalog, PriceRecordRepository prices, PricePolicy policy,
            NamedParameterJdbcTemplate jdbc, Clock clock) {
        this.shoppingLists = shoppingLists;
        this.locations = locations;
        this.stores = stores;
        this.catalog = catalog;
        this.prices = prices;
        this.policy = policy;
        this.jdbc = jdbc;
        this.clock = clock;
    }

    public ListGoodPricesResponse find(UUID userId, UUID listId, UUID cityId, boolean premium) {
        var list = shoppingLists.getOwnedList(userId, listId);
        locations.requireCity(cityId);
        List<StoreResponse> cityStores = stores.selectComparisonStores(cityId, null, MAXIMUM_STORES);
        if (list.items().isEmpty() || cityStores.isEmpty()) {
            return new ListGoodPricesResponse(listId, cityId, !premium, 0, 0, List.of());
        }
        Map<UUID, String> storeNames = cityStores.stream()
                .collect(Collectors.toMap(StoreResponse::id, StoreResponse::name));
        Map<UUID, List<UUID>> productsByItem = productsByItem(list.items());
        List<UUID> allProducts = productsByItem.values().stream().flatMap(List::stream).distinct().toList();
        Map<UUID, CatalogItem> catalogItems = catalog.findAllById(list.items().stream()
                        .map(ShoppingListItemResponse::catalogItemId).filter(Objects::nonNull).distinct().toList())
                .stream().collect(Collectors.toMap(CatalogItem::getId, Function.identity()));

        Instant now = clock.instant();
        LocalDate today = UsualPrice.today(clock);
        Map<UUID, Map<LocalDate, BigDecimal>> dailyByProduct = dailyLowest(allProducts, storeNames.keySet(),
                LocalDate.ofInstant(now.minus(FreePlan.PREMIUM_HISTORY), UsualPrice.ZONE), today);
        Map<UUID, List<PriceRecord>> latestByProduct = prices.findLatestForStoresAndProducts(storeNames.keySet(),
                allProducts).stream().collect(Collectors.groupingBy(PriceRecord::getProductId));

        List<ListGoodPricesResponse.GoodPrice> good = new ArrayList<>();
        for (ShoppingListItemResponse item : list.items()) {
            List<UUID> products = productsByItem.get(item.id());
            PriceRecord cheapest = products.stream()
                    .flatMap(product -> latestByProduct.getOrDefault(product, List.of()).stream())
                    .filter(record -> policy.quote(record, now).unitPrice() != null)
                    .min(Comparator.comparing(record -> policy.quote(record, now).unitPrice()))
                    .orElse(null);
            if (cheapest == null) {
                continue;
            }
            BigDecimal current = policy.quote(cheapest, now).unitPrice();
            TreeMap<LocalDate, BigDecimal> daily = new TreeMap<>();
            products.forEach(product -> dailyByProduct.getOrDefault(product, Map.of())
                    .forEach((day, price) -> daily.merge(day, price, BigDecimal::min)));
            var verdict = PriceVerdict.of(List.of(new CatalogItemHistoryResponse.StoreSeries(null, null,
                    daily.entrySet().stream().map(entry -> new CatalogItemHistoryResponse.PricePoint(entry.getKey(),
                            entry.getValue())).toList())), current, today);
            if (verdict.kind() != PriceVerdict.Kind.LOWEST_IN_PERIOD && verdict.kind() != PriceVerdict.Kind.BELOW_AVERAGE) {
                continue;
            }
            CatalogItem catalogItem = item.catalogItemId() == null ? null : catalogItems.get(item.catalogItemId());
            boolean stockUp = verdict.kind() == PriceVerdict.Kind.LOWEST_IN_PERIOD
                    && verdict.differencePercent() <= -STOCK_UP_MINIMUM_DISCOUNT
                    && StockUpAisles.keeps(catalogItem == null ? null : catalogItem.getCategory());
            good.add(new ListGoodPricesResponse.GoodPrice(item.id(), item.productId(), item.productName(),
                    item.catalogItemId(), item.quantity(), verdict.kind(), current, cheapest.getStoreId(),
                    storeNames.get(cheapest.getStoreId()), verdict.averagePrice(), verdict.differencePercent(),
                    verdict.lowestPrice(), verdict.periodDays(), stockUp));
        }
        good.sort(Comparator.comparing(ListGoodPricesResponse.GoodPrice::stockUp).reversed()
                .thenComparing(ListGoodPricesResponse.GoodPrice::differencePercent));
        int stockUp = (int) good.stream().filter(ListGoodPricesResponse.GoodPrice::stockUp).count();
        return new ListGoodPricesResponse(listId, cityId, !premium, stockUp, good.size() - stockUp,
                premium ? good : List.of());
    }

    /** A generic item is compared through every equivalent SKU; a specific one through itself. */
    private Map<UUID, List<UUID>> productsByItem(List<ShoppingListItemResponse> items) {
        Map<UUID, List<UUID>> byCatalogItem = new HashMap<>();
        List<UUID> catalogIds = items.stream().map(ShoppingListItemResponse::catalogItemId).filter(Objects::nonNull)
                .distinct().toList();
        if (!catalogIds.isEmpty()) {
            for (Object[] link : catalog.findLinks(catalogIds)) {
                byCatalogItem.computeIfAbsent(UUID.fromString((String) link[0]), ignored -> new ArrayList<>())
                        .add(UUID.fromString((String) link[1]));
            }
        }
        return items.stream().collect(Collectors.toMap(ShoppingListItemResponse::id, item ->
                item.catalogItemId() == null ? List.of(item.productId())
                        : byCatalogItem.getOrDefault(item.catalogItemId(), List.of(item.productId()))));
    }

    private Map<UUID, Map<LocalDate, BigDecimal>> dailyLowest(List<UUID> products, java.util.Set<UUID> storeIds,
            LocalDate since, LocalDate until) {
        Map<UUID, Map<LocalDate, BigDecimal>> byProduct = new HashMap<>();
        jdbc.query("""
                SELECT product_id, price_day, MIN(lowest_price) AS lowest_price
                FROM daily_lowest_prices
                WHERE product_id IN (:products) AND store_id IN (:stores) AND price_day >= :since AND price_day < :until
                GROUP BY product_id, price_day
                """, new MapSqlParameterSource().addValue("products", products).addValue("stores", storeIds)
                .addValue("since", since).addValue("until", until), row -> {
            byProduct.computeIfAbsent(row.getObject("product_id", UUID.class), ignored -> new HashMap<>())
                    .put(row.getObject("price_day", LocalDate.class), row.getBigDecimal("lowest_price"));
        });
        return byProduct;
    }
}
