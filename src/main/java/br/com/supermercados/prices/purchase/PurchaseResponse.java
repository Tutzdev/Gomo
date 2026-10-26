package br.com.supermercados.prices.purchase;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * A recorded purchase. {@code savings} is what the compared items (those with a price in at least two stores of
 * the city) cost less than the city average that day; it is negative when the person paid more.
 *
 * @param itemsWithoutPrice bought items with no price at the store and none typed from the shelf: not counted
 */
public record PurchaseResponse(
        UUID id,
        UUID listId,
        String listName,
        UUID storeId,
        String storeName,
        Instant purchasedAt,
        BigDecimal totalPaid,
        int comparedItems,
        BigDecimal comparedPaid,
        BigDecimal comparedCityAverage,
        BigDecimal savings,
        int itemsWithoutPrice,
        List<Item> items) {

    public PurchaseResponse {
        items = List.copyOf(items);
    }

    /** @param savings {@code null} for an item not compared with the city */
    public record Item(
            UUID productId,
            String productName,
            BigDecimal quantity,
            BigDecimal unitPrice,
            String priceSource,
            BigDecimal cityAveragePrice,
            int cityStores,
            BigDecimal linePaid,
            BigDecimal savings) {
    }

    static PurchaseResponse from(Purchase purchase, String storeName, List<PurchaseItem> items) {
        return new PurchaseResponse(purchase.getId(), purchase.getShoppingListId(), purchase.getListName(),
                purchase.getStoreId(), storeName, purchase.getPurchasedAt(), purchase.getTotalPaid(),
                purchase.getComparedItems(), purchase.getComparedPaid(), purchase.getComparedCityAverage(),
                purchase.savings(), purchase.getItemsWithoutPrice(), items.stream().map(item -> new Item(
                        item.getProductId(), item.getProductName(), item.getQuantity(), item.getUnitPrice(),
                        item.getPriceSource().name(), item.getCityAveragePrice(), item.getCityStores(),
                        item.linePaid(), item.lineCityAverage() == null ? null
                                : item.lineCityAverage().subtract(item.linePaid()))).toList());
    }
}
