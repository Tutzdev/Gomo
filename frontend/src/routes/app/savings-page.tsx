import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { Lock, PiggyBank, Store, Trash2 } from "lucide-react";
import { useState } from "react";
import { PageHeading, StatusBadge } from "@/components/page/page-elements";
import { ConfirmDialog } from "@/components/ui/confirm-dialog";
import { EmptyState, ErrorState, SkeletonRows } from "@/components/ui/feedback";
import { SelectField } from "@/components/ui/form-field";
import { NativeButton } from "@/components/ui/native-button";
import { useUpsell } from "@/features/plan/upsell-context";
import { monthName, recentMonths } from "@/features/savings/savings-month";
import { formatCurrency, formatDate } from "@/lib/brand";
import { cn } from "@/lib/cn";
import { savingsApi } from "@/services/gomo-api";
import type { Purchase } from "@/types/api";

const MONTHS = recentMonths(6);

/**
 * How much the purchases of a month cost less than the city average. The total is for everyone (it is the best
 * argument for the Premium); purchase by purchase, item by item, is Premium.
 */
export function SavingsPage() {
  const [month, setMonth] = useState(MONTHS[0]);
  const [deleting, setDeleting] = useState<Purchase | null>(null);
  const queryClient = useQueryClient();
  const { showUpsell } = useUpsell();
  const report = useQuery({ queryKey: ["savings", month], queryFn: () => savingsApi.month(month) });
  const remove = useMutation({
    mutationFn: (purchase: Purchase) => savingsApi.deletePurchase(purchase.id),
    onSuccess: async () => {
      setDeleting(null);
      await queryClient.invalidateQueries({ queryKey: ["savings"] });
    },
  });
  const name = monthName(month);

  return (
    <>
      <PageHeading
        title="Minha economia"
        description="Quanto suas compras saíram abaixo da média dos mercados da cidade, mês a mês."
        action={
          <div className="w-48">
            <SelectField id="savings-month" label="Mês" value={month} onChange={(event) => setMonth(event.target.value)}>
              {MONTHS.map((option) => <option key={option} value={option}>{monthName(option)}</option>)}
            </SelectField>
          </div>
        }
      />
      {report.isLoading ? <SkeletonRows rows={4} /> : report.isError ? <ErrorState message={report.error.message} retry={() => void report.refetch()} /> : !report.data?.purchases ? (
        <EmptyState
          title={`Nenhuma compra registrada em ${name}`}
          description="Na comparação da lista, toque em “Ir às compras aqui”. No mercado, marque os itens e toque em “Terminei a compra”: o Gomo compara o que você pagou com a média da cidade."
          action={<NativeButton to="/app/listas">Abrir minhas listas</NativeButton>}
        />
      ) : (
        <div className="space-y-5">
          <section className={cn("surface p-5", report.data.savings >= 0 ? "border-success/25 bg-success-soft" : "")}>
            <p className="flex items-center gap-2 text-sm font-semibold text-muted">
              <PiggyBank className="size-5 text-success" aria-hidden />
              Em {name}
            </p>
            <p className="mt-2 text-3xl font-extrabold tabular-nums">
              {report.data.savings >= 0
                ? `O Gomo te fez economizar ${formatCurrency(report.data.savings)}`
                : `Suas compras saíram ${formatCurrency(-report.data.savings)} acima da média`}
            </p>
            <p className="mt-2 text-sm text-muted">
              {report.data.purchases} {report.data.purchases === 1 ? "compra" : "compras"}, {formatCurrency(report.data.totalPaid)} no total.
              A economia conta os {report.data.comparedItems} itens que tinham preço em pelo menos 2 mercados.
            </p>
          </section>

          {report.data.locked ? (
            <div className="surface flex flex-wrap items-center justify-between gap-4 p-5">
              <p className="text-sm text-muted">Compra por compra, item por item, com o preço que você pagou e a média da cidade.</p>
              <NativeButton variant="secondary" onClick={() => showUpsell("SAVINGS_REPORT", { savings: report.data.savings })}>
                <Lock className="size-4" aria-hidden />
                Ver cada compra no Premium
              </NativeButton>
            </div>
          ) : (
            <ul className="space-y-3">
              {report.data.details.map((purchase) => <PurchaseCard key={purchase.id} purchase={purchase} onDelete={() => setDeleting(purchase)} />)}
            </ul>
          )}
        </div>
      )}
      <ConfirmDialog
        open={deleting !== null}
        onOpenChange={(open) => !open && setDeleting(null)}
        title="Tirar esta compra da economia?"
        description={deleting ? `A compra de ${formatCurrency(deleting.totalPaid)} no ${deleting.storeName} sai do relatório. Use se ela foi registrada por engano.` : ""}
        confirmLabel="Tirar compra"
        loading={remove.isPending}
        error={remove.error?.message}
        onConfirm={() => deleting && remove.mutate(deleting)}
      />
    </>
  );
}

function PurchaseCard({ purchase, onDelete }: { purchase: Purchase; onDelete: () => void }) {
  return (
    <li className="surface overflow-hidden">
      <details>
        <summary className="flex cursor-pointer flex-wrap items-center justify-between gap-3 p-4">
          <div className="min-w-0">
            <p className="flex items-center gap-1.5 font-semibold">
              <Store className="size-4 shrink-0 text-primary" aria-hidden />
              {purchase.storeName}
            </p>
            <p className="text-xs text-muted">{purchase.listName} · {formatDate(purchase.purchasedAt)}</p>
          </div>
          <div className="text-right">
            <p className="font-bold tabular-nums">{formatCurrency(purchase.totalPaid)}</p>
            <StatusBadge tone={purchase.savings >= 0 ? "success" : "warning"}>
              {purchase.savings >= 0 ? `${formatCurrency(purchase.savings)} abaixo da média` : `${formatCurrency(-purchase.savings)} acima da média`}
            </StatusBadge>
          </div>
        </summary>
        <ul className="divide-y divide-border border-t border-border">
          {purchase.items.map((item) => (
            <li key={item.productId} className="flex flex-wrap items-center justify-between gap-2 px-4 py-2.5 text-sm">
              <span className="min-w-0">
                {item.productName} <span className="text-muted">× {item.quantity.toLocaleString("pt-BR")}</span>
                {item.priceSource === "SHELF" ? <span className="ml-1 text-xs text-muted">(preço da gôndola)</span> : null}
              </span>
              <span className="text-right tabular-nums">
                {formatCurrency(item.linePaid)}
                <span className="block text-xs text-muted">
                  {item.cityAveragePrice === null ? "sem outro mercado para comparar" : `média da cidade ${formatCurrency(item.cityAveragePrice)}/un.`}
                </span>
              </span>
            </li>
          ))}
        </ul>
        {purchase.itemsWithoutPrice > 0 ? (
          <p className="border-t border-border px-4 py-2 text-xs text-muted">
            {purchase.itemsWithoutPrice === 1 ? "1 item ficou de fora" : `${purchase.itemsWithoutPrice} itens ficaram de fora`}: sem preço no mercado e sem preço da gôndola.
          </p>
        ) : null}
        <div className="border-t border-border p-3 text-right">
          <NativeButton variant="ghost" size="sm" onClick={onDelete}>
            <Trash2 className="size-4" aria-hidden />
            Registrada por engano
          </NativeButton>
        </div>
      </details>
    </li>
  );
}
