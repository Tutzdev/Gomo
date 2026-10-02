package br.com.supermercados.prices.comparison;

import br.com.supermercados.prices.common.ApiException;
import br.com.supermercados.prices.location.LocationService;
import br.com.supermercados.prices.price.PriceFixtures;
import br.com.supermercados.prices.price.PricePolicy;
import br.com.supermercados.prices.price.PriceRecordRepository;
import br.com.supermercados.prices.product.ProductEquivalenceService;
import br.com.supermercados.prices.product.Product;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import java.util.LinkedHashMap;
import br.com.supermercados.prices.price.PriceStatus;
import br.com.supermercados.prices.product.ProductService;
import br.com.supermercados.prices.shoppinglist.ShoppingListItemResponse;
import br.com.supermercados.prices.shoppinglist.ShoppingListResponse;
import br.com.supermercados.prices.shoppinglist.ShoppingListService;
import br.com.supermercados.prices.shoppinglist.ShoppingType;
import br.com.supermercados.prices.store.StoreResponse;
import br.com.supermercados.prices.store.StoreService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import java.util.ArrayList;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ShoppingComparisonServiceTest {

    private final Instant now = Instant.parse("2026-09-10T12:00:00Z");
    private final UUID userId = UUID.randomUUID();
    private final UUID listId = UUID.randomUUID();
    private final UUID cityId = UUID.randomUUID();
    private final PageRequest pageable = PageRequest.of(0, 20, Sort.by("name", "id"));

    @Mock private LocationService locations;
    @Mock private StoreService stores;
    @Mock private ProductService products;
    @Mock private ShoppingListService shoppingLists;
    @Mock private PriceRecordRepository prices;
    @Mock private ProductEquivalenceService equivalence;

    private ShoppingComparisonService comparisons;

    @BeforeEach
    void setUp() {
        PricePolicy policy = new PricePolicy(Duration.ofDays(2));
        lenient().when(equivalence.find(any())).thenAnswer(invocation -> {
            var matches = new LinkedHashMap<UUID, ProductEquivalenceService.Matches>();
            for (UUID id : invocation.<List<UUID>>getArgument(0)) {
                Product product = mock(Product.class);
                when(product.getId()).thenReturn(id);
                matches.put(id, new ProductEquivalenceService.Matches(List.of(product), List.of()));
            }
            return matches;
        });
        comparisons = new ShoppingComparisonService(locations, stores, products, shoppingLists,
                new EquivalentPriceService(equivalence, prices, policy),
                policy, new ShoppingPriceCalculator(policy), Clock.fixed(now, ZoneOffset.UTC), 500);
    }

    @Test
    void allStoreAndProductPricesAreLoadedInOneBoundedQuery() {
        UUID firstProduct = UUID.randomUUID();
        UUID secondProduct = UUID.randomUUID();
        UUID firstStore = UUID.randomUUID();
        UUID secondStore = UUID.randomUUID();
        List<ShoppingListItemResponse> items = List.of(item(firstProduct), item(secondProduct));
        when(shoppingLists.getOwnedList(userId, listId)).thenReturn(shoppingList(items));
        when(stores.findActiveStores(cityId, pageable)).thenReturn(new PageImpl<>(
                List.of(store(firstStore, "Synthetic A"), store(secondStore, "Synthetic B")), pageable, 25));
        when(prices.findLatestForStoresAndProducts(List.of(firstStore, secondStore),
                List.of(firstProduct, secondProduct))).thenReturn(List.of(
                PriceFixtures.regular(firstProduct, firstStore, "10.00", now),
                PriceFixtures.regular(secondProduct, firstStore, "5.00", now),
                PriceFixtures.regular(firstProduct, secondStore, "1.00", now.minus(Duration.ofDays(3)))));

        ShoppingListComparisonResponse response = comparisons.compareShoppingList(userId, listId, cityId, pageable);

        assertThat(response.stores().totalElements()).isEqualTo(25);
        assertThat(response.stores().content().getFirst().subtotalKnown()).isEqualByComparingTo("30.00");
        assertThat(response.stores().content().getFirst().completeShoppingList()).isTrue();
        ShoppingStoreComparison second = response.stores().content().get(1);
        assertThat(second.subtotalKnown()).isNull();
        assertThat(second.completeShoppingList()).isFalse();
        assertThat(second.items().getFirst().price().status()).isEqualTo(PriceStatus.EXPIRED);
        verify(prices).findLatestForStoresAndProducts(List.of(firstStore, secondStore),
                List.of(firstProduct, secondProduct));
        verifyNoMoreInteractions(prices);
    }

    @Test
    void anotherUsersShoppingListIsRejectedBeforeLoadingStoresOrPrices() {
        when(shoppingLists.getOwnedList(userId, listId))
                .thenThrow(new ApiException(HttpStatus.NOT_FOUND, "Lista não encontrada."));

        assertThatThrownBy(() -> comparisons.compareShoppingList(userId, listId, cityId, pageable))
                .isInstanceOf(ApiException.class);

        verifyNoInteractions(prices, stores, locations);
    }

    @Test
    void emptyListDoesNotIssueAnUnboundedOrEmptyInClauseQuery() {
        UUID storeId = UUID.randomUUID();
        when(shoppingLists.getOwnedList(userId, listId)).thenReturn(shoppingList(List.of()));
        when(stores.findActiveStores(cityId, pageable)).thenReturn(new PageImpl<>(
                List.of(store(storeId, "Synthetic A")), pageable, 1));

        ShoppingListComparisonResponse response = comparisons.compareShoppingList(userId, listId, cityId, pageable);

        assertThat(response.stores().content().getFirst().completeShoppingList()).isFalse();
        assertThat(response.stores().content().getFirst().subtotalKnown()).isNull();
        verifyNoInteractions(prices);
    }

    @Test
    void incompleteStoreNeverWinsRecommendationBecauseOfItsLowerSubtotal() {
        UUID firstProduct = UUID.randomUUID();
        UUID secondProduct = UUID.randomUUID();
        UUID incompleteStore = UUID.randomUUID();
        UUID completeStore = UUID.randomUUID();
        List<ShoppingListItemResponse> items = List.of(item(firstProduct), item(secondProduct));
        when(shoppingLists.getOwnedList(userId, listId)).thenReturn(shoppingList(items));
        when(stores.selectComparisonStores(cityId, null, 500)).thenReturn(List.of(
                store(incompleteStore, "Incomplete"), store(completeStore, "Complete")));
        when(prices.findLatestForStoresAndProducts(
                List.of(incompleteStore, completeStore), List.of(firstProduct, secondProduct)))
                .thenReturn(List.of(
                        PriceFixtures.regular(firstProduct, incompleteStore, "1.00", now),
                        PriceFixtures.regular(firstProduct, completeStore, "5.00", now),
                        PriceFixtures.regular(secondProduct, completeStore, "5.00", now)));

        ShoppingRecommendationResponse response = comparisons.recommendShoppingList(userId, listId, cityId);

        assertThat(response.status()).isEqualTo(RecommendationStatus.COMPLETE_STORE_FOUND);
        assertThat(response.recommendation().storeId()).isEqualTo(completeStore);
        assertThat(response.recommendation().total()).isEqualByComparingTo("20.00");
        assertThat(response.recommendation().completeShoppingList()).isTrue();
        assertThat(response.splitSavings().storeId()).isEqualTo(completeStore);
        assertThat(response.splitSavings().savings()).isEqualByComparingTo("8.00");
    }

    @Test
    void freePlanSeesTheSplitSavingsButNotWhereToBuyEachItem() {
        UUID firstProduct = UUID.randomUUID();
        UUID secondProduct = UUID.randomUUID();
        UUID cheapStore = UUID.randomUUID();
        UUID completeStore = UUID.randomUUID();
        when(shoppingLists.getOwnedList(userId, listId))
                .thenReturn(shoppingList(List.of(item(firstProduct), item(secondProduct))));
        when(stores.selectComparisonStores(cityId, null, 500)).thenReturn(List.of(
                store(cheapStore, "Cheap"), store(completeStore, "Complete")));
        when(prices.findLatestForStoresAndProducts(
                List.of(cheapStore, completeStore), List.of(firstProduct, secondProduct)))
                .thenReturn(List.of(
                        PriceFixtures.regular(firstProduct, cheapStore, "1.00", now),
                        PriceFixtures.regular(firstProduct, completeStore, "5.00", now),
                        PriceFixtures.regular(secondProduct, completeStore, "5.00", now)));

        ShoppingRecommendationResponse response =
                comparisons.recommendShoppingList(userId, listId, cityId, null, false);

        assertThat(response.combination().locked()).isTrue();
        assertThat(response.combination().stores()).isEmpty();
        assertThat(response.combination().storeCount()).isEqualTo(2);
        assertThat(response.splitSavings().savings()).isEqualByComparingTo("8.00");
    }

    @Test
    void recommendationConsidersStoresBeyondAConventionalFirstPage() {
        UUID productId = UUID.randomUUID();
        List<StoreResponse> availableStores = new ArrayList<>();
        List<br.com.supermercados.prices.price.PriceRecord> observations = new ArrayList<>();
        for (int index = 0; index < 21; index++) {
            UUID storeId = UUID.randomUUID();
            availableStores.add(store(storeId, "Store " + index));
            observations.add(PriceFixtures.regular(productId, storeId,
                    index == 20 ? "1.00" : "10.00", now));
        }
        UUID lastStoreId = availableStores.getLast().id();
        when(shoppingLists.getOwnedList(userId, listId))
                .thenReturn(shoppingList(List.of(item(productId))));
        when(stores.selectComparisonStores(cityId, null, 500)).thenReturn(availableStores);
        when(prices.findLatestForStoresAndProducts(
                availableStores.stream().map(StoreResponse::id).toList(), List.of(productId)))
                .thenReturn(observations);

        ShoppingRecommendationResponse response = comparisons.recommendShoppingList(userId, listId, cityId);

        assertThat(response.evaluatedStores()).isEqualTo(21);
        assertThat(response.recommendation().storeId()).isEqualTo(lastStoreId);
    }

    @Test
    void noCompleteStoreIsReportedWithoutDeclaringAnIncompleteWinner() {
        UUID productId = UUID.randomUUID();
        UUID storeId = UUID.randomUUID();
        when(shoppingLists.getOwnedList(userId, listId))
                .thenReturn(shoppingList(List.of(item(productId), item(UUID.randomUUID()))));
        when(stores.selectComparisonStores(cityId, null, 500)).thenReturn(List.of(store(storeId, "Incomplete")));
        when(prices.findLatestForStoresAndProducts(
                org.mockito.ArgumentMatchers.eq(List.of(storeId)), org.mockito.ArgumentMatchers.anyList()))
                .thenReturn(List.of(PriceFixtures.regular(productId, storeId, "3.00", now)));

        ShoppingRecommendationResponse response = comparisons.recommendShoppingList(userId, listId, cityId);

        assertThat(response.status()).isEqualTo(RecommendationStatus.NO_COMPLETE_STORE);
        assertThat(response.recommendation()).isNull();
        assertThat(response.closestMatches()).hasSize(1);
        assertThat(response.closestMatches().getFirst().missingItems()).isEqualTo(1);
    }

    private ShoppingListItemResponse item(UUID productId) {
        return new ShoppingListItemResponse(UUID.randomUUID(), productId, "Synthetic product",
                new BigDecimal("2.000"));
    }

    private ShoppingListResponse shoppingList(List<ShoppingListItemResponse> items) {
        return new ShoppingListResponse(listId, "Synthetic list", ShoppingType.CUSTOM, items, now, now, 0);
    }

    private StoreResponse store(UUID id, String name) {
        return new StoreResponse(id, null, cityId, name, null, null, null, true,
                UUID.randomUUID(), "synthetic-store-" + id, now, now, null);
    }
}
