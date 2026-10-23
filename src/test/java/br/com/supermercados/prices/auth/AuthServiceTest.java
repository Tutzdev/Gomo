package br.com.supermercados.prices.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import br.com.supermercados.prices.common.ApiException;
import br.com.supermercados.prices.user.User;
import br.com.supermercados.prices.user.UserRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    private static final String PASSWORD = "test-password-123";
    private static final Instant NOW = Instant.parse("2026-09-10T12:00:00Z");

    @Mock
    private UserRepository users;

    @Mock
    private TokenService tokens;

    @Mock
    private AccountService accounts;

    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder(4);
    private AuthService auth;

    @BeforeEach
    void setUp() {
        auth = new AuthService(users, passwordEncoder, tokens, new PasswordPolicy(), accounts,
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void registrationNormalizesEmailAndPersistsOnlyPasswordHash() {
        when(users.saveAndFlush(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        auth.register(new RegisterRequest("  Test User  ", "USER@EXAMPLE.TEST", PASSWORD));

        ArgumentCaptor<User> storedUser = ArgumentCaptor.forClass(User.class);
        verify(users).saveAndFlush(storedUser.capture());
        verify(accounts).issueEmailVerification(storedUser.getValue());
        verify(accounts, never()).notifyExistingAccount(any());
        assertThat(storedUser.getValue().getName()).isEqualTo("Test User");
        assertThat(storedUser.getValue().getEmail()).isEqualTo("user@example.test");
        assertThat(storedUser.getValue().getCreatedAt()).isEqualTo(NOW);
        assertThat(storedUser.getValue().getPasswordHash()).isNotEqualTo(PASSWORD);
        assertThat(passwordEncoder.matches(PASSWORD, storedUser.getValue().getPasswordHash())).isTrue();
    }

    @Test
    void existingEmailNotifiesTheOwnerInsteadOfFailingOrSaving() {
        User owner = new User("Owner", "user@example.test", passwordEncoder.encode(PASSWORD), NOW);
        when(users.findByEmail("user@example.test")).thenReturn(Optional.of(owner));

        auth.register(new RegisterRequest("Test User", "USER@EXAMPLE.TEST", "another-password-123"));

        verify(accounts).notifyExistingAccount(owner);
        verify(accounts, never()).issueEmailVerification(any());
        verify(users, never()).saveAndFlush(any());
        assertThat(passwordEncoder.matches(PASSWORD, owner.getPasswordHash())).isTrue();
    }

    @Test
    void registrationRejectsPasswordExceedingBcryptByteLimit() {
        String password = "é".repeat(37);

        assertThatThrownBy(() -> auth.register(new RegisterRequest("Test User", "user@example.test", password)))
                .isInstanceOfSatisfying(ApiException.class,
                        exception -> assertThat(exception.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST));

        verifyNoInteractions(users, tokens);
    }

    @Test
    void registrationAcceptsPasswordAtExactBcryptByteLimit() {
        when(users.saveAndFlush(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        auth.register(new RegisterRequest("Test User", "user@example.test", "é".repeat(36)));

        verify(users).saveAndFlush(any(User.class));
    }

    @Test
    void registrationRejectsShortPasswords() {
        assertThatThrownBy(() -> auth.register(new RegisterRequest("Test User", "user@example.test", "short")))
                .isInstanceOf(ApiException.class);

        verifyNoInteractions(users, tokens);
    }

    @Test
    void successfulLoginIssuesTokenForAuthenticatedUser() {
        User user = new User("Test User", "user@example.test", passwordEncoder.encode(PASSWORD), NOW);
        when(users.findByEmail("user@example.test")).thenReturn(Optional.of(user));
        when(tokens.issue(user.getId())).thenReturn(new TokenService.IssuedToken("test-token", NOW.plusSeconds(3600)));

        var response = auth.login(new LoginRequest("USER@EXAMPLE.TEST", PASSWORD));

        assertThat(response.accessToken()).isEqualTo("test-token");
        assertThat(response.tokenType()).isEqualTo("Bearer");
        assertThat(response.expiresAt()).isEqualTo(NOW.plusSeconds(3600));
        assertThat(response.user().id()).isEqualTo(user.getId());
    }

    @Test
    void wrongPasswordDoesNotIssueToken() {
        User user = new User("Test User", "user@example.test", passwordEncoder.encode(PASSWORD), NOW);
        when(users.findByEmail("user@example.test")).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> auth.login(new LoginRequest("user@example.test", "incorrect-password")))
                .isInstanceOfSatisfying(ApiException.class,
                        exception -> assertThat(exception.getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED))
                .hasMessage("E-mail ou senha inválidos.");

        verifyNoInteractions(tokens);
    }

    @Test
    void unknownUserReturnsSameCredentialError() {
        when(users.findByEmail("unknown@example.test")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> auth.login(new LoginRequest("unknown@example.test", PASSWORD)))
                .isInstanceOfSatisfying(ApiException.class,
                        exception -> assertThat(exception.getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED))
                .hasMessage("E-mail ou senha inválidos.");

        verifyNoInteractions(tokens);
    }

    @Test
    void oversizedUnicodeLoginDoesNotBecomeBcryptServerError() {
        assertThatThrownBy(() -> auth.login(new LoginRequest("user@example.test", "é".repeat(37))))
                .isInstanceOfSatisfying(ApiException.class,
                        exception -> assertThat(exception.getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED));

        verifyNoInteractions(users, tokens);
    }
}
