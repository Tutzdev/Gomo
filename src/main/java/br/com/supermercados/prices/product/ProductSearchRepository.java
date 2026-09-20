package br.com.supermercados.prices.product;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;

import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;

@Repository
public class ProductSearchRepository {

    private static final Set<String> STOP_WORDS = Set.of("de", "do", "da", "dos", "das", "e", "com", "para");
    private final EntityManager entityManager;
    private final Clock clock;
    private final Duration maxPriceAge;

    public ProductSearchRepository(EntityManager entityManager, Clock clock,
            @Value("${app.prices.max-age:P2D}") Duration maxPriceAge) {
        this.entityManager = entityManager;
        this.clock = clock;
        this.maxPriceAge = maxPriceAge;
    }

    public Page<ProductResponse> search(ProductSearch search, Pageable pageable) {
        SearchSql sql = prepare(search);
        long total = ((Number) bind(entityManager.createNativeQuery(
                "SELECT count(*) FROM products p WHERE " + sql.where()), sql.parameters()).getSingleResult()).longValue();
        String order = order(search, sql);
        Query query = entityManager.createNativeQuery("SELECT p.* FROM products p WHERE " + sql.where()
                + " ORDER BY " + order + ", p.search_name, p.name, p.id", Product.class);
        bind(query, sql.parameters());
        if (search.sort().startsWith("price_")) {
            query.setParameter("now", clock.instant());
            query.setParameter("freshAfter", clock.instant().minus(maxPriceAge));
        }
        query.setFirstResult(Math.toIntExact(pageable.getOffset()));
        query.setMaxResults(pageable.getPageSize());
        List<?> rows = query.getResultList();
        List<ProductResponse> products = rows.stream()
                .map(product -> ProductResponse.from((Product) product)).toList();
        return new PageImpl<>(products, pageable, total);
    }

    /** Pagination belongs after equivalence grouping, not before it. */
    public List<Product> findCandidates(ProductSearch search) {
        SearchSql sql = prepare(search);
        Query query = bind(entityManager.createNativeQuery("SELECT p.* FROM products p WHERE " + sql.where()
                + " ORDER BY " + sql.relevance() + ", p.search_name, p.name, p.id", Product.class), sql.parameters());
        return query.getResultList().stream().map(Product.class::cast).toList();
    }

    public ProductSearchFacets facets(ProductSearch search) {
        SearchSql sql = prepare(search);
        List<String> brands = distinctText(sql, "brand");
        List<String> categories = distinctText(sql, "category");
        List<?> measurements = bind(entityManager.createNativeQuery(
                "SELECT DISTINCT p.unit, p.quantity FROM products p WHERE " + sql.where()
                        + " AND p.unit IS NOT NULL AND p.quantity IS NOT NULL ORDER BY p.unit, p.quantity LIMIT 40"),
                sql.parameters()).getResultList();
        List<ProductSearchFacets.Measurement> sizes = measurements.stream().map(row -> {
            Object[] columns = (Object[]) row;
            return new ProductSearchFacets.Measurement((String) columns[0], (BigDecimal) columns[1]);
        }).toList();
        List<?> markets = bind(entityManager.createNativeQuery(
                "SELECT DISTINCT s.id, s.name FROM stores s JOIN price_records r ON r.store_id = s.id "
                        + "JOIN products p ON p.id = r.product_id WHERE s.active AND " + sql.where()
                        + " ORDER BY s.name, s.id LIMIT 40"), sql.parameters()).getResultList();
        List<ProductSearchFacets.Market> stores = markets.stream().map(row -> {
            Object[] columns = (Object[]) row;
            return new ProductSearchFacets.Market((UUID) columns[0], (String) columns[1]);
        }).toList();
        return new ProductSearchFacets(brands, categories, sizes, stores);
    }

    private List<String> distinctText(SearchSql sql, String column) {
        List<?> rows = bind(entityManager.createNativeQuery("SELECT DISTINCT p." + column
                + " FROM products p WHERE " + sql.where() + " AND p." + column
                + " IS NOT NULL ORDER BY p." + column + " LIMIT 40"), sql.parameters())
                .getResultList();
        return rows.stream().map(String.class::cast).toList();
    }

