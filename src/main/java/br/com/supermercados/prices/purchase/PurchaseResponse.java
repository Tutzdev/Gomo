package br.com.supermercados.prices.purchase;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

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

    public record Item(
            UUID productId,
            String productName,
            BigDecimal quantity,
            BigDecimal unitPrice,
            String priceSource,
            BigDecimal cityAveragePrice,
            int cityStores,
            BigDecimal linePaid,
            BigDecimal savings) {}

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
