package br.com.supermercados.prices.seo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import br.com.supermercados.prices.common.ApiException;

class SitemapTest {

    private static final UUID ID = UUID.fromString("7d0f3c1e-2a4b-4c5d-8e9f-0a1b2c3d4e5f");

    @Test
    void slugIsTheNameWithoutAccentsOrSymbols() {
        assertThat(ProductPagePath.of(ID, "Feijão Carioca Camil 1 kg"))
                .isEqualTo("/precos/feijao-carioca-camil-1-kg-" + ID);
        assertThat(ProductPagePath.slug("  Água de Coco (Kero Coco) 1L!  ")).isEqualTo("agua-de-coco-kero-coco-1l");
        assertThat(ProductPagePath.slug("Leite UHT Integral Italac 1 L — caixa com tampa de rosca e válida até o fim"
                + " do mês, embalagem econômica")).hasSizeLessThanOrEqualTo(80).doesNotEndWith("-");
        assertThat(ProductPagePath.slug("!!!")).isEqualTo("produto");
    }

    @Test
    void addressesAreEscapedForXml() {
        String xml = SitemapService.render("https://gomo.example.test",
                List.of(new SitemapService.Page(ID, "M&M's", Instant.parse("2026-10-20T02:00:00Z"))));

        assertThat(xml).startsWith("<?xml version=\"1.0\" encoding=\"UTF-8\"?>")
                .contains("<loc>https://gomo.example.test/ofertas</loc><changefreq>daily</changefreq>")
                .contains("<loc>https://gomo.example.test/precos/m-m-s-" + ID + "</loc><lastmod>2026-10-20</lastmod>")
                .endsWith("</urlset>\n");
    }

    @Test
    void withoutTheSiteAddressThereIsNoSitemap() {
        var controller = new SitemapController(null, " ");

        assertThatThrownBy(controller::sitemap).isInstanceOfSatisfying(ApiException.class,
                exception -> assertThat(exception.getStatus()).isEqualTo(HttpStatus.NOT_FOUND));
    }

    @Test
    void rejectsASiteAddressThatIsNotAPublicUrl() {
        assertThatThrownBy(() -> new SitemapController(null, "gomo.com.br"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
