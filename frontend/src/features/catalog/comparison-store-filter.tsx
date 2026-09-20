import { useQuery } from "@tanstack/react-query";
import { ErrorState, LoadingState } from "@/components/ui/feedback";
import { catalogApi } from "@/services/gomo-api";

export function ComparisonStoreFilter({
  cityId,
  value,
  onChange,
}: {
  cityId: string;
  value: string[];
  onChange: (storeIds: string[]) => void;
}) {
  const stores = useQuery({
    queryKey: ["comparison-stores", cityId],
    queryFn: () => catalogApi.allStores(cityId),
    enabled: Boolean(cityId),
  });
  if (!cityId) return null;
  return (
    <fieldset className="min-w-0 rounded-lg border border-border p-4">
      <legend className="px-1 text-sm font-semibold">
        Mercados da comparação
      </legend>
      <label className="flex min-h-11 cursor-pointer items-center gap-3 text-sm">
        <input
          type="checkbox"
          className="size-4 accent-primary"
          checked={value.length === 0}
          onChange={() => onChange([])}
        />
        Todos os mercados
      </label>
      <p className="mb-2 text-xs text-muted" role="status">
        {value.length
          ? `${value.length} mercado(s) selecionado(s).`
          : "Comparando todos. Marque abaixo para comparar somente alguns."}
      </p>
      {stores.isPending ? (
        <LoadingState label="Carregando mercados…" />
      ) : stores.isError ? (
        <ErrorState retry={() => void stores.refetch()} />
      ) : (
        <details>
          <summary className="cursor-pointer py-2 text-sm font-semibold">
            Escolher mercados ({stores.data.length})
          </summary>
          <div className="mt-2 grid gap-x-5 sm:grid-cols-2">
            {stores.data.map((store) => (
              <label
                key={store.id}
                className="flex min-h-11 cursor-pointer items-center gap-3 text-sm"
              >
                <input
                  type="checkbox"
                  className="size-4 shrink-0 accent-primary"
                  checked={value.includes(store.id)}
                  onChange={(event) =>
                    onChange(
                      event.target.checked
                        ? [...value, store.id].sort()
                        : value.filter((id) => id !== store.id),
                    )
                  }
                />
                <span>
                  {store.name}
                  {store.priceSourceNote ? (
                    <span className="block text-xs text-muted">
                      Preços não integrados
                    </span>
                  ) : null}
                </span>
              </label>
            ))}
          </div>
        </details>
      )}
    </fieldset>
  );
}
