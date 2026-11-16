package br.com.supermercados.prices.collection.mercafacil;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import org.jsoup.Jsoup;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

final class MercafacilPage {

    private static final Pattern FLIGHT = Pattern.compile("self\\.__next_f\\.push\\((\\[1,.*?\\])\\)", Pattern.DOTALL);
    private final Map<String, JsonNode> records = new LinkedHashMap<>();

    MercafacilPage(String html, ObjectMapper mapper) {
        StringBuilder content = new StringBuilder();

        for (var script : Jsoup.parse(html).select("script")) {
            var matches = FLIGHT.matcher(script.data());

            while (matches.find()) {
                content.append(mapper.readTree(matches.group(1)).path(1).asString());
            }
        }

        for (String line : content.toString().split("\n")) {
            int separator = line.indexOf(':');

            if (separator < 1) {continue;}

            String json = line.substring(separator + 1);

            if (json.startsWith("{") || json.startsWith("[")) {
                records.put(line.substring(0, separator), mapper.readTree(json));
            }
        }

        if (records.isEmpty()) {throw new IllegalStateException("A página pública não contém um catálogo reconhecido");}
    }

    List<JsonNode> objects(String typename) {
        Map<String, JsonNode> matches = new LinkedHashMap<>();
        records.values().forEach(record -> visit(record, typename, matches));

        return List.copyOf(matches.values());
    }

    JsonNode pagination() {
        List<JsonNode> matches = new ArrayList<>();
        records.values().forEach(record -> findPagination(record, matches));

        return matches.stream().findFirst()
                .orElseThrow(() -> new IllegalStateException("Catálogo sem paginação verificável"));
    }

    JsonNode resolve(JsonNode node) {
        if (node.isString() && node.asString().matches("\\$[0-9a-f]+")) {
            return records.getOrDefault(node.asString().substring(1), node);
        }

        return node;
    }

    private void visit(JsonNode node, String typename, Map<String, JsonNode> matches) {
        if (typename.equals(node.path("__typename").asString())) {
            matches.putIfAbsent(node.path("id").asString(), node);
        }

        if (node.isArray() || node.isObject()) {node.forEach(child -> visit(child, typename, matches));}
    }

    private void findPagination(JsonNode node, List<JsonNode> matches) {
        if (node.path("pagination").isObject()) {matches.add(node.path("pagination"));}

        if (node.isArray() || node.isObject()) {node.forEach(child -> findPagination(child, matches));}
    }
}
