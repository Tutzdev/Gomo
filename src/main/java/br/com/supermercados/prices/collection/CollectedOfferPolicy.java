package br.com.supermercados.prices.collection;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** Rules applied to every collected offer before it is stored, whatever market it came from. */
@Component
public class CollectedOfferPolicy {

    /** 9999, 99999, 9999.99: what store systems put in the price field of an item that is not for sale. */
    private static final Pattern NINES = Pattern.compile("9{4,}(?:\\.(?:9+|0+))?");
    private static final BigDecimal THOUSAND = BigDecimal.valueOf(1000);
    private static final BigDecimal FIVE_HUNDRED = BigDecimal.valueOf(500);
    private static final BigDecimal THREE_HUNDRED = BigDecimal.valueOf(300);
    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    private final Duration undatedPromotionWindow;

    public CollectedOfferPolicy(@Value("${app.collection.undated-promotion-window:PT12H}") Duration undatedPromotionWindow) {
        if (undatedPromotionWindow.isNegative() || undatedPromotionWindow.isZero()) {
            throw new IllegalArgumentException("app.collection.undated-promotion-window deve ser positivo");
        }
        this.undatedPromotionWindow = undatedPromotionWindow;
    }

    /**
     * Most stores (VipCommerce, Mercafácil) publish a promotional price without an end date. It is the price the
     * site charges when the collection runs, so it counts as current for a short window; collections run every
     * few hours and renew it while the promotion lasts, and a promotion that ends falls back to the regular price.
     * Club and quantity conditions are never applied to the common price.
     */
    public Instant promotionValidUntil(CollectedProduct product, Instant collectedAt) {
        if (product.promotionalPrice() == null || product.promotionValidUntil() != null
                || product.promotionCondition() != null) {
            return product.promotionValidUntil();
        }
        Instant assumed = collectedAt.plus(undatedPromotionWindow);
        return product.validUntil() != null && product.validUntil().isBefore(assumed) ? product.validUntil() : assumed;
    }

    /** Why the offer must not become a price, or empty when it can. */
    public Optional<String> rejection(CollectedProduct product) {
        if (isPlaceholder(product.regularPrice()) || (product.promotionalPrice() != null && isPlaceholder(product.promotionalPrice()))) {
            return Optional.of("Preço de marcação da loja (ex.: 9999 ou 5000), não é preço de venda.");
        }
        boolean perKilogram = product.name().toLowerCase(Locale.ROOT).contains("(preço de 1 kg)");
        if (perKilogram && isRoundHundreds(product.regularPrice())) {
            return Optional.of("Preço por kg de marcação da loja (ex.: R$ 500/kg), não é preço de venda.");
        }
        return Optional.empty();
    }

    private static boolean isPlaceholder(BigDecimal price) {
        String plain = price.stripTrailingZeros().toPlainString();
        if (NINES.matcher(price.toPlainString()).matches() || NINES.matcher(plain).matches()) return true;
        return price.compareTo(THOUSAND) >= 0 && price.remainder(FIVE_HUNDRED).signum() == 0;
    }

    /** R$ 300, 500, 1000… per kilogram: no fresh produce or meat costs a round number of hundreds. */
    static boolean isRoundHundreds(BigDecimal price) {
        return price.compareTo(THREE_HUNDRED) >= 0 && price.remainder(HUNDRED).signum() == 0;
    }
}
