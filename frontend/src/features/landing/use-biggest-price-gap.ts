import { useQuery } from "@tanstack/react-query";
import { useAuth } from "@/features/auth/auth-context";
import { useDefaultCityId } from "@/lib/use-default-city";
import { catalogApi } from "@/services/gomo-api";
import type { CatalogItem } from "@/types/api";

/** The catalog lists the products sold in the most stores first; one page of them is enough to find real gaps. */
const CANDIDATE_ITEMS = 60;
/** Only products priced in several stores, so the gap reflects the city and not one odd listing. */
const MIN_PRICED_STORES = 5;

export interface PriceGap {
  productName: string;
  lowest: number;
  highest: number;
  collectedAt: Date | null;
}

/**
 * Real current prices: the product whose price changes the most from one market to another in the same city
 * (the visitor's preferred city, or one that has markets). Returns null while loading or without data, so the
 * page keeps its message and simply drops the number.
 */
export function useBiggestPriceGap(): PriceGap | null {
  const { user } = useAuth();
  const cityId = useDefaultCityId({ signedIn: Boolean(user) });
  // One request instead of a search per product: each catalog query prices the whole city on the server.
  const candidates = useQuery({
    queryKey: ["landing-price-gaps", cityId],
    queryFn: () => catalogApi.catalogItems({ cityId, size: CANDIDATE_ITEMS }),
    enabled: Boolean(cityId),
    staleTime: 10 * 60 * 1000,
  });

  const gaps = (candidates.data?.content ?? []).flatMap((item) => {
    const gap = toPriceGap(item);
    return gap ? [gap] : [];
  });
  if (!gaps.length) return null;
  return gaps.reduce((biggest, gap) => (gap.highest / gap.lowest > biggest.highest / biggest.lowest ? gap : biggest));
}

function toPriceGap(item: CatalogItem): PriceGap | null {
  if (item.lowestPrice === null || item.highestPrice === null || item.pricedStores < MIN_PRICED_STORES) return null;
  if (item.highestPrice <= item.lowestPrice) return null;
  return {
    productName: item.name,
    lowest: item.lowestPrice,
    highest: item.highestPrice,
    collectedAt: item.pricesCollectedAt ? new Date(item.pricesCollectedAt) : null,
  };
}

export function isToday(date: Date) {
  return date.toDateString() === new Date().toDateString();
}

export function formatCollectionTime(date: Date) {
  const time = new Intl.DateTimeFormat("pt-BR", { hour: "2-digit", minute: "2-digit" }).format(date);
  if (isToday(date)) return `hoje às ${time}`;
  return `em ${new Intl.DateTimeFormat("pt-BR", { day: "2-digit", month: "2-digit" }).format(date)} às ${time}`;
}
