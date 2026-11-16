package br.com.supermercados.prices.product;

import java.math.BigDecimal;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

import org.springframework.http.HttpStatus;

import br.com.supermercados.prices.common.ApiException;

public record ProductSearch(
        String query,
        String brand,
        String gtin,
        String category,
        UUID storeId,
        String unit,
        BigDecimal quantity,
        String sort) {

    public ProductSearch(
        String query,
        String brand,
        String gtin,
        String category) {

        this(
            query,
            brand,
            gtin,
            category,
            null,
            null,
            null,
            "relevance");
    }

    public ProductSearch {
        query = normalize(query, 200);
        brand = normalize(brand, 120);
        gtin = Gtin.normalize(gtin);
        category = normalize(category, 120);
        unit = normalize(unit, 30);
        sort = sort == null ? "relevance" : sort;

        if (!Set.of("relevance", "name", "price_asc", "price_desc").contains(sort)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Ordenação inválida");
        }

        if (quantity != null && (quantity.signum() <= 0 || quantity.precision() > 14)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Quantidade de busca inválida");
        }
    }

    ProductSearch inStore(UUID id) {
        return new ProductSearch(
            query,
            brand,
            gtin,
            category,
            id,
            unit,
            quantity,
            sort);
    }

    private static String normalize(String value, int maxLength) {
        if (value == null || value.isBlank()) {return null;}

        if (value.length() > maxLength) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Filtro de busca excede o tamanho permitido");
        }

        return value.strip().toLowerCase(Locale.ROOT);
    }
}
