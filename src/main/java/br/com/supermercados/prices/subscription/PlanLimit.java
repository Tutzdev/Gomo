package br.com.supermercados.prices.subscription;

/** Free-plan limits the API can report; the frontend uses the value to show the matching upgrade message. */
public enum PlanLimit {
    SHOPPING_LISTS,
    LIST_ITEMS,
    ACTIVE_ALERTS,
    PRICE_HISTORY,
    /** "Warn me when it is X% below the usual price" reads the price history, which is Premium. */
    BELOW_AVERAGE_ALERTS,
    /** The deals page is open to everyone; narrowing it to the person's own lists is Premium. */
    DEALS_FOR_MY_LISTS,
    /** Sharing a list with the household is Premium; joining a shared list is not. */
    SHARED_LISTS
}
