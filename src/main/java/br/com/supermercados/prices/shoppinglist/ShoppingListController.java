package br.com.supermercados.prices.shoppinglist;

import br.com.supermercados.prices.auth.AuthenticatedUser;
import br.com.supermercados.prices.common.PageRequests;
import br.com.supermercados.prices.common.PageResponse;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.UUID;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/shopping-lists")
public class ShoppingListController {

    private final ShoppingListService service;

    public ShoppingListController(ShoppingListService service) {
        this.service = service;
    }

    @GetMapping
    public PageResponse<ShoppingListSummary> findLists(@AuthenticationPrincipal AuthenticatedUser user,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        var pageable = PageRequests.create(page, size, Sort.by(Sort.Order.desc("updatedAt"), Sort.Order.asc("id")));
        return PageResponse.from(service.findOwnedLists(user.id(), pageable));
    }

    @GetMapping("/{id}")
    public ShoppingListResponse getList(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID id) {
        return service.getOwnedList(user.id(), id);
    }

    @PostMapping
    public ResponseEntity<ShoppingListResponse> create(@AuthenticationPrincipal AuthenticatedUser user,
            @Valid @RequestBody ShoppingListRequest request) {
        var list = service.create(user.id(), user.premium(), request);
        return ResponseEntity.created(URI.create("/api/v1/shopping-lists/" + list.id())).body(list);
    }

    @PutMapping("/{id}")
    public ShoppingListResponse update(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID id,
            @Valid @RequestBody ShoppingListRequest request) {
        return service.update(user.id(), id, request);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID id) {
        service.delete(user.id(), id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/items")
    public ResponseEntity<ShoppingListItemResponse> addItem(@AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable UUID id, @Valid @RequestBody AddItemRequest request) {
        var item = service.addItem(user.id(), user.premium(), id, request);
        return ResponseEntity.created(URI.create("/api/v1/shopping-lists/" + id + "/items/" + item.id())).body(item);
    }

    @PutMapping("/{id}/items/{itemId}")
    public ShoppingListItemResponse updateItem(@AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable UUID id, @PathVariable UUID itemId, @Valid @RequestBody UpdateItemRequest request) {
        return service.updateItem(user.id(), id, itemId, request);
    }

    @DeleteMapping("/{id}/items/{itemId}")
    public ResponseEntity<Void> removeItem(@AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable UUID id, @PathVariable UUID itemId) {
        service.removeItem(user.id(), id, itemId);
        return ResponseEntity.noContent().build();
    }
}
