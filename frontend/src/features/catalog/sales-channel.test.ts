import { describe, expect, it } from "vitest";
import { channelNote } from "./sales-channel";

describe("sales channel", () => {
  it("explains where each price comes from", () => {
    expect(channelNote("ONLINE")).toBe("Preço do site do mercado. Na loja física pode ser diferente.");
    expect(channelNote("PHYSICAL_FLYER")).toBe("Oferta do encarte da loja.");
    expect(channelNote("UNSPECIFIED")).toBeNull();
  });
});
