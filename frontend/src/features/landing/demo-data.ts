/*
 * Dados ilustrativos da landing page. Não vêm da API e são sempre exibidos
 * com o selo "Exemplo ilustrativo": servem apenas para explicar o produto.
 * As lojas têm nomes genéricos para não atribuir preços inventados a redes reais.
 */

export interface DemoStorePrice {
  store: string;
  price: number;
}

export interface DemoProduct {
  id: string;
  name: string;
  shortName: string;
  prices: DemoStorePrice[];
}

export const demoProducts: DemoProduct[] = [
  {
    id: "coca",
    name: "Coca-Cola Original 2 L",
    shortName: "Coca-Cola 2 L",
    prices: [
      { store: "Supermercado Avenida", price: 10.49 },
      { store: "Mercado Central", price: 8.99 },
      { store: "Mercado do Bairro", price: 11.29 },
      { store: "Atacarejo Norte", price: 9.79 },
    ],
  },
  {
    id: "arroz",
    name: "Arroz Tipo 1 Tio João 5 kg",
    shortName: "Arroz 5 kg",
    prices: [
      { store: "Supermercado Avenida", price: 27.9 },
      { store: "Mercado Central", price: 29.49 },
      { store: "Mercado do Bairro", price: 25.98 },
      { store: "Atacarejo Norte", price: 31.9 },
    ],
  },
  {
    id: "cafe",
    name: "Café Torrado e Moído Pilão 500 g",
    shortName: "Café 500 g",
    prices: [
      { store: "Supermercado Avenida", price: 18.99 },
      { store: "Mercado Central", price: 21.5 },
      { store: "Mercado do Bairro", price: 19.79 },
      { store: "Atacarejo Norte", price: 17.89 },
    ],
  },
];

export const demoListTotals: DemoStorePrice[] = [
  { store: "Mercado Central", price: 287.4 },
  { store: "Supermercado Avenida", price: 301.15 },
  { store: "Atacarejo Norte", price: 309.8 },
];

export function sortByPrice<T extends DemoStorePrice>(prices: T[]) {
  return [...prices].sort((first, second) => first.price - second.price);
}
