import { useQuery } from "@tanstack/react-query";
import { Check, Minus } from "lucide-react";
import { useState } from "react";
import { SurfaceTitle } from "@/components/page/page-elements";
import { ErrorState, SkeletonRows } from "@/components/ui/feedback";
import { SelectField } from "@/components/ui/form-field";
import { useDefaultCityId } from "@/lib/use-default-city";
import { adminApi, catalogApi } from "@/services/gomo-api";

/** Which market has a current price for each everyday item: a gap here is a collector to look at. */
export function BasicItemCoveragePanel() {
  const [cityId, setCityId] = useState("");
  const defaultCityId = useDefaultCityId();
  const selectedCity = cityId || defaultCityId;
  const cities = useQuery({ queryKey: ["cities", "admin"], queryFn: () => catalogApi.cities(undefined, 0, 100) });
  const coverage = useQuery({
    queryKey: ["admin-basic-item-coverage", selectedCity],
    queryFn: () => adminApi.basicItemCoverage(selectedCity),
    enabled: Boolean(selectedCity),
  });

  return (
    <section className="surface overflow-hidden">
      <SurfaceTitle
        title="Cobertura da cesta básica"
        description="Itens de qualquer marca com preço atual em cada mercado. Um buraco aqui costuma ser um setor que o coletor não lê."
      />
      <div className="max-w-xs px-5 pb-4">
        <SelectField id="coverage-city" label="Cidade" value={selectedCity} onChange={(event) => setCityId(event.target.value)}>
          <option value="">Selecione</option>
          {cities.data?.content.map((city) => (
            <option key={city.id} value={city.id}>
              {city.name}
            </option>
          ))}
        </SelectField>
      </div>
      {coverage.isLoading ? (
        <div className="p-5">
          <SkeletonRows rows={6} />
        </div>
      ) : coverage.isError ? (
        <div className="p-5">
          <ErrorState retry={() => void coverage.refetch()} />
        </div>
      ) : coverage.data ? (
        <div className="overflow-x-auto">
          <table className="data-table min-w-[48rem]">
            <caption className="sr-only">Itens básicos por mercado</caption>
            <thead>
              <tr>
                <th>Item</th>
                {coverage.data.stores.map((store) => (
                  <th key={store.id} className="text-center">
                    {store.name}
                    <span className="block text-xs font-normal text-muted">
                      {store.pricedItems}/{coverage.data.items.length}
                    </span>
                  </th>
                ))}
              </tr>
            </thead>
            <tbody>
              {coverage.data.items.map((item) => (
                <tr key={item.slug}>
                  <td>
                    {item.name}
                    <span className="block text-xs text-muted">
                      {item.aisle} · {item.pricedStoreIds.length}/{coverage.data.stores.length}
                    </span>
                  </td>
                  {coverage.data.stores.map((store) => (
                    <td key={store.id} className="text-center">
                      {item.pricedStoreIds.includes(store.id) ? (
                        <Check className="mx-auto size-4 text-success" aria-label="Com preço" />
                      ) : (
                        <Minus className="mx-auto size-4 text-danger" aria-label="Sem preço" />
                      )}
                    </td>
                  ))}
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      ) : null}
    </section>
  );
}
