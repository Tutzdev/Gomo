package br.com.supermercados.prices.deal;

import br.com.supermercados.prices.price.UsualPrice;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Every current offer, item by store, that is at least {@link #MINIMUM_DISCOUNT_PERCENT}% under the item's
 * {@link UsualPrice} in the city. One query for the whole city: the current price follows the same rules as
 * {@code PricePolicy} (newest observation of the store, fresh, valid, in stock, promotion still running) and the
 * reference comes from {@code daily_lowest_prices}.
 */
@Component
class DealFinder {

    static final int MINIMUM_DISCOUNT_PERCENT = 10;
    /** The history window of "the lowest price in 90 days". */
    static final int HISTORY_DAYS = 90;
    /** A city page lists the deepest discounts; past this many it stops being a page anyone reads. */
    static final int MAXIMUM_DEALS = 600;

    private final NamedParameterJdbcTemplate jdbc;
    private final Duration maxPriceAge;

    DealFinder(NamedParameterJdbcTemplate jdbc, @Value("${app.prices.max-age:P2D}") Duration maxPriceAge) {
        this.jdbc = jdbc;
        this.maxPriceAge = maxPriceAge;
    }

    /** One item at one store, cheaper than usual. */
    record Offer(UUID itemId, UUID storeId, BigDecimal price, boolean promotion, Instant collectedAt,
            BigDecimal usualPrice, BigDecimal lowestBefore) {

        int discountPercent() {
            return usualPrice.subtract(price).movePointRight(2)
                    .divide(usualPrice, 0, java.math.RoundingMode.HALF_UP).intValue();
        }

        /** As cheap as or cheaper than every day of the last 90 before today. */
        boolean lowestIn90Days() {
            return price.compareTo(lowestBefore) <= 0;
        }
    }

    List<Offer> find(Collection<UUID> storeIds, Instant now) {
        if (storeIds.isEmpty()) {
            return List.of();
        }
        LocalDate today = LocalDate.ofInstant(now, UsualPrice.ZONE);
        var parameters = new MapSqlParameterSource()
                .addValue("stores", storeIds)
                .addValue("now", Timestamp.from(now))
                .addValue("freshAfter", Timestamp.from(now.minus(maxPriceAge)))
                .addValue("historySince", today.minusDays(HISTORY_DAYS))
                .addValue("usualSince", UsualPrice.windowStart(today))
                .addValue("today", today)
                .addValue("minimumDays", UsualPrice.MINIMUM_DAYS)
                .addValue("maximumShare", BigDecimal.valueOf(100 - MINIMUM_DISCOUNT_PERCENT).movePointLeft(2))
                .addValue("limit", MAXIMUM_DEALS);
        return jdbc.query("""
                WITH fresh AS (
                    SELECT DISTINCT ON (r.store_id, r.product_id) r.store_id, r.product_id, r.regular_price,
                           r.promotional_price, r.promotion_condition, r.promotion_valid_until, r.availability,
                           r.valid_until, r.collected_at
                    FROM price_records r
                    WHERE r.store_id IN (:stores) AND r.collected_at > :freshAfter AND r.collected_at <= :now
                    ORDER BY r.store_id, r.product_id, r.collected_at DESC, r.recorded_at DESC, r.id DESC
                ), current_prices AS (
                    SELECT DISTINCT ON (link.catalog_item_id, fresh.store_id)
                           link.catalog_item_id, fresh.store_id, fresh.collected_at, promotion.applied,
                           CASE WHEN promotion.applied THEN fresh.promotional_price ELSE fresh.regular_price END AS price
                    FROM fresh
                    CROSS JOIN LATERAL (SELECT fresh.promotional_price IS NOT NULL AND fresh.promotion_condition IS NULL
                                               AND fresh.promotion_valid_until > :now AS applied) promotion
                    JOIN catalog_item_products link ON link.product_id = fresh.product_id
                    JOIN catalog_items item ON item.id = link.catalog_item_id AND item.active
                    WHERE fresh.availability <> 'UNAVAILABLE' AND (fresh.valid_until IS NULL OR fresh.valid_until > :now)
                    ORDER BY link.catalog_item_id, fresh.store_id, price
                ), daily AS (
                    SELECT link.catalog_item_id, d.price_day, MIN(d.lowest_price) AS day_lowest
                    FROM daily_lowest_prices d
                    JOIN catalog_item_products link ON link.product_id = d.product_id
                    WHERE d.store_id IN (:stores) AND d.price_day >= :historySince AND d.price_day < :today
                      AND link.catalog_item_id IN (SELECT catalog_item_id FROM current_prices)
                    GROUP BY link.catalog_item_id, d.price_day
                ), reference AS (
                    SELECT catalog_item_id,
                           ROUND(AVG(day_lowest) FILTER (WHERE price_day >= :usualSince), 2) AS usual_price,
                           COUNT(*) FILTER (WHERE price_day >= :usualSince) AS usual_days,
                           MIN(day_lowest) AS lowest_before
                    FROM daily
                    GROUP BY catalog_item_id
                )
                SELECT current_prices.catalog_item_id, current_prices.store_id, current_prices.price,
                       current_prices.applied, current_prices.collected_at, reference.usual_price,
                       reference.lowest_before
                FROM current_prices
                JOIN reference ON reference.catalog_item_id = current_prices.catalog_item_id
                WHERE reference.usual_days >= :minimumDays
                  AND current_prices.price <= reference.usual_price * :maximumShare
                ORDER BY current_prices.price / reference.usual_price, current_prices.catalog_item_id,
                         current_prices.store_id
                LIMIT :limit
                """, parameters, (row, index) -> new Offer(
                row.getObject("catalog_item_id", UUID.class), row.getObject("store_id", UUID.class),
                row.getBigDecimal("price"), row.getBoolean("applied"),
                row.getTimestamp("collected_at").toInstant(), row.getBigDecimal("usual_price"),
                row.getBigDecimal("lowest_before")));
    }
}
