import { useMutation, useQuery } from "@tanstack/react-query";
import { Lock, PackagePlus, ThumbsUp } from "lucide-react";
import { NativeButton } from "@/components/ui/native-button";
import { useUpsell } from "@/features/plan/upsell-context";
import { formatCurrency } from "@/lib/brand";
import { shoppingListApi } from "@/services/gomo-api";
import type { ListGoodPrices } from "@/types/api";

type GoodPrice = ListGoodPrices["items"][number];

/**
 * "Hora de estocar": the pantry items of the list at the lowest price of the last months, worth buying for the
 * next weeks too, and the other items under the usual price today. Hidden when nothing on the list is a good buy.
 */
export function GoodPricesCard({ listId, cityId, onChanged }: { listId: string; cityId: string; onChanged: () => Promise<void> }) {
  const { showUpsell } = useUpsell();
  const goodPrices = useQuery({
    queryKey: ["shopping-list-good-prices", listId, cityId],
    queryFn: () => shoppingListApi.goodPrices(listId, cityId),
    enabled: Boolean(cityId),
  });
  if (!goodPrices.data) return null;
  const { locked, stockUpItems, belowAverageItems, items } = goodPrices.data;
  if (stockUpItems + belowAverageItems === 0) return null;

  if (locked) {
    return (
      <section className="surface mb-6 flex flex-wrap items-center justify-between gap-4 border-success/25 bg-success-soft p-5">
        <div className="flex min-w-0 gap-3">
          <PackagePlus className="mt-0.5 size-5 shrink-0 text-success" aria-hidden />
          <p className="text-sm">
            <strong>{summary(stockUpItems, belowAverageItems)}</strong> O Premium mostra quais e onde.
          </p>
        </div>
        <NativeButton variant="secondary" size="sm" onClick={() => showUpsell("STOCK_UP")}>
          <Lock className="size-4" aria-hidden />
          Ver quais no Premium
        </NativeButton>
      </section>
    );
  }

  const stockUp = items.filter((item) => item.stockUp);
  const belowAverage = items.filter((item) => !item.stockUp);
  return (
    <section className="surface mb-6 overflow-hidden border-success/25" aria-labelledby="good-prices-title">
      <h2 id="good-prices-title" className="flex items-center gap-2 bg-success-soft px-5 py-3 font-bold">
        <PackagePlus className="size-5 text-success" aria-hidden />
        {summary(stockUpItems, belowAverageItems)}
      </h2>
      {stockUp.length ? (
        <div className="p-5">
          <h3 className="text-sm font-bold uppercase tracking-wide text-muted">Hora de estocar</h3>
          <p className="mt-1 text-sm text-muted">Itens de despensa no menor preço do período: dá para levar para as próximas semanas.</p>
          <ul className="mt-3 divide-y divide-border">
            {stockUp.map((item) => <StockUpRow key={item.itemId} listId={listId} item={item} onChanged={onChanged} />)}
          </ul>
        </div>
      ) : null}
      {belowAverage.length ? (
        <div className="border-t border-border p-5">
          <h3 className="flex items-center gap-1.5 text-sm font-bold uppercase tracking-wide text-muted">
            <ThumbsUp className="size-4" aria-hidden />
            Abaixo do preço de sempre hoje
          </h3>
          <ul className="mt-3 divide-y divide-border">
            {belowAverage.map((item) => (
              <li key={item.itemId} className="flex flex-wrap items-baseline justify-between gap-2 py-2.5 text-sm">
                <span className="min-w-0 font-semibold">{item.productName}</span>
                <span className="text-right">
                  <strong className="tabular-nums">{formatCurrency(item.currentPrice)}</strong> no {item.storeName}
                  <span className="block text-xs text-muted">{Math.abs(item.differencePercent)}% abaixo do preço de sempre ({formatCurrency(item.usualPrice)})</span>
                </span>
              </li>
            ))}
          </ul>
        </div>
      ) : null}
    </section>
  );
}

function StockUpRow({ listId, item, onChanged }: { listId: string; item: GoodPrice; onChanged: () => Promise<void> }) {
  const double = useMutation({
    mutationFn: () => shoppingListApi.updateItem(listId, item.itemId, item.quantity * 2),
    onSuccess: onChanged,
  });
  return (
    <li className="flex flex-wrap items-center justify-between gap-3 py-3">
      <div className="min-w-0">
        <p className="font-semibold">{item.productName}</p>
        <p className="text-sm text-muted">
          <strong className="text-foreground tabular-nums">{formatCurrency(item.currentPrice)}</strong> no {item.storeName} · menor preço dos últimos {item.periodDays} dias · {Math.abs(item.differencePercent)}% abaixo do preço de sempre
        </p>
      </div>
      <NativeButton size="sm" variant="secondary" loading={double.isPending} onClick={() => double.mutate()}>
        Levar o dobro ({(item.quantity * 2).toLocaleString("pt-BR")})
      </NativeButton>
    </li>
  );
}

function summary(stockUp: number, belowAverage: number) {
  const parts = [
    stockUp ? (stockUp === 1 ? "1 item da sua lista está no menor preço do período" : `${stockUp} itens da sua lista estão no menor preço do período`) : null,
    belowAverage ? (belowAverage === 1 ? "1 está abaixo do preço de sempre" : `${belowAverage} estão abaixo do preço de sempre`) : null,
  ].filter(Boolean);
  if (!stockUp && belowAverage) {
    return belowAverage === 1 ? "1 item da sua lista está abaixo do preço de sempre hoje." : `${belowAverage} itens da sua lista estão abaixo do preço de sempre hoje.`;
  }
  return `${parts.join(" e ")}.`;
}
