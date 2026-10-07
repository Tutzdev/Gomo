package br.com.supermercados.prices.collection.vip;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import br.com.supermercados.prices.collection.CollectedCatalog;
import br.com.supermercados.prices.collection.CatalogCheckpoint;
import br.com.supermercados.prices.collection.CollectedStore;
import br.com.supermercados.prices.collection.CollectorMetadata;
import br.com.supermercados.prices.collection.PublicCatalogHttp;
import br.com.supermercados.prices.collection.SupermarketCollector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

public final class VipStoreCollector implements SupermarketCollector {

    private static final Logger LOGGER = LoggerFactory.getLogger(VipStoreCollector.class);

    private final VipStoreDefinition definition;
    private final URI api;
    private final ObjectMapper mapper;
    private final Clock clock;
    private final Duration delay;
    private final VipProductParser parser;

    public VipStoreCollector(VipStoreDefinition definition, URI api, ObjectMapper mapper,
            Clock clock, Duration delay, VipProductParser parser) {
        this.definition = definition;
        this.api = api;
        this.mapper = mapper;
        this.clock = clock;
        this.delay = delay;
        this.parser = parser;
    }

    @Override
    public CollectorMetadata metadata() {
        return definition.metadata();
    }

    @Override
    public CollectedCatalog collect() {
        PublicCatalogHttp http = new PublicCatalogHttp(delay);
        String domain = definition.website().getHost().replaceFirst("^www\\.", "");
        String organization = "/api-admin/v1/org/" + definition.organizationId();
        Map<String, String> headers = new HashMap<>();
        headers.put("organizationid", String.valueOf(definition.organizationId()));
        headers.put("domainkey", domain);
        JsonNode identity = get(http, headers, "/api-admin/v1/organizacoes/filiais/dominio/" + domain).path("data");
        require(identity, "id", String.valueOf(definition.storefrontId()));
        require(identity.path("organizacao"), "id", String.valueOf(definition.organizationId()));
        require(identity.path("organizacao"), "enderecoServidor", domain);
        if (!identity.path("ativo").asBoolean() || identity.path("em_manutencao").asBoolean()) {
            throw new IllegalStateException("Loja pública inativa ou em manutenção");
        }

        String configuration = VipStorefrontConfiguration.load(definition.website(), definition.configurationPath(), http::get);
        // These values are published by the storefront for anonymous browsing, never customer credentials.
        String login = mapper.writeValueAsString(mapper.createObjectNode().put("domain", domain)
                .put("username", configurationValue(configuration, "lojaUser"))
                .put("key", configurationValue(configuration, "lojaAuthJWT")));
        JsonNode session = mapper.readTree(http.request(api.resolve(organization + "/auth/loja/login"), headers, login));
        String token = session.path("data").asString("");
        if (!session.path("success").asBoolean() || token.isBlank()) {
            throw new IllegalStateException("Fonte não iniciou sessão de navegação pública");
        }
        headers.put("Authorization", "Bearer " + token);
        JsonNode store = get(http, headers, organization + "/loja/centros_distribuicoes/" + definition.distributionId()).path("data");
        validateStore(store);
        String catalogPath = organization + "/filial/" + definition.branchId()
                + "/centro_distribuicao/" + definition.distributionId() + "/loja";
        JsonNode departments = get(http, headers, catalogPath + "/classificacoes_mercadologicas/departamentos/arvore").path("data");
        if (!departments.isArray() || departments.isEmpty()) throw new IllegalStateException("Fonte sem departamentos");
        List<JsonNode> entries = new ArrayList<>();
        List<String> failures = new ArrayList<>();
        CatalogCheckpoint checkpoint = new CatalogCheckpoint(definition.code(), clock, mapper);
        for (JsonNode department : departments) {
            int id = department.path("classificacao_mercadologica_id").asInt(-1);
            if (id < 1) throw new IllegalStateException("Departamento sem identidade");
            String path = catalogPath + "/classificacoes_mercadologicas/departamentos/" + id + "/produtos";
            try {
                entries.addAll(checkpoint.category(path, CatalogCheckpoint.Entries.class,
                        () -> new CatalogCheckpoint.Entries(paginate(http, headers, path))).products());
            } catch (RuntimeException exception) {
                failures.add("Categoria " + id + " incompleta: " + exception.getMessage());
            }
        }
        var parsed = parser.parse(entries, "vip:" + definition.organizationId(), definition.chain(), definition.website());
        if (parsed.products().isEmpty()) throw new IllegalStateException("Fonte não retornou produtos válidos");
        JsonNode address = store.path("endereco");
        CollectedStore collectedStore = new CollectedStore(definition.name(),
                address.path("logradouro").asString() + ", " + address.path("numero").asString()
                        + " - " + address.path("bairro").asString() + " - Volta Redonda/RJ",
                new BigDecimal(store.path("coordenada_geografica").path("latitude").asString()).setScale(7, RoundingMode.HALF_UP),
                new BigDecimal(store.path("coordenada_geografica").path("longitude").asString()).setScale(7, RoundingMode.HALF_UP), true);
        if (failures.isEmpty()) checkpoint.complete();
        failures.addAll(parsed.warnings());
        return new CollectedCatalog(collectedStore, parsed.products(), entries.size(), checkpoint.startedAt(), List.copyOf(failures));
    }

