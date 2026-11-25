import { useQuery } from "@tanstack/react-query";
import { ArrowLeft, SlidersHorizontal } from "lucide-react";
import { useState } from "react";
import { Link, useParams, useSearchParams } from "react-router-dom";
import { ErrorState } from "@/components/ui/feedback";
import { SelectField } from "@/components/ui/form-field";
import { BasicItemComparison } from "@/features/basic-items/basic-item-comparison";
import { ComparisonStoreFilter } from "@/features/catalog/comparison-store-filter";
import { useDefaultCityId } from "@/lib/use-default-city";
import { catalogApi } from "@/services/gomo-api";

export function BasicItemPage() {
  const { slug = "" } = useParams();
  const [searchParams] = useSearchParams();
  const [cityId, setCityId] = useState(searchParams.get("cityId") ?? "");
  const [storeIds, setStoreIds] = useState<string[]>([]);
  const cities = useQuery({
    queryKey: ["cities", "comparison"],
    queryFn: () => catalogApi.cities(undefined, 0, 100),
  });
  const defaultCityId = useDefaultCityId();
  const selectedCity = cityId || defaultCityId;
  const cityName = cities.data?.content.find((city) => city.id === selectedCity)?.name ?? null;

  return (
    <>
      <Link
        to="/app/produtos"
        className="mb-4 inline-flex min-h-11 items-center gap-2 text-sm font-semibold text-muted hover:text-foreground"
      >
        <ArrowLeft className="size-4" aria-hidden />
        Voltar à busca
      </Link>

      <div aria-live="polite">
        <BasicItemComparison
          slug={slug}
          cityId={selectedCity}
          storeIds={storeIds}
          cityName={cityName}
        />
      </div>

      <details className="surface mt-6">
        <summary className="flex min-h-11 cursor-pointer items-center gap-2 px-4 text-sm font-semibold sm:px-5">
          <SlidersHorizontal className="size-4 text-muted" aria-hidden />
          Mudar cidade ou mercados
          {cityName ? <span className="font-normal text-muted">({cityName})</span> : null}
        </summary>
        <div className="grid gap-4 border-t border-border p-4 sm:p-5">
          <SelectField
            id="basic-item-city"
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
          <ComparisonStoreFilter
            cityId={selectedCity}
            value={storeIds}
            onChange={setStoreIds}
          />
        </div>
      </details>

      {cities.isError ? <ErrorState retry={() => void cities.refetch()} /> : null}
    </>
  );
}
