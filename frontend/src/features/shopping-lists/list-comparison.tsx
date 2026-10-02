import { useQuery } from "@tanstack/react-query";
import { Lock } from "lucide-react";
import { useEffect, useRef, useState } from "react";
import { Pagination, StatusBadge } from "@/components/page/page-elements";
import { EmptyState, ErrorState, LoadingState } from "@/components/ui/feedback";
import { formatCurrency, formatDate } from "@/lib/brand";
import { shoppingListApi } from "@/services/gomo-api";
import { LockedStores } from "@/features/plan/plan-locks";
import { ShoppingRoute } from "@/features/plan/shopping-route";
import { SplitSavingsBanner } from "@/features/plan/split-savings-banner";
import { useUpsell } from "@/features/plan/upsell-context";
import { ComparisonItems } from "./comparison-items";
import type { ShoppingListItem } from "@/types/api";

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
      <div className="grid gap-5 lg:grid-cols-2">
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
                  Há itens cujo estoque não foi informado pela fonte.
                </p>
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
                    Maior cobertura parcial
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
                        Faltam {store.missingItems} itens. Este valor não é o
                        total da lista.
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
                  Ainda não há preços utilizáveis para os itens nesta cidade.
                </p>
              )}
            </>
          )}
        </section>
        <section className="surface p-5">
          <h3 className="font-bold">Menor preço por item entre mercados</h3>
          <div className="mt-2">
            <StatusBadge
              tone={combination.completeShoppingList ? "success" : "warning"}
            >
              {combination.completeShoppingList
                ? "Combinação completa"
                : "Combinação parcial"}
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
              className="mt-4 inline-flex min-h-10 items-center gap-2 rounded-full border border-primary/25 bg-primary-soft px-4 text-sm font-bold text-primary-dark hover:bg-[#ffe1de] focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-focus"
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
              Sem preço utilizável em nenhum mercado:{" "}
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
            Frete e deslocamento não estão incluídos. Apenas produtos com a
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
        <ShoppingRoute cityId={cityId} combination={combination} splitSavings={recommendation.data.splitSavings} />
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
