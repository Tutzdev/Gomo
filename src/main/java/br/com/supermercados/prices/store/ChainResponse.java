package br.com.supermercados.prices.store;

import java.time.Instant;
import java.util.UUID;

public record ChainResponse(
        UUID id,
        String name,
        UUID sourceId,
        String sourceReference,
        Instant collectedAt,
        Instant updatedAt) {

    static ChainResponse from(SupermarketChain chain) {
        return new ChainResponse(
            chain.getId(),
            chain.getName(),
            chain.getSourceId(),
            chain.getSourceReference(),
            chain.getCollectedAt(),
            chain.getUpdatedAt());
    }
}
