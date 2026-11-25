import { describe, expect, it } from "vitest";
import { pricedStoresLabel, relativeTime } from "./comparison-text";

describe("comparison text", () => {
  it("says in how many markets of the city the product has a price", () => {
    expect(pricedStoresLabel(9, "Volta Redonda")).toBe("Preço em 9 mercados de Volta Redonda");
    expect(pricedStoresLabel(1, "Volta Redonda")).toBe("Preço em 1 mercado de Volta Redonda");
    expect(pricedStoresLabel(0, "Volta Redonda")).toBe("Sem preço atual nos mercados de Volta Redonda");
    expect(pricedStoresLabel(3)).toBe("Preço em 3 mercados");
  });

  it("tells how long ago the price was read", () => {
    const now = new Date("2026-11-25T12:00:00Z").getTime();

    expect(relativeTime("2026-11-25T11:59:30Z", now)).toBe("agora");
    expect(relativeTime("2026-11-25T11:40:00Z", now)).toBe("há 20 min");
    expect(relativeTime("2026-11-25T09:00:00Z", now)).toBe("há 3 h");
    expect(relativeTime("2026-11-24T12:00:00Z", now)).toBe("ontem");
    expect(relativeTime("2026-11-22T12:00:00Z", now)).toBe("há 3 dias");
  });
});
