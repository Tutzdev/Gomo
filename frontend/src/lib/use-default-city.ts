import { useQuery } from "@tanstack/react-query";
import { catalogApi, preferenceApi } from "@/services/gomo-api";

/**
 * The city to compare in when the person has not picked one: their preferred city, otherwise a city with a
 * market online right now (never the first city alphabetically, which may have none).
 */
export function useDefaultCityId({ signedIn = true }: { signedIn?: boolean } = {}): string {
  const preferences = useQuery({
    queryKey: ["preferences"],
    queryFn: preferenceApi.get,
    retry: false,
    // Preferences belong to an account; public pages skip them for visitors.
    enabled: signedIn,
  });
  const stores = useQuery({
    queryKey: ["stores", "default-city"],
    // The list also has markets that are offline; one with current prices decides the city.
    queryFn: () => catalogApi.stores(undefined, 0, 100),
    staleTime: 10 * 60 * 1000,
  });
  const markets = stores.data?.content ?? [];
  const market = markets.find((store) => store.priceStatus === "CURRENT") ?? markets[0];
  return preferences.data?.preferredCityId || market?.cityId || "";
}
