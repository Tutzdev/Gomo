package br.com.supermercados.prices.catalog;

import java.math.BigDecimal;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reduces a retailer description ("Refr. Coca-cola 2lt Pet", "REF COCA COLA 2L") to the identity of the
 * real-world product ("COCA COLA|1x2000ML"). Generic descriptors and packaging words are dropped; brand,
 * variant (Zero, flavour) and exact size are kept, so different products never share a key.
 */
public final class CatalogKey {

    private static final List<Rewrite> REWRITES = List.of(
            new Rewrite("\\bCOCA\\s*COLA\\b", "COCA COLA"),
            new Rewrite("\\bREFRIG(?:ERANTE)?\\b|\\bREFRI\\b|\\bREFR\\b|\\bREF\\b", "REFRIGERANTE"),
            new Rewrite("\\b(?:SEM|S)\\s*ACUCAR\\b", "ZERO"),
            // "AZEITONA VERDE S CAROCO" is without pits; "C CAROCO" (with) stays the default.
            new Rewrite("\\bS\\s+(?=(?:CAROCO|SEMENTES?|GLUTEN|LACTOSE|SAL|PELE|OSSO|CASCA|ALCOOL|GAS|CONSERVANTES?)\\b)", "SEM "),
            // A tray weighed at the till: "Abacate Bandeja 700g (aproximadamente 1 Unid)".
            new Rewrite("\\bAPROX(?:IMADAMENTE)?\\b(?:\\s+\\d+\\s*UN[A-Z]*)?", " "),
            new Rewrite("\\bBANDEJA\\b|\\bBDJ\\b|\\bGRANEL\\b", " "),
            // Butchers write the animal in either gender: "Pernil Suina" and "Pernil Suino" are one cut.
            new Rewrite("\\bSUINA\\b", "SUINO"),
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
            // ERP abbreviations used by Ville, Pame and the VIP stores.
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
            new Rewrite("\\b(?:AG|AGUA)\\s+SANIT\\b", "AGUA SANITARIA"),
            new Rewrite("\\bTORRADO\\s+(?:E\\s+)?MOIDO\\b", "PO"),
            // Still water is the default; sparkling water is a different product.
            new Rewrite("\\bSEM\\s+GAS\\b", " "),
            new Rewrite("\\bCOM\\s+GAS\\b", "COMGAS"),
            // Type 1 is the default grade of rice and beans; type 2 is a different product.
            new Rewrite("\\b(?:TIPO|TP|T)\\s*1\\b(?!\\s*(?:ML|L|KG|G)\\b)", " "),
            new Rewrite("\\b(?:TIPO|TP|T)\\s*2\\b(?!\\s*(?:ML|L|KG|G)\\b)", "TIPO2"),
            // "OLEO LIZA MILHO 900M 900ml": a size cut short by the store's own description.
            new Rewrite("(\\d)\\s*M\\b", "$1 ML"));
    private static final Set<String> NOISE = Set.of(("DE DA DO DAS DOS E EM COM C P PARA NA NO AO A O SABOR BEBIDA "
            + "ENERGETICO REPOSITOR REFRIGERANTE GARRAFA GFA GF PET LATA LT VIDRO DESCARTAVEL EMB EMBALAGEM UN UND UNID "
            + "UNIDADE UNIDADES CX CAIXA GELADO GELADA FRIO FRESCO NOVO NOVA PROMOCAO OFERTA TP TIPO LV "
            + "TRADICIONAL ORIGINAL ENERGY DRINK PRECO X L ML G KG").split(" "));
    private static final Pattern BUNDLE = Pattern.compile(
            "\\b(?:KIT|COMBO|LEVE|PAGUE|BONUS|GRATIS|BRINDE|GANHE|SORTIDOS?|SORTIDAS)\\b|\\+");
    private static final Pattern MEASURE = Pattern.compile("(\\d+(?:\\.\\d+)?)\\s*(ML|L|KG|G)\\b");
    private static final Pattern COUNT = Pattern.compile(
            "\\b(\\d+)\\s*(?:UNIDADES|UNIDADE|UNID|UN|UND)\\b|\\b(\\d+)\\s*X\\s*(?=\\d)|\\b(?:PACK|C|COM)\\s*(\\d+)\\b");
    private static final Pattern NUMBER = Pattern.compile("[\\d.]+");
    private static final Set<String> NOT_BRANDS = Set.of("HORTIFRUTI", "NAGUMO", "PADARIA", "CHINA", "ACOUGUE",
            "DIVERSOS", "GENERICO", "PEIXARIA", "FRIOS", "A GRANEL", "GRANEL", "SEM MARCA", "OUTROS", "BRASIL",
            "IMPORTADO", "NACIONAL");
    /** Tokens seen in at most this many descriptions behave like a brand (e.g. a small local manufacturer). */
    private static final int RARE_TOKEN_LIMIT = 120;

