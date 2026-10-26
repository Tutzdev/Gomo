import { useQuery } from "@tanstack/react-query";
import { useState } from "react";
import { PageHeading } from "@/components/page/page-elements";
import { SelectField } from "@/components/ui/form-field";
import { DealsBoard } from "@/features/deals/deals-board";
import { useDefaultCityId } from "@/lib/use-default-city";
import { catalogApi } from "@/services/gomo-api";

/** The deals of the city inside the app, open to visitors like the search. */
export function AppDealsPage() {
  const defaultCityId = useDefaultCityId();
  const [cityId, setCityId] = useState("");
  const selectedCityId = cityId || defaultCityId;
  const cities = useQuery({ queryKey: ["cities", "deals"], queryFn: () => catalogApi.cities(undefined, 0, 100) });
  const cityName = cities.data?.content.find((city) => city.id === selectedCityId)?.name ?? null;

  return (
    <>
      <PageHeading
        title="Ofertas da semana"
        description="O que está mais barato que o normal agora, em cada mercado."
        action={
          cities.data && cities.data.content.length > 1 ? (
            <div className="w-56">
              <SelectField id="deals-city" label="Cidade" value={selectedCityId} onChange={(event) => setCityId(event.target.value)}>
                {cities.data.content.map((city) => <option key={city.id} value={city.id}>{city.name}</option>)}
              </SelectField>
            </div>
          ) : undefined
        }
      />
      {selectedCityId ? <DealsBoard key={selectedCityId} cityId={selectedCityId} cityName={cityName} inApp /> : null}
    </>
  );
}
