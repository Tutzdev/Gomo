import { useQuery } from "@tanstack/react-query";
import { Bell, CircleDollarSign, ListChecks, MapPin, PiggyBank, Search, Store } from "lucide-react";
import { Link } from "react-router-dom";
import { EmptyState, ErrorState, SkeletonRows } from "@/components/ui/feedback";
import { NativeButton } from "@/components/ui/native-button";
import { PageHeading, SurfaceTitle } from "@/components/page/page-elements";
import { alertApi, catalogApi, preferenceApi, savingsApi, shoppingListApi } from "@/services/gomo-api";
import { monthName, recentMonths } from "@/features/savings/savings-month";
import { RecurringListsCard } from "@/features/shopping-lists/recurring-lists-card";
import { useDefaultCityId } from "@/lib/use-default-city";
import { formatCurrency, formatDate } from "@/lib/brand";

export function OverviewPage() {
  // Each key holds exactly what its query fetched: the recent lists are a short page of their own, while the
  // alerts are the same page the alerts screen loads, so the count of active alerts is the real one.
  const lists = useQuery({ queryKey: ["shopping-lists", "recent"], queryFn: () => shoppingListApi.list(0, 4) });
  const alerts = useQuery({ queryKey: ["alerts", 0], queryFn: () => alertApi.list(0, 100) });
  const unread = useQuery({ queryKey: ["notifications", "unread"], queryFn: alertApi.unreadNotifications });
  const preferences = useQuery({ queryKey: ["preferences"], queryFn: preferenceApi.get });
  const cities = useQuery({ queryKey: ["cities", "all"], queryFn: () => catalogApi.cities(undefined, 0, 100) });
  const defaultCityId = useDefaultCityId();

  const preferredCity = cities.data?.content.find((city) => city.id === preferences.data?.preferredCityId);
  const hasError = lists.isError || alerts.isError || unread.isError || preferences.isError;

  return (
    <>
      <PageHeading title="Início" description="Suas listas, alertas e preferências em um só lugar." action={<NativeButton to="/app/produtos"><Search className="size-4" aria-hidden />Buscar preços</NativeButton>} />

      {hasError ? <ErrorState message="Parte das informações não pôde ser carregada." retry={() => void Promise.all([lists.refetch(), alerts.refetch(), unread.refetch(), preferences.refetch()])} /> : null}

      <SavingsHighlight />
      {defaultCityId ? <RecurringListsCard cityId={defaultCityId} /> : null}

      <section aria-label="Resumo da conta" className="grid gap-4 sm:grid-cols-2 xl:grid-cols-4">
        <SummaryItem icon={ListChecks} label="Listas salvas" value={lists.data ? String(lists.data.totalElements) : "—"} loading={lists.isLoading} to="/app/listas" />
        <SummaryItem icon={Bell} label="Alertas ativos" value={alerts.data ? String(alerts.data.content.filter((item) => item.active).length) : "—"} loading={alerts.isLoading} to="/app/alertas" />
        <SummaryItem icon={CircleDollarSign} label="Notificações não lidas" value={unread.data ? String(unread.data.unread) : "—"} loading={unread.isLoading} to="/app/notificacoes" />
        <SummaryItem icon={MapPin} label="Cidade preferida" value={preferredCity?.name ?? (preferences.isLoading ? "—" : "Não definida")} loading={preferences.isLoading || cities.isLoading} to="/app/perfil" />
      </section>

      <div className="mt-6 grid gap-6 xl:grid-cols-2">
        <section className="surface overflow-hidden" aria-labelledby="recent-lists-title">
          <SurfaceTitle title="Listas recentes" description="Atualizadas mais recentemente." action={<Link className="text-sm font-bold text-primary hover:underline" to="/app/listas">Ver listas</Link>} />
          <div className="p-4 sm:p-5">
            {lists.isLoading ? <SkeletonRows rows={3} /> : lists.data?.content.length ? (
              <ul className="divide-y divide-border">
                {lists.data.content.map((list) => (
                  <li key={list.id}><Link to={`/app/listas/${list.id}`} className="flex items-center justify-between gap-4 rounded-lg px-2 py-3 hover:bg-[#fafafa] focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-focus"><div className="min-w-0"><p className="truncate font-semibold">{list.name}</p><p className="mt-1 text-xs text-muted">Atualizada em {formatDate(list.updatedAt)}</p></div><span className="text-sm font-semibold text-primary">Abrir</span></Link></li>
                ))}
              </ul>
            ) : <EmptyState title="Nenhuma lista ainda" description="Crie uma lista para reunir os produtos da próxima compra." action={<NativeButton to="/app/listas" size="sm">Criar lista</NativeButton>} />}
          </div>
        </section>

        <section className="surface overflow-hidden" aria-labelledby="favorites-title">
          <SurfaceTitle title="Preferências" description="Sua cidade e seus mercados favoritos." action={<Link className="text-sm font-bold text-primary hover:underline" to="/app/perfil">Editar</Link>} />
          <div className="grid gap-4 p-4 sm:grid-cols-2 sm:p-5">
            <div className="anim-trigger rounded-lg border border-border p-4"><MapPin className="size-5 text-primary" aria-hidden /><p className="mt-4 text-xs font-bold uppercase tracking-wide text-muted">Cidade preferida</p><p className="mt-1 font-semibold">{preferredCity?.name ?? "Não definida"}</p></div>
            <div className="anim-trigger rounded-lg border border-border p-4"><Store className="size-5 text-primary" aria-hidden /><p className="mt-4 text-xs font-bold uppercase tracking-wide text-muted">Lojas favoritas</p><p className="mt-1 font-semibold">{preferences.data ? preferences.data.favoriteStoreIds.length : "—"}</p></div>
          </div>
        </section>
      </div>
    </>
  );
}

