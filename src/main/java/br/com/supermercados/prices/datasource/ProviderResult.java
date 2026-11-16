package br.com.supermercados.prices.datasource;

import java.util.List;
import java.util.Objects;

public record ProviderResult<T>(Status status, List<T> items, String nextCursor, String message) {

    public enum Status { AVAILABLE, UNAVAILABLE }

    public ProviderResult {
        Objects.requireNonNull(status, "status");
        items = List.copyOf(items);

        if (items.size() > 100) {
            throw new IllegalArgumentException("Provider response exceeds batch limit");
        }

        if (status == Status.UNAVAILABLE
                && (!items.isEmpty() || nextCursor != null || message == null || message.isBlank())) {
            throw new IllegalArgumentException("Unavailable provider must give a reason without fabricated results");
        }
    }

    public static <T> ProviderResult<T> available(List<T> items, String nextCursor) {
        return new ProviderResult<>(Status.AVAILABLE, items, nextCursor, null);
    }

    public static <T> ProviderResult<T> unavailable(String reason) {
        return new ProviderResult<>(Status.UNAVAILABLE, List.of(), null, reason);
    }
}
