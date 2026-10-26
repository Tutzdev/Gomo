import type { Trip } from "@/types/api";

/**
 * What one trip to one market remembers on this phone: the items put in the cart here (a list split between two
 * markets sends each purchase with its own items), the prices typed from the shelf and the items already in a
 * recorded purchase, so a second tap on "Terminei a compra" never records them again. Kept in localStorage so a
 * reload in the middle of the market loses nothing; cleared when the list starts over.
 */
export interface TripSession {
  boughtHere: string[];
  shelfPrices: Record<string, number>;
  recorded: string[];
}

export const EMPTY_SESSION: TripSession = { boughtHere: [], shelfPrices: {}, recorded: [] };

const sessionKey = (listId: string, storeId: string) => `gomo:trip-session:${listId}:${storeId}`;
const snapshotKey = (listId: string, storeId: string) => `gomo:trip-snapshot:${listId}:${storeId}`;

export function loadSession(listId: string, storeId: string): TripSession {
  try {
    const stored = JSON.parse(localStorage.getItem(sessionKey(listId, storeId)) ?? "null") as Partial<TripSession> | null;
    return {
      boughtHere: Array.isArray(stored?.boughtHere) ? stored.boughtHere : [],
      shelfPrices: stored?.shelfPrices && typeof stored.shelfPrices === "object" ? stored.shelfPrices : {},
      recorded: Array.isArray(stored?.recorded) ? stored.recorded : [],
    };
  } catch {
    return EMPTY_SESSION;
  }
}

export function saveSession(listId: string, storeId: string, session: TripSession) {
  try {
    localStorage.setItem(sessionKey(listId, storeId), JSON.stringify(session));
  } catch {
    // Blocked storage: the trip is remembered while the page is open.
  }
}

export function clearSession(listId: string, storeId: string) {
  try {
    localStorage.removeItem(sessionKey(listId, storeId));
  } catch {
    // Nothing stored, nothing to clear.
  }
}

/** The last trip loaded, so the page still opens with the list if the signal drops right at the door. */
export function loadSnapshot(listId: string, storeId: string): Trip | undefined {
  try {
    return (JSON.parse(localStorage.getItem(snapshotKey(listId, storeId)) ?? "null") as Trip | null) ?? undefined;
  } catch {
    return undefined;
  }
}

export function saveSnapshot(trip: Trip) {
  try {
    localStorage.setItem(snapshotKey(trip.listId, trip.storeId), JSON.stringify(trip));
  } catch {
    // The snapshot is a convenience.
  }
}
