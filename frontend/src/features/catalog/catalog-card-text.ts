import { formatCurrency } from "@/lib/brand";
import type { CatalogItem } from "@/types/api";

type PricedItem = Pick<CatalogItem, "pricedStores" | "lowestPrice" | "highestPrice">;

export function otherStoresLabel(item: PricedItem) {
  const others = item.pricedStores - 1;

  if (others <= 0 || item.lowestPrice === null) {
    return "Só este mercado tem preço agora";
  }

  const markets = others === 1 ? "No outro mercado" : `Nos outros ${others} mercados`;

  if (item.highestPrice === null || item.highestPrice <= item.lowestPrice) {
    return `${markets}: o mesmo preço`;
  }

  return `${markets}: até ${formatCurrency(item.highestPrice)}`;
}