    private final Map<String, List<Brand>> brandsByFirstWord;
    private final Map<String, Integer> documentFrequency;

    private CatalogKey(Map<String, List<Brand>> brandsByFirstWord, Map<String, Integer> documentFrequency) {
        this.brandsByFirstWord = brandsByFirstWord;
        this.documentFrequency = documentFrequency;
    }

    /** Learns brands from the brand fields retailers publish and word rarity from every description. */
    public static CatalogKey learn(Collection<String> declaredBrands, Collection<String> descriptions) {
        Map<String, List<Brand>> brands = new HashMap<>();
        for (String declared : declaredBrands) {
            if (declared == null) continue;
            String brand = words(declared);
            if (brand.length() < 2 || NOT_BRANDS.contains(brand) || brand.chars().allMatch(Character::isDigit)) continue;
            // Numbers never reach a key, so "3 Corações" is recognised by "CORACOES" and keeps its full name.
            List<String> tokens = Arrays.stream(brand.split(" ")).filter(word -> !NUMBER.matcher(word).matches()).toList();
            if (tokens.isEmpty()) continue;
            List<Brand> known = brands.computeIfAbsent(tokens.getFirst(), ignored -> new ArrayList<>());
            if (known.stream().noneMatch(existing -> existing.tokens().equals(tokens))) known.add(new Brand(tokens, brand));
        }
        brands.values().forEach(list -> list.sort((left, right) -> right.tokens().size() - left.tokens().size()));
        Map<String, Integer> frequency = new HashMap<>();
        for (String description : descriptions) {
            for (String word : new LinkedHashSet<>(List.of(words(description).split(" ")))) {
                frequency.merge(word, 1, Integer::sum);
            }
        }
        return new CatalogKey(brands, frequency);
    }

    public Identity identify(String name) {
        // "600 Grama(s)": the plural marker must not survive as a stray "S" word.
        String text = ascii(name).replace("(S)", "S");
        text = text.replaceAll("(\\d),(\\d)", "$1.$2");
        text = text.replaceAll("[^A-Z0-9.]+", " ");
        text = text.replaceAll("(?<!\\d)\\.|\\.(?!\\d)", " ");
        // Loose produce and butcher cuts are priced per kilogram and carry no brand.
        boolean perKilogram = text.contains("PRECO DE 1 KG");
        text = text.replace("PRECO DE 1 KG", " 1 KG ");
        text = text.replaceAll("(\\d)\\s*X\\s*(\\d)", "$1 X $2");
        text = text.replaceAll("(\\d)([A-Z])", "$1 $2");
        text = text.replaceAll("\\s+", " ");
        for (Rewrite rewrite : REWRITES) {
            text = rewrite.pattern().matcher(text).replaceAll(rewrite.replacement());
        }
        if (BUNDLE.matcher(text).find()) return null;

        Set<String> measures = new LinkedHashSet<>();
        List<BigDecimal> measureAmounts = new ArrayList<>();
        List<String> measureUnits = new ArrayList<>();
        Matcher measure = MEASURE.matcher(text);
        while (measure.find()) {
            BigDecimal amount = new BigDecimal(measure.group(1));
            String unit = measure.group(2);
            // "ANIL COLMAN 200ML 0.2 LT" states one size twice, so sizes are compared in ml or g.
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
                if (count.group(group) != null) packCount = Integer.parseInt(count.group(group));
            }
        }
        if (counts > 1 || measures.size() > 1 || packCount <= 0) return null;

        String size;
        if (!measures.isEmpty()) {
            BigDecimal amount = measureAmounts.getFirst();
            String unit = measureUnits.getFirst();
            if (unit.equals("L") || unit.equals("KG")) amount = amount.multiply(BigDecimal.valueOf(1000));
            String base = unit.equals("L") || unit.equals("ML") ? "ML" : "G";
            if (amount.signum() <= 0) return null;
            size = packCount + "x" + amount.stripTrailingZeros().toPlainString() + base;
        } else if (counts == 1) {
            size = packCount + "xUN";
        } else {
            return null;
        }

        String remainder = COUNT.matcher(MEASURE.matcher(text).replaceAll(" ")).replaceAll(" ");
        List<String> tokens = new ArrayList<>();
        for (String word : remainder.split(" ")) {
            if (!word.isBlank() && !NOISE.contains(word) && !NUMBER.matcher(word).matches()) tokens.add(word);
        }
        if (tokens.isEmpty()) return null;

