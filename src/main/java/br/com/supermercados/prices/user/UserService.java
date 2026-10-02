package br.com.supermercados.prices.user;

import br.com.supermercados.prices.common.ApiException;
import java.time.Clock;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserService {

    private final UserRepository users;
    private final Clock clock;

    public UserService(UserRepository users, Clock clock) {
        this.users = users;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public UserResponse findCurrentUser(UUID userId) {
        return UserResponse.from(requireUser(userId), clock.instant());
    }

    @Transactional
    public UserResponse updateCurrentUser(UUID userId, UpdateUserRequest request) {
        User user = requireUser(userId);
        user.rename(request.name(), clock.instant());
        return UserResponse.from(user, clock.instant());
    }

    private User requireUser(UUID userId) {
        return users.findById(userId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Usuário não encontrado."));
    }
}
