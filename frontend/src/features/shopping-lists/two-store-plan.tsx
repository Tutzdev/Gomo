import { Lock, ShoppingCart, Store } from "lucide-react";
import { Link } from "react-router-dom";
import { StatusBadge } from "@/components/page/page-elements";
import { useUpsell } from "@/features/plan/upsell-context";
import { formatCurrency } from "@/lib/brand";
import type { ShoppingCombination, ShoppingRecommendation } from "@/types/api";

/**
 * The split people actually do: at most two stops. Shown next to the single store and the split in every store,
 * so the person sees what the second stop is worth and how little the other stops add.
 */
export function TwoStorePlan({
  listId,
  pair,
  everyStore,
  savings,
}: {
  listId: string;
  pair: ShoppingCombination;
  everyStore: ShoppingCombination;
  /** Against the best single store; exists also when no store has the whole list. */
  savings: ShoppingRecommendation["twoStoreSavings"];
}) {
  const { showUpsell } = useUpsell();
  if (pair.pricedItems === 0 || pair.subtotalKnown === null) return null;

  if (pair.storeCount < 2) {
    return (
      <section className="surface p-5">
        <h3 className="font-bold">Em até 2 mercados</h3>
        <p className="mt-3 text-sm text-muted">
          {pair.stores.length
            ? `Dividir não compensa nesta lista: uma segunda parada economizaria menos de R$ 5. Comprar tudo no ${pair.stores[0].storeName} já é o melhor.`
            : "Dividir não compensa nesta lista: uma segunda parada economizaria menos de R$ 5."}
        </p>
      </section>
    );
  }

  // What the remaining stops of the full split add on top of the pair.
  const extraStops = everyStore.storeCount - pair.storeCount;
  const extraSavings =
    everyStore.subtotalKnown !== null && everyStore.pricedItems === pair.pricedItems
      ? pair.subtotalKnown - everyStore.subtotalKnown
      : null;

  return (
    <section className="surface border-primary/30 p-5 ring-1 ring-primary/15">
      <div className="flex flex-wrap items-center justify-between gap-2">
        <h3 className="font-bold">Em até 2 mercados</h3>
        <StatusBadge tone="success">Mais prático</StatusBadge>
      </div>
      <p className="mt-3 text-2xl font-extrabold tabular-nums">{formatCurrency(pair.subtotalKnown)}</p>
      <p className="mt-2 text-sm text-muted">
        {pair.pricedItems} de {pair.requestedItems} itens em 2 mercados.
        {!pair.completeShoppingList ? " Subtotal apenas dos itens encontrados." : ""}
      </p>
      {pair.savingsAgainstCompleteStore !== null && pair.savingsAgainstCompleteStore > 0 ? (
        <p className="mt-3 text-sm font-semibold">
          Economia de {formatCurrency(pair.savingsAgainstCompleteStore)} em relação ao mercado mais barato com a lista
          completa.
        </p>
      ) : savings && savings.savings > 0 ? (
        <p className="mt-3 text-sm font-semibold">
          Economia de {formatCurrency(savings.savings)} nos {savings.comparedItems} itens que o {savings.storeName} também
          tem.
        </p>
      ) : null}
      {extraStops > 0 && extraSavings !== null ? (
        <p className="mt-2 text-sm text-muted">
          {extraSavings > 0.005
            ? `Passar nos ${everyStore.storeCount} mercados economizaria só mais ${formatCurrency(extraSavings)}.`
            : `Passar nos ${everyStore.storeCount} mercados não economizaria nada a mais.`}
        </p>
      ) : null}

      {pair.locked ? (
        <button
          type="button"
          className="mt-4 inline-flex min-h-10 items-center gap-2 rounded-full border border-primary/25 bg-primary-soft px-4 text-sm font-bold text-primary-dark hover:bg-primary-soft-strong focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-focus"
          onClick={() =>
            showUpsell("SPLIT_PURCHASE", {
              savings: pair.savingsAgainstCompleteStore ?? savings?.savings ?? undefined,
              storeCount: pair.storeCount,
            })
          }
        >
          <Lock className="size-4" aria-hidden />
          Ver quais são os 2 mercados no Premium
        </button>
      ) : (
        <ul className="mt-4 space-y-2">
          {pair.stores.map((store) => (
            <li key={store.storeId} className="flex items-center gap-3 rounded-lg bg-surface-strong px-3 py-2.5">
              <Store className="size-4 shrink-0 text-primary" aria-hidden />
              <div className="min-w-0 flex-1">
                <p className="truncate font-semibold">{store.storeName}</p>
                <p className="text-xs text-muted">
                  {store.items.length} {store.items.length === 1 ? "item" : "itens"}
                </p>
              </div>
              <span className="shrink-0 text-sm font-bold tabular-nums">{formatCurrency(store.subtotal)}</span>
              <Link
                to={`/app/listas/${listId}/mercado/${store.storeId}`}
                className="grid size-9 shrink-0 place-items-center rounded-full text-primary-dark hover:bg-primary-soft focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-focus"
                aria-label={`Comprar no ${store.storeName}`}
                title="Comprar aqui"
              >
                <ShoppingCart className="size-4" aria-hidden />
              </Link>
            </li>
          ))}
        </ul>
      )}
    </section>
  );
}
