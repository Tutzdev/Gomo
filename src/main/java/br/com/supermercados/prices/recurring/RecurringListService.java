package br.com.supermercados.prices.recurring;

import br.com.supermercados.prices.comparison.ShoppingComparisonService;
import br.com.supermercados.prices.comparison.StoreRecommendationCandidate;
import br.com.supermercados.prices.location.LocationService;
import br.com.supermercados.prices.price.UsualPrice;
import br.com.supermercados.prices.shoppinglist.ShoppingListItemResponse;
import br.com.supermercados.prices.shoppinglist.ShoppingListResponse;
import br.com.supermercados.prices.shoppinglist.ShoppingListService;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.LocalDate;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Weekly and monthly lists recalculated: what each one costs today at its best single market, kept one row a day
 * so the home page can compare it with about a week ago. A list is compared only with itself as it was: when its
 * items or quantities changed, the difference would say nothing about prices.
 */
@Service
public class RecurringListService {

    private static final Logger log = LoggerFactory.getLogger(RecurringListService.class);
    /** "Last week" is the newest day between 7 and 13 days ago. */
    static final int WEEK = 7;
    static final int WEEK_TOLERANCE = 6;
    /** Older days serve no comparison. */
    static final int RETENTION_DAYS = 60;

    private final ShoppingListService shoppingLists;
    private final ShoppingComparisonService comparisons;
    private final LocationService locations;
    private final NamedParameterJdbcTemplate jdbc;
    private final Clock clock;

    public RecurringListService(ShoppingListService shoppingLists, ShoppingComparisonService comparisons,
            LocationService locations, NamedParameterJdbcTemplate jdbc, Clock clock) {
        this.shoppingLists = shoppingLists;
        this.comparisons = comparisons;
        this.locations = locations;
        this.jdbc = jdbc;
        this.clock = clock;
    }

    /** The person's weekly and monthly lists in the city, recalculated now. */
    @Transactional
    public List<RecurringListResponse> mine(UUID userId, UUID cityId) {
        locations.requireCity(cityId);
        LocalDate today = UsualPrice.today(clock);
        return shoppingLists.findRecurringLists(userId).stream()
                .map(summary -> shoppingLists.getOwnedList(userId, summary.id()))
                .filter(list -> !list.items().isEmpty())
                .map(list -> {
                    Snapshot now = snapshot(userId, list, cityId, today);
                    Optional<Snapshot> before = weekBefore(list.id(), cityId, today);
                    boolean changed = before.isPresent() && !before.get().signature().equals(now.signature());
                    BigDecimal difference = before.isPresent() && !changed && now.total() != null
                            && before.get().total() != null && now.complete() == before.get().complete()
                            ? now.total().subtract(before.get().total()) : null;
                    return new RecurringListResponse(list.id(), list.name(), list.shoppingType(), cityId,
                            now.storeId(), now.storeName(), now.total(), now.complete(), now.pricedItems(),
                            now.requestedItems(), before.map(Snapshot::day).orElse(null),
                            before.map(Snapshot::total).orElse(null), before.map(Snapshot::storeName).orElse(null),
                            difference, changed);
                })
                .toList();
    }

    /**
     * Daily: today's row for every weekly and monthly list of someone with a preferred city, so next week has a
     * day to compare with even if the person did not open the app. One list failing does not stop the others.
     */
    public int snapshotAll() {
        LocalDate today = UsualPrice.today(clock);
        List<Object[]> lists = jdbc.query("""
                SELECT l.id, l.user_id, p.preferred_city_id
                FROM shopping_lists l JOIN user_preferences p ON p.user_id = l.user_id
                WHERE l.shopping_type IN ('WEEKLY', 'MONTHLY') AND p.preferred_city_id IS NOT NULL
                """, (row, index) -> new Object[] {row.getObject(1, UUID.class), row.getObject(2, UUID.class),
                row.getObject(3, UUID.class)});
        int written = 0;
        for (Object[] list : lists) {
            try {
                var response = shoppingLists.getOwnedList((UUID) list[1], (UUID) list[0]);
                if (!response.items().isEmpty()) {
                    snapshot((UUID) list[1], response, (UUID) list[2], today);
                    written++;
                }
            } catch (RuntimeException exception) {
                log.warn("Recurring list {} was not recalculated: {}", list[0], exception.getMessage());
            }
        }
        jdbc.update("DELETE FROM recurring_list_snapshots WHERE snapshot_day < :oldest",
                new MapSqlParameterSource("oldest", today.minusDays(RETENTION_DAYS)));
        return written;
    }

