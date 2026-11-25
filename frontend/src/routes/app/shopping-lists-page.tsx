import { zodResolver } from "@hookform/resolvers/zod";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { ListChecks, Plus, Trash2 } from "lucide-react";
import { useState } from "react";
import { useForm } from "react-hook-form";
import { Link, useNavigate } from "react-router-dom";
import { z } from "zod";
import { ConfirmDialog } from "@/components/ui/confirm-dialog";
import { ErrorState, InlineError, SkeletonRows } from "@/components/ui/feedback";
import { SelectField, TextField } from "@/components/ui/form-field";
import { NativeButton } from "@/components/ui/native-button";
import { PageHeading, Pagination, StatusBadge } from "@/components/page/page-elements";
import { ApiError } from "@/lib/api";
import { formatUpdatedAt } from "@/lib/brand";
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

  const empty = lists.isSuccess && !lists.data.content.length;

  return (
    <>
      <PageHeading
        title="Minhas listas"
        description="Crie listas, organize quantidades e compare a compra completa."
        action={empty ? undefined : (
          <NativeButton onClick={openNewListForm}>
            <Plus className="size-4" aria-hidden />
            Nova lista
          </NativeButton>
        )}
      />

      {listLimit.notice ? <div className="mb-5"><PlanLimitNotice>{listLimit.notice}</PlanLimitNotice></div> : null}

      {showForm || empty ? (
        <form onSubmit={createList} className="surface mb-5 grid gap-4 p-4 sm:p-5" noValidate>
          {empty ? (
            <div>
              <h2 className="text-lg font-bold text-foreground">Crie sua primeira lista</h2>
              <p className="mt-1 text-sm text-muted">Dê um nome e depois adicione os produtos. O Gomo mostra em qual mercado a compra sai mais barata.</p>
            </div>
          ) : null}

          <div className="grid gap-4 sm:grid-cols-[1fr_auto] sm:items-end">
            <TextField
              id="list-name"
              label="Nome da lista"
              placeholder="Ex.: Compra do mês"
              error={errors.name?.message}
              {...register("name")}
            />
            <NativeButton type="submit" loading={isSubmitting}>Criar lista</NativeButton>
          </div>

          <details className="rounded-lg border border-border p-3">
            <summary className="flex min-h-11 cursor-pointer items-center text-sm font-semibold text-foreground">
              Repetir esta lista? (opcional)
            </summary>
            <div className="mt-3 max-w-sm">
              <SelectField
                id="list-type"
                label="Com que frequência você faz esta compra"
                hint="Listas de toda semana ou todo mês aparecem no Início com o total de hoje."
                error={errors.shoppingType?.message}
                {...register("shoppingType")}
              >
                <option value="CUSTOM">Não repetir</option>
                <option value="DAILY">Todo dia</option>
                <option value="WEEKLY">Toda semana</option>
                <option value="MONTHLY">Todo mês</option>
              </SelectField>
            </div>
          </details>

          <InlineError>{apiError}</InlineError>
        </form>
      ) : null}

      {lists.isLoading ? <SkeletonRows rows={6} /> : lists.isError ? <ErrorState retry={() => void lists.refetch()} /> : empty ? null : (
        <>
          <div className="grid gap-4 md:grid-cols-2 xl:grid-cols-3">
            {lists.data?.content.map((list) => (
              <article key={list.id} className="surface anim-trigger p-5">
                <div className="flex items-start justify-between gap-4">
                  <span className="grid size-10 place-items-center rounded-lg bg-primary-soft text-primary">
                    <ListChecks className="size-5" aria-hidden />
                  </span>
                  {list.owned ? (
                    <button
                      type="button"
                      className="icon-button"
                      aria-label={`Excluir lista ${list.name}`}
                      onClick={() => setDeleteTarget(list)}
                    >
                      <Trash2 className="size-4" aria-hidden />
                    </button>
                  ) : null}
                </div>

                <h2 className="mt-4 font-bold">{list.name}</h2>

                <div className="mt-3 flex flex-wrap gap-1.5">
                  {frequencyLabel(list.shoppingType) ? <StatusBadge>{frequencyLabel(list.shoppingType)}</StatusBadge> : null}
                  {!list.owned ? (
                    <StatusBadge tone="neutral">Lista de {list.ownerName ?? "outra pessoa"}</StatusBadge>
                  ) : list.memberCount ? (
                    <StatusBadge tone="success">Compartilhada com {list.memberCount}</StatusBadge>
                  ) : null}
                </div>

                <p className="mt-4 text-xs text-muted">Atualizada {formatUpdatedAt(list.updatedAt)}</p>
                <Link to={`/app/listas/${list.id}`} className="mt-5 inline-flex min-h-11 items-center font-bold text-primary hover:underline">Abrir lista</Link>
              </article>
            ))}
          </div>
          {lists.data ? <Pagination page={lists.data.page} totalPages={lists.data.totalPages} onChange={setPage} /> : null}
        </>
      )}

      <ConfirmDialog
        open={Boolean(deleteTarget)}
        onOpenChange={(open) => !open && setDeleteTarget(null)}
        title="Excluir lista?"
        description={`A lista “${deleteTarget?.name ?? ""}” e todos os seus itens serão removidos. Esta ação não pode ser desfeita.`}
        confirmLabel="Excluir lista"
        loading={removeList.isPending}
        error={removeList.error?.message}
        onConfirm={() => deleteTarget && removeList.mutate(deleteTarget.id)}
      />
    </>
  );
}

function frequencyLabel(type: ShoppingListSummary["shoppingType"]) {
  if (type === "DAILY") {return "Todo dia";}

  if (type === "WEEKLY") {return "Toda semana";}

  if (type === "MONTHLY") {return "Todo mês";}

  return null;
}
