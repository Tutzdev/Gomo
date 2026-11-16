package br.com.supermercados.prices.auth;

import br.com.supermercados.prices.common.ApiException;
import br.com.supermercados.prices.subscription.PremiumAccess;
import br.com.supermercados.prices.user.User;
import br.com.supermercados.prices.user.UserRepository;
import br.com.supermercados.prices.user.UserResponse;
import java.time.Clock;
import java.util.Optional;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    private final UserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final TokenService tokens;
    private final PasswordPolicy passwordPolicy;
    private final AccountService accounts;
    private final PremiumAccess premiumAccess;
    private final Clock clock;
    private final String dummyPasswordHash;

    public AuthService(
        UserRepository users,
        PasswordEncoder passwordEncoder,
        TokenService tokens,
        PasswordPolicy passwordPolicy,
        AccountService accounts,
        PremiumAccess premiumAccess,
        Clock clock) {

        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.tokens = tokens;
        this.passwordPolicy = passwordPolicy;
        this.accounts = accounts;
        this.premiumAccess = premiumAccess;
        this.clock = clock;
        this.dummyPasswordHash = passwordEncoder.encode(UUID.randomUUID().toString());
    }

    @Transactional
    public void register(RegisterRequest request) {
        passwordPolicy.validate(request.password());

        // O hash vem antes de saber se o e-mail já tem conta: o tempo de resposta não revela quem usa o Gomo.
        String email = User.normalizeEmail(request.email());
        String passwordHash = passwordEncoder.encode(request.password());
        Optional<User> existing = users.findByEmail(email);

        if (existing.isPresent()) {
            accounts.notifyExistingAccount(existing.get());
            return;
        }

        User savedUser = users.saveAndFlush(new User(request.name(), email, passwordHash, clock.instant()));

        accounts.issueEmailVerification(savedUser);
    }

    public AuthResponse login(LoginRequest request) {
        if (PasswordPolicy.exceedsBcryptLimit(request.password())) {throw invalidCredentials();}

        Optional<User> user = users.findByEmail(User.normalizeEmail(request.email()));
        String passwordHash = user.map(User::getPasswordHash).orElse(dummyPasswordHash);
        boolean passwordMatches = passwordEncoder.matches(request.password(), passwordHash);

        if (!passwordMatches || user.isEmpty()) {throw invalidCredentials();}

        User authenticatedUser = user.orElseThrow();
        TokenService.IssuedToken token = tokens.issue(authenticatedUser.getId());
        UserResponse userResponse = UserResponse.from(authenticatedUser, premiumAccess, clock.instant());

        return new AuthResponse(token.value(), "Bearer", token.expiresAt(), userResponse);
    }

    private ApiException invalidCredentials() {
        return new ApiException(HttpStatus.UNAUTHORIZED, "E-mail ou senha inválidos.");
    }
}
