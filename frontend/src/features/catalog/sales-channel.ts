import type { SalesChannel } from "@/types/api";

export function channelNote(channel: SalesChannel) {
  switch (channel) {
    case "ONLINE":
      return "Preço do site do mercado. Na loja física pode ser diferente.";
    case "PHYSICAL_FLYER":
      return "Oferta do encarte da loja.";
    default:
      return null;
  }
}
