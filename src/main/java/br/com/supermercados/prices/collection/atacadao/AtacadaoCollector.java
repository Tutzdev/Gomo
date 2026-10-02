package br.com.supermercados.prices.collection.atacadao;

import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.jsoup.Jsoup;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import br.com.supermercados.prices.collection.CatalogCheckpoint;
import br.com.supermercados.prices.collection.CollectedCatalog;
import br.com.supermercados.prices.collection.CollectedProduct;
import br.com.supermercados.prices.collection.CollectedStore;
import br.com.supermercados.prices.collection.CollectorMetadata;
import br.com.supermercados.prices.collection.PublicCatalogHttp;
import br.com.supermercados.prices.collection.SupermarketCollector;
import lombok.RequiredArgsConstructor;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.collection.regional.enabled", havingValue = "true", matchIfMissing = true)
public class AtacadaoCollector implements SupermarketCollector {

    private static final Logger LOGGER = LoggerFactory.getLogger(AtacadaoCollector.class);
    private static final URI WEBSITE = URI.create("https://www.atacadao.com.br");
    private static final String SELLER = "atacadaobr815";
    private final ObjectMapper mapper;
    private final Clock clock;

    @Override
    public CollectorMetadata metadata() {
        return new CollectorMetadata("atacadao_sao_geraldo_online", "Atacadão", "Atacadão São Geraldo",
                "atacadao_online", "E-commerce público Atacadão — retirada São Geraldo", WEBSITE.toString(),
                Instant.parse("2026-09-20T22:33:00Z"), "Atacadão", "atacadao:chain", "atacadao:store:815",
                UUID.fromString("5c4cb935-52e1-4bf8-8d17-902dc0837c66"), "atacadao_reviewed_flyers");
    }

    @Override
    public CollectedCatalog collect() {
        PublicCatalogHttp http = new PublicCatalogHttp(Duration.ofSeconds(1));
        JsonNode stores = mapper.readTree(http.get(URI.create(
                "https://bff-frontend-api.cloud.carrefour.com.br/api/v1/store-info?accountName=" + SELLER + "&type=loja")));
        if (!stores.isArray() || stores.size() != 1) throw new IllegalStateException("Unidade Atacadão não confirmada");
        JsonNode store = stores.path(0);
        if (!SELLER.equals(store.path("accountName").asString())
                || !"Volta Redonda".equals(store.path("city").asString()) || !"RJ".equals(store.path("uf").asString())
                || !"27253-005".equals(store.path("postalCode").asString())
                || !"volta-redonda-sao-geraldo".equals(store.path("slug").asString())) {
            throw new IllegalStateException("Identidade da filial Atacadão divergente");
        }
        JsonNode regions = mapper.readTree(http.get(WEBSITE.resolve("/api/checkout/pub/regions?postalCode=27253005&sc=1&country=BRA")));
        String regionId = null;
        for (JsonNode region : regions) {
            for (JsonNode seller : region.path("sellers")) {
                if (SELLER.equals(seller.path("id").asString())) regionId = region.path("id").asString();
            }
        }
        if (regionId == null) throw new IllegalStateException("Região não confirmou vendedor São Geraldo");
        var document = Jsoup.parse(http.get(WEBSITE.resolve("/limpeza")));
        var script = document.selectFirst("script#__NEXT_DATA__");
        if (script == null) throw new IllegalStateException("Menu público Atacadão indisponível");
        JsonNode menu = mapper.readTree(script.data()).path("props").path("pageProps")
                .path("cmsMenuCategory").path("menu").path("menuCategories").path("menuItems");
        if (!menu.isArray() || menu.isEmpty()) throw new IllegalStateException("Departamentos Atacadão ausentes");
        var facets = List.of(Map.of("key", "region-id", "value", regionId),
                Map.of("key", "channel", "value", mapper.writeValueAsString(Map.of("salesChannel", "1", "seller", SELLER, "regionId", regionId))),
                Map.of("key", "locale", "value", "pt-BR"));
        CatalogCheckpoint checkpoint = new CatalogCheckpoint(metadata().code(), clock, mapper);
        Map<String, CollectedProduct> products = new LinkedHashMap<>();
        List<String> warnings = new ArrayList<>();
        boolean complete = true;
        int found = 0;
        for (JsonNode category : menu) {
            String path = category.path("href").asString().strip();
            try {
                var result = checkpoint.category("complete-pages-v3:" + path, CatalogCheckpoint.Category.class,
                        () -> collectCategory(http, facets, category, checkpoint));
                result.products().forEach(product -> products.putIfAbsent(product.sourceReference(), product));
                warnings.addAll(result.warnings());
                found += result.found();
                LOGGER.info("Atacadão São Geraldo: {} concluída, {} registros", path, result.found());
            } catch (RuntimeException exception) {
                complete = false;
                warnings.add("Categoria Atacadão " + path + " incompleta: " + exception.getMessage());
                LOGGER.warn("Atacadão {}: {}", path, exception.getMessage());
            }
        }
        if (products.isEmpty()) throw new IllegalStateException("Nenhuma oferta Atacadão utilizável");
        if (complete) checkpoint.complete();
        return new CollectedCatalog(new CollectedStore("Atacadão São Geraldo",
                "Rodovia dos Metalúrgicos, 1085 - São Geraldo - Volta Redonda/RJ", null, null, true),
                List.copyOf(products.values()), found, checkpoint.startedAt(), warnings);
    }

