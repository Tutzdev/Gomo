import type { SalesChannel } from "@/types/api";

export const DELIVERY_APP_BADGE = "Preço no app (iFood)";

export function isDeliveryApp(channel: SalesChannel) {
  return channel === "DELIVERY_APP";
}

export function channelNote(channel: SalesChannel) {
  switch (channel) {
    case "ONLINE":
      return "Preço do site do mercado. Na loja física pode ser diferente.";
    case "IN_STORE":
      return "Preço anotado na prateleira pela equipe do Gomo. Pode ter mudado desde a visita.";
    case "PHYSICAL_FLYER":
      return "Oferta do encarte da loja.";
    case "DELIVERY_APP":
      return "Preço no app (iFood), porque o mercado não tem loja online própria. Na loja física pode ser diferente.";
    default:
      return null;
  }
}

export function seenAtLabel(channel: SalesChannel) {
  switch (channel) {
    case "IN_STORE":
      return "Anotado na loja em";
    case "DELIVERY_APP":
      return "Visto no iFood em";
    default:
      return "Visto no site do mercado em";
  }
}

export function sourcePlace(channel: SalesChannel) {
  if (isDeliveryApp(channel)) {
    return "no iFood";
  }

  return "no site do mercado";
}

export function sourceDescription(channel: SalesChannel) {
  if (isDeliveryApp(channel)) {
    return "Do app iFood, onde o mercado vende.";
  }

  return "Do site do próprio mercado.";
}
