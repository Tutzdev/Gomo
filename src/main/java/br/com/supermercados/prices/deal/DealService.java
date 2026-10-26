package br.com.supermercados.prices.deal;

import br.com.supermercados.prices.auth.AuthenticatedUser;
import br.com.supermercados.prices.catalog.CatalogItem;
import br.com.supermercados.prices.catalog.CatalogItemRepository;
import br.com.supermercados.prices.common.PageResponse;
import br.com.supermercados.prices.location.LocationService;
import br.com.supermercados.prices.shoppinglist.ShoppingListService;
import br.com.supermercados.prices.store.StoreResponse;
import br.com.supermercados.prices.store.StoreService;
import br.com.supermercados.prices.subscription.PlanLimit;
import br.com.supermercados.prices.subscription.PlanLimitException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The deals page: what is cheaper than usual in the city right now. Public, so it is computed once per city and
 * kept for half an hour ({@code app.deals.cache-ttl}); prices change four times a day, a page a few minutes old says the same thing.
 * Premium can narrow it to the products of their own lists.
 */
@Service
public class DealService {

    private static final int MAXIMUM_STORES = 500;

    private final DealFinder finder;
    private final StoreService stores;
    private final LocationService locations;
    private final CatalogItemRepository items;
    private final ShoppingListService lists;
    private final Clock clock;
    private final Duration cacheTtl;
    private final Map<UUID, CityDeals> cache = new ConcurrentHashMap<>();

    public DealService(DealFinder finder, StoreService stores, LocationService locations, CatalogItemRepository items,
            ShoppingListService lists, Clock clock, @Value("${app.deals.cache-ttl:PT30M}") Duration cacheTtl) {
        this.finder = finder;
        this.stores = stores;
        this.locations = locations;
        this.items = items;
        this.lists = lists;
        this.clock = clock;
        this.cacheTtl = cacheTtl;
    }

    @Transactional(readOnly = true)
    public DealsResponse find(AuthenticatedUser user, UUID cityId, UUID storeId, String category,
            boolean onlyMyLists, Pageable pageable) {
        locations.requireCity(cityId);
        if (onlyMyLists && !AuthenticatedUser.hasPremium(user)) {
            throw new PlanLimitException(PlanLimit.DEALS_FOR_MY_LISTS,
                    "Ver só as ofertas dos produtos das suas listas faz parte do Premium.");
        }
        CityDeals city = cityDeals(cityId);
        Set<UUID> mine = onlyMyLists ? lists.catalogItemIdsInLists(user.id()) : null;
        List<DealResponse> scope = city.deals().stream()
                .filter(deal -> mine == null || mine.contains(deal.itemId()))
                .toList();

        List<DealResponse> view = storeId == null ? cheapestPerItem(scope)
                : scope.stream().filter(deal -> deal.storeId().equals(storeId)).toList();
        List<DealsResponse.CategoryDeals> categories = view.stream()
                .filter(deal -> deal.category() != null)
                .collect(Collectors.groupingBy(DealResponse::category, Collectors.counting()))
                .entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed().thenComparing(Map.Entry.comparingByKey()))
                .map(entry -> new DealsResponse.CategoryDeals(entry.getKey(), entry.getValue().intValue()))
                .toList();
        List<DealsResponse.StoreDeals> storeCounts = scope.stream()
                .collect(Collectors.groupingBy(DealResponse::storeId, LinkedHashMap::new, Collectors.toList()))
                .values().stream()
                .map(deals -> new DealsResponse.StoreDeals(deals.getFirst().storeId(), deals.getFirst().storeName(),
                        deals.size()))
                .sorted(Comparator.comparing(DealsResponse.StoreDeals::deals).reversed()
                        .thenComparing(DealsResponse.StoreDeals::storeName, String.CASE_INSENSITIVE_ORDER))
                .toList();

        List<DealResponse> filtered = category == null || category.isBlank() ? view
                : view.stream().filter(deal -> category.equals(deal.category())).toList();
        int from = (int) Math.min(pageable.getOffset(), filtered.size());
        int to = Math.min(from + pageable.getPageSize(), filtered.size());
        var page = new PageImpl<>(filtered.subList(from, to), pageable, filtered.size());
        return new DealsResponse(cityId, city.computedAt(), DealFinder.MINIMUM_DISCOUNT_PERCENT,
                PageResponse.from(page), storeCounts, categories, onlyMyLists);
    }

    /** The deals of the city, computed at most once per {@code cacheTtl}. */
    private CityDeals cityDeals(UUID cityId) {
        Instant now = clock.instant();
        CityDeals cached = cache.get(cityId);
        if (cached != null && cached.computedAt().isAfter(now.minus(cacheTtl))) {
            return cached;
        }
        CityDeals computed = compute(cityId, now);
        cache.put(cityId, computed);
        return computed;
    }

    private CityDeals compute(UUID cityId, Instant now) {
        Map<UUID, StoreResponse> cityStores = stores.selectComparisonStores(cityId, null, MAXIMUM_STORES).stream()
                .collect(Collectors.toMap(StoreResponse::id, Function.identity()));
        List<DealFinder.Offer> offers = finder.find(cityStores.keySet(), now);
        Map<UUID, CatalogItem> byId = items.findAllById(offers.stream().map(DealFinder.Offer::itemId).distinct().toList())
                .stream().collect(Collectors.toMap(CatalogItem::getId, Function.identity()));
        List<DealResponse> deals = offers.stream()
                .filter(offer -> byId.containsKey(offer.itemId()))
                .map(offer -> {
                    CatalogItem item = byId.get(offer.itemId());
                    return new DealResponse(item.getId(), item.getDisplayName(), item.getBrand(), item.getSizeLabel(),
                            item.getCategory(), item.getImageUrl(), offer.storeId(),
                            cityStores.get(offer.storeId()).name(), offer.price(), offer.usualPrice(),
                            offer.discountPercent(), offer.promotion(), offer.lowestIn90Days(), offer.collectedAt());
                })
                .toList();
        return new CityDeals(now, deals);
    }

    /** Each product once, at its cheapest store, keeping the deepest discounts first. */
    private static List<DealResponse> cheapestPerItem(List<DealResponse> deals) {
        Map<UUID, DealResponse> cheapest = new LinkedHashMap<>();
        for (DealResponse deal : deals) {
            cheapest.merge(deal.itemId(), deal,
                    (current, next) -> next.price().compareTo(current.price()) < 0 ? next : current);
        }
        return cheapest.values().stream()
                .sorted(Comparator.comparing(DealResponse::discountPercent).reversed()
                        .thenComparing(DealResponse::name, String.CASE_INSENSITIVE_ORDER)
                        .thenComparing(deal -> Objects.toString(deal.storeName())))
                .toList();
    }

    private record CityDeals(Instant computedAt, List<DealResponse> deals) {
    }
}
