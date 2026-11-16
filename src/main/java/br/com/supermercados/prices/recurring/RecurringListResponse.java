package br.com.supermercados.prices.recurring;

import br.com.supermercados.prices.shoppinglist.ShoppingType;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

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
        boolean listChanged) {}
