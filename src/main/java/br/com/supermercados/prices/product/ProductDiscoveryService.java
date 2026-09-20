package br.com.supermercados.prices.product;

import java.math.BigDecimal;
import java.time.Clock;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.com.supermercados.prices.comparison.EquivalentPriceService;
import br.com.supermercados.prices.comparison.ProductStoreComparison;
import br.com.supermercados.prices.price.PricePolicy;
import br.com.supermercados.prices.store.StoreResponse;
import br.com.supermercados.prices.store.StoreService;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ProductDiscoveryService {

    private final ProductSearchRepository search;
    private final ProductEquivalenceService equivalence;
    private final ProductComparisonEvidence evidence;
    private final EquivalentPriceService prices;
    private final StoreService stores;
    private final PricePolicy policy;
    private final Clock clock;

    public Page<ProductDiscoveryResult> search(ProductSearch filters, UUID cityId, List<UUID> storeIds,
            Pageable pageable) {
        List<StoreResponse> selectedStores = cityId == null ? List.of()
                : stores.selectComparisonStores(cityId, storeIds, 5000);
        List<Product> candidates = search.findCandidates(filters);
        var matches = equivalence.findConfirmed(candidates.stream().map(Product::getId).toList());
        Map<UUID, Product> representatives = new LinkedHashMap<>();
        for (Product candidate : candidates) {
            var match = matches.get(candidate.getId());
            UUID groupId = match.confirmed().stream().map(Product::getId).min(UUID::compareTo).orElseThrow();
            representatives.putIfAbsent(groupId, candidate);
        }
        Map<UUID, ProductEquivalenceService.Matches> groups = new LinkedHashMap<>();
        representatives.values().forEach(product -> groups.put(product.getId(), matches.get(product.getId())));
        var now = clock.instant();
        var observations = prices.find(groups, selectedStores.stream().map(StoreResponse::id).toList(), now);
        Map<UUID, StoreResponse> byStore = selectedStores.stream().collect(Collectors.toMap(StoreResponse::id, store -> store));
        Map<UUID, List<ProductStoreComparison>> offers = new HashMap<>();
        observations.byStore().forEach((storeId, records) -> records.forEach((productId, record) -> {
            var quote = policy.quote(record, now);
            if (quote.unitPrice() == null) return;
            Product matched = observations.matchedProduct(productId, record);
            var store = byStore.get(storeId);
            offers.computeIfAbsent(productId, ignored -> new ArrayList<>()).add(new ProductStoreComparison(
                    storeId, store.name(), quote,
                    observations.measurementPrice(productId, record, quote.unitPrice()),
                    ProductResponse.from(matched), store.priceSourceNote()));
        }));
        List<ProductDiscoveryResult> results = representatives.values().stream().map(product -> {
            List<ProductStoreComparison> productOffers = offers.getOrDefault(product.getId(), List.of()).stream()
                    .sorted(Comparator.comparing((ProductStoreComparison offer) -> offer.price().unitPrice())
                            .thenComparing(ProductStoreComparison::storeName)).toList();
            return new ProductDiscoveryResult(ProductResponse.from(product), productOffers,
                    groups.get(product.getId()).confirmed().size(), groups.get(product.getId()).identityConfirmed(),
                    evidence.displayName(product));
        }).sorted(order(filters)).toList();
        int first = (int) Math.min(pageable.getOffset(), results.size());
        int last = Math.min(first + pageable.getPageSize(), results.size());
        return new PageImpl<>(results.subList(first, last), pageable, results.size());
    }

    private Comparator<ProductDiscoveryResult> order(ProductSearch filters) {
        if ("name".equals(filters.sort())) return Comparator.comparing(result -> result.product().name());
        Comparator<ProductDiscoveryResult> pricedFirst = Comparator.comparing(result -> result.offers().isEmpty());
        if (filters.sort().startsWith("price_")) {
            Comparator<BigDecimal> priceOrder = "price_desc".equals(filters.sort())
                    ? Comparator.reverseOrder() : Comparator.naturalOrder();
            return pricedFirst.thenComparing(result -> result.offers().isEmpty() ? null
                    : result.offers().getFirst().price().unitPrice(), Comparator.nullsLast(priceOrder));
        }
        ProductSearchTerms terms = ProductSearchTerms.parse(filters.query());
        // Keep the requested size ahead of other sizes, then prefer groups with usable local prices.
        return Comparator.comparing((ProductDiscoveryResult result) -> !matchesSize(result.product(), terms))
                .thenComparing(pricedFirst).thenComparing(result -> !result.identityConfirmed());
    }

    private boolean matchesSize(ProductResponse product, ProductSearchTerms terms) {
        return terms.quantity() == null || terms.unit().equals(product.unit())
                && product.quantity() != null && terms.quantity().compareTo(product.quantity()) == 0;
    }
}
