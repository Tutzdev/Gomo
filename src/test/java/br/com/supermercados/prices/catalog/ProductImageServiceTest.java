package br.com.supermercados.prices.catalog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.net.URI;
import java.nio.file.Path;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ProductImageServiceTest {

    @TempDir Path cache;

    private ProductImageService service() {
        return new ProductImageService("phygital-files.mercafacil.com, Other.Example.com", cache);
    }

    @Test
    void onlyHttpsPhotosOfTheListedHostsAreFetched() {
        var images = service();

        assertThat(images.accepts(URI.create("https://phygital-files.mercafacil.com/a/b.png"))).isTrue();
        assertThat(images.accepts(URI.create("https://other.example.com/x.jpg"))).isTrue();
        assertThat(images.accepts(URI.create("http://phygital-files.mercafacil.com/a/b.png"))).isFalse();
        assertThat(images.accepts(URI.create("https://evil.example.com/a.png"))).isFalse();
        assertThat(images.accepts(URI.create("https://user@phygital-files.mercafacil.com/a.png"))).isFalse();
        assertThat(images.accepts(URI.create("https://phygital-files.mercafacil.com:8443/a.png"))).isFalse();
        assertThat(images.accepts(URI.create("https://127.0.0.1/a.png"))).isFalse();
        assertThatThrownBy(() -> images.thumbnail(URI.create("https://evil.example.com/a.png"), 96))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void widthsSnapToAFewCachedSizes() {
        assertThat(ProductImageService.snap(40)).isEqualTo(96);
        assertThat(ProductImageService.snap(96)).isEqualTo(96);
        assertThat(ProductImageService.snap(97)).isEqualTo(160);
        assertThat(ProductImageService.snap(224)).isEqualTo(240);
        assertThat(ProductImageService.snap(1600)).isEqualTo(480);
    }

    @Test
    void scalingKeepsTheProportionNeverEnlargesAndPaintsTransparencyWhite() throws Exception {
        BufferedImage original = new BufferedImage(1000, 500, BufferedImage.TYPE_INT_ARGB);

        BufferedImage small = ProductImageService.scale(original, 160);
        BufferedImage tiny = ProductImageService.scale(new BufferedImage(50, 80, BufferedImage.TYPE_INT_ARGB), 160);

        assertThat(small.getWidth()).isEqualTo(160);
        assertThat(small.getHeight()).isEqualTo(80);
        assertThat(small.getRGB(10, 10) & 0xFFFFFF).isEqualTo(0xFFFFFF);
        assertThat(tiny.getWidth()).isEqualTo(50);
        assertThat(tiny.getHeight()).isEqualTo(80);
        BufferedImage decoded = ImageIO.read(new ByteArrayInputStream(ProductImageService.jpeg(small)));
        assertThat(decoded.getWidth()).isEqualTo(160);
    }
}
