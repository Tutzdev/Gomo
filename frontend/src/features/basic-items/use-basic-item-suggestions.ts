import { useQuery } from "@tanstack/react-query";
import { basicItemApi } from "@/services/gomo-api";

const MAXIMUM_SUGGESTIONS = 3;

export function useBasicItemSuggestions(query: string, cityId: string | undefined) {
  return useQuery({
    queryKey: ["basic-items", query, cityId],
    queryFn: ({ signal }) => basicItemApi.search(query, cityId, signal),
    enabled: query.trim().length >= 3,
    staleTime: 60_000,
    select: (items) => items.slice(0, MAXIMUM_SUGGESTIONS),
  });
}
