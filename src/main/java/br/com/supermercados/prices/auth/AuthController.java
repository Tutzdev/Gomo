package br.com.supermercados.prices.auth;

import br.com.supermercados.prices.common.ApiException;
import br.com.supermercados.prices.user.UserResponse;
import br.com.supermercados.prices.user.UserService;
import jakarta.validation.Valid;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthService auth;
    private final TokenService tokens;
    private final AccountService accounts;
    private final AbuseProtectionService abuseProtection;
    private final SessionCookies cookies;
    private final UserService users;

    public AuthController(
        AuthService auth,
        TokenService tokens,
        AccountService accounts,
        AbuseProtectionService abuseProtection,
        SessionCookies cookies,
        UserService users) {

        this.auth = auth;
        this.tokens = tokens;
        this.accounts = accounts;
        this.abuseProtection = abuseProtection;
        this.cookies = cookies;
        this.users = users;
    }

    @PostMapping("/register")
    public ResponseEntity<Void> register(@Valid @RequestBody RegisterRequest request, HttpServletRequest httpRequest) {
        abuseProtection.checkRegistration(httpRequest.getRemoteAddr(), request.email());
        auth.register(request);

        return ResponseEntity.accepted().build();
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(
        @Valid @RequestBody LoginRequest request,
        HttpServletRequest httpRequest,
        HttpServletResponse httpResponse) {

        abuseProtection.checkLogin(httpRequest.getRemoteAddr(), request.email());

        AuthResponse response = auth.login(request);

        if (SessionCookies.requested(httpRequest)) {
            response = keepTokenInCookie(response, httpResponse);
        }

        return okWithoutCache()
                .header(HttpHeaders.PRAGMA, "no-cache")
                .body(response);
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(
        @AuthenticationPrincipal AuthenticatedUser principal,
        @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization,
        HttpServletRequest httpRequest,
        HttpServletResponse httpResponse) {

        tokens.revoke(currentToken(authorization, httpRequest), principal.id());
        cookies.clear(httpResponse);

        return ResponseEntity.noContent().build();
    }

    @PostMapping("/logout-all")
    public ResponseEntity<Void> logoutEverywhere(
        @AuthenticationPrincipal AuthenticatedUser principal,
        HttpServletResponse httpResponse) {

        tokens.revokeAll(principal.id());
        cookies.clear(httpResponse);

        return ResponseEntity.noContent().build();
    }

    @PostMapping("/password")
    public ResponseEntity<Void> changePassword(
        @AuthenticationPrincipal AuthenticatedUser principal,
        @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization,
        @Valid @RequestBody ChangePasswordRequest request,
        HttpServletRequest httpRequest) {

        abuseProtection.checkPasswordChange(principal.id());

        accounts.changePassword(
            principal.id(),
            request.currentPassword(),
            request.newPassword(),
            currentToken(authorization, httpRequest));

        return ResponseEntity.noContent().build();
    }

    @GetMapping("/session")
    public ResponseEntity<UserResponse> currentSession(@AuthenticationPrincipal AuthenticatedUser principal) {
        if (principal == null) {
            return ResponseEntity.noContent().header(HttpHeaders.CACHE_CONTROL, "no-store").build();
        }

        return okWithoutCache().body(users.findCurrentUser(principal.id()));
    }

    @PostMapping("/session")
    public ResponseEntity<UserResponse> moveSessionToCookie(
        @AuthenticationPrincipal AuthenticatedUser principal,
        @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization,
        HttpServletRequest httpRequest,
        HttpServletResponse httpResponse) {

        if (authorization == null || !SessionCookies.requested(httpRequest)) {
            throw new ApiException(
                HttpStatus.BAD_REQUEST,
                "Envie o token antigo no Authorization e o cabeçalho X-Gomo-Session: cookie.");
        }

        cookies.write(httpResponse, tokens.issue(principal.id()));
        tokens.revoke(BearerTokenFilter.bearerToken(authorization), principal.id());

        return okWithoutCache().body(users.findCurrentUser(principal.id()));
    }

    @PostMapping("/email-verifications")
    public ResponseEntity<Void> requestEmailVerification(
        @AuthenticationPrincipal AuthenticatedUser principal,
        HttpServletRequest httpRequest) {

        abuseProtection.checkAccountConfirmation(httpRequest.getRemoteAddr());
        accounts.requestEmailVerification(principal.id());

        return ResponseEntity.accepted().build();
    }

    @PostMapping("/email-verifications/confirm")
    public ResponseEntity<Void> confirmEmail(
        @Valid @RequestBody EmailVerificationConfirmRequest request,
        HttpServletRequest httpRequest) {

        abuseProtection.checkAccountConfirmation(httpRequest.getRemoteAddr());
        accounts.confirmEmail(request.token());

        return ResponseEntity.noContent().build();
    }

    @PostMapping("/password-resets")
    public ResponseEntity<Void> requestPasswordReset(
        @Valid @RequestBody PasswordResetRequest request,
        HttpServletRequest httpRequest) {

        abuseProtection.checkAccountMessage(httpRequest.getRemoteAddr(), request.email());
        accounts.requestPasswordReset(request.email());

        return ResponseEntity.accepted().build();
    }

    @PostMapping("/password-resets/confirm")
    public ResponseEntity<Void> resetPassword(
        @Valid @RequestBody PasswordResetConfirmRequest request,
        HttpServletRequest httpRequest) {

        abuseProtection.checkAccountConfirmation(httpRequest.getRemoteAddr());
        accounts.resetPassword(request.token(), request.password());

        return ResponseEntity.noContent().build();
    }

    private AuthResponse keepTokenInCookie(AuthResponse response, HttpServletResponse httpResponse) {
        cookies.write(httpResponse, new TokenService.IssuedToken(response.accessToken(), response.expiresAt()));

        return new AuthResponse(null, "Cookie", response.expiresAt(), response.user());
    }

    private String currentToken(String authorization, HttpServletRequest httpRequest) {
        return authorization != null
                ? BearerTokenFilter.bearerToken(authorization)
                : cookies.read(httpRequest).orElseThrow();
    }

    private static ResponseEntity.BodyBuilder okWithoutCache() {
        return ResponseEntity.ok().header(HttpHeaders.CACHE_CONTROL, "no-store");
    }
}
