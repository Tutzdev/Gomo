export const BRAND = {
  name: "Gomo",
  logoWordmark: "gomo",
  monthlyPrice: 14.9,
  annualPrice: 119.9,
  currency: "BRL",
  locale: "pt-BR",
} as const;

const DAY_IN_MS = 24 * 60 * 60 * 1000;

export const formatCurrency = (value: number | string) =>
  new Intl.NumberFormat(BRAND.locale, {
    style: "currency",
    currency: BRAND.currency,
  }).format(Number(value));

export const formatDate = (value: string | null | undefined) => {
  if (!value) {return "Não informado";}

  return new Intl.DateTimeFormat(BRAND.locale, {
    dateStyle: "short",
    timeStyle: "short",
  }).format(new Date(value));
};

/** "hoje às 11:09", "ontem às 21:35" or "em 23/11/2026 às 08:00": reads after "Atualizado", in one format everywhere. */
export const formatUpdatedAt = (
  value: string | Date | null | undefined,
  now: Date = new Date(),
) => {
  if (!value) {return "Não informado";}

  const date = new Date(value);
  const time = new Intl.DateTimeFormat(BRAND.locale, {
    hour: "2-digit",
    minute: "2-digit",
  }).format(date);
  const daysAgo = Math.round((startOfDay(now) - startOfDay(date)) / DAY_IN_MS);

  if (daysAgo === 0) {return `hoje às ${time}`;}

  if (daysAgo === 1) {return `ontem às ${time}`;}

  const day = new Intl.DateTimeFormat(BRAND.locale, { dateStyle: "short" }).format(date);

  return `em ${day} às ${time}`;
};

function startOfDay(date: Date) {
  return new Date(
    date.getFullYear(),
    date.getMonth(),
    date.getDate(),
  ).getTime();
}
