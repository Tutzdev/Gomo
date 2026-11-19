import { useQuery } from "@tanstack/react-query";
import { ArrowLeft } from "lucide-react";
import { useState } from "react";
import { Link, useParams, useSearchParams } from "react-router-dom";
import { PageHeading } from "@/components/page/page-elements";
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

  return (
    <>
      <Link
        to="/app/produtos"
        className="mb-4 inline-flex items-center gap-2 text-sm font-semibold text-muted hover:text-foreground"
      >
        <ArrowLeft className="size-4" aria-hidden />
        Voltar à busca
      </Link>
      <PageHeading
        title="Qualquer marca"
        description="O mais barato de cada mercado, de qualquer marca, comparado pelo preço por quilo, litro ou unidade."
      />
      <div className="surface grid gap-4 p-4 sm:p-5 lg:grid-cols-[1fr_2fr] lg:items-start">
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
        <ComparisonStoreFilter cityId={selectedCity} value={storeIds} onChange={setStoreIds} />
      </div>
      {cities.isError ? <ErrorState retry={() => void cities.refetch()} /> : null}
      <div className="mt-6" aria-live="polite">
        <BasicItemComparison slug={slug} cityId={selectedCity} storeIds={storeIds} />
      </div>
    </>
  );
}
