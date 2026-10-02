import { useQuery } from "@tanstack/react-query";
import { useState } from "react";
import { useNavigate, useParams, useSearchParams } from "react-router-dom";
import { EmptyState, ErrorState } from "@/components/ui/feedback";
import { SelectField } from "@/components/ui/form-field";
import { PageHeading } from "@/components/page/page-elements";
import { CatalogItemComparison } from "@/features/catalog/catalog-item-comparison";
import { CatalogItemPicker } from "@/features/catalog/catalog-item-picker";
import { ComparisonStoreFilter } from "@/features/catalog/comparison-store-filter";
import { catalogApi } from "@/services/gomo-api";
import type { CatalogItem } from "@/types/api";
import { useDefaultCityId } from "@/lib/use-default-city";

/** Pick a product by its everyday name and see what every market charges for it right now. */
export function ComparisonPage() {
  const { id: itemId = "" } = useParams();
  const [searchParams] = useSearchParams();
  const navigate = useNavigate();
  const [picked, setPicked] = useState<CatalogItem | null>(null);
  const [cityId, setCityId] = useState(searchParams.get("cityId") ?? "");
  const [storeIds, setStoreIds] = useState<string[]>(
    searchParams.get("storeIds")?.split(",").filter(Boolean) ?? [],
  );
  const cities = useQuery({
    queryKey: ["cities", "comparison"],
    queryFn: () => catalogApi.cities(undefined, 0, 100),
  });
  const defaultCityId = useDefaultCityId();
  const selectedCity = cityId || defaultCityId;

  return (
    <>
      <PageHeading
        title="Comparar preços"
        description="Digite o produto como você falaria no mercado. Mostramos só mercados com preço atual."
      />
      <div className="surface grid gap-4 p-4 sm:p-5 lg:grid-cols-[1.6fr_1fr] lg:items-start">
        <CatalogItemPicker
          id="comparison-product"
          cityId={selectedCity}
          storeIds={storeIds}
          value={picked}
          onChange={(item) => {
            setPicked(item);
            if (item) navigate(`/app/comparar/${item.id}`, { replace: Boolean(itemId) });
          }}
        />
        <SelectField
          id="comparison-city"
          label="Cidade"
          value={selectedCity}
          onChange={(event) => {
            setCityId(event.target.value);
            setStoreIds([]);
          }}
        >
          <option value="">Selecione uma cidade</option>
          {cities.data?.content.map((city) => (
            <option key={city.id} value={city.id}>
              {city.name}
            </option>
          ))}
        </SelectField>
        <div className="lg:col-span-2">
          <ComparisonStoreFilter cityId={selectedCity} value={storeIds} onChange={setStoreIds} />
        </div>
      </div>

      {cities.isError ? <ErrorState retry={() => void cities.refetch()} /> : null}

      <div className="mt-6" aria-live="polite">
        {itemId ? (
          <CatalogItemComparison itemId={itemId} cityId={selectedCity} storeIds={storeIds} />
        ) : (
          <EmptyState
            title="Qual produto você quer comparar?"
            description="Ex.: “coca cola 2 litros”, “monster”, “leite ninho 380g”."
          />
        )}
      </div>
    </>
  );
}
