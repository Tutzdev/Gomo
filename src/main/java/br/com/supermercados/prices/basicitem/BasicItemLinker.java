package br.com.supermercados.prices.basicitem;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.com.supermercados.prices.collection.CollectionCompletedListener;

@Service
@Order(Ordered.HIGHEST_PRECEDENCE + 1)
public class BasicItemLinker implements CollectionCompletedListener {

    private static final Logger LOGGER = LoggerFactory.getLogger(BasicItemLinker.class);

    private static final String PRICED_PRODUCTS = """
            SELECT p.id, p.name, p.package_description, p.quantity, p.unit
            FROM products p
            WHERE EXISTS (SELECT 1 FROM latest_prices latest
                          JOIN stores s ON s.id = latest.store_id AND s.active
                          WHERE latest.product_id = p.id)
            """;

    private static final String INSERT_LINK = """
            INSERT INTO basic_item_products (
                product_id,
                basic_item,
                package_amount)
            VALUES (?, ?, ?)
            """;

    private final JdbcTemplate jdbc;
    private final boolean linkOnStart;

    public BasicItemLinker(JdbcTemplate jdbc, @Value("${app.catalog.rebuild-on-start:true}") boolean linkOnStart) {
        this.jdbc = jdbc;
        this.linkOnStart = linkOnStart;
    }

    @Override
    public void collectionCompleted() {
        relink();
    }

    @EventListener(ApplicationReadyEvent.class)
    public void onStart() {
        if (!linkOnStart) {return;}

        Thread.ofVirtual().name("basic-items-startup").start(this::relinkSafely);
    }

    @Transactional
    public synchronized Summary relink() {
        List<Link> links = new ArrayList<>();

        jdbc.query(PRICED_PRODUCTS, row -> {
            String name = row.getString("name");
            BasicItem item = BasicItems.match(name).orElse(null);

            if (item == null) {return;}

            BigDecimal packageAmount = PackageSize.of(
                item.measure(),
                name,
                row.getString("package_description"),
                row.getBigDecimal("quantity"),
                row.getString("unit")).orElse(null);

            links.add(new Link(row.getObject("id", UUID.class), item, packageAmount));
        });

        jdbc.update("DELETE FROM basic_item_products");

        jdbc.batchUpdate(INSERT_LINK, links, 500, (statement, link) -> {
            statement.setObject(1, link.productId());
            statement.setString(2, link.item().slug());
            statement.setBigDecimal(3, link.packageAmount());
        });

        Map<String, Long> perItem = links.stream()
                .collect(Collectors.groupingBy(link -> link.item().slug(), TreeMap::new, Collectors.counting()));

        LOGGER.info("Itens básicos religados: {} produtos em {} itens {}", links.size(), perItem.size(), perItem);

        return new Summary(links.size(), perItem);
    }

    private void relinkSafely() {
        try {
            relink();
        } catch (RuntimeException exception) {
            LOGGER.error("Ligação inicial dos itens básicos falhou: {}", exception.getMessage(), exception);
        }
    }

    public record Summary(int linkedProducts, Map<String, Long> productsPerItem) {}

    private record Link(UUID productId, BasicItem item, BigDecimal packageAmount) {}
}
