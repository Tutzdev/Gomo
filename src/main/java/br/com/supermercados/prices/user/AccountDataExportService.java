package br.com.supermercados.prices.user;

import java.sql.Date;
import java.sql.Timestamp;
import java.time.Clock;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.com.supermercados.prices.common.ApiException;

@Service
public class AccountDataExportService {

    private static final String ACCOUNT = """
            SELECT name AS "nome", email AS "email", created_at AS "contaCriadaEm", updated_at AS "atualizadaEm",
                email_verified_at AS "emailConfirmadoEm", last_login_at AS "ultimoAcessoEm", role AS "papel",
                subscriber AS "assinante", premium_trial_started_at AS "testePremiumComecouEm",
                premium_trial_ends_at AS "testePremiumTerminaEm", preferred_billing_cycle AS "cicloDePagamentoPreferido",
                premium_paid_until AS "premiumPagoAte"
            FROM app_users WHERE id = ?
            """;

    private static final String PREFERRED_CITY = """
            SELECT c.name FROM user_preferences p JOIN cities c ON c.id = p.preferred_city_id WHERE p.user_id = ?
            """;

    private static final String FAVORITE_STORES = """
            SELECT s.name AS "mercado", f.created_at AS "favoritadoEm"
            FROM favorite_stores f JOIN stores s ON s.id = f.store_id WHERE f.user_id = ? ORDER BY f.created_at
            """;

    private static final String PRICE_ALERTS = """
            SELECT p.name AS "produto", c.name AS "cidade",
                CASE a.kind WHEN 'BELOW_AVERAGE' THEN 'abaixo da média' ELSE 'preço escolhido' END AS "tipo",
                a.target_price AS "precoAlvo", a.discount_percent AS "percentualAbaixoDaMedia",
                a.active AS "ativo", a.created_at AS "criadoEm", a.last_notified_at AS "ultimoAvisoEm",
                a.last_notified_price AS "precoDoUltimoAviso"
            FROM price_alerts a JOIN products p ON p.id = a.product_id JOIN cities c ON c.id = a.city_id
            WHERE a.user_id = ? ORDER BY a.created_at
            """;

    private static final String ALERT_NOTIFICATIONS = """
            SELECT p.name AS "produto", s.name AS "mercado", n.unit_price AS "preco", n.created_at AS "avisadoEm",
                n.read_at AS "lidoEm"
            FROM alert_notifications n JOIN price_alerts a ON a.id = n.alert_id
                JOIN products p ON p.id = a.product_id JOIN stores s ON s.id = n.store_id
            WHERE n.user_id = ? ORDER BY n.created_at
            """;

    private static final String PRICE_CONTRIBUTIONS = """
            SELECT p.name AS "produto", s.name AS "mercado", c.regular_price AS "preco",
                c.promotional_price AS "precoPromocional", c.observed_at AS "vistoEm", c.submitted_at AS "enviadoEm",
                c.status AS "situacao", c.rejection_reason AS "motivoDaRecusa"
            FROM price_contributions c JOIN products p ON p.id = c.product_id JOIN stores s ON s.id = c.store_id
            WHERE c.contributor_id = ? ORDER BY c.submitted_at
            """;

    private static final String PRODUCT_SUGGESTIONS = """
            SELECT g.name AS "produto", g.brand AS "marca", g.package_size AS "embalagem",
                g.barcode AS "codigoDeBarras", s.name AS "mercado", g.price AS "preco", g.note AS "observacao",
                g.submitted_at AS "enviadoEm",
                CASE g.status WHEN 'APPROVED' THEN 'aceita' WHEN 'REJECTED' THEN 'recusada' ELSE 'em análise' END
                    AS "situacao",
                g.decided_at AS "analisadaEm", g.rejection_reason AS "motivoDaRecusa"
            FROM product_suggestions g LEFT JOIN stores s ON s.id = g.store_id
            WHERE g.contributor_id = ? ORDER BY g.submitted_at
            """;

    private static final String LISTS_SHARED_WITH_ME = """
            SELECT l.name AS "lista", u.name AS "dona", m.joined_at AS "entreiEm"
            FROM shopping_list_members m JOIN shopping_lists l ON l.id = m.shopping_list_id
                JOIN app_users u ON u.id = l.user_id
            WHERE m.user_id = ? ORDER BY m.joined_at
            """;

    private static final String COMPARISONS_PER_DAY = """
            SELECT usage_date AS "dia", count(*) AS "comparacoes"
            FROM comparison_usage WHERE user_id = ? GROUP BY usage_date ORDER BY usage_date
            """;

    private static final String SUBSCRIPTIONS = """
            SELECT billing_cycle AS "ciclo", status AS "situacao", created_at AS "criadaEm",
                cancelled_at AS "canceladaEm"
            FROM billing_subscriptions WHERE user_id = ? ORDER BY created_at
            """;

    private static final String CHECKOUTS = """
            SELECT billing_cycle AS "ciclo", created_at AS "iniciadoEm"
            FROM billing_checkouts WHERE user_id = ? ORDER BY created_at
            """;

