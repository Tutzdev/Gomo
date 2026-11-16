package br.com.supermercados.prices.auth;

import br.com.supermercados.prices.user.UserRole;
import java.util.UUID;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

public record AuthenticatedUser(UUID id, UserRole role, boolean emailVerified, boolean premium) {

    public AuthenticatedUser(UUID id) {
        this(id, UserRole.USER, false, false);
    }

    public AuthenticatedUser(UUID id, UserRole role, boolean emailVerified) {
        this(id, role, emailVerified, false);
    }

    public static boolean hasPremium(AuthenticatedUser user) {
        return user != null && user.premium();
    }

    public SimpleGrantedAuthority authority() {
        return new SimpleGrantedAuthority("ROLE_" + role.name());
    }
}
