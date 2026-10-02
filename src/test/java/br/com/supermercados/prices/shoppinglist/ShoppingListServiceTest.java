package br.com.supermercados.prices.shoppinglist;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import br.com.supermercados.prices.common.ApiException;
import br.com.supermercados.prices.product.Product;
import br.com.supermercados.prices.product.ProductService;
import br.com.supermercados.prices.subscription.PlanLimit;
import br.com.supermercados.prices.subscription.PlanLimitException;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

@ExtendWith(MockitoExtension.class)
class ShoppingListServiceTest {

    @Mock ShoppingListRepository lists;
    @Mock ShoppingListItemRepository items;
    @Mock ProductService products;
    @Mock Product product;

    ShoppingListService service;
    final UUID userId = UUID.randomUUID();
    final UUID listId = UUID.randomUUID();
    final UUID productId = UUID.randomUUID();
    final Instant now = Instant.parse("2025-01-10T12:00:00Z");

    @BeforeEach
    void setUp() {
        service = new ShoppingListService(lists, items, products,
                org.mockito.Mockito.mock(br.com.supermercados.prices.catalog.CatalogItemRepository.class),
                Clock.fixed(now, ZoneOffset.UTC));
    }

    @Test
    void fullListRejectsAdditionalProductsBeforeWriting() {
        when(lists.lockOwnedList(listId, userId)).thenReturn(Optional.of(
                new ShoppingList(userId, "Lista fictícia", ShoppingType.CUSTOM, now)));
        when(products.requireProduct(productId)).thenReturn(product);
        when(product.getId()).thenReturn(productId);
        when(items.countByShoppingListId(listId)).thenReturn(200L);

        assertThatThrownBy(() -> service.addItem(userId, true, listId, new AddItemRequest(productId, BigDecimal.ONE)))
                .isInstanceOfSatisfying(ApiException.class, exception ->
                        org.assertj.core.api.Assertions.assertThat(exception.getStatus())
                                .isEqualTo(HttpStatus.UNPROCESSABLE_CONTENT));
        verify(items, never()).save(any());
    }

    @Test
    void unauthorizedMutationDoesNotLookUpProductsOrItems() {
        when(lists.lockOwnedList(listId, userId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.addItem(userId, true, listId, new AddItemRequest(productId, BigDecimal.ONE)))
                .isInstanceOfSatisfying(ApiException.class, exception ->
                        org.assertj.core.api.Assertions.assertThat(exception.getStatus()).isEqualTo(HttpStatus.NOT_FOUND));
        verifyNoInteractions(products, items);
    }

    @Test
    void freeListStopsAtTenProducts() {
        when(lists.lockOwnedList(listId, userId)).thenReturn(Optional.of(
                new ShoppingList(userId, "Lista fictícia", ShoppingType.CUSTOM, now)));
        when(products.requireProduct(productId)).thenReturn(product);
        when(product.getId()).thenReturn(productId);
        when(items.countByShoppingListId(listId)).thenReturn(10L);

        assertThatThrownBy(() -> service.addItem(userId, false, listId, new AddItemRequest(productId, BigDecimal.ONE)))
                .isInstanceOfSatisfying(PlanLimitException.class, exception ->
                        org.assertj.core.api.Assertions.assertThat(exception.getLimit()).isEqualTo(PlanLimit.LIST_ITEMS));
        verify(items, never()).save(any());
    }

    @Test
    void freeAccountKeepsOneListAtATime() {
        when(lists.countByUserId(userId)).thenReturn(1L);

        assertThatThrownBy(() -> service.create(userId, false,
                new ShoppingListRequest("Segunda lista", ShoppingType.WEEKLY)))
                .isInstanceOfSatisfying(PlanLimitException.class, exception ->
                        org.assertj.core.api.Assertions.assertThat(exception.getLimit())
                                .isEqualTo(PlanLimit.SHOPPING_LISTS));
        verify(lists, never()).saveAndFlush(any());
    }

    @Test
    void premiumAccountCreatesMoreListsWithoutCountingThem() {
        when(lists.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));

        service.create(userId, true, new ShoppingListRequest("Churrasco", ShoppingType.CUSTOM));

        verify(lists, never()).countByUserId(any());
    }
}