function SummaryItem({ icon: Icon, label, value, loading, to }: { icon: typeof ListChecks; label: string; value: string; loading: boolean; to: string }) {
  return (
    <Link to={to} className="surface flex min-h-28 items-center gap-4 p-4 transition-colors hover:border-primary/30 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-focus">
      <span className="grid size-10 shrink-0 place-items-center rounded-lg bg-primary-soft text-primary"><Icon className="size-5" aria-hidden /></span>
      <div className="min-w-0"><p className="text-sm text-muted">{label}</p><p className="mt-1 truncate text-xl font-extrabold">{loading ? "…" : value}</p></div>
    </Link>
  );
}

/** "Em outubro, o Gomo te fez economizar R$ 83": the month so far, or how to start counting. */
function SavingsHighlight() {
  const [month] = recentMonths(1);
  const report = useQuery({ queryKey: ["savings", month], queryFn: () => savingsApi.month(month) });
  if (!report.data) return null;
  const { purchases, savings } = report.data;

  return (
    <Link
      to="/app/economia"
      className="surface mb-4 flex items-center gap-4 border-success/25 bg-success-soft p-4 transition-colors hover:border-success/50 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-focus sm:p-5"
    >
      <span className="grid size-11 shrink-0 place-items-center rounded-lg bg-white text-success"><PiggyBank className="size-6" aria-hidden /></span>
      <div className="min-w-0">
        {purchases === 0 ? (
          <>
            <p className="font-bold">Quanto o Gomo te faz economizar?</p>
            <p className="mt-0.5 text-sm text-muted">Use o modo “No mercado” da sua lista e toque em “Terminei a compra”: a economia do mês aparece aqui.</p>
          </>
        ) : (
          <>
            <p className="text-lg font-extrabold">
              {savings >= 0
                ? `Em ${monthName(month)}, o Gomo te fez economizar ${formatCurrency(savings)}`
                : `Em ${monthName(month)}, suas compras saíram ${formatCurrency(-savings)} acima da média`}
            </p>
            <p className="mt-0.5 text-sm text-muted">{purchases} {purchases === 1 ? "compra registrada" : "compras registradas"}, comparadas com a média dos mercados da cidade.</p>
          </>
        )}
      </div>
    </Link>
  );
}
