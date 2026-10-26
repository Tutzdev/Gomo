import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { Bell, Check, TrendingDown } from "lucide-react";
import { Link } from "react-router-dom";
import { EmptyState, ErrorState, SkeletonRows } from "@/components/ui/feedback";
import { NativeButton } from "@/components/ui/native-button";
import { PageHeading, StatusBadge } from "@/components/page/page-elements";
import { formatCurrency, formatDate } from "@/lib/brand";
import { alertApi } from "@/services/gomo-api";
import type { AlertNotification } from "@/types/api";

export function NotificationsPage() {
  const queryClient = useQueryClient();
  const notifications = useQuery({ queryKey: ["notifications", 0], queryFn: () => alertApi.notifications(0, 100) });
  const markRead = useMutation({ mutationFn: alertApi.markNotificationRead, onSuccess: () => queryClient.invalidateQueries({ queryKey: ["notifications"] }) });

  return (
    <>
      <PageHeading title="Notificações" description="Acompanhe os preços que atingiram seus alertas e marque o que já foi visto." />
      {notifications.isLoading ? <SkeletonRows rows={7} /> : notifications.isError ? <ErrorState retry={() => void notifications.refetch()} /> : !notifications.data?.content.length ? <EmptyState title="Tudo tranquilo por aqui" description="Quando um preço atingir um de seus alertas, a notificação aparecerá nesta página." /> : (
        <div className="surface divide-y divide-border overflow-hidden">
          {notifications.data.content.map((notification) => (
            <article key={notification.id} className={`anim-trigger flex flex-col gap-4 p-4 sm:flex-row sm:items-center sm:justify-between sm:p-5 ${notification.readAt ? "bg-white" : "bg-primary-soft/45"}`}>
              <div className="flex min-w-0 gap-4">
                <span className="grid size-10 shrink-0 place-items-center rounded-lg bg-primary-soft text-primary">
                  {notification.alertKind === "BELOW_AVERAGE" ? <TrendingDown className="size-5" aria-hidden /> : <Bell className="size-5" aria-hidden />}
                </span>
                <div className="min-w-0">
                  <div className="flex flex-wrap items-center gap-2">
                    <h2 className="font-bold">
                      <Link to={`/app/produtos/${notification.productId}`} className="hover:text-primary hover:underline">{notification.productName}</Link>{" "}
                      por {formatCurrency(notification.unitPrice)}
                    </h2>
                    {!notification.readAt ? <StatusBadge tone="warning">Nova</StatusBadge> : null}
                  </div>
                  <p className="mt-1 text-sm text-muted">{notification.storeName} • {formatDate(notification.createdAt)}</p>
                  <p className="mt-1 text-xs text-muted">{alertCondition(notification)}</p>
                </div>
              </div>
              {!notification.readAt ? <NativeButton variant="secondary" size="sm" loading={markRead.isPending && markRead.variables === notification.id} onClick={() => markRead.mutate(notification.id)}><Check className="size-4" aria-hidden />Marcar como lida</NativeButton> : null}
            </article>
          ))}
        </div>
      )}
    </>
  );
}

/** What the person asked for, so the notification says why it arrived. */
function alertCondition(notification: AlertNotification) {
  if (notification.alertKind === "BELOW_AVERAGE") {
    return `Seu alerta: ${notification.discountPercent}% ou mais abaixo da média dos últimos 30 dias`;
  }
  return notification.targetPrice === null ? "Seu alerta de preço" : `Seu alerta: até ${formatCurrency(notification.targetPrice)}`;
}
