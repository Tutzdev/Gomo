import { useQuery } from "@tanstack/react-query";
import { Bell, Check, ListChecks, MapPin, PiggyBank, Search, Store, TrendingDown } from "lucide-react";
import { Link } from "react-router-dom";
import { EmptyState, ErrorState, SkeletonRows } from "@/components/ui/feedback";
import { NativeButton } from "@/components/ui/native-button";
import { PageHeading, SurfaceTitle } from "@/components/page/page-elements";
import { alertApi, catalogApi, preferenceApi, savingsApi, shoppingListApi } from "@/services/gomo-api";
import { monthName, recentMonths } from "@/features/savings/savings-month";
import { RecurringListsCard } from "@/features/shopping-lists/recurring-lists-card";
import { useDefaultCityId } from "@/lib/use-default-city";
import { formatCurrency, formatUpdatedAt } from "@/lib/brand";
import { cn } from "@/lib/cn";

export function OverviewPage() {
  const lists = useQuery({ queryKey: ["shopping-lists", "recent"], queryFn: () => shoppingListApi.list(0, 4) });
  const alerts = useQuery({ queryKey: ["alerts", 0], queryFn: () => alertApi.list(0, 100) });
  const unread = useQuery({ queryKey: ["notifications", "unread"], queryFn: alertApi.unreadNotifications });
  const preferences = useQuery({ queryKey: ["preferences"], queryFn: preferenceApi.get });
  const cities = useQuery({ queryKey: ["cities", "all"], queryFn: () => catalogApi.cities(undefined, 0, 100) });
  const defaultCityId = useDefaultCityId();

  const preferredCity = cities.data?.content.find((city) => city.id === preferences.data?.preferredCityId);
  const hasError = lists.isError || alerts.isError || unread.isError || preferences.isError;
  const listCount = lists.data?.totalElements ?? 0;
  const activeAlerts = alerts.data?.content.filter((item) => item.active).length ?? 0;
  const unreadCount = unread.data?.unread ?? 0;
  const loaded = lists.isSuccess && preferences.isSuccess;
  const hasCity = Boolean(preferences.data?.preferredCityId);
  const newcomer = loaded && (!hasCity || listCount === 0);
  const hasActivity = listCount > 0 || activeAlerts > 0 || unreadCount > 0;

  return (
    <>
      <PageHeading
        title="Início"
        description="Suas listas, alertas e preferências em um só lugar."
        action={(
          <NativeButton to="/app/produtos">
            <Search className="size-4" aria-hidden />
            Buscar preços
          </NativeButton>
        )}
      />

      {hasError ? (
        <ErrorState
          message="Parte das informações não pôde ser carregada."
          retry={() => void Promise.all([lists.refetch(), alerts.refetch(), unread.refetch(), preferences.refetch()])}
        />
      ) : null}

      {newcomer ? (
        <FirstSteps
          hasCity={hasCity}
          hasList={listCount > 0}
        />
      ) : null}

      {listCount > 0 ? <SavingsHighlight /> : null}

      {defaultCityId ? <RecurringListsCard cityId={defaultCityId} /> : null}

      {hasActivity ? (
        <section aria-label="Resumo da conta" className="grid gap-3 sm:grid-cols-3 sm:gap-4">
          <SummaryItem
            icon={ListChecks}
            label="Listas salvas"
            value={String(listCount)}
            loading={lists.isLoading}
            to="/app/listas"
          />
          <SummaryItem
            icon={TrendingDown}
            label="Alertas de preço ligados"
            value={String(activeAlerts)}
            loading={alerts.isLoading}
            to="/app/alertas"
          />
          <SummaryItem
            icon={Bell}
            label="Notificações não lidas"
            value={String(unreadCount)}
            loading={unread.isLoading}
            to="/app/notificacoes"
          />
        </section>
      ) : null}

      <div className="mt-6 grid gap-6 xl:grid-cols-2">
        <section className="surface overflow-hidden" aria-labelledby="recent-lists-title">
          <SurfaceTitle
            title="Listas recentes"
            description="Atualizadas mais recentemente."
            action={<Link className="inline-flex min-h-11 items-center text-sm font-bold text-primary hover:underline" to="/app/listas">Ver listas</Link>}
          />
          <div className="p-4 sm:p-5">
            {lists.isLoading ? <SkeletonRows rows={3} /> : lists.data?.content.length ? (
              <ul className="divide-y divide-border">
                {lists.data.content.map((list) => (
                  <li key={list.id}>
                    <Link
                      to={`/app/listas/${list.id}`}
                      className="flex min-h-14 items-center justify-between gap-4 rounded-lg px-2 py-3 hover:bg-[#fafafa] focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-focus"
                    >
                      <div className="min-w-0">
                        <p className="font-semibold">{list.name}</p>
                        <p className="mt-1 text-xs text-muted">Atualizada {formatUpdatedAt(list.updatedAt)}</p>
                      </div>
                      <span className="text-sm font-semibold text-primary">Abrir</span>
                    </Link>
                  </li>
                ))}
              </ul>
            ) : (
              <EmptyState
                title="Nenhuma lista ainda"
                description="Crie uma lista para reunir os produtos da próxima compra."
                action={<NativeButton to="/app/listas" size="sm">Criar lista</NativeButton>}
              />
            )}
          </div>
        </section>

        <section className="surface overflow-hidden" aria-labelledby="favorites-title">
          <SurfaceTitle
            title="Preferências"
            description="Sua cidade e seus mercados favoritos."
            action={<Link className="inline-flex min-h-11 items-center text-sm font-bold text-primary hover:underline" to="/app/perfil">Editar</Link>}
          />
          <div className="grid gap-4 p-4 sm:grid-cols-2 sm:p-5">
            <div className="anim-trigger rounded-lg border border-border p-4">
              <MapPin className="size-5 text-primary" aria-hidden />
              <p className="mt-4 text-xs font-bold uppercase tracking-wide text-muted">Cidade preferida</p>
              <p className="mt-1 font-semibold">{preferredCity?.name ?? "Não definida"}</p>
            </div>
            <div className="anim-trigger rounded-lg border border-border p-4">
              <Store className="size-5 text-primary" aria-hidden />
              <p className="mt-4 text-xs font-bold uppercase tracking-wide text-muted">Mercados favoritos</p>
              <p className="mt-1 font-semibold">{preferences.data ? preferences.data.favoriteStoreIds.length : "—"}</p>
            </div>
          </div>
        </section>
      </div>
    </>
  );
}

