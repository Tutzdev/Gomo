package br.com.supermercados.prices.alert;

import br.com.supermercados.prices.auth.AuthenticatedUser;
import br.com.supermercados.prices.common.ApiException;
import br.com.supermercados.prices.common.PageResponse;
import br.com.supermercados.prices.location.LocationService;
import br.com.supermercados.prices.product.ProductService;
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
            
        products.requireProduct(request.productId());
        locations.requireCity(request.cityId());

        return PriceAlertResponse.from(alerts.save(new PriceAlert(user.id(), request, clock.instant())));
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
