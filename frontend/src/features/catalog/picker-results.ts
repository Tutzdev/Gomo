/**
 * While a new search loads, the list keeps the previous search's results on screen (dimmed) so it does not flash.
 * Those results answer another question: they can be seen, never chosen.
 */
export function resultsMatchTypedSearch(typed: string, searched: string, previousResults: boolean) {
  return typed.trim() === searched && !previousResults;
}
