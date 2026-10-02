export interface GeoPoint {
  latitude: number;
  longitude: number;
}

export interface RouteStop<T> {
  stop: T;
  /** Straight-line distance from the previous point (the person or the previous store), in km. */
  legKm: number;
}

const EARTH_RADIUS_KM = 6371;
/** Above this many stores, trying every order gets slow; the nearest-neighbour order is used instead. */
const MAX_STOPS_FOR_EXACT_ORDER = 7;

export function distanceKm(from: GeoPoint, to: GeoPoint) {
  const toRadians = (degrees: number) => (degrees * Math.PI) / 180;
  const latitudeDelta = toRadians(to.latitude - from.latitude);
  const longitudeDelta = toRadians(to.longitude - from.longitude);
  const a =
    Math.sin(latitudeDelta / 2) ** 2 +
    Math.cos(toRadians(from.latitude)) * Math.cos(toRadians(to.latitude)) * Math.sin(longitudeDelta / 2) ** 2;
  return 2 * EARTH_RADIUS_KM * Math.asin(Math.sqrt(a));
}

/**
 * The order of visits that covers the least straight-line distance starting from `origin` (one way, no return).
 * Small purchases are solved exactly; larger ones use the nearest store at each step.
 */
export function planRoute<T extends GeoPoint>(origin: GeoPoint, stops: T[]): RouteStop<T>[] {
  const ordered = stops.length <= MAX_STOPS_FOR_EXACT_ORDER ? shortestOrder(origin, stops) : nearestNeighbourOrder(origin, stops);
  let previous: GeoPoint = origin;
  return ordered.map((stop) => {
    const legKm = distanceKm(previous, stop);
    previous = stop;
    return { stop, legKm };
  });
}

export function routeLength(route: RouteStop<unknown>[]) {
  return route.reduce((total, leg) => total + leg.legKm, 0);
}

function shortestOrder<T extends GeoPoint>(origin: GeoPoint, stops: T[]): T[] {
  let best: T[] = stops;
  let bestLength = Number.POSITIVE_INFINITY;
  for (const order of permutations(stops)) {
    let length = 0;
    let previous: GeoPoint = origin;
    for (const stop of order) {
      length += distanceKm(previous, stop);
      previous = stop;
    }
    if (length < bestLength) {
      bestLength = length;
      best = order;
    }
  }
  return best;
}

function nearestNeighbourOrder<T extends GeoPoint>(origin: GeoPoint, stops: T[]): T[] {
  const remaining = [...stops];
  const ordered: T[] = [];
  let previous: GeoPoint = origin;
  while (remaining.length) {
    let nearestIndex = 0;
    remaining.forEach((stop, index) => {
      if (distanceKm(previous, stop) < distanceKm(previous, remaining[nearestIndex])) nearestIndex = index;
    });
    const [nearest] = remaining.splice(nearestIndex, 1);
    ordered.push(nearest);
    previous = nearest;
  }
  return ordered;
}

function* permutations<T>(items: T[]): Generator<T[]> {
  if (items.length <= 1) {
    yield items;
    return;
  }
  for (let index = 0; index < items.length; index++) {
    const rest = [...items.slice(0, index), ...items.slice(index + 1)];
    for (const tail of permutations(rest)) yield [items[index], ...tail];
  }
}

export function googleMapsDirectionsUrl(origin: GeoPoint, stops: GeoPoint[]) {
  const format = (point: GeoPoint) => `${point.latitude},${point.longitude}`;
  const destination = stops[stops.length - 1];
  const params = new URLSearchParams({
    api: "1",
    origin: format(origin),
    destination: format(destination),
    travelmode: "driving",
  });
  if (stops.length > 1) params.set("waypoints", stops.slice(0, -1).map(format).join("|"));
  return `https://www.google.com/maps/dir/?${params.toString()}`;
}
