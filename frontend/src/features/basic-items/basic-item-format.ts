import { formatDate } from "@/lib/brand";
import { priceByMeasure } from "@/features/catalog/unit-price";
import type { BasicItem, MissingStore } from "@/types/api";

export function perMeasure(value: number, item: Pick<BasicItem, "measure">) {
  return priceByMeasure(value, item.measure);
}

export function basicItemCoverage(item: BasicItem) {
  if (!item.comparedStores) return "Compara todas as marcas";
  return `Em ${item.pricedStores} de ${item.comparedStores} mercados`;
}

export function missingReason(store: MissingStore) {
  switch (store.reason) {
    case "OUT_OF_STOCK":
      return store.lastPriceCollectedAt
        ? `O mercado vende, mas estava em falta no site na última leitura (${formatDate(store.lastPriceCollectedAt)}).`
        : "O mercado vende, mas está em falta no site agora.";
    case "NOT_CHECKED_IN_STORE":
      return "Mercado sem loja online: ainda não conferimos este item na prateleira.";
    case "OUTDATED_PRICES":
      return store.lastPriceCollectedAt
        ? `Os preços deste mercado estão desatualizados (última leitura em ${formatDate(store.lastPriceCollectedAt)}).`
        : "Ainda não conseguimos ler os preços deste mercado.";
    default:
      return store.lastPriceCollectedAt
        ? `Não está à venda no site do mercado na última leitura (${formatDate(store.lastPriceCollectedAt)}).`
        : "Não está à venda no site do mercado.";
  }
}
