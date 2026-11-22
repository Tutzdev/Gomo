package br.com.supermercados.prices.deal;

import br.com.supermercados.prices.price.UsualPrice;
import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

@Component
class DealFinder {

    private static final String CURRENT_PRICES = """
            SELECT DISTINCT ON (link.catalog_item_id, latest.store_id)
                   link.catalog_item_id, latest.store_id, latest.collected_at, promotion.applied,
                   CASE WHEN promotion.applied THEN latest.promotional_price ELSE latest.regular_price END AS price
            FROM latest_prices latest
            CROSS JOIN LATERAL (SELECT latest.promotional_price IS NOT NULL AND latest.promotion_condition IS NULL
                                       AND latest.promotion_valid_until > :now AS applied) promotion
            JOIN catalog_item_products link ON link.product_id = latest.product_id
            JOIN catalog_items item ON item.id = link.catalog_item_id AND item.active
            WHERE latest.store_id IN (:stores) AND latest.collected_at > :visitFreshAfter
              AND (latest.origin_type = 'STORE_VISIT' OR latest.collected_at > :freshAfter)
              AND latest.collected_at <= :now
              AND latest.availability <> 'UNAVAILABLE' AND (latest.valid_until IS NULL OR latest.valid_until > :now)
            ORDER BY link.catalog_item_id, latest.store_id, price
            """;

    private static final String HISTORY = """
            WITH daily AS (
                SELECT link.catalog_item_id, d.price_day, MIN(d.lowest_price) AS day_lowest
                FROM daily_lowest_prices d
                JOIN catalog_item_products link ON link.product_id = d.product_id
                WHERE d.store_id IN (:stores) AND d.price_day >= :historySince AND d.price_day < :today
                GROUP BY link.catalog_item_id, d.price_day
            )
            SELECT catalog_item_id,
                   ROUND(AVG(day_lowest) FILTER (WHERE price_day >= :usualSince), 2) AS usual_price,
                   COUNT(*) FILTER (WHERE price_day >= :usualSince) AS usual_days,
                   MIN(day_lowest) AS lowest_before
            FROM daily
            GROUP BY catalog_item_id
            HAVING COUNT(*) FILTER (WHERE price_day >= :usualSince) >= :minimumDays
            """;

    static final int HISTORY_DAYS = 90;

    private final NamedParameterJdbcTemplate jdbc;
    private final Duration maxPriceAge;
    private final Duration inStoreMaxPriceAge;

    DealFinder(
        NamedParameterJdbcTemplate jdbc,
        @Value("${app.prices.max-age:P2D}") Duration maxPriceAge,
        @Value("${app.prices.in-store-max-age:P7D}") Duration inStoreMaxPriceAge) {

        this.jdbc = jdbc;
        this.maxPriceAge = maxPriceAge;
        this.inStoreMaxPriceAge = inStoreMaxPriceAge;
    }

    List<UUID> citiesWithStores() {
        return jdbc.getJdbcTemplate().queryForList("SELECT DISTINCT city_id FROM stores WHERE active", UUID.class);
    }

    List<CurrentPrice> currentPrices(Collection<UUID> storeIds, Instant now) {
        if (storeIds.isEmpty()) {return List.of();}

        var parameters = new MapSqlParameterSource()
                .addValue("stores", storeIds)
                .addValue("now", Timestamp.from(now))
                .addValue("freshAfter", Timestamp.from(now.minus(maxPriceAge)))
                .addValue("visitFreshAfter", Timestamp.from(now.minus(inStoreMaxPriceAge)));

        return jdbc.query(CURRENT_PRICES, parameters, (row, index) -> currentPrice(row));
    }

    Map<UUID, ItemHistory> history(Collection<UUID> storeIds, LocalDate today) {
        if (storeIds.isEmpty()) {return Map.of();}

        var parameters = new MapSqlParameterSource()
                .addValue("stores", storeIds)
                .addValue("historySince", today.minusDays(HISTORY_DAYS))
                .addValue("usualSince", UsualPrice.windowStart(today))
                .addValue("today", today)
                .addValue("minimumDays", UsualPrice.MINIMUM_DAYS);

        Map<UUID, ItemHistory> history = new HashMap<>();

        jdbc.query(HISTORY, parameters, row -> {
            history.put(row.getObject("catalog_item_id", UUID.class), itemHistory(row));
        });

        return Map.copyOf(history);
    }

    private static CurrentPrice currentPrice(ResultSet row) throws SQLException {
        return new CurrentPrice(
            row.getObject("catalog_item_id", UUID.class),
            row.getObject("store_id", UUID.class),
            row.getBigDecimal("price"),
            row.getBoolean("applied"),
            row.getTimestamp("collected_at").toInstant());
    }

    private static ItemHistory itemHistory(ResultSet row) throws SQLException {
        return new ItemHistory(
            row.getBigDecimal("usual_price"),
            row.getBigDecimal("lowest_before"));
    }

    record CurrentPrice(UUID itemId, UUID storeId, BigDecimal price, boolean promotion, Instant collectedAt) {}

    record ItemHistory(BigDecimal usualPrice, BigDecimal lowestBefore) {}
}
