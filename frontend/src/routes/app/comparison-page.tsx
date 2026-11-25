import { useQuery } from "@tanstack/react-query";
import { useState } from "react";
import { ArrowLeft, SlidersHorizontal } from "lucide-react";
import { Link, useNavigate, useParams, useSearchParams } from "react-router-dom";
import { EmptyState, ErrorState } from "@/components/ui/feedback";
import { SelectField } from "@/components/ui/form-field";
import { PageHeading } from "@/components/page/page-elements";
import { CatalogItemComparison } from "@/features/catalog/catalog-item-comparison";
import { CatalogItemPicker } from "@/features/catalog/catalog-item-picker";
import { ComparisonStoreFilter } from "@/features/catalog/comparison-store-filter";
import { catalogApi } from "@/services/gomo-api";
import type { CatalogItem } from "@/types/api";
import { useDefaultCityId } from "@/lib/use-default-city";

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

      {itemId ? (
        <div aria-live="polite">
          <CatalogItemComparison
            itemId={itemId}
            cityId={selectedCity}
            storeIds={storeIds}
            cityName={cityName}
          />
        </div>
      ) : (
        <PageHeading
          title="Preços do produto"
          description="Digite o produto e veja quanto cada mercado cobra agora."
        />
      )}

      <section
        className={itemId ? "surface mt-6 grid gap-4 p-4 sm:p-5" : "surface grid gap-4 p-4 sm:p-5"}
        aria-labelledby={itemId ? "comparison-search-title" : undefined}
      >
        {itemId ? (
          <h2 id="comparison-search-title" className="text-lg font-extrabold">
            Procurar outro produto
          </h2>
        ) : null}
        <CatalogItemPicker
          id="comparison-product"
          label={itemId ? "Nome do produto" : "Buscar produto"}
          cityId={selectedCity}
          storeIds={storeIds}
          value={picked}
          onChange={(item) => {
            setPicked(item);

            if (item) {
              navigate(`/app/comparar/${item.id}`, { replace: Boolean(itemId) });
            }
          }}
          anyBrand={{
            onChoose: (item) => navigate(`/app/basicos/${item.slug}${selectedCity ? `?cityId=${selectedCity}` : ""}`),
          }}
        />
        <details
          className="rounded-lg border border-border"
          open={!itemId}
        >
          <summary className="flex min-h-11 cursor-pointer items-center gap-2 px-4 text-sm font-semibold">
            <SlidersHorizontal className="size-4 text-muted" aria-hidden />
            Mudar cidade ou mercados
            {cityName ? <span className="font-normal text-muted">({cityName})</span> : null}
          </summary>
          <div className="grid gap-4 border-t border-border p-4">
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
            <ComparisonStoreFilter
              cityId={selectedCity}
              value={storeIds}
              onChange={setStoreIds}
            />
          </div>
        </details>
      </section>

      {cities.isError ? <ErrorState retry={() => void cities.refetch()} /> : null}

      {itemId ? null : (
        <div className="mt-6">
          <EmptyState
            title="Qual produto você quer comparar?"
            description="Ex.: “coca cola 2 litros”, “monster”, “leite ninho 380g”."
          />
        </div>
      )}
    </>
  );
}
