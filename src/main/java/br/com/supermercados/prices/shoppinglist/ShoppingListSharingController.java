package br.com.supermercados.prices.shoppinglist;

import br.com.supermercados.prices.auth.AuthenticatedUser;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The invite token travels in request bodies, never in a URL the server or a proxy could log.
 */
@RestController
@RequestMapping("/api/v1")
public class ShoppingListSharingController {

    private final ShoppingListSharingService sharing;

    public ShoppingListSharingController(ShoppingListSharingService sharing) {
        this.sharing = sharing;
    }

    public record InviteTokenRequest(@NotBlank @Size(max = 100) String token) {
    }

    @PostMapping("/shopping-lists/{id}/invites")
    public ResponseEntity<ShoppingListSharingService.InviteResponse> invite(
            @AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID id) {
        return ResponseEntity.status(HttpStatus.CREATED).body(sharing.invite(user, id));
    }

    @DeleteMapping("/shopping-lists/{id}/invites")
    public ResponseEntity<Void> stopSharing(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID id) {
        sharing.stopSharing(user.id(), id);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/shopping-lists/{id}/members/{memberId}")
    public ResponseEntity<Void> removeMember(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID id,
            @PathVariable UUID memberId) {
        sharing.removeMember(user.id(), id, memberId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/list-invites/preview")
    public ShoppingListSharingService.InvitePreviewResponse preview(@AuthenticationPrincipal AuthenticatedUser user,
            @Valid @RequestBody InviteTokenRequest request) {
        return sharing.preview(user.id(), request.token());
    }

    @PostMapping("/list-invites/acceptance")
    public Map<String, UUID> accept(@AuthenticationPrincipal AuthenticatedUser user,
            @Valid @RequestBody InviteTokenRequest request) {
        return Map.of("listId", sharing.accept(user.id(), request.token()));
    }
}
