import { zodResolver } from "@hookform/resolvers/zod";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { ListChecks, Plus, Trash2 } from "lucide-react";
import { useState } from "react";
import { useForm } from "react-hook-form";
import { Link, useNavigate } from "react-router-dom";
import { z } from "zod";
import { ConfirmDialog } from "@/components/ui/confirm-dialog";
import { EmptyState, ErrorState, InlineError, SkeletonRows } from "@/components/ui/feedback";
import { SelectField, TextField } from "@/components/ui/form-field";
import { NativeButton } from "@/components/ui/native-button";
import { PageHeading, Pagination, StatusBadge } from "@/components/page/page-elements";
import { ApiError } from "@/lib/api";
import { formatDate } from "@/lib/brand";
import { shoppingListApi } from "@/services/gomo-api";
import type { ShoppingListSummary } from "@/types/api";
import { PlanLimitNotice } from "@/features/plan/plan-locks";
import { useLimitUpsell } from "@/features/plan/use-limit-upsell";
import { planLimitOf, usePlan } from "@/features/plan/use-plan";

const schema = z.object({
  name: z.string().trim().min(1, "Informe o nome da lista.").max(120),
  shoppingType: z.enum(["DAILY", "WEEKLY", "MONTHLY", "CUSTOM"]),
});
type Values = z.infer<typeof schema>;

export function ShoppingListsPage() {
  const queryClient = useQueryClient();
  const navigate = useNavigate();
  const [page, setPage] = useState(0);
  const [showForm, setShowForm] = useState(false);
  const [deleteTarget, setDeleteTarget] = useState<ShoppingListSummary | null>(null);
  const [apiError, setApiError] = useState<string | null>(null);
  const lists = useQuery({ queryKey: ["shopping-lists", page], queryFn: () => shoppingListApi.list(page, 20) });
  const { premium } = usePlan();
  const listLimit = useLimitUpsell();
  // The free plan keeps one list; trying to start a second one is the moment to explain Premium.
  const reachedListLimit = !premium && (lists.data?.totalElements ?? 0) >= 1;
  const openNewListForm = () => {
    if (reachedListLimit) {
      setShowForm(false);
      listLimit.block("SHOPPING_LISTS");
      return;
    }
    listLimit.clear();
    setShowForm((value) => !value);
  };
  const { register, handleSubmit, reset, formState: { errors, isSubmitting } } = useForm<Values>({ resolver: zodResolver(schema), defaultValues: { shoppingType: "CUSTOM" } });
  const removeList = useMutation({ mutationFn: shoppingListApi.remove, onSuccess: async () => { setDeleteTarget(null); await queryClient.invalidateQueries({ queryKey: ["shopping-lists"] }); } });

  const createList = handleSubmit(async (values) => {
    setApiError(null);
    try { const created = await shoppingListApi.create(values); reset(); setShowForm(false); await queryClient.invalidateQueries({ queryKey: ["shopping-lists"] }); navigate(`/app/listas/${created.id}`); } catch (error) {
      if (planLimitOf(error) === "SHOPPING_LISTS") { setShowForm(false); listLimit.block("SHOPPING_LISTS"); return; }
      setApiError(error instanceof ApiError ? error.message : "Não foi possível criar a lista.");
    }
  });

  return (
    <>
      <PageHeading title="Minhas listas" description="Crie listas, organize quantidades e compare a compra completa." action={<NativeButton onClick={openNewListForm}><Plus className="size-4" aria-hidden />Nova lista</NativeButton>} />
      {listLimit.notice ? <div className="mb-5"><PlanLimitNotice>{listLimit.notice}</PlanLimitNotice></div> : null}
      {showForm ? <form onSubmit={createList} className="surface mb-5 grid gap-4 p-4 sm:grid-cols-[1fr_0.7fr_auto] sm:items-end sm:p-5" noValidate><TextField id="list-name" label="Nome da lista" placeholder="Ex.: Compra da semana" error={errors.name?.message} {...register("name")} /><SelectField id="list-type" label="Frequência" error={errors.shoppingType?.message} {...register("shoppingType")}><option value="CUSTOM">Personalizada</option><option value="DAILY">Diária</option><option value="WEEKLY">Semanal</option><option value="MONTHLY">Mensal</option></SelectField><NativeButton type="submit" loading={isSubmitting}>Criar lista</NativeButton><div className="sm:col-span-3"><InlineError>{apiError}</InlineError></div></form> : null}
      {lists.isLoading ? <SkeletonRows rows={6} /> : lists.isError ? <ErrorState retry={() => void lists.refetch()} /> : !lists.data?.content.length ? <EmptyState title="Nenhuma lista criada" description="Crie sua primeira lista para adicionar produtos e comparar supermercados." action={<NativeButton onClick={() => setShowForm(true)}>Criar primeira lista</NativeButton>} /> : <><div className="grid gap-4 md:grid-cols-2 xl:grid-cols-3">{lists.data.content.map((list) => <article key={list.id} className="surface p-5"><div className="flex items-start justify-between gap-4"><span className="grid size-10 place-items-center rounded-lg bg-primary-soft text-primary"><ListChecks className="size-5" aria-hidden /></span><button type="button" className="icon-button" aria-label={`Excluir lista ${list.name}`} onClick={() => setDeleteTarget(list)}><Trash2 className="size-4" aria-hidden /></button></div><h2 className="mt-4 truncate font-bold">{list.name}</h2><div className="mt-3"><StatusBadge>{list.shoppingType === "DAILY" ? "Diária" : list.shoppingType === "WEEKLY" ? "Semanal" : list.shoppingType === "MONTHLY" ? "Mensal" : "Personalizada"}</StatusBadge></div><p className="mt-4 text-xs text-muted">Atualizada em {formatDate(list.updatedAt)}</p><Link to={`/app/listas/${list.id}`} className="mt-5 inline-flex min-h-10 items-center font-bold text-primary hover:underline">Abrir lista</Link></article>)}</div><Pagination page={lists.data.page} totalPages={lists.data.totalPages} onChange={setPage} /></>}
      <ConfirmDialog open={Boolean(deleteTarget)} onOpenChange={(open) => !open && setDeleteTarget(null)} title="Excluir lista?" description={`A lista “${deleteTarget?.name ?? ""}” e todos os seus itens serão removidos. Esta ação não pode ser desfeita.`} confirmLabel="Excluir lista" loading={removeList.isPending} error={removeList.error?.message} onConfirm={() => deleteTarget && removeList.mutate(deleteTarget.id)} />
    </>
  );
}
