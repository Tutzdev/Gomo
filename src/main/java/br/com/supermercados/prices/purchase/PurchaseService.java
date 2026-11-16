package br.com.supermercados.prices.purchase;

import br.com.supermercados.prices.common.ApiException;
import br.com.supermercados.prices.comparison.EquivalentPriceService;
import br.com.supermercados.prices.price.PricePolicy;
import br.com.supermercados.prices.price.PriceRecord;
import br.com.supermercados.prices.price.UsualPrice;
import br.com.supermercados.prices.purchase.RecordPurchaseRequest.BoughtItem;
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

    public PurchaseService(
        PurchaseRepository purchases,
        PurchaseItemRepository purchaseItems,
        ShoppingListService shoppingLists,
        StoreService stores,
        EquivalentPriceService prices,
        PricePolicy policy,
        Clock clock) {

        this.purchases = purchases;
        this.purchaseItems = purchaseItems;
        this.shoppingLists = shoppingLists;
        this.stores = stores;
        this.prices = prices;
        this.policy = policy;
        this.clock = clock;
    }

    public record RecordedPurchase(PurchaseResponse purchase, boolean listRestarted) {}

    @Transactional
    public RecordedPurchase record(UUID userId, UUID listId, RecordPurchaseRequest request) {
        var previous = purchases.findById(request.id());

        if (previous.isPresent()) {return new RecordedPurchase(responseOf(userId, previous.get()), false);}

        var list = shoppingLists.getList(userId, listId);

        Map<UUID, ShoppingListItemResponse> listItems = list.items().stream()
                .collect(Collectors.toMap(ShoppingListItemResponse::id, Function.identity()));

        requireItemsFromList(request, listItems);

        Store store = stores.requireActiveStore(request.storeId());
        Instant now = clock.instant();
        PurchasePrices pricing = pricesFor(request, listItems, store, now);

        List<PurchaseItem> items = new ArrayList<>();
        int withoutPrice = 0;

        for (BoughtItem bought : request.items()) {
            ShoppingListItemResponse listItem = listItems.get(bought.itemId());
            BigDecimal paid = bought.shelfPrice() != null ? bought.shelfPrice() : pricing.atStore(listItem.productId());

            if (paid == null) {
                withoutPrice++;
                continue;
            }

            List<BigDecimal> cityPrices = pricing.inCity(listItem.productId());

            items.add(new PurchaseItem(
                request.id(),
                listItem.productId(),
                listItem.productName(),
                listItem.quantity(),
                paid.setScale(2, RoundingMode.HALF_UP),
                priceSource(bought),
                cityAverage(cityPrices),
                cityPrices.size()));
        }

        BigDecimal totalPaid = sum(items.stream().map(PurchaseItem::linePaid).toList());
        List<PurchaseItem> compared = items.stream().filter(item -> item.getCityAveragePrice() != null).toList();

        int inserted = purchases.insertIfAbsent(
            request.id(),
            userId,
            list.id(),
            list.name(),
            store.getId(),
            store.getCityId(),
            now,
            totalPaid,
            compared.size(),
            sum(compared.stream().map(PurchaseItem::linePaid).toList()),
            sum(compared.stream().map(PurchaseItem::lineCityAverage).toList()),
            withoutPrice);

        if (inserted == 0) {
            return new RecordedPurchase(responseOf(userId, purchases.findById(request.id()).orElseThrow()), false);
        }

        purchaseItems.saveAll(items);
        boolean restarted = shoppingLists.clearChecksIfEverythingWasBought(userId, list.id());
        purchaseItems.flush();

        return new RecordedPurchase(responseOf(userId, purchases.findById(request.id()).orElseThrow()), restarted);
    }

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

        return new SavingsReportResponse(
            yearMonth.toString(),
            found.size(),
            paid,
            savings,
            compared,
            !premium,
            details);
    }

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
        if (found.isEmpty()) {return List.of();}

        Map<UUID, List<PurchaseItem>> itemsByPurchase = purchaseItems
                .findByPurchaseIdInOrderByProductName(found.stream().map(Purchase::getId).toList()).stream()
                .collect(Collectors.groupingBy(PurchaseItem::getPurchaseId));

        Map<UUID, String> storeNames = found.stream().map(Purchase::getStoreId).distinct()
                .collect(Collectors.toMap(Function.identity(), storeId -> stores.requireStore(storeId).getName()));

        return found.stream().map(purchase -> PurchaseResponse.from(purchase, storeNames.get(purchase.getStoreId()),
                itemsByPurchase.getOrDefault(purchase.getId(), List.of()))).toList();
    }

    private static void requireItemsFromList(
        RecordPurchaseRequest request,
        Map<UUID, ShoppingListItemResponse> listItems) {

        Set<UUID> seen = new HashSet<>();

        for (BoughtItem bought : request.items()) {
            if (!listItems.containsKey(bought.itemId()) || !seen.add(bought.itemId())) {
                throw new ApiException(
                    HttpStatus.UNPROCESSABLE_CONTENT,
                    "Algum item da compra não está na lista ou veio repetido.");
            }
        }
    }

    private PurchasePrices pricesFor(
        RecordPurchaseRequest request,
        Map<UUID, ShoppingListItemResponse> listItems,
        Store store,
        Instant now) {

        List<UUID> cityStores = stores.selectComparisonStores(store.getCityId(), null, MAXIMUM_STORES).stream()
                .map(StoreResponse::id)
                .toList();

        List<UUID> searchedStores = cityStores.contains(store.getId()) ? cityStores : concat(cityStores, store.getId());

        List<UUID> productIds = request.items().stream()
                .map(bought -> listItems.get(bought.itemId()).productId())
                .toList();

        var offers = prices.find(productIds, searchedStores, now);

        return new PurchasePrices(store.getId(), cityStores, offers.byStore(), now);
    }

    private static PurchaseItem.PriceSource priceSource(BoughtItem bought) {
        return bought.shelfPrice() != null ? PurchaseItem.PriceSource.SHELF : PurchaseItem.PriceSource.COLLECTED;
    }

    private static BigDecimal cityAverage(List<BigDecimal> cityPrices) {
        if (cityPrices.size() < MINIMUM_STORES_FOR_AN_AVERAGE) {return null;}

        BigDecimal total = cityPrices.stream().reduce(BigDecimal.ZERO, BigDecimal::add);

        return total.divide(BigDecimal.valueOf(cityPrices.size()), 2, RoundingMode.HALF_UP);
    }

    private static BigDecimal sum(List<BigDecimal> values) {
        return values.stream().reduce(BigDecimal.ZERO.setScale(2), BigDecimal::add);
    }

    private static List<UUID> concat(List<UUID> stores, UUID store) {
        List<UUID> all = new ArrayList<>(stores);
        all.add(store);

        return all;
    }

    private YearMonth parse(String month) {
        try {
            return month == null || month.isBlank() ? YearMonth.from(UsualPrice.today(clock)) : YearMonth.parse(month);
        } catch (DateTimeParseException exception) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Informe o mês como AAAA-MM.");
        }
    }

    private final class PurchasePrices {

        private final UUID storeId;
        private final List<UUID> cityStores;
        private final Map<UUID, Map<UUID, PriceRecord>> byStore;
        private final Instant now;

        private PurchasePrices(
            UUID storeId,
            List<UUID> cityStores,
            Map<UUID, Map<UUID, PriceRecord>> byStore,
            Instant now) {

            this.storeId = storeId;
            this.cityStores = cityStores;
            this.byStore = byStore;
            this.now = now;
        }

        private BigDecimal atStore(UUID productId) {
            return price(storeId, productId);
        }

        private List<BigDecimal> inCity(UUID productId) {
            return cityStores.stream()
                    .map(cityStore -> price(cityStore, productId))
                    .filter(Objects::nonNull)
                    .toList();
        }

        private BigDecimal price(UUID store, UUID productId) {
            PriceRecord record = byStore.getOrDefault(store, Map.of()).get(productId);

            return policy.quote(record, now).unitPrice();
        }
    }
}
