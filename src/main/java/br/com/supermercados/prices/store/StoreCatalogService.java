package br.com.supermercados.prices.store;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.com.supermercados.prices.price.PricePolicy;
import br.com.supermercados.prices.price.PriceRecord;
import br.com.supermercados.prices.price.PriceRecordRepository;
import br.com.supermercados.prices.product.ProductResponse;
import br.com.supermercados.prices.product.ProductSearch;
import br.com.supermercados.prices.product.ProductService;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class StoreCatalogService {

    private final StoreService stores;
    private final ProductService products;
    private final PriceRecordRepository prices;
    private final PricePolicy pricePolicy;
    private final Clock clock;

    public Page<StoreProductResponse> findProducts(UUID storeId, ProductSearch search, Pageable pageable) {
        stores.requireStore(storeId);
        Page<ProductResponse> page = products.searchInStore(storeId, search, pageable);

        if (page.isEmpty()) {return page.map(product -> new StoreProductResponse(product, null));}

        List<UUID> productIds = page.stream().map(ProductResponse::id).toList();

        Map<UUID, PriceRecord> observations = prices.findLatestForStoresAndProducts(List.of(storeId), productIds)
                .stream().collect(Collectors.toMap(PriceRecord::getProductId, Function.identity()));

        Instant now = clock.instant();

        return page.map(product -> new StoreProductResponse(product,
                pricePolicy.quote(observations.get(product.id()), now)));
    }
}
