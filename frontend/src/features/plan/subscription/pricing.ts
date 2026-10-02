import { BRAND, formatCurrency } from "@/lib/brand";
import type { BillingCycle } from "@/types/api";

export function dailyCostLabel(cycle: BillingCycle) {
  return cycle === "ANNUAL"
    ? `${formatCurrency(BRAND.annualPrice / 365)} por dia`
    : "menos de R$ 0,50 por dia";
}

export function priceLabel(cycle: BillingCycle) {
  return cycle === "ANNUAL" ? `${formatCurrency(BRAND.annualPrice)}/ano` : `${formatCurrency(BRAND.monthlyPrice)}/mês`;
}
