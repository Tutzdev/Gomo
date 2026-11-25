import { useQuery } from "@tanstack/react-query";
import { Lock, Search, Store, Tag, Trophy, X } from "lucide-react";
import { useState } from "react";
import { Link } from "react-router-dom";
import { Pagination, StatusBadge } from "@/components/page/page-elements";
import { EmptyState, ErrorState, SkeletonRows } from "@/components/ui/feedback";
import { SelectField } from "@/components/ui/form-field";
import { NativeButton } from "@/components/ui/native-button";
import { ShareButton } from "@/components/ui/share-button";
import { useAuth } from "@/features/auth/auth-context";
import { AddToList } from "@/features/catalog/add-to-list";
import { CatalogItemThumbnail } from "@/features/catalog/catalog-item-picker";
import { useUpsell } from "@/features/plan/upsell-context";
import { usePlan } from "@/features/plan/use-plan";
import { formatCurrency, formatUpdatedAt } from "@/lib/brand";
import { productPagePath } from "@/lib/product-page-path";
import { siteUrl } from "@/lib/site-url";
import { useDebouncedValue } from "@/lib/use-debounced-value";
import { dealApi } from "@/services/gomo-api";
import type { Deal } from "@/types/api";

const PAGE_SIZE = 24;
const SEARCH_DELAY_MS = 300;

/**
 * What is cheaper than usual in the city right now. One bar filters by word, market and aisle; the same board is
 * the public page (/ofertas) and a page of the app, where a Premium account can narrow it to their lists.
 */
