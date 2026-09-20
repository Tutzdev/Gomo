import { useQuery } from "@tanstack/react-query";
import { Search, SlidersHorizontal } from "lucide-react";
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
import { MeasurementPrice } from "@/features/catalog/measurement-price";
import { ProductImage } from "@/features/catalog/product-image";
import { ComparisonStoreFilter } from "@/features/catalog/comparison-store-filter";
import { queryString } from "@/lib/api";
import { ProductSearchFilters } from "@/features/catalog/product-search-filters";
import { productMetadata } from "@/features/catalog/product-label";
import { formatCurrency, formatDate } from "@/lib/brand";
import { catalogApi, type ProductFilters } from "@/services/gomo-api";

export function ProductsPage() {
  const [storeIds, setStoreIds] = useState<string[]>([]);
  const [page, setPage] = useState(0);
  const [search, setSearch] = useState("");
  const [query, setQuery] = useState("");
  const [selectedCity, setSelectedCity] = useState("");
  const [filters, setFilters] = useState<ProductFilters>({});
  const [showFilters, setShowFilters] = useState(false);
  useEffect(() => {
    const timer = window.setTimeout(() => setQuery(search.trim()), 300);
    return () => window.clearTimeout(timer);
  }, [search]);
  const cities = useQuery({
    queryKey: ["cities", "catalog"],
    queryFn: () => catalogApi.cities(),
  });
  const cityId =
    selectedCity ||
    cities.data?.content.find((city) => city.name === "Volta Redonda")?.id ||
    "";
  const searchReady = query.length >= 2 || Object.values(filters).some(Boolean);
  const products = useQuery({
    queryKey: ["products", "discovery", query, page, filters, cityId, storeIds],
    queryFn: ({ signal }) =>
      catalogApi.discoverProducts({ ...filters, query, page, size: 20, cityId, storeIds }, signal),
    enabled: !cities.isPending && searchReady,
  });
  const waiting = (searchReady && products.isPending) || search.trim() !== query;

  return (
    <>
      <PageHeading
        title="Produtos"
        description="Pesquise do seu jeito e encontre os preços nas lojas da sua cidade."
      />
      <section className="surface mb-6" aria-label="Pesquisa no catálogo">
        <div className="grid gap-4 p-4 sm:p-5 lg:grid-cols-[1fr_16rem_auto] lg:items-end">
          <div>
            <label
              htmlFor="catalog-search"
              className="mb-2 block text-sm font-medium"
            >
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
                placeholder="Ex.: coca cola 1 litro, arroz 5kg, detergente"
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
            aria-expanded={showFilters}
            aria-controls="catalog-filters"
            onClick={() => setShowFilters(!showFilters)}
          >
            <SlidersHorizontal className="size-4" aria-hidden />
            Filtros
          </NativeButton>
        </div>
        {showFilters ? (
          <div id="catalog-filters">
            <ProductSearchFilters
              id="catalog"
              query={query}
              value={filters}
              onChange={(value) => {
                setFilters(value);
                setPage(0);
              }}
            />
            <div className="px-5 pb-5">
              <ComparisonStoreFilter
                cityId={cityId}
                value={storeIds}
                onChange={(ids) => { setStoreIds(ids); setPage(0); }}
              />
            </div>
          </div>
        ) : null}
      </section>
      {cities.isError ? (
        <ErrorState retry={() => void cities.refetch()} />
      ) : null}
      <section aria-live="polite" aria-busy={waiting}>
        <div className="mb-4 flex flex-wrap items-center justify-between gap-3">
          <h2 className="font-semibold">
            {query ? `Resultados para “${query}”` : "Explorar o catálogo"}
          </h2>
          {!waiting && products.data ? (
            <span className="text-xs text-muted">
              {products.data.totalElements.toLocaleString("pt-BR")} produtos
            </span>
          ) : null}
        </div>
        {!searchReady ? (
          <EmptyState title="Qual produto você quer comparar?" description="Digite o nome, a marca e, se souber, o tamanho. As ofertas equivalentes aparecem juntas por produto." />
        ) : waiting ? (
          <SkeletonRows rows={5} />
        ) : products.isError ? (
          <ErrorState retry={() => void products.refetch()} />
        ) : !products.data?.content.length ? (
          <EmptyState
            title="Nenhum produto encontrado"
            description="Tente uma palavra mais curta ou remova os filtros."
          />
        ) : (
          <>
            <div className="grid gap-4 sm:grid-cols-2 xl:grid-cols-3">
              {products.data.content.map((group) => {
                const product = group.product;
                const available = group.offers;
                const comparisonUrl = `/app/produtos/${product.id}?${queryString({ cityId, storeIds: storeIds.join(",") })}`;
                const best = available[0];
                return (
                  <article
                    key={product.id}
                    className="surface flex min-w-0 flex-col p-4 sm:p-5"
                  >
                    <div className="flex items-start gap-3">
                      <ProductImage product={product} />
                      <div className="min-w-0">
                        <h3 className="text-sm font-semibold leading-6">
                          <Link
                            to={comparisonUrl}
                            className="hover:text-primary hover:underline focus-visible:outline-2 focus-visible:outline-focus"
                          >
                            {group.displayName}
                          </Link>
                        </h3>
                        <p className="mt-1 text-xs leading-5 text-muted">
                          {productMetadata(product)}
                        </p>
                      </div>
                    </div>
                    <div className="mb-5 mt-5 flex-1">
                      {best && best.price.unitPrice !== null ? (
                        <>
                          <StatusBadge tone={available.length > 1 ? "success" : "neutral"}>
                            {available.length > 1 ? `Menor preço entre ${available.length} mercados` : "Preço em 1 mercado"}
                          </StatusBadge>
                          <p className="mt-2 text-2xl font-extrabold tabular-nums">
                            {formatCurrency(best.price.unitPrice)}
                          </p>
                          <MeasurementPrice value={best.measurementPrice} />
                          <p className="mt-2 text-sm font-medium">
                            {best.storeName}
                          </p>
                          <p className="mt-1 text-xs text-muted">
                            Coletado em{" "}
                            {formatDate(best.price.observation?.collectedAt)}
                          </p>
                          {best.price.availability === "UNKNOWN" ? (
                            <p className="mt-1 text-xs text-warning">
                              Estoque não informado pela loja
                            </p>
                          ) : null}
                        </>
                      ) : (
                        <p className="text-sm text-muted">
                          {cityId
                              ? "Sem preço atual nesta cidade."
                              : "Selecione a cidade para consultar preços."}
                        </p>
                      )}
                    </div>
                    {!group.identityConfirmed ? <p className="mb-3 text-xs text-warning">Variante ou apresentação incompleta. Confira as possíveis correspondências.</p> : null}
                    <div className="flex flex-wrap gap-2">
                      <NativeButton to={comparisonUrl} size="sm">
                        Comparar
                      </NativeButton>
                      <AddToList product={product} />
                    </div>
                  </article>
                );
              })}
            </div>
            <Pagination
              page={page}
              totalPages={products.data.totalPages}
              onChange={setPage}
            />
          </>
        )}
      </section>
    </>
  );
}
