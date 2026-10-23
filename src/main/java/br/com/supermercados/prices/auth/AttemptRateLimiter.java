package br.com.supermercados.prices.auth;

import br.com.supermercados.prices.common.ApiException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

/**
 * Fixed windows kept in memory: they reset when the API restarts. Deliberate for a single instance (see
 * docs/operations.md, "Limite de tentativas da API"); the Nginx limit on /api/v1/auth/ survives deploys.
 */
@Component
class AttemptRateLimiter {

    private static final int CLEANUP_THRESHOLD = 10_000;

    private final ConcurrentHashMap<String, Window> attempts = new ConcurrentHashMap<>();
    private final Clock clock;

    AttemptRateLimiter(Clock clock) {
        this.clock = clock;
    }

    void check(String scope, String key, int limit, Duration duration) {
        Instant now = clock.instant();
        AtomicBoolean rejected = new AtomicBoolean();
        String attemptKey = scope + ":" + key;
        attempts.compute(attemptKey, (ignored, current) -> {
            if (current == null || !current.startedAt().plus(duration).isAfter(now)) {
                return new Window(now, 1);
            }
            if (current.count() >= limit) {
                rejected.set(true);
                return current;
            }
            return new Window(current.startedAt(), current.count() + 1);
        });
        cleanupExpired(now);
        if (rejected.get()) {
            throw new ApiException(HttpStatus.TOO_MANY_REQUESTS,
                    "Limite de tentativas excedido. Tente novamente mais tarde.");
        }
    }

    private void cleanupExpired(Instant now) {
        if (attempts.size() <= CLEANUP_THRESHOLD) {
            return;
        }
        attempts.entrySet().removeIf(entry -> entry.getValue().startedAt().plus(Duration.ofHours(1)).isBefore(now));
    }

    private record Window(Instant startedAt, int count) {
    }
}
