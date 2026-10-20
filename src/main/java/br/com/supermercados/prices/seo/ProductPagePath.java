package br.com.supermercados.prices.seo;

import java.text.Normalizer;
import java.util.Locale;
import java.util.UUID;

/**
 * Address of the public price page of a catalog item: a readable name followed by the id, as in
 * {@code /precos/coca-cola-2-l-<id>}. The frontend builds the same slug (frontend/src/lib/product-page-path.ts).
 */
public final class ProductPagePath {

    private static final int MAXIMUM_SLUG_LENGTH = 80;

    private ProductPagePath() {
    }

    public static String of(UUID itemId, String name) {
        return "/precos/" + slug(name) + "-" + itemId;
    }

    static String slug(String name) {
        String plain = Normalizer.normalize(name == null ? "" : name, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("^-+|-+$", "");
        if (plain.length() > MAXIMUM_SLUG_LENGTH) {
            plain = plain.substring(0, MAXIMUM_SLUG_LENGTH).replaceAll("-+$", "");
        }
        return plain.isEmpty() ? "produto" : plain;
    }
}
