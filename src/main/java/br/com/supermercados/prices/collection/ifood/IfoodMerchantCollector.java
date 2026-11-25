package br.com.supermercados.prices.collection.ifood;

import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import br.com.supermercados.prices.collection.CatalogCheckpoint;
import br.com.supermercados.prices.collection.CollectedCatalog;
import br.com.supermercados.prices.collection.CollectedProduct;
import br.com.supermercados.prices.collection.CollectorMetadata;
import br.com.supermercados.prices.collection.PublicCatalogHttp;
import br.com.supermercados.prices.collection.SupermarketCollector;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

public final class IfoodMerchantCollector implements SupermarketCollector {

    static final int PAGE_SIZE = 50;
    private static final int MAX_PAGES = 200;
    private static final Set<String> SKIPPED_CATEGORIES = Set.of("Combos");
    private static final Logger LOGGER = LoggerFactory.getLogger(IfoodMerchantCollector.class);

    private final IfoodMerchant merchant;
    private final URI website;
    private final ObjectMapper mapper;
    private final Clock clock;
    private final Duration delay;
    private final IfoodProductParser parser = new IfoodProductParser();

    public IfoodMerchantCollector(
        IfoodMerchant merchant,
        URI website,
        ObjectMapper mapper,
        Clock clock,
        Duration delay) {

        this.merchant = merchant;
        this.website = website;
        this.mapper = mapper;
        this.clock = clock;
        this.delay = delay;
    }

    @Override
    public CollectorMetadata metadata() {
        return merchant.metadata();
    }

    @Override
    public CollectedCatalog collect() {
        PublicCatalogHttp http = new PublicCatalogHttp(delay);
        List<Category> categories = categories(http);
        CatalogCheckpoint checkpoint = new CatalogCheckpoint(merchant.code(), clock, mapper);
        Map<String, CollectedProduct> products = new LinkedHashMap<>();
        List<String> warnings = new ArrayList<>();
        int found = 0;
        boolean complete = true;

        for (Category category : categories) {
            try {
                var collected = checkpoint.category(
                    category.code(),
                    CatalogCheckpoint.Category.class,
                    () -> collectCategory(http, category));

                collected.products().forEach(product -> products.putIfAbsent(product.sourceReference(), product));
                warnings.addAll(collected.warnings());
                found += collected.found();

                LOGGER.info(
                    "{}: categoria {} concluída, {} registros recebidos",
                    merchant.name(),
                    category.name(),
                    collected.found());
            } catch (RuntimeException exception) {
                complete = false;
                warnings.add("Categoria iFood " + category.name() + " incompleta: " + exception.getMessage());
            }
        }

        if (products.isEmpty()) {
            throw new IllegalStateException("iFood não forneceu preços utilizáveis para " + merchant.name()
                    + (warnings.isEmpty() ? "" : ": " + warnings.getFirst()));
        }

        if (complete) {checkpoint.complete();}

        return new CollectedCatalog(
            merchant.store(),
            List.copyOf(products.values()),
            found,
            checkpoint.startedAt(),
            warnings);
    }

    private List<Category> categories(PublicCatalogHttp http) {
        String path = catalogPath() + "?latitude=" + merchant.deliveryLatitude().toPlainString()
                + "&longitude=" + merchant.deliveryLongitude().toPlainString() + "&category_items_size=1";

        JsonNode menu = read(http, path).path("menu");

        if (!menu.isArray() || menu.isEmpty()) {
            throw new IllegalStateException("Cardápio iFood sem corredores para " + merchant.name());
        }

        List<Category> categories = new ArrayList<>();

        for (JsonNode entry : menu) {
            String code = entry.path("code").asString("");
            String name = entry.path("name").asString("").strip();

            if (code.isBlank() || name.isBlank() || SKIPPED_CATEGORIES.contains(name)) {continue;}

            categories.add(new Category(code, name));
        }

        return categories;
    }

    private CatalogCheckpoint.Category collectCategory(PublicCatalogHttp http, Category category) {
        List<CollectedProduct> products = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        int total = -1;
        int pages = 1;

        for (int page = 1; page <= pages; page++) {
            JsonNode data = read(http, catalogPath() + "/" + category.code()
                    + "?items_page=" + page + "&items_size=" + PAGE_SIZE);

            JsonNode pagination = data.path("metadata").path("pagination");
            int items = pagination.path("items").asInt(-1);
            int announced = pagination.path("pages").asInt(-1);

            if (items < 0 || announced < 0 || announced > MAX_PAGES || (total >= 0 && total != items)) {
                throw new IllegalStateException("Paginação do iFood mudou durante a coleta");
            }

            total = items;
            pages = announced;

            for (JsonNode item : data.path("categoryMenu").path("itens")) {
                try {
                    products.add(parser.parse(item, merchant, category.name()));
                } catch (IllegalArgumentException exception) {
                    warnings.add("Produto iFood " + item.path("id").asString("") + ": " + exception.getMessage());
                }
            }
        }

        return new CatalogCheckpoint.Category(
            products,
            Math.max(total, 0),
            warnings);
    }

    private JsonNode read(PublicCatalogHttp http, String path) {
        JsonNode response = mapper.readTree(http.get(website.resolve(path)));

        if (!response.path("data").isObject()) {
            throw new IllegalStateException("Resposta do iFood sem dados para " + merchant.name());
        }

        return response.path("data");
    }

    private String catalogPath() {
        return "/site-api/v1/merchants/multicategory/" + merchant.merchantId() + "/catalog";
    }

    private record Category(String code, String name) {}
}
