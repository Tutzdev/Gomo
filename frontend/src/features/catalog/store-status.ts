import { formatUpdatedAt } from "@/lib/brand";
import type { Store, StorePriceStatus } from "@/types/api";

const STATUS: Record<StorePriceStatus, { label: string; tone: "success" | "danger" | "neutral" }> = {
  CURRENT: { label: "Preços em dia", tone: "success" },
  OUTDATED: { label: "Preços atrasados", tone: "danger" },
  NO_PRICES: { label: "Ainda sem preços", tone: "neutral" },
};

/** Badge and explanation for a market on the markets list and on its own page. */
export function storeStatus(store: Store) {
  const status = STATUS[store.priceStatus ?? "NO_PRICES"];
  const updated = store.lastPriceCollectedAt ? formatUpdatedAt(store.lastPriceCollectedAt) : null;

  const help =
    store.priceSourceNote ??
    (store.priceStatus === "CURRENT"
      ? `Preços atualizados ${updated}.`
      : store.priceStatus === "OUTDATED"
        ? `Último preço ${updated}. Fica fora das buscas e comparações até a próxima coleta.`
        : "Os preços deste mercado chegam em breve.");

  return { ...status, help };
}
