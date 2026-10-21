import { useMutation, useQueries, useQuery, useQueryClient } from "@tanstack/react-query";
import { Plus, ReceiptText } from "lucide-react";
import { useState } from "react";
import { EmptyState, ErrorState, InlineError, SkeletonRows } from "@/components/ui/feedback";
import { SelectField, TextField } from "@/components/ui/form-field";
import { NativeButton } from "@/components/ui/native-button";
import { PageHeading, StatusBadge } from "@/components/page/page-elements";
import { ProductPicker } from "@/features/catalog/product-picker";
import { ApiError } from "@/lib/api";
import { formatCurrency, formatDate } from "@/lib/brand";
import { catalogApi, contributionApi } from "@/services/gomo-api";
import type { StockAvailability } from "@/types/api";

export function ContributionsPage() {
  const queryClient = useQueryClient();
  const [showForm, setShowForm] = useState(false);
  const [form, setForm] = useState({ productId: "", storeId: "", regularPrice: "", promotionalPrice: "", observedAt: new Date().toISOString().slice(0, 16), availability: "AVAILABLE" as StockAvailability });
  const [apiError, setApiError] = useState<string | null>(null);
  const contributions = useQuery({ queryKey: ["contributions", 0], queryFn: () => contributionApi.listMine(0, 100) });
  const stores = useQuery({ queryKey: ["stores", "contributions"], queryFn: () => catalogApi.stores(undefined, 0, 100) });
  const submit = useMutation({ mutationFn: () => contributionApi.submit({ productId: form.productId, storeId: form.storeId, regularPrice: Number(form.regularPrice), promotionalPrice: form.promotionalPrice ? Number(form.promotionalPrice) : undefined, currency: "BRL", observedAt: new Date(form.observedAt).toISOString(), availability: form.availability }), onSuccess: async () => { setShowForm(false); setApiError(null); await queryClient.invalidateQueries({ queryKey: ["contributions"] }); }, onError: (error) => setApiError(error instanceof ApiError ? error.message : "Não foi possível enviar a contribuição.") });
  const productDetails = useQueries({ queries: [...new Set(contributions.data?.content.map((item) => item.productId) ?? [])].map((id) => ({ queryKey: ["product", id], queryFn: () => catalogApi.product(id) })) });
  const productNames = new Map(productDetails.flatMap((result) => result.data ? [[result.data.id, result.data.name]] : []));
  const storeNames = new Map(stores.data?.content.map((store) => [store.id, store.name]));

  return (
    <>
      <PageHeading title="Contribuir com preço" description="Envie um preço encontrado. A informação fica pendente até a moderação administrativa." action={<NativeButton onClick={() => setShowForm((value) => !value)}><Plus className="size-4" aria-hidden />Nova contribuição</NativeButton>} />
      {showForm ? <form className="surface mb-6 grid gap-4 p-4 sm:grid-cols-2 sm:p-5 xl:grid-cols-3" onSubmit={(event) => { event.preventDefault(); submit.mutate(); }}><ProductPicker id="contribution-product" value={form.productId} onChange={(productId) => setForm({ ...form, productId })} /><SelectField id="contribution-store" label="Supermercado" value={form.storeId} onChange={(event) => setForm({ ...form, storeId: event.target.value })} required><option value="">Selecione</option>{stores.data?.content.map((store) => <option key={store.id} value={store.id}>{store.name}</option>)}</SelectField><TextField id="contribution-date" label="Data observada" type="datetime-local" value={form.observedAt} onChange={(event) => setForm({ ...form, observedAt: event.target.value })} required /><TextField id="contribution-regular-price" label="Preço regular" type="number" min="0.01" step="0.01" value={form.regularPrice} onChange={(event) => setForm({ ...form, regularPrice: event.target.value })} required /><TextField id="contribution-promo-price" label="Preço promocional (opcional)" type="number" min="0.01" step="0.01" value={form.promotionalPrice} onChange={(event) => setForm({ ...form, promotionalPrice: event.target.value })} /><SelectField id="contribution-availability" label="Disponibilidade" value={form.availability} onChange={(event) => setForm({ ...form, availability: event.target.value as StockAvailability })}><option value="AVAILABLE">Em estoque</option><option value="UNAVAILABLE">Sem estoque</option><option value="UNKNOWN">Não confirmada</option></SelectField><div className="sm:col-span-2 xl:col-span-3"><InlineError>{apiError}</InlineError><NativeButton type="submit" loading={submit.isPending} disabled={!form.productId || !form.storeId || Number(form.regularPrice) <= 0}>Enviar para moderação</NativeButton></div></form> : null}
      {contributions.isLoading ? <SkeletonRows rows={6} /> : contributions.isError ? <ErrorState retry={() => void contributions.refetch()} /> : !contributions.data?.content.length ? <EmptyState title="Nenhuma contribuição enviada" description="Quando você encontrar um preço, envie para análise da equipe." action={<NativeButton onClick={() => setShowForm(true)}>Enviar contribuição</NativeButton>} /> : <div className="grid gap-4 md:grid-cols-2 xl:grid-cols-3">{contributions.data.content.map((contribution) => <article key={contribution.id} className="surface anim-trigger p-5"><div className="flex items-start justify-between gap-4"><span className="grid size-10 place-items-center rounded-lg bg-primary-soft text-primary"><ReceiptText className="size-5" aria-hidden /></span><StatusBadge tone={contribution.status === "APPROVED" ? "success" : contribution.status === "REJECTED" ? "danger" : "warning"}>{contribution.status === "APPROVED" ? "Aprovada" : contribution.status === "REJECTED" ? "Rejeitada" : "Em análise"}</StatusBadge></div><h2 className="mt-4 font-bold">{productNames.get(contribution.productId) ?? "Produto"}</h2><p className="mt-1 text-sm text-muted">{storeNames.get(contribution.storeId) ?? "Supermercado"}</p><p className="mt-4 text-2xl font-extrabold">{formatCurrency(contribution.promotionalPrice ?? contribution.regularPrice)}</p><p className="mt-2 text-xs text-muted">Enviada em {formatDate(contribution.submittedAt)}</p>{contribution.rejectionReason ? <p className="mt-4 rounded-lg bg-danger-soft p-3 text-sm text-danger">Motivo: {contribution.rejectionReason}</p> : null}</article>)}</div>}
    </>
  );
}
