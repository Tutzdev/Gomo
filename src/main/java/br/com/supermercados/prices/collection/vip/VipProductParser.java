package br.com.supermercados.prices.collection.vip;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URI;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

import br.com.supermercados.prices.collection.CollectedProduct;
import br.com.supermercados.prices.common.ApiException;
import br.com.supermercados.prices.price.StockAvailability;
import br.com.supermercados.prices.product.Gtin;
import tools.jackson.databind.JsonNode;

@Component
public class VipProductParser {

    private static final BigDecimal CENT = new BigDecimal("0.01");
    private static final BigDecimal THOUSAND = BigDecimal.valueOf(1000);
    /** "Mamao Formoso 1,400kg" for a 1,4 kg tray; written weights are rounded, so 2% is tolerated. */
    private static final BigDecimal WEIGHT_TOLERANCE = new BigDecimal("0.02");
    private static final java.util.regex.Pattern WEIGHT = java.util.regex.Pattern.compile(
            "(?iu)(\\d+(?:[.,]\\d+)?)\\s*(kg|kilos?|quilos?|gramas?|grs?|g)(?![\\p{L}\\d])");
    /** Trays weighed at the till: "Bandeja 800g", "(aproximadamente 2 Unids)". */
    private static final java.util.regex.Pattern APPROXIMATE = java.util.regex.Pattern.compile(
            "(?iu)\\(\\s*aprox[^)]*\\)?|\\baprox(?:imadamente|\\.)?\\b[^)]*|\\bbandeja\\b");

    public ParsedProducts parse(List<JsonNode> entries, String sourcePrefix, String sourceName) {
        return parse(entries, sourcePrefix, sourceName, null);
    }

    public ParsedProducts parse(List<JsonNode> entries, String sourcePrefix, String sourceName, URI website) {
        Map<String, CollectedProduct> products = new LinkedHashMap<>();
        List<String> warnings = new ArrayList<>();
        for (JsonNode entry : entries) {
            try {
                CollectedProduct product = parseProduct(entry, sourcePrefix, sourceName, website);
                CollectedProduct previous = products.putIfAbsent(product.sourceReference(), product);
                if (previous != null && !previous.equals(product)) {
                    throw new IllegalArgumentException("produto repetido com conteúdo divergente");
                }
            } catch (IllegalArgumentException exception) {
                warnings.add("Produto " + sourceName + " " + entry.path("produto_id").asString("sem identificador")
                        + " ignorado: " + exception.getMessage());
            }
        }
        return new ParsedProducts(List.copyOf(products.values()), List.copyOf(warnings));
    }

    private CollectedProduct parseProduct(JsonNode entry, String sourcePrefix, String sourceName, URI website) {
        String id = required(entry, "produto_id");
        String name = required(entry, "descricao");
        String unit = required(entry, "unidade_sigla");
        Weighed weighed = null;
        if ("KG".equals(unit)) {
            weighed = weighed(entry, name);
            name = weighed.name();
        } else if (!"UN".equals(unit) || entry.path("possui_unidade_diferente").asBoolean()) {
            throw new IllegalArgumentException("unidade de venda exige revisão antes de comparar embalagens");
        }
        BigDecimal regular = weighed == null ? price(entry.path("preco")) : weighed.price(price(entry.path("preco")));
        BigDecimal promotion = null;
        String condition = null;
        JsonNode offer = entry.path("oferta");
        if (entry.path("em_oferta").asBoolean() && offer.isObject()) {
            BigDecimal offered = price(offer.path("preco_oferta"));
            BigDecimal previous = price(offer.path("preco_antigo"));
            if (weighed != null) {
                offered = weighed.price(offered);
                previous = weighed.price(previous);
            }
            if (previous.compareTo(regular) > 0) {
                regular = previous;
            }
            if (offered.compareTo(regular) < 0) {
                promotion = offered;
                if (offer.path("tipo_oferta_id").asInt() != 1
                        || offer.path("quantidade_minima").asInt() > 1
                        || !"G".equals(offer.path("categoria").asString())) {
                    condition = "Oferta " + sourceName + ": " + offer.path("nome").asString("condição específica")
                            + "; quantidade mínima " + offer.path("quantidade_minima").asString("não informada");
                }
            }
        }
        StockAvailability availability = StockAvailability.UNKNOWN;
        if (entry.path("disponivel").isBoolean()) {
            availability = entry.path("disponivel").asBoolean()
                    ? StockAvailability.AVAILABLE : StockAvailability.UNAVAILABLE;
        }
        String gtin;
        try {
            gtin = Gtin.normalize(entry.path("codigo_barras").asString(null));
        } catch (ApiException exception) {
            // The platform also puts internal ERP codes in codigo_barras; these are not universal identifiers.
            gtin = null;
        }
        String brand = entry.path("marca").isString() ? entry.path("marca").asString(null) : null;
        String filename = entry.path("imagem").asString("");
        String image = filename.matches("[A-Za-z0-9_-]+\\.(?:jpg|jpeg|png|webp)")
                ? "https://produto-assets-vipcommerce-com-br.br-se1.magaluobjects.com/250x250/" + filename : null;
        String slug = entry.path("link").asString("");
        String origin = website != null && "https".equals(website.getScheme()) && id.matches("[0-9]+") && slug.matches("[a-z0-9-]+")
                ? website.resolve("/produto/" + id + "/" + slug).toString() : null;
        return new CollectedProduct(sourcePrefix + ":product:" + id, name, gtin, brand, null,
                null, regular, promotion, condition, null, null, availability, image, origin);
    }

