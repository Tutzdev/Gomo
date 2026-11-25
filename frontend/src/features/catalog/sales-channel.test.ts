import { describe, expect, it } from "vitest";
import {
  DELIVERY_APP_BADGE,
  channelNote,
  isDeliveryApp,
  seenAtLabel,
  sourceDescription,
  sourcePlace,
} from "./sales-channel";

describe("sales channel", () => {
  it("marks a delivery app price as seen on iFood", () => {
    expect(isDeliveryApp("DELIVERY_APP")).toBe(true);
    expect(DELIVERY_APP_BADGE).toBe("Preço no app (iFood)");
    expect(channelNote("DELIVERY_APP")).toContain("Preço no app (iFood)");
    expect(seenAtLabel("DELIVERY_APP")).toBe("Visto no iFood em");
    expect(sourcePlace("DELIVERY_APP")).toBe("no iFood");
    expect(sourceDescription("DELIVERY_APP")).toContain("iFood");
  });

  it("keeps the wording of the other channels", () => {
    expect(isDeliveryApp("ONLINE")).toBe(false);
    expect(channelNote("ONLINE")).toBe("Preço do site do mercado. Na loja física pode ser diferente.");
    expect(channelNote("IN_STORE")).toContain("anotado na prateleira");
    expect(channelNote("PHYSICAL_FLYER")).toBe("Oferta do encarte da loja.");
    expect(channelNote("UNSPECIFIED")).toBeNull();
    expect(seenAtLabel("IN_STORE")).toBe("Anotado na loja em");
    expect(seenAtLabel("ONLINE")).toBe("Visto no site do mercado em");
    expect(sourcePlace("ONLINE")).toBe("no site do mercado");
    expect(sourceDescription("ONLINE")).toBe("Do site do próprio mercado.");
  });
});
