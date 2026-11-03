import { useQuery } from "@tanstack/react-query";
import { Bell, Menu, Sparkles } from "lucide-react";
import { useState } from "react";
import { Link, Outlet, useLocation, useNavigate } from "react-router-dom";
import { BrandLogo } from "@/components/brand-logo";
import { DashboardSidebar } from "@/components/layout/dashboard-sidebar";
import { MobileTabBar } from "@/components/layout/mobile-tab-bar";
import { NativeButton } from "@/components/ui/native-button";
import { useAuth } from "@/features/auth/auth-context";
import { UpsellProvider } from "@/features/plan/upsell-provider";
import { alertApi } from "@/services/gomo-api";
import type { User } from "@/types/api";
import { cn } from "@/lib/cn";
import "@/components/ui/animated-icons.css";

const pageNames: Record<string, string> = {
  "/app": "Início",
  "/app/produtos": "Buscar preços",
  "/app/ofertas": "Ofertas da semana",
  "/app/supermercados": "Supermercados",
  "/app/listas": "Minhas listas",
  "/app/economia": "Minha economia",
  "/app/convite": "Convite para lista",
  "/app/alertas": "Alertas de preço",
  "/app/contribuir": "Contribuir com preço",
  "/app/notificacoes": "Notificações",
  "/app/perfil": "Perfil",
  "/app/admin": "Administração",
};

const COLLAPSED_KEY = "gomo.sidebar-collapsed";

export function AppShell() {
  const { user, logout } = useAuth();
  const navigate = useNavigate();
  const location = useLocation();
  const [collapsed, setCollapsed] = useState(() => readCollapsed());
  // "No mercado" has its own bar at the bottom (the running total); the tabs would cover it.
  const showTabBar = !/^\/app\/listas\/[^/]+\/mercado\//.test(location.pathname);

  const toggleCollapsed = () => {
    setCollapsed((current) => {
      const next = !current;
      try {
        window.localStorage.setItem(COLLAPSED_KEY, String(next));
      } catch {
        // Private mode: the choice lasts until the page closes.
      }
      return next;
    });
  };

  // Visitors come back to the page they were on after signing in.
  const returnPath = encodeURIComponent(location.pathname + location.search);

  const handleLogout = async () => {
    await logout();
    navigate("/entrar", { replace: true });
  };

  const currentPage =
    pageNames[location.pathname] ??
    (location.pathname.startsWith("/app/listas/")
      ? "Detalhes da lista"
      : location.pathname.startsWith("/app/comparar/") || location.pathname.startsWith("/app/produtos/")
        ? "Preços do produto"
        : location.pathname.startsWith("/app/supermercados/")
          ? "Produtos do mercado"
          : "Gomo");

  return (
    <UpsellProvider>
      <div className="app-shell motion-icons min-h-screen bg-[#f7f7f8]">
        <a href="#conteudo-principal" className="skip-link">Pular para o conteúdo</a>

        <aside
          className={cn(
            "fixed inset-y-0 left-0 z-40 hidden bg-sidebar transition-[width] duration-200 lg:block motion-reduce:transition-none",
            collapsed ? "w-20" : "w-64",
          )}
        >
          <DashboardSidebar
            collapsed={collapsed}
            role={user?.role ?? null}
            returnPath={returnPath}
            onToggle={toggleCollapsed}
            onLogout={() => void handleLogout()}
          />
        </aside>

        <div className={cn("min-h-screen transition-[padding] duration-200 motion-reduce:transition-none", collapsed ? "lg:pl-20" : "lg:pl-64")}>
          <header className="sticky top-0 z-30 flex h-14 items-center justify-between gap-3 border-b border-border bg-white/95 px-4 pt-[env(safe-area-inset-top)] backdrop-blur sm:h-16 sm:px-6 lg:px-8">
            <div className="flex min-w-0 items-center gap-3">
              <Link
                to={user ? "/app" : "/"}
                aria-label="Gomo — início"
                className="shrink-0 rounded-md focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-focus lg:hidden"
              >
                <BrandLogo compact className="size-8" />
              </Link>
              <div className="hidden lg:block">
                <button type="button" className="icon-button" onClick={toggleCollapsed} aria-label={collapsed ? "Expandir navegação" : "Recolher navegação"} aria-expanded={!collapsed}>
                  <Menu className="size-5" aria-hidden />
                </button>
              </div>
              <p className="truncate font-display text-lg font-semibold text-foreground sm:font-sans sm:text-base">{currentPage}</p>
            </div>
            {user ? (
              <div className="flex min-w-0 items-center gap-3">
                {!user.premium ? (
                  <Link
                    to="/assinar?origem=topo"
                    className="hidden min-h-9 items-center gap-1.5 rounded-full bg-primary-soft px-3 text-xs font-bold text-primary-dark hover:bg-primary-soft-strong focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-focus sm:inline-flex"
                  >
                    <Sparkles className="size-3.5" aria-hidden />
                    Conhecer o Premium
                  </Link>
                ) : null}
                <NotificationBell />
                <div className="hidden min-w-0 text-right sm:block">
                  <p className="truncate text-sm font-semibold text-foreground">{user.name}</p>
                  <p className="text-xs text-muted">{planLabel(user)}</p>
                </div>
              </div>
            ) : (
              <div className="flex items-center gap-2">
                <NativeButton to={`/entrar?depois=${returnPath}`} variant="ghost" size="sm" className="hidden sm:inline-flex">Entrar</NativeButton>
                <NativeButton to={`/entrar?modo=cadastro&depois=${returnPath}`} size="sm">Criar conta grátis</NativeButton>
              </div>
            )}
          </header>

          <main
            id="conteudo-principal"
            className={cn(
              "mx-auto w-full max-w-[96rem] p-4 sm:p-6 lg:p-8",
              showTabBar && "pb-[calc(var(--app-bottom-inset)+1.5rem)] sm:pb-[calc(var(--app-bottom-inset)+1.5rem)] lg:pb-8",
            )}
            tabIndex={-1}
          >
            <Outlet />
          </main>
        </div>

        {showTabBar ? (
          <MobileTabBar
            user={user}
            planLabel={user ? planLabel(user) : null}
            returnPath={returnPath}
            onLogout={() => void handleLogout()}
          />
        ) : null}
      </div>
    </UpsellProvider>
  );
}

