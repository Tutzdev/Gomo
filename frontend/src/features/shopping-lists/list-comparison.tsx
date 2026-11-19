import { useQuery } from "@tanstack/react-query";
import { ChevronDown, CircleSlash, Lock, ShoppingCart, Trophy } from "lucide-react";
import { useEffect, useRef, useState } from "react";
import { Pagination, StatusBadge } from "@/components/page/page-elements";
import { EmptyState, ErrorState, LoadingState } from "@/components/ui/feedback";
import { NativeButton } from "@/components/ui/native-button";
import { formatCurrency, formatDate } from "@/lib/brand";
import { cn } from "@/lib/cn";
import { shoppingListApi } from "@/services/gomo-api";
import { LockedStores } from "@/features/plan/plan-locks";
import { ShoppingRoute } from "@/features/plan/shopping-route";
import { SplitSavingsBanner } from "@/features/plan/split-savings-banner";
import { useUpsell } from "@/features/plan/upsell-context";
import { ShareButton } from "@/components/ui/share-button";
import { siteUrl } from "@/lib/site-url";
import { ComparisonItems } from "./comparison-items";
import { TwoStorePlan } from "./two-store-plan";
import type { ShoppingListItem, ShoppingRecommendation, ShoppingStoreComparison } from "@/types/api";

function listShareText(storeName: string, total: number, items: number, priciestTotal: number | null) {
  const savings = priciestTotal !== null && priciestTotal > total ? priciestTotal - total : 0;
  return savings > 0
    ? `Comparei minha lista de ${items} itens no Gomo: no ${storeName} sai ${formatCurrency(total)}, ${formatCurrency(savings)} a menos que no mercado mais caro.`
    : `Comparei minha lista de ${items} itens no Gomo: o menor total é no ${storeName}, ${formatCurrency(total)}.`;
}

function bestFirst(a: ShoppingStoreComparison, b: ShoppingStoreComparison) {
  return (
    Number(b.completeShoppingList) - Number(a.completeShoppingList) ||
    b.pricedItems - a.pricedItems ||
    (a.subtotalKnown ?? Number.POSITIVE_INFINITY) - (b.subtotalKnown ?? Number.POSITIVE_INFINITY) ||
    a.storeName.localeCompare(b.storeName, "pt-BR")
  );
}

function missingNames(store: ShoppingStoreComparison) {
  return store.items
    .filter((item) => item.price.unitPrice === null)
    .map((item) => item.productName)
    .join(", ");
}

/**
 * The list in every market, best first: the total, how many of the items each one has and which are missing.
 * Splitting the purchase between markets stays one tap away, for whoever wants to save more.
 */
export function ListComparison({
  id,
  cityId,
  storeIds,
  items,
}: {
  id: string;
  cityId: string;
  storeIds: string[];
  items: ShoppingListItem[];
}) {
  const [page, setPage] = useState(0);
  const results = useRef<HTMLDivElement>(null);
  const scrolledToResults = useRef(false);
  const comparison = useQuery({
    queryKey: ["shopping-list-comparison", id, cityId, storeIds, page],
    queryFn: () => shoppingListApi.compare(id, cityId, page, 50, storeIds),
  });
  const recommendation = useQuery({
    queryKey: ["shopping-list-recommendation", id, cityId, storeIds],
    queryFn: () => shoppingListApi.recommendation(id, cityId, storeIds),
  });
  useEffect(() => {
    if (comparison.isSuccess && recommendation.isSuccess && !scrolledToResults.current) {
      results.current?.scrollIntoView({ block: "start" });
      scrolledToResults.current = true;
    }
  }, [comparison.isSuccess, recommendation.isSuccess]);
  if (comparison.isLoading || recommendation.isLoading) return <LoadingState label="Calculando comparação…" />;
  if (comparison.isError || recommendation.isError || !comparison.data || !recommendation.data)
    return (
      <ErrorState
        message={(comparison.error ?? recommendation.error)?.message}
        retry={() => void Promise.all([comparison.refetch(), recommendation.refetch()])}
      />
    );

  const stores = comparison.data.stores.content;
  const ranked = stores.filter((store) => store.pricedItems > 0).sort(bestFirst);
  const withoutPrices = stores.filter((store) => store.pricedItems === 0);
  const priciestCompleteTotal = stores.reduce<number | null>(
    (priciest, store) =>
      store.completeShoppingList && store.subtotalKnown !== null && store.subtotalKnown > (priciest ?? 0)
        ? store.subtotalKnown
        : priciest,
    null,
  );
  const bestTotals = new Map(
    recommendation.data.combination.stores.flatMap((store) =>
      store.items.flatMap((item) => (item.lineTotal === null ? [] : [[item.productId, item.lineTotal] as const])),
    ),
  );
  const anyBrandItems = items.filter((item) => item.basicItem).length;

  return (
    <div
      ref={results}
      className="scroll-mt-32 space-y-5"
      aria-live="polite"
      aria-busy={comparison.isFetching || recommendation.isFetching}
    >
      <p className="text-sm text-muted">
        {ranked.length + comparison.data.access.lockedStores} de{" "}
        {stores.length + comparison.data.access.lockedStores} mercado(s) com preço de algum item ·{" "}
        {formatDate(recommendation.data.comparedAt)}
        {anyBrandItems
          ? ` · ${anyBrandItems} ${anyBrandItems === 1 ? "item" : "itens"} de qualquer marca: em cada mercado entra o mais barato por kg.`
          : ""}
      </p>
      {!ranked.length ? (
        <EmptyState
          title="Nenhum mercado com preço atual para estes itens"
          description="Os preços são atualizados várias vezes ao dia. Para o básico, troque o produto por “qualquer marca”: aparece em muito mais mercados."
        />
      ) : (
        <ol className="space-y-3" aria-label="Mercados do mais vantajoso ao menos vantajoso">
          {ranked.map((store, index) => (
            <StoreCard
              key={store.storeId}
              listId={id}
              store={store}
              position={index}
              bestTotals={bestTotals}
              shareText={
                index === 0 && store.completeShoppingList && store.subtotalKnown !== null
                  ? listShareText(store.storeName, store.subtotalKnown, store.requestedItems, priciestCompleteTotal)
                  : null
              }
            />
          ))}
        </ol>
      )}
      {comparison.data.access.lockedStores > 0 ? (
        <div className="surface overflow-hidden">
          <LockedStores count={comparison.data.access.lockedStores} className="border-t-0" />
        </div>
      ) : null}
      {withoutPrices.length ? (
        <section className="surface p-4 sm:p-5">
          <h3 className="flex items-center gap-2 text-sm font-bold">
            <CircleSlash className="size-4 text-muted" aria-hidden />
            Sem preço online de nenhum item da lista
          </h3>
          <p className="mt-1 text-sm">{withoutPrices.map((store) => store.storeName).join(", ")}.</p>
          <p className="mt-1 text-xs text-muted">
            O site desses mercados não vende estes produtos agora, ou os preços ainda não foram lidos hoje.
          </p>
        </section>
      ) : null}
      {comparison.data.stores.totalPages > 1 ? (
        <Pagination page={page} totalPages={comparison.data.stores.totalPages} onChange={setPage} />
      ) : null}
      <SplitPurchase listId={id} cityId={cityId} items={items} recommendation={recommendation.data} />
    </div>
  );
}

