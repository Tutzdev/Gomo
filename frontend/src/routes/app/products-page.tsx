import { keepPreviousData, useQuery, useQueryClient } from "@tanstack/react-query";
import { Search, SlidersHorizontal, Store } from "lucide-react";
import { useEffect, useState } from "react";
import { Link } from "react-router-dom";
import { EmptyState, ErrorState, SkeletonRows } from "@/components/ui/feedback";
import { SelectField } from "@/components/ui/form-field";
import { NativeButton } from "@/components/ui/native-button";
import {
  PageHeading,
  Pagination,
  StatusBadge,
} from "@/components/page/page-elements";
import { AddToList } from "@/features/catalog/add-to-list";
import { CatalogItemThumbnail } from "@/features/catalog/catalog-item-picker";
import { ComparisonStoreFilter } from "@/features/catalog/comparison-store-filter";
import { queryString } from "@/lib/api";
import { formatCurrency, formatDate } from "@/lib/brand";
import { catalogApi } from "@/services/gomo-api";
import type { CatalogItem } from "@/types/api";
import { useDefaultCityId } from "@/lib/use-default-city";
import { useScrollToTopOnChange } from "@/lib/use-scroll-to-top";

const PAGE_SIZE = 24;
/** Prices change four times a day: a page seen a minute ago is still right, so going back is instant. */
const CATALOG_STALE_TIME = 2 * 60 * 1000;

export function ProductsPage() {
  const [storeIds, setStoreIds] = useState<string[]>([]);
  const [page, setPage] = useState(0);
  const [search, setSearch] = useState("");
  const [query, setQuery] = useState("");
  const [selectedCity, setSelectedCity] = useState("");
  const [showFilters, setShowFilters] = useState(false);
  useEffect(() => {
    const timer = window.setTimeout(() => setQuery(search.trim()), 300);
    return () => window.clearTimeout(timer);
  }, [search]);
  const cities = useQuery({
    queryKey: ["cities", "catalog"],
    queryFn: () => catalogApi.cities(),
  });
  const defaultCityId = useDefaultCityId();
  const cityId = selectedCity || defaultCityId;
  const queryClient = useQueryClient();
  const items = useQuery({
    queryKey: ["catalog-items", "page", query, page, cityId, storeIds],
    queryFn: ({ signal }) =>
      catalogApi.catalogItems({ query, page, size: PAGE_SIZE, cityId, storeIds }, signal),
    enabled: !cities.isPending,
    staleTime: CATALOG_STALE_TIME,
    // The cards already on screen stay while the next search or page loads, instead of blinking into a skeleton.
    placeholderData: keepPreviousData,
  });
  const totalPages = items.data?.totalPages ?? 0;
  // The next page is usually the next tap: fetch it while the shopper is still reading this one.
  useEffect(() => {
    if (items.isPlaceholderData || page + 1 >= totalPages) return;
    void queryClient.prefetchQuery({
      queryKey: ["catalog-items", "page", query, page + 1, cityId, storeIds],
      queryFn: ({ signal }) =>
        catalogApi.catalogItems({ query, page: page + 1, size: PAGE_SIZE, cityId, storeIds }, signal),
      staleTime: CATALOG_STALE_TIME,
    });
  }, [queryClient, items.isPlaceholderData, page, totalPages, query, cityId, storeIds]);
  useScrollToTopOnChange(page);
  const waiting = items.isPending;
  const refreshing = !waiting && (items.isPlaceholderData || search.trim() !== query);

  return (
    <>
      <PageHeading
        title="Buscar preços"
        description="Digite o produto como você falaria no mercado. Cada um aparece uma vez, com o preço de todos os mercados que o vendem."
      />
      <section className="surface mb-6" aria-label="Pesquisa no catálogo">
        <div className="grid grid-cols-[minmax(0,1fr)_auto] gap-3 p-4 sm:gap-4 sm:p-5 lg:grid-cols-[1fr_16rem_auto] lg:items-end">
          <div className="col-span-2 lg:col-span-1">
            <label htmlFor="catalog-search" className="mb-2 block text-sm font-medium">
              Buscar produto
            </label>
            <div className="relative">
              <Search
                className="pointer-events-none absolute left-3 top-3.5 size-5 text-muted"
                aria-hidden
              />
              <input
                id="catalog-search"
                type="search"
                className="field-control product-search-input w-full pl-10"
                placeholder="Ex.: coca cola 2 litros, monster, leite ninho"
                value={search}
                onChange={(event) => {
                  setSearch(event.target.value);
                  setPage(0);
                }}
                autoComplete="off"
              />
            </div>
          </div>
          <SelectField
            id="catalog-city"
            label="Cidade dos preços"
            value={cityId}
            onChange={(event) => {
              setSelectedCity(event.target.value);
              setStoreIds([]);
              setPage(0);
            }}
          >
            <option value="">Selecione a cidade</option>
            {cities.data?.content.map((city) => (
              <option key={city.id} value={city.id}>
                {city.name}
              </option>
            ))}
          </SelectField>
          <NativeButton
            variant="secondary"
            className="self-end"
            aria-expanded={showFilters}
            aria-controls="catalog-filters"
            onClick={() => setShowFilters(!showFilters)}
          >
            <SlidersHorizontal className="size-4" aria-hidden />
            Mercados
          </NativeButton>
        </div>
        {showFilters ? (
          <div id="catalog-filters" className="px-4 pb-5 sm:px-5">
            <ComparisonStoreFilter
              cityId={cityId}
              value={storeIds}
              onChange={(ids) => {
                setStoreIds(ids);
                setPage(0);
              }}
            />
          </div>
        ) : null}
      </section>
      {cities.isError ? <ErrorState retry={() => void cities.refetch()} /> : null}
      <section aria-live="polite" aria-busy={waiting || refreshing}>
        <div className="mb-4 flex flex-wrap items-center justify-between gap-3">
          <h2 className="font-semibold">
            {query ? `Resultados para “${query}”` : "Mais encontrados nos mercados"}
          </h2>
          {refreshing ? (
            <span className="text-xs text-muted">Atualizando…</span>
          ) : !waiting && items.data ? (
            <span className="text-xs text-muted">
              {items.data.totalElements.toLocaleString("pt-BR")} produtos
            </span>
          ) : null}
        </div>
        {waiting ? (
          <SkeletonRows rows={5} />
        ) : items.isError ? (
          <ErrorState retry={() => void items.refetch()} />
        ) : !items.data?.content.length ? (
          <EmptyState
            title="Nenhum produto encontrado"
            description="Tente só a marca, ou a marca e o tamanho (ex.: “ninho 380g”). Mostramos apenas produtos vendidos em mais de um mercado."
          />
        ) : (
          <>
            <div
              className={`grid gap-3 transition-opacity duration-150 sm:grid-cols-2 sm:gap-4 xl:grid-cols-3 motion-reduce:transition-none ${refreshing ? "opacity-60" : ""}`}
            >
              {items.data.content.map((item) => (
                <CatalogItemCard
                  key={item.id}
                  item={item}
                  href={`/app/comparar/${item.id}?${queryString({ cityId, storeIds: storeIds.join(",") })}`}
                  cityChosen={Boolean(cityId)}
                />
              ))}
            </div>
            <Pagination page={page} totalPages={items.data.totalPages} onChange={setPage} />
          </>
        )}
      </section>
    </>
  );
}

