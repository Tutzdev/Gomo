package br.com.supermercados.prices.suggestion;

import br.com.supermercados.prices.admin.AdminAction;
import br.com.supermercados.prices.admin.AdminAuditService;
import br.com.supermercados.prices.auth.AuthenticatedUser;
import br.com.supermercados.prices.common.ApiException;
import br.com.supermercados.prices.common.PageResponse;
import br.com.supermercados.prices.contribution.ContributionStatus;
import br.com.supermercados.prices.store.StoreService;
import java.time.Clock;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Products shoppers miss in the catalog. Approving only marks the suggestion as accepted: the admin still
 * registers the product (and its price) through the catalog tools, so nothing a shopper typed reaches the
 * comparison unchecked.
 */
@Service
public class ProductSuggestionService {

    private final ProductSuggestionRepository suggestions;
    private final StoreService stores;
    private final AdminAuditService audit;
    private final Clock clock;

    public ProductSuggestionService(ProductSuggestionRepository suggestions, StoreService stores,
            AdminAuditService audit, Clock clock) {
        this.suggestions = suggestions;
        this.stores = stores;
        this.audit = audit;
        this.clock = clock;
    }

    @Transactional
    public ProductSuggestionResponse submit(AuthenticatedUser contributor, SubmitProductSuggestionRequest request) {
        if (!contributor.emailVerified()) {
            throw new ApiException(HttpStatus.FORBIDDEN, "Confirme o e-mail antes de sugerir produtos.");
        }
        if (request.price() != null && request.storeId() == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Informe o mercado onde viu esse preço.");
        }
        if (request.storeId() != null) stores.requireActiveStore(request.storeId());
        return ProductSuggestionResponse.from(
                suggestions.save(new ProductSuggestion(contributor.id(), request, clock.instant())));
    }

    @Transactional(readOnly = true)
    public PageResponse<ProductSuggestionResponse> findMine(UUID contributorId, Pageable pageable) {
        return PageResponse.from(suggestions.findByContributorId(contributorId, pageable)
                .map(ProductSuggestionResponse::from));
    }

    @Transactional(readOnly = true)
    public PageResponse<ProductSuggestionResponse> findForModeration(ContributionStatus status, Pageable pageable) {
        return PageResponse.from(suggestions.findByStatus(status, pageable).map(ProductSuggestionResponse::from));
    }

    @Transactional
    public ProductSuggestionResponse approve(UUID moderatorId, UUID suggestionId) {
        ProductSuggestion suggestion = requirePending(suggestionId);
        suggestion.approve(moderatorId, clock.instant());
        audit.record(moderatorId, AdminAction.PRODUCT_SUGGESTION_APPROVED, "PRODUCT_SUGGESTION", suggestionId);
        return ProductSuggestionResponse.from(suggestion);
    }

    @Transactional
    public ProductSuggestionResponse reject(UUID moderatorId, UUID suggestionId, String reason) {
        ProductSuggestion suggestion = requirePending(suggestionId);
        suggestion.reject(moderatorId, reason, clock.instant());
        audit.record(moderatorId, AdminAction.PRODUCT_SUGGESTION_REJECTED, "PRODUCT_SUGGESTION", suggestionId);
        return ProductSuggestionResponse.from(suggestion);
    }

    private ProductSuggestion requirePending(UUID suggestionId) {
        ProductSuggestion suggestion = suggestions.findByIdForUpdate(suggestionId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Sugestão não encontrada."));
        if (!suggestion.isPending()) {
            throw new ApiException(HttpStatus.CONFLICT, "Essa sugestão já foi analisada.");
        }
        return suggestion;
    }
}
