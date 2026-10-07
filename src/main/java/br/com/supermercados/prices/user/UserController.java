package br.com.supermercados.prices.user;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import br.com.supermercados.prices.auth.AccountDeletionService;
import br.com.supermercados.prices.auth.AuthenticatedUser;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/users/me")
public class UserController {

    private final UserService users;
    private final AccountDeletionService deletion;

    public UserController(UserService users, AccountDeletionService deletion) {
        this.users = users;
        this.deletion = deletion;
    }

    @GetMapping
    public UserResponse currentUser(@AuthenticationPrincipal AuthenticatedUser principal) {
        return users.findCurrentUser(principal.id());
    }

    @PatchMapping
    public UserResponse updateCurrentUser(@AuthenticationPrincipal AuthenticatedUser principal,
            @Valid @RequestBody UpdateUserRequest request) {
        return users.updateCurrentUser(principal.id(), request);
    }

    /** Deletes the account; the password confirms that its owner is asking. */
    @DeleteMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteCurrentUser(@AuthenticationPrincipal AuthenticatedUser principal,
            @Valid @RequestBody DeleteAccountRequest request) {
        deletion.delete(principal.id(), request.password());
    }

    public record DeleteAccountRequest(@NotBlank @Size(max = 72) String password) {
    }
}
