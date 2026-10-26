package br.com.supermercados.prices.catalog;

import java.util.Set;

/**
 * The aisles worth stocking up from when the price is the lowest in months: what keeps for weeks in the pantry.
 * Produce, meat, dairy, bread and frozen food are left out: buying two of them is buying one to throw away (or a
 * freezer most people do not have room in).
 */
public final class StockUpAisles {

    private static final Set<String> KEEPS = Set.of(CatalogCategory.GROCERY, CatalogCategory.BREAKFAST,
            CatalogCategory.SWEETS, CatalogCategory.DRINKS, CatalogCategory.ALCOHOL, CatalogCategory.CLEANING,
            CatalogCategory.PERSONAL_CARE, CatalogCategory.BABY, CatalogCategory.PET, CatalogCategory.HOUSEHOLD);

    private StockUpAisles() {
    }

    public static boolean keeps(String category) {
        return category != null && KEEPS.contains(category);
    }
}