    private Snapshot snapshot(UUID userId, ShoppingListResponse list, UUID cityId, LocalDate today) {
        var recommendation = comparisons.recommendShoppingList(userId, list.id(), cityId, null, true);
        StoreRecommendationCandidate best = recommendation.recommendation() != null ? recommendation.recommendation()
                : recommendation.closestMatches().isEmpty() ? null : recommendation.closestMatches().getFirst();
        Snapshot snapshot = new Snapshot(today, best == null ? null : best.storeId(),
                best == null ? null : best.storeName(), best == null ? null : best.total(),
                best != null && best.completeShoppingList(), best == null ? 0 : best.pricedItems(),
                list.items().size(), signature(list.items()));
        jdbc.update("""
                INSERT INTO recurring_list_snapshots (id, shopping_list_id, city_id, snapshot_day, store_id, store_name,
                    total, complete, priced_items, requested_items, items_signature, created_at)
                VALUES (:id, :listId, :cityId, :day, :storeId, :storeName, :total, :complete, :pricedItems,
                    :requestedItems, :signature, :createdAt)
                ON CONFLICT (shopping_list_id, city_id, snapshot_day) DO UPDATE SET
                    store_id = EXCLUDED.store_id, store_name = EXCLUDED.store_name, total = EXCLUDED.total,
                    complete = EXCLUDED.complete, priced_items = EXCLUDED.priced_items,
                    requested_items = EXCLUDED.requested_items, items_signature = EXCLUDED.items_signature,
                    created_at = EXCLUDED.created_at
                """, new MapSqlParameterSource()
                .addValue("id", UUID.randomUUID()).addValue("listId", list.id()).addValue("cityId", cityId)
                .addValue("day", today).addValue("storeId", snapshot.storeId())
                .addValue("storeName", snapshot.storeName()).addValue("total", snapshot.total())
                .addValue("complete", snapshot.complete()).addValue("pricedItems", snapshot.pricedItems())
                .addValue("requestedItems", snapshot.requestedItems()).addValue("signature", snapshot.signature())
                .addValue("createdAt", Timestamp.from(clock.instant())));
        return snapshot;
    }

    private Optional<Snapshot> weekBefore(UUID listId, UUID cityId, LocalDate today) {
        return jdbc.query("""
                SELECT snapshot_day, store_id, store_name, total, complete, priced_items, requested_items,
                    items_signature
                FROM recurring_list_snapshots
                WHERE shopping_list_id = :listId AND city_id = :cityId AND snapshot_day <= :latest
                  AND snapshot_day >= :earliest
                ORDER BY snapshot_day DESC
                LIMIT 1
                """, new MapSqlParameterSource().addValue("listId", listId).addValue("cityId", cityId)
                .addValue("latest", today.minusDays(WEEK)).addValue("earliest", today.minusDays(WEEK + WEEK_TOLERANCE)),
                (row, index) -> new Snapshot(row.getObject("snapshot_day", LocalDate.class),
                        row.getObject("store_id", UUID.class), row.getString("store_name"),
                        row.getBigDecimal("total"), row.getBoolean("complete"), row.getInt("priced_items"),
                        row.getInt("requested_items"), row.getString("items_signature")))
                .stream().findFirst();
    }

    /** The items and quantities, in a stable order. */
    static String signature(List<ShoppingListItemResponse> items) {
        String lines = items.stream()
                .map(item -> item.productId() + ":" + item.quantity().stripTrailingZeros().toPlainString())
                .sorted().reduce("", (all, line) -> all + line + "\n");
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(lines.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("The Java runtime must support SHA-256", exception);
        }
    }

    private record Snapshot(LocalDate day, UUID storeId, String storeName, BigDecimal total, boolean complete,
            int pricedItems, int requestedItems, String signature) {
    }
}
