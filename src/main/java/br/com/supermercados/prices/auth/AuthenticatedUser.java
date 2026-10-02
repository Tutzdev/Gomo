package br.com.supermercados.prices.auth;

import br.com.supermercados.prices.user.UserRole;
import java.util.UUID;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

/** The caller of a request. {@code premium} is resolved once per request from the account's plan. */
public record AuthenticatedUser(UUID id, UserRole role, boolean emailVerified, boolean premium) {

    public AuthenticatedUser(UUID id) {
        this(id, UserRole.USER, false, false);
    }

    public AuthenticatedUser(UUID id, UserRole role, boolean emailVerified) {
        this(id, role, emailVerified, false);
    }

    /** Anonymous callers of public endpoints get the same view as a free account. */
    public static boolean hasPremium(AuthenticatedUser user) {
        return user != null && user.premium();
    }

    public SimpleGrantedAuthority authority() {
        return new SimpleGrantedAuthority("ROLE_" + role.name());
    }
}
