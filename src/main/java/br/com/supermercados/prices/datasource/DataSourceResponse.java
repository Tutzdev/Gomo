package br.com.supermercados.prices.datasource;

import java.time.Instant;
import java.util.UUID;

public record DataSourceResponse(
        UUID id,
        String code,
        String name,
        String baseUrl,
        boolean enabled,
        Instant verifiedAt,
        Instant createdAt) {

    public static DataSourceResponse from(DataSource source) {
        return new DataSourceResponse(
            source.getId(),
            source.getCode(),
            source.getName(),
            source.getBaseUrl(),
            source.isEnabled(),
            source.getVerifiedAt(),
            source.getCreatedAt());
    }
}
