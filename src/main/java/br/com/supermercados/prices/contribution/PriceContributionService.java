package br.com.supermercados.prices.contribution;

import br.com.supermercados.prices.admin.AdminAction;
import br.com.supermercados.prices.admin.AdminAuditService;
import br.com.supermercados.prices.auth.AuthenticatedUser;
import br.com.supermercados.prices.common.ApiException;
import br.com.supermercados.prices.common.PageResponse;
import br.com.supermercados.prices.price.PriceObservation;
import br.com.supermercados.prices.price.PriceObservationRules;
import br.com.supermercados.prices.price.PriceRecordResponse;
import br.com.supermercados.prices.price.PriceService;
import br.com.supermercados.prices.product.ProductService;
import br.com.supermercados.prices.store.StoreService;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PriceContributionService {

    private static final Logger log = LoggerFactory.getLogger(PriceContributionService.class);

    private final PriceContributionRepository contributions;
    private final ProductService products;
    private final StoreService stores;
    private final PriceService prices;
    private final PriceObservationRules observationRules;
    private final ContributionSource contributionSource;
    private final AdminAuditService audit;
    private final Clock clock;

    public PriceContributionService(PriceContributionRepository contributions, ProductService products,
            StoreService stores, PriceService prices, PriceObservationRules observationRules,
            ContributionSource contributionSource, AdminAuditService audit, Clock clock) {
        this.contributions = contributions;
        this.products = products;
        this.stores = stores;
        this.prices = prices;
        this.observationRules = observationRules;
        this.contributionSource = contributionSource;
        this.audit = audit;
        this.clock = clock;
    }

    @Transactional
    public PriceContributionResponse submit(
            AuthenticatedUser contributor, SubmitPriceContributionRequest request) {
        requireVerifiedEmail(contributor);
        Instant now = clock.instant();
        observationRules.validate(request.regularPrice(), request.promotionalPrice(), request.observedAt(),
                request.validUntil(), request.promotionValidUntil(), now);
        products.requireProduct(request.productId());
        stores.requireActiveStore(request.storeId());

        PriceContribution contribution = new PriceContribution(contributor.id(), request, now);
        return PriceContributionResponse.from(contributions.save(contribution));
    }

    @Transactional(readOnly = true)
    public PageResponse<PriceContributionResponse> findMine(UUID contributorId, Pageable pageable) {
        return PageResponse.from(contributions.findByContributorId(contributorId, pageable)
                .map(PriceContributionResponse::from));
    }

    @Transactional(readOnly = true)
    public PriceContributionResponse findMine(UUID contributorId, UUID contributionId) {
        return contributions.findByIdAndContributorId(contributionId, contributorId)
                .map(PriceContributionResponse::from)
                .orElseThrow(() -> notFound());
    }

    @Transactional(readOnly = true)
    public PageResponse<PriceContributionResponse> findForModeration(
            ContributionStatus status, Pageable pageable) {
        return PageResponse.from(contributions.findByStatus(status, pageable)
                .map(PriceContributionResponse::from));
    }

    @Transactional
    public PriceContributionResponse approve(UUID moderatorId, UUID contributionId) {
        PriceContribution contribution = requireForDecision(contributionId);
        if (contribution.getStatus() == ContributionStatus.APPROVED) {
            return PriceContributionResponse.from(contribution);
        }
        if (contribution.getStatus() == ContributionStatus.REJECTED) {
            throw alreadyDecided();
        }

        UUID sourceId = contributionSource.requireEnabledSource().getId();
        PriceObservation observation = new PriceObservation(
                contribution.getProductId(), contribution.getStoreId(), sourceId,
                "user-contribution:" + contribution.getId(), contribution.getRegularPrice(),
                contribution.getPromotionalPrice(), contribution.getCurrency(), contribution.getObservedAt(),
                contribution.getValidUntil(), contribution.getPromotionValidUntil(), contribution.getAvailability());
        PriceRecordResponse record = prices.appendUserContribution(observation, contribution.getId());
        contribution.approve(moderatorId, record.id(), clock.instant());
        audit.record(moderatorId, AdminAction.CONTRIBUTION_APPROVED, "PRICE_CONTRIBUTION", contributionId);
        log.info("Contribution {} approved by moderator {}", contributionId, moderatorId);
        return PriceContributionResponse.from(contribution);
    }

    @Transactional
    public PriceContributionResponse reject(
            UUID moderatorId, UUID contributionId, RejectContributionRequest request) {
        PriceContribution contribution = requireForDecision(contributionId);
        if (contribution.getStatus() == ContributionStatus.REJECTED) {
            return PriceContributionResponse.from(contribution);
        }
        if (contribution.getStatus() == ContributionStatus.APPROVED) {
            throw alreadyDecided();
        }

        contribution.reject(moderatorId, request.reason(), clock.instant());
        audit.record(moderatorId, AdminAction.CONTRIBUTION_REJECTED, "PRICE_CONTRIBUTION", contributionId);
        log.info("Contribution {} rejected by moderator {}", contributionId, moderatorId);
        return PriceContributionResponse.from(contribution);
    }

    private PriceContribution requireForDecision(UUID contributionId) {
        return contributions.findByIdForUpdate(contributionId).orElseThrow(this::notFound);
    }

    private void requireVerifiedEmail(AuthenticatedUser contributor) {
        if (!contributor.emailVerified()) {
            throw new ApiException(HttpStatus.FORBIDDEN,
                    "Confirme o e-mail antes de enviar contribuições.");
        }
    }

    private ApiException notFound() {
        return new ApiException(HttpStatus.NOT_FOUND, "Contribuição não encontrada.");
    }

    private ApiException alreadyDecided() {
        return new ApiException(HttpStatus.CONFLICT, "A contribuição já recebeu uma decisão diferente.");
    }
}
