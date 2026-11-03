package br.com.supermercados.prices.suggestion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.com.supermercados.prices.admin.AdminAction;
import br.com.supermercados.prices.admin.AdminAuditService;
import br.com.supermercados.prices.auth.AuthenticatedUser;
import br.com.supermercados.prices.user.UserRole;
import br.com.supermercados.prices.common.ApiException;
import br.com.supermercados.prices.contribution.ContributionStatus;
import br.com.supermercados.prices.store.StoreService;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ProductSuggestionServiceTest {

    private static final Instant NOW = Instant.parse("2026-11-03T12:00:00Z");

    @Mock private ProductSuggestionRepository suggestions;
    @Mock private StoreService stores;
    @Mock private AdminAuditService audit;

    private ProductSuggestionService service() {
        return new ProductSuggestionService(suggestions, stores, audit, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private static AuthenticatedUser shopper(boolean verified) {
        return new AuthenticatedUser(UUID.randomUUID(), UserRole.USER, verified, false);
    }

    private static SubmitProductSuggestionRequest request(UUID storeId, String price) {
        return new SubmitProductSuggestionRequest("  Biscoito de polvilho Globo  ", "Globo", "35 g", "", storeId,
                price == null ? null : new BigDecimal(price), null);
    }

    @Test
    void aSuggestionWaitsForReviewWithTheTextTrimmed() {
        UUID store = UUID.randomUUID();
        when(suggestions.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        ProductSuggestionResponse response = service().submit(shopper(true), request(store, "4.99"));

        assertThat(response.status()).isEqualTo(ContributionStatus.PENDING);
        assertThat(response.name()).isEqualTo("Biscoito de polvilho Globo");
        assertThat(response.barcode()).isNull();
        verify(stores).requireActiveStore(store);
    }

    @Test
    void anUnverifiedAccountOrAPriceWithoutMarketIsRefused() {
        assertThatThrownBy(() -> service().submit(shopper(false), request(null, null)))
                .isInstanceOf(ApiException.class).hasMessageContaining("Confirme o e-mail");
        assertThatThrownBy(() -> service().submit(shopper(true), request(null, "4.99")))
                .isInstanceOf(ApiException.class).hasMessageContaining("mercado");
        verify(suggestions, never()).save(any());
    }

    @Test
    void aReviewedSuggestionCannotBeDecidedAgain() {
        UUID moderator = UUID.randomUUID();
        ProductSuggestion suggestion = new ProductSuggestion(UUID.randomUUID(), request(null, null), NOW);
        when(suggestions.findByIdForUpdate(suggestion.getId())).thenReturn(Optional.of(suggestion));

        ProductSuggestionResponse approved = service().approve(moderator, suggestion.getId());

        assertThat(approved.status()).isEqualTo(ContributionStatus.APPROVED);
        assertThat(approved.decidedAt()).isEqualTo(NOW);
        verify(audit).record(moderator, AdminAction.PRODUCT_SUGGESTION_APPROVED, "PRODUCT_SUGGESTION", suggestion.getId());
        assertThatThrownBy(() -> service().reject(moderator, suggestion.getId(), "Duplicado"))
                .isInstanceOf(ApiException.class).hasMessageContaining("já foi analisada");
    }
}
