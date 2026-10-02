package br.com.supermercados.prices.subscription;

import java.time.Instant;

/** The account's plan, what the free plan allows and how much of it is in use. */
public record SubscriptionStatusResponse(
        boolean premium,
        PremiumSource premiumSource,
        Instant trialEndsAt,
        boolean trialAvailable,
        BillingCycle preferredBillingCycle,
        FreeLimits freeLimits,
        Usage usage) {

    public record FreeLimits(int shoppingLists, int listItems, int activeAlerts, int dailyComparisons,
            int visibleStores) {

        static FreeLimits current() {
            return new FreeLimits(FreePlan.MAX_SHOPPING_LISTS, FreePlan.MAX_LIST_ITEMS, FreePlan.MAX_ACTIVE_ALERTS,
                    FreePlan.DAILY_COMPARISONS, FreePlan.VISIBLE_STORES);
        }
    }

    public record Usage(long shoppingLists, long activeAlerts, int comparisonsToday) {
    }
}
