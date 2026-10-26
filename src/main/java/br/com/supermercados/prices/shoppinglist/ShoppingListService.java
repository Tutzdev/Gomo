package br.com.supermercados.prices.shoppinglist;

import br.com.supermercados.prices.catalog.CatalogItemRepository;
import br.com.supermercados.prices.common.ApiException;
import br.com.supermercados.prices.product.ProductService;
import br.com.supermercados.prices.subscription.FreePlan;
import br.com.supermercados.prices.subscription.PlanLimit;
import br.com.supermercados.prices.subscription.PlanLimitException;
import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Lists and their items. A list belongs to its owner; the people it is shared with (members) see it, add and
 * change items and check them off at the market like the owner. Renaming, deleting and sharing stay with the
 * owner, and a list nobody can see answers 404 as if it did not exist.
 */
@Service
@Transactional(readOnly = true)
public class ShoppingListService {

    private static final int MAX_ITEMS = 200;
    private final ShoppingListRepository lists;
    private final ShoppingListItemRepository items;
    private final ShoppingListMemberRepository members;
    private final ProductService products;
    private final CatalogItemRepository catalog;
    private final Clock clock;

    public ShoppingListService(ShoppingListRepository lists, ShoppingListItemRepository items,
            ShoppingListMemberRepository members, ProductService products, CatalogItemRepository catalog,
            Clock clock) {
        this.lists = lists;
        this.items = items;
        this.members = members;
        this.products = products;
        this.catalog = catalog;
        this.clock = clock;
    }

    /** Only the lists the person owns: what the free plan counts. */
    public Page<ShoppingListSummary> findOwnedLists(UUID userId, Pageable pageable) {
        return lists.findByUserId(userId, pageable).map(ShoppingListSummary::from);
    }

    /** The person's own lists and the ones shared with them. */
    public Page<ShoppingListSummary> findLists(UUID userId, Pageable pageable) {
        Page<ShoppingList> page = lists.findAccessible(userId, pageable);
        Map<UUID, Integer> memberCounts = page.isEmpty() ? Map.of()
                : members.countByList(page.map(ShoppingList::getId).getContent()).stream()
                        .collect(Collectors.toMap(row -> (UUID) row[0], row -> ((Long) row[1]).intValue()));
        return page.map(list -> {
            boolean owned = list.getUserId().equals(userId);
            return ShoppingListSummary.from(list, owned, owned ? null : members.findUserName(list.getUserId()),
                    memberCounts.getOrDefault(list.getId(), 0));
        });
    }

    public long countOwnedLists(UUID userId) {
        return lists.countByUserId(userId);
    }

    /** The lists bought again and again: weekly and monthly ones, own or shared. */
    public List<ShoppingListSummary> findRecurringLists(UUID userId) {
        return lists.findAccessibleOfTypes(userId, List.of(ShoppingType.WEEKLY, ShoppingType.MONTHLY)).stream()
                .map(list -> ShoppingListSummary.from(list, list.getUserId().equals(userId), null, 0)).toList();
    }

    /** The generic products the person keeps in any of their lists, own or shared. */
    public Set<UUID> catalogItemIdsInLists(UUID userId) {
        return items.findCatalogItemIdsOfUser(userId);
    }

    /** A list the person owns or was invited to. */
    public ShoppingListResponse getList(UUID userId, UUID listId) {
        return response(lists.findAccessible(listId, userId).orElseThrow(this::notFound), userId);
    }

    @Transactional
    public ShoppingListResponse create(UUID userId, boolean premium, ShoppingListRequest request) {
        if (!premium && lists.countByUserId(userId) >= FreePlan.MAX_SHOPPING_LISTS) {
            throw new PlanLimitException(PlanLimit.SHOPPING_LISTS,
                    "No plano grátis você mantém 1 lista por vez. Exclua a atual ou assine o Premium.");
        }
        var list = new ShoppingList(userId, request.name(), request.shoppingType(), clock.instant());

        return response(lists.saveAndFlush(list), userId);
    }

    @Transactional
    public ShoppingListResponse update(UUID userId, UUID listId, ShoppingListRequest request) {
        var list = lockOwnedList(userId, listId);
        list.rename(request.name(), request.shoppingType(), clock.instant());
        lists.flush();

        return response(list, userId);
    }

    @Transactional
    public void delete(UUID userId, UUID listId) {
        lists.delete(lockOwnedList(userId, listId));
    }

