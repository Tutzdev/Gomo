import { useQuery } from "@tanstack/react-query";
import { ExternalLink, Store, Trophy } from "lucide-react";
import { EmptyState, ErrorState, LoadingState } from "@/components/ui/feedback";
import { StatusBadge } from "@/components/page/page-elements";
import { formatCurrency } from "@/lib/brand";
import { cn } from "@/lib/cn";
import { DailyComparisonsMeter, DailyLimitNotice, LockedStores } from "@/features/plan/plan-locks";
import { PriceHistoryPanel } from "@/features/plan/price-history-panel";
import { catalogApi } from "@/services/gomo-api";
import type { CatalogItemDetail } from "@/types/api";
import { AddToList } from "./add-to-list";
import { CatalogItemThumbnail } from "./catalog-item-picker";

/**
 * One product, every market that sells it at a real, current price, cheapest first.
 * Markets without a current price are not listed: they would not be a comparison.
 */
export function CatalogItemComparison({
  itemId,
  cityId,
  storeIds,
}: {
  itemId: string;
  cityId: string;
  storeIds?: string[];
}) {
  const detail = useQuery({
    queryKey: ["catalog-item", itemId, cityId, storeIds],
    queryFn: () => catalogApi.catalogItem(itemId, cityId, storeIds),
    enabled: Boolean(itemId && cityId),
  });
  if (!cityId) return <EmptyState title="Escolha a cidade" description="Os preços são comparados entre os mercados da cidade." />;
  if (detail.isPending) return <LoadingState label="Comparando mercados…" />;
  if (detail.isError) return <ErrorState message={detail.error.message} retry={() => void detail.refetch()} />;
  return (
    <>
      <ComparisonResult detail={detail.data} />
      {detail.data.offers.length ? <PriceHistoryPanel itemId={itemId} cityId={cityId} storeIds={storeIds} /> : null}
    </>
  );
}

function ComparisonResult({ detail }: { detail: CatalogItemDetail }) {
  const { item, offers, storesWithoutPrice, access } = detail;
  // Stores hidden by the plan still have a current price, so the headline counts them.
  const pricedStores = offers.length + access.lockedStores;
  const cheapest = offers[0]?.price.unitPrice ?? null;
  const priciest = offers.at(-1)?.price.unitPrice ?? null;

  return (
    <section className="surface overflow-hidden" aria-labelledby={`compare-${item.id}`}>
      <header className="flex flex-wrap items-center gap-4 border-b border-border p-4 sm:p-5">
        <CatalogItemThumbnail imageUrl={item.imageUrl} />
        <div className="min-w-0 flex-1">
          <h2 id={`compare-${item.id}`} className="text-lg font-extrabold leading-tight">
            {item.name}
          </h2>
          <p className="mt-1 text-sm text-muted">
            {pricedStores > 1
              ? `Preço atual em ${pricedStores} mercados`
              : pricedStores === 1
                ? "Preço atual em 1 mercado"
                : "Sem preço atual nos mercados"}
          </p>
          <div className="mt-2">
            <DailyComparisonsMeter access={access} />
          </div>
        </div>
        <AddToList catalogItem={item} />
      </header>

      <DailyLimitNotice access={access} />
      {!offers.length ? (
        <div className="p-5">
          <EmptyState
            title="Nenhum mercado com preço atual agora"
            description="Os preços são atualizados várias vezes ao dia. Volte em alguns minutos."
          />
        </div>
      ) : (
        <>
          {cheapest !== null && priciest !== null && priciest > cheapest ? (
            <p className="border-b border-border bg-success-soft px-4 py-3 text-sm sm:px-5">
              Comprando no mais barato você economiza até{" "}
              <strong className="tabular-nums">{formatCurrency(priciest - cheapest)}</strong> por unidade
              {access.lockedStores > 0 ? " entre os mercados mostrados." : "."}
            </p>
          ) : null}
          <ol className="divide-y divide-border">
            {offers.map((offer, index) => {
              const price = offer.price.unitPrice!;
              const difference = cheapest === null ? 0 : price - cheapest;
              const observation = offer.price.observation;
              return (
                <li
                  key={offer.storeId}
                  className={cn("flex flex-wrap items-center gap-x-4 gap-y-2 px-4 py-4 sm:px-5", index === 0 && "bg-primary-soft/40")}
                >
                  <span
                    className={cn(
                      "grid size-8 shrink-0 place-items-center rounded-full text-sm font-bold tabular-nums",
                      index === 0 ? "bg-success text-white" : "bg-surface-strong text-muted",
                    )}
                    aria-hidden
                  >
                    {index === 0 ? <Trophy className="size-4" /> : index + 1}
                  </span>
                  <div className="min-w-0 flex-1">
                    <p className="flex items-center gap-1.5 font-semibold">
                      <Store className="size-4 shrink-0 text-muted" aria-hidden />
                      {offer.storeName}
                      {index === 0 ? <span className="sr-only">(mais barato)</span> : null}
                    </p>
                    {offer.storeProductName ? (
                      <p className="mt-0.5 truncate text-xs text-muted" title={offer.storeProductName}>
                        No mercado: {offer.storeProductName}
                      </p>
                    ) : null}
                    <div className="mt-1 flex flex-wrap items-center gap-2 text-xs text-muted">
                      {offer.price.promotionApplied ? <StatusBadge tone="success">Promoção</StatusBadge> : null}
                      {observation ? <span>Atualizado {relativeTime(observation.collectedAt)}</span> : null}
                      {offer.originUrl ? (
                        <a
                          className="inline-flex items-center gap-1 font-semibold text-primary underline"
                          href={offer.originUrl}
                          target="_blank"
                          rel="noreferrer"
                        >
                          Ver no site
                          <ExternalLink className="size-3" aria-hidden />
                        </a>
                      ) : null}
                    </div>
                  </div>
                  <div className="text-right">
                    <p className={cn("text-xl font-extrabold tabular-nums", index === 0 && "text-success")}>
                      {formatCurrency(price)}
                    </p>
                    {offer.price.promotionApplied && observation ? (
                      <p className="text-xs text-muted line-through tabular-nums">{formatCurrency(observation.regularPrice)}</p>
                    ) : index === 0 ? (
                      <p className="text-xs font-semibold text-success">Mais barato</p>
                    ) : (
                      <p className="text-xs text-muted tabular-nums">+{formatCurrency(difference)}</p>
                    )}
                  </div>
                </li>
              );
            })}
          </ol>
          <LockedStores count={access.lockedStores} />
        </>
      )}
      {storesWithoutPrice > 0 && offers.length ? (
        <p className="border-t border-border px-4 py-3 text-xs text-muted sm:px-5">
          {storesWithoutPrice === 1
            ? "1 mercado não tem preço atual deste produto e ficou fora da comparação."
            : `${storesWithoutPrice} mercados não têm preço atual deste produto e ficaram fora da comparação.`}
        </p>
      ) : null}
    </section>
  );
}

function relativeTime(value: string) {
  const minutes = Math.max(0, Math.round((Date.now() - new Date(value).getTime()) / 60000));
  if (minutes < 60) return minutes <= 1 ? "agora" : `há ${minutes} min`;
  const hours = Math.round(minutes / 60);
  if (hours < 24) return `há ${hours} h`;
  const days = Math.round(hours / 24);
  return days === 1 ? "ontem" : `há ${days} dias`;
}
