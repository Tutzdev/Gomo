package br.com.supermercados.prices.auth;

import br.com.supermercados.prices.common.ApiException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

@Component
class AttemptRateLimiter {

    private static final int CLEANUP_THRESHOLD = 10_000;
    private static final Duration STALE_AFTER = Duration.ofHours(1);

    private final ConcurrentHashMap<String, Window> attempts = new ConcurrentHashMap<>();
    private final Clock clock;

    AttemptRateLimiter(Clock clock) {
        this.clock = clock;
    }

    void check(String scope, String key, int limit, Duration duration) {
        Instant now = clock.instant();
        AtomicBoolean rejected = new AtomicBoolean();

        attempts.compute(scope + ":" + key, (ignored, current) -> {
            if (current == null || current.hasEnded(duration, now)) {return new Window(now, 1);}

            if (current.count() >= limit) {
                rejected.set(true);
                return current;
            }

            return current.withOneMoreAttempt();
        });

        cleanupExpired(now);

        if (rejected.get()) {
            throw new ApiException(
                HttpStatus.TOO_MANY_REQUESTS,
                "Limite de tentativas excedido. Tente novamente mais tarde.");
        }
    }

    private void cleanupExpired(Instant now) {
        if (attempts.size() <= CLEANUP_THRESHOLD) {return;}

        attempts.entrySet().removeIf(entry -> entry.getValue().startedAt().plus(STALE_AFTER).isBefore(now));
    }

    private record Window(Instant startedAt, int count) {

        boolean hasEnded(Duration duration, Instant now) {
            return !startedAt.plus(duration).isAfter(now);
        }

        Window withOneMoreAttempt() {
            return new Window(startedAt, count + 1);
        }
    }
}
