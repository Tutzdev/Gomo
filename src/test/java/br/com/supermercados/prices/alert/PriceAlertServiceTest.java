package br.com.supermercados.prices.alert;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.com.supermercados.prices.auth.AuthenticatedUser;
import br.com.supermercados.prices.common.ApiException;
import br.com.supermercados.prices.location.LocationService;
import br.com.supermercados.prices.product.ProductService;
import br.com.supermercados.prices.user.UserRole;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

@ExtendWith(MockitoExtension.class)
class PriceAlertServiceTest {

    private static final Instant NOW = Instant.parse("2026-09-15T12:00:00Z");

    @Mock private PriceAlertRepository alerts;
    @Mock private ProductService products;
    @Mock private LocationService locations;

    private final AuthenticatedUser user = new AuthenticatedUser(UUID.randomUUID(), UserRole.USER, true, true);
    private PriceAlertService service;

    @BeforeEach
    void setUp() {
        service = new PriceAlertService(alerts, products, locations, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void belowAverageAlertIsSavedWithItsPercentageAndNoTargetPrice() {
        when(alerts.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        PriceAlertResponse response = service.create(user,
                CreatePriceAlertRequest.belowAverage(UUID.randomUUID(), UUID.randomUUID(), 15));

        assertThat(response.kind()).isEqualTo(PriceAlertKind.BELOW_AVERAGE);
        assertThat(response.discountPercent()).isEqualTo(15);
        assertThat(response.targetPrice()).isNull();
    }

    @Test
    void requestWithoutAKindKeepsBeingATargetPriceAlert() {
        when(alerts.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        PriceAlertResponse response = service.create(user, new CreatePriceAlertRequest(UUID.randomUUID(),
                UUID.randomUUID(), null, new BigDecimal("9.90"), null));

        assertThat(response.kind()).isEqualTo(PriceAlertKind.TARGET_PRICE);
        assertThat(response.targetPrice()).isEqualByComparingTo("9.90");
    }

    @Test
    void alertMixingAPriceAndAPercentageIsRejectedBeforeAnyLookup() {
        var mixed = new CreatePriceAlertRequest(UUID.randomUUID(), UUID.randomUUID(),
                PriceAlertKind.BELOW_AVERAGE, new BigDecimal("9.90"), 10);
        var missingPrice = new CreatePriceAlertRequest(UUID.randomUUID(), UUID.randomUUID(),
                PriceAlertKind.TARGET_PRICE, null, null);

        assertThatThrownBy(() -> service.create(user, mixed))
                .isInstanceOfSatisfying(ApiException.class,
                        error -> assertThat(error.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST));
        assertThatThrownBy(() -> service.create(user, missingPrice))
                .hasMessage("Informe o preço desejado.");
        verify(products, never()).requireProduct(any());
        verify(alerts, never()).save(any());
    }
}
