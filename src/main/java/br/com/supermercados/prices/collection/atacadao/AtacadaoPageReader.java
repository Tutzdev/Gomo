package br.com.supermercados.prices.collection.atacadao;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import br.com.supermercados.prices.collection.PublicCatalogHttp;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

final class AtacadaoPageReader {

    private final PublicCatalogHttp http;
    private final ObjectMapper mapper;
    private final URI website;

    AtacadaoPageReader(PublicCatalogHttp http, ObjectMapper mapper, URI website) {
        this.http = http;
        this.mapper = mapper;
        this.website = website;
    }

    Page read(List<Map<String, String>> facets, int offset, int size) {
        String variables = mapper.writeValueAsString(Map.of("first", size, "after", String.valueOf(offset),
                "sort", "score_desc", "term", "", "selectedFacets", facets));
        JsonNode response = mapper.readTree(http.get(website.resolve("/api/graphql?operationName=ProductsQuery&variables="
                + URLEncoder.encode(variables, StandardCharsets.UTF_8))));
        if (response.has("errors")) {
            String error = response.path("errors").toString();
            if (!error.contains("Cannot read properties of undefined (reading 'price')")) {
                throw new IllegalStateException("Consulta pública recusada: " + error);
            }
            // A missing source price can break an entire page. Smaller public pages isolate that entry.
            int half = size / 2;
            if (half == 0 || (offset + size - half) / half >= 50) {
                return new Page(-1, List.of(), List.of("Fonte não retornou preços no intervalo " + offset
                        + "–" + (offset + size - 1) + "; mantida lacuna, sem presumir estoque."));
            }
            Page first = read(facets, offset, half);
            Page second = read(facets, offset + half, size - half);
            if (first.total() >= 0 && second.total() >= 0 && first.total() != second.total()) {
                throw new IllegalStateException("Total da categoria mudou durante a coleta");
            }
            List<JsonNode> edges = new ArrayList<>(first.edges());
            edges.addAll(second.edges());
            List<String> warnings = new ArrayList<>(first.warnings());
            warnings.addAll(second.warnings());
            return new Page(Math.max(first.total(), second.total()), edges, warnings);
        }
        JsonNode result = response.path("data").path("search").path("products");
        int total = result.path("pageInfo").path("totalCount").asInt(-1);
        if (total < 0 || !result.path("edges").isArray()
                || result.path("edges").size() != Math.max(0, Math.min(size, total - offset))) {
            throw new IllegalStateException("Página pública incompleta");
        }
        List<JsonNode> edges = new ArrayList<>();
        result.path("edges").forEach(edges::add);
        return new Page(total, edges, List.of());
    }

    record Page(int total, List<JsonNode> edges, List<String> warnings) { }
}
