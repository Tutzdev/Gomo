package br.com.supermercados.prices.alert;

import br.com.supermercados.prices.auth.AuthenticatedUser;
import br.com.supermercados.prices.common.ApiException;
import br.com.supermercados.prices.common.PageResponse;
import br.com.supermercados.prices.location.LocationService;
import br.com.supermercados.prices.product.ProductService;
import br.com.supermercados.prices.subscription.FreePlan;
import br.com.supermercados.prices.subscription.PlanLimit;
import br.com.supermercados.prices.subscription.PlanLimitException;
import java.time.Clock;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PriceAlertService {

    private final PriceAlertRepository alerts;
    private final ProductService products;
    private final LocationService locations;
    private final Clock clock;

    public PriceAlertService(PriceAlertRepository alerts, ProductService products,
            LocationService locations, Clock clock) {
        this.alerts = alerts;
        this.products = products;
        this.locations = locations;
        this.clock = clock;
    }

    @Transactional
    public PriceAlertResponse create(AuthenticatedUser user, CreatePriceAlertRequest request) {
        if (!user.emailVerified()) {
            throw new ApiException(HttpStatus.FORBIDDEN, "Confirme o e-mail antes de criar alertas.");}
        if (!user.premium() && alerts.countByUserIdAndActiveTrue(user.id()) >= FreePlan.MAX_ACTIVE_ALERTS) {
            throw new PlanLimitException(PlanLimit.ACTIVE_ALERTS,
                    "No plano grátis você mantém até 2 alertas ativos. Desative um ou assine o Premium.");
        }

        products.requireProduct(request.productId());
        locations.requireCity(request.cityId());

        return PriceAlertResponse.from(alerts.save(new PriceAlert(user.id(), request, clock.instant())));
    }

    @Transactional(readOnly = true)
    public long countActive(UUID userId) {
        return alerts.countByUserIdAndActiveTrue(userId);
    }

    @Transactional(readOnly = true)
    public PageResponse<PriceAlertResponse> findMine(UUID userId, Pageable pageable) {
        return PageResponse.from(alerts.findByUserId(userId, pageable).map(PriceAlertResponse::from));
    }

    @Transactional
    public PriceAlertResponse deactivate(UUID userId, UUID alertId) {
        PriceAlert alert = alerts.findByIdAndUserId(alertId, userId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Alerta não encontrado."));
        alert.deactivate(clock.instant());

        return PriceAlertResponse.from(alert);
    }
}
