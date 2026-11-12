package br.com.supermercados.prices.storevisit;

/** Days the shelf prices of one market stay current; null goes back to the default. */
public record StoreVisitValidityRequest(Integer days) {
}
