package br.com.supermercados.prices.subscription;

import java.time.Duration;
import java.time.ZoneId;

public final class FreePlan {

    public static final int MAX_SHOPPING_LISTS = 1;
    public static final int MAX_LIST_ITEMS = 10;
    public static final int MAX_ACTIVE_ALERTS = 2;
    public static final int DAILY_COMPARISONS = 5;
    public static final int VISIBLE_STORES = 3;
    public static final int VISIBLE_STORES_AFTER_DAILY_LIMIT = 1;

    public static final Duration TRIAL_LENGTH = Duration.ofDays(7);
    public static final Duration PREMIUM_HISTORY = Duration.ofDays(90);

    public static final ZoneId USAGE_ZONE = ZoneId.of("America/Sao_Paulo");

    private FreePlan() {}
}
