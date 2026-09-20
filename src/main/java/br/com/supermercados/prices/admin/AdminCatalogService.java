package br.com.supermercados.prices.admin;

import br.com.supermercados.prices.datasource.DataSource;
import br.com.supermercados.prices.datasource.DataSourceResponse;
import br.com.supermercados.prices.datasource.DataSourceService;
import br.com.supermercados.prices.datasource.SourceRegistration;
import br.com.supermercados.prices.datasource.UpdateDataSourceRequest;
import br.com.supermercados.prices.price.PriceObservation;
import br.com.supermercados.prices.price.PriceRecordResponse;
import br.com.supermercados.prices.price.PriceService;
import br.com.supermercados.prices.product.ProductIngestionService;
import br.com.supermercados.prices.product.ProductObservation;
import br.com.supermercados.prices.product.ProductResponse;
import br.com.supermercados.prices.store.ChainObservation;
import br.com.supermercados.prices.store.ChainResponse;
import br.com.supermercados.prices.store.StoreIngestionService;
import br.com.supermercados.prices.store.StoreObservation;
import br.com.supermercados.prices.store.StoreResponse;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AdminCatalogService {

    private final DataSourceService sources;
    private final StoreIngestionService stores;
    private final ProductIngestionService products;
    private final PriceService prices;
    private final AdminAuditService audit;

    public AdminCatalogService(DataSourceService sources, StoreIngestionService stores,
            ProductIngestionService products, PriceService prices, AdminAuditService audit) {
        this.sources = sources;
        this.stores = stores;
        this.products = products;
        this.prices = prices;
        this.audit = audit;
    }

    @Transactional
    public DataSourceResponse registerSource(UUID actorUserId, SourceRegistration registration) {
        DataSource source = sources.registerVerifiedSource(registration);
        audit.record(actorUserId, AdminAction.SOURCE_REGISTERED, "DATA_SOURCE", source.getId());

        return DataSourceResponse.from(source);
    }

    @Transactional
    public DataSourceResponse changeSourceStatus(
            UUID actorUserId, UUID sourceId, UpdateDataSourceRequest request) {
        DataSourceResponse response = sources.changeEnabled(sourceId, request.enabled());
        audit.record(actorUserId, AdminAction.SOURCE_STATUS_CHANGED, "DATA_SOURCE", response.id());

        return response;
    }

    @Transactional
    public ChainResponse ingestChain(UUID actorUserId, ChainObservation observation) {
        ChainResponse response = stores.ingestChain(observation);
        audit.record(actorUserId, AdminAction.CHAIN_INGESTED, "SUPERMARKET_CHAIN", response.id());

        return response;
    }

    @Transactional
    public StoreResponse ingestStore(UUID actorUserId, StoreObservation observation) {
        StoreResponse response = stores.ingestStore(observation);
        audit.record(actorUserId, AdminAction.STORE_INGESTED, "STORE", response.id());

        return response;
    }

    @Transactional
    public ProductResponse ingestProduct(UUID actorUserId, ProductObservation observation) {
        ProductResponse response = products.ingest(observation);
        audit.record(actorUserId, AdminAction.PRODUCT_INGESTED, "PRODUCT", response.id());

        return response;
    }

    @Transactional
    public PriceRecordResponse recordPrice(UUID actorUserId, PriceObservation observation) {
        PriceRecordResponse response = prices.appendObservation(observation);
        audit.record(actorUserId, AdminAction.PRICE_RECORDED, "PRICE_RECORD", response.id());
        
        return response;
    }
}