function CatalogItemCard({
  item,
  href,
  cityChosen,
}: {
  item: CatalogItem;
  href: string;
  cityChosen: boolean;
}) {
  const priced = item.lowestPrice !== null;
  const spread =
    priced && item.highestPrice !== null && item.highestPrice > item.lowestPrice!
      ? item.highestPrice - item.lowestPrice!
      : 0;
  return (
    <article className="surface flex min-w-0 flex-col p-4 sm:p-5">
      <div className="flex items-start gap-3">
        <CatalogItemThumbnail imageUrl={item.imageUrl} />
        <div className="min-w-0">
          <h3 className="text-sm font-semibold leading-6">
            <Link
              to={href}
              className="hover:text-primary hover:underline focus-visible:outline-2 focus-visible:outline-focus"
            >
              {item.name}
            </Link>
          </h3>
          <p className="mt-1 flex items-center gap-1 text-xs text-muted">
            <Store className="size-3.5 shrink-0" aria-hidden />
            {/* Without a city there are no current prices to count: say where it is sold instead of "0 mercados". */}
            {!cityChosen
              ? `Vendido em ${item.storeCount} mercados`
              : item.pricedStores === 1
                ? "Preço atual em 1 mercado"
                : `Preço atual em ${item.pricedStores} mercados`}
          </p>
        </div>
      </div>
      <div className="mb-5 mt-5 flex-1">
        {priced ? (
          <>
            <StatusBadge tone={item.pricedStores > 1 ? "success" : "neutral"}>
              {item.pricedStores > 1
                ? `Menor preço entre ${item.pricedStores} mercados`
                : "Preço atual em 1 mercado"}
            </StatusBadge>
            <p className="mt-2 text-2xl font-extrabold tabular-nums">
              {formatCurrency(item.lowestPrice!)}
            </p>
            <p className="mt-1 text-sm font-medium">{item.lowestPriceStore}</p>
            {spread > 0 ? (
              <p className="mt-1 text-xs text-muted">
                Até {formatCurrency(item.highestPrice!)} nos outros: economia de{" "}
                <span className="font-semibold text-foreground">{formatCurrency(spread)}</span>
              </p>
            ) : null}
            <p className="mt-1 text-xs text-muted">Atualizado em {formatDate(item.pricesCollectedAt)}</p>
          </>
        ) : (
          <p className="text-sm text-muted">
            {cityChosen
              ? "Sem preço atualizado agora. Os mercados são consultados várias vezes por dia."
              : "Selecione a cidade para consultar preços."}
          </p>
        )}
      </div>
      <div className="flex flex-wrap gap-2">
        <NativeButton to={href} size="sm">
          Comparar
        </NativeButton>
        <AddToList catalogItem={item} />
      </div>
    </article>
  );
}
