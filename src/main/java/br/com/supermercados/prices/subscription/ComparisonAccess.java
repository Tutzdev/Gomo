package br.com.supermercados.prices.subscription;

import java.util.List;

/**
 * How much of a price comparison the caller may see.
 *
 * @param visibleStoreLimit how many of the cheapest stores are shown; {@code null} shows every store
 * @param lockedStores stores with a current price that were left out because of the plan
 * @param dailyComparisonsUsed complete comparisons used today; {@code null} when the plan does not count them
 */
public record ComparisonAccess(
        boolean premium,
        Integer visibleStoreLimit,
        int lockedStores,
        Integer dailyComparisonsUsed,
        Integer dailyComparisonsLimit,
        boolean dailyLimitReached) {

    public static ComparisonAccess premiumAccess() {
        return new ComparisonAccess(true, null, 0, null, null, false);
    }

    /** Free view without the daily counter: anonymous visitors and shopping-list comparisons. */
    public static ComparisonAccess uncountedFreeAccess() {
        return new ComparisonAccess(false, FreePlan.VISIBLE_STORES, 0, null, null, false);
    }

    public static ComparisonAccess forPlan(boolean premium) {
        return premium ? premiumAccess() : uncountedFreeAccess();
    }

    public static ComparisonAccess freeAccess(int comparisonsUsed) {
        return new ComparisonAccess(false, FreePlan.VISIBLE_STORES, 0, comparisonsUsed,
                FreePlan.DAILY_COMPARISONS, false);
    }

    public static ComparisonAccess dailyLimitReachedAccess(int comparisonsUsed) {
        return new ComparisonAccess(false, FreePlan.VISIBLE_STORES_AFTER_DAILY_LIMIT, 0, comparisonsUsed,
                FreePlan.DAILY_COMPARISONS, true);
    }

    /** Keeps the first stores of a list already ranked cheapest first. */
    public <T> List<T> visibleOf(List<T> rankedStores) {
        if (visibleStoreLimit == null || rankedStores.size() <= visibleStoreLimit) {
            return rankedStores;
        }
        return rankedStores.subList(0, visibleStoreLimit);
    }

    public ComparisonAccess withLockedStores(int lockedStores) {
        return new ComparisonAccess(premium, visibleStoreLimit, lockedStores, dailyComparisonsUsed,
                dailyComparisonsLimit, dailyLimitReached);
    }
}
