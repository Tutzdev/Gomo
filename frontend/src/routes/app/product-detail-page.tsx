import { useQuery } from "@tanstack/react-query";
import { ArrowLeft } from "lucide-react";
import { useState } from "react";
import { Link, useParams } from "react-router-dom";
import { ErrorState, LoadingState, EmptyState } from "@/components/ui/feedback";
import { SelectField } from "@/components/ui/form-field";
import { PageHeading } from "@/components/page/page-elements";
import { catalogApi } from "@/services/gomo-api";
import { formatCurrency, formatDate } from "@/lib/brand";
import { AddToList } from "@/features/catalog/add-to-list";
import { ProductImage } from "@/features/catalog/product-image";
import { ProductOffersPanel } from "@/features/catalog/product-offers-panel";
import { LockedPriceHistory } from "@/features/plan/price-history-panel";
import { usePlan } from "@/features/plan/use-plan";

export function ProductDetailPage() {
  const { id = "" } = useParams();
  const [storeId, setStoreId] = useState("");
  const { premium } = usePlan();
  const product = useQuery({
    queryKey: ["product", id],
    queryFn: () => catalogApi.product(id),
    enabled: Boolean(id),
  });
  const stores = useQuery({
    queryKey: ["stores", "all"],
    queryFn: () => catalogApi.stores(undefined, 0, 100),
  });
  const history = useQuery({
    queryKey: ["price-history", id, storeId],
    queryFn: () => catalogApi.priceHistory(id, storeId),
    enabled: Boolean(premium && id && storeId),
  });

  if (product.isLoading) return <LoadingState />;
  if (product.isError || !product.data)
    return <ErrorState retry={() => void product.refetch()} />;

  return (
    <>
      <Link
        to="/app/produtos"
        className="mb-4 inline-flex items-center gap-2 text-sm font-semibold text-muted hover:text-foreground"
      >
        <ArrowLeft className="size-4" aria-hidden />
        Voltar aos produtos
      </Link>
      <div className="mb-4 flex items-start gap-4">
        <ProductImage product={product.data} large />
        <div className="min-w-0 flex-1">
          <PageHeading
            title={product.data.name}
            description={
              [
                product.data.brand,
                product.data.packageDescription,
                product.data.category,
              ]
                .filter(Boolean)
                .join(" • ") || "Produto do catálogo Gomo"
            }
            action={<AddToList product={product.data} />}
          />
        </div>
      </div>
      <ProductOffersPanel productId={id} />
      <div className="grid gap-6 lg:grid-cols-[0.75fr_1.25fr]">
        <section className="surface p-5">
          <h2 className="font-bold">Detalhes</h2>
          <dl className="mt-5 space-y-4 text-sm">
            {[
              ["Marca", product.data.brand],
              ["Categoria", product.data.category],
              ["Unidade", product.data.unit],
              ["Quantidade", product.data.quantity],
              ["Atualizado em", formatDate(product.data.updatedAt)],
            ].map(([term, value]) => (
              <div
                key={String(term)}
                className="flex justify-between gap-5 border-b border-border pb-3"
              >
                <dt className="text-muted">{term}</dt>
                <dd className="text-right font-semibold">
                  {value ?? "Não informado"}
                </dd>
              </div>
            ))}
          </dl>
          {product.data.description ? (
            <p className="mt-5 text-sm leading-6 text-muted">
              {product.data.description}
            </p>
          ) : null}
        </section>
        {!premium ? (
          <section className="surface overflow-hidden">
            <div className="border-b border-border p-5">
              <h2 className="font-bold">Histórico de preços</h2>
              <p className="mt-1 text-sm text-muted">Observações dos últimos 90 dias em cada loja.</p>
            </div>
            <LockedPriceHistory />
          </section>
        ) : (
          <section className="surface p-5">
            <h2 className="font-bold">Histórico de preços</h2>
            <p className="mt-1 text-sm text-muted">
              Escolha uma loja para consultar as observações dos últimos 90 dias.
            </p>
            <div className="mt-5 max-w-md">
              <SelectField
                id="history-store"
                label="Supermercado"
                value={storeId}
                onChange={(event) => setStoreId(event.target.value)}
              >
                <option value="">Selecione uma loja</option>
                {stores.data?.content.map((store) => (
                  <option key={store.id} value={store.id}>
                    {store.name}
                  </option>
                ))}
              </SelectField>
            </div>
            <div className="mt-6">
              {!storeId ? (
                <EmptyState
                  title="Selecione uma loja"
                  description="O histórico depende do produto e da loja escolhidos."
                />
              ) : history.isLoading ? (
                <LoadingState label="Carregando histórico…" />
              ) : history.isError ? (
                <ErrorState retry={() => void history.refetch()} />
              ) : !history.data?.content.length ? (
                <EmptyState
                  title="Sem histórico disponível"
                  description="Ainda não há observações para esta combinação."
                />
              ) : (
                <div className="overflow-x-auto">
                  <table className="data-table min-w-[42rem]">
                    <caption className="sr-only">
                      Histórico de preços do produto
                    </caption>
                    <thead>
                      <tr>
                        <th>Data</th>
                        <th>Preço regular</th>
                        <th>Promoção</th>
                        <th>Disponibilidade</th>
                        <th>Origem</th>
                      </tr>
                    </thead>
                    <tbody>
                      {history.data.content.map((record) => (
                        <tr key={record.id}>
                          <td>{formatDate(record.collectedAt)}</td>
                          <td>{formatCurrency(record.regularPrice)}</td>
                          <td>
                            {record.promotionalPrice
                              ? formatCurrency(record.promotionalPrice)
                              : "—"}
                          </td>
                          <td>
                            {record.availability === "AVAILABLE"
                              ? "Em estoque"
                              : record.availability === "UNAVAILABLE"
                                ? "Sem estoque"
                                : "Desconhecida"}
                          </td>
                          <td>
                            {record.originType === "USER_CONTRIBUTION"
                              ? "Comunidade"
                              : "Fonte cadastrada"}
                          </td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </div>
              )}
            </div>
          </section>
        )}
      </div>
    </>
  );
}
