package br.com.supermercados.prices.seo;

import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** The sitemap search engines read: the home page and the public price page of every active catalog item. */
@Service
public class SitemapService {

    /** A sitemap file holds at most 50,000 addresses; the home page takes one of them. */
    static final int MAXIMUM_PRODUCT_PAGES = 49_999;

    private final JdbcTemplate jdbc;

    public SitemapService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional(readOnly = true)
    public String render(String siteUrl) {
        // Products sold in more markets first: if the catalog ever outgrows one file, those stay in it.
        List<Page> pages = jdbc.query("""
                SELECT id, display_name, updated_at FROM catalog_items
                WHERE active ORDER BY store_count DESC, id LIMIT ?
                """, (row, index) -> new Page(row.getObject("id", UUID.class), row.getString("display_name"),
                row.getTimestamp("updated_at").toInstant()), MAXIMUM_PRODUCT_PAGES);
        return render(siteUrl, pages);
    }

    static String render(String siteUrl, List<Page> pages) {
        StringBuilder xml = new StringBuilder("""
                <?xml version="1.0" encoding="UTF-8"?>
                <urlset xmlns="http://www.sitemaps.org/schemas/sitemap/0.9">
                """);
        xml.append("  <url><loc>").append(escape(siteUrl + "/")).append("</loc></url>\n");
        for (Page page : pages) {
            xml.append("  <url><loc>").append(escape(siteUrl + ProductPagePath.of(page.id(), page.name())))
                    .append("</loc><lastmod>").append(page.updatedAt().atOffset(ZoneOffset.UTC).toLocalDate())
                    .append("</lastmod></url>\n");
        }
        return xml.append("</urlset>\n").toString();
    }

    private static String escape(String value) {
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                .replace("\"", "&quot;").replace("'", "&apos;");
    }

    record Page(UUID id, String name, Instant updatedAt) {
    }
}
