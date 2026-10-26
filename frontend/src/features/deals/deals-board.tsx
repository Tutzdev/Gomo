import { useQuery } from "@tanstack/react-query";
import { Lock, Store, Tag, Trophy } from "lucide-react";
import { useState, type ReactNode } from "react";
import { Link } from "react-router-dom";
import { Pagination, StatusBadge } from "@/components/page/page-elements";
import { EmptyState, ErrorState, SkeletonRows } from "@/components/ui/feedback";
import { SelectField } from "@/components/ui/form-field";
import { ShareButton } from "@/components/ui/share-button";
import { useAuth } from "@/features/auth/auth-context";
import { AddToList } from "@/features/catalog/add-to-list";
import { CatalogItemThumbnail } from "@/features/catalog/catalog-item-picker";
import { useUpsell } from "@/features/plan/upsell-context";
import { usePlan } from "@/features/plan/use-plan";
import { formatCurrency, formatDate } from "@/lib/brand";
import { cn } from "@/lib/cn";
import { productPagePath } from "@/lib/product-page-path";
import { siteUrl } from "@/lib/site-url";
import { dealApi } from "@/services/gomo-api";
import type { Deal } from "@/types/api";

const PAGE_SIZE = 24;

/**
 * What is cheaper than usual in the city right now, by market and aisle. The same board is the public page
 * (/ofertas, for Google and WhatsApp) and a page of the app; inside the app a Premium account can narrow it to
 * the products of their lists.
 */
export function DealsBoard({ cityId, cityName, inApp }: { cityId: string; cityName: string | null; inApp: boolean }) {
  const { user } = useAuth();
  const { premium } = usePlan();
  const { showUpsell } = useUpsell();
  const [storeId, setStoreId] = useState("");
  const [category, setCategory] = useState("");
  const [onlyMyLists, setOnlyMyLists] = useState(false);
  const [page, setPage] = useState(0);
  const deals = useQuery({
    queryKey: ["deals", cityId, storeId, category, onlyMyLists, page],
    queryFn: () => dealApi.list({ cityId, storeId, category, onlyMyLists, page, size: PAGE_SIZE }),
    enabled: Boolean(cityId),
    placeholderData: (previous) => previous,
  });
  const inCity = cityName ? ` em ${cityName}` : "";
  const reset = () => setPage(0);
  const toggleMyLists = () => {
    if (!premium) {
      showUpsell("DEALS_FOR_MY_LISTS");
      return;
    }
    setOnlyMyLists((value) => !value);
    reset();
  };

  if (deals.isPending) return <SkeletonRows rows={6} />;
  if (deals.isError) return <ErrorState message={deals.error.message} retry={() => void deals.refetch()} />;
  const data = deals.data;
  const total = data.stores.reduce((sum, store) => sum + store.deals, 0);

  return (
    <div className="space-y-5" aria-busy={deals.isFetching}>
      <div className="flex flex-wrap items-center justify-between gap-3">
        <p className="text-sm text-muted">
          Produtos pelo menos {data.minimumDiscountPercent}% abaixo do preço de sempre{inCity}, comparando com a média
          dos últimos 30 dias. Atualizado em {formatDate(data.computedAt)}.
        </p>
        <ShareButton
          label="Compartilhar ofertas"
          text={`Ofertas de hoje${inCity} no Gomo: produtos abaixo do preço de sempre em cada mercado.`}
          url={siteUrl("/ofertas")}
        />
      </div>

      <div className="flex flex-wrap gap-2" role="group" aria-label="Mercado">
        <FilterChip active={storeId === ""} onClick={() => { setStoreId(""); reset(); }}>
          Todos os mercados
        </FilterChip>
        {data.stores.map((store) => (
          <FilterChip key={store.storeId} active={storeId === store.storeId} onClick={() => { setStoreId(store.storeId); reset(); }}>
            {store.storeName} <span className="tabular-nums opacity-75">({store.deals})</span>
          </FilterChip>
        ))}
      </div>

      <div className="flex flex-wrap items-end gap-4">
        <div className="w-full max-w-xs">
          <SelectField id="deals-category" label="Corredor" value={category} onChange={(event) => { setCategory(event.target.value); reset(); }}>
            <option value="">Todos</option>
            {data.categories.map((entry) => (
              <option key={entry.category} value={entry.category}>{entry.category} ({entry.deals})</option>
            ))}
          </SelectField>
        </div>
        {inApp && user ? (
          <button
            type="button"
            aria-pressed={onlyMyLists}
            onClick={toggleMyLists}
            className={cn(
              "inline-flex min-h-11 items-center gap-2 rounded-full border px-4 text-sm font-semibold focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-focus",
              onlyMyLists ? "border-primary bg-primary text-white" : "border-border bg-white text-foreground hover:border-primary/40",
            )}
          >
            {!premium ? <Lock className="size-4" aria-label="Premium" /> : null}
            Só o que está nas minhas listas
          </button>
        ) : null}
      </div>

      {!data.deals.content.length ? (
        <EmptyState
          title={onlyMyLists ? "Nada da sua lista em oferta agora" : "Nenhuma oferta por aqui agora"}
          description={
            onlyMyLists
              ? "Quando um produto das suas listas ficar pelo menos 10% abaixo do preço de sempre, ele aparece aqui."
              : total === 0
                ? "Os preços são coletados 4 vezes ao dia; as ofertas aparecem depois de uma semana de histórico na cidade."
                : "Tente outro mercado ou corredor."
          }
        />
      ) : (
        <ul className="grid gap-4 sm:grid-cols-2 xl:grid-cols-3">
          {data.deals.content.map((deal) => (
            <DealCard key={`${deal.itemId}-${deal.storeId}`} deal={deal} inApp={inApp} />
          ))}
        </ul>
      )}
      {data.deals.totalPages > 1 ? <Pagination page={page} totalPages={data.deals.totalPages} onChange={setPage} /> : null}
    </div>
  );
}

