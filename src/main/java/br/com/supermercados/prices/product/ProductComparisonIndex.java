package br.com.supermercados.prices.product;

import java.util.UUID;

import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class ProductComparisonIndex implements SmartInitializingSingleton {

    private final JdbcTemplate jdbc;

    @Override
    public void afterSingletonsInstantiated() {
        rebuild();
    }

    public void rebuild() {
        while (true) {
            var batch = jdbc.query("""
                    SELECT id, name, version FROM products
                    WHERE comparison_identity_version < 4 ORDER BY id LIMIT 500
                    """, (row, index) -> new Entry(row.getObject("id", UUID.class),
                    ProductComparisonIdentity.searchFamily(row.getString("name")), row.getLong("version")));
            if (batch.isEmpty()) return;
            // Do not overwrite a collector's concurrent update or alter observation timestamps.
            jdbc.batchUpdate("""
                    UPDATE products SET comparison_family = ?, comparison_identity_version = 4
                    WHERE id = ? AND version = ? AND comparison_identity_version < 4
                    """, batch, 500, (statement, entry) -> {
                statement.setString(1, entry.family());
                statement.setObject(2, entry.id());
                statement.setLong(3, entry.version());
            });
        }
    }

    private record Entry(UUID id, String family, long version) {
    }
}
