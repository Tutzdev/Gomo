export const BRAND = {
  name: "Gomo",
  logoWordmark: "gomo",
  monthlyPrice: 14.9,
  annualPrice: 119.9,
  currency: "BRL",
  locale: "pt-BR",
} as const;

export const formatCurrency = (value: number | string) =>
  new Intl.NumberFormat(BRAND.locale, {
    style: "currency",
    currency: BRAND.currency,
  }).format(Number(value));

export const formatDate = (value: string | null | undefined) => {
  if (!value) return "Não informado";

  return new Intl.DateTimeFormat(BRAND.locale, {
    dateStyle: "short",
    timeStyle: "short",
  }).format(new Date(value));
};