    /**
     * An item sold by weight ("KG"). With {@code possui_unidade_diferente} the price is for a package of
     * {@code quantidade_unidade_diferente} kg ("Queijo Provolone Regina 300g", R$ 39,00 = 0,3 × R$ 130/kg);
     * without it the price is per kilogram. A tray of approximate weight ("Banana Prata Bandeja 800g",
     * "Abacate Bandeja 700g (aproximadamente 1 Unid)") is weighed at the till, so it is published per kilogram,
     * like the loose produce of the other markets. The weight written in the name must agree with the weight
     * the price refers to; otherwise the price basis is unknown and the item is left for review.
     */
    private Weighed weighed(JsonNode entry, String name) {
        boolean packaged = entry.path("possui_unidade_diferente").asBoolean();
        BigDecimal kilograms = packaged ? positive(entry.path("quantidade_unidade_diferente")) : BigDecimal.ONE;
        if (kilograms == null) throw new IllegalArgumentException("peso da embalagem ausente ou inválido");
        BigDecimal perKilogram = null;
        JsonNode fraction = entry.path("unidade_fracao");
        if (fraction.isObject() && "KG".equals(fraction.path("sigla").asString(""))) {
            perKilogram = positive(fraction.path("preco"));
            BigDecimal fractionWeight = positive(fraction.path("quantidade"));
            if (fractionWeight != null && fractionWeight.compareTo(kilograms) != 0) {
                throw new IllegalArgumentException("peso da embalagem diverge entre os campos da loja");
            }
        }
        BigDecimal packagePrice = price(entry.path("preco"));
        if (perKilogram != null && packagePrice.subtract(kilograms.multiply(perKilogram)).abs().compareTo(CENT) > 0) {
            throw new IllegalArgumentException("preço da embalagem não corresponde ao preço por kg");
        }
        if (perKilogram == null && kilograms.compareTo(BigDecimal.ONE) != 0) {
            throw new IllegalArgumentException("preço por peso sem base de cálculo confirmada");
        }
        BigDecimal kilogramPrice = perKilogram != null ? perKilogram
                : packagePrice.divide(kilograms, 2, RoundingMode.HALF_UP);
        if (kilogramPrice.compareTo(BigDecimal.valueOf(300)) >= 0
                && kilogramPrice.remainder(BigDecimal.valueOf(100)).signum() == 0) {
            throw new IllegalArgumentException("preço por kg de marcação da loja, não é preço de venda");
        }

        BigDecimal grams = kilograms.multiply(THOUSAND);
        List<BigDecimal> written = writtenWeights(name);
        if (written.stream().anyMatch(weight -> weight.subtract(grams).abs().compareTo(grams.multiply(WEIGHT_TOLERANCE)) > 0)) {
            throw new IllegalArgumentException("peso do nome diverge do peso vendido");
        }
        if (APPROXIMATE.matcher(name).find()) {
            String base = APPROXIMATE.matcher(WEIGHT.matcher(name).replaceAll(" ")).replaceAll(" ")
                    .replaceAll("\\s+", " ").strip();
            return new Weighed(base + " (preço de 1 kg)", kilograms);
        }
        if (written.isEmpty()) {
            String label;
            if (kilograms.compareTo(BigDecimal.ONE) == 0) label = "(preço de 1 kg)";
            else if (kilograms.compareTo(BigDecimal.ONE) > 0) label = kilograms.stripTrailingZeros().toPlainString().replace('.', ',') + " kg";
            else label = grams.stripTrailingZeros().toPlainString() + " g";
            return new Weighed(name + " " + label, BigDecimal.ONE);
        }
        return new Weighed(name, BigDecimal.ONE);
    }

    /** Every weight written in a description, in grams. */
    private static List<BigDecimal> writtenWeights(String name) {
        List<BigDecimal> grams = new ArrayList<>();
        var matcher = WEIGHT.matcher(name);
        while (matcher.find()) {
            String written = matcher.group(1);
            String unit = matcher.group(2).toLowerCase(java.util.Locale.ROOT);
            boolean kilograms = unit.startsWith("k") || unit.startsWith("q");
            // "Lingua Friboi 1,400g": grams written with a thousands separator.
            if (!kilograms && written.matches("\\d{1,2}[.,]\\d{3}")) written = written.replaceAll("[.,]", "");
            BigDecimal amount = new BigDecimal(written.replace(',', '.'));
            grams.add(kilograms ? amount.multiply(THOUSAND) : amount);
        }
        return grams;
    }

    private static BigDecimal positive(JsonNode node) {
        if (!node.isNumber() && !node.isString()) return null;
        try {
            BigDecimal value = new BigDecimal(node.asString(""));
            return value.signum() > 0 ? value : null;
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    /**
     * How a weighed item is published: its name, and the weight in kg its price is divided by
     * (1 for a package priced as a whole; the tray weight for a tray converted to its price per kg).
     */
    private record Weighed(String name, BigDecimal divisor) {
        BigDecimal price(BigDecimal published) {
            return divisor.compareTo(BigDecimal.ONE) == 0 ? published : published.divide(divisor, 2, RoundingMode.HALF_UP);
        }
    }

    private String required(JsonNode node, String field) {
        String value = node.path(field).asString("").strip();
        if (value.isEmpty()) {
            throw new IllegalArgumentException("campo " + field + " ausente");
        }
        return value;
    }

    private BigDecimal price(JsonNode node) {
        try {
            BigDecimal price = new BigDecimal(node.asString("")).setScale(2, RoundingMode.UNNECESSARY);
            if (price.signum() <= 0) {
                throw new IllegalArgumentException("preço deve ser positivo");
            }
            return price;
        } catch (NumberFormatException | ArithmeticException exception) {
            throw new IllegalArgumentException("preço ausente ou inválido", exception);
        }
    }

    public record ParsedProducts(List<CollectedProduct> products, List<String> warnings) {
    }
}