function FirstSteps({ hasCity, hasList }: { hasCity: boolean; hasList: boolean }) {
  const steps = [
    {
      title: "Escolha sua cidade",
      description: "Assim o Gomo mostra os mercados perto de você.",
      done: hasCity,
      action: "Escolher cidade",
      to: "/app/perfil",
    },
    {
      title: "Busque um produto",
      description: "Digite o nome, como você falaria no mercado, e veja o preço em cada mercado.",
      done: false,
      action: "Buscar preços",
      to: "/app/produtos",
    },
    {
      title: "Monte sua lista de compras",
      description: "O Gomo mostra em qual mercado a compra inteira sai mais barata.",
      done: hasList,
      action: "Criar lista",
      to: "/app/listas",
    },
  ];

  const next = steps.findIndex((step) => !step.done);

  return (
    <section className="surface mb-6 p-4 sm:p-6" aria-labelledby="first-steps-title">
      <h2 id="first-steps-title" className="text-xl font-extrabold text-foreground">Comece por aqui</h2>
      <p className="mt-1 text-muted">Três passos para economizar nas compras.</p>

      <ol className="mt-5 grid gap-4 lg:grid-cols-3">
        {steps.map((step, index) => (
          <li
            key={step.title}
            className={cn(
              "flex flex-col gap-3 rounded-xl border p-4",
              step.done ? "border-success/30 bg-success-soft" : "border-border bg-white",
            )}
          >
            <div className="flex items-center gap-3">
              <span
                className={cn(
                  "grid size-9 shrink-0 place-items-center rounded-full text-base font-extrabold",
                  step.done ? "bg-success text-white" : "bg-primary-soft text-primary-dark",
                )}
                aria-hidden
              >
                {step.done ? <Check className="size-5" /> : index + 1}
              </span>
              <h3 className="text-base font-bold text-foreground">
                {step.title}
                {step.done ? <span className="sr-only"> (feito)</span> : null}
              </h3>
            </div>
            <p className="text-sm text-muted">{step.description}</p>
            {step.done ? (
              <p className="mt-auto text-sm font-semibold text-success">Feito</p>
            ) : (
              <NativeButton
                to={step.to}
                variant={index === next ? "primary" : "secondary"}
                className="mt-auto self-start"
              >
                {step.action}
              </NativeButton>
            )}
          </li>
        ))}
      </ol>
    </section>
  );
}

function SummaryItem({ icon: Icon, label, value, loading, to }: { icon: typeof ListChecks; label: string; value: string; loading: boolean; to: string }) {
  return (
    <Link to={to} className="surface flex min-h-20 items-center gap-4 p-4 transition-colors hover:border-primary/30 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-focus active:scale-[0.98] motion-reduce:active:scale-100">
      <span className="grid size-11 shrink-0 place-items-center rounded-lg bg-primary-soft text-primary">
        <Icon className="size-5" aria-hidden />
      </span>
      <div className="min-w-0">
        <p className="text-sm leading-snug text-muted">{label}</p>
        <p className="mt-1 text-2xl font-extrabold">{loading ? "…" : value}</p>
      </div>
    </Link>
  );
}

function SavingsHighlight() {
  const [month] = recentMonths(1);
  const report = useQuery({ queryKey: ["savings", month], queryFn: () => savingsApi.month(month) });

  if (!report.data) {return null;}

  const { purchases, savings } = report.data;

  return (
    <Link
      to="/app/economia"
      className="surface mb-4 flex items-center gap-4 border-success/25 bg-success-soft p-4 transition-colors hover:border-success/50 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-focus sm:p-5"
    >
      <span className="grid size-11 shrink-0 place-items-center rounded-lg bg-white text-success">
        <PiggyBank className="size-6" aria-hidden />
      </span>
      <div className="min-w-0">
        {purchases === 0 ? (
          <>
            <p className="font-bold">Quanto você economizou este mês?</p>
            <p className="mt-0.5 text-sm text-muted">Depois de comprar, abra sua lista e toque em “Terminei a compra”. A economia aparece aqui.</p>
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
