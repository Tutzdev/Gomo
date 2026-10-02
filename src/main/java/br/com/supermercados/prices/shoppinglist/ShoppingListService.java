package br.com.supermercados.prices.shoppinglist;

import br.com.supermercados.prices.catalog.CatalogItemRepository;
import br.com.supermercados.prices.common.ApiException;
import br.com.supermercados.prices.product.ProductService;
import java.time.Clock;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class ShoppingListService {

    private static final int MAX_ITEMS = 200;
    private final ShoppingListRepository lists;
    private final ShoppingListItemRepository items;
    private final ProductService products;
    private final CatalogItemRepository catalog;
    private final Clock clock;

    public ShoppingListService(ShoppingListRepository lists, ShoppingListItemRepository items,
            ProductService products, CatalogItemRepository catalog, Clock clock) {
        this.lists = lists;
        this.items = items;
        this.products = products;
        this.catalog = catalog;
        this.clock = clock;
    }

    public Page<ShoppingListSummary> findOwnedLists(UUID userId, Pageable pageable) {
        return lists.findByUserId(userId, pageable).map(ShoppingListSummary::from);
    }

    public ShoppingListResponse getOwnedList(UUID userId, UUID listId) {
        return response(lists.findByIdAndUserId(listId, userId).orElseThrow(this::notFound));
    }

    @Transactional
    public ShoppingListResponse create(UUID userId, ShoppingListRequest request) {
        var list = new ShoppingList(userId, request.name(), request.shoppingType(), clock.instant());

        return response(lists.saveAndFlush(list));
    }

    @Transactional
    public ShoppingListResponse update(UUID userId, UUID listId, ShoppingListRequest request) {
        var list = lockOwnedList(userId, listId);
        list.rename(request.name(), request.shoppingType(), clock.instant());
        lists.flush();

        return response(list);
    }

    @Transactional
    public void delete(UUID userId, UUID listId) {
        lists.delete(lockOwnedList(userId, listId));
    }

    @Transactional
    public ShoppingListItemResponse addItem(UUID userId, UUID listId, AddItemRequest request) {
        var list = lockOwnedList(userId, listId);
        // A specific SKU that belongs to a generic item is stored as that item, so the list reads "Coca-Cola 2 L".
        var catalogItem = request.catalogItemId() == null
                ? catalog.findActiveByProductId(request.productId()).orElse(null)
                : catalog.findById(request.catalogItemId()).filter(found -> found.isActive())
                        .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Produto não encontrado."));
        // A generic item is compared through every equivalent SKU; its representative anchors the list row.
        var product = products.requireProduct(catalogItem == null
                ? request.productId() : catalogItem.getRepresentativeProductId());

        if (items.existsByShoppingListIdAndProductId(listId, product.getId()) || catalogItem != null
                && items.existsByShoppingListIdAndCatalogItemId(listId, catalogItem.getId())) {
            throw new ApiException(HttpStatus.CONFLICT, "Produto já está na lista; altere sua quantidade.");
        }
        if (items.countByShoppingListId(listId) >= MAX_ITEMS) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_CONTENT, "Uma lista pode conter até 200 produtos.");
        }
        var item = items.save(new ShoppingListItem(listId, product.getId(),
                catalogItem == null ? null : catalogItem.getId(), request.quantity()));
        list.touch(clock.instant());

        return catalogItem == null
                ? new ShoppingListItemResponse(item.getId(), product.getId(), product.getName(), item.getQuantity(),
                        null, product.getImageUrl())
                : new ShoppingListItemResponse(item.getId(), product.getId(), catalogItem.getDisplayName(),
                        item.getQuantity(), catalogItem.getId(), catalogItem.getImageUrl());
    }

    @Transactional
    public ShoppingListItemResponse updateItem(UUID userId, UUID listId, UUID itemId, UpdateItemRequest request) {
        var list = lockOwnedList(userId, listId);
        var item = requireItem(listId, itemId);

        item.changeQuantity(request.quantity());
        list.touch(clock.instant());

        var product = products.requireProduct(item.getProductId());
        var catalogItem = item.getCatalogItemId() == null ? null : catalog.findById(item.getCatalogItemId()).orElse(null);

        return new ShoppingListItemResponse(item.getId(), product.getId(),
                catalogItem == null ? product.getName() : catalogItem.getDisplayName(), item.getQuantity(),
                item.getCatalogItemId(), catalogItem == null ? product.getImageUrl() : catalogItem.getImageUrl());
    }

    @Transactional
    public void removeItem(UUID userId, UUID listId, UUID itemId) {
        var list = lockOwnedList(userId, listId);
        items.delete(requireItem(listId, itemId));
        list.touch(clock.instant());
    }

    private ShoppingList lockOwnedList(UUID userId, UUID listId) {
        return lists.lockOwnedList(listId, userId).orElseThrow(this::notFound);
    }

    private ShoppingListItem requireItem(UUID listId, UUID itemId) {
        return items.findByIdAndShoppingListId(itemId, listId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Item não encontrado."));
    }

    private ShoppingListResponse response(ShoppingList list) {
        return new ShoppingListResponse(list.getId(), list.getName(), list.getShoppingType(),
                items.findItems(list.getId()), list.getCreatedAt(), list.getUpdatedAt(), list.getVersion());
    }

    private ApiException notFound() {
        return new ApiException(HttpStatus.NOT_FOUND, "Lista não encontrada.");
    }
}
