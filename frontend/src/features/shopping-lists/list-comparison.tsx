import { useQuery } from "@tanstack/react-query";
import { Lock, ShoppingCart } from "lucide-react";
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
import type { ShoppingListItem } from "@/types/api";

function listShareText(storeName: string, total: number, items: number, priciestTotal: number | null) {
  const savings = priciestTotal !== null && priciestTotal > total ? priciestTotal - total : 0;
  return savings > 0
    ? `Comparei minha lista de ${items} itens no Gomo: no ${storeName} sai ${formatCurrency(total)}, ${formatCurrency(savings)} a menos que no mercado mais caro.`
    : `Comparei minha lista de ${items} itens no Gomo: o menor total é no ${storeName}, ${formatCurrency(total)}.`;
}

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
  const { showUpsell } = useUpsell();
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
    if (
      comparison.isSuccess &&
      recommendation.isSuccess &&
      !scrolledToResults.current
    ) {
      results.current?.scrollIntoView({ block: "start" });
      scrolledToResults.current = true;
    }
  }, [comparison.isSuccess, recommendation.isSuccess]);
  if (comparison.isLoading || recommendation.isLoading)
    return <LoadingState label="Calculando comparação…" />;
  if (
    comparison.isError ||
    recommendation.isError ||
    !comparison.data ||
    !recommendation.data
  )
    return (
      <ErrorState
        message={(comparison.error ?? recommendation.error)?.message}
        retry={() =>
          void Promise.all([comparison.refetch(), recommendation.refetch()])
        }
      />
    );
  const {
    recommendation: winner,
    closestMatches,
    combination,
    twoStoreCombination,
  } = recommendation.data;
  // Stores with no current price for any item add nothing to a real comparison.
  const comparedStores = comparison.data.stores.content
    .filter((store) => store.pricedItems > 0)
    .sort(
      (a, b) =>
        b.pricedItems - a.pricedItems ||
        (a.subtotalKnown ?? Number.POSITIVE_INFINITY) -
          (b.subtotalKnown ?? Number.POSITIVE_INFINITY),
    );
  // The most expensive market that has the whole list too: the honest comparison for "how much did I save".
  const priciestCompleteTotal = comparison.data.stores.content.reduce<number | null>(
    (priciest, store) =>
      store.completeShoppingList && store.subtotalKnown !== null && store.subtotalKnown > (priciest ?? 0)
        ? store.subtotalKnown
        : priciest,
    null,
  );
  const pairShown = twoStoreCombination.pricedItems > 0 && twoStoreCombination.subtotalKnown !== null;
  const bestTotals = new Map(
    combination.stores.flatMap((store) =>
      store.items.flatMap((item) =>
        item.lineTotal === null
          ? []
          : [[item.productId, item.lineTotal] as const],
      ),
    ),
  );
  return (
    <div
      ref={results}
      className="scroll-mt-32 space-y-5"
      aria-live="polite"
      aria-busy={comparison.isFetching || recommendation.isFetching}
    >
      <p className="text-sm text-muted">
        {comparedStores.length + comparison.data.access.lockedStores} mercado(s) com preços atuais ·{" "}
        {formatDate(recommendation.data.comparedAt)}
      </p>
      <SplitSavingsBanner recommendation={recommendation.data} />
      <div className="grid gap-5 md:grid-cols-2 xl:grid-cols-3">
        <section className="surface p-5">
          <h3 className="font-bold">Comprar em um mercado</h3>
          {winner ? (
            <>
              <StatusBadge tone="success">Lista completa</StatusBadge>
              <p className="mt-3 font-semibold">{winner.storeName}</p>
              <p className="mt-2 text-2xl font-extrabold">
                {winner.total === null
                  ? "Sem total"
                  : formatCurrency(winner.total)}
              </p>
              <p className="mt-2 text-sm text-muted">
                Menor total entre os mercados com todos os{" "}
                {winner.requestedItems} itens.
              </p>
              {winner.stockUncertain ? (
                <p className="mt-2 text-sm text-warning">
                  O mercado não informa se alguns itens estão disponíveis. Vale conferir.
                </p>
              ) : null}
              {winner.total !== null ? (
                <div className="mt-4 flex flex-wrap gap-2">
                  <NativeButton to={`/app/listas/${id}/mercado/${winner.storeId}`} size="sm">
                    <ShoppingCart className="size-4" aria-hidden />
                    Ir às compras aqui
                  </NativeButton>
                  <ShareButton
                    label="Compartilhar economia"
                    text={listShareText(winner.storeName, winner.total, winner.requestedItems, priciestCompleteTotal)}
                    url={siteUrl()}
                  />
                </div>
              ) : null}
            </>
          ) : (
            <>
              <p className="mt-2 text-sm text-muted">
                Nenhum mercado tem preço atual para a lista inteira.
              </p>
              {closestMatches.length ? (
                <>
                  <h4 className="mt-4 font-semibold">
                    Mercados com mais itens da lista
                  </h4>
                  {closestMatches.map((store) => (
                    <div
                      key={store.storeId}
                      className="mt-3 rounded-lg bg-surface-strong p-3"
                    >
                      <p className="font-semibold">{store.storeName}</p>
                      <p className="mt-1 text-sm">
                        {store.pricedItems} de {store.requestedItems} itens ·
                        subtotal{" "}
                        {store.total === null
                          ? "Sem total"
                          : formatCurrency(store.total)}
                      </p>
                      <p className="mt-1 text-xs text-muted">
                        Faltam {store.missingItems} itens, então este valor não
                        é o total da lista.
                      </p>
                    </div>
                  ))}
                  <p className="mt-3 text-xs text-muted">
                    Os mercados podem cobrir produtos diferentes; confira os
                    itens abaixo.
                  </p>
                </>
              ) : (
                <p className="mt-3 text-sm">
                  Ainda não temos preço atual destes itens nesta cidade.
                </p>
              )}
            </>
          )}
        </section>
        <TwoStorePlan listId={id} pair={twoStoreCombination} everyStore={combination} savings={recommendation.data.twoStoreSavings} />
        {/* With three cards, the third one fills the whole second row between md and xl instead of half of it. */}
        <section className={cn("surface p-5", pairShown && "md:col-span-2 xl:col-span-1")}>
          <h3 className="font-bold">Menor preço por item entre mercados</h3>
          <div className="mt-2">
            <StatusBadge
              tone={combination.completeShoppingList ? "success" : "warning"}
            >
              {combination.completeShoppingList
                ? "Todos os itens"
                : "Faltam itens"}
            </StatusBadge>
          </div>
          <p className="mt-3 text-2xl font-extrabold">
            {combination.subtotalKnown === null
              ? "Sem preços atuais"
              : formatCurrency(combination.subtotalKnown)}
          </p>
          <p className="mt-2 text-sm text-muted">
            {combination.pricedItems} de {combination.requestedItems} itens em{" "}
            {combination.storeCount} mercado(s).{" "}
            {!combination.completeShoppingList
              ? "Subtotal apenas dos itens encontrados."
              : ""}
          </p>
          {combination.savingsAgainstCompleteStore !== null ? (
            <p className="mt-3 text-sm font-semibold">
              Economia de{" "}
              {formatCurrency(combination.savingsAgainstCompleteStore)} em
              relação ao mercado mais barato com a lista completa.
            </p>
          ) : null}
          {combination.locked ? (
            <button
              type="button"
              className="mt-4 inline-flex min-h-10 items-center gap-2 rounded-full border border-primary/25 bg-primary-soft px-4 text-sm font-bold text-primary-dark hover:bg-primary-soft-strong focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-focus"
              onClick={() =>
                showUpsell("SPLIT_PURCHASE", {
                  savings: recommendation.data.splitSavings?.savings,
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
              Sem preço atual em nenhum mercado:{" "}
              {items
                .filter((item) =>
                  combination.missingProductIds.includes(item.productId),
                )
                .map((item) => item.productName)
                .join("; ")}
              .
            </p>
          ) : null}
          <p className="mt-3 text-xs text-muted">
            Frete não está incluído; o custo do deslocamento aparece na rota. Apenas produtos com a
            mesma identidade são comparados; não há substituição automática de
            marcas ou embalagens.
          </p>
        </section>
      </div>
      {combination.stores.length ? (
        <section className="surface overflow-hidden">
          <h3 className="p-5 font-bold">Onde comprar cada item</h3>
          {combination.stores.map((store) => (
            <details key={store.storeId} className="border-t border-border">
              <summary className="cursor-pointer p-4 font-semibold">
                {store.storeName} · {store.items.length} itens ·{" "}
                {formatCurrency(store.subtotal)}
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
              savings: recommendation.data.twoStoreSavings?.savings ?? twoStoreCombination.savingsAgainstCompleteStore,
            },
            {
              id: "every",
              label: `Em todos (${combination.storeCount})`,
              combination,
              savings: recommendation.data.splitSavings?.savings ?? null,
            },
          ]}
          reference={recommendation.data.splitSavings}
        />
      ) : null}
      <h3 className="text-lg font-bold">Sua lista em cada mercado</h3>
      <p className="text-sm text-muted">
        A economia por item considera a quantidade da lista e o menor preço
        encontrado entre os mercados.
      </p>
      {!comparedStores.length ? (
        <EmptyState
          title="Nenhum mercado com preço atual para estes itens"
          description="Os preços são atualizados várias vezes ao dia. Volte em alguns minutos."
        />
      ) : (
        comparedStores.map((store) => (
          <section key={store.storeId} className="surface overflow-hidden">
            <div className="flex flex-wrap items-start justify-between gap-4 border-b border-border p-5">
              <div>
                <h4 className="font-bold">{store.storeName}</h4>
                {store.priceSourceNote ? (
                  <p className="mt-2 text-sm text-muted">
                    {store.priceSourceNote}
                  </p>
                ) : null}
                <p className="mt-2 text-sm text-muted">
                  {store.completeShoppingList
                    ? `Tem os ${store.requestedItems} itens da lista`
                    : `Tem ${store.pricedItems} de ${store.requestedItems} itens`}
                </p>
                {!store.completeShoppingList ? (
                  <p className="mt-1 text-xs text-muted">
                    Sem preço atual aqui:{" "}
                    {store.items
                      .filter((item) => item.price.unitPrice === null)
                      .map((item) => item.productName)
                      .join(", ")}
                  </p>
                ) : null}
              </div>
              <div className="text-right">
                <p className="text-xs text-muted">
                  {store.completeShoppingList
                    ? "Total da lista"
                    : "Subtotal dos itens encontrados"}
                </p>
                <p className="mt-1 text-xl font-extrabold">
                  {store.subtotalKnown === null
                    ? "Sem preços atuais"
                    : formatCurrency(store.subtotalKnown)}
                </p>
                <NativeButton to={`/app/listas/${id}/mercado/${store.storeId}`} size="sm" variant="secondary" className="mt-2">
                  <ShoppingCart className="size-4" aria-hidden />
                  Comprar aqui
                </NativeButton>
              </div>
            </div>
            <ComparisonItems items={store.items} bestTotals={bestTotals} />
          </section>
        ))
      )}
      {comparison.data.access.lockedStores > 0 ? (
        <div className="surface overflow-hidden">
          <LockedStores count={comparison.data.access.lockedStores} className="border-t-0" />
        </div>
      ) : null}
      {comparison.data.stores.totalPages > 1 ? (
        <Pagination
          page={page}
          totalPages={comparison.data.stores.totalPages}
          onChange={setPage}
        />
      ) : null}
    </div>
  );
}
