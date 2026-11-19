package br.com.supermercados.prices.comparison;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.com.supermercados.prices.basicitem.BasicItem;
import br.com.supermercados.prices.basicitem.BasicItemOffer;
import br.com.supermercados.prices.basicitem.BasicItemOffers;
import br.com.supermercados.prices.basicitem.BasicItems;
import br.com.supermercados.prices.price.PricePolicy;
import br.com.supermercados.prices.price.MeasurementPrice;
import br.com.supermercados.prices.price.PriceRecord;
import br.com.supermercados.prices.price.PriceRecordRepository;
import br.com.supermercados.prices.product.Product;
import br.com.supermercados.prices.product.ProductEquivalenceService;
import br.com.supermercados.prices.product.ProductRepository;
import br.com.supermercados.prices.shoppinglist.ShoppingListItemResponse;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class EquivalentPriceService {

    private final ProductEquivalenceService equivalence;
    private final PriceRecordRepository prices;
    private final PricePolicy policy;
    private final BasicItemOffers basicItems;
    private final ProductRepository products;

    public Result findForList(List<ShoppingListItemResponse> items, List<UUID> storeIds, Instant comparedAt) {
        List<UUID> exactIds = items.stream()
                .filter(item -> !item.anyBrand())
                .map(ShoppingListItemResponse::productId)
                .toList();

        Result exact = find(exactIds, storeIds, comparedAt);
        List<ShoppingListItemResponse> anyBrand = items.stream().filter(ShoppingListItemResponse::anyBrand).toList();

        if (anyBrand.isEmpty() || storeIds.isEmpty()) {return exact;}

        List<BasicItem> requested = anyBrand.stream()
                .map(item -> BasicItems.find(item.basicItem()))
                .flatMap(Optional::stream)
                .distinct()
                .toList();

        Map<String, Map<UUID, BasicItemOffer>> cheapest = basicItems.cheapestByStore(requested, storeIds, comparedAt);

        List<UUID> involved = Stream.concat(
                anyBrand.stream().map(ShoppingListItemResponse::productId),
                cheapest.values().stream().flatMap(byStore -> byStore.values().stream()).map(BasicItemOffer::productId))
                .distinct()
                .toList();

        Map<UUID, Product> byId = products.findAllById(involved).stream()
                .collect(Collectors.toMap(Product::getId, Function.identity()));

        return exact.withAnyBrand(anyBrand, cheapest, byId);
    }

    public Result find(List<UUID> productIds, List<UUID> storeIds, Instant comparedAt) {
        if (productIds.isEmpty() || storeIds.isEmpty()) {return new Result(Map.of(), Map.of());}

        var matches = equivalence.find(productIds);

        return find(matches, storeIds, comparedAt);
    }

    public Result find(Map<UUID, ProductEquivalenceService.Matches> matches, List<UUID> storeIds, Instant comparedAt) {
        if (storeIds.isEmpty()) {return new Result(Map.of(), matches);}

        List<UUID> equivalentIds = matches.values().stream().flatMap(match -> match.confirmed().stream())
                .map(Product::getId).distinct().toList();

        if (equivalentIds.isEmpty()) {return new Result(Map.of(), matches);}

        Map<UUID, List<UUID>> requestedByCandidate = new HashMap<>();

        matches.forEach((requestedId, match) -> match.confirmed().forEach(candidate ->
                requestedByCandidate.computeIfAbsent(candidate.getId(), ignored -> new ArrayList<>())
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
        int recency = current.getCollectedAt().compareTo(next.getCollectedAt());

        if (recency != 0) {return recency > 0 ? current : next;}

        var currentPrice = policy.quote(current, comparedAt).unitPrice();
        var nextPrice = policy.quote(next, comparedAt).unitPrice();

        if (currentPrice != null && nextPrice == null) {return current;}

        if (nextPrice != null && currentPrice == null) {return next;}

        if (currentPrice != null && currentPrice.compareTo(nextPrice) != 0) {
            return currentPrice.compareTo(nextPrice) < 0 ? current : next;
        }

        return next.getCollectedAt().isAfter(current.getCollectedAt()) ? next : current;
    }

    public record Result(
            Map<UUID, Map<UUID, PriceRecord>> byStore,
            Map<UUID, ProductEquivalenceService.Matches> matches) {

        public MeasurementPrice measurementPrice(UUID requestedId, PriceRecord record, BigDecimal unitPrice) {
            var match = matches.get(requestedId);

            if (match == null || !match.identityConfirmed()) {return null;}

            var product = matchedProduct(requestedId, record);

            return product == null ? null
                    : MeasurementPrice.calculate(unitPrice, product.getQuantity(), product.getUnit());
        }

        Result withAnyBrand(
            List<ShoppingListItemResponse> anyBrand,
            Map<String, Map<UUID, BasicItemOffer>> cheapest,
            Map<UUID, Product> products) {

            Map<UUID, Map<UUID, PriceRecord>> pricedByStore = new HashMap<>();
            byStore.forEach((storeId, prices) -> pricedByStore.put(storeId, new HashMap<>(prices)));
            Map<UUID, ProductEquivalenceService.Matches> matched = new HashMap<>(matches);

            for (ShoppingListItemResponse item : anyBrand) {
                Map<UUID, BasicItemOffer> offers = cheapest.getOrDefault(item.basicItem(), Map.of());
                List<Product> confirmed = new ArrayList<>();

                Optional.ofNullable(products.get(item.productId())).ifPresent(confirmed::add);

                offers.forEach((storeId, offer) -> {
                    pricedByStore.computeIfAbsent(storeId, ignored -> new HashMap<>())
                            .put(item.productId(), offer.record());

                    Product product = products.get(offer.productId());

                    if (product != null && !confirmed.contains(product)) {confirmed.add(product);}
                });

                matched.put(item.productId(), new ProductEquivalenceService.Matches(List.copyOf(confirmed), List.of()));
            }

            return new Result(pricedByStore, matched);
        }

        public List<Product> possible(UUID productId) {
            var match = matches.get(productId);

            return match == null ? List.of() : match.possible();
        }

        public Product matchedProduct(UUID requestedId, PriceRecord price) {
            var match = matches.get(requestedId);

            if (price == null || match == null) {return null;}

            return match.confirmed().stream().filter(product -> product.getId().equals(price.getProductId()))
                    .findFirst().orElse(null);
        }
    }
}
