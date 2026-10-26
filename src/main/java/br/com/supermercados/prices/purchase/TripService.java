package br.com.supermercados.prices.purchase;

import br.com.supermercados.prices.comparison.EquivalentPriceService;
import br.com.supermercados.prices.price.PricePolicy;
import br.com.supermercados.prices.price.PriceRecord;
import br.com.supermercados.prices.shoppinglist.ShoppingListItemResponse;
import br.com.supermercados.prices.shoppinglist.ShoppingListService;
import br.com.supermercados.prices.store.Store;
import br.com.supermercados.prices.store.StoreService;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The list priced at the one market the person is in. Any plan gets it: the person already picked the store, so
 * nothing about the ranking of the others is revealed.
 */
@Service
public class TripService {

    private final ShoppingListService shoppingLists;
    private final StoreService stores;
    private final EquivalentPriceService prices;
    private final PricePolicy policy;
    private final Clock clock;

    public TripService(ShoppingListService shoppingLists, StoreService stores, EquivalentPriceService prices,
            PricePolicy policy, Clock clock) {
        this.shoppingLists = shoppingLists;
        this.stores = stores;
        this.prices = prices;
        this.policy = policy;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public TripResponse trip(UUID userId, UUID listId, UUID storeId) {
        var list = shoppingLists.getList(userId, listId);
        Store store = stores.requireActiveStore(storeId);
        Instant now = clock.instant();
        var offers = prices.find(list.items().stream().map(ShoppingListItemResponse::productId).toList(),
                List.of(storeId), now);
        Map<UUID, PriceRecord> storePrices = offers.byStore().getOrDefault(storeId, Map.of());

        List<TripResponse.TripItem> items = list.items().stream().map(item -> {
            PriceRecord record = storePrices.get(item.productId());
            var quote = policy.quote(record, now);
            BigDecimal lineTotal = quote.unitPrice() == null ? null
                    : quote.unitPrice().multiply(item.quantity()).setScale(2, RoundingMode.HALF_UP);
            var matched = offers.matchedProduct(item.productId(), record);
            return new TripResponse.TripItem(item.id(), item.productId(), item.productName(), item.quantity(),
                    item.catalogItemId(), item.imageUrl(), matched == null ? item.productId() : matched.getId(),
                    quote.unitPrice(), lineTotal, quote.promotionApplied(), item.checkedAt(), item.checkedBy());
        }).toList();
        BigDecimal expected = items.stream().map(TripResponse.TripItem::lineTotal)
                .filter(java.util.Objects::nonNull).reduce(BigDecimal.ZERO.setScale(2), BigDecimal::add);
        int priced = (int) items.stream().filter(item -> item.unitPrice() != null).count();
        return new TripResponse(list.id(), list.name(), store.getId(), store.getName(), store.getCityId(), items,
                priced, expected);
    }
}