function StoreCard({
  listId,
  store,
  position,
  bestTotals,
  shareText,
}: {
  listId: string;
  store: ShoppingStoreComparison;
  position: number;
  bestTotals: Map<string, number>;
  shareText: string | null;
}) {
  const [open, setOpen] = useState(false);
  const first = position === 0;
  const missing = missingNames(store);
  return (
    <li className={cn("surface overflow-hidden", first && "ring-2 ring-success")}>
      <div className="flex flex-wrap items-start gap-x-4 gap-y-3 p-4 sm:p-5">
        <span
          className={cn(
            "grid size-9 shrink-0 place-items-center rounded-full text-sm font-bold tabular-nums",
            first ? "bg-success text-white" : "bg-surface-strong text-muted",
          )}
          aria-hidden
        >
          {first ? <Trophy className="size-4" /> : position + 1}
        </span>
        <div className="min-w-0 flex-1">
          <h4 className="font-bold">
            {store.storeName}
            {first ? <span className="sr-only"> (melhor opção)</span> : null}
          </h4>
          <div className="mt-1 flex flex-wrap items-center gap-2">
            {store.completeShoppingList ? (
              <StatusBadge tone="success">Tem a lista toda</StatusBadge>
            ) : (
              <StatusBadge tone="warning">
                Tem {store.pricedItems} de {store.requestedItems} itens
              </StatusBadge>
            )}
            {first ? <span className="text-xs font-semibold text-success">Melhor opção</span> : null}
          </div>
          {missing ? <p className="mt-2 text-xs text-muted">Sem preço aqui: {missing}.</p> : null}
          {store.priceSourceNote ? <p className="mt-1 text-xs text-muted">{store.priceSourceNote}</p> : null}
        </div>
        <div className="text-right">
          <p className="text-xs text-muted">{store.completeShoppingList ? "Total" : "Itens encontrados"}</p>
          <p className={cn("text-2xl font-extrabold tabular-nums", first && "text-success")}>
            {store.subtotalKnown === null ? "—" : formatCurrency(store.subtotalKnown)}
          </p>
        </div>
      </div>
      <div className="flex flex-wrap gap-2 border-t border-border px-4 py-3 sm:px-5">
        <NativeButton
          to={`/app/listas/${listId}/mercado/${store.storeId}`}
          size="sm"
          variant={first ? "primary" : "secondary"}
        >
          <ShoppingCart className="size-4" aria-hidden />
          Ir às compras aqui
        </NativeButton>
        <NativeButton size="sm" variant="ghost" aria-expanded={open} onClick={() => setOpen(!open)}>
          <ChevronDown className={cn("size-4 transition-transform motion-reduce:transition-none", open && "rotate-180")} aria-hidden />
          {open ? "Esconder itens" : "Ver preço de cada item"}
        </NativeButton>
        {shareText ? <ShareButton label="Compartilhar economia" text={shareText} url={siteUrl()} /> : null}
      </div>
      {open ? <ComparisonItems items={store.items} bestTotals={bestTotals} /> : null}
    </li>
  );
}

