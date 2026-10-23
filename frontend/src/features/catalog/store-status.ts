import { formatDate } from "@/lib/brand";
import type { Store, StorePriceStatus } from "@/types/api";

const STATUS: Record<StorePriceStatus, { label: string; tone: "success" | "danger" | "neutral" }> = {
  CURRENT: { label: "Online", tone: "success" },
  OUTDATED: { label: "Fora do ar", tone: "danger" },
  NO_PRICES: { label: "Sem preços", tone: "neutral" },
};

/** Badge and explanation for a market on the markets list and on its own page. */
export function storeStatus(store: Store) {
  const status = STATUS[store.priceStatus ?? "NO_PRICES"];
  const updated = store.lastPriceCollectedAt ? formatDate(store.lastPriceCollectedAt) : null;
  const help =
    store.priceSourceNote ??
    (store.priceStatus === "CURRENT"
      ? `Preços atualizados em ${updated}.`
      : store.priceStatus === "OUTDATED"
        ? `Último preço em ${updated}. Fica fora das buscas e comparações até a próxima coleta.`
        : "Os preços deste mercado chegam em breve.");
  return { ...status, help };
}
