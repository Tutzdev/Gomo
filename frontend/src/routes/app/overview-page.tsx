import { useQuery } from "@tanstack/react-query";
import { Bell, CircleDollarSign, ListChecks, MapPin, Search, Store } from "lucide-react";
import { Link } from "react-router-dom";
import { EmptyState, ErrorState, SkeletonRows } from "@/components/ui/feedback";
import { NativeButton } from "@/components/ui/native-button";
import { PageHeading, SurfaceTitle } from "@/components/page/page-elements";
import { alertApi, catalogApi, preferenceApi, shoppingListApi } from "@/services/gomo-api";
import { formatDate } from "@/lib/brand";

export function OverviewPage() {
  const lists = useQuery({ queryKey: ["shopping-lists", 0], queryFn: () => shoppingListApi.list(0, 4) });
  const alerts = useQuery({ queryKey: ["alerts", 0], queryFn: () => alertApi.list(0, 4) });
  const notifications = useQuery({ queryKey: ["notifications", 0], queryFn: () => alertApi.notifications(0, 4) });
  const preferences = useQuery({ queryKey: ["preferences"], queryFn: preferenceApi.get });
  const cities = useQuery({ queryKey: ["cities", "all"], queryFn: () => catalogApi.cities(undefined, 0, 100) });

  const preferredCity = cities.data?.content.find((city) => city.id === preferences.data?.preferredCityId);
  const hasError = lists.isError || alerts.isError || notifications.isError || preferences.isError;

  return (
    <>
      <PageHeading title="Início" description="Suas listas, alertas e preferências em um só lugar." action={<NativeButton to="/app/produtos"><Search className="size-4" aria-hidden />Buscar preços</NativeButton>} />

      {hasError ? <ErrorState message="Parte das informações não pôde ser carregada." retry={() => void Promise.all([lists.refetch(), alerts.refetch(), notifications.refetch(), preferences.refetch()])} /> : null}

      <section aria-label="Resumo da conta" className="grid gap-4 sm:grid-cols-2 xl:grid-cols-4">
        <SummaryItem icon={ListChecks} label="Listas salvas" value={lists.data ? String(lists.data.totalElements) : "—"} loading={lists.isLoading} to="/app/listas" />
        <SummaryItem icon={Bell} label="Alertas ativos" value={alerts.data ? String(alerts.data.content.filter((item) => item.active).length) : "—"} loading={alerts.isLoading} to="/app/alertas" />
        <SummaryItem icon={CircleDollarSign} label="Notificações não lidas" value={notifications.data ? String(notifications.data.content.filter((item) => !item.readAt).length) : "—"} loading={notifications.isLoading} to="/app/notificacoes" />
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
