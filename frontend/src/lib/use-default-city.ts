import { useQuery } from "@tanstack/react-query";
import { catalogApi, preferenceApi } from "@/services/gomo-api";

/**
 * The city to compare in when the person has not picked one: their preferred city, otherwise a city that
 * actually has markets (never the first city alphabetically, which may have none).
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
    queryFn: () => catalogApi.stores(undefined, 0, 1),
    staleTime: 10 * 60 * 1000,
  });
  return preferences.data?.preferredCityId || stores.data?.content[0]?.cityId || "";
}
