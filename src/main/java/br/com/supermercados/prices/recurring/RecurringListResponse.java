package br.com.supermercados.prices.recurring;

import br.com.supermercados.prices.shoppinglist.ShoppingType;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * "Sua compra do mês hoje sai R$ 412 no Bramil (R$ 18 a menos que semana passada)": a weekly or monthly list at
 * its best single market today, next to about a week ago.
 *
 * @param complete the market has every item; otherwise {@code total} is what it has
 * @param previousDay the day compared with, 7 to 13 days ago; {@code null} without one
 * @param difference {@code total − previousTotal}: negative is cheaper today. {@code null} when there is no week to
 *     compare with or the list changed since ({@code listChanged})
 */
public record RecurringListResponse(
        UUID listId,
        String name,
        ShoppingType shoppingType,
        UUID cityId,
        UUID storeId,
        String storeName,
        BigDecimal total,
        boolean complete,
        int pricedItems,
        int requestedItems,
        LocalDate previousDay,
        BigDecimal previousTotal,
        String previousStoreName,
        BigDecimal difference,
        boolean listChanged) {
}
