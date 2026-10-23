package br.com.supermercados.prices.catalog;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Accents that ERP descriptions drop ("Oleo", "Pao", "Cafe 3 Coracoes"). Only words with a single reading are
 * listed: "Para" (for, or the state), "Maca" (apple, or a stretcher) and "Coco" stay as the store wrote them.
 */
final class CatalogSpelling {

    private static final String ACCENTED = """
            Óleo Óleos Pão Pães Café Cafés Limão Limões Açúcar Feijão Macarrão Requeijão Sabão Água Águas Filé Francês
            Pêssego Maracujá Chá Chás Mamão Melão Purê Algodão Higiênico Higiênica Louça Louças Líquido Líquida Pó Açaí
            Lácteo Láctea Lácteos Lácteas Tônica Tônico Guaraná Cachaça Uísque Néctar Suíno Suína Linguiça Linguiças
            Músculo Acém Hambúrguer Hambúrgueres Fígado Coração Corações Tilápia Camarão Salmão Pimentão Brócolis Abóbora
            Rúcula Agrião Jiló Hortelã Manjericão Orégano Açafrão Cúrcuma Páprica Fubá Grão Grãos Paçoca Pinhão Orgânico
            Orgânica Orgânicos Orgânicas Proteína Proteínas Vitamínico Vitamínica Clássico Clássica Clássicos Tradição
            Nutrição Proteção Hidratação Reconstrução Reparação Cápsula Cápsulas Solúvel Moído Moída Instantâneo
            Instantânea Ração Rações Cães Cão Alumínio Plástico Plásticos Fósforo Fósforos Carvão Lâmpada Lâmpadas
            Lâmina Lâminas Lenço Lenços Loção Antisséptico Sanitária Sanitário Saponáceo Móveis Álcool Gás Inglês Japonês
            Sírio Econômico Econômica Prático Prática Fácil Rápido Rápida Médio Média Médios Médias Pérola Glúten
            Sódio Cálcio Linhaça Amêndoa Amêndoas Avelã Tâmara Cupuaçu Parmesão Química Químico Biológico Diário Diária
            Descartável Descartáveis Elétrico Elétrica Bebê Bebês Isotônico Energético Energéticos Adoçante Fécula
            Pescoço Contrafilé Coxão Sachê Sachês Leão Pilão Três São João Nestlé Ypê Perdigão Itambé Piraquê Qualitá
            Elegê Sazón Ômega Edição Coleção Ação Promoção Família Pré Pós Óculos Português Espécie Sandália Máscara
            Equilíbrio Pedaço Pedaços Raças Pérsia Hialurônico Hialurônica Lírio Lírios Herbíssimo
            """;
    private static final Map<String, String> SPELLINGS = new HashMap<>();
    private static final Pattern WORD = Pattern.compile("\\p{L}+");

    static {
        for (String word : ACCENTED.split("\\s+")) {
            if (!word.isBlank()) SPELLINGS.put(CatalogKey.words(word), word);
        }
    }

    private CatalogSpelling() {
    }

    /** "Oleo Liza Soja" → "Óleo Liza Soja", keeping each word's case; words already accented are left alone. */
    static String accented(String name) {
        Matcher matcher = WORD.matcher(name);
        StringBuilder result = new StringBuilder();
        while (matcher.find()) {
            String word = matcher.group();
            String spelled = word.chars().allMatch(character -> character < 128)
                    ? SPELLINGS.get(word.toUpperCase(Locale.ROOT)) : null;
            matcher.appendReplacement(result, Matcher.quoteReplacement(spelled == null ? word : sameCase(word, spelled)));
        }
        matcher.appendTail(result);
        return result.toString();
    }

    private static String sameCase(String original, String spelled) {
        if (original.length() > 1 && original.equals(original.toUpperCase(Locale.ROOT))) {
            return spelled.toUpperCase(Locale.ROOT);
        }
        if (Character.isUpperCase(original.charAt(0))) return spelled;
        return spelled.toLowerCase(Locale.ROOT);
    }
}
