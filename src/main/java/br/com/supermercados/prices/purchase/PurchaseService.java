package br.com.supermercados.prices.purchase;

import br.com.supermercados.prices.common.ApiException;
import br.com.supermercados.prices.comparison.EquivalentPriceService;
import br.com.supermercados.prices.price.PricePolicy;
import br.com.supermercados.prices.price.PriceRecord;
import br.com.supermercados.prices.price.UsualPrice;
import br.com.supermercados.prices.shoppinglist.ShoppingListItemResponse;
import br.com.supermercados.prices.shoppinglist.ShoppingListService;
import br.com.supermercados.prices.store.Store;
import br.com.supermercados.prices.store.StoreResponse;
import br.com.supermercados.prices.store.StoreService;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Instant;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Records "Terminei a compra" and reads it back as savings. A purchase keeps the prices of its moment: what the
 * person paid (the store's current price, or the one typed from the shelf) and the average current price among
 * the city's stores. Only items with a price in at least two stores are compared; one store alone is no average.
 */
@Service
public class PurchaseService {

    private static final int MAXIMUM_STORES = 500;
    private static final int MINIMUM_STORES_FOR_AN_AVERAGE = 2;

    private final PurchaseRepository purchases;
    private final PurchaseItemRepository purchaseItems;
    private final ShoppingListService shoppingLists;
    private final StoreService stores;
    private final EquivalentPriceService prices;
    private final PricePolicy policy;
    private final Clock clock;

    public PurchaseService(PurchaseRepository purchases, PurchaseItemRepository purchaseItems,
            ShoppingListService shoppingLists, StoreService stores, EquivalentPriceService prices, PricePolicy policy,
            Clock clock) {
        this.purchases = purchases;
        this.purchaseItems = purchaseItems;
        this.shoppingLists = shoppingLists;
        this.stores = stores;
        this.prices = prices;
        this.policy = policy;
        this.clock = clock;
    }

    /** @param listRestarted every item of the list was bought, so its marks were cleared for the next trip */
    public record RecordedPurchase(PurchaseResponse purchase, boolean listRestarted) {
    }

    @Transactional
    public RecordedPurchase record(UUID userId, UUID listId, RecordPurchaseRequest request) {
        var previous = purchases.findById(request.id());
        if (previous.isPresent()) {
            return new RecordedPurchase(responseOf(userId, previous.get()), false);
        }
        var list = shoppingLists.getOwnedList(userId, listId);
        Map<UUID, ShoppingListItemResponse> listItems = list.items().stream()
                .collect(Collectors.toMap(ShoppingListItemResponse::id, Function.identity()));
        Set<UUID> seen = new HashSet<>();
        for (var bought : request.items()) {
            if (!listItems.containsKey(bought.itemId()) || !seen.add(bought.itemId())) {
                throw new ApiException(HttpStatus.UNPROCESSABLE_CONTENT,
                        "Algum item da compra não está na lista ou veio repetido.");
            }
        }
        Store store = stores.requireActiveStore(request.storeId());
        List<UUID> cityStores = stores.selectComparisonStores(store.getCityId(), null, MAXIMUM_STORES).stream()
                .map(StoreResponse::id).toList();
        Instant now = clock.instant();
        List<UUID> productIds = request.items().stream().map(bought -> listItems.get(bought.itemId()).productId())
                .toList();
        var offers = prices.find(productIds, cityStores.contains(store.getId()) ? cityStores
                : concat(cityStores, store.getId()), now);

        List<PurchaseItem> items = new ArrayList<>();
        int withoutPrice = 0;
        for (var bought : request.items()) {
            var listItem = listItems.get(bought.itemId());
            BigDecimal collected = price(offers.byStore(), store.getId(), listItem.productId(), now);
            BigDecimal paid = bought.shelfPrice() != null ? bought.shelfPrice() : collected;
            if (paid == null) {
                withoutPrice++;
                continue;
            }
            List<BigDecimal> cityPrices = cityStores.stream()
                    .map(storeId -> price(offers.byStore(), storeId, listItem.productId(), now))
                    .filter(Objects::nonNull).toList();
            BigDecimal average = cityPrices.size() < MINIMUM_STORES_FOR_AN_AVERAGE ? null
                    : cityPrices.stream().reduce(BigDecimal.ZERO, BigDecimal::add)
                            .divide(BigDecimal.valueOf(cityPrices.size()), 2, RoundingMode.HALF_UP);
            items.add(new PurchaseItem(request.id(), listItem.productId(), listItem.productName(),
                    listItem.quantity(), paid.setScale(2, RoundingMode.HALF_UP),
                    bought.shelfPrice() != null ? PurchaseItem.PriceSource.SHELF : PurchaseItem.PriceSource.COLLECTED,
                    average, cityPrices.size()));
        }

        BigDecimal totalPaid = sum(items.stream().map(PurchaseItem::linePaid).toList());
        List<PurchaseItem> compared = items.stream().filter(item -> item.getCityAveragePrice() != null).toList();
        int inserted = purchases.insertIfAbsent(request.id(), userId, list.id(), list.name(), store.getId(),
                store.getCityId(), now, totalPaid, compared.size(),
                sum(compared.stream().map(PurchaseItem::linePaid).toList()),
                sum(compared.stream().map(PurchaseItem::lineCityAverage).toList()), withoutPrice);
        if (inserted == 0) {
            // The same purchase arrived twice at the same moment; the other request wrote it.
            return new RecordedPurchase(responseOf(userId, purchases.findById(request.id()).orElseThrow()), false);
        }
        purchaseItems.saveAll(items);
        boolean restarted = shoppingLists.clearChecksIfEverythingWasBought(userId, list.id());
        purchaseItems.flush();
        return new RecordedPurchase(responseOf(userId, purchases.findById(request.id()).orElseThrow()), restarted);
    }

    /**
     * The month in the shoppers' calendar. The free plan sees the total; the purchase by purchase breakdown is
     * Premium.
     */
    @Transactional(readOnly = true)
    public SavingsReportResponse month(UUID userId, boolean premium, String month) {
        YearMonth yearMonth = parse(month);
        Instant since = yearMonth.atDay(1).atStartOfDay(UsualPrice.ZONE).toInstant();
        Instant until = yearMonth.plusMonths(1).atDay(1).atStartOfDay(UsualPrice.ZONE).toInstant();
        List<Purchase> found = purchases.findBetween(userId, since, until);
        BigDecimal paid = sum(found.stream().map(Purchase::getTotalPaid).toList());
        BigDecimal savings = sum(found.stream().map(Purchase::savings).toList());
        int compared = found.stream().mapToInt(Purchase::getComparedItems).sum();
        List<PurchaseResponse> details = premium ? responses(found) : List.of();
        return new SavingsReportResponse(yearMonth.toString(), found.size(), paid, savings, compared, !premium,
                details);
    }

    /** A purchase recorded by mistake leaves the report. */
    @Transactional
    public void delete(UUID userId, UUID purchaseId) {
        purchases.delete(purchases.findByIdAndUserId(purchaseId, userId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Compra não encontrada.")));
    }

    private PurchaseResponse responseOf(UUID userId, Purchase purchase) {
        if (!purchase.getUserId().equals(userId)) {
            throw new ApiException(HttpStatus.CONFLICT, "Este identificador de compra já foi usado.");
        }
        return responses(List.of(purchase)).getFirst();
    }

    private List<PurchaseResponse> responses(List<Purchase> found) {
        if (found.isEmpty()) {
            return List.of();
        }
        Map<UUID, List<PurchaseItem>> itemsByPurchase = purchaseItems
                .findByPurchaseIdInOrderByProductName(found.stream().map(Purchase::getId).toList()).stream()
                .collect(Collectors.groupingBy(PurchaseItem::getPurchaseId));
        Map<UUID, String> storeNames = found.stream().map(Purchase::getStoreId).distinct()
                .collect(Collectors.toMap(Function.identity(), storeId -> stores.requireStore(storeId).getName()));
        return found.stream().map(purchase -> PurchaseResponse.from(purchase, storeNames.get(purchase.getStoreId()),
                itemsByPurchase.getOrDefault(purchase.getId(), List.of()))).toList();
    }

    private BigDecimal price(Map<UUID, Map<UUID, PriceRecord>> byStore, UUID storeId, UUID productId, Instant now) {
        return policy.quote(byStore.getOrDefault(storeId, Map.of()).get(productId), now).unitPrice();
    }

    private static BigDecimal sum(List<BigDecimal> values) {
        return values.stream().reduce(BigDecimal.ZERO.setScale(2), BigDecimal::add);
    }

    private static List<UUID> concat(List<UUID> stores, UUID store) {
        List<UUID> all = new ArrayList<>(stores);
        all.add(store);
        return all;
    }

    /** No month means this month. */
    private YearMonth parse(String month) {
        try {
            return month == null || month.isBlank() ? YearMonth.from(UsualPrice.today(clock)) : YearMonth.parse(month);
        } catch (DateTimeParseException exception) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Informe o mês como AAAA-MM.");
        }
    }
}
