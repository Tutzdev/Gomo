import { describe, expect, it } from "vitest";
import { otherStoresLabel } from "./catalog-card-text";

describe("catalog card text", () => {
  it("says up to how much the other markets charge", () => {
    expect(otherStoresLabel({ pricedStores: 9, lowestPrice: 3.89, highestPrice: 5.69 })).toMatch(/^Nos outros 8 mercados: até R\$\s5,69$/);
    expect(otherStoresLabel({ pricedStores: 2, lowestPrice: 3.89, highestPrice: 4.1 })).toMatch(/^No outro mercado: até R\$\s4,10$/);
  });

  it("covers equal prices and a single market", () => {
    expect(otherStoresLabel({ pricedStores: 3, lowestPrice: 2, highestPrice: 2 })).toBe("Nos outros 2 mercados: o mesmo preço");
    expect(otherStoresLabel({ pricedStores: 1, lowestPrice: 2, highestPrice: 2 })).toBe("Só este mercado tem preço agora");
  });
});
