import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { Heart, MapPin, Store as StoreIcon } from "lucide-react";
import { useState } from "react";
import { EmptyState, ErrorState, SkeletonRows } from "@/components/ui/feedback";
import { SelectField, TextField } from "@/components/ui/form-field";
import { NativeButton } from "@/components/ui/native-button";
import { PageHeading } from "@/components/page/page-elements";
import { catalogApi, preferenceApi } from "@/services/gomo-api";
import { formatDate } from "@/lib/brand";

export function StoresPage() {
  const queryClient = useQueryClient();
  const [cityId, setCityId] = useState("");
  const [search, setSearch] = useState("");
  const cities = useQuery({
    queryKey: ["cities", "stores"],
    queryFn: () => catalogApi.cities(undefined, 0, 100),
  });
  const stores = useQuery({
    queryKey: ["stores", cityId],
    queryFn: () => catalogApi.stores(cityId || undefined, 0, 100),
  });
  const preferences = useQuery({
    queryKey: ["preferences"],
    queryFn: preferenceApi.get,
  });
  const favoriteMutation = useMutation({
    mutationFn: ({
      storeId,
      favorite,
    }: {
      storeId: string;
      favorite: boolean;
    }) =>
      favorite
        ? preferenceApi.removeFavoriteStore(storeId)
        : preferenceApi.addFavoriteStore(storeId),
    onSuccess: (data) => queryClient.setQueryData(["preferences"], data),
  });
  const filtered =
    stores.data?.content.filter((store) =>
      `${store.name} ${store.address ?? ""}`
        .toLowerCase()
        .includes(search.toLowerCase()),
    ) ?? [];

  return (
    <>
      <PageHeading
        title="Supermercados"
        description="Consulte as lojas cadastradas por cidade e mantenha suas favoritas por perto."
      />
      <div className="surface mb-5 grid gap-4 p-4 sm:grid-cols-2 sm:p-5">
        <SelectField
          id="store-city"
          label="Cidade"
          value={cityId}
          onChange={(event) => setCityId(event.target.value)}
        >
          <option value="">Todas as cidades</option>
          {cities.data?.content.map((city) => (
            <option key={city.id} value={city.id}>
              {city.name}
            </option>
          ))}
        </SelectField>
        <TextField
          id="store-search"
          label="Buscar supermercado"
          value={search}
          onChange={(event) => setSearch(event.target.value)}
          placeholder="Nome ou endereço"
        />
      </div>
      {favoriteMutation.isError ? (
        <ErrorState message={favoriteMutation.error.message} />
      ) : null}
      {cities.isLoading || stores.isLoading || preferences.isLoading ? (
        <SkeletonRows rows={6} />
      ) : cities.isError || stores.isError || preferences.isError ? (
        <ErrorState
          message={(cities.error ?? stores.error ?? preferences.error)?.message}
          retry={() =>
            void Promise.all([
              cities.refetch(),
              stores.refetch(),
              preferences.refetch(),
            ])
          }
        />
      ) : !filtered.length ? (
        <EmptyState
          title="Nenhum supermercado encontrado"
          description="Tente outra cidade ou ajuste o termo da busca."
        />
      ) : (
        <div className="grid gap-4 md:grid-cols-2 xl:grid-cols-3">
          {filtered.map((store) => {
            const favorite =
              preferences.data?.favoriteStoreIds.includes(store.id) ?? false;
            return (
              <article key={store.id} className="surface p-5">
                <div className="flex items-start justify-between gap-4">
                  <span className="grid size-10 place-items-center rounded-lg bg-primary-soft text-primary">
                    <StoreIcon className="size-5" aria-hidden />
                  </span>
                  <NativeButton
                    size="icon"
                    variant="ghost"
                    aria-label={
                      favorite
                        ? `Remover ${store.name} dos favoritos`
                        : `Adicionar ${store.name} aos favoritos`
                    }
                    loading={
                      favoriteMutation.isPending &&
                      favoriteMutation.variables?.storeId === store.id
                    }
                    onClick={() =>
                      favoriteMutation.mutate({ storeId: store.id, favorite })
                    }
                  >
                    <Heart
                      className={`size-5 ${favorite ? "fill-primary text-primary" : ""}`}
                      aria-hidden
                    />
                  </NativeButton>
                </div>
                <h2 className="mt-4 font-bold">{store.name}</h2>
                <p className="mt-2 flex items-start gap-2 text-sm leading-6 text-muted">
                  <MapPin className="mt-1 size-4 shrink-0" aria-hidden />
                  {store.address ?? "Endereço não informado"}
                </p>
                <p className="mt-4 text-xs text-muted">
                  {store.priceSourceNote ??
                    (store.lastPriceCollectedAt
                      ? `Última coleta de preços: ${formatDate(store.lastPriceCollectedAt)}`
                      : "Aguardando a primeira coleta de preços")}
                </p>
                <NativeButton
                  className="mt-4"
                  variant="secondary"
                  to={`/app/supermercados/${store.id}`}
                >
                  Ver produtos e preços
                </NativeButton>
              </article>
            );
          })}
        </div>
      )}
    </>
  );
}
