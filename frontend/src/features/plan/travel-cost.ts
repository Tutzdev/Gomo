export type TravelMode = "CAR" | "BUS" | "WALK";

export interface TravelSettings {
  mode: TravelMode;
  /** Car consumption, km per liter. */
  kmPerLiter: number;
  /** Fuel price, R$ per liter. */
  fuelPrice: number;
  /** One bus ride, R$. */
  busFare: number;
}

/**
 * Streets are longer than the straight line between two points. 1.3 is the usual detour factor for city
 * driving; it keeps the estimate honest without a routing service that would receive the person's location.
 */
export const ROAD_FACTOR = 1.3;

export const DEFAULT_TRAVEL_SETTINGS: TravelSettings = {
  mode: "CAR",
  kmPerLiter: 10,
  fuelPrice: 6.2,
  busFare: 5,
};

const STORAGE_KEY = "gomo:travel-settings";

/** Kept only in this browser, like the location: a convenience, never sent to the server. */
export function loadTravelSettings(): TravelSettings {
  try {
    const stored = JSON.parse(localStorage.getItem(STORAGE_KEY) ?? "null") as Partial<TravelSettings> | null;
    if (!stored) return DEFAULT_TRAVEL_SETTINGS;
    return {
      mode: stored.mode === "BUS" || stored.mode === "WALK" ? stored.mode : "CAR",
      kmPerLiter: positiveOr(stored.kmPerLiter, DEFAULT_TRAVEL_SETTINGS.kmPerLiter),
      fuelPrice: positiveOr(stored.fuelPrice, DEFAULT_TRAVEL_SETTINGS.fuelPrice),
      busFare: positiveOr(stored.busFare, DEFAULT_TRAVEL_SETTINGS.busFare),
    };
  } catch {
    return DEFAULT_TRAVEL_SETTINGS;
  }
}

export function saveTravelSettings(settings: TravelSettings) {
  try {
    localStorage.setItem(STORAGE_KEY, JSON.stringify(settings));
  } catch {
    // Private windows and blocked storage: the settings just last until the page closes.
  }
}

export interface TravelEstimate {
  /** Extra street distance of the split, round trip, compared with going to the single store and back. */
  extraKm: number;
  /** What the extra trip costs in fuel or fares. */
  extraCost: number;
  /** The split savings minus the extra trip. */
  netSavings: number;
}

/**
 * The real saving of a split purchase once the trip is paid: round trip from home through every stop and back,
 * against a round trip to the single store. By bus, each extra stop is one more ride.
 */
export function estimateTravel(
  settings: TravelSettings,
  savings: number,
  splitRoundTripKm: number,
  singleRoundTripKm: number,
  stops: number,
): TravelEstimate {
  const extraKm = Math.max(0, (splitRoundTripKm - singleRoundTripKm) * ROAD_FACTOR);
  const extraCost =
    settings.mode === "CAR"
      ? (extraKm / settings.kmPerLiter) * settings.fuelPrice
      : settings.mode === "BUS"
        ? Math.max(0, stops - 1) * settings.busFare
        : 0;
  const rounded = Math.round(extraCost * 100) / 100;
  return { extraKm, extraCost: rounded, netSavings: Math.round((savings - rounded) * 100) / 100 };
}

function positiveOr(value: unknown, fallback: number) {
  return typeof value === "number" && Number.isFinite(value) && value > 0 ? value : fallback;
}
