import { BellRing, Hourglass, Minus, ThumbsUp, Trophy, type LucideIcon } from "lucide-react";
import { Link } from "react-router-dom";
import { formatCurrency } from "@/lib/brand";
import { cn } from "@/lib/cn";
import type { PriceVerdict } from "@/types/api";
import { formatDay } from "./price-history-data";

type Tone = "success" | "neutral" | "warning";

/** The history in one sentence: buy now, the usual price, or wait. */
export function PriceVerdictBanner({ verdict, productId }: { verdict: PriceVerdict; productId: string }) {
  const copy = verdictCopy(verdict);
  if (!copy) return null;
  const Icon = copy.icon;

  return (
    <div
      className={cn(
        "mb-4 flex gap-3 rounded-lg border p-4",
        copy.tone === "success" && "border-success/25 bg-success-soft",
        copy.tone === "warning" && "border-warning/25 bg-warning-soft",
        copy.tone === "neutral" && "border-border bg-surface-strong",
      )}
      role="status"
    >
      <Icon
        className={cn(
          "mt-0.5 size-5 shrink-0",
          copy.tone === "success" ? "text-success" : copy.tone === "warning" ? "text-warning" : "text-muted",
        )}
        aria-hidden
      />
      <div className="min-w-0">
        <p className="font-bold">{copy.title}</p>
        <p className="mt-0.5 text-sm text-muted">{copy.detail}</p>
        {verdict.kind === "ABOVE_AVERAGE" ? (
          <Link
            to={`/app/alertas?produto=${productId}&tipo=abaixo-da-media`}
            className="mt-2 inline-flex items-center gap-1.5 text-sm font-semibold text-primary-dark hover:underline focus-visible:outline-2 focus-visible:outline-focus"
          >
            <BellRing className="size-4" aria-hidden />
            Criar um alerta para quando baixar
          </Link>
        ) : null}
      </div>
    </div>
  );
}

function verdictCopy(verdict: PriceVerdict): { icon: LucideIcon; tone: Tone; title: string; detail: string } | null {
  const { currentPrice, averagePrice, lowestPrice, lowestDay, differencePercent, days, periodDays } = verdict;
  if (verdict.kind === "NOT_ENOUGH_DATA" || currentPrice === null || averagePrice === null) return null;
  const usual = `${formatCurrency(averagePrice)} (média de ${days} dias com preço no último mês)`;
  const difference = Math.abs(differencePercent ?? 0);

  switch (verdict.kind) {
    case "LOWEST_IN_PERIOD":
      return {
        icon: Trophy,
        tone: "success",
        title: `Bom momento: ${formatCurrency(currentPrice)} é o menor preço dos últimos ${periodDays} dias`,
        detail: `O preço de sempre é ${usual}. Se é um produto que você usa sempre, vale levar agora.`,
      };
    case "BELOW_AVERAGE":
      return {
        icon: ThumbsUp,
        tone: "success",
        title: `Preço bom: ${difference}% abaixo do preço de sempre`,
        detail: `Hoje sai ${formatCurrency(currentPrice)}; o preço de sempre é ${usual}.`,
      };
    case "USUAL":
      return {
        icon: Minus,
        tone: "neutral",
        title: "Preço de sempre",
        detail: `${formatCurrency(currentPrice)} está perto de ${usual}. Nem promoção, nem alta.`,
      };
    case "ABOVE_AVERAGE":
      return {
        icon: Hourglass,
        tone: "warning",
        title: `Vale esperar: ${difference}% acima do preço de sempre`,
        detail:
          lowestPrice !== null && lowestDay !== null
            ? `Hoje sai ${formatCurrency(currentPrice)}, mas o preço de sempre é ${usual} e já esteve por ${formatCurrency(lowestPrice)} em ${formatDay(lowestDay)}.`
            : `Hoje sai ${formatCurrency(currentPrice)}, mas o preço de sempre é ${usual}.`,
      };
  }
}
