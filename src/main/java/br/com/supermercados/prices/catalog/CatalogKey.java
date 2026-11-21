package br.com.supermercados.prices.catalog;

import java.math.BigDecimal;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class CatalogKey {

    private static final List<Rewrite> REWRITES = List.of(
        new Rewrite("\\bCOCA\\s*COLA\\b", "COCA COLA"),
        new Rewrite("\\bREFRIG(?:ERANTE)?\\b|\\bREFRI\\b|\\bREFR\\b|\\bREF\\b", "REFRIGERANTE"),
        new Rewrite("\\b(?:SEM|S)\\s*ACUCAR\\b", "ZERO"),
        new Rewrite("\\bS\\s+(?=(?:CAROCO|SEMENTES?|GLUTEN|LACTOSE|SAL|PELE|OSSO|CASCA|ALCOOL|GAS|CONSERVANTES?)\\b)", "SEM "),
        new Rewrite("\\bAPROX(?:IMADAMENTE)?\\b(?:\\s+\\d+\\s*UN[A-Z]*)?", " "),
        new Rewrite("\\bBANDEJA\\b|\\bBDJ\\b|\\bGRANEL\\b", " "),
        new Rewrite("\\bSUINA\\b", "SUINO"),
        new Rewrite("\\bBANANA\\s+(?:D|DA)?\\s*AGUA\\b", "BANANA NANICA"),
        new Rewrite("\\bTAHITI\\b", "TAITI"),
        new Rewrite("\\bMEIA\\s+DUZIAS?\\b", " 6 UN "),
        new Rewrite("\\bDUZIAS?\\b|\\bDZ\\b", " 12 UN "),
        new Rewrite("\\bBOVINA\\b", "BOVINO"),
        new Rewrite("\\bZERO\\s+ACUCAR\\b", "ZERO"),
        new Rewrite("\\bZERO\\s+ZERO\\b", "ZERO"),
        new Rewrite("\\bENERG\\b|\\bENER\\b|\\bENERGETICA\\b|\\bENERGETICO\\b", "ENERGETICO"),
        new Rewrite("\\bBEB\\b", "BEBIDA"),
        new Rewrite("\\bDET\\b|\\bDETERG\\b", "DETERGENTE"),
        new Rewrite("\\bLIQ\\b", "LIQUIDO"),
        new Rewrite("\\bHELLMANN\\s*S\\b", "HELLMANNS"),
        new Rewrite("\\bANTARTICA\\b", "ANTARCTICA"),
        new Rewrite("\\bLITROS?\\b|\\bLTS?\\b", "L"),
        new Rewrite("\\bGRS?\\b|\\bGRAMAS?\\b", "G"),
        new Rewrite("\\bQUILOS?\\b|\\bQUILOGRAMAS?\\b", "KG"),
        new Rewrite("\\bMILILITROS?\\b", "ML"),
        new Rewrite("\\bINTEG\\b|\\bINT\\b", "INTEGRAL"),
        new Rewrite("\\bDESNA?\\b", "DESNATADO"),
        new Rewrite("\\bSEMI\\s*DESN(?:ATADO)?\\b", "SEMIDESNATADO"),
        new Rewrite("\\bUHT\\b|\\bLONGA\\s+VIDA\\b", " "),
        new Rewrite("\\bPCT\\b|\\bPACOTE\\b", " "),
        new Rewrite("\\bUNIDS?\\b", "UN"),
        new Rewrite("\\bSABON\\b", "SABONETE"),
        new Rewrite("\\bCOND\\b", "CONDENSADO"),
        new Rewrite("\\bBISC\\b", "BISCOITO"),
        new Rewrite("\\bCHOC\\b", "CHOCOLATE"),
        new Rewrite("\\bTRAD(?:I|IC|ICI|ICIO|ICION)?\\b", "TRADICIONAL"),
        new Rewrite("\\bIOG\\b", "IOGURTE"),
        new Rewrite("\\bREQ\\b", "REQUEIJAO"),
        new Rewrite("\\bMARG\\b", "MARGARINA"),
        new Rewrite("\\bLING\\b", "LINGUICA"),
        new Rewrite("\\bRAL\\b", "RALADO"),
        new Rewrite("\\bAMAC\\b", "AMACIANTE"),
        new Rewrite("\\bDESOD\\b", "DESODORANTE"),
        new Rewrite("\\bACHOC\\b", "ACHOCOLATADO"),
        new Rewrite("\\bROSQ\\b", "ROSQUINHA"),
        new Rewrite("\\bBOLONH\\b", "BOLONHESA"),
        new Rewrite("\\bNAT\\b", "NATURAL"),
        new Rewrite("\\bCONC\\b", "CONCENTRADO"),
        new Rewrite("\\bSC\\b", "SACHE"),
        new Rewrite("\\bSB\\b|\\bLAVA\\s*ROUPAS?\\b", "SABAO"),
        new Rewrite("^ST\\b", "SABONETE"),
        new Rewrite("\\bFRG\\b|\\bFGO\\b", "FRANGO"),
        new Rewrite("\\bRECH\\b", "RECHEADO"),
        new Rewrite("\\bSALG\\b", "SALGADINHO"),
        new Rewrite("\\bDESINF\\b", "DESINFETANTE"),
        new Rewrite("\\bBCO\\b", "BRANCO"),
        new Rewrite("\\bTTO\\b", "TINTO"),
        new Rewrite("\\bMRG\\b", "MORANGO"),
        new Rewrite("\\bZR\\b", "ZERO"),
        new Rewrite("\\bRF\\b", "REFIL"),
        new Rewrite("\\bSCH\\b", "SACHE"),
        new Rewrite("\\bSAB\\b", "SABOR"),
        new Rewrite("\\bLN\\b", "LONG NECK"),
        new Rewrite("(?<=\\b(?:MANTEIGA|MARGARINA)\\b.{0,60})\\b(?:COM|C)\\s+(?:SAL|S)\\b", " "),
        new Rewrite("\\bUVA\\s+TINT[OA]\\b", "UVA"),
        new Rewrite("\\bPANETONE\\b", "PANETTONE"),
        new Rewrite("\\bPARBOLIZADO\\b", "PARBOILIZADO"),
        new Rewrite("\\bCAPELETI\\b", "CAPELETTI"),
        new Rewrite("\\bVANILA\\b", "VANILLA"),
        new Rewrite("\\bWAFFER\\b", "WAFER"),
        new Rewrite("\\bFRISSANTE\\b", "FRISANTE"),
        new Rewrite("\\b(?:AG|AGUA)\\s+SANIT\\b", "AGUA SANITARIA"),
        new Rewrite("\\bTORRADO\\s+(?:E\\s+)?MOIDO\\b", "PO"),
        new Rewrite("\\bSEM\\s+GAS\\b", " "),
        new Rewrite("\\bCOM\\s+GAS\\b", "COMGAS"),
        new Rewrite("\\b(?:TIPO|TP|T)\\s*1\\b(?!\\s*(?:ML|L|KG|G)\\b)", " "),
        new Rewrite("\\b(?:TIPO|TP|T)\\s*2\\b(?!\\s*(?:ML|L|KG|G)\\b)", "TIPO2"),
        new Rewrite("(\\d)\\s*M\\b", "$1 ML"));

    private static final Set<String> NOISE = Set.of(("DE DA DO DAS DOS E EM COM C P PARA NA NO AO A O SABOR BEBIDA "
            + "ENERGETICO REPOSITOR REFRIGERANTE GARRAFA GFA GF PET LATA LT VIDRO DESCARTAVEL EMB EMBALAGEM UN UND UNID "
            + "UNIDADE UNIDADES CX CAIXA GELADO GELADA FRIO FRESCO NOVO NOVA PROMOCAO OFERTA TP TIPO LV "
            + "TRADICIONAL ORIGINAL ENERGY DRINK PRECO X L ML G KG").split(" "));

    private static final Set<String> PRODUCT_KINDS = Set.of("REFRIGERANTE", "ENERGETICO", "REPOSITOR", "BEBIDA");

    private static final Pattern BUNDLE = Pattern.compile(
            "\\bKIT\\b(?!\\s*KAT\\b)|\\b(?:COMBO|PAGUE|BONUS|GRATIS|BRINDE|GANHE|SORTIDOS?|SORTIDAS)\\b|\\+");

    private static final Pattern RAW_BUNDLE = Pattern.compile(
            "\\d\\s*(?:ML|L|LT|G|GR|GRS|KG|M)\\s*\\+|\\+\\s*\\d+(?:[.,]\\d+)?\\s*(?:ML|L|LT|G|GR|GRS|KG|M)\\b"
                    + "|\\bL\\s*\\+\\s*P\\b|\\b(?:SHAMPOO|SH)\\b.*\\+.*\\b(?:CONDICIONADOR|COND|CONDIC)\\b"
                    + "|\\bAPA?R?\\.?\\s*\\+");

    private static final int PRODUCT_WORD_MINIMUM = 15;
    private static final Pattern MEASURE = Pattern.compile("\\b(\\d+(?:\\.\\d+)?)\\s*(ML|L|KG|G)\\b");
    private static final Pattern GLUED_SIZE = Pattern.compile("([A-Z]{2,})(?=\\d+(?:\\.\\d+)?(?:ML|LTS?|L|KG|GRS?|G|M)\\b)");

    private static final Pattern COUNT = Pattern.compile(
            "\\b(\\d+)\\s*(?:UNIDADES|UNIDADE|UNID|UN|UND)\\b|\\b(\\d+)\\s*X\\s*(?=\\d)|\\b(?:PACK|C|COM)\\s*(\\d+)\\b");

    private static final Pattern NUMBER = Pattern.compile("[\\d.]+");

    private static final Set<String> NOT_BRANDS = Set.of(
        "HORTIFRUTI",
        "NAGUMO",
        "PADARIA",
        "CHINA",
        "ACOUGUE",
        "DIVERSOS",
        "GENERICO",
        "PEIXARIA",
        "FRIOS",
        "A GRANEL",
        "GRANEL",
        "SEM MARCA",
        "OUTROS",
        "BRASIL",
        "IMPORTADO",
        "NACIONAL",
        "NATURAL");

    private static final Set<String> PRODUCE_GRADES = Set.of(("MEDIA MEDIO MEDIAS MEDIOS GRAUDA GRAUDO GRAUDAS "
            + "GRAUDOS GRANDE GRANDES PEQUENA PEQUENO MIUDA MIUDO SELECIONADA SELECIONADO NACIONAL FRESCA FRESCO "
            + "KILO").split(" "));

    private static final Map<String, Set<String>> EVERYDAY_VARIETIES = Map.of(
            "TOMATE", Set.of("DEBORA", "SALADA", "CARMEM", "CARMEN", "SANTA", "CRUZ", "LONGA", "VIDA", "VERMELHO",
                    "MADURO", "NETUNO", "RASTEIRO"),
            "BATATA", Set.of("INGLESA", "LAVADA", "ESCOVADA", "COMUM"),
            "CEBOLA", Set.of("AMARELA", "COMUM"));

    private static final Set<String> EGG_PRODUCTS = Set.of(("PASCOA PASC CHOCOLATE KINDER COLHER PASTEURIZADO "
            + "PAUSTERIZADO LIQUIDO PO DESIDRATADO CONSERVA MASSA MACARRAO LASANHA CREME RECHEADO TRUFADO COZIDO "
            + "CLARA CLARAS GEMA GEMAS INTEGRAL").split(" "));

    private static final Map<String, String> EGG_COLOURS = Map.ofEntries(
        Map.entry("BRANCO", "Brancos"),
        Map.entry("BRANCOS", "Brancos"),
        Map.entry("BRANCA", "Brancos"),
        Map.entry("BRANCAS", "Brancos"),
        Map.entry("BCO", "Brancos"),
        Map.entry("BCOS", "Brancos"),
        Map.entry("VERMELHO", "Vermelhos"),
        Map.entry("VERMELHOS", "Vermelhos"),
        Map.entry("VERMELHA", "Vermelhos"),
        Map.entry("VERMELHAS", "Vermelhos"),
        Map.entry("VERM", "Vermelhos"));

    private static final Map<String, String> EGG_SIZES = Map.ofEntries(
        Map.entry("PEQUENO", "Pequenos"),
        Map.entry("PEQUENOS", "Pequenos"),
        Map.entry("MEDIO", "Médios"),
        Map.entry("MEDIOS", "Médios"),
        Map.entry("GRANDE", "Grandes"),
        Map.entry("GRANDES", "Grandes"),
        Map.entry("GDE", "Grandes"),
        Map.entry("GDES", "Grandes"),
        Map.entry("EXTRA", "Extra"),
        Map.entry("EXTRAS", "Extra"),
        Map.entry("JUMBO", "Jumbo"));

    private static final Map<String, String> EGG_KINDS = Map.ofEntries(
        Map.entry("CAIPIRA", "Caipira"),
        Map.entry("CAIPIRAS", "Caipira"),
        Map.entry("ORGANICO", "Orgânicos"),
        Map.entry("ORGANICOS", "Orgânicos"),
        Map.entry("ORGANIC", "Orgânicos"),
        Map.entry("LIVRE", "de Galinhas Livres"),
        Map.entry("LIVRES", "de Galinhas Livres"),
        Map.entry("HAPPY", "de Galinhas Livres"),
        Map.entry("OMEGA", "Ômega 3"),
        Map.entry("OMEGA3", "Ômega 3"),
        Map.entry("CODORNA", "de Codorna"));

    private static final List<String> EGG_KIND_PRIORITY = List.of(
        "de Codorna",
        "Caipira",
        "Orgânicos",
        "de Galinhas Livres",
        "Ômega 3");

    private static final int RARE_TOKEN_LIMIT = 120;

    private final Map<String, List<Brand>> brandsByFirstWord;
    private final Map<String, Integer> documentFrequency;
    private final Set<String> productWords;

    private CatalogKey(
        Map<String, List<Brand>> brandsByFirstWord,
        Map<String, Integer> documentFrequency,
        Set<String> productWords) {

        this.brandsByFirstWord = brandsByFirstWord;
        this.documentFrequency = documentFrequency;
        this.productWords = productWords;
    }

    public static CatalogKey learn(Collection<String> declaredBrands, Collection<String> descriptions) {
        Set<String> productWords = productWords(declaredBrands, descriptions);
        Map<String, List<Brand>> brands = new HashMap<>();

        for (String declared : declaredBrands) {
            if (declared == null) {continue;}

            String brand = words(declared);

            if (brand.length() < 2 || NOT_BRANDS.contains(brand) || brand.chars().allMatch(Character::isDigit)) {
                continue;
            }

            List<String> tokens = Arrays.stream(brand.split(" "))
                    .filter(word -> !NUMBER.matcher(word).matches())
                    .toList();

            if (tokens.isEmpty()) {continue;}

            List<Brand> known = brands.computeIfAbsent(tokens.getFirst(), ignored -> new ArrayList<>());

            if (known.stream().noneMatch(existing -> existing.tokens().equals(tokens))) {
                known.add(new Brand(tokens, brand));
            }
        }

        brands.values().forEach(list -> list.sort((left, right) -> right.tokens().size() - left.tokens().size()));
        Map<String, Integer> frequency = new HashMap<>();

        for (String description : descriptions) {
            for (String word : new LinkedHashSet<>(List.of(words(description).split(" ")))) {
                frequency.merge(word, 1, Integer::sum);
            }
        }

        return new CatalogKey(brands, frequency, productWords);
    }

    private static Set<String> productWords(Collection<String> declaredBrands, Collection<String> descriptions) {
        Map<String, Integer> declared = new HashMap<>();

        for (String brand : declaredBrands) {
            if (brand != null) {declared.merge(words(brand), 1, Integer::sum);}
        }

        Map<String, Integer> starting = new HashMap<>();

        for (String description : descriptions) {
            String first = words(description).split(" ")[0];

            if (declared.containsKey(first)) {starting.merge(first, 1, Integer::sum);}
        }

        Set<String> productWords = new HashSet<>();

        starting.forEach((word, count) -> {
            if (count >= PRODUCT_WORD_MINIMUM && declared.get(word) * 4 < count) {productWords.add(word);}
        });

        return productWords;
    }

    public boolean knownBrand(String brand) {
        if (brand == null) {return false;}

        List<String> tokens = Arrays.stream(words(brand).split(" "))
                .filter(word -> !NUMBER.matcher(word).matches())
                .toList();

        return !tokens.isEmpty() && !productWords.contains(String.join(" ", tokens))
                && brandsByFirstWord.getOrDefault(tokens.getFirst(), List.of()).stream()
                .anyMatch(known -> known.tokens().equals(tokens));
    }

    public static boolean isBundle(String name) {
        String text = ascii(name);

        return RAW_BUNDLE.matcher(text).find() || BUNDLE.matcher(text.replaceAll("[^A-Z0-9]+", " ")).find();
    }

    public Identity identify(String name) {
        if (isBundle(name)) {return null;}

        String text = ascii(name).replace("(S)", "S");
        text = text.replaceAll("(\\d),(\\d)", "$1.$2");
        text = text.replaceAll("[^A-Z0-9.]+", " ");
        text = text.replaceAll("(?<!\\d)\\.|\\.(?!\\d)", " ");
        boolean perKilogram = text.contains("PRECO DE 1 KG");
        text = text.replace("PRECO DE 1 KG", " 1 KG ");
        text = text.replaceAll("(\\d)\\s*X\\s*(\\d)", "$1 X $2");
        text = GLUED_SIZE.matcher(text).replaceAll("$1 ");
        text = text.replaceAll("(\\d)([A-Z])", "$1 $2");
        text = text.replaceAll("\\s+", " ");

        for (Rewrite rewrite : REWRITES) {
            text = rewrite.pattern().matcher(text).replaceAll(rewrite.replacement());
        }

        if (BUNDLE.matcher(text).find()) {return null;}

        Set<String> measures = new LinkedHashSet<>();
        List<BigDecimal> measureAmounts = new ArrayList<>();
        List<String> measureUnits = new ArrayList<>();
        Matcher measure = MEASURE.matcher(text);

        while (measure.find()) {
            BigDecimal amount = new BigDecimal(measure.group(1));
            String unit = measure.group(2);
            boolean thousands = unit.equals("L") || unit.equals("KG");

            String inBaseUnit = (thousands ? amount.multiply(BigDecimal.valueOf(1000)) : amount).stripTrailingZeros()
                    .toPlainString() + (unit.equals("L") || unit.equals("ML") ? "ML" : "G");

            if (measures.add(inBaseUnit)) {
                measureAmounts.add(amount);
                measureUnits.add(unit);
            }
        }

        Matcher count = COUNT.matcher(text);
        int packCount = 1;
        int counts = 0;

        while (count.find()) {
            counts++;

            for (int group = 1; group <= 3; group++) {
                if (count.group(group) != null) {packCount = Integer.parseInt(count.group(group));}
            }
        }

        if (counts > 1 || measures.size() > 1 || packCount <= 0) {return null;}

        String size;

        if (!measures.isEmpty()) {
            BigDecimal amount = measureAmounts.getFirst();
            String unit = measureUnits.getFirst();

            if (unit.equals("L") || unit.equals("KG")) {amount = amount.multiply(BigDecimal.valueOf(1000));}

            String base = unit.equals("L") || unit.equals("ML") ? "ML" : "G";

            if (amount.signum() <= 0) {return null;}

            size = packCount + "x" + amount.stripTrailingZeros().toPlainString() + base;
        } else if (counts == 1) {
            size = packCount + "xUN";
        } else {
            return null;
        }

        String remainder = COUNT.matcher(MEASURE.matcher(text).replaceAll(" ")).replaceAll(" ");
        List<String> tokens = new ArrayList<>();

        for (String word : remainder.split(" ")) {
            if (!word.isBlank() && !NOISE.contains(word) && !NUMBER.matcher(word).matches()) {tokens.add(word);}
        }

        if (tokens.isEmpty()) {return null;}

        if (measures.isEmpty() && (tokens.getFirst().equals("OVO") || tokens.getFirst().equals("OVOS"))) {
            if (name.strip().length() == 30 && name.strip().matches("(?s).*(?:\\d|C/?)$")) {return null;}

            Identity eggs = eggs(tokens, size);

            if (eggs != null) {return eggs;}
        }

        String brand = findBrand(tokens);
        boolean ownName = brand != null && words(brand).equals(tokens.getFirst());

        if ((brand == null || ownName) && perKilogram && size.equals("1x1000G")) {
            List<String> produce = everydayVariety(tokens.stream()
                    .filter(word -> !PRODUCE_GRADES.contains(word) && !word.matches("[A-Z]\\d")).toList());

            if (produce.isEmpty()) {return null;}

            return new Identity(
                String.join(" ", new TreeSet<>(produce)) + "|" + size,
                null,
                size,
                String.join(" ", produce));
        }

        if (brand == null) {
            String rare = tokens.stream().filter(word -> word.length() >= 3
                    && documentFrequency.getOrDefault(word, 0) <= RARE_TOKEN_LIMIT).findFirst().orElse(null);

            if (tokens.size() < 3 || rare == null) {return null;}
        }

        return new Identity(String.join(" ", new TreeSet<>(tokens)) + "|" + size, brand, size);
    }

    private static List<String> everydayVariety(List<String> tokens) {
        if (tokens.isEmpty()) {return tokens;}

        Set<String> tradeNames = EVERYDAY_VARIETIES.get(tokens.getFirst());

        if (tradeNames != null && tokens.subList(1, tokens.size()).stream().allMatch(tradeNames::contains)) {
            return List.of(tokens.getFirst());
        }

        return tokens;
    }

    private static Identity eggs(List<String> tokens, String size) {
        if (!size.endsWith("xUN") || tokens.stream().anyMatch(EGG_PRODUCTS::contains)) {return null;}

        String colour = null;
        String eggSize = null;
        String kind = null;

        for (String word : tokens) {
            if (EGG_COLOURS.containsKey(word)) {colour = EGG_COLOURS.get(word);}

            if (EGG_SIZES.containsKey(word)) {eggSize = EGG_SIZES.get(word);}

            String found = EGG_KINDS.get(word);

            if (found != null && (kind == null || EGG_KIND_PRIORITY.indexOf(found) < EGG_KIND_PRIORITY.indexOf(kind))) {
                kind = found;
            }
        }

        if (colour == null && kind == null) {return null;}

        List<String> name = new ArrayList<>(List.of("Ovos"));

        if ("de Codorna".equals(kind)) {
            name.add(kind);
        } else if ("Caipira".equals(kind)) {
            name.add(kind);

            if (eggSize != null) {name.add(eggSize);}
        } else {
            if (colour != null) {name.add(colour);}

            if (eggSize != null) {name.add(eggSize);}

            if (kind != null) {name.add(kind);}
        }

        String label = String.join(" ", name);

        return new Identity(
            String.join(" ", new TreeSet<>(List.of(words(label).split(" ")))) + "|" + size,
            null,
            size,
            label);
    }

    public static SearchTerms searchTerms(String query) {
        String text = searchable(query);
        String size = null;
        Matcher measure = MEASURE.matcher(text);

        if (measure.find()) {
            BigDecimal amount = new BigDecimal(measure.group(1));
            String unit = measure.group(2);

            if (unit.equals("L") || unit.equals("KG")) {amount = amount.multiply(BigDecimal.valueOf(1000));}

            size = "x" + amount.stripTrailingZeros().toPlainString() + (unit.equals("L") || unit.equals("ML") ? "ML" : "G");
            text = MEASURE.matcher(text).replaceAll(" ");
        }

        List<String> tokens = new ArrayList<>();

        for (String word : text.split(" ")) {
            if (!word.isBlank() && !NOISE.contains(word) && !NUMBER.matcher(word).matches() && !tokens.contains(word)) {
                tokens.add(word);
            }
        }

        if (tokens.isEmpty()) {return new SearchTerms(productKindsIn(text), size);}

        return new SearchTerms(List.copyOf(tokens), size);
    }

    public static List<String> productKinds(String name) {
        return productKindsIn(searchable(name));
    }

    private static List<String> productKindsIn(String text) {
        return Arrays.stream(text.split(" ")).filter(PRODUCT_KINDS::contains).distinct().toList();
    }

    private static String searchable(String value) {
        String text = ascii(value == null ? "" : value);
        text = text.replaceAll("(\\d),(\\d)", "$1.$2");
        text = text.replaceAll("[^A-Z0-9.]+", " ");
        text = text.replaceAll("(?<!\\d)\\.|\\.(?!\\d)", " ");
        text = GLUED_SIZE.matcher(text).replaceAll("$1 ");
        text = text.replaceAll("(\\d)([A-Z])", "$1 $2");

        for (Rewrite rewrite : REWRITES) {
            text = rewrite.pattern().matcher(text).replaceAll(rewrite.replacement());
        }

        return text;
    }

    public record SearchTerms(List<String> tokens, String size) {}

    private String findBrand(List<String> tokens) {
        String productWordBrand = null;

        for (int index = 0; index < tokens.size(); index++) {
            for (Brand brand : brandsByFirstWord.getOrDefault(tokens.get(index), List.of())) {
                int end = index + brand.tokens().size();

                if (end > tokens.size() || !tokens.subList(index, end).equals(brand.tokens())) {continue;}

                if (brand.tokens().size() > 1 || !productWords.contains(brand.tokens().getFirst())) {
                    return brand.name();
                }

                if (index > 0 && productWordBrand == null) {productWordBrand = brand.name();}
            }
        }

        return productWordBrand;
    }

    public static String words(String value) {
        return ascii(value).replaceAll("[^A-Z0-9]+", " ").strip();
    }

    private static String ascii(String value) {
        return Normalizer.normalize(value == null ? "" : value, Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "").toUpperCase(Locale.ROOT).replaceAll("([A-Z])['’´`]S\\b", "$1S");
    }

    public static String sizeLabel(String size) {
        int separator = size.indexOf('x');
        int count = Integer.parseInt(size.substring(0, separator));
        String measure = size.substring(separator + 1);

        if (measure.equals("UN")) {return count + " un";}

        String unit = measure.endsWith("ML") ? "ML" : "G";
        BigDecimal amount = new BigDecimal(measure.substring(0, measure.length() - unit.length()));
        String single;

        if (amount.compareTo(BigDecimal.valueOf(1000)) >= 0) {
            String value = amount.divide(BigDecimal.valueOf(1000))
                    .stripTrailingZeros()
                    .toPlainString()
                    .replace('.', ',');

            single = value + (unit.equals("ML") ? " L" : " kg");
        } else {
            single = amount.stripTrailingZeros().toPlainString().replace('.', ',') + (unit.equals("ML") ? " ml" : " g");
        }

        return count == 1 ? single : count + " × " + single;
    }

    public record Identity(
            String key,
            String brand,
            String size,
            String genericName) {
        public Identity(String key, String brand, String size) {
            this(key, brand, size, null);
        }
    }

    private record Brand(List<String> tokens, String name) {}

    private record Rewrite(Pattern pattern, String replacement) {
        Rewrite(String regex, String replacement) {
            this(Pattern.compile(regex), replacement);
        }
    }
}
