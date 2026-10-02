package br.com.supermercados.prices.collection.hortifruti;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.jsoup.Jsoup;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import br.com.supermercados.prices.collection.CollectedCatalog;
import br.com.supermercados.prices.collection.CatalogCheckpoint;
import br.com.supermercados.prices.collection.CollectedProduct;
import br.com.supermercados.prices.collection.CollectedStore;
import br.com.supermercados.prices.collection.CollectorMetadata;
import br.com.supermercados.prices.collection.PublicCatalogHttp;
import br.com.supermercados.prices.collection.SupermarketCollector;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Component
@ConditionalOnProperty(name = "app.collection.regional.enabled", havingValue = "true", matchIfMissing = true)
public class HortifrutiCollector implements SupermarketCollector {

    static final String SELLER = "hortifrutibraterradohf";
    private static final Logger LOGGER = LoggerFactory.getLogger(HortifrutiCollector.class);
    private final ObjectMapper mapper;
    private final Clock clock;
    private final URI website;
    private final Duration delay;

    @Autowired
    public HortifrutiCollector(ObjectMapper mapper, Clock clock) {
        this(mapper, clock, URI.create("https://www.hortifruti.com.br"), Duration.ofSeconds(1));
    }

    HortifrutiCollector(ObjectMapper mapper, Clock clock, URI website, Duration delay) {
        this.mapper = mapper;
        this.clock = clock;
        this.website = website;
        this.delay = delay;
    }

    @Override
    public CollectorMetadata metadata() {
        return new CollectorMetadata("hortifruti_aterrado", "Hortifruti", "Hortifruti Aterrado",
                "hortifruti_public", "E-commerce público Hortifruti", website.toString(),
                Instant.parse("2026-09-18T00:00:00Z"), "Hortifruti", "hortifruti:chain", SELLER,
                UUID.fromString("5c4cb935-52e1-4bf8-8d17-902dc0837c66"));
    }

    @Override
    public CollectedCatalog collect() {
        PublicCatalogHttp http = new PublicCatalogHttp(delay);
        JsonNode pickup = query(http, "ClientPickupPointsQuery", "3fa04e88c811fcb5ece7206fd5aa745bdbc143a8",
                Map.of("geoCoordinates", Map.of("latitude", -22.51, "longitude", -44.09)))
                .path("pickupPoints").path("pickupPointDistances");
        JsonNode store = null;
        for (JsonNode entry : pickup) {
            if ((SELLER + "_1119").equals(entry.path("pickupId").asString())) store = entry;
        }
        if (store == null || !store.path("isActive").asBoolean()
                || !"Volta Redonda".equals(store.path("address").path("city").asString())
                || !"RJ".equals(store.path("address").path("state").asString())
                || !"874".equals(store.path("address").path("number").asString())) {
            throw new IllegalStateException("Unidade Hortifruti Aterrado não confirmada");
        }
        JsonNode region = query(http, "GetSellersByPostalCodeQuery", "285e40ec689755393866a7c3f72e64319f84a06e",
                Map.of("postalCode", "27213270", "country", "BRA", "salesChannel", "1")).path("sellers");
        boolean supported = false;
        for (JsonNode seller : region.path("sellers")) {
            if (SELLER.equals(seller.path("id").asString())) supported = true;
        }
        if (!supported || region.path("id").asString("").isBlank()) {
            throw new IllegalStateException("Região pública não identifica o Hortifruti Aterrado");
        }
        String regionId = region.path("id").asString();
        List<Map<String, String>> facets = List.of(Map.of("key", "region-id", "value", regionId),
                Map.of("key", "channel", "value", mapper.writeValueAsString(
                        Map.of("salesChannel", "1", "seller", SELLER, "regionId", regionId))),
                Map.of("key", "locale", "value", "pt-BR"));
        Map<String, CollectedProduct> products = new LinkedHashMap<>();
        List<String> warnings = new ArrayList<>();
        int found = 0;
        CatalogCheckpoint checkpoint = new CatalogCheckpoint(metadata().code(), clock, mapper);
        boolean complete = true;
        // Public category pages provide the actual facets; the API refuses offsets above 2500.
        for (String path : List.of("/hortifruti-variedades", "/nossos-organicos", "/acougue-e-peixaria",
                "/nossos-prontinhos", "/nossa-padaria", "/bebidas", "/nossa-mercearia", "/matinais",
                "/frios-queijos-e-laticinios", "/emporio", "/congelados", "/nao-alimentar", "/suplementos-e-vitaminas")) {
            try {
                var document = Jsoup.parse(http.get(website.resolve(path)));
                var script = document.selectFirst("script#__NEXT_DATA__");
                if (script == null) throw new IllegalStateException("Categoria Hortifruti sem metadados públicos");
                JsonNode category = mapper.readTree(script.data()).path("props").path("pageProps").path("data")
                        .path("collection").path("meta").path("selectedFacets");
                if (!category.isArray() || category.isEmpty()) throw new IllegalStateException("Categoria Hortifruti não identificada: " + path);
                List<Map<String, String>> categoryFacets = new ArrayList<>(facets);
                for (JsonNode facet : category) {
                    categoryFacets.add(Map.of("key", facet.path("key").asString(), "value", facet.path("value").asString()));
                }
                var collected = checkpoint.category(path, CatalogCheckpoint.Category.class, () -> {
                    Map<String, CollectedProduct> categoryProducts = new LinkedHashMap<>();
                    List<String> categoryWarnings = new ArrayList<>();
                    int count = collectCategory(http, categoryFacets, categoryProducts, categoryWarnings);
                    return new CatalogCheckpoint.Category(List.copyOf(categoryProducts.values()), count, categoryWarnings);
                });
                collected.products().forEach(product -> products.putIfAbsent(product.sourceReference(), product));
                warnings.addAll(collected.warnings());
                int received = collected.found();
                found += received;
                LOGGER.info("Hortifruti Aterrado: categoria {} concluída, {} registros recebidos", path, received);
            } catch (RuntimeException exception) {
                complete = false;
                warnings.add("Categoria Hortifruti " + path + " incompleta: " + exception.getMessage());
            }
        }
        if (products.isEmpty()) throw new IllegalStateException("Hortifruti não forneceu preços utilizáveis");
        if (complete) checkpoint.complete();
        return new CollectedCatalog(new CollectedStore("Hortifruti Aterrado",
                "Avenida Paulo de Frontin, 874 - Aterrado - Volta Redonda/RJ", null, null, true),
                List.copyOf(products.values()), found, checkpoint.startedAt(), warnings);
    }

