import { describe, expect, it } from "vitest";
import { measureName, measureWords, priceByMeasure, unitPriceFromSize } from "./unit-price";

describe("unit price", () => {
  it("names the measure in words", () => {
    expect(measureName("L")).toBe("litro");
    expect(measureWords("kg")).toBe("o quilo");
    expect(measureWords("L")).toBe("o litro");
    expect(measureWords("un")).toBe("a unidade");
    expect(measureWords("M")).toBe("o metro");
    expect(priceByMeasure(3.6, "kg")).toMatch(/3,60 o quilo$/);
  });

  it("divides the shelf price by the package size", () => {
    expect(unitPriceFromSize(4.6, "1 kg")).toMatch(/4,60 o quilo$/);
    expect(unitPriceFromSize(17.99, "5 kg")).toMatch(/3,60 o quilo$/);
    expect(unitPriceFromSize(16.98, "900 ml")).toMatch(/18,87 o litro$/);
    expect(unitPriceFromSize(1.83, "200 ml")).toMatch(/9,15 o litro$/);
    expect(unitPriceFromSize(9, "2 × 1,5 L")).toMatch(/3,00 o litro$/);
    expect(unitPriceFromSize(2.99, "175 g")).toMatch(/17,09 o quilo$/);
    expect(unitPriceFromSize(12, "12 un")).toMatch(/1,00 a unidade$/);
  });

  it("says nothing when the size is unknown or a single unit", () => {
    expect(unitPriceFromSize(5, "1 un")).toBeNull();
    expect(unitPriceFromSize(5, "")).toBeNull();
    expect(unitPriceFromSize(5, null)).toBeNull();
    expect(unitPriceFromSize(5, "pacote grande")).toBeNull();
  });
});
