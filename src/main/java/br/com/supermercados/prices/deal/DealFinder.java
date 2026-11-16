package br.com.supermercados.prices.deal;

import br.com.supermercados.prices.price.UsualPrice;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.ResultSet;
import java.sql.SQLException;
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

@Component
class DealFinder {

    private static final String DEALS = """
            WITH fresh AS (
                SELECT r.store_id, r.product_id, r.regular_price, r.promotional_price, r.promotion_condition,
                       r.promotion_valid_until, r.availability, r.valid_until, r.collected_at
                FROM latest_prices latest
                JOIN price_records r ON r.id = latest.price_record_id
                WHERE latest.store_id IN (:stores) AND latest.collected_at > :visitFreshAfter
                  AND (latest.origin_type = 'STORE_VISIT' OR latest.collected_at > :freshAfter)
                  AND latest.collected_at <= :now
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
            """;

    static final int MINIMUM_DISCOUNT_PERCENT = 10;
    static final int HISTORY_DAYS = 90;
    static final int MAXIMUM_DEALS = 600;

    private final NamedParameterJdbcTemplate jdbc;
    private final Duration maxPriceAge;
    private final Duration inStoreMaxPriceAge;

    DealFinder(
        NamedParameterJdbcTemplate jdbc,
        @Value("${app.prices.max-age:P2D}") Duration maxPriceAge,
        @Value("${app.prices.in-store-max-age:P7D}") Duration inStoreMaxPriceAge) {

        this.inStoreMaxPriceAge = inStoreMaxPriceAge;
        this.jdbc = jdbc;
        this.maxPriceAge = maxPriceAge;
    }

    record Offer(
            UUID itemId,
            UUID storeId,
            BigDecimal price,
            boolean promotion,
            Instant collectedAt,
            BigDecimal usualPrice,
            BigDecimal lowestBefore) {

        int discountPercent() {
            return usualPrice.subtract(price).movePointRight(2)
                    .divide(usualPrice, 0, RoundingMode.HALF_UP).intValue();
        }

        boolean lowestIn90Days() {
            return price.compareTo(lowestBefore) <= 0;
        }
    }

    List<UUID> citiesWithStores() {
        return jdbc.getJdbcTemplate().queryForList("SELECT DISTINCT city_id FROM stores WHERE active", UUID.class);
    }

    List<Offer> find(Collection<UUID> storeIds, Instant now) {
        if (storeIds.isEmpty()) {return List.of();}

        LocalDate today = LocalDate.ofInstant(now, UsualPrice.ZONE);

        var parameters = new MapSqlParameterSource()
                .addValue("stores", storeIds)
                .addValue("now", Timestamp.from(now))
                .addValue("freshAfter", Timestamp.from(now.minus(maxPriceAge)))
                .addValue("visitFreshAfter", Timestamp.from(now.minus(inStoreMaxPriceAge)))
                .addValue("historySince", today.minusDays(HISTORY_DAYS))
                .addValue("usualSince", UsualPrice.windowStart(today))
                .addValue("today", today)
                .addValue("minimumDays", UsualPrice.MINIMUM_DAYS)
                .addValue("maximumShare", BigDecimal.valueOf(100 - MINIMUM_DISCOUNT_PERCENT).movePointLeft(2))
                .addValue("limit", MAXIMUM_DEALS);

        return jdbc.query(DEALS, parameters, (row, index) -> offer(row));
    }

    private static Offer offer(ResultSet row) throws SQLException {
        return new Offer(
            row.getObject("catalog_item_id", UUID.class),
            row.getObject("store_id", UUID.class),
            row.getBigDecimal("price"),
            row.getBoolean("applied"),
            row.getTimestamp("collected_at").toInstant(),
            row.getBigDecimal("usual_price"),
            row.getBigDecimal("lowest_before"));
    }
}
