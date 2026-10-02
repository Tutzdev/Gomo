package br.com.supermercados.prices.collection.mercafacil;

import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import br.com.supermercados.prices.collection.CollectedCatalog;
import br.com.supermercados.prices.collection.CatalogCheckpoint;
import br.com.supermercados.prices.collection.CollectedProduct;
import br.com.supermercados.prices.collection.CollectedStore;
import br.com.supermercados.prices.collection.CollectorMetadata;
import br.com.supermercados.prices.collection.PublicCatalogHttp;
import br.com.supermercados.prices.collection.SupermarketCollector;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

public final class MercafacilCollector implements SupermarketCollector {

    private final MercafacilStoreDefinition store;
    private final ObjectMapper mapper;
    private final Clock clock;
    private final Duration delay;

    public MercafacilCollector(MercafacilStoreDefinition store, ObjectMapper mapper, Clock clock, Duration delay) {
        this.store = store;
        this.mapper = mapper;
        this.clock = clock;
        this.delay = delay;
    }

    @Override
    public CollectorMetadata metadata() {
        return store.metadata();
    }

    @Override
    public CollectedCatalog collect() {
        PublicCatalogHttp http = new PublicCatalogHttp(delay);
        MercafacilPage home = fetch(http, "/" + store.slug() + "/categorias");
        List<JsonNode> departments = home.objects("StoreDepartment");
        if (departments.isEmpty()) throw new IllegalStateException("Loja sem categorias públicas");
        Map<String, CollectedProduct> products = new LinkedHashMap<>();
        List<String> warnings = new ArrayList<>();
        CatalogCheckpoint checkpoint = new CatalogCheckpoint(store.code(), clock, mapper);
        boolean complete = true;
        for (JsonNode department : departments) {
            if ("99999".equals(department.path("id").asString())) continue;
            try {
                var category = checkpoint.category(department.path("id").asString(), CatalogCheckpoint.Category.class, () -> {
                    Map<String, CollectedProduct> categoryProducts = new LinkedHashMap<>();
                    List<String> categoryWarnings = new ArrayList<>();
                    collectDepartment(http, department, categoryProducts, categoryWarnings);
                    return new CatalogCheckpoint.Category(List.copyOf(categoryProducts.values()), categoryProducts.size(), categoryWarnings);
                });
                category.products().forEach(product -> products.putIfAbsent(product.sourceReference(), product));
                warnings.addAll(category.warnings());
            } catch (RuntimeException exception) {
                complete = false;
                warnings.add("Categoria " + department.path("id").asString() + " incompleta: " + exception.getMessage());
            }
        }
        if (products.isEmpty()) throw new IllegalStateException("Catálogo sem produtos válidos");
        CollectedStore collectedStore = new CollectedStore(store.name(), store.address(), null, null, true);
        if (complete) checkpoint.complete();
        return new CollectedCatalog(collectedStore, List.copyOf(products.values()),
                products.size() + warnings.size(), checkpoint.startedAt(), warnings);
    }

    private void collectDepartment(PublicCatalogHttp http, JsonNode department,
            Map<String, CollectedProduct> products, List<String> warnings) {
        String path = "/" + store.slug() + "/" + department.path("slug").asString()
                + "-" + department.path("id").asString();
        MercafacilProductParser parser = new MercafacilProductParser();
        int pages = 1;
        int expected = -1;
        int received = 0;
        for (int number = 1; number <= pages; number++) {
            MercafacilPage page = fetch(http, path + "?page=" + number);
            JsonNode pagination = page.pagination();
            int count = pagination.path("records").asInt(-1);
            int totalPages = pagination.path("totalPages").asInt(-1);
            if (count < 0 || totalPages < 0 || totalPages > 500 || pagination.path("page").asInt() != number
                    || (expected >= 0 && (count != expected || totalPages != pages))) {
                throw new IllegalStateException("Paginação pública inconsistente: " + path);
            }
            expected = count;
            pages = totalPages;
            List<JsonNode> entries = page.objects("EcommerceProduct");
            received += entries.size();
            for (JsonNode entry : entries) {
                try {
                    CollectedProduct product = parser.parse(entry, page, store);
                    products.putIfAbsent(product.sourceReference(), product);
                } catch (IllegalArgumentException exception) {
                    warnings.add("Produto " + entry.path("id").asString() + ": " + exception.getMessage());
                }
            }
        }
        if (received != expected) throw new IllegalStateException("Categoria incompleta: " + path);
    }

    private MercafacilPage fetch(PublicCatalogHttp http, String path) {
        MercafacilPage page = new MercafacilPage(http.get(store.website().resolve(path)), mapper);
        boolean verified = page.objects("Store").stream().anyMatch(identity ->
                store.storeId().equals(identity.path("id").asString())
                        && store.cnpj().equals(identity.path("cnpj").asString())
                        && store.slug().equals(identity.path("slug").asString())
                        && identity.path("active").asBoolean());
        if (!verified) throw new IllegalStateException("A identidade da unidade mudou: " + store.code());
        return page;
    }
}
