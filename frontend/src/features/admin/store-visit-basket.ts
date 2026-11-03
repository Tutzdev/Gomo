/**
 * What to write down on a visit to a market without an online store: the items that weigh most in a
 * household's monthly shopping, in the order of a walk through the aisles. Each term opens the product
 * search already filled; the visitor picks the exact brand and size on the shelf.
 */
export const STORE_VISIT_BASKET: { aisle: string; terms: string[] }[] = [
  {
    aisle: "Mercearia",
    terms: [
      "arroz branco 5kg", "arroz parboilizado 5kg", "feijão carioca 1kg", "feijão preto 1kg",
      "açúcar refinado 1kg", "açúcar cristal 5kg", "café em pó 500g", "café em pó 250g",
      "óleo de soja 900ml", "azeite extravirgem 500ml", "sal refinado 1kg", "farinha de trigo 1kg",
      "farinha de mandioca 500g", "fubá 1kg", "macarrão espaguete 500g", "macarrão parafuso 500g",
      "macarrão instantâneo", "molho de tomate 300g", "extrato de tomate", "milho verde lata",
      "ervilha lata", "sardinha lata", "atum lata", "maionese 500g", "ketchup", "vinagre",
      "tempero completo", "caldo de galinha", "aveia em flocos", "achocolatado 400g",
      "leite condensado", "creme de leite", "biscoito cream cracker", "biscoito maisena",
      "biscoito recheado", "gelatina", "fermento em pó", "pipoca de micro-ondas",
    ],
  },
  {
    aisle: "Laticínios e frios",
    terms: [
      "leite integral 1l", "leite desnatado 1l", "manteiga 200g", "margarina 500g", "queijo muçarela",
      "presunto", "requeijão 200g", "iogurte", "queijo minas frescal", "ovos 12 unidades",
      "ovos 30 unidades", "salsicha", "mortadela",
    ],
  },
  {
    aisle: "Açougue",
    terms: [
      "peito de frango kg", "coxa e sobrecoxa kg", "frango inteiro kg", "carne moída kg", "acém kg",
      "patinho kg", "alcatra kg", "costela bovina kg", "linguiça toscana kg", "bisteca suína kg",
    ],
  },
  {
    aisle: "Hortifrúti e padaria",
    terms: [
      "batata kg", "cebola kg", "tomate kg", "alho", "cenoura kg", "banana prata kg", "maçã kg",
      "laranja kg", "limão kg", "alface", "pão francês kg", "pão de forma",
    ],
  },
  {
    aisle: "Bebidas",
    terms: [
      "refrigerante cola 2l", "refrigerante guaraná 2l", "suco em pó", "suco de caixinha 1l",
      "água mineral 1,5l", "cerveja lata 350ml",
    ],
  },
  {
    aisle: "Limpeza",
    terms: [
      "sabão em pó 1kg", "sabão líquido", "amaciante 2l", "detergente 500ml", "água sanitária 2l",
      "desinfetante", "esponja de louça", "sabão em barra", "limpador multiuso", "saco de lixo",
      "papel toalha",
    ],
  },
  {
    aisle: "Higiene",
    terms: [
      "papel higiênico 12 rolos", "creme dental", "sabonete", "xampu", "condicionador", "desodorante",
      "absorvente", "fralda descartável", "escova de dente", "aparelho de barbear",
    ],
  },
];

export const STORE_VISIT_BASKET_SIZE = STORE_VISIT_BASKET.reduce((total, aisle) => total + aisle.terms.length, 0);
