package br.com.supermercados.prices.catalog;

import java.net.URI;
import java.time.Duration;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import br.com.supermercados.prices.common.ApiException;

/** Public, like the catalog: a product photo scaled to the size on screen (see {@link ProductImageService}). */
@RestController
class ProductImageController {

    private final ProductImageService images;

    ProductImageController(ProductImageService images) {
        this.images = images;
    }

    @GetMapping("/api/v1/images/products")
    ResponseEntity<byte[]> thumbnail(@RequestParam("src") String src, @RequestParam("w") int width) {
        URI source;
        try {
            source = URI.create(src);
        } catch (IllegalArgumentException exception) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Endereço de foto inválido.");
        }
        if (!images.accepts(source) || width < 1 || width > 2000) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Foto fora dos sites dos mercados atendidos.");
        }
        return images.thumbnail(source, ProductImageService.snap(width))
                .map(jpeg -> ResponseEntity.ok().contentType(MediaType.IMAGE_JPEG)
                        .cacheControl(CacheControl.maxAge(Duration.ofDays(30)).cachePublic().immutable()).body(jpeg))
                // Could not read it (format, size, site down): the browser loads the original as before.
                .orElseGet(() -> ResponseEntity.status(HttpStatus.FOUND).location(source)
                        .cacheControl(CacheControl.maxAge(Duration.ofHours(1)).cachePublic()).build());
    }
}
