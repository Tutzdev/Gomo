package br.com.supermercados.prices.collection;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class CollectedOfferPolicy {

    private static final Pattern NINES = Pattern.compile("9{4,}(?:\\.(?:9+|0+))?");
    private static final BigDecimal THOUSAND = BigDecimal.valueOf(1000);
    private static final BigDecimal FIVE_HUNDRED = BigDecimal.valueOf(500);
    private static final BigDecimal THREE_HUNDRED = BigDecimal.valueOf(300);
    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    private final Duration undatedPromotionWindow;

    public CollectedOfferPolicy(
        @Value("${app.collection.undated-promotion-window:PT12H}") Duration undatedPromotionWindow) {

        if (undatedPromotionWindow.isNegative() || undatedPromotionWindow.isZero()) {
            throw new IllegalArgumentException("app.collection.undated-promotion-window deve ser positivo");
        }

        this.undatedPromotionWindow = undatedPromotionWindow;
    }

    public Instant promotionValidUntil(CollectedProduct product, Instant collectedAt) {
        if (product.promotionalPrice() == null || product.promotionValidUntil() != null
                || product.promotionCondition() != null) {
            return product.promotionValidUntil();
        }

        Instant assumed = collectedAt.plus(undatedPromotionWindow);

        return product.validUntil() != null && product.validUntil().isBefore(assumed) ? product.validUntil() : assumed;
    }

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

        if (NINES.matcher(price.toPlainString()).matches() || NINES.matcher(plain).matches()) {return true;}

        return price.compareTo(THOUSAND) >= 0 && price.remainder(FIVE_HUNDRED).signum() == 0;
    }

    static boolean isRoundHundreds(BigDecimal price) {
        return price.compareTo(THREE_HUNDRED) >= 0 && price.remainder(HUNDRED).signum() == 0;
    }
}
