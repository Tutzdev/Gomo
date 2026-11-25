import { describe, expect, it } from "vitest";
import { pricedStoresLabel } from "./comparison-text";

describe("comparison text", () => {
  it("says in how many markets of the city the product has a price", () => {
    expect(pricedStoresLabel(9, "Volta Redonda")).toBe("Preço em 9 mercados de Volta Redonda");
    expect(pricedStoresLabel(1, "Volta Redonda")).toBe("Preço em 1 mercado de Volta Redonda");
    expect(pricedStoresLabel(0, "Volta Redonda")).toBe("Sem preço atual nos mercados de Volta Redonda");
    expect(pricedStoresLabel(3)).toBe("Preço em 3 mercados");
  });
});