    private int collectCategory(PublicCatalogHttp http, List<Map<String, String>> facets,
            Map<String, CollectedProduct> products, List<String> warnings) {
        HortifrutiProductParser parser = new HortifrutiProductParser();
        int total = -1;
        for (int offset = 0; total < 0 || offset < total; offset += 20) {
            JsonNode result = query(http, "ClientManyProductsQuery", "f33281cdf32c8270dbfb69330029e00be9a0b3f9",
                    Map.of("first", 20, "after", String.valueOf(offset), "sort", "score_desc", "term", "", "selectedFacets", facets))
                    .path("search").path("products");
            int count = result.path("pageInfo").path("totalCount").asInt(-1);
            if (count < 0 || count > 2500 || (total >= 0 && total != count)
                    || result.path("edges").size() != Math.min(20, count - offset)) {
                throw new IllegalStateException("Paginação do Hortifruti mudou durante a coleta");
            }
            total = count;
            for (JsonNode edge : result.path("edges")) {
                JsonNode product = edge.path("node");
                try {
                    CollectedProduct parsed = parser.parse(product);
                    products.putIfAbsent(parsed.sourceReference(), parsed);
                } catch (IllegalArgumentException exception) {
                    warnings.add("Produto Hortifruti " + product.path("sku").asString() + ": " + exception.getMessage());
                }
            }
        }
        return total;
    }

    private JsonNode query(PublicCatalogHttp http, String operation, String hash, Object variables) {
        String path = "/api/graphql?operationName=" + operation + "&operationHash=" + hash + "&variables="
                + URLEncoder.encode(mapper.writeValueAsString(variables), StandardCharsets.UTF_8);
        JsonNode response = mapper.readTree(http.get(website.resolve(path)));
        if (!response.path("data").isObject() || !response.path("errors").isMissingNode()) {
            throw new IllegalStateException("Consulta pública Hortifruti recusada: " + operation);
        }
        return response.path("data");
    }
}