    private CatalogCheckpoint.Category collectCategory(PublicCatalogHttp http, List<Map<String, String>> region, JsonNode category,
            CatalogCheckpoint checkpoint) {
        String path = URI.create(category.path("href").asString().strip()).getPath();
        if (!path.matches("/[a-z0-9/-]+")) throw new IllegalStateException("Categoria inválida");
        List<Map<String, String>> facets = new ArrayList<>();
        String[] parts = path.substring(1).split("/");
        for (int index = 0; index < parts.length; index++) {
            facets.add(Map.of("key", "category-" + (index + 1), "value", parts[index]));
        }
        facets.addAll(region);
        List<CollectedProduct> products = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        AtacadaoProductParser parser = new AtacadaoProductParser();
        AtacadaoPageReader reader = new AtacadaoPageReader(http, mapper, WEBSITE);
        int total = -1;
        int received = 0;
        for (int offset = 0; offset < 1000 && (total < 0 || offset < total); offset += 20) {
            int pageOffset = offset;
            AtacadaoPageReader.Page result;
            try {
                result = checkpoint.category(path + ":recovered-page:" + offset, AtacadaoPageReader.Page.class,
                        () -> reader.read(facets, pageOffset, 20));
            } catch (RuntimeException exception) {
                warnings.add("Categoria " + path + " interrompida na posição " + offset + ": " + exception.getMessage());
                break;
            }
            if (offset == 0 && (result.total() > 1000 || !result.warnings().isEmpty())
                    && !category.path("subCategories").isEmpty()) {
                for (JsonNode child : category.path("subCategories")) {
                    try {
                        var subcategory = collectCategory(http, region, child, checkpoint);
                        products.addAll(subcategory.products());
                        warnings.addAll(subcategory.warnings());
                        received += subcategory.found();
                    } catch (RuntimeException exception) {
                        warnings.add("Subcategoria " + child.path("href").asString() + ": " + exception.getMessage());
                    }
                }
                return new CatalogCheckpoint.Category(products, received, warnings);
            }
            if (total >= 0 && result.total() >= 0 && total != result.total()) {
                warnings.add("Total de " + path + " mudou durante a coleta; ofertas já recebidas preservadas.");
                break;
            }
            if (result.total() >= 0) total = result.total();
            warnings.addAll(result.warnings().stream().map(warning -> path + ": " + warning).toList());
            received += result.edges().size();
            if (offset % 400 == 0) LOGGER.info("Atacadão {}: página em {}, total {}", path, offset, total);
            for (JsonNode edge : result.edges()) {
                try {
                    products.add(parser.parse(edge.path("node"), SELLER));
                } catch (RuntimeException exception) {
                    warnings.add("Produto Atacadão " + edge.path("node").path("sku").asString() + ": " + exception.getMessage());
                }
            }
        }
        if (total > 1000) warnings.add("Categoria " + path + " excede as 50 páginas públicas e não tem subdivisão disponível.");
        return new CatalogCheckpoint.Category(products, received, warnings);
    }
}
