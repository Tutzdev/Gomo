package br.com.supermercados.prices.auth;

import br.com.supermercados.prices.common.ApiException;
import br.com.supermercados.prices.user.User;
import br.com.supermercados.prices.user.UserRepository;
import br.com.supermercados.prices.user.UserResponse;
import java.nio.charset.StandardCharsets;
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
    private final Clock clock;
    private final String dummyPasswordHash;

    public AuthService(UserRepository users, PasswordEncoder passwordEncoder, TokenService tokens,
            PasswordPolicy passwordPolicy, AccountService accounts, Clock clock) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.tokens = tokens;
        this.passwordPolicy = passwordPolicy;
        this.accounts = accounts;
        this.clock = clock;
        this.dummyPasswordHash = passwordEncoder.encode(UUID.randomUUID().toString());
    }

    @Transactional
    public UserResponse register(RegisterRequest request) {
        passwordPolicy.validate(request.password());
        String email = User.normalizeEmail(request.email());
        if (users.existsByEmail(email)) {
            throw new ApiException(HttpStatus.CONFLICT, "E-mail já cadastrado.");
        }
        User user = new User(request.name(), email, passwordEncoder.encode(request.password()), clock.instant());
        User savedUser = users.saveAndFlush(user);
        accounts.issueEmailVerification(savedUser);
        return UserResponse.from(savedUser, clock.instant());
    }

    public AuthResponse login(LoginRequest request) {
        if (request.password().getBytes(StandardCharsets.UTF_8).length > PasswordPolicy.BCRYPT_MAX_BYTES) {
            throw invalidCredentials();
        }
        Optional<User> user = users.findByEmail(User.normalizeEmail(request.email()));
        String passwordHash = user.map(User::getPasswordHash).orElse(dummyPasswordHash);
        boolean passwordMatches = passwordEncoder.matches(request.password(), passwordHash);
        if (!passwordMatches || user.isEmpty()) {
            throw invalidCredentials();
        }
        User authenticatedUser = user.orElseThrow();
        TokenService.IssuedToken token = tokens.issue(authenticatedUser.getId());
        return new AuthResponse(token.value(), "Bearer", token.expiresAt(), UserResponse.from(authenticatedUser, clock.instant()));
    }

    private ApiException invalidCredentials() {
        return new ApiException(HttpStatus.UNAUTHORIZED, "E-mail ou senha inválidos.");
    }
}