    private SearchSql prepare(ProductSearch search) {
        // Transaction-local: pooled connections must not retain a relaxed fuzzy threshold.
        entityManager.createNativeQuery("SELECT set_config('pg_trgm.word_similarity_threshold', '0.3', true)")
                .getSingleResult();
        ProductSearchTerms terms = ProductSearchTerms.parse(search.query());
        String normalized = (String) entityManager.createNativeQuery("SELECT gomo_search_text(:text)")
                .setParameter("text", terms.text()).getSingleResult();
        List<String> tokens = Arrays.stream(normalized.split(" "))
                .filter(token -> !token.isBlank() && !STOP_WORDS.contains(token)).distinct().toList();
        Map<String, Object> parameters = new LinkedHashMap<>();
        List<String> conditions = new ArrayList<>();
        List<String> scores = new ArrayList<>();
        List<String> exact = new ArrayList<>();

        if (search.query() != null && search.query().matches(".*[%_!].*")) {
            conditions.add("lower(p.name) LIKE :literal ESCAPE '!'");
            parameters.put("literal", "%" + search.query().replace("!", "!!").replace("%", "!%").replace("_", "!_") + "%");
        } else {
            for (int index = 0; index < tokens.size(); index++) {
                String token = tokens.get(index);
                boolean categoryHint = tokens.size() > 1 && Set.of("refrigerante", "carne").contains(token);
                addToken(token, index, conditions, scores, exact, parameters, categoryHint);
            }
            if (exact.size() > 1) {
                conditions.add("(" + String.join(" + ", exact) + ") >= " + (exact.size() - 1));
            }
        }
        addFilter("lower(p.brand)", "brand", search.brand(), conditions, parameters);
        addFilter("lower(p.category)", "category", search.category(), conditions, parameters);
        addFilter("p.gtin", "gtin", search.gtin(), conditions, parameters);
        addFilter("lower(p.unit)", "unit", search.unit(), conditions, parameters);
        addFilter("p.quantity", "quantity", search.quantity(), conditions, parameters);
        if (search.storeId() != null) {
            conditions.add("EXISTS (SELECT 1 FROM price_records r WHERE r.product_id = p.id AND r.store_id = :storeId)");
            parameters.put("storeId", search.storeId());
        }
        // Size is a strong ranking signal, not a reason to hide other sizes.
        String measurementScore = "0";
        if (terms.quantity() != null) {
            // Numeric literals originate in BigDecimal, never in unescaped input.
            measurementScore = "CASE WHEN p.unit = '" + terms.unit() + "' AND p.quantity = "
                    + terms.quantity().toPlainString() + " THEN 500 ELSE 0 END";
            if (tokens.isEmpty()) {
                conditions.add("p.unit = '" + terms.unit() + "' AND p.quantity = " + terms.quantity().toPlainString());
            }
        }
        if (!tokens.isEmpty() && (search.query() == null || !search.query().matches(".*[%_!].*"))) {
            String phrase = String.join(" ", tokens);
            scores.add("CASE WHEN p.search_name = '" + phrase + "' THEN 2000 "
                    + "WHEN p.search_name LIKE '" + phrase + " %' THEN 100 ELSE 0 END");
        }
        String score = String.join(" + ", scores);
        if (score.isEmpty()) {
            score = "0";
        }
        String kitOrder = tokens.isEmpty() || tokens.contains("kit") || tokens.contains("pack") ? ""
                : "CASE WHEN p.search_name ~ '\\m(kit|pack|combo|mista|alcoolica)\\M' THEN 1 ELSE 0 END, ";
        String exactOrder = exact.isEmpty() ? "" : "(" + String.join(" + ", exact) + ") DESC, ";
        return new SearchSql(conditions.isEmpty() ? "TRUE" : String.join(" AND ", conditions), parameters,
                kitOrder + exactOrder + "(" + measurementScore + " + " + score + ") DESC");
    }

    private void addToken(String token, int index, List<String> conditions, List<String> scores,
            List<String> exact, Map<String, Object> parameters, boolean categoryHint) {
        String key = "token" + index;
        String pattern = "pattern" + index;
        parameters.put(pattern, "%" + token + "%");
        String match = "(p.search_text LIKE :" + pattern + " OR p.gtin LIKE :" + pattern + ")";
        if (categoryHint) {
            // Retailers sometimes omit the generic product type from an otherwise precise name.
            conditions.add("(:" + pattern + " IS NOT NULL)");
        } else if (token.length() >= 4 && !token.matches("[0-9]+")) {
            parameters.put(key, token);
            conditions.add("(" + match + " OR (p.search_name %> :" + key
                    + " AND EXISTS (SELECT 1 FROM regexp_split_to_table(p.search_name, ' ') word "
                    + "WHERE length(word) BETWEEN " + (token.length() - 1) + " AND " + (token.length() + 1)
                    + " AND levenshtein_less_equal(:" + key + ", word, 1) <= 1)))");
        } else {
            conditions.add(match);
        }
        if (!categoryHint) {
            exact.add("CASE WHEN " + match + " THEN 1 ELSE 0 END");
        }
        scores.add("CASE WHEN p.search_name LIKE :" + pattern + " THEN 100 ELSE 0 END");
        scores.add("CASE WHEN p.search_brand LIKE :" + pattern + " THEN 50 ELSE 0 END");
        scores.add("CASE WHEN " + match + " THEN 20 ELSE 0 END");
    }

    private void addFilter(String column, String parameter, Object value, List<String> conditions,
            Map<String, Object> parameters) {
        if (value != null) {
            conditions.add(column + " = :" + parameter);
            parameters.put(parameter, value);
        }
    }

    private String order(ProductSearch search, SearchSql sql) {
        if (search.sort().equals("name")) {
            return "p.name";
        }
        if (!search.sort().startsWith("price_")) {
            return sql.relevance();
        }
        String storeFilter = search.storeId() == null ? "" : " AND r.store_id = :storeId";
        // Same validity/stock/promotion rules as PricePolicy; newest observation wins, including stock-outs.
        return """
                (SELECT min(CASE WHEN latest.promotional_price IS NOT NULL
                    AND latest.promotion_valid_until > :now AND latest.promotion_condition IS NULL
                    THEN latest.promotional_price ELSE latest.regular_price END)
                 FROM (SELECT DISTINCT ON (r.store_id) r.* FROM price_records r
                     JOIN stores s ON s.id = r.store_id AND s.active
                     WHERE r.product_id = p.id
                """ + storeFilter + """
                     ORDER BY r.store_id, r.collected_at DESC, r.recorded_at DESC, r.id DESC) latest
                 WHERE latest.collected_at > :freshAfter AND latest.collected_at <= :now
                     AND (latest.valid_until IS NULL OR latest.valid_until > :now)
                     AND latest.availability <> 'UNAVAILABLE')
                """ + (search.sort().equals("price_desc") ? " DESC" : " ASC") + " NULLS LAST";
    }

    private Query bind(Query query, Map<String, Object> parameters) {
        parameters.forEach(query::setParameter);
        return query;
    }

    private record SearchSql(String where, Map<String, Object> parameters, String relevance) {
    }
}
