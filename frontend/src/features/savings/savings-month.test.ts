import { describe, expect, it } from "vitest";
import { monthName, recentMonths } from "./savings-month";

describe("recentMonths", () => {
  it("follows the São Paulo calendar and crosses the year", () => {
    // 02:00 UTC on 1 February is still 31 January in São Paulo.
    expect(recentMonths(3, new Date("2026-02-01T02:00:00Z"))).toEqual(["2026-01", "2025-12", "2025-11"]);
  });
});

describe("monthName", () => {
  it("says only the month this year and adds the year otherwise", () => {
    const today = new Date("2026-10-20T12:00:00Z");
    expect(monthName("2026-10", today)).toBe("outubro");
    expect(monthName("2025-12", today)).toBe("dezembro de 2025");
  });
});
