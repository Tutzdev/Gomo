import { useQuery } from "@tanstack/react-query";
import { ExternalLink, LocateFixed, Navigation, Route } from "lucide-react";
import { useState } from "react";
import { InlineError } from "@/components/ui/feedback";
import { NativeButton } from "@/components/ui/native-button";
import { formatCurrency } from "@/lib/brand";
import { catalogApi } from "@/services/gomo-api";
import type { ShoppingCombination, ShoppingRecommendation } from "@/types/api";
import { distanceKm, googleMapsDirectionsUrl, planRoute, routeLength, type GeoPoint } from "./route-planner";

type LocationState =
  | { status: "idle" }
  | { status: "locating" }
  | { status: "ready"; origin: GeoPoint }
  | { status: "failed"; message: string };

const formatKm = (value: number) =>
  `${value.toLocaleString("pt-BR", { minimumFractionDigits: 1, maximumFractionDigits: 1 })} km`;

/**
 * Premium: the order to visit the stores of the split purchase, from where the person is, and what the split
 * costs in distance compared with buying everything at the best single store. Distances are straight lines;
 * the location stays in the browser.
 */
export function ShoppingRoute({
  cityId,
  combination,
  splitSavings,
}: {
  cityId: string;
  combination: ShoppingCombination;
  splitSavings: ShoppingRecommendation["splitSavings"];
}) {
  const [location, setLocation] = useState<LocationState>({ status: "idle" });
  const stores = useQuery({
    queryKey: ["stores", "all", cityId],
    queryFn: () => catalogApi.allStores(cityId),
    enabled: location.status === "ready",
  });

  if (combination.stores.length < 2) return null;

  const locate = () => {
    if (!("geolocation" in navigator)) {
      setLocation({ status: "failed", message: "Este navegador não informa a localização." });
      return;
    }
    setLocation({ status: "locating" });
    navigator.geolocation.getCurrentPosition(
      (position) => setLocation({ status: "ready", origin: { latitude: position.coords.latitude, longitude: position.coords.longitude } }),
      (error) =>
        setLocation({
          status: "failed",
          message: error.code === error.PERMISSION_DENIED
            ? "Sem permissão de localização. Libere o acesso no navegador para montar a rota."
            : "Não foi possível obter sua localização. Tente de novo.",
        }),
      { enableHighAccuracy: false, timeout: 10_000, maximumAge: 300_000 },
    );
  };

  const coordinates = new Map(
    (stores.data ?? []).flatMap((store) =>
      store.latitude !== null && store.longitude !== null
        ? [[store.id, { latitude: Number(store.latitude), longitude: Number(store.longitude) }] as const]
        : [],
    ),
  );
  const stops = combination.stores.flatMap((purchase) => {
    const point = coordinates.get(purchase.storeId);
    return point ? [{ ...point, purchase }] : [];
  });
  const missingCoordinates = combination.stores.length - stops.length;

  return (
    <section className="surface p-5" aria-labelledby="shopping-route-title">
      <h3 id="shopping-route-title" className="flex items-center gap-2 font-bold">
        <Route className="size-4 text-primary" aria-hidden />
        Rota da compra dividida
      </h3>
      <p className="mt-1 text-sm text-muted">
        A ordem de visita que percorre menos distância a partir de onde você está. Sua localização não sai do navegador.
      </p>

      {location.status !== "ready" ? (
        <div className="mt-4">
          <NativeButton variant="secondary" loading={location.status === "locating"} onClick={locate}>
            <LocateFixed className="size-4" aria-hidden />
            Usar minha localização
          </NativeButton>
          {location.status === "failed" ? <InlineError>{location.message}</InlineError> : null}
        </div>
      ) : stores.isPending ? (
        <p className="mt-4 text-sm text-muted">Calculando a rota…</p>
      ) : stores.isError ? (
        <InlineError>Não foi possível carregar os endereços dos mercados.</InlineError>
      ) : stops.length === 0 ? (
        <p className="mt-4 text-sm text-muted">Os mercados desta compra ainda não têm localização cadastrada.</p>
      ) : (
        <RouteResult origin={location.origin} stops={stops} missingCoordinates={missingCoordinates} splitSavings={splitSavings} coordinates={coordinates} />
      )}
    </section>
  );
}

function RouteResult({
  origin,
  stops,
  missingCoordinates,
  splitSavings,
  coordinates,
}: {
  origin: GeoPoint;
  stops: Array<GeoPoint & { purchase: ShoppingCombination["stores"][number] }>;
  missingCoordinates: number;
  splitSavings: ShoppingRecommendation["splitSavings"];
  coordinates: Map<string, GeoPoint>;
}) {
  const route = planRoute(origin, stops);
  const totalKm = routeLength(route);
  const singleStore = splitSavings ? coordinates.get(splitSavings.storeId) : undefined;
  const singleStoreKm = singleStore ? distanceKm(origin, singleStore) : null;
  const extraKm = singleStoreKm === null ? null : totalKm - singleStoreKm;

  return (
    <div className="mt-4">
      <ol className="space-y-2">
        {route.map(({ stop, legKm }, index) => (
          <li key={stop.purchase.storeId} className="flex items-center gap-3 rounded-lg bg-surface-strong px-3 py-2.5">
            <span className="grid size-7 shrink-0 place-items-center rounded-full bg-primary text-xs font-bold text-white">{index + 1}</span>
            <div className="min-w-0 flex-1">
              <p className="truncate font-semibold">{stop.purchase.storeName}</p>
              <p className="text-xs text-muted">
                {stop.purchase.items.length} {stop.purchase.items.length === 1 ? "item" : "itens"} · {formatCurrency(stop.purchase.subtotal)}
              </p>
            </div>
            <span className="shrink-0 text-xs font-semibold tabular-nums text-muted">+{formatKm(legKm)}</span>
          </li>
        ))}
      </ol>

      <p className="mt-4 text-sm">
        Trajeto total: <strong className="tabular-nums">{formatKm(totalKm)}</strong> em linha reta, só ida.
      </p>
      {splitSavings && extraKm !== null ? (
        <p className="mt-1 text-sm text-muted">
          {extraKm > 0.05
            ? `Dividir a compra economiza ${formatCurrency(splitSavings.savings)} e soma ${formatKm(extraKm)} em relação a ir só ao ${splitSavings.storeName}.`
            : `Dividir a compra economiza ${formatCurrency(splitSavings.savings)} sem aumentar a distância em relação a ir só ao ${splitSavings.storeName}.`}
        </p>
      ) : null}
      {missingCoordinates > 0 ? (
        <p className="mt-1 text-xs text-muted">
          {missingCoordinates === 1 ? "1 mercado não tem localização cadastrada e ficou fora da rota." : `${missingCoordinates} mercados não têm localização cadastrada e ficaram fora da rota.`}
        </p>
      ) : null}

      <a
        className="mt-4 inline-flex min-h-10 items-center gap-2 rounded-lg border border-border-strong px-4 text-sm font-semibold hover:border-primary/35 hover:bg-primary-soft focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-focus"
        href={googleMapsDirectionsUrl(origin, route.map(({ stop }) => stop))}
        target="_blank"
        rel="noreferrer"
      >
        <Navigation className="size-4" aria-hidden />
        Abrir rota no Google Maps
        <ExternalLink className="size-3.5" aria-hidden />
      </a>
    </div>
  );
}