export function DealsBoard({ cityId, cityName, inApp }: { cityId: string; cityName: string | null; inApp: boolean }) {
  const { user } = useAuth();
  const { premium } = usePlan();
  const { showUpsell } = useUpsell();
  const [search, setSearch] = useState("");
  const [storeId, setStoreId] = useState("");
  const [category, setCategory] = useState("");
  const [onlyMyLists, setOnlyMyLists] = useState(false);
  const [page, setPage] = useState(0);
  const query = useDebouncedValue(search.trim(), SEARCH_DELAY_MS);
  const deals = useQuery({
    queryKey: ["deals", cityId, storeId, category, query, onlyMyLists, page],
    queryFn: () => dealApi.list({ cityId, storeId, category, query, onlyMyLists, page, size: PAGE_SIZE }),
    enabled: Boolean(cityId),
    staleTime: 5 * 60 * 1000,
    placeholderData: (previous) => previous,
  });
  const inCity = cityName ? ` em ${cityName}` : "";
  const filtered = Boolean(search || storeId || category || onlyMyLists);
  const data = deals.data;
  const dealsInCity = data?.stores.reduce((sum, store) => sum + store.deals, 0) ?? 0;

  const clearFilters = () => {
    setSearch("");
    setStoreId("");
    setCategory("");
    setOnlyMyLists(false);
    setPage(0);
  };

  const toggleMyLists = () => {
    if (!premium) {
      showUpsell("DEALS_FOR_MY_LISTS");
      return;
    }

    setOnlyMyLists((value) => !value);
    setPage(0);
  };

  return (
    <div className="space-y-5">
      <section aria-label="Filtrar ofertas" className="surface space-y-3 p-4">
        <div className="grid grid-cols-2 gap-3 md:grid-cols-[minmax(0,2fr)_minmax(0,1fr)_minmax(0,1fr)]">
          <div className="col-span-2 md:col-span-1">
            <label htmlFor="deals-search" className="mb-1.5 block text-sm font-semibold text-foreground">
              Buscar oferta
            </label>
            <div className="relative">
              <Search className="pointer-events-none absolute left-3 top-1/2 size-4 -translate-y-1/2 text-muted" aria-hidden />
              <input
                id="deals-search"
                type="search"
                value={search}
                onChange={(event) => {
                  setSearch(event.target.value);
                  setPage(0);
                }}
                placeholder="Café, arroz, sabão em pó…"
                autoComplete="off"
                className="field-control"
                style={{ paddingLeft: "2.25rem" }}
              />
            </div>
          </div>
          <SelectField
            id="deals-store"
            label="Mercado"
            value={storeId}
            onChange={(event) => {
              setStoreId(event.target.value);
              setPage(0);
            }}
          >
            <option value="">Todos</option>
            {data?.stores.map((store) => (
              <option key={store.storeId} value={store.storeId}>{store.storeName} ({store.deals})</option>
            ))}
          </SelectField>
          <SelectField
            id="deals-category"
            label="Corredor"
            value={category}
            onChange={(event) => {
              setCategory(event.target.value);
              setPage(0);
            }}
          >
            <option value="">Todos</option>
            {data?.categories.map((entry) => (
              <option key={entry.category} value={entry.category}>{entry.category} ({entry.deals})</option>
            ))}
          </SelectField>
        </div>
        {inApp && user ? (
          <label className="inline-flex min-h-10 cursor-pointer items-center gap-2 text-sm font-semibold">
            <input type="checkbox" checked={onlyMyLists} onChange={toggleMyLists} className="size-4 accent-primary" />
            Só o que está nas minhas listas
            {!premium ? <Lock className="size-4 text-muted" aria-label="Premium" /> : null}
          </label>
        ) : null}
      </section>

      {deals.isPending ? (
        <div>
          <p role="status" className="mb-3 text-sm text-muted">Buscando as ofertas da semana…</p>
          <SkeletonRows rows={6} />
        </div>
      ) : null}
      {deals.isError ? <ErrorState message={deals.error.message} retry={() => void deals.refetch()} /> : null}

      {data ? (
        <>
          <div className="flex flex-wrap items-center justify-between gap-3" aria-live="polite" aria-busy={deals.isFetching}>
            <p className="text-sm text-muted">
              <span className="font-semibold text-foreground">
                {data.deals.totalElements} {data.deals.totalElements === 1 ? "oferta" : "ofertas"}
              </span>{" "}
              com {data.minimumDiscountPercent}% ou mais de desconto · atualizado {formatUpdatedAt(data.computedAt)}
            </p>
            <div className="flex items-center gap-2">
              {filtered ? (
                <NativeButton variant="ghost" size="sm" onClick={clearFilters}>
                  <X className="size-4" aria-hidden />
                  Limpar filtros
                </NativeButton>
              ) : null}
              <ShareButton
                label="Compartilhar"
                text={`Ofertas de hoje${inCity} no Gomo: produtos abaixo do preço de sempre em cada mercado.`}
                url={siteUrl("/ofertas")}
              />
            </div>
          </div>

          {!data.deals.content.length ? (
            <EmptyState
              title={emptyTitle(query, onlyMyLists)}
              description={emptyDescription(filtered, dealsInCity)}
              action={filtered ? <NativeButton variant="secondary" onClick={clearFilters}>Limpar filtros</NativeButton> : undefined}
            />
          ) : (
            <ul className="grid gap-4 sm:grid-cols-2 xl:grid-cols-3">
              {data.deals.content.map((deal) => (
                <DealCard key={`${deal.itemId}-${deal.storeId}`} deal={deal} inApp={inApp} />
              ))}
            </ul>
          )}
          {data.deals.totalPages > 1 ? <Pagination page={page} totalPages={data.deals.totalPages} onChange={setPage} /> : null}
        </>
      ) : null}
    </div>
  );
}

function emptyTitle(query: string, onlyMyLists: boolean) {
  if (query) return `Nenhuma oferta para "${query}"`;
  if (onlyMyLists) return "Nada da sua lista em oferta agora";
  return "Nenhuma oferta por aqui agora";
}

function emptyDescription(filtered: boolean, dealsInCity: number) {
  if (filtered) return "Tente outra palavra, outro mercado ou outro corredor.";
  if (dealsInCity === 0) {
    return "Os preços são coletados 4 vezes ao dia; as ofertas aparecem depois de uma semana de histórico na cidade.";
  }
  return "Volte mais tarde: as ofertas mudam a cada coleta.";
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
