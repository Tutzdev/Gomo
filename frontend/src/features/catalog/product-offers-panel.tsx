import { useQuery } from "@tanstack/react-query";
import { useSearchParams } from "react-router-dom";
import { useState } from "react";
import { ErrorState, LoadingState } from "@/components/ui/feedback";
import { SelectField } from "@/components/ui/form-field";
import { catalogApi } from "@/services/gomo-api";
import { ComparisonStoreFilter } from "./comparison-store-filter";
import { PossibleProductMatches } from "./possible-product-matches";
import { StoreOffers } from "./store-offers";

export function ProductOffersPanel({ productId }: { productId: string }) {
  const [params] = useSearchParams();
  const [storeIds, setStoreIds] = useState<string[]>(
    () => params.get("storeIds")?.split(",").filter(Boolean) ?? [],
  );
  const [selectedCity, setSelectedCity] = useState(
    () => params.get("cityId") ?? "",
  );
  const cities = useQuery({
    queryKey: ["cities", "product-offers"],
    queryFn: () => catalogApi.cities(),
  });
  const cityId =
    selectedCity ||
    cities.data?.content.find((city) => city.name === "Volta Redonda")?.id ||
    "";
  const comparison = useQuery({
    queryKey: ["comparison", productId, cityId, storeIds],
    queryFn: () =>
      catalogApi.compareAllProductOffers(productId, cityId, storeIds),
    enabled: Boolean(cityId),
  });
  return (
    <section className="mb-8" aria-label="Preços por supermercado">
      <div className="mb-5 flex flex-wrap items-end justify-between gap-4">
        <div>
          <h2 className="text-xl font-bold">Compare nas lojas</h2>
          <p className="mt-1 text-sm text-muted">
            Mesma embalagem, com preços e atualização de cada mercado.
          </p>
        </div>
        <div className="w-full sm:w-64">
          <SelectField
            id="product-offers-city"
            label="Cidade"
            value={cityId}
            onChange={(event) => {
              setSelectedCity(event.target.value);
              setStoreIds([]);
            }}
          >
            <option value="">Selecione a cidade</option>
            {cities.data?.content.map((city) => (
              <option key={city.id} value={city.id}>
                {city.name}
              </option>
            ))}
          </SelectField>
        </div>
      </div>
      <div className="mb-5">
        <ComparisonStoreFilter
          cityId={cityId}
          value={storeIds}
          onChange={setStoreIds}
        />
      </div>
      {cities.isError ? (
        <ErrorState retry={() => void cities.refetch()} />
      ) : comparison.isError ? (
        <ErrorState retry={() => void comparison.refetch()} />
      ) : cities.isPending || (cityId && comparison.isPending) ? (
        <LoadingState label="Comparando preços…" />
      ) : comparison.data ? (
        <>
          <StoreOffers stores={comparison.data.stores.content} />
          <PossibleProductMatches products={comparison.data.possibleMatches} cityId={cityId} storeIds={storeIds} />
        </>
      ) : (
        <p className="text-sm text-muted">Selecione a cidade para comparar.</p>
      )}
    </section>
  );
}
