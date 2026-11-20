import { describe, expect, it } from "vitest";
import { resultsMatchTypedSearch } from "./picker-results";

describe("picker results", () => {
  it("lets the person choose from the results of what they typed", () => {
    expect(resultsMatchTypedSearch("arroz ", "arroz", false)).toBe(true);
  });

  it("blocks choosing while the typed search has not been sent yet", () => {
    expect(resultsMatchTypedSearch("arroz integral", "arroz", false)).toBe(false);
  });

  it("blocks choosing the previous search's results while the new one loads", () => {
    expect(resultsMatchTypedSearch("feijao", "feijao", true)).toBe(false);
  });
});
