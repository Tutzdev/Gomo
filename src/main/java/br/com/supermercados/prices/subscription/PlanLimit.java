package br.com.supermercados.prices.subscription;

/** Free-plan limits the API can report; the frontend uses the value to show the matching upgrade message. */
public enum PlanLimit {
    SHOPPING_LISTS,
    LIST_ITEMS,
    ACTIVE_ALERTS,
    PRICE_HISTORY
}
