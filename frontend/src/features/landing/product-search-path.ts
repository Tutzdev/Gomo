export function productSearchPath(query: string) {
  const term = query.trim();

  return term ? `/app/produtos?q=${encodeURIComponent(term)}` : "/app/produtos";
}
