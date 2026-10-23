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

    public AuthController(AuthService auth, TokenService tokens, AccountService accounts,
            AbuseProtectionService abuseProtection, SessionCookies cookies, UserService users) {
        this.auth = auth;
        this.tokens = tokens;
        this.accounts = accounts;
        this.abuseProtection = abuseProtection;
        this.cookies = cookies;
        this.users = users;
    }

    /**
     * Always 202 with no body or session, whether or not the e-mail already had an account (its owner gets an
     * e-mail instead), so registration does not reveal who uses the Gomo. The client signs in afterwards.
     */
    @PostMapping("/register")
    public ResponseEntity<Void> register(@Valid @RequestBody RegisterRequest request, HttpServletRequest httpRequest) {
        abuseProtection.checkRegistration(httpRequest.getRemoteAddr(), request.email());
        auth.register(request);
        return ResponseEntity.accepted().build();
    }

    /** The site asks for the cookie and gets no token in the body; other clients keep the Bearer token. */
    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(
            @Valid @RequestBody LoginRequest request, HttpServletRequest httpRequest,
            HttpServletResponse httpResponse) {
        abuseProtection.checkLogin(httpRequest.getRemoteAddr(), request.email());
        AuthResponse response = auth.login(request);
        if (SessionCookies.requested(httpRequest)) {
            cookies.write(httpResponse, new TokenService.IssuedToken(response.accessToken(), response.expiresAt()));
            response = new AuthResponse(null, "Cookie", response.expiresAt(), response.user());
        }
        return ResponseEntity.ok().header(HttpHeaders.CACHE_CONTROL, "no-store")
                .header(HttpHeaders.PRAGMA, "no-cache").body(response);
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@AuthenticationPrincipal AuthenticatedUser principal,
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization,
            HttpServletRequest httpRequest, HttpServletResponse httpResponse) {
        String rawToken = authorization != null
                ? authorization.substring("Bearer ".length())
                : cookies.read(httpRequest).orElseThrow();
        tokens.revoke(rawToken, principal.id());
        cookies.clear(httpResponse);
        return ResponseEntity.noContent().build();
    }

    /** The signed-in user, or 204 when there is no session; lets the site check its cookie without a 401. */
    @GetMapping("/session")
    public ResponseEntity<UserResponse> currentSession(@AuthenticationPrincipal AuthenticatedUser principal) {
        if (principal == null) {
            return ResponseEntity.noContent().header(HttpHeaders.CACHE_CONTROL, "no-store").build();
        }
        return ResponseEntity.ok().header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(users.findCurrentUser(principal.id()));
    }

    /**
     * Moves a session the site kept in localStorage (before the cookie) to the cookie: the old Bearer token is
     * revoked and a new one goes only into the cookie, so nobody already signed in has to log in again.
     */
    @PostMapping("/session")
    public ResponseEntity<UserResponse> moveSessionToCookie(@AuthenticationPrincipal AuthenticatedUser principal,
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization,
            HttpServletRequest httpRequest, HttpServletResponse httpResponse) {
        if (authorization == null || !SessionCookies.requested(httpRequest)) {
            throw new ApiException(HttpStatus.BAD_REQUEST,
                    "Envie o token antigo no Authorization e o cabeçalho X-Gomo-Session: cookie.");
        }
        cookies.write(httpResponse, tokens.issue(principal.id()));
        tokens.revoke(authorization.substring("Bearer ".length()), principal.id());
        return ResponseEntity.ok().header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(users.findCurrentUser(principal.id()));
    }

    @PostMapping("/email-verifications")
    public ResponseEntity<Void> requestEmailVerification(
            @AuthenticationPrincipal AuthenticatedUser principal, HttpServletRequest httpRequest) {
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
}
