import { useQuery } from "@tanstack/react-query";
import { useAuth } from "@/features/auth/auth-context";
import { useDefaultCityId } from "@/lib/use-default-city";
import { catalogApi } from "@/services/gomo-api";

/** The catalog lists the products sold in the most stores first; the largest page the API serves. */
const CANDIDATE_ITEMS = 100;

/**
 * The first page of the visitor's city catalog, shared by the hero (its example products) and the "Como funciona"
 * note (how many products the city has). One request for both, cached for ten minutes.
 */
export function useLandingCatalog() {
  const { user } = useAuth();
  const cityId = useDefaultCityId({ signedIn: Boolean(user) });
  const catalog = useQuery({
    queryKey: ["landing-catalog", cityId],
    queryFn: () => catalogApi.catalogItems({ cityId, size: CANDIDATE_ITEMS }),
    enabled: Boolean(cityId),
    staleTime: 10 * 60 * 1000,
  });
  return { cityId, catalog };
}
