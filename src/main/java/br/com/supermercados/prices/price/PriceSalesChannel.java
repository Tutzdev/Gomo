package br.com.supermercados.prices.price;

public enum PriceSalesChannel {
    ONLINE,
    PHYSICAL_FLYER,
    UNSPECIFIED;

    static PriceSalesChannel fromSourceReference(String reference) {
        if (reference == null) return UNSPECIFIED;
        if (reference.startsWith("atacadao:flyer-product:")) return PHYSICAL_FLYER;
        if (reference.startsWith("atacadao:online:product:") || reference.startsWith("nagumo:product:")
                || reference.startsWith("royal:product:") || reference.startsWith("vip:")
                || reference.startsWith("mercafacil:") || reference.startsWith("hortifruti:product:")) return ONLINE;
        return UNSPECIFIED;
    }
}
