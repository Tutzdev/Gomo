import { useQuery } from "@tanstack/react-query";
import { useAuth } from "@/features/auth/auth-context";
import { useDefaultCityId } from "@/lib/use-default-city";
import { catalogApi } from "@/services/gomo-api";
import type { CatalogItem, CatalogItemDetail } from "@/types/api";

/** One page of the catalog (the products sold in the most stores come first) is enough to find real gaps. */
const CANDIDATE_ITEMS = 60;
/** Only products priced in several stores, so the example reflects the city and not one odd listing. */
const MIN_PRICED_STORES = 5;
/** How many products the visitor can pick in the hero. */
const PRODUCT_OPTIONS = 5;

export interface HeroOffer {
  store: string;
  price: number;
}

export interface HeroProduct {
  id: string;
  name: string;
}

export interface HeroComparison {
  products: HeroProduct[];
  product: HeroProduct;
  cityName: string;
  /** City-wide range, from the catalog (the public view may hide some markets). */
  lowest: number;
  highest: number;
  lowestStore: string;
  pricedStores: number;
  /** The markets the public view shows, from the cheapest to the most expensive. */
  offers: HeroOffer[];
  /** Markets priced but hidden in the public view. */
  lockedStores: number;
  collectedAt: Date | null;
  /** False while showing the built-in example (API offline or city without data). */
  live: boolean;
}

/*
 * O exemplo de quando a API não responde: preços reais da Coca-Cola 2 L coletados em 05/10/2026.
 * Ao atualizar, troque os números aqui e a data em FALLBACK_COLLECTED_ON.
 */
export const FALLBACK_COLLECTED_ON = "5/10";

const fallbackOffers: HeroOffer[] = [
  { store: "Hortifruti Aterrado", price: 10.49 },
  { store: "Pérola Água Limpa", price: 10.79 },
  { store: "Spani Volta Redonda", price: 11.5 },
  { store: "Ville Sessenta", price: 11.9 },
  { store: "Nagumo Ponte Alta", price: 12.59 },
  { store: "Royal Retiro", price: 12.79 },
  { store: "Bramil Santo Agostinho", price: 13.69 },
];

const fallback: HeroComparison = {
  products: [{ id: "exemplo", name: "Coca-Cola 2 L" }],
  product: { id: "exemplo", name: "Coca-Cola 2 L" },
  cityName: "Volta Redonda",
  lowest: fallbackOffers[0].price,
  highest: fallbackOffers.at(-1)!.price,
  lowestStore: fallbackOffers[0].store,
  pricedStores: fallbackOffers.length,
  offers: fallbackOffers,
  lockedStores: 0,
  collectedAt: null,
  live: false,
};

/**
 * The hero's example: the products whose price changes the most between markets of the same city, and the
 * current price of the chosen one in each market. Public endpoints only, so it never spends an account's
 * daily comparisons. Falls back to the built-in example while loading or without data.
 */
export function useHeroComparison(selectedId: string | null): HeroComparison {
  const { user } = useAuth();
  const cityId = useDefaultCityId({ signedIn: Boolean(user) });

  const cities = useQuery({
    queryKey: ["cities", "public"],
    queryFn: () => catalogApi.cities(undefined, 0, 100),
    staleTime: 10 * 60 * 1000,
  });
  const candidates = useQuery({
    queryKey: ["landing-price-gaps", cityId],
    queryFn: () => catalogApi.catalogItems({ cityId, size: CANDIDATE_ITEMS }),
    enabled: Boolean(cityId),
    staleTime: 10 * 60 * 1000,
  });

  const products = biggestGaps(candidates.data?.content ?? []);
  const selected = products.find((item) => item.id === selectedId) ?? products[0];

  const detail = useQuery({
    queryKey: ["public-catalog-item", selected?.id, cityId],
    queryFn: () => catalogApi.publicCatalogItem(selected!.id, cityId),
    enabled: Boolean(selected && cityId),
    staleTime: 10 * 60 * 1000,
    // While the next product loads, the hero keeps showing the previous one instead of blinking.
    placeholderData: (previous) => previous,
  });

  const product = products.find((item) => item.id === detail.data?.item.id);
  const offers = detail.data ? toOffers(detail.data) : [];
  if (!product || offers.length < 2) return fallback;

  const collectedAt = product.pricesCollectedAt;
  return {
    products: products.map(({ id, name }) => ({ id, name })),
    product: { id: product.id, name: product.name },
    cityName: cities.data?.content.find((city) => city.id === cityId)?.name ?? fallback.cityName,
    lowest: product.lowestPrice!,
    highest: product.highestPrice!,
    lowestStore: product.lowestPriceStore ?? offers[0].store,
    pricedStores: product.pricedStores,
    offers,
    lockedStores: detail.data?.access.lockedStores ?? 0,
    collectedAt: collectedAt ? new Date(collectedAt) : null,
    live: true,
  };
}

function biggestGaps(items: CatalogItem[]) {
  return items
    .filter(
      (item) =>
        item.lowestPrice !== null &&
        item.highestPrice !== null &&
        item.highestPrice > item.lowestPrice &&
        item.pricedStores >= MIN_PRICED_STORES,
    )
    .sort((a, b) => b.highestPrice! / b.lowestPrice! - a.highestPrice! / a.lowestPrice!)
    .slice(0, PRODUCT_OPTIONS);
}

function toOffers(detail: CatalogItemDetail): HeroOffer[] {
  return detail.offers
    .flatMap((offer) =>
      offer.price.status === "KNOWN" && offer.price.unitPrice !== null
        ? [{ store: offer.storeName, price: offer.price.unitPrice }]
        : [],
    )
    .sort((a, b) => a.price - b.price);
}
