package br.com.supermercados.prices.storevisit;

import br.com.supermercados.prices.admin.AdminAction;
import br.com.supermercados.prices.admin.AdminAuditService;
import br.com.supermercados.prices.common.ApiException;
import br.com.supermercados.prices.datasource.DataSource;
import br.com.supermercados.prices.datasource.DataSourceService;
import br.com.supermercados.prices.price.PriceObservation;
import br.com.supermercados.prices.price.PricePolicy;
import br.com.supermercados.prices.price.PriceService;
import br.com.supermercados.prices.price.StockAvailability;
import br.com.supermercados.prices.store.Store;
import br.com.supermercados.prices.store.StorePriceChannel;
import br.com.supermercados.prices.store.StoreRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Records the shelf prices of a market without an online store. Only markets registered as
 * {@link StorePriceChannel#IN_STORE} accept them, so a scraped market never mixes in hand-written prices.
 */
@Service
public class StoreVisitService {

    static final String SOURCE_CODE = "in_store_price_check";

    private final StoreRepository stores;
    private final DataSourceService sources;
    private final PriceService prices;
    private final PricePolicy policy;
    private final AdminAuditService audit;
    private final Clock clock;

    public StoreVisitService(StoreRepository stores, DataSourceService sources, PriceService prices,
            PricePolicy policy, AdminAuditService audit, Clock clock) {
        this.stores = stores;
        this.sources = sources;
        this.prices = prices;
        this.policy = policy;
        this.audit = audit;
        this.clock = clock;
    }

    @Transactional
    public StoreVisitResponse record(UUID actorUserId, UUID storeId, StoreVisitRequest visit) {
        Store store = stores.findById(storeId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Loja não encontrada."));
        if (!store.isActive() || store.getPriceChannel() != StorePriceChannel.IN_STORE) {
            throw new ApiException(HttpStatus.CONFLICT,
                    "Só mercados sem loja online recebem preço conferido na loja.");
        }
        Instant now = clock.instant();
        Instant currentUntil = visit.observedAt().plus(policy.inStoreMaxAge());
        if (!currentUntil.isAfter(now)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Essa visita é antiga demais: os preços já estariam vencidos.");
        }
        Set<UUID> seen = new HashSet<>();
        for (StoreVisitRequest.Item item : visit.items()) {
            if (!seen.add(item.productId())) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "O mesmo produto aparece duas vezes na visita.");
            }
        }
        DataSource source = sources.findByCode(SOURCE_CODE)
                .orElseThrow(() -> new IllegalStateException("Fonte " + SOURCE_CODE + " ausente: rode as migrações"));
        for (StoreVisitRequest.Item item : visit.items()) {
            prices.appendStoreVisit(new PriceObservation(item.productId(), storeId, source.getId(),
                    reference(storeId, item.productId(), visit.observedAt()), item.price(), null, "BRL",
                    visit.observedAt(), null, null, null, StockAvailability.AVAILABLE));
        }
        audit.record(actorUserId, AdminAction.STORE_VISIT_RECORDED, "STORE", storeId);
        return new StoreVisitResponse(storeId, visit.observedAt(), visit.items().size(), currentUntil);
    }

    /** One shelf reading per product and visit time: resending the same visit records nothing new. */
    static String reference(UUID storeId, UUID productId, Instant observedAt) {
        return "store-visit:" + storeId + ":" + productId + ":" + observedAt.getEpochSecond();
    }
}