        String brand = findBrand(tokens);
        if (brand == null && perKilogram && size.equals("1x1000G")) {
            // "Cebola Nacional", "Laranja Pera", "Alcatra Bovino" priced per kg: the words alone name the item,
            // so only identical words are the same item (no brand to confirm a looser match).
            return new Identity(String.join(" ", new TreeSet<>(tokens)) + "|" + size, null, size);
        }
        if (brand == null) {
            String rare = tokens.stream().filter(word -> word.length() >= 3
                    && documentFrequency.getOrDefault(word, 0) <= RARE_TOKEN_LIMIT).findFirst().orElse(null);
            // Without a known or distinctive brand word two stores' generic items could be different products.
            if (tokens.size() < 3 || rare == null) return null;
        }
        return new Identity(String.join(" ", new TreeSet<>(tokens)) + "|" + size, brand, size);
    }

    /** Splits what a shopper typed ("coca zero 2 litros") into the same vocabulary used by keys. */
    public static SearchTerms searchTerms(String query) {
        String text = ascii(query == null ? "" : query);
        text = text.replaceAll("(\\d),(\\d)", "$1.$2");
        text = text.replaceAll("[^A-Z0-9.]+", " ");
        text = text.replaceAll("(?<!\\d)\\.|\\.(?!\\d)", " ");
        text = text.replaceAll("(\\d)([A-Z])", "$1 $2");
        for (Rewrite rewrite : REWRITES) {
            text = rewrite.pattern().matcher(text).replaceAll(rewrite.replacement());
        }
        String size = null;
        Matcher measure = MEASURE.matcher(text);
        if (measure.find()) {
            BigDecimal amount = new BigDecimal(measure.group(1));
            String unit = measure.group(2);
            if (unit.equals("L") || unit.equals("KG")) amount = amount.multiply(BigDecimal.valueOf(1000));
            size = "x" + amount.stripTrailingZeros().toPlainString() + (unit.equals("L") || unit.equals("ML") ? "ML" : "G");
            text = MEASURE.matcher(text).replaceAll(" ");
        }
        List<String> tokens = new ArrayList<>();
        for (String word : text.split(" ")) {
            if (!word.isBlank() && !NOISE.contains(word) && !NUMBER.matcher(word).matches() && !tokens.contains(word)) {
                tokens.add(word);
            }
        }
        return new SearchTerms(List.copyOf(tokens), size);
    }

    public record SearchTerms(List<String> tokens, String size) {
    }

    private String findBrand(List<String> tokens) {
        for (int index = 0; index < tokens.size(); index++) {
            for (Brand brand : brandsByFirstWord.getOrDefault(tokens.get(index), List.of())) {
                int end = index + brand.tokens().size();
                if (end <= tokens.size() && tokens.subList(index, end).equals(brand.tokens())) return brand.name();
            }
        }
        return null;
    }

    /** Uppercase, accent-free words used for keys and search text. */
    public static String words(String value) {
        return ascii(value).replaceAll("[^A-Z0-9]+", " ").strip();
    }

    private static String ascii(String value) {
        return Normalizer.normalize(value == null ? "" : value, Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "").toUpperCase(Locale.ROOT);
    }

    /** "1x2000ML" → "2 L", "6x350ML" → "6 × 350 ml", "30xUN" → "30 un". */
    public static String sizeLabel(String size) {
        int separator = size.indexOf('x');
        int count = Integer.parseInt(size.substring(0, separator));
        String measure = size.substring(separator + 1);
        if (measure.equals("UN")) return count + " un";
        String unit = measure.endsWith("ML") ? "ML" : "G";
        BigDecimal amount = new BigDecimal(measure.substring(0, measure.length() - unit.length()));
        String single;
        if (amount.compareTo(BigDecimal.valueOf(1000)) >= 0) {
            String value = amount.divide(BigDecimal.valueOf(1000)).stripTrailingZeros().toPlainString().replace('.', ',');
            single = value + (unit.equals("ML") ? " L" : " kg");
        } else {
            single = amount.stripTrailingZeros().toPlainString().replace('.', ',') + (unit.equals("ML") ? " ml" : " g");
        }
        return count == 1 ? single : count + " × " + single;
    }

    public record Identity(String key, String brand, String size) {
    }

    /** A declared brand: the words that find it in a description and the name it is shown with. */
    private record Brand(List<String> tokens, String name) {
    }

    private record Rewrite(Pattern pattern, String replacement) {
        Rewrite(String regex, String replacement) {
            this(Pattern.compile(regex), replacement);
        }
    }
}
