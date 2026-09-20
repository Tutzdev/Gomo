package br.com.supermercados.prices.admin;

import br.com.supermercados.prices.auth.AuthenticatedUser;
import br.com.supermercados.prices.common.PageRequests;
import br.com.supermercados.prices.common.PageResponse;
import br.com.supermercados.prices.datasource.DataSourceResponse;
import br.com.supermercados.prices.datasource.SourceRegistration;
import br.com.supermercados.prices.datasource.UpdateDataSourceRequest;
import br.com.supermercados.prices.price.PriceObservation;
import br.com.supermercados.prices.price.PriceRecordResponse;
import br.com.supermercados.prices.product.ProductObservation;
import br.com.supermercados.prices.product.ProductResponse;
import br.com.supermercados.prices.store.ChainObservation;
import br.com.supermercados.prices.store.ChainResponse;
import br.com.supermercados.prices.store.StoreObservation;
import br.com.supermercados.prices.store.StoreResponse;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.UUID;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin")
public class AdminController {

    private final AdminCatalogService catalog;
    private final AdminAuditService audit;

    public AdminController(AdminCatalogService catalog, AdminAuditService audit) {
        this.catalog = catalog;
        this.audit = audit;
    }

    @PostMapping("/sources")
    public ResponseEntity<DataSourceResponse> registerSource(
            @AuthenticationPrincipal AuthenticatedUser actor,
            @Valid @RequestBody SourceRegistration request) {
        DataSourceResponse response = catalog.registerSource(actor.id(), request);
        
        return ResponseEntity.created(URI.create("/api/v1/admin/sources/" + response.id())).body(response);
    }

    @PatchMapping("/sources/{id}")
    public DataSourceResponse changeSourceStatus(
            @AuthenticationPrincipal AuthenticatedUser actor,
            @PathVariable UUID id,
            @RequestBody UpdateDataSourceRequest request) {

        return catalog.changeSourceStatus(actor.id(), id, request);
    }

    @PostMapping("/chains")
    public ResponseEntity<ChainResponse> ingestChain(
            @AuthenticationPrincipal AuthenticatedUser actor,
            @Valid @RequestBody ChainObservation request) {
        ChainResponse response = catalog.ingestChain(actor.id(), request);

        return ResponseEntity.created(URI.create("/api/v1/chains/" + response.id())).body(response);
    }

    @PostMapping("/stores")
    public ResponseEntity<StoreResponse> ingestStore(
            @AuthenticationPrincipal AuthenticatedUser actor,
            @Valid @RequestBody StoreObservation request) {
        StoreResponse response = catalog.ingestStore(actor.id(), request);

        return ResponseEntity.created(URI.create("/api/v1/stores/" + response.id())).body(response);
    }

    @PostMapping("/products")
    public ResponseEntity<ProductResponse> ingestProduct(
            @AuthenticationPrincipal AuthenticatedUser actor,
            @Valid @RequestBody ProductObservation request) {
        ProductResponse response = catalog.ingestProduct(actor.id(), request);

        return ResponseEntity.created(URI.create("/api/v1/products/" + response.id())).body(response);
    }

    @PostMapping("/prices")
    public ResponseEntity<PriceRecordResponse> recordPrice(
            @AuthenticationPrincipal AuthenticatedUser actor,
            @Valid @RequestBody PriceObservation request) {
        PriceRecordResponse response = catalog.recordPrice(actor.id(), request);

        return ResponseEntity.created(URI.create("/api/v1/prices/" + response.id())).body(response);
    }

    @GetMapping("/audit")
    public PageResponse<AdminAuditResponse> audit(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Sort sort = Sort.by(Sort.Direction.DESC, "occurredAt", "id");

        return audit.findAll(PageRequests.create(page, size, sort));
    }
}
