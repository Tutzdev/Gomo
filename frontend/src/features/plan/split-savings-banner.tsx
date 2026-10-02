import { Lock, Route } from "lucide-react";
import { formatCurrency } from "@/lib/brand";
import type { ShoppingRecommendation } from "@/types/api";
import { useUpsell } from "./upsell-context";

/**
 * Shown after a successful list comparison on the free plan: the real amount the split purchase would save.
 * It sits in the page and never interrupts; the modal opens only when the person asks to see the split.
 */
export function SplitSavingsBanner({ recommendation }: { recommendation: ShoppingRecommendation }) {
  const { showUpsell } = useUpsell();
  const { splitSavings, combination } = recommendation;
  if (!combination.locked || !splitSavings || splitSavings.savings <= 0 || combination.storeCount < 2) return null;

  return (
    <section className="split-savings" aria-labelledby="split-savings-title">
      <span className="split-savings__icon" aria-hidden>
        <Route className="size-5" />
      </span>
      <div className="min-w-0 flex-1">
        <h3 id="split-savings-title" className="font-bold">
          Você economizaria <span className="tabular-nums">{formatCurrency(splitSavings.savings)}</span> comprando cada item onde está mais barato
        </h3>
        <p className="mt-1 text-sm text-[#4d3f3e]">
          Comparado a fazer tudo no {splitSavings.storeName}, nos {splitSavings.comparedItems} itens que ele tem. A compra fica dividida entre {combination.storeCount} mercados.
        </p>
      </div>
      <button
        type="button"
        className="split-savings__button"
        onClick={() => showUpsell("SPLIT_PURCHASE", { savings: splitSavings.savings, storeCount: combination.storeCount })}
      >
        <Lock className="size-4" aria-hidden />
        Ver onde comprar cada item
      </button>
    </section>
  );
}
