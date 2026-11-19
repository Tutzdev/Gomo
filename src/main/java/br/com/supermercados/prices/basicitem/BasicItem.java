package br.com.supermercados.prices.basicitem;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.regex.Pattern;

public record BasicItem(
        String slug,
        String name,
        String aisle,
        Measure measure,
        BigDecimal typicalAmount,
        Pattern includes,
        Pattern excludes) {

    private static final BigDecimal SMALLEST_TYPICAL_SHARE = new BigDecimal("0.5");
    private static final BigDecimal LARGEST_TYPICAL_SHARE = new BigDecimal("1.5");

    public static BasicItem of(
        String slug,
        String name,
        String aisle,
        Measure measure,
        String typicalAmount,
        String includes,
        String excludes) {

        return new BasicItem(
            slug,
            name,
            aisle,
            measure,
            new BigDecimal(typicalAmount),
            Pattern.compile(includes),
            excludes == null ? null : Pattern.compile(excludes));
    }

    public boolean matches(String words) {
        if (!includes.matcher(words).find()) {return false;}

        return excludes == null || !excludes.matcher(words).find();
    }

    public UUID id() {
        return UUID.nameUUIDFromBytes(("basic-item:" + slug).getBytes(StandardCharsets.UTF_8));
    }

    public String typicalLabel() {
        return measure.amountLabel(typicalAmount);
    }

    public boolean typicalPackage(BigDecimal amount) {
        if (amount == null) {return false;}

        BigDecimal share = amount.divide(typicalAmount, 4, RoundingMode.HALF_UP);

        return share.compareTo(SMALLEST_TYPICAL_SHARE) >= 0 && share.compareTo(LARGEST_TYPICAL_SHARE) <= 0;
    }
}