    /**
     * A shared list was shared by a Premium owner, so it is not held to the free plan's 10 items, whoever adds
     * the next one.
     */
    @Transactional
    public ShoppingListItemResponse addItem(UUID userId, boolean premium, UUID listId, AddItemRequest request) {
        var list = lockAccessibleList(userId, listId);
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
        long itemCount = items.countByShoppingListId(listId);
        if (!premium && itemCount >= FreePlan.MAX_LIST_ITEMS && !members.existsByShoppingListId(listId)) {
            throw new PlanLimitException(PlanLimit.LIST_ITEMS,
                    "No plano grátis cada lista tem até 10 produtos.");
        }
        if (itemCount >= MAX_ITEMS) {
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
        var list = lockAccessibleList(userId, listId);
        var item = requireItem(listId, itemId);

        item.changeQuantity(request.quantity());
        list.touch(clock.instant());

        return itemResponse(item);
    }

    @Transactional
    public void removeItem(UUID userId, UUID listId, UUID itemId) {
        var list = lockAccessibleList(userId, listId);
        items.delete(requireItem(listId, itemId));
        list.touch(clock.instant());
    }

    /** One item out, the other in, in one step: the list never ends up with both or neither. */
    @Transactional
    public ShoppingListItemResponse replaceItem(UUID userId, UUID listId, UUID itemId, ReplaceItemRequest request) {
        var list = lockAccessibleList(userId, listId);
        var previous = requireItem(listId, itemId);
        var catalogItem = catalog.findById(request.catalogItemId()).filter(found -> found.isActive())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Produto não encontrado."));
        var product = products.requireProduct(catalogItem.getRepresentativeProductId());
        if (items.existsByShoppingListIdAndCatalogItemId(listId, catalogItem.getId())
                || items.existsByShoppingListIdAndProductId(listId, product.getId())) {
            throw new ApiException(HttpStatus.CONFLICT, "Esse produto já está na lista; altere sua quantidade.");
        }
        items.delete(previous);
        items.flush();
        var item = items.save(new ShoppingListItem(listId, product.getId(), catalogItem.getId(), request.quantity()));
        list.touch(clock.instant());
        return itemResponse(item);
    }

    /** Into the cart, or back out of it. Idempotent, so the "No mercado" mode can resend after losing signal. */
    @Transactional
    public ShoppingListItemResponse checkItem(UUID userId, UUID listId, UUID itemId, boolean checked) {
        lockAccessibleList(userId, listId);
        var item = requireItem(listId, itemId);
        if (checked) {
            item.check(userId, clock.instant());
        } else {
            item.uncheck();
        }
        return itemResponse(item);
    }

    /** Starts the next shopping trip with nothing in the cart. */
    @Transactional
    public void clearChecks(UUID userId, UUID listId) {
        lockAccessibleList(userId, listId);
        items.clearChecks(listId);
    }

    /**
     * After a purchase: when every item of the list went into a cart, the list starts over for the next trip.
     * A list split between two markets keeps its marks until the second purchase.
     */
    @Transactional
    public boolean clearChecksIfEverythingWasBought(UUID userId, UUID listId) {
        lockAccessibleList(userId, listId);
        if (items.countByShoppingListIdAndCheckedAtIsNull(listId) > 0) {
            return false;
        }
        items.clearChecks(listId);
        return true;
    }

    private ShoppingListItemResponse itemResponse(ShoppingListItem item) {
        var product = products.requireProduct(item.getProductId());
        var catalogItem = item.getCatalogItemId() == null ? null : catalog.findById(item.getCatalogItemId()).orElse(null);
        return new ShoppingListItemResponse(item.getId(), product.getId(),
                catalogItem == null ? product.getName() : catalogItem.getDisplayName(), item.getQuantity(),
                item.getCatalogItemId(), catalogItem == null ? product.getImageUrl() : catalogItem.getImageUrl(),
                item.getCheckedAt(), item.getCheckedBy(),
                item.getCheckedBy() == null ? null : members.findUserName(item.getCheckedBy()));
    }

    private ShoppingList lockOwnedList(UUID userId, UUID listId) {
        return lists.lockOwnedList(listId, userId).orElseThrow(() -> lists.findAccessible(listId, userId).isPresent()
                ? new ApiException(HttpStatus.FORBIDDEN, "Só quem criou a lista pode renomear, excluir ou compartilhar.")
                : notFound());
    }

    private ShoppingList lockAccessibleList(UUID userId, UUID listId) {
        return lists.lockAccessibleList(listId, userId).orElseThrow(this::notFound);
    }

    private ShoppingListItem requireItem(UUID listId, UUID itemId) {
        return items.findByIdAndShoppingListId(itemId, listId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Item não encontrado."));
    }

    private ShoppingListResponse response(ShoppingList list, UUID userId) {
        boolean owned = list.getUserId().equals(userId);
        return new ShoppingListResponse(list.getId(), list.getName(), list.getShoppingType(),
                items.findItems(list.getId()), list.getCreatedAt(), list.getUpdatedAt(), list.getVersion(), owned,
                members.findUserName(list.getUserId()), members.findMembers(list.getId()));
    }

    private ApiException notFound() {
        return new ApiException(HttpStatus.NOT_FOUND, "Lista não encontrada.");
    }
}
