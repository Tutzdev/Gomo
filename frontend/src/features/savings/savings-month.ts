/** "2026-10" for the current month in the shoppers' calendar (São Paulo), and the months before it. */
export function recentMonths(count: number, today = new Date()): string[] {
  const [year, month] = new Intl.DateTimeFormat("en-CA", { timeZone: "America/Sao_Paulo", year: "numeric", month: "2-digit" })
    .format(today)
    .split("-")
    .map(Number);
  return Array.from({ length: count }, (_, index) => {
    const date = new Date(Date.UTC(year, month - 1 - index, 1));
    return `${date.getUTCFullYear()}-${String(date.getUTCMonth() + 1).padStart(2, "0")}`;
  });
}

/** "outubro" or "outubro de 2025" when it is not this year. */
export function monthName(month: string, today = new Date()) {
  const [year, monthNumber] = month.split("-").map(Number);
  const date = new Date(Date.UTC(year, monthNumber - 1, 15));
  const thisYear = Number(new Intl.DateTimeFormat("en-CA", { timeZone: "America/Sao_Paulo", year: "numeric" }).format(today));
  return new Intl.DateTimeFormat("pt-BR", {
    month: "long",
    ...(year === thisYear ? {} : { year: "numeric" }),
    timeZone: "UTC",
  }).format(date);
}
