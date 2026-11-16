package br.com.supermercados.prices.subscription;

import java.util.List;

public record ComparisonAccess(
        boolean premium,
        Integer visibleStoreLimit,
        int lockedStores,
        Integer dailyComparisonsUsed,
        Integer dailyComparisonsLimit,
        boolean dailyLimitReached) {

    public static ComparisonAccess premiumAccess() {
        return new ComparisonAccess(
            true,
            null,
            0,
            null,
            null,
            false);
    }

    public static ComparisonAccess uncountedFreeAccess() {
        return new ComparisonAccess(
            false,
            FreePlan.VISIBLE_STORES,
            0,
            null,
            null,
            false);
    }

    public static ComparisonAccess forPlan(boolean premium) {
        return premium ? premiumAccess() : uncountedFreeAccess();
    }

    public static ComparisonAccess freeAccess(int comparisonsUsed) {
        return new ComparisonAccess(
            false,
            FreePlan.VISIBLE_STORES,
            0,
            comparisonsUsed,
            FreePlan.DAILY_COMPARISONS,
            false);
    }

    public static ComparisonAccess dailyLimitReachedAccess(int comparisonsUsed) {
        return new ComparisonAccess(
            false,
            FreePlan.VISIBLE_STORES_AFTER_DAILY_LIMIT,
            0,
            comparisonsUsed,
            FreePlan.DAILY_COMPARISONS,
            true);
    }

    public <T> List<T> visibleOf(List<T> rankedStores) {
        if (visibleStoreLimit == null || rankedStores.size() <= visibleStoreLimit) {return rankedStores;}

        return rankedStores.subList(0, visibleStoreLimit);
    }

    public ComparisonAccess withLockedStores(int lockedStores) {
        return new ComparisonAccess(
            premium,
            visibleStoreLimit,
            lockedStores,
            dailyComparisonsUsed,
            dailyComparisonsLimit,
            dailyLimitReached);
    }
}
