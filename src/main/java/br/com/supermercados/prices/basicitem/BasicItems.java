package br.com.supermercados.prices.basicitem;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import br.com.supermercados.prices.catalog.CatalogKey;

public final class BasicItems {

    private static final Set<String> CONNECTORS = Set.of("DE", "DA", "DO", "E", "EM", "COM", "PARA");

    private static final String PADARIA = "Padaria";
    private static final String ACOUGUE = "Açougue";
    private static final String MERCEARIA = "Mercearia";
    private static final String FRIOS = "Frios e laticínios";
    private static final String HORTIFRUTI = "Hortifrúti";

    private static final String READY_MEALS = "PRONTO|PRONTA|COZID[OA]|VAPZA|ESCONDIDINHO|LASANHA|SOPA|CALDO|TEMPERO|SABOR"
            + "|KIT|BISCOITO|BOLACHA|SALGADINHO|SNACK|CHIPS|SORVETE|PAPINHA|RACAO|DOG|CAT|SHAMPOO|CONDICIONADOR"
            + "|SABONETE|CREME TRAT|MASCARA|BARRA|BALA|IOGURTE|BEBIDA|SUCO|GELEIA";

    private static final String PROCESSED_MEAT = "MOID[OA]|MOIDA|TEMPERAD[OA]|EMPANAD[OA]|HAMBURG|ALMONDEG|ESCONDIDINHO"
            + "|DEFUMAD[OA]|NUGGET|LINGUICA|SALSICHA|KIBE|QUIBE|STEAK|RACAO|SUIN[OA]|CORDEIRO|FRANGO|PERU|CHESTER"
            + "|PEIXE|SALMAO|VEGETAL|SOJA|LIMPADOR|LIMPA|DESENGORDURANTE|MR";

