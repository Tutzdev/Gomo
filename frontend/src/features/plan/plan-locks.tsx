import { CalendarRange, Lock, Sparkles } from "lucide-react";
import { useEffect } from "react";
import { Link } from "react-router-dom";
import { cn } from "@/lib/cn";
import type { ComparisonAccess } from "@/types/api";
import { useUpsell } from "./upsell-context";

/**
 * Stand-in rows for the stores the free plan hides. The rows are decorative placeholders, never real names or
 * prices, so nothing locked leaks into the page.
 */
export function LockedStores({ count, className }: { count: number; className?: string }) {
  const { showUpsell } = useUpsell();
  if (count <= 0) return null;
  const placeholderRows = Math.min(count, 3);

  return (
    <div className={cn("locked-stores", className)}>
      <div className="locked-stores__rows" aria-hidden>
        {Array.from({ length: placeholderRows }, (_, index) => (
          <div key={index} className="locked-stores__row">
            <span className="locked-stores__dot" />
            <span className="locked-stores__name" style={{ width: `${46 - index * 7}%` }} />
            <span className="locked-stores__price" />
          </div>
        ))}
      </div>
      <button
        type="button"
        className="locked-stores__cta"
        onClick={() => showUpsell("LOCKED_STORES", { lockedStores: count })}
      >
        <Lock className="size-4" aria-hidden />
        <span>
          {count === 1 ? "+1 mercado disponível no Premium" : `+${count} mercados disponíveis no Premium`}
        </span>
      </button>
    </div>
  );
}

/** Inline explanation of a limit, used when the session already showed its one interruptive upsell. */
export function PlanLimitNotice({ children }: { children: string }) {
  return (
    <p role="status" className="mt-3 flex flex-wrap items-center gap-x-2 gap-y-1 rounded-lg border border-primary/20 bg-primary-soft px-3 py-2.5 text-sm">
      <Lock className="size-4 shrink-0 text-primary-dark" aria-hidden />
      <span>{children}</span>
      <Link to="/assinar" className="font-bold text-primary-dark underline">
        Conhecer o Premium
      </Link>
    </p>
  );
}

/** How many complete comparisons the free plan has left today. */
export function DailyComparisonsMeter({ access }: { access: ComparisonAccess }) {
  if (access.premium || access.dailyComparisonsUsed === null || access.dailyComparisonsLimit === null) return null;
  const used = Math.min(access.dailyComparisonsUsed, access.dailyComparisonsLimit);
  const limit = access.dailyComparisonsLimit;

  return (
    <div className="flex flex-wrap items-center gap-x-3 gap-y-1 text-xs text-muted">
      <span className="font-semibold text-foreground">
        {used} de {limit} comparações completas hoje
      </span>
      <span className="flex gap-1" aria-hidden>
        {Array.from({ length: limit }, (_, index) => (
          <span key={index} className={cn("h-1.5 w-4 rounded-full", index < used ? "bg-primary" : "bg-border")} />
        ))}
      </span>
      <Link to="/assinar?origem=contador" className="inline-flex items-center gap-1 font-semibold text-primary-dark hover:underline">
        <Sparkles className="size-3.5" aria-hidden />
        Ilimitadas no Premium
      </Link>
    </div>
  );
}

/**
 * After the daily comparisons run out, the result shows only the cheapest store. The page always says so;
 * the upgrade modal opens by itself only if the session has not shown an interruptive upsell yet.
 */
export function DailyLimitNotice({ access }: { access: ComparisonAccess }) {
  const { showUpsell } = useUpsell();
  const reached = access.dailyLimitReached;

  useEffect(() => {
    if (reached) showUpsell("DAILY_COMPARISONS", {}, { interruptive: true });
  }, [reached, showUpsell]);

  if (!reached) return null;
  return (
    <p role="status" className="flex flex-wrap items-center gap-x-2 gap-y-1 border-b border-border bg-warning-soft px-4 py-3 text-sm sm:px-5">
      <CalendarRange className="size-4 shrink-0 text-warning" aria-hidden />
      <span>Você usou as {access.dailyComparisonsLimit} comparações completas de hoje. Para produtos novos, mostramos só o mercado mais barato até amanhã.</span>
      <Link to="/assinar?origem=limite-diario" className="font-bold text-primary-dark underline">Comparar sem limite</Link>
    </p>
  );
}
