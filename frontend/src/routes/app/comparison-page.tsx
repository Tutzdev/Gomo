import { useQuery } from "@tanstack/react-query";
import { CheckCircle2, Search } from "lucide-react";
import { useMemo, useState } from "react";
import { EmptyState, ErrorState, LoadingState } from "@/components/ui/feedback";
import { SelectField } from "@/components/ui/form-field";
import { NativeButton } from "@/components/ui/native-button";
import { PageHeading } from "@/components/page/page-elements";
import { catalogApi } from "@/services/gomo-api";
import { ProductPicker } from "@/features/catalog/product-picker";
import { ComparisonStoreFilter } from "@/features/catalog/comparison-store-filter";
import { PossibleProductMatches } from "@/features/catalog/possible-product-matches";
import { StoreOffers } from "@/features/catalog/store-offers";
import { formatDate } from "@/lib/brand";

export function ComparisonPage() {
  const [storeIds, setStoreIds] = useState<string[]>([]);
  const [productId, setProductId] = useState("");
  const [cityId, setCityId] = useState("");
  const [submitted, setSubmitted] = useState<{
    productId: string;
    cityId: string;
    storeIds: string[];
  } | null>(null);
  const cities = useQuery({
    queryKey: ["cities", "comparison"],
    queryFn: () => catalogApi.cities(undefined, 0, 100),
  });
  const comparison = useQuery({
    queryKey: ["comparison", submitted],
    queryFn: () =>
      catalogApi.compareAllProductOffers(
        submitted!.productId,
        submitted!.cityId,
        submitted!.storeIds,
      ),
    enabled: Boolean(submitted),
  });
  const sortedStores = useMemo(
    () =>
      comparison.data?.stores.content
        .slice()
        .sort(
          (a, b) =>
            (a.price.unitPrice ?? Number.POSITIVE_INFINITY) -
            (b.price.unitPrice ?? Number.POSITIVE_INFINITY),
        ) ?? [],
    [comparison.data],
  );

  return (
    <>
      <PageHeading
        title="Comparar preços"
        description="Escolha uma cidade e um produto para consultar preços, promoções e disponibilidade por loja."
      />
      <form
        className="surface grid gap-4 p-4 sm:p-5 lg:grid-cols-[1.5fr_1fr_auto] lg:items-start"
        onSubmit={(event) => {
          event.preventDefault();
          if (productId && cityId)
            setSubmitted({ productId, cityId, storeIds });
        }}
      >
        <ProductPicker
          id="comparison-product"
          cityId={cityId}
          storeIds={storeIds}
          value={productId}
          onChange={(id) => {
            setProductId(id);
            setSubmitted(null);
          }}
        />
        <SelectField
          id="comparison-city"
          label="Cidade"
          value={cityId}
          onChange={(event) => {
            setCityId(event.target.value);
            setStoreIds([]);
            setSubmitted(null);
          }}
          required
        >
          <option value="">Selecione uma cidade</option>
          {cities.data?.content.map((city) => (
            <option key={city.id} value={city.id}>
              {city.name}
            </option>
          ))}
        </SelectField>
        <NativeButton
          type="submit"
          className="lg:mt-7"
          disabled={!productId || !cityId}
        >
          <Search className="size-4" aria-hidden />
          Comparar
        </NativeButton>
        <div className="lg:col-span-3">
          <ComparisonStoreFilter
            cityId={cityId}
            value={storeIds}
            onChange={(ids) => {
              setStoreIds(ids);
              setSubmitted(null);
            }}
          />
        </div>
      </form>

      {cities.isError ? (
        <ErrorState retry={() => void cities.refetch()} />
      ) : null}

      <section
        className="mt-6"
        aria-live="polite"
        aria-busy={comparison.isFetching}
      >
        {!submitted ? (
          <EmptyState
            title="Pronto para comparar"
            description="Selecione o produto e a cidade para iniciar uma consulta real."
          />
        ) : comparison.isLoading ? (
          <LoadingState label="Comparando preços…" />
        ) : comparison.isError ? (
          <ErrorState
            message={comparison.error.message}
            retry={() => void comparison.refetch()}
          />
        ) : !sortedStores.length ? (
          <EmptyState
            title="Nenhuma loja disponível"
            description="Não há supermercados ativos para esta cidade."
          />
        ) : (
          <>
            <div className="mb-4 flex flex-wrap items-end justify-between gap-3">
              <div>
                <h2 className="text-xl font-extrabold">
                  {comparison.data?.productName}
                </h2>
                <p className="mt-1 text-sm text-muted">
                  Comparação realizada em{" "}
                  {formatDate(comparison.data?.comparedAt)}
                </p>
              </div>
              <p className="text-sm text-muted">
                {comparison.data?.stores.totalElements} supermercado(s)
              </p>
            </div>
            <StoreOffers stores={sortedStores} />
            <PossibleProductMatches
              products={comparison.data?.possibleMatches ?? []}
              cityId={submitted?.cityId}
              storeIds={submitted?.storeIds}
            />
            <p className="mt-5 flex items-start gap-2 text-sm text-muted">
              <CheckCircle2
                className="mt-0.5 size-4 shrink-0 text-success"
                aria-hidden
              />
              Os resultados exibem a data e não tratam observações ausentes como
              preços válidos.
            </p>
          </>
        )}
      </section>
    </>
  );
}
