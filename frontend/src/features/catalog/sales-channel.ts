import type { SalesChannel } from "@/types/api";

export function channelNote(channel: SalesChannel) {
  switch (channel) {
    case "ONLINE":
      return "Preço do site do mercado. Na loja física pode ser diferente.";
    case "IN_STORE":
      return "Preço anotado na prateleira pela equipe do Gomo. Pode ter mudado desde a visita.";
    case "PHYSICAL_FLYER":
      return "Oferta do encarte da loja.";
    default:
      return null;
  }
}

export function seenAtLabel(channel: SalesChannel) {
  if (channel === "IN_STORE") {
    return "Anotado na loja em";
  }

  return "Visto no site do mercado em";
}
