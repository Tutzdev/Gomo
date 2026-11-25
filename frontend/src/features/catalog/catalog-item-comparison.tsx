import { useQuery } from "@tanstack/react-query";
import { ExternalLink, Store, Tags, Trophy } from "lucide-react";
import { Link } from "react-router-dom";
import { perMeasure } from "@/features/basic-items/basic-item-format";
import { MissingStores } from "@/features/basic-items/missing-stores";
import { EmptyState, ErrorState, LoadingState } from "@/components/ui/feedback";
import { StatusBadge } from "@/components/page/page-elements";
import { formatCurrency, formatUpdatedAt } from "@/lib/brand";
import { cn } from "@/lib/cn";
import { DailyComparisonsMeter, DailyLimitNotice, LockedStores } from "@/features/plan/plan-locks";
import { PriceHistoryPanel } from "@/features/plan/price-history-panel";
import { basicItemApi, catalogApi } from "@/services/gomo-api";
import type { CatalogItemDetail } from "@/types/api";
import { ShareButton } from "@/components/ui/share-button";
import { productPagePath } from "@/lib/product-page-path";
import { siteUrl } from "@/lib/site-url";
import { AddToList } from "./add-to-list";
import { CatalogItemThumbnail } from "./catalog-item-picker";
import { pricedStoresLabel } from "./comparison-text";

function productShareText(name: string, cheapest: number, cheapestStore: string, priciest: number | null) {
  const elsewhere = priciest !== null && priciest > cheapest ? `, e chega a ${formatCurrency(priciest)} em outro mercado` : "";
  return `${name} está ${formatCurrency(cheapest)} no ${cheapestStore}${elsewhere}. Vi no Gomo:`;
}

/**
 * One product, every market that sells it at a real, current price, cheapest first.
 * Markets without a current price come last, by name, each with the reason.
 */
export function CatalogItemComparison({
  itemId,
  cityId,
  storeIds,
  cityName,
}: {
  itemId: string;
  cityId: string;
  storeIds?: string[];
  cityName?: string | null;
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
      <ComparisonResult
        detail={detail.data}
        cityName={cityName}
      />
      <AnyBrandBanner
        itemId={itemId}
        cityId={cityId}
        exactStores={detail.data.offers.length + detail.data.access.lockedStores}
      />
      {detail.data.offers.length ? <PriceHistoryPanel itemId={itemId} productId={detail.data.item.productId} cityId={cityId} storeIds={storeIds} /> : null}
    </>
  );
}

function ComparisonResult({ detail, cityName }: { detail: CatalogItemDetail; cityName?: string | null }) {
  const { item, offers, missingStores, access } = detail;
  // Stores hidden by the plan still have a current price, so the headline counts them.
  const pricedStores = offers.length + access.lockedStores;
  const cheapest = offers[0]?.price.unitPrice ?? null;
  const priciest = offers.at(-1)?.price.unitPrice ?? null;

  return (
    <section className="surface overflow-hidden" aria-labelledby={`compare-${item.id}`}>
      <header className="flex flex-wrap items-center gap-4 border-b border-border p-4 sm:p-5">
        <CatalogItemThumbnail imageUrl={item.imageUrl} />
        <div className="min-w-0 flex-1">
          <h1 id={`compare-${item.id}`} className="page-title">
            {item.name}
          </h1>
          <p className="mt-1 text-sm text-muted">
            {pricedStoresLabel(pricedStores, cityName)}
          </p>
          <div className="mt-2">
            <DailyComparisonsMeter access={access} />
          </div>
        </div>
        <div className="flex flex-wrap gap-2">
          <AddToList catalogItem={item} />
          {cheapest !== null ? (
            <ShareButton
              text={productShareText(item.name, cheapest, offers[0].storeName, priciest)}
              url={siteUrl(productPagePath(item.id, item.name))}
            />
          ) : null}
        </div>
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
                      <p className="mt-0.5 line-clamp-2 text-xs text-muted">
                        Nome no mercado: {offer.storeProductName}
                      </p>
                    ) : null}
                    <div className="mt-2 flex flex-wrap items-center gap-2">
                      {offer.price.promotionApplied ? <StatusBadge tone="success">Promoção</StatusBadge> : null}
                      {observation ? (
                        <span className="text-sm font-semibold text-muted">
                          Atualizado {formatUpdatedAt(observation.collectedAt)}
                        </span>
                      ) : null}
                      {offer.originUrl ? (
                        <a
                          className="inline-flex min-h-11 items-center gap-1.5 rounded-lg border border-border-strong bg-white px-3 text-sm font-semibold text-foreground hover:bg-primary-soft focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-focus focus-visible:ring-offset-2"
                          href={offer.originUrl}
                          target="_blank"
                          rel="noreferrer"
                        >
                          Ver no site do mercado
                          <ExternalLink
                            className="size-4"
                            aria-hidden
                          />
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
                      <p className="text-sm font-semibold text-muted tabular-nums">+{formatCurrency(difference)}</p>
                    )}
                  </div>
                </li>
              );
            })}
          </ol>
          <LockedStores count={access.lockedStores} />
        </>
      )}
      <MissingStores stores={missingStores} what={item.name} />
    </section>
  );
}

function AnyBrandBanner({ itemId, cityId, exactStores }: { itemId: string; cityId: string; exactStores: number }) {
  const basicItem = useQuery({
    queryKey: ["basic-item-of", itemId, cityId],
    queryFn: () => basicItemApi.ofCatalogItem(itemId, cityId),
    enabled: Boolean(itemId && cityId),
    staleTime: 60_000,
  });
  const item = basicItem.data;
  if (!item || item.pricedStores <= exactStores) return null;
  return (
    <Link
      to={`/app/basicos/${item.slug}?cityId=${cityId}`}
      className="mt-4 flex items-center gap-3 rounded-lg border border-primary/25 bg-primary-soft px-4 py-3 text-sm hover:bg-primary-soft-strong focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-focus"
    >
      <Tags className="size-5 shrink-0 text-primary" aria-hidden />
      <span className="min-w-0 flex-1">
        <strong>
          {exactStores === 1 ? "Este produto exato está em 1 mercado." : `Este produto exato está em ${exactStores} mercados.`}
        </strong>{" "}
        {item.name} de qualquer marca tem preço em {item.pricedStores} de {item.comparedStores}
        {item.lowestPricePerMeasure !== null ? `, a partir de ${perMeasure(item.lowestPricePerMeasure, item)}` : ""}.
      </span>
      <span className="shrink-0 font-semibold text-primary">Comparar marcas</span>
    </Link>
  );
}