function readCollapsed() {
  try {
    return window.localStorage.getItem(COLLAPSED_KEY) === "true";
  } catch {
    return false;
  }
}

function planLabel(user: User) {
  if (user.role === "ADMIN") return "Administrador";
  if (user.premiumSource === "BETA") return "Beta: tudo liberado";
  if (user.premiumSource === "TRIAL" && user.trialEndsAt) {
    const endsAt = new Intl.DateTimeFormat("pt-BR", { day: "2-digit", month: "2-digit" }).format(new Date(user.trialEndsAt));
    return `Teste Premium até ${endsAt}`;
  }
  return user.premium ? "Premium" : "Plano grátis";
}

/** The bell shows how many notifications are waiting; it checks again every few minutes. */
function NotificationBell() {
  const unread = useQuery({
    queryKey: ["notifications", "unread"],
    queryFn: alertApi.unreadNotifications,
    refetchInterval: 5 * 60 * 1000,
  });
  const count = unread.data?.unread ?? 0;
  const label = count === 0 ? "Notificações" : count === 1 ? "Notificações: 1 nova" : `Notificações: ${count} novas`;

  return (
    <Link to="/app/notificacoes" className="icon-button relative" aria-label={label} title={label}>
      <Bell className="size-5" aria-hidden />
      {count > 0 ? (
        <span
          className="absolute -right-0.5 -top-0.5 grid min-w-4.5 place-items-center rounded-full bg-primary px-1 text-[0.65rem] font-bold leading-4.5 text-white tabular-nums"
          aria-hidden
        >
          {count > 99 ? "99+" : count}
        </span>
      ) : null}
    </Link>
  );
}
