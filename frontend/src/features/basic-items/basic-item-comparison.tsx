import { useQuery } from "@tanstack/react-query";
import { ExternalLink, Store, Trophy } from "lucide-react";
import { Link } from "react-router-dom";
import { StatusBadge } from "@/components/page/page-elements";
import { EmptyState, ErrorState, LoadingState } from "@/components/ui/feedback";
import { AddToList } from "@/features/catalog/add-to-list";
import { DailyComparisonsMeter, DailyLimitNotice, LockedStores } from "@/features/plan/plan-locks";
import { formatCurrency } from "@/lib/brand";
import { measureName } from "@/features/catalog/unit-price";
import { cn } from "@/lib/cn";
import { basicItemApi } from "@/services/gomo-api";
import type { BasicItem, BasicItemComparison as Comparison, BasicItemOffer } from "@/types/api";
import { perMeasure } from "./basic-item-format";
import { MissingStores } from "./missing-stores";

export function BasicItemComparison({
  slug,
  cityId,
  storeIds,
  cityName,
}: {
  slug: string;
  cityId: string;
  storeIds?: string[];
  cityName?: string | null;
}) {
  const comparison = useQuery({
    queryKey: ["basic-item", slug, cityId, storeIds],
    queryFn: () => basicItemApi.compare(slug, cityId, storeIds),
    enabled: Boolean(slug && cityId),
  });
  if (!cityId) return <EmptyState title="Escolha a cidade" description="Os preços são comparados entre os mercados da cidade." />;
  if (comparison.isPending) return <LoadingState label="Comparando mercados…" />;
  if (comparison.isError) {
    return <ErrorState message={comparison.error.message} retry={() => void comparison.refetch()} />;
  }
  return (
    <ComparisonResult
      comparison={comparison.data}
      cityName={cityName}
    />
  );
}

function ComparisonResult({ comparison, cityName }: { comparison: Comparison; cityName?: string | null }) {
  const { item, offers, missingStores, access } = comparison;
  const pricedStores = offers.length + access.lockedStores;
  const cheapest = offers[0]?.pricePerMeasure ?? null;
  const priciest = offers.reduce<number | null>(
    (highest, offer) => (offer.pricePerMeasure !== null && offer.pricePerMeasure > (highest ?? 0) ? offer.pricePerMeasure : highest),
    null,
  );

  return (
    <section className="surface overflow-hidden" aria-labelledby={`basic-${item.slug}`}>
      <header className="flex flex-wrap items-center gap-4 border-b border-border p-4 sm:p-5">
        <div className="min-w-0 flex-1">
          <p className="text-xs font-semibold text-primary">{item.aisle}</p>
          <h1 id={`basic-${item.slug}`} className="page-title mt-1">
            {item.name} (qualquer marca)
          </h1>
          <p className="mt-1 text-sm text-muted">
            {pricedStores
              ? `Preço atual em ${pricedStores} de ${pricedStores + missingStores.length} mercados${cityName ? ` de ${cityName}` : ""}.`
              : "Nenhum mercado com preço atual agora."}{" "}
            Em cada mercado mostramos a marca mais barata no preço do {measureName(item.measure)}, em embalagem perto de {item.typicalPackage}.
          </p>
          <div className="mt-2">
            <DailyComparisonsMeter access={access} />
          </div>
        </div>
        <AddToList basicItem={item} />
      </header>

      <DailyLimitNotice access={access} />
      {cheapest !== null && priciest !== null && priciest > cheapest ? (
        <p className="border-b border-border bg-success-soft px-4 py-3 text-sm sm:px-5">
          Do mais barato ao mais caro, a diferença é de{" "}
          <strong className="tabular-nums">{perMeasure(priciest - cheapest, item)}</strong>.
        </p>
      ) : null}
      {offers.length ? (
        <ol className="divide-y divide-border">
          {offers.map((offer, index) => (
            <OfferRow key={offer.storeId} offer={offer} item={item} position={index} cheapest={cheapest} />
          ))}
        </ol>
      ) : (
        <div className="p-5">
          <EmptyState
            title="Nenhum mercado com preço atual agora"
            description="Os preços são atualizados várias vezes ao dia. Volte em alguns minutos."
          />
        </div>
      )}
      <LockedStores count={access.lockedStores} />
      <MissingStores stores={missingStores} what={item.name.toLowerCase()} />
    </section>
  );
}

function OfferRow({
  offer,
  item,
  position,
  cheapest,
}: {
  offer: BasicItemOffer;
  item: BasicItem;
  position: number;
  cheapest: number | null;
}) {
  const first = position === 0;
  const price = offer.price.unitPrice!;
  const difference = cheapest !== null && offer.pricePerMeasure !== null ? offer.pricePerMeasure - cheapest : null;
  return (
    <li className={cn("flex flex-wrap items-center gap-x-4 gap-y-2 px-4 py-4 sm:px-5", first && "bg-primary-soft/40")}>
      <span
        className={cn(
          "grid size-8 shrink-0 place-items-center rounded-full text-sm font-bold tabular-nums",
          first ? "bg-success text-white" : "bg-surface-strong text-muted",
        )}
        aria-hidden
      >
        {first ? <Trophy className="size-4" /> : position + 1}
      </span>
      <div className="min-w-0 flex-1">
        <p className="flex items-center gap-1.5 font-semibold">
          <Store className="size-4 shrink-0 text-muted" aria-hidden />
          {offer.storeName}
          {first ? <span className="sr-only">(mais barato)</span> : null}
        </p>
        <p className="mt-0.5 text-sm">
          {offer.catalogItemId ? (
            <Link className="hover:text-primary hover:underline" to={`/app/comparar/${offer.catalogItemId}`}>
              {offer.productName}
            </Link>
          ) : (
            offer.productName
          )}
        </p>
        <div className="mt-2 flex flex-wrap items-center gap-2">
          {offer.price.promotionApplied ? <StatusBadge tone="success">Promoção</StatusBadge> : null}
          {!offer.typicalPackage && offer.packageLabel ? (
            <span className="text-xs text-muted">Embalagem fora do comum</span>
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
        <p className={cn("text-xl font-extrabold tabular-nums", first && "text-success")}>
          {formatCurrency(price)}
        </p>
        <p className="text-xs text-muted">
          {offer.packageLabel ? `embalagem de ${offer.packageLabel}` : "tamanho não informado"}
        </p>
        {offer.pricePerMeasure !== null ? (
          <p className="mt-1 text-sm font-semibold tabular-nums">{perMeasure(offer.pricePerMeasure, item)}</p>
        ) : null}
        {first ? (
          <p className="text-sm font-semibold text-success">Mais barato</p>
        ) : difference !== null ? (
          <p className="text-sm font-semibold text-muted tabular-nums">+{perMeasure(difference, item)}</p>
        ) : null}
      </div>
    </li>
  );
}
