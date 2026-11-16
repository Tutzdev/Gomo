package br.com.supermercados.prices.catalog;

import java.util.Set;

public final class StockUpAisles {

    private static final Set<String> KEEPS = Set.of(
        CatalogCategory.GROCERY,
        CatalogCategory.BREAKFAST,
        CatalogCategory.SWEETS,
        CatalogCategory.DRINKS,
        CatalogCategory.ALCOHOL,
        CatalogCategory.CLEANING,
        CatalogCategory.PERSONAL_CARE,
        CatalogCategory.BABY,
        CatalogCategory.PET,
        CatalogCategory.HOUSEHOLD);

    private StockUpAisles() {}

    public static boolean keeps(String category) {
        return category != null && KEEPS.contains(category);
    }
}