function DealCard({ deal, inApp }: { deal: Deal; inApp: boolean }) {
  const details = [deal.brand, deal.sizeLabel].filter(Boolean).join(" · ");
  return (
    <li className="surface anim-trigger flex flex-col p-4">
      <div className="flex items-start gap-3">
        <CatalogItemThumbnail imageUrl={deal.imageUrl} />
        <div className="min-w-0 flex-1">
          <Link
            to={inApp ? `/app/comparar/${deal.itemId}` : productPagePath(deal.itemId, deal.name)}
            className="line-clamp-2 font-semibold leading-snug hover:text-primary hover:underline"
          >
            {deal.name}
          </Link>
          {details ? <p className="mt-0.5 text-xs text-muted">{details}</p> : null}
        </div>
        <span className="shrink-0 rounded-full bg-success-soft px-2 py-0.5 text-sm font-extrabold text-success tabular-nums">
          −{deal.discountPercent}%
        </span>
      </div>
      <div className="mt-4 flex items-end justify-between gap-3">
        <div>
          <p className="text-2xl font-extrabold tabular-nums">{formatCurrency(deal.price)}</p>
          <p className="text-xs text-muted">
            Preço de sempre: <span className="line-through">{formatCurrency(deal.usualPrice)}</span>
          </p>
        </div>
        <AddToList catalogItem={{ id: deal.itemId, name: deal.name }} />
      </div>
      <p className="mt-3 flex items-center gap-1.5 text-sm">
        <Store className="size-4 shrink-0 text-primary" aria-hidden />
        <span className="truncate">{deal.storeName}</span>
      </p>
      <div className="mt-2 flex flex-wrap gap-1.5">
        {deal.lowestIn90Days ? (
          <StatusBadge tone="success">
            <Trophy className="mr-1 inline size-3.5" aria-hidden />
            Menor preço em 90 dias
          </StatusBadge>
        ) : null}
        {deal.promotion ? (
          <StatusBadge tone="neutral">
            <Tag className="mr-1 inline size-3.5" aria-hidden />
            Promoção do mercado
          </StatusBadge>
        ) : null}
      </div>
    </li>
  );
}

function FilterChip({ active, onClick, children }: { active: boolean; onClick: () => void; children: ReactNode }) {
  return (
    <button
      type="button"
      aria-pressed={active}
      onClick={onClick}
      className={cn(
        "min-h-9 rounded-full px-3.5 text-sm font-semibold focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-focus",
        active ? "bg-primary text-white" : "bg-surface-strong text-muted hover:text-foreground",
      )}
    >
      {children}
    </button>
  );
}