    private List<JsonNode> paginate(PublicCatalogHttp http, Map<String, String> headers, String path) {
        List<JsonNode> products = new ArrayList<>();
        int totalPages = 1;
        int totalItems = -1;
        for (int page = 1; page <= totalPages; page++) {
            JsonNode response = get(http, headers, path + "?page=" + page);
            JsonNode pagination = response.path("paginator");
            int count = pagination.path("total_items").asInt(-1);
            int pages = pagination.path("total_pages").asInt(-1);
            if (count < 0 || pages < 0 || pages > 500 || pagination.path("page").asInt(-1) != page
                    || (totalItems >= 0 && (count != totalItems || pages != totalPages))) {
                throw new IllegalStateException("Paginação inconsistente; a coleta será repetida sem sobrescrever o histórico");
            }
            totalPages = pages;
            totalItems = count;
            if (page == 1 || page % 20 == 0) {
                LOGGER.info("Coletor {}: {} página {}/{}, {} produtos na categoria", definition.code(), path, page, pages, count);
            }
            JsonNode entries = response.path("data").isArray() ? response.path("data") : response.path("data").path("produtos");
            if (!entries.isArray()) throw new IllegalStateException("Página de produtos inválida");
            entries.forEach(products::add);
        }
        if (products.size() != totalItems) throw new IllegalStateException("Catálogo incompleto");
        return products;
    }

    private JsonNode get(PublicCatalogHttp http, Map<String, String> headers, String path) {
        JsonNode response = mapper.readTree(http.request(api.resolve(path), headers, null));
        if (!response.path("success").asBoolean()) throw new IllegalStateException("Fonte não confirmou a consulta pública");
        return response;
    }

    void validateStore(JsonNode store) {
        require(store, "id", String.valueOf(definition.distributionId()));
        require(store, "cnpj", definition.cnpj());
        require(store.path("endereco"), "cidade", "Volta Redonda");
        require(store.path("endereco"), "estado", "Rio de Janeiro");
        if (!store.path("ativo").asBoolean() || store.path("exclusivo_televendas").asBoolean()) {
            throw new IllegalStateException("Unidade indisponível para consulta pública");
        }
    }

    private void require(JsonNode node, String field, String expected) {
        if (!expected.equals(node.path(field).asString())) throw new IllegalStateException("Identidade da fonte divergente: " + field);
    }

    private String configurationValue(String configuration, String field) {
        var matcher = Pattern.compile(Pattern.quote(field) + ":\"([^\"]+)\"").matcher(configuration);
        if (!matcher.find()) throw new IllegalStateException("Configuração pública mudou: " + field);
        return matcher.group(1);
    }
}
