import { formatDate } from "@/lib/brand";
import type { Store, StorePriceStatus } from "@/types/api";

const STATUS: Record<StorePriceStatus, { label: string; tone: "success" | "danger" | "neutral" }> = {
  CURRENT: { label: "Online", tone: "success" },
  OUTDATED: { label: "Fora do ar", tone: "danger" },
  NO_PRICES: { label: "Sem preços", tone: "neutral" },
};

const IN_STORE_STATUS: Record<StorePriceStatus, { label: string; tone: "success" | "danger" | "neutral" }> = {
  CURRENT: { label: "Preço da loja", tone: "success" },
  OUTDATED: { label: "Preços vencidos", tone: "danger" },
  NO_PRICES: { label: "Sem preços", tone: "neutral" },
};

/** Badge and explanation for a market on the markets list and on its own page. */
export function storeStatus(store: Store) {
  const inStore = store.priceChannel === "IN_STORE";
  const status = (inStore ? IN_STORE_STATUS : STATUS)[store.priceStatus ?? "NO_PRICES"];
  const updated = store.lastPriceCollectedAt ? formatDate(store.lastPriceCollectedAt) : null;
  if (inStore) {
    const help =
      store.priceStatus === "CURRENT"
        ? `Mercado sem loja online. Preços anotados na loja em ${updated}.`
        : store.priceStatus === "OUTDATED"
          ? `Preços anotados na loja em ${updated}. Ficam fora das buscas e comparações até a próxima visita.`
          : "Mercado sem loja online. Os preços chegam depois da primeira visita da equipe do Gomo.";
    return { ...status, help };
  }
  const help =
    store.priceSourceNote ??
    (store.priceStatus === "CURRENT"
      ? `Preços atualizados em ${updated}.`
      : store.priceStatus === "OUTDATED"
        ? `Último preço em ${updated}. Fica fora das buscas e comparações até a próxima coleta.`
        : "Os preços deste mercado chegam em breve.");
  return { ...status, help };
}
