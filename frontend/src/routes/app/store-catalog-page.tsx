import { productMetadata } from "@/features/catalog/product-label";
import { useQuery } from "@tanstack/react-query";
import { useEffect, useState } from "react";
import { Link, useParams } from "react-router-dom";
import { PageHeading, Pagination, StatusBadge } from "@/components/page/page-elements";
import { storeStatus } from "@/features/catalog/store-status";
import { EmptyState, ErrorState, LoadingState } from "@/components/ui/feedback";
import { TextField } from "@/components/ui/form-field";
import { catalogApi } from "@/services/gomo-api";
import { PriceDetails } from "@/features/catalog/price-details";

export function StoreCatalogPage() {
  const { id = "" } = useParams();
  const [search, setSearch] = useState("");
  const [query, setQuery] = useState("");
  const [page, setPage] = useState(0);
  useEffect(() => {
    const timer = window.setTimeout(() => setQuery(search.trim()), 300);
    return () => window.clearTimeout(timer);
  }, [search]);
  const store = useQuery({
    queryKey: ["store", id],
    queryFn: () => catalogApi.store(id),
  });
  const catalog = useQuery({
    queryKey: ["store-products", id, query, page],
    queryFn: () => catalogApi.storeProducts(id, query, page),
  });
  const status = store.data ? storeStatus(store.data) : null;
  return (
    <>
      <Link
        to="/app/supermercados"
        className="mb-4 inline-block text-sm font-semibold text-muted hover:text-primary"
      >
        ← Voltar aos supermercados
      </Link>
      <PageHeading
        title={store.data?.name ?? "Catálogo do mercado"}
        description={
          store.data?.address ?? "Produtos e preços deste mercado."
        }
      />
      {status &&
      (store.data?.priceSourceNote || store.data?.priceStatus !== "CURRENT") ? (
        <div className="surface mb-5 p-5 text-sm text-muted">
          <StatusBadge tone={status.tone}>{status.label}</StatusBadge>
          <p className="mt-2">{status.help}</p>
        </div>
      ) : null}
      {store.isError ? <ErrorState retry={() => void store.refetch()} /> : null}
      <div className="surface mb-5 p-5">
        <TextField
          id="store-product-search"
          label="Buscar produtos deste mercado"
          placeholder="Nome, marca ou código de barras"
          value={search}
          onChange={(event) => {
            setSearch(event.target.value);
            setPage(0);
          }}
        />
        <p className="mt-3 text-sm text-muted">
          {catalog.data?.totalElements.toLocaleString("pt-BR") ?? "…"}{" "}
          produto(s). Cada um mostra quando o preço foi visto e se está
          disponível.
        </p>
      </div>
      {catalog.isLoading || search.trim() !== query ? (
        <LoadingState />
      ) : catalog.isError ? (
        <ErrorState
          message={catalog.error.message}
          retry={() => void catalog.refetch()}
        />
      ) : !catalog.data?.content.length ? (
        <EmptyState
          title="Nenhum produto encontrado"
          description="Tente outro nome ou código. Os resultados incluem apenas produtos observados nesta loja."
        />
      ) : (
        <>
          <div className="grid gap-4 md:grid-cols-2 xl:grid-cols-3">
            {catalog.data.content.map(({ product, price }) => (
              <article key={product.id} className="surface min-w-0 p-5">
                <h2 className="font-bold">
                  <Link
                    to={`/app/produtos/${product.id}`}
                    className="hover:text-primary"
                  >
                    {product.name}
                  </Link>
                </h2>
                <p className="mt-2 text-xs text-muted">
                  {productMetadata(product)}
                </p>
                <PriceDetails price={price} />
              </article>
            ))}
          </div>
          <Pagination
            page={page}
            totalPages={catalog.data.totalPages}
            onChange={setPage}
          />
        </>
      )}
    </>
  );
}
