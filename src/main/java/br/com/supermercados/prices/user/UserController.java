package br.com.supermercados.prices.user;

import java.time.Instant;
import java.time.ZoneId;
import java.util.Map;

import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import br.com.supermercados.prices.auth.AccountDeletionService;
import br.com.supermercados.prices.auth.AuthenticatedUser;
import br.com.supermercados.prices.auth.SessionCookies;
import br.com.supermercados.prices.subscription.PaidSubscriptionService;
import jakarta.servlet.http.HttpServletResponse;
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

    /** The day in the file name is the user's day, not the server's. */
    private static final ZoneId BRAZIL = ZoneId.of("America/Sao_Paulo");

    private final UserService users;
    private final AccountDeletionService deletion;
    private final AccountDataExportService dataExport;
    private final PaidSubscriptionService paidSubscriptions;
    private final SessionCookies cookies;

    public UserController(UserService users, AccountDeletionService deletion, AccountDataExportService dataExport,
            PaidSubscriptionService paidSubscriptions, SessionCookies cookies) {
        this.users = users;
        this.deletion = deletion;
        this.dataExport = dataExport;
        this.paidSubscriptions = paidSubscriptions;
        this.cookies = cookies;
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

    /** Everything the Gomo keeps about the account, as a JSON file to download (LGPD art. 18). */
    @GetMapping("/export")
    public ResponseEntity<Map<String, Object>> exportData(@AuthenticationPrincipal AuthenticatedUser principal) {
        Map<String, Object> data = dataExport.export(principal.id());
        String day = ((Instant) data.get("geradoEm")).atZone(BRAZIL).toLocalDate().toString();
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename("gomo-meus-dados-" + day + ".json").build().toString())
                .contentType(MediaType.APPLICATION_JSON)
                .body(data);
    }

    /**
     * Deletes the account; the password confirms that its owner is asking. A paid subscription is cancelled with
     * the provider first, so a deleted account is never charged again.
     */
    @DeleteMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteCurrentUser(@AuthenticationPrincipal AuthenticatedUser principal,
            @Valid @RequestBody DeleteAccountRequest request, HttpServletResponse response) {
        deletion.verifyPassword(principal.id(), request.password());
        paidSubscriptions.cancelBeforeAccountDeletion(principal.id());
        deletion.delete(principal.id(), request.password());
        cookies.clear(response);
    }

    public record DeleteAccountRequest(@NotBlank @Size(max = 72) String password) {
    }
}
