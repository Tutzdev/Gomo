package br.com.supermercados.prices.subscription;

import br.com.supermercados.prices.alert.PriceAlertService;
import br.com.supermercados.prices.common.ApiException;
import br.com.supermercados.prices.comparison.ShoppingComparisonService;
import br.com.supermercados.prices.comparison.ShoppingRecommendationResponse;
import br.com.supermercados.prices.comparison.SplitSavings;
import br.com.supermercados.prices.shoppinglist.ShoppingListService;
import br.com.supermercados.prices.shoppinglist.ShoppingListSummary;
import br.com.supermercados.prices.user.User;
import br.com.supermercados.prices.user.UserRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** The account's plan and the free 7-day Premium trial. Paid checkout does not exist yet. */
@Service
public class SubscriptionService {

    private static final int LISTS_CHECKED_FOR_VALUE = 5;

    private final UserRepository users;
    private final ShoppingListService shoppingLists;
    private final PriceAlertService alerts;
    private final ComparisonUsageService comparisonUsage;
    private final ShoppingComparisonService comparisons;
    private final Clock clock;

    public SubscriptionService(UserRepository users, ShoppingListService shoppingLists, PriceAlertService alerts,
            ComparisonUsageService comparisonUsage, ShoppingComparisonService comparisons, Clock clock) {
        this.users = users;
        this.shoppingLists = shoppingLists;
        this.alerts = alerts;
        this.comparisonUsage = comparisonUsage;
        this.comparisons = comparisons;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public SubscriptionStatusResponse status(UUID userId) {
        return status(requireUser(userId), clock.instant());
    }

    /** Starts the one-time trial. No payment data is requested; the chosen cycle is kept for future checkout. */
    @Transactional
    public SubscriptionStatusResponse startTrial(UUID userId, StartTrialRequest request) {
        User user = requireUser(userId);
        Instant now = clock.instant();
        if (user.hasPremiumAccess(now)) {
            throw new ApiException(HttpStatus.CONFLICT, "Sua conta já tem acesso Premium.");
        }
        if (!user.isTrialAvailable(now)) {
            throw new ApiException(HttpStatus.CONFLICT, "O teste grátis do Premium já foi usado nesta conta.");
        }

        user.startPremiumTrial(FreePlan.TRIAL_LENGTH, request.billingCycle(), now);
        return status(user, now);
    }

    @Transactional
    public SubscriptionStatusResponse cancelTrial(UUID userId) {
        User user = requireUser(userId);
        Instant now = clock.instant();
        if (user.premiumSource(now) != PremiumSource.TRIAL) {
            throw new ApiException(HttpStatus.CONFLICT, "Não há teste grátis ativo para cancelar.");
        }

        user.cancelPremiumTrial(now);
        return status(user, now);
    }

    /** The list where splitting the purchase between stores saves the most today. */
    @Transactional(readOnly = true)
    public PremiumValueResponse premiumValue(UUID userId, UUID cityId) {
        var recentLists = shoppingLists.findOwnedLists(userId,
                PageRequest.of(0, LISTS_CHECKED_FOR_VALUE, Sort.by(Sort.Direction.DESC, "updatedAt")));
        PremiumValueResponse best = PremiumValueResponse.none();
        for (ShoppingListSummary list : recentLists) {
            ShoppingRecommendationResponse recommendation = comparisons.recommendShoppingList(userId, list.id(), cityId);
            SplitSavings split = recommendation.splitSavings();
            if (split == null || split.savings().signum() <= 0) {
                continue;
            }
            if (best.savings() == null || split.savings().compareTo(best.savings()) > 0) {
                best = new PremiumValueResponse(list.name(), split.savings(), recommendation.combination().storeCount());
            }
        }
        return best;
    }

    private SubscriptionStatusResponse status(User user, Instant now) {
        PremiumSource source = user.premiumSource(now);
        Instant trialEndsAt = source == PremiumSource.TRIAL ? user.getPremiumTrialEndsAt() : null;
        var usage = new SubscriptionStatusResponse.Usage(shoppingLists.countOwnedLists(user.getId()),
                alerts.countActive(user.getId()), comparisonUsage.countToday(user.getId()));
        return new SubscriptionStatusResponse(source != PremiumSource.NONE, source, trialEndsAt,
                user.isTrialAvailable(now), user.getPreferredBillingCycle(),
                SubscriptionStatusResponse.FreeLimits.current(), usage);
    }

    private User requireUser(UUID userId) {
        return users.findById(userId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Usuário não encontrado."));
    }
}