function SplitPurchase({
  listId,
  cityId,
  items,
  recommendation,
}: {
  listId: string;
  cityId: string;
  items: ShoppingListItem[];
  recommendation: ShoppingRecommendation;
}) {
  const { showUpsell } = useUpsell();
  const { combination, twoStoreCombination } = recommendation;
  const pairShown = twoStoreCombination.pricedItems > 0 && twoStoreCombination.subtotalKnown !== null;
  if (!combination.pricedItems) return null;
  return (
    <details className="surface group overflow-hidden">
      <summary className="flex cursor-pointer list-none items-center justify-between gap-3 p-4 sm:p-5">
        <span>
          <span className="block font-bold">Economizar dividindo a compra</span>
          <span className="mt-0.5 block text-sm text-muted">
            Cada item no mercado mais barato
            {combination.subtotalKnown !== null ? `: ${formatCurrency(combination.subtotalKnown)}` : ""}
            {combination.storeCount ? ` em ${combination.storeCount} mercado(s)` : ""}.
          </span>
        </span>
        <ChevronDown
          className="size-5 shrink-0 transition-transform group-open:rotate-180 motion-reduce:transition-none"
          aria-hidden
        />
      </summary>
      <div className="space-y-5 border-t border-border p-4 sm:p-5">
        <SplitSavingsBanner recommendation={recommendation} />
        <div className="grid gap-5 md:grid-cols-2">
          <TwoStorePlan
            listId={listId}
            pair={twoStoreCombination}
            everyStore={combination}
            savings={recommendation.twoStoreSavings}
          />
          <section className={cn("rounded-lg border border-border p-5", !pairShown && "md:col-span-2")}>
            <h3 className="font-bold">Menor preço de cada item</h3>
            <p className="mt-2 text-2xl font-extrabold">
              {combination.subtotalKnown === null ? "Sem preços atuais" : formatCurrency(combination.subtotalKnown)}
            </p>
            <p className="mt-2 text-sm text-muted">
              {combination.pricedItems} de {combination.requestedItems} itens em {combination.storeCount} mercado(s).
              {!combination.completeShoppingList ? " Soma só dos itens encontrados." : ""}
            </p>
            {combination.savingsAgainstCompleteStore !== null ? (
              <p className="mt-3 text-sm font-semibold">
                {formatCurrency(combination.savingsAgainstCompleteStore)} a menos que o mercado mais barato com a
                lista toda.
              </p>
            ) : null}
            {combination.locked ? (
              <button
                type="button"
                className="mt-4 inline-flex min-h-10 items-center gap-2 rounded-full border border-primary/25 bg-primary-soft px-4 text-sm font-bold text-primary-dark hover:bg-primary-soft-strong focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-focus"
                onClick={() =>
                  showUpsell("SPLIT_PURCHASE", {
                    savings: recommendation.splitSavings?.savings,
                    storeCount: combination.storeCount,
                  })
                }
              >
                <Lock className="size-4" aria-hidden />
                Ver onde comprar cada item no Premium
              </button>
            ) : null}
            {combination.missingProductIds.length ? (
              <p className="mt-3 text-sm">
                Sem preço em nenhum mercado:{" "}
                {items
                  .filter((item) => combination.missingProductIds.includes(item.productId))
                  .map((item) => item.productName)
                  .join("; ")}
                .
              </p>
            ) : null}
            <p className="mt-3 text-xs text-muted">Frete não incluído; o deslocamento aparece na rota.</p>
          </section>
        </div>
        {combination.stores.length ? (
          <section className="overflow-hidden rounded-lg border border-border">
            <h3 className="p-4 font-bold">Onde comprar cada item</h3>
            {combination.stores.map((store) => (
              <details key={store.storeId} className="border-t border-border">
                <summary className="cursor-pointer p-4 font-semibold">
                  {store.storeName} · {store.items.length} itens · {formatCurrency(store.subtotal)}
                </summary>
                <ComparisonItems items={store.items} />
              </details>
            ))}
          </section>
        ) : null}
        {!combination.locked ? (
          <ShoppingRoute
            cityId={cityId}
            plans={[
              {
                id: "pair",
                label: "Em 2 mercados",
                combination: twoStoreCombination,
                savings: recommendation.twoStoreSavings?.savings ?? twoStoreCombination.savingsAgainstCompleteStore,
              },
              {
                id: "every",
                label: `Em todos (${combination.storeCount})`,
                combination,
                savings: recommendation.splitSavings?.savings ?? null,
              },
            ]}
            reference={recommendation.splitSavings}
          />
        ) : null}
      </div>
    </details>
  );
}
