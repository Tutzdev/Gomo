import { describe, expect, it } from "vitest";
import { channelNote, seenAtLabel } from "./sales-channel";

describe("sales channel", () => {
  it("explains where each price comes from", () => {
    expect(channelNote("ONLINE")).toBe("Preço do site do mercado. Na loja física pode ser diferente.");
    expect(channelNote("IN_STORE")).toContain("anotado na prateleira");
    expect(channelNote("PHYSICAL_FLYER")).toBe("Oferta do encarte da loja.");
    expect(channelNote("UNSPECIFIED")).toBeNull();
  });

  it("says when the price was seen", () => {
    expect(seenAtLabel("IN_STORE")).toBe("Anotado na loja em");
    expect(seenAtLabel("ONLINE")).toBe("Visto no site do mercado em");
  });
});
