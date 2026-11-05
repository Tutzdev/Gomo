import { useQuery } from "@tanstack/react-query";
import { useMemo } from "react";
import { catalogApi } from "@/services/gomo-api";

export type CityMarkets = {
  id: string;
  name: string;
  /** Each chain once (two Bramil units show one Bramil). */
  chains: { id: string; name: string }[];
  /** Active stores in the city, units of the same chain counted one by one. */
  storeCount: number;
};

/**
 * Cities with at least one active market, from the public lists, the city with more markets first (the better
 * first impression, and usually the visitor's). Empty while loading. Shared by the logo row, the "Como funciona"
 * note and the FAQ, so the landing never names a city the Gomo does not cover.
 */
export function useCityMarkets(): CityMarkets[] {
  const stores = useQuery({ queryKey: ["stores", "landing"], queryFn: () => catalogApi.stores(undefined, 0, 100), staleTime: 10 * 60 * 1000 });
  const cities = useQuery({ queryKey: ["cities", "public"], queryFn: () => catalogApi.cities(undefined, 0, 100), staleTime: 10 * 60 * 1000 });
  const chains = useQuery({ queryKey: ["chains", "public"], queryFn: () => catalogApi.chains(), staleTime: 10 * 60 * 1000 });

  return useMemo(() => {
    const chainNames = new Map((chains.data?.content ?? []).map((chain) => [chain.id, chain.name]));
    const result = new Map<string, CityMarkets>();
    for (const store of stores.data?.content ?? []) {
      const chainName = chainNames.get(store.supermarketChainId);
      const cityName = cities.data?.content.find((item) => item.id === store.cityId)?.name;
      if (!store.active || !chainName || !cityName) continue;
      const entry = result.get(store.cityId) ?? { id: store.cityId, name: cityName, chains: [], storeCount: 0 };
      entry.storeCount += 1;
      if (!entry.chains.some((chain) => chain.id === store.supermarketChainId)) {
        entry.chains.push({ id: store.supermarketChainId, name: chainName });
      }
      result.set(store.cityId, entry);
    }
    return [...result.values()].sort((a, b) => b.chains.length - a.chains.length || a.name.localeCompare(b.name, "pt-BR"));
  }, [stores.data, cities.data, chains.data]);
}

/** "Volta Redonda e Barra Mansa", "A, B e C". */
export function joinNames(names: string[]) {
  if (names.length < 2) return names.join("");
  return `${names.slice(0, -1).join(", ")} e ${names.at(-1)}`;
}
