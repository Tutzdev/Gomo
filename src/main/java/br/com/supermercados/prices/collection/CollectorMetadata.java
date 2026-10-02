package br.com.supermercados.prices.collection;

import java.time.Instant;
import java.util.UUID;

public record CollectorMetadata(
        String code,
        String supermarketName,
        String storeName,
        String sourceCode,
        String sourceName,
        String sourceBaseUrl,
        Instant sourceVerifiedAt,
        String chainName,
        String chainSourceReference,
        String storeSourceReference,
        UUID cityId,
        String storeDirectorySourceCode) {

    public CollectorMetadata(String code, String supermarketName, String storeName, String sourceCode,
            String sourceName, String sourceBaseUrl, Instant sourceVerifiedAt, String chainName,
            String chainSourceReference, String storeSourceReference, UUID cityId) {
        this(code, supermarketName, storeName, sourceCode, sourceName, sourceBaseUrl, sourceVerifiedAt,
                chainName, chainSourceReference, storeSourceReference, cityId, null);
    }
}
