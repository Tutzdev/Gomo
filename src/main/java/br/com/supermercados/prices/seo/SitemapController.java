package br.com.supermercados.prices.seo;

import java.net.URI;
import java.time.Duration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import br.com.supermercados.prices.common.ApiException;

/**
 * Served under /api because Nginx forwards only that path to the backend; robots.txt points search engines here.
 * A sitemap needs absolute addresses, so without {@code app.site-url} there is none.
 */
@RestController
public class SitemapController {

    private final SitemapService sitemap;
    private final String siteUrl;

    public SitemapController(SitemapService sitemap, @Value("${app.site-url:}") String siteUrl) {
        this.sitemap = sitemap;
        this.siteUrl = normalize(siteUrl);
    }

    @GetMapping(value = "/api/v1/sitemap.xml", produces = MediaType.APPLICATION_XML_VALUE)
    public ResponseEntity<String> sitemap() {
        if (siteUrl.isEmpty()) {
            throw new ApiException(HttpStatus.NOT_FOUND, "Sitemap indisponível: o endereço do site não foi configurado.");
        }
        return ResponseEntity.ok()
                .cacheControl(CacheControl.maxAge(Duration.ofHours(1)).cachePublic())
                .body(sitemap.render(siteUrl));
    }

    private static String normalize(String configured) {
        String url = configured == null ? "" : configured.strip().replaceAll("/+$", "");
        if (url.isEmpty()) return url;
        URI uri = URI.create(url);
        if (!("http".equals(uri.getScheme()) || "https".equals(uri.getScheme())) || uri.getHost() == null
                || uri.getRawQuery() != null || uri.getRawFragment() != null) {
            throw new IllegalArgumentException("app.site-url must be the public HTTP(S) address of the site");
        }
        return url;
    }
}
