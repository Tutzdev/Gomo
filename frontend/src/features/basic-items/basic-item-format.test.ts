import { describe, expect, it } from "vitest";
import type { BasicItem, MissingStore } from "@/types/api";
import { basicItemCoverage, missingReason, perMeasure } from "./basic-item-format";

const bread: BasicItem = {
  slug: "pao-de-hamburguer",
  name: "Pão de hambúrguer",
  aisle: "Padaria",
  measure: "kg",
  typicalPackage: "400 g",
  pricedStores: 9,
  comparedStores: 9,
  lowestPricePerMeasure: 16.98,
  lowestPriceStore: "Mercado A",
};

function missing(reason: MissingStore["reason"], lastPriceCollectedAt: string | null = null): MissingStore {
  return { storeId: "1", storeName: "Mercado B", reason, lastPriceCollectedAt };
}

describe("basic item format", () => {
  it("shows the price per measure of the item", () => {
    expect(perMeasure(16.98, bread)).toMatch(/16,98 o quilo$/);
  });

  it("says in how many markets the item has a price", () => {
    expect(basicItemCoverage(bread)).toBe("Em 9 de 9 mercados");
    expect(basicItemCoverage({ ...bread, comparedStores: 0 })).toBe("Compara todas as marcas");
  });

  it("explains why a market has no price", () => {
    expect(missingReason(missing("NOT_SOLD_ONLINE"))).toBe("Não está à venda no site do mercado.");
    expect(missingReason(missing("OUT_OF_STOCK"))).toBe("O mercado vende, mas está em falta no site agora.");
    expect(missingReason(missing("NOT_CHECKED_IN_STORE"))).toContain("sem loja online");
    expect(missingReason(missing("OUTDATED_PRICES", "2026-11-18T10:00:00Z"))).toContain("desatualizados");
    expect(missingReason(missing("OUTDATED_PRICES"))).toBe("Ainda não conseguimos ler os preços deste mercado.");
  });
});
