package br.com.supermercados.prices.comparison;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.com.supermercados.prices.price.PricePolicy;
import br.com.supermercados.prices.price.MeasurementPrice;
import br.com.supermercados.prices.price.PriceRecord;
import br.com.supermercados.prices.price.PriceRecordRepository;
import br.com.supermercados.prices.product.Product;
import br.com.supermercados.prices.product.ProductEquivalenceService;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class EquivalentPriceService {

    private final ProductEquivalenceService equivalence;
    private final PriceRecordRepository prices;
    private final PricePolicy policy;

    public Result find(List<UUID> productIds, List<UUID> storeIds, Instant comparedAt) {
        if (productIds.isEmpty() || storeIds.isEmpty()) return new Result(Map.of(), Map.of());
        var matches = equivalence.find(productIds);
        return find(matches, storeIds, comparedAt);
    }

    public Result find(Map<UUID, ProductEquivalenceService.Matches> matches, List<UUID> storeIds, Instant comparedAt) {
        if (storeIds.isEmpty()) return new Result(Map.of(), matches);
        List<UUID> equivalentIds = matches.values().stream().flatMap(match -> match.confirmed().stream())
                .map(Product::getId).distinct().toList();
        if (equivalentIds.isEmpty()) return new Result(Map.of(), matches);
        Map<UUID, List<UUID>> requestedByCandidate = new HashMap<>();
        matches.forEach((requestedId, match) -> match.confirmed().forEach(candidate ->
                requestedByCandidate.computeIfAbsent(candidate.getId(), ignored -> new java.util.ArrayList<>())
                        .add(requestedId)));
        Map<UUID, Map<UUID, PriceRecord>> byStore = new HashMap<>();
        for (PriceRecord record : prices.findLatestForStoresAndProducts(storeIds, equivalentIds)) {
            Map<UUID, PriceRecord> store = byStore.computeIfAbsent(record.getStoreId(), ignored -> new HashMap<>());
            for (UUID requestedId : requestedByCandidate.get(record.getProductId())) {
                store.merge(requestedId, record, (current, next) -> preferred(current, next, comparedAt));
            }
        }
        return new Result(byStore, matches);
    }

    private PriceRecord preferred(PriceRecord current, PriceRecord next, Instant comparedAt) {
        // A newer collection supersedes an older duplicate, including stock-outs.
        int recency = current.getCollectedAt().compareTo(next.getCollectedAt());
        if (recency != 0) return recency > 0 ? current : next;
        var currentPrice = policy.quote(current, comparedAt).unitPrice();
        var nextPrice = policy.quote(next, comparedAt).unitPrice();
        if (currentPrice != null && nextPrice == null) return current;
        if (nextPrice != null && currentPrice == null) return next;
        if (currentPrice != null && currentPrice.compareTo(nextPrice) != 0) {
            return currentPrice.compareTo(nextPrice) < 0 ? current : next;
        }
        return next.getCollectedAt().isAfter(current.getCollectedAt()) ? next : current;
    }

    public record Result(Map<UUID, Map<UUID, PriceRecord>> byStore,
                         Map<UUID, ProductEquivalenceService.Matches> matches) {

        public MeasurementPrice measurementPrice(UUID requestedId, PriceRecord record, BigDecimal unitPrice) {
            var match = matches.get(requestedId);
            if (match == null || !match.identityConfirmed()) return null;
            var product = matchedProduct(requestedId, record);
            return product == null ? null
                    : MeasurementPrice.calculate(unitPrice, product.getQuantity(), product.getUnit());
        }

        public List<Product> possible(UUID productId) {
            var match = matches.get(productId);
            return match == null ? List.of() : match.possible();
        }

        public Product matchedProduct(UUID requestedId, PriceRecord price) {
            var match = matches.get(requestedId);
            if (price == null || match == null) return null;
            return match.confirmed().stream().filter(product -> product.getId().equals(price.getProductId()))
                    .findFirst().orElse(null);
        }
    }
}
