import { describe, expect, it } from "vitest";
import { productSearchPath } from "./product-search-path";

describe("productSearchPath", () => {
  it("carries what was typed to the search page", () => {
    expect(productSearchPath("arroz 5 kg")).toBe("/app/produtos?q=arroz%205%20kg");
  });

  it("encodes characters that would break the address", () => {
    expect(productSearchPath("café & leite")).toBe("/app/produtos?q=caf%C3%A9%20%26%20leite");
  });

  it("opens the search page when nothing was typed", () => {
    expect(productSearchPath("   ")).toBe("/app/produtos");
  });
});