    private static final List<BasicItem> ALL = List.of(
        BasicItem.of(
            "pao-de-hamburguer",
            "Pão de hambúrguer",
            PADARIA,
            Measure.KG,
            "0.4",
            "\\bPAO\\b(?: [A-Z0-9]+){0,4} HAMB(?:U|UR)?(?:R|RG|G)",
            "\\bGLUTEN\\b|\\b(?:" + READY_MEALS + ")\\b"),
        BasicItem.of(
            "pao-de-forma",
            "Pão de forma tradicional",
            PADARIA,
            Measure.KG,
            "0.45",
            "\\bPAO\\b(?: [A-Z0-9]+){0,2} FORMA\\b",
            "\\b(?:INTEGRAL|GRAOS|GLUTEN|ZERO|ZEROS|LIGHT|AVEIA|CENTEIO|PROTEINA|PROTEINAS|CHIA|BRIOCHE|SEMENTES"
                    + "|LINHACA|" + READY_MEALS + ")\\b"),
        BasicItem.of(
            "pao-frances",
            "Pão francês",
            PADARIA,
            Measure.KG,
            "1",
            "\\bPAO\\b(?: [A-Z0-9]+){0,1} FRANCES\\b|\\bCARIOQUINHA\\b",
            "\\b(?:CONGELAD[OA]|PRE ASSADO|MINI|TAPIOCA|INTEGRAL|" + READY_MEALS + ")\\b"),
        BasicItem.of(
            "carne-moida",
            "Carne moída bovina",
            ACOUGUE,
            Measure.KG,
            "0.5",
            "\\bMOID[AO]\\b",
            "\\b(?:VEGETAL|SOJA|PIMENTA|FRANGO|SUIN[OA]|PERU|CEVADA|CAFE|UVA|CANELA|COMINHO|ERVA|NOZ|PAPRICA|ALHO"
                    + "|OREGANO|CRAVO|GENGIBRE|AMENDOIM|CASTANHA|AMENDOA|COCO|SAL|LINHACA|ACUCAR|CACAU|QUEIJO|LEGUMES"
                    + "|PURE|BATATA|ARROZ|PROTEINA|PASTEL|EMPADA|COXINHA|PIZZA|CANELONE|" + READY_MEALS + ")\\b"),
        BasicItem.of(
            "acem",
            "Acém",
            ACOUGUE,
            Measure.KG,
            "1",
            "\\bACEM\\b",
            "\\bPESCOCO\\b|\\b(?:" + PROCESSED_MEAT + ")\\b"),
        BasicItem.of(
            "patinho",
            "Patinho",
            ACOUGUE,
            Measure.KG,
            "1",
            "\\bPATINHO\\b",
            "\\b(?:BORRACHA|BRINQUEDO|PELUCIA|BANHO|FEIO|" + PROCESSED_MEAT + ")\\b"),
        BasicItem.of(
            "coxao-mole",
            "Coxão mole",
            ACOUGUE,
            Measure.KG,
            "1",
            "\\bCOXAO MOLE\\b",
            "\\b(?:" + PROCESSED_MEAT + ")\\b"),
        BasicItem.of(
            "alcatra",
            "Alcatra",
            ACOUGUE,
            Measure.KG,
            "1",
            "\\bALCATRA\\b",
            "\\b(?:PICANHA|MAMINHA|" + PROCESSED_MEAT + ")\\b"),
        BasicItem.of(
            "musculo",
            "Músculo bovino",
            ACOUGUE,
            Measure.KG,
            "1",
            "\\bMUSCULO\\b",
            "\\bPALETA\\b|\\b(?:" + PROCESSED_MEAT + ")\\b"),
        BasicItem.of(
            "peito-de-frango",
            "Peito de frango",
            ACOUGUE,
            Measure.KG,
            "1",
            "\\bPEITO\\b(?: [A-Z0-9]+){0,3} (?:FRANGO|FGO|FRG)\\b|\\bFILE DE PEITO\\b",
            "\\b(?:PERU|CHESTER|DEFUMAD[OA]|EMPANAD[OA]|TEMPERAD[OA]|NUGGETS?|DESFIAD[OA]|LEGUMES|RECHEAD[OA]"
                    + "|CONGELAD[OA] PRONTO|GRELHAD[OA]|ASSAD[OA]|FACIL|" + READY_MEALS + ")\\b"),
        BasicItem.of(
            "coxa-e-sobrecoxa",
            "Coxa e sobrecoxa de frango",
            ACOUGUE,
            Measure.KG,
            "1",
            "\\bSOBR?E?COXA\\b|\\bSOBECOXA\\b",
            "\\b(?:TEMPERAD[OA]|EMPANAD[OA]|ASSAD[OA]|DEFUMAD[OA]|PERU|" + READY_MEALS + ")\\b"),
        BasicItem.of(
            "arroz-branco",
            "Arroz branco tipo 1",
            MERCEARIA,
            Measure.KG,
            "5",
            "^(?:ARROZ|[0-9]ARROZ)\\b",
            "\\b(?:INTEGRAL|PARBO|PARBOILIZADO|PARBOLIZADO|ARBOREO|ARBORIO|JAPONES|GOHAN|SELVAGEM|NEGRO|VERMELHO|CATETO"
                    + "|BASMATI|JASMIM|BIRO|DOCE|FLOCOS|GRAOS|CARRETEIRO|RISOTO|ORIENTAL|SUSHI|TIPO 2|ERVAS|MILANES"
                    + "|GREGO|BROCOLIS|ALHO|CEBOLA|COMBO|FARINHA|CREME|" + READY_MEALS + ")\\b"),
        BasicItem.of(
            "feijao-carioca",
            "Feijão carioca",
            MERCEARIA,
            Measure.KG,
            "1",
            "\\bFEIJAO\\b(?: [A-Z0-9]+){0,4} CARIOCA\\b|\\bCARIOCA\\b(?: [A-Z0-9]+){0,3} FEIJAO\\b",
            "\\b(?:FEIJOADA|LATA|CONGELAD[OA]|TIPO 2|" + READY_MEALS + ")\\b"),
        BasicItem.of(
            "feijao-preto",
            "Feijão preto",
            MERCEARIA,
            Measure.KG,
            "1",
            "\\bFEIJAO\\b(?: [A-Z0-9]+){0,4} PRETO\\b",
            "\\b(?:FEIJOADA|LATA|CONGELAD[OA]|ORGANICO|TIPO 2|" + READY_MEALS + ")\\b"),
        BasicItem.of(
            "acucar-refinado",
            "Açúcar refinado",
            MERCEARIA,
            Measure.KG,
            "1",
            "^(?:ACUCAR|[0-9]ACUCAR)\\b(?: [A-Z0-9]+){0,3} (?:REFINADO|REF)\\b",
            "\\b(?:LIGHT|DIET|SACHE|SACHES|CONFEITEIRO|" + READY_MEALS + ")\\b"),
        BasicItem.of(
            "acucar-cristal",
            "Açúcar cristal",
            MERCEARIA,
            Measure.KG,
            "5",
            "^(?:ACUCAR|[0-9]ACUCAR)\\b(?: [A-Z0-9]+){0,3} CRISTAL\\b",
            "\\b(?:ORGANICO|SACHE|SACHES|" + READY_MEALS + ")\\b"),
        BasicItem.of(
            "oleo-de-soja",
            "Óleo de soja",
            MERCEARIA,
            Measure.L,
            "0.9",
            "^(?:OLEO|[0-9]OLEO)\\b(?: [A-Z0-9]+){0,2} SOJA\\b",
            "\\b(?:SPRAY|COMPOSTO|AZEITE|SARDINHA|ATUM|ORGANICO|ORGANIC|PIMENTA|" + READY_MEALS + ")\\b"),
        BasicItem.of(
            "cafe-em-po",
            "Café torrado e moído",
            MERCEARIA,
            Measure.KG,
            "0.5",
            "^(?:CAFE|[0-9]CAFE)\\b(?!.*\\b(?:SOLUVEL|CAPSULAS?|CAPPUCCINO|GRAOS?|BEBIDA|COLD|LEITE|SACHE|DESCAFEINADO"
                    + "|FILTRO|COADOR|INSTANTANEO|ORIGINAL NESCAFE|NESCAFE|DOLCE|TRES|CHOCOLATE|COLOMBIANO|ARABICA)\\b)",
            "\\b(?:GOURMET|ORGANICO|" + READY_MEALS + ")\\b"),
        BasicItem.of(
            "leite-integral",
            "Leite integral UHT",
            FRIOS,
            Measure.L,
            "1",
            "^(?:LEITE|[0-9]LEITE)\\b(?: [A-Z0-9]+){0,4} INTEGRAL\\b",
            "\\b(?:PO|EM PO|INSTANTANEO|CONDENSADO|FERMENTADO|COCO|AMENDOA|AVEIA|LACTOSE|SEM LACTOSE|BUFALA|CABRA"
                    + "|CAPRILAT|PASTEURIZADO|CREME|ACHOCOLATADO|CHOCOLATE|" + READY_MEALS + ")\\b"),
        BasicItem.of(
            "farinha-de-trigo",
            "Farinha de trigo",
            MERCEARIA,
            Measure.KG,
            "1",
            "\\bFARINHA\\b(?: [A-Z0-9]+){0,1} TRIGO\\b",
            "\\b(?:INTEGRAL|FERMENTO|PASTEL|PIZZA|SALGADOS|PANIFICACAO|ROSCA|BOLO|TEMPURA|EMPANAR|KIBE|QUIBE|MISTURA"
                    + "|" + READY_MEALS + ")\\b"),
        BasicItem.of(
            "macarrao-espaguete",
            "Macarrão espaguete",
            MERCEARIA,
            Measure.KG,
            "0.5",
            "\\b(?:ESPAGUETE|SPAGHETTI|SPAGUETTI|ESPAGUETI)\\b",
            "\\b(?:INTEGRAL|INTEGRALI|ARROZ|GLUTEN|CEREALI|PALMITO|CENOURA|ABOBRINHA|LEGUMES|INSTANTANEO|LAMEN"
                    + "|MOLHO|CONGELAD[OA]|GRAO DE BICO|LENTILHA|DOLCE GABBANA|" + READY_MEALS + ")\\b"),
        BasicItem.of(
            "sal-refinado",
            "Sal refinado",
            MERCEARIA,
            Measure.KG,
            "1",
            "^(?:SAL|[0-9]SAL)\\b(?: [A-Z0-9]+){0,3} REFINADO\\b",
            "\\b(?:LIGHT|SACHE|SACHES|HIMALAIA|ROSA|GROSSO|PARRILLA|" + READY_MEALS + ")\\b"),
        BasicItem.of(
            "ovos-brancos",
            "Ovos brancos",
            FRIOS,
            Measure.UN,
            "12",
            "^(?:OVOS?|[0-9]OVOS?)\\b",
            "\\b(?:PASCOA|PASC|CHOCOLATE|KINDER|LACTA|ARCOR|CODORNA|PASTEURIZADO|LIQUIDO|DESIDRATADO|PO|CAIPIRA"
                    + "|VERMELHOS?|ORGANICOS?|COLHER|TRUFADO|RECHEADO|" + READY_MEALS + ")\\b"),
        BasicItem.of(
            "manteiga",
            "Manteiga com sal",
            FRIOS,
            Measure.KG,
            "0.2",
            "^(?:MANTEIGA|[0-9]MANTEIGA)\\b",
            "\\b(?:SEM SAL|CACAU|KARITE|AMENDOIM|GHEE|GARRAFA|AROMA|VAGEM|FEIJAO|PIPOCA|" + READY_MEALS + ")\\b"),
        BasicItem.of(
            "margarina",
            "Margarina com sal",
            FRIOS,
            Measure.KG,
            "0.5",
            "^(?:MARGARINA|[0-9]MARGARINA)\\b",
            "\\b(?:SEM SAL|BALDE|LIGHT|" + READY_MEALS + ")\\b"),
        BasicItem.of(
            "tomate",
            "Tomate",
            HORTIFRUTI,
            Measure.KG,
            "1",
            "^(?:TOMATE|[0-9]TOMATE)\\b",
            "\\b(?:MOLHO|EXTRATO|PELADO|SECO|CEREJA|GRAPE|SWEET|SUCO|POLPA|PASSATA|KETCHUP|CATCHUP|PURE|ORGANICO"
                    + "|DESIDRATADO|CONSERVA|SARDINHA|" + READY_MEALS + ")\\b"),
        BasicItem.of(
            "batata",
            "Batata inglesa",
            HORTIFRUTI,
            Measure.KG,
            "1",
            "^(?:BATATA|[0-9]BATATA)\\b",
            "\\b(?:DOCE|BAROA|YACON|PALHA|CHIPS|FRITA|PRE FRITA|CONGELAD[OA]|PURE|RUFFLES|PRINGLES|LAYS|ONDULADA"
                    + "|PALITO|SMILE|NOISETTE|RUSTICA|CRINKLE|ROSTI|ASTERIX|BOLINHA|ORGANICA|" + READY_MEALS + ")\\b"),
        BasicItem.of(
            "cebola",
            "Cebola",
            HORTIFRUTI,
            Measure.KG,
            "1",
            "^(?:CEBOLA|[0-9]CEBOLA)\\b",
            "\\b(?:ROXA|CREME|FRITA|DESIDRATADA|FLOCOS|EM PO|PO|CONSERVA|CEBOLINHA|ORGANICA|BRANCA|" + READY_MEALS
                    + ")\\b"),
        BasicItem.of(
            "banana-prata",
            "Banana prata",
            HORTIFRUTI,
            Measure.KG,
            "1",
            "^(?:BANANA|[0-9]BANANA)\\b(?: [A-Z0-9]+){0,1} PRATA\\b",
            "\\b(?:CHIPS|PASSA|DESIDRATADA|ORGANICA|" + READY_MEALS + ")\\b"),
        BasicItem.of(
            "banana-nanica",
            "Banana nanica",
            HORTIFRUTI,
            Measure.KG,
            "1",
            "^(?:BANANA|[0-9]BANANA)\\b(?: [A-Z0-9]+){0,1} (?:NANICA|D AGUA|DAGUA|DA AGUA|CATURRA)\\b",
            "\\b(?:CHIPS|PASSA|DESIDRATADA|ORGANICA|" + READY_MEALS + ")\\b"));

    private BasicItems() {}

    public static List<BasicItem> all() {
        return ALL;
    }

    public static Optional<BasicItem> find(String slug) {
        return ALL.stream().filter(item -> item.slug().equals(slug)).findFirst();
    }

    public static Optional<BasicItem> match(String productName) {
        String words = CatalogKey.words(productName);

        return ALL.stream().filter(item -> item.matches(words)).findFirst();
    }

    public static List<BasicItem> search(String query) {
        String words = CatalogKey.words(query);

        if (words.isBlank()) {return ALL;}

        List<String> terms = Arrays.stream(words.split(" ")).filter(term -> !CONNECTORS.contains(term)).toList();

        if (terms.isEmpty()) {return List.of();}

        return ALL.stream()
                .filter(item -> terms.stream().allMatch(term -> searchable(item).contains(" " + term)))
                .toList();
    }

    private static String searchable(BasicItem item) {
        return " " + CatalogKey.words(item.name() + " " + item.slug().replace('-', ' ') + " " + item.aisle());
    }
}
