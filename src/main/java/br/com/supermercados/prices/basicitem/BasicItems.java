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
    private static final String BEBIDAS = "Bebidas";
    private static final String LIMPEZA = "Limpeza";
    private static final String HIGIENE = "Higiene";

    private static final String READY_MEALS = "PRONTO|PRONTA|COZID[OA]|VAPZA|ESCONDIDINHO|LASANHA|SOPA|CALDO|TEMPERO|SABOR"
            + "|KIT|BISCOITO|BOLACHA|SALGADINHO|SNACK|CHIPS|SORVETE|PAPINHA|RACAO|DOG|CAT|SHAMPOO|CONDICIONADOR"
            + "|SABONETE|CREME TRAT|MASCARA|BARRA|BALA|IOGURTE|BEBIDA|SUCO|GELEIA|TEMPERAD[OA]";

    private static final String PROCESSED_MEAT = "MOID[OA]|MOIDA|TEMPERAD[OA]|EMPANAD[OA]|HAMBURG|ALMONDEG|ESCONDIDINHO"
            + "|DEFUMAD[OA]|NUGGET|LINGUICA|SALSICHA|KIBE|QUIBE|STEAK|RACAO|SUIN[OA]|CORDEIRO|FRANGO|PERU|CHESTER"
            + "|PEIXE|SALMAO|VEGETAL|SOJA|LIMPADOR|LIMPA|DESENGORDURANTE|MR|SUI|STROGONOFF";

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
            "\\b(?:INTEGRAL|INTEG[A-Z]*|INT|GRAOS|GLUTEN|GLUTEM|GLU|ZERO|ZEROS|LIGHT|AVEIA|CENTEIO|PROTEINA|PROTEINAS"
                    + "|CHIA|BRIOCHE|SEMENTES|LINHACA|MULTIGRAOS|1[0-9] ?GR(?:A|AOS)?|100 I|ABOBOR[A-Z]*|CAST[A-Z]*"
                    + "|CRANB[A-Z]*|FRUT[A-Z]*|MACA|COCO|MILHO|CANELA|PASSAS|QUINOA|NOZES|AUSTRALIANO|"
                    + READY_MEALS + ")\\b"),
        BasicItem.of(
            "pao-frances",
            "Pão francês",
            PADARIA,
            Measure.KG,
            "1",
            "\\bPAO\\b(?: [A-Z0-9]+){0,1} (?:FRANCES|CARIOQUINHA)\\b|^PAO DE SAL\\b",
            "\\b(?:CONGELAD[OA]|PRE ASSADO|MINI|TAPIOCA|INTEGRAL|MISTURA|GRAOS|GLUTEN|GLUTEM|SCHAR|"
                    + READY_MEALS + ")\\b"),
        BasicItem.of(
            "carne-moida",
            "Carne moída bovina",
            ACOUGUE,
            Measure.KG,
            "0.5",
            "\\bMOID[AO]\\b",
            "\\b(?:VEGETAL|SOJA|PIMENTA|FRANGO|SUIN[OA]|PERU|CEVADA|CAFE|UVA|CANELA|COMINHO|ERVA|NOZ|PAPRICA|ALHO"
                    + "|OREGANO|CRAVO|GENGIBRE|AMENDOIM|CASTANHA|AMENDOA|COCO|SAL|LINHACA|ACUCAR|CACAU|QUEIJO|LEGUMES"
                    + "|PURE|BATATA|ARROZ|PROTEINA|PASTEL|EMPADA|COXINHA|PIZZA|CANELONE|PEITO|" + READY_MEALS + ")\\b"),
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
                    + "|CONGELAD[OA] PRONTO|GRELHAD[OA]|ASSAD[OA]|FACIL|CERA|EMBUTIDO|COZ|ESPETINHO|MOIDO|CUBOS|TEMP"
                    + "|" + READY_MEALS + ")\\b"),
        BasicItem.of(
            "coxa-e-sobrecoxa",
            "Coxa e sobrecoxa de frango",
            ACOUGUE,
            Measure.KG,
            "1",
            "\\bSOBR?E?COXA\\b|\\bSOBECOXA\\b",
            "\\b(?:EMPANAD[OA]|ASSAD[OA]|DEFUMAD[OA]|PERU|PATO|CHIMICHURRI|TEMP|EMP|FACIL|" + READY_MEALS + ")\\b"),
        BasicItem.of(
            "arroz-branco",
            "Arroz branco tipo 1",
            MERCEARIA,
            Measure.KG,
            "5",
            "^(?:ARROZ|[0-9]ARROZ)\\b",
            "\\b(?:INTEGRAL|PARBO|PARBOILIZADO|PARBOLIZADO|ARBOREO|ARBORIO|JAPONES|GOHAN|SELVAGEM|NEGRO|VERMELHO|CATETO"
                    + "|BASMATI|JASMIM|BIRO|DOCE|FLOCOS|GRAOS|CARRETEIRO|RISOTO|ORIENTAL|SUSHI|TIPO 2|ERVAS|MILANES"
                    + "|GREGO|GREGA|BROCOLIS|ALHO|CEBOLA|COMBO|FARINHA|CREME|SELV|INTEG[A-Z]*|INTGRAL|CARNAROLI|CARNA"
                    + "|ARBOR|PARB[A-Z]*|COZ|VERM|CEREAIS|PRETO|CURRY|FUNGHI|SASANISHIKI|PAELLA|TILAPIA|COUVE|FEIJOADA"
                    + "|MOMIJI|RISO|RISOTOS|VEGETAIS|KANGAI|ITALIANO|" + READY_MEALS + ")\\b"),
        BasicItem.of(
            "feijao-carioca",
            "Feijão carioca",
            MERCEARIA,
            Measure.KG,
            "1",
            "\\bFEIJAO\\b(?: [A-Z0-9]+){0,4} (?:CARIOCA|CARIOC|CARIO|CARI|CAR)\\b"
                    + "|\\bCARIOCA\\b(?: [A-Z0-9]+){0,3} FEIJAO\\b",
            "\\b(?:FEIJOADA|LATA|CONGELAD[OA]|TIPO 2|FUGINI|FUGIN|SACHE|" + READY_MEALS + ")\\b"),
        BasicItem.of(
            "feijao-preto",
            "Feijão preto",
            MERCEARIA,
            Measure.KG,
            "1",
            "\\bFEIJAO\\b(?: [A-Z0-9]+){0,4} PRETO\\b",
            "\\b(?:FEIJOADA|LATA|CONGELAD[OA]|ORGANICO|TIPO 2|FUGINI|FUGIN|SACHE|" + READY_MEALS + ")\\b"),
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
            "\\b(?:ORGANICO|ORG|ORGAN|SACHE|SACHES|" + READY_MEALS + ")\\b"),
        BasicItem.of(
            "oleo-de-soja",
            "Óleo de soja",
            MERCEARIA,
            Measure.L,
            "0.9",
            "^(?:OLEO|[0-9]OLEO)\\b(?: [A-Z0-9]+){0,2} SOJA\\b",
            "\\b(?:SPRAY|COMPOSTO|MISTO|AZEITE|SARDINHA|ATUM|ORGANICO|ORGANIC|PIMENTA|" + READY_MEALS + ")\\b"),
        BasicItem.of(
            "cafe-em-po",
            "Café torrado e moído",
            MERCEARIA,
            Measure.KG,
            "0.5",
            "^(?:CAFE|[0-9]CAFE)\\b(?!.*\\b(?:SOLUVEL|CAPSULAS?|CAPPUCCINO|GRAOS?|BEBIDA|COLD|LEITE|SACHE|DESCAFEINADO"
                    + "|FILTRO|COADOR|INSTANTANEO|ORIGINAL NESCAFE|NESCAFE|DOLCE|TRES|CHOCOLATE|COLOMBIANO|ARABICA)\\b)",
            "\\b(?:GOURMET|GOURME|ORGANICO|DESC[A-Z]*|DECAF|SOLU[A-Z]*|REFI[A-Z]*|LIOFILIZADO|DRIP|NESPRESSO|STARBUCKS"
                    + "|GRAN|VD|RITUAIS?|MUNDO|PORTIN|SOL|" + READY_MEALS + ")\\b"),
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
                    + "|ORGANICA|CASCA|" + READY_MEALS + ")\\b"),
        BasicItem.of(
            "macarrao-espaguete",
            "Macarrão espaguete",
            MERCEARIA,
            Measure.KG,
            "0.5",
            "\\b(?:ESPAGUETE|SPAGHETTI|SPAGUETTI|ESPAGUETI)\\b",
            "\\b(?:INTEGRAL|INTEGRALI|ARROZ|GLUTEN|CEREALI|PALMITO|CENOURA|ABOBRINHA|LEGUMES|INSTANTANEO|LAMEN"
                    + "|MOLHO|CONGELAD[OA]|GRAO DE BICO|LENTILHA|DOLCE GABBANA|NISSIN|ORGANICA|INTEGRALE|" + READY_MEALS
                    + ")\\b"),
        BasicItem.of(
            "sal-refinado",
            "Sal refinado",
            MERCEARIA,
            Measure.KG,
            "1",
            "^(?:SAL|[0-9]SAL)\\b(?: [A-Z0-9]+){0,3} REFINADO\\b|^SAL [A-Z]+ 1 ?KG$",
            "\\b(?:LIGHT|SACHE|SACHES|HIMALAIA|ROSA|GROSSO|PARRILLA|SALEIRO|" + READY_MEALS + ")\\b"),
        BasicItem.of(
            "ovos-brancos",
            "Ovos brancos",
            FRIOS,
            Measure.UN,
            "12",
            "^(?:OVOS?|[0-9]OVOS?)\\b",
            "\\b(?:PASCOA|PASC|CHOCOLATE|KINDER|LACTA|ARCOR|CODORNA|PASTEURIZADO|LIQUIDO|DESIDRATADO|PO|CAIPIRA"
                    + "|CAIPIRAS|VERM[A-Z]*|VEERM|HAPPY|ORGANICOS?|ORGANIC|COLHER|TRUFADO|RECHEADO|" + READY_MEALS
                    + ")\\b"),
        BasicItem.of(
            "manteiga",
            "Manteiga com sal",
            FRIOS,
            Measure.KG,
            "0.2",
            "^(?:MANTEIGA|[0-9]MANTEIGA)\\b",
            "\\b(?:SEM SAL|S SAL|S SA|S S|CACAU|KARITE|AMENDOIM|GHEE|GARRAFA|AROMA|VAGEM|FEIJAO|PIPOCA|CAPILAR|VEG"
                    + "|VEGETAL|VEGANA|CASTANHA|AVEIA|BUFALA|BUFULA|BLISTER|GORDURA|" + READY_MEALS + ")\\b"),
        BasicItem.of(
            "margarina",
            "Margarina com sal",
            FRIOS,
            Measure.KG,
            "0.5",
            "^(?:MARGARINA|[0-9]MARGARINA)\\b",
            "\\b(?:SEM SAL|S SAL|BALDE|LIGHT|LIQUIDA|FOLHADA|ERVAS|" + READY_MEALS + ")\\b"),
        BasicItem.of(
            "tomate",
            "Tomate",
            HORTIFRUTI,
            Measure.KG,
            "1",
            "^(?:TOMATE|[0-9]TOMATE)\\b",
            "\\b(?:MOLHO|EXTRATO|PELADO|SECO|CEREJA|GRAPE|SWEET|SUCO|POLPA|PASSATA|KETCHUP|CATCHUP|PURE|ORGANICO"
                    + "|DESIDRATADO|CONSERVA|SARDINHA|PELADOS|PEL|PELATI|TRITURADO|COQUETEL|UVA|TOMATINHO|" + READY_MEALS
                    + ")\\b"),
        BasicItem.of(
            "batata",
            "Batata inglesa",
            HORTIFRUTI,
            Measure.KG,
            "1",
            "^(?:BATATA|[0-9]BATATA)\\b",
            "\\b(?:DOCE|BAROA|YACON|PALHA|CHIPS|FRITA|PRE FRITA|CONGELAD[OA]|PURE|RUFFLES|PRINGLES|LAYS|ONDULADA"
                    + "|PALITO|SMILE|NOISETTE|RUSTICA|CRINKLE|ROSTI|ASTERIX|BOLINHA|ORGANICA|CONG|MCCAIN|MC CAIN"
                    + "|BEM BRASIL|PRE|CONSERVA|PROCESSADA|YOKITOS|SALSA|CALABRESA|KARITOS|JOHNNY|JACKER|CRISPS|OND"
                    + "|ONDUL|PIRAQUE|PRAMESA|SENSACOES|YAKON|FRIES|UAI|PIFPAF|BOBS|AIRFRYER|CUBOS|CANOA|CLASSICAS"
                    + "|CASA DE MAE|LISA|KERUS|" + READY_MEALS + ")\\b"),
        BasicItem.of(
            "cebola",
            "Cebola",
            HORTIFRUTI,
            Measure.KG,
            "1",
            "^(?:CEBOLA|[0-9]CEBOLA)\\b",
            "\\b(?:ROXA|CREME|FRITA|DESIDRATADA|FLOCOS|EM PO|PO|CONSERVA|CEBOLINHA|ORGANICA|BRANCA|CARAMELIZADA"
                    + "|GRANULADA|ALHO|SALSA|CRISPY|PALHA|PICADA|ONION|" + READY_MEALS + ")\\b"),
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
            "\\b(?:CHIPS|PASSA|DESIDRATADA|ORGANICA|" + READY_MEALS + ")\\b"),
        BasicItem.of(
            "mucarela",
            "Muçarela",
            FRIOS,
            Measure.KG,
            "1",
            "^(?:QUEIJO |QJO )?(?:MU[CS]+AREL+A|MOZZARELLA|MOZARELA|MUZZARELA)\\b",
            "\\b(?:BUFALA|BUFULA|BUFALO|LACTOSE|LACTO|LAC|LACFREE|MINI|LIGHT|FIT|VEG|VEGANA|VEGANO|VEGETAL|CASTANHA|CAJU|BOLINHA|NOZINHO|PALITO"
                    + "|PALITOS|TRANCA|CEREJA|PIZZA|PAO|PASTEL|ESFIHA|EMPANAD[OA]|RALAD[OA]|SALGADINHO|BATATA)\\b"),
        BasicItem.of(
            "presunto",
            "Presunto cozido",
            FRIOS,
            Measure.KG,
            "1",
            "^PRESUNTO\\b",
            "\\b(?:PARMA|CRU|SERRANO|DEFUMAD[OA]|ALEMAO|BEINSCHINKEN|ROYAL|ROYALE|GOURMET|MIGNON|MIGNONETO|MINGHONETO"
                    + "|FILE|PERU|CHESTER|ITALIANO|ITA|TENDER|SPECIALE|PIZZA|PAO|PASTEL|CROISSANT|ENROLADINHO|MISTO"
                    + "|QUEIJO)\\b"),
        BasicItem.of(
            "iogurte-natural",
            "Iogurte natural",
            FRIOS,
            Measure.KG,
            "0.17",
            "^IOGURTE\\b(?: [A-Z0-9]+){0,4} NATURAL\\b",
            "\\b(?:GREGO|DESNATADO|ZERO|LIGHT|MEL|FRUTAS|CONFEITO|MORANGO|AMEIXA|COCO|LACFREE|LACTOSE|PROTEINA|SEMENTES"
                    + "|GRANOLA|POLPA|CALDA|MIX|BAUNILHA|VEGETAL|VEG|SKYR|KEFIR|FIT|BATIDO|LIQUIDO|LIQ|COLAGENO|TANGERINA"
                    + "|CENOURA|LARANJA)\\b"),
        BasicItem.of(
            "refrigerante",
            "Refrigerante 2 L",
            BEBIDAS,
            Measure.L,
            "2",
            "^(?:REFRIGERANTE|REFRIG|REFRI|REFR)\\b|^(?:COCA COLA|GUARANA ANTARCTICA|GUARANA ANTARTICA|PEPSI|FANTA"
                    + "|SPRITE|SUKITA|KUAT)\\b(?=.*\\b[0-9]+ ?(?:L|LT|LTS|LITROS?)\\b)",
            "\\b(?:ZERO|ZE|DIET|LIGHT|BLACK|S AC|S ACUCA|S ACUCAR|SEM ACUCAR|LATA|LT|COPO|BALA|PIRULITO|XAROPE|GELATINA"
                    + "|PO|REFIL|SUCO|ENERGETICO|TONICA|AGUA|H2O|H2OH)\\b"),
        BasicItem.of(
            "detergente",
            "Detergente líquido",
            LIMPEZA,
            Measure.L,
            "0.5",
            "^(?:DETERGENTE|DETERG|DET)\\b",
            "\\b(?:PO|EM PO|MAQ|MAQUINA|LAVA LOUCAS|PASTILHAS?|TABLETES?|GEL|GELATINOSO|HARPIC|SANITARIO|BLOCO|ROUPAS?"
                    + "|DESINFETANTE|LIMPADOR|CONCENTRADO|CONC|PASTA)\\b"),
        BasicItem.of(
            "sabao-em-po",
            "Sabão em pó",
            LIMPEZA,
            Measure.KG,
            "1.6",
            "^SABAO\\b(?: EM)? PO\\b|^LAVA ROUPAS?\\b(?=.*\\b[0-9]+ ?(?:KG|G|GR)\\b)",
            "\\b(?:COCO|LIQUIDO|LIQ|ML|L|BARRA|PASTOSO|TIRA MANCHAS?|ALVEJANTE|AMACIANTE)\\b"),
        BasicItem.of(
            "papel-higienico",
            "Papel higiênico folha dupla",
            HIGIENE,
            Measure.UN,
            "12",
            "^PAPEL HIG(?:IENICO)?\\b",
            "\\b(?:SIMPLES|TRIPLA|ROLAO|300|UMEDECIDO|LENCO|INTERFOLHA|INTERFOLHADO|TOALHA)\\b"));

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
