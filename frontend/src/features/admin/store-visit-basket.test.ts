import { describe, expect, it } from "vitest";
import { storeStatus } from "@/features/catalog/store-status";
import type { Store } from "@/types/api";
import { STORE_VISIT_BASKET, STORE_VISIT_BASKET_SIZE } from "./store-visit-basket";

describe("store visit basket", () => {
  it("has a hundred distinct items to write down", () => {
    const terms = STORE_VISIT_BASKET.flatMap((aisle) => aisle.terms);
    expect(STORE_VISIT_BASKET_SIZE).toBe(100);
    expect(new Set(terms).size).toBe(terms.length);
  });
});

describe("store status", () => {
  const store = (overrides: Partial<Store>) => ({ name: "Mercado", lastPriceCollectedAt: null, priceSourceNote: null, ...overrides }) as Store;

  it("tells a market without online store apart from one that went offline", () => {
    expect(storeStatus(store({ priceChannel: "IN_STORE", priceStatus: "NO_PRICES" })).help).toContain("primeira visita");
    expect(storeStatus(store({ priceChannel: "IN_STORE", priceStatus: "OUTDATED", lastPriceCollectedAt: "2026-11-01T12:00:00Z" })).label)
      .toBe("Preços vencidos");
    expect(storeStatus(store({ priceChannel: "ONLINE", priceStatus: "OUTDATED", lastPriceCollectedAt: "2026-11-01T12:00:00Z" })).label)
      .toBe("Fora do ar");
  });
});
