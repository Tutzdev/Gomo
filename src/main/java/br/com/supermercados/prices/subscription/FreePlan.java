package br.com.supermercados.prices.subscription;

import java.time.Duration;
import java.time.ZoneId;

/**
 * Limits of the free plan. They are generous enough for a first real saving: one list with ten products,
 * five complete comparisons a day and two alerts.
 */
public final class FreePlan {

    public static final int MAX_SHOPPING_LISTS = 1;
    public static final int MAX_LIST_ITEMS = 10;
    public static final int MAX_ACTIVE_ALERTS = 2;
    public static final int DAILY_COMPARISONS = 5;
    public static final int VISIBLE_STORES = 3;
    /** After the daily comparisons are used, the cheapest store is still shown. */
    public static final int VISIBLE_STORES_AFTER_DAILY_LIMIT = 1;

    public static final Duration TRIAL_LENGTH = Duration.ofDays(7);
    public static final Duration PREMIUM_HISTORY = Duration.ofDays(90);

    /** The "day" of the daily comparison limit follows the shoppers' local calendar. */
    public static final ZoneId USAGE_ZONE = ZoneId.of("America/Sao_Paulo");

    private FreePlan() {
    }
}
