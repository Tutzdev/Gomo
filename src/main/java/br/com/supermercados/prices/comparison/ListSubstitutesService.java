package br.com.supermercados.prices.comparison;

import br.com.supermercados.prices.catalog.CatalogItem;
import br.com.supermercados.prices.catalog.CatalogItemRepository;
import br.com.supermercados.prices.location.LocationService;
import br.com.supermercados.prices.price.PricePolicy;
import br.com.supermercados.prices.price.PriceRecord;
import br.com.supermercados.prices.price.PriceRecordRepository;
import br.com.supermercados.prices.shoppinglist.ShoppingListItemResponse;
import br.com.supermercados.prices.shoppinglist.ShoppingListService;
import br.com.supermercados.prices.store.StoreResponse;
import br.com.supermercados.prices.store.StoreService;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * "Troque por um equivalente mais barato": for each generic item of a list, the item of the same {@link ItemKind}
 * and aisle with the lowest current price per measure in the city, when it is at least
 * {@value #MINIMUM_SAVINGS_PERCENT}% cheaper that way. Conservative on purpose: same words apart from the brand,
 * same kind of measure and a package between half and twice the size, so it never suggests parboiled rice for
 * white rice or a 5 kg sack for a 1 kg pack.
 */
@Service
@Transactional(readOnly = true)
public class ListSubstitutesService {

    static final int MINIMUM_SAVINGS_PERCENT = 10;
    private static final BigDecimal SMALLEST_SIZE = new BigDecimal("0.5");
    private static final BigDecimal LARGEST_SIZE = new BigDecimal("2");
    private static final int MAXIMUM_STORES = 500;

    private final ShoppingListService shoppingLists;
    private final LocationService locations;
    private final StoreService stores;
    private final CatalogItemRepository catalog;
    private final PriceRecordRepository prices;
    private final PricePolicy policy;
    private final Clock clock;

    public ListSubstitutesService(ShoppingListService shoppingLists, LocationService locations, StoreService stores,
            CatalogItemRepository catalog, PriceRecordRepository prices, PricePolicy policy, Clock clock) {
        this.shoppingLists = shoppingLists;
        this.locations = locations;
        this.stores = stores;
        this.catalog = catalog;
        this.prices = prices;
        this.policy = policy;
        this.clock = clock;
    }

    public ListSubstitutesResponse find(UUID userId, UUID listId, UUID cityId, boolean premium) {
        var list = shoppingLists.getOwnedList(userId, listId);
        locations.requireCity(cityId);
        Map<UUID, CatalogItem> listed = catalog.findAllById(list.items().stream()
                        .map(ShoppingListItemResponse::catalogItemId).filter(Objects::nonNull).distinct().toList())
                .stream().collect(Collectors.toMap(CatalogItem::getId, Function.identity()));
        Set<String> categories = listed.values().stream().map(CatalogItem::getCategory).filter(Objects::nonNull)
                .collect(Collectors.toSet());
        List<StoreResponse> cityStores = stores.selectComparisonStores(cityId, null, MAXIMUM_STORES);
        if (listed.isEmpty() || categories.isEmpty() || cityStores.isEmpty()) {
            return new ListSubstitutesResponse(listId, cityId, !premium, 0, List.of());
        }

        // Candidates: same aisle and same kind as some item of the list, not already in it.
        Map<UUID, ItemKind> kinds = new HashMap<>();
        listed.values().forEach(item -> ItemKind.of(item.getCatalogKey(), item.getBrand())
                .ifPresent(kind -> kinds.put(item.getId(), kind)));
        List<CatalogItem> candidates = catalog.findByActiveTrueAndCategoryIn(categories).stream()
                .filter(candidate -> !listed.containsKey(candidate.getId()))
                .filter(candidate -> ItemKind.of(candidate.getCatalogKey(), candidate.getBrand())
                        .filter(kind -> kinds.values().stream().anyMatch(kind::sameKindAs)).isPresent())
                .toList();
        candidates.forEach(candidate -> ItemKind.of(candidate.getCatalogKey(), candidate.getBrand())
                .ifPresent(kind -> kinds.put(candidate.getId(), kind)));
        Map<UUID, String> storeNames = cityStores.stream()
                .collect(Collectors.toMap(StoreResponse::id, StoreResponse::name));
        Map<UUID, Offer> cheapest = cheapestOffers(
                Stream.concat(listed.keySet().stream(), candidates.stream().map(CatalogItem::getId))
                        .toList(), storeNames.keySet());

        List<ListSubstitutesResponse.Substitution> substitutions = new ArrayList<>();
        for (ShoppingListItemResponse item : list.items()) {
            ItemKind kind = item.catalogItemId() == null ? null : kinds.get(item.catalogItemId());
            Offer current = item.catalogItemId() == null ? null : cheapest.get(item.catalogItemId());
            CatalogItem original = item.catalogItemId() == null ? null : listed.get(item.catalogItemId());
            if (kind == null || current == null || original == null) {
                continue;
            }
            BigDecimal perMeasure = kind.pricePerMeasure(current.price());
            BigDecimal limit = perMeasure.multiply(BigDecimal.valueOf(100 - MINIMUM_SAVINGS_PERCENT)).movePointLeft(2);
            candidates.stream()
                    .filter(candidate -> Objects.equals(candidate.getCategory(), original.getCategory()))
                    .filter(candidate -> kinds.get(candidate.getId()).sameKindAs(kind))
                    .filter(candidate -> withinSize(kinds.get(candidate.getId()).sizeAgainst(kind)))
                    .filter(candidate -> cheapest.containsKey(candidate.getId()))
                    .map(candidate -> new Option(candidate, cheapest.get(candidate.getId()),
                            kinds.get(candidate.getId()).pricePerMeasure(cheapest.get(candidate.getId()).price())))
                    .filter(option -> option.perMeasure().compareTo(limit) <= 0)
                    .min(Comparator.comparing(Option::perMeasure))
                    .ifPresent(option -> substitutions.add(substitution(item, kind, current, perMeasure, option,
                            kinds.get(option.item().getId()), storeNames)));
        }
        substitutions.sort(Comparator.comparing(ListSubstitutesResponse.Substitution::savingsPercent).reversed());
        return new ListSubstitutesResponse(listId, cityId, !premium, substitutions.size(),
                premium ? substitutions : List.of());
    }

    private static boolean withinSize(BigDecimal ratio) {
        return ratio.compareTo(SMALLEST_SIZE) >= 0 && ratio.compareTo(LARGEST_SIZE) <= 0;
    }

    private static ListSubstitutesResponse.Substitution substitution(ShoppingListItemResponse item, ItemKind kind,
            Offer current, BigDecimal perMeasure, Option option, ItemKind substituteKind, Map<UUID, String> storeNames) {
        // About the same amount as the list asks for: 2 × 1 kg becomes 1 × 2 kg, never less than one package.
        BigDecimal suggested = item.quantity().divide(substituteKind.sizeAgainst(kind), 0, RoundingMode.HALF_UP)
                .max(BigDecimal.ONE);
        int savings = perMeasure.subtract(option.perMeasure()).movePointRight(2)
                .divide(perMeasure, 0, RoundingMode.HALF_UP).intValue();
        CatalogItem substitute = option.item();
        return new ListSubstitutesResponse.Substitution(item.id(), item.productName(), item.quantity(),
                current.price(), perMeasure.setScale(2, RoundingMode.HALF_UP), kind.measureUnit(), substitute.getId(),
                substitute.getDisplayName(), substitute.getBrand(), substitute.getSizeLabel(), substitute.getImageUrl(),
                option.offer().price(), option.perMeasure().setScale(2, RoundingMode.HALF_UP), option.offer().storeId(),
                storeNames.get(option.offer().storeId()), suggested, savings);
    }

    /** The cheapest current offer of each generic item among the city's stores. */
    private Map<UUID, Offer> cheapestOffers(List<UUID> itemIds, Set<UUID> storeIds) {
        Map<UUID, List<UUID>> productsByItem = new HashMap<>();
        for (Object[] link : catalog.findLinks(itemIds)) {
            productsByItem.computeIfAbsent(UUID.fromString((String) link[0]), ignored -> new ArrayList<>())
                    .add(UUID.fromString((String) link[1]));
        }
        Map<UUID, UUID> itemByProduct = new HashMap<>();
        productsByItem.forEach((item, products) -> products.forEach(product -> itemByProduct.put(product, item)));
        if (itemByProduct.isEmpty()) {
            return Map.of();
        }
        Instant now = clock.instant();
        Map<UUID, Offer> cheapest = new HashMap<>();
        for (PriceRecord record : prices.findLatestForStoresAndProducts(storeIds, itemByProduct.keySet())) {
            BigDecimal price = policy.quote(record, now).unitPrice();
            if (price != null) {
                cheapest.merge(itemByProduct.get(record.getProductId()), new Offer(price, record.getStoreId()),
                        (first, second) -> second.price().compareTo(first.price()) < 0 ? second : first);
            }
        }
        return cheapest;
    }

    private record Offer(BigDecimal price, UUID storeId) {
    }

    private record Option(CatalogItem item, Offer offer, BigDecimal perMeasure) {
    }
}
