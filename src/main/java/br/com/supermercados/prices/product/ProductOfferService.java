package br.com.supermercados.prices.product;

import java.time.Clock;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.com.supermercados.prices.common.ApiException;
import br.com.supermercados.prices.comparison.ProductStoreComparison;
import br.com.supermercados.prices.price.PricePolicy;
import br.com.supermercados.prices.comparison.EquivalentPriceService;
import br.com.supermercados.prices.store.StoreService;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ProductOfferService {

    private final ProductRepository products;
    private final StoreService stores;
    private final EquivalentPriceService prices;
    private final PricePolicy policy;
    private final Clock clock;

    public List<ProductOffers> findOffers(List<UUID> productIds, UUID cityId) {
        return findOffers(productIds, cityId, null);
    }

    public List<ProductOffers> findOffers(List<UUID> productIds, UUID cityId, List<UUID> storeIds) {
        if (productIds.isEmpty() || productIds.size() > 100) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Consulte entre 1 e 100 produtos por página");
        }
        var catalog = products.findAllById(productIds).stream().collect(Collectors.toMap(Product::getId, product -> product));
        var markets = stores.selectComparisonStores(cityId, storeIds, 5000).stream().collect(Collectors.toMap(store -> store.id(), store -> store));
        Map<UUID, List<ProductStoreComparison>> offers = new HashMap<>();
        if (!catalog.isEmpty() && !markets.isEmpty()) {
            var now = clock.instant();
            var equivalentOffers = prices.find(productIds, List.copyOf(markets.keySet()), now);
            for (var storeEntry : equivalentOffers.byStore().entrySet()) {
                var store = markets.get(storeEntry.getKey());
                for (var productEntry : storeEntry.getValue().entrySet()) {
                    var record = productEntry.getValue();
                    var quote = policy.quote(record, now);
                    if (quote.unitPrice() == null) continue;
                    var product = catalog.get(productEntry.getKey());
                    var matched = equivalentOffers.matchedProduct(product.getId(), record);
                    offers.computeIfAbsent(product.getId(), ignored -> new java.util.ArrayList<>())
                            .add(new ProductStoreComparison(store.id(), store.name(), quote,
                                    equivalentOffers.measurementPrice(product.getId(), record, quote.unitPrice()),
                                    matched == null ? null : ProductResponse.from(matched), store.priceSourceNote()));
                }
            }
        }

        return productIds.stream().distinct().map(id -> new ProductOffers(id,
                offers.getOrDefault(id, List.of()).stream()
                        .sorted(Comparator.comparing((ProductStoreComparison offer) -> offer.price().unitPrice())
                                .thenComparing(ProductStoreComparison::storeName)).toList())).toList();
    }
}
