import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { Check, X } from "lucide-react";
import { ErrorState, SkeletonRows } from "@/components/ui/feedback";
import { NativeButton } from "@/components/ui/native-button";
import { SurfaceTitle } from "@/components/page/page-elements";
import { formatCurrency, formatDate } from "@/lib/brand";
import { catalogApi, suggestionApi } from "@/services/gomo-api";

/** Products shoppers miss in the catalog. Approving marks it accepted; the product is registered by hand. */
export function ProductSuggestionsQueue() {
  const queryClient = useQueryClient();
  const queue = useQuery({ queryKey: ["admin-product-suggestions", "PENDING"], queryFn: () => suggestionApi.moderation("PENDING") });
  const stores = useQuery({ queryKey: ["stores", "suggestions"], queryFn: () => catalogApi.stores(undefined, 0, 200) });
  const storeNames = new Map(stores.data?.content.map((store) => [store.id, store.name]));
  const refresh = () => queryClient.invalidateQueries({ queryKey: ["admin-product-suggestions"] });
  const approve = useMutation({ mutationFn: suggestionApi.approve, onSuccess: refresh });
  const reject = useMutation({ mutationFn: (id: string) => suggestionApi.reject(id, "Sugestão recusada pela moderação."), onSuccess: refresh });

  return (
    <section className="surface mt-6 overflow-hidden">
      <SurfaceTitle title="Produtos sugeridos" description="Produtos que alguém não encontrou no Gomo. Aprovar só marca como aceito: o produto é cadastrado depois, na Ingestão." />
      {queue.isLoading ? <div className="p-5"><SkeletonRows rows={3} /></div>
        : queue.isError ? <div className="p-5"><ErrorState retry={() => void queue.refetch()} /></div>
        : !queue.data?.content.length ? <div className="p-8 text-center text-sm text-muted">Não há produtos sugeridos.</div>
        : (
          <div className="overflow-x-auto">
            <table className="data-table min-w-[48rem]">
              <caption className="sr-only">Produtos sugeridos</caption>
              <thead><tr><th>Enviada</th><th>Produto</th><th>Mercado e preço</th><th>Ações</th></tr></thead>
              <tbody>
                {queue.data.content.map((item) => (
                  <tr key={item.id}>
                    <td>{formatDate(item.submittedAt)}</td>
                    <td>
                      <p className="font-semibold">{item.name}</p>
                      <p className="text-xs text-muted">{[item.brand, item.packageSize, item.barcode].filter(Boolean).join(" · ")}</p>
                      {item.note ? <p className="mt-1 text-xs text-muted">{item.note}</p> : null}
                    </td>
                    <td>{item.storeId ? storeNames.get(item.storeId) ?? "Mercado" : "—"}{item.price ? ` · ${formatCurrency(item.price)}` : ""}</td>
                    <td>
                      <div className="flex gap-2">
                        <NativeButton size="sm" loading={approve.isPending && approve.variables === item.id} onClick={() => approve.mutate(item.id)}><Check className="size-4" aria-hidden />Aprovar</NativeButton>
                        <NativeButton size="sm" variant="danger" loading={reject.isPending && reject.variables === item.id} onClick={() => reject.mutate(item.id)}><X className="size-4" aria-hidden />Recusar</NativeButton>
                      </div>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
    </section>
  );
}
