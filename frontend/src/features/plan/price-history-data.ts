import type { CatalogItemHistory } from "@/types/api";

/** One day of the chart: the city's cheapest and priciest store, plus the store the person picked. */
export interface HistoryDay {
  day: string;
  lowest: number;
  lowestStore: string;
  highest: number;
  selected: number | null;
}

export function buildHistoryDays(history: CatalogItemHistory, selectedStoreId: string): HistoryDay[] {
  const byDay = new Map<string, HistoryDay>();
  for (const store of history.stores) {
    for (const point of store.points) {
      const current = byDay.get(point.day);
      const selected = store.storeId === selectedStoreId ? point.price : null;
      if (!current) {
        byDay.set(point.day, { day: point.day, lowest: point.price, lowestStore: store.storeName, highest: point.price, selected });
        continue;
      }
      if (point.price < current.lowest) {
        current.lowest = point.price;
        current.lowestStore = store.storeName;
      }
      current.highest = Math.max(current.highest, point.price);
      if (selected !== null) current.selected = selected;
    }
  }
  return [...byDay.values()].sort((first, second) => first.day.localeCompare(second.day));
}

export function formatDay(day: string) {
  const [year, month, date] = day.split("-").map(Number);
  return new Intl.DateTimeFormat("pt-BR", { day: "2-digit", month: "2-digit" }).format(new Date(year, month - 1, date));
}

export function dayTime(day: string) {
  const [year, month, date] = day.split("-").map(Number);
  return new Date(year, month - 1, date).getTime();
}
