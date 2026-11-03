package br.com.supermercados.prices.price;

public enum PriceSalesChannel {
    ONLINE,
    PHYSICAL_FLYER,
    /** Written down at the shelf of the physical store. */
    IN_STORE,
    UNSPECIFIED;

    static PriceSalesChannel of(PriceRecord record) {
        if (record.getOriginType() == PriceOriginType.STORE_VISIT) return IN_STORE;
        return fromSourceReference(record.getSourceProductReference());
    }

    static PriceSalesChannel fromSourceReference(String reference) {
        if (reference == null) return UNSPECIFIED;
        if (reference.startsWith("atacadao:flyer-product:")) return PHYSICAL_FLYER;
        if (reference.startsWith("atacadao:online:product:") || reference.startsWith("nagumo:product:")
                || reference.startsWith("royal:product:") || reference.startsWith("vip:")
                || reference.startsWith("mercafacil:") || reference.startsWith("hortifruti:product:")) return ONLINE;
        return UNSPECIFIED;
    }
}