    private static final String OPEN_SESSIONS = """
            SELECT created_at AS "entrouEm", expires_at AS "expiraEm"
            FROM auth_tokens WHERE user_id = ? AND expires_at > ? ORDER BY created_at
            """;

    private final JdbcTemplate jdbc;
    private final Clock clock;

    public AccountDataExportService(JdbcTemplate jdbc, Clock clock) {
        this.jdbc = jdbc;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> export(UUID userId) {
        List<Map<String, Object>> account = rows(ACCOUNT, userId);

        if (account.isEmpty()) {throw new ApiException(HttpStatus.NOT_FOUND, "Usuário não encontrado.");}

        Map<String, Object> export = new LinkedHashMap<>();

        export.put("geradoEm", clock.instant());
        export.put("conta", account.getFirst());
        export.put("cidadePreferida", preferredCity(userId));
        export.put("mercadosFavoritos", rows(FAVORITE_STORES, userId));
        export.put("listasDeCompras", shoppingLists(userId));
        export.put("alertasDePreco", rows(PRICE_ALERTS, userId));
        export.put("avisosDePreco", rows(ALERT_NOTIFICATIONS, userId));
        export.put("precosEnviados", rows(PRICE_CONTRIBUTIONS, userId));
        export.put("produtosSugeridos", rows(PRODUCT_SUGGESTIONS, userId));
        export.put("listasCompartilhadasComigo", rows(LISTS_SHARED_WITH_ME, userId));
        export.put("compras", purchases(userId));
        export.put("comparacoesPorDia", rows(COMPARISONS_PER_DAY, userId));
        export.put("assinaturas", rows(SUBSCRIPTIONS, userId));
        export.put("pagamentosIniciados", rows(CHECKOUTS, userId));
        export.put("sessoesAbertas", rows(OPEN_SESSIONS, userId, Timestamp.from(clock.instant())));

        return export;
    }

    private String preferredCity(UUID userId) {
        return jdbc.queryForList(PREFERRED_CITY, String.class, userId).stream().findFirst().orElse(null);
    }

    private List<Map<String, Object>> shoppingLists(UUID userId) {
        List<Map<String, Object>> lists = jdbc.queryForList("""
                SELECT id, name, shopping_type, created_at, updated_at
                FROM shopping_lists WHERE user_id = ? ORDER BY created_at
                """, userId);

        return lists.stream().map(list -> {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("nome", list.get("name"));
            row.put("tipo", list.get("shopping_type"));
            row.put("criadaEm", readable(list.get("created_at")));
            row.put("atualizadaEm", readable(list.get("updated_at")));

            row.put("itens", rows("""
                    SELECT coalesce(c.display_name, p.name) AS "produto", i.quantity AS "quantidade"
                    FROM shopping_list_items i JOIN products p ON p.id = i.product_id
                        LEFT JOIN catalog_items c ON c.id = i.catalog_item_id
                    WHERE i.shopping_list_id = ? ORDER BY 1
                    """, list.get("id")));

            return row;
        }).toList();
    }

    private List<Map<String, Object>> purchases(UUID userId) {
        List<Map<String, Object>> found = jdbc.queryForList("""
                SELECT p.id, p.list_name, s.name AS store_name, p.purchased_at, p.total_paid, p.compared_items,
                    p.compared_city_average - p.compared_paid AS savings
                FROM purchases p JOIN stores s ON s.id = p.store_id WHERE p.user_id = ? ORDER BY p.purchased_at
                """, userId);

        return found.stream().map(purchase -> {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("lista", purchase.get("list_name"));
            row.put("mercado", purchase.get("store_name"));
            row.put("compradaEm", readable(purchase.get("purchased_at")));
            row.put("totalPago", purchase.get("total_paid"));
            row.put("itensComparados", purchase.get("compared_items"));
            row.put("economiaContraAMediaDaCidade", purchase.get("savings"));

            row.put("itens", rows("""
                    SELECT product_name AS "produto", quantity AS "quantidade", unit_price AS "precoPago",
                        CASE price_source WHEN 'SHELF' THEN 'digitado na gôndola' ELSE 'preço coletado' END AS "origemDoPreco",
                        city_average_price AS "mediaDaCidade"
                    FROM purchase_items WHERE purchase_id = ? ORDER BY product_name
                    """, purchase.get("id")));

            return row;
        }).toList();
    }

    private List<Map<String, Object>> rows(String sql, Object... arguments) {
        return jdbc.queryForList(sql, arguments).stream().map(row -> {
            Map<String, Object> readable = new LinkedHashMap<>();
            row.forEach((column, value) -> readable.put(column, readable(value)));

            return readable;
        }).toList();
    }

    private static Object readable(Object value) {
        if (value instanceof Timestamp timestamp) {return timestamp.toInstant();}

        if (value instanceof Date date) {return date.toLocalDate();}

        return value;
    }
}
