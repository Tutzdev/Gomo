import * as Dialog from "@radix-ui/react-dialog";
import { useQuery } from "@tanstack/react-query";
import { Bell, Menu, Sparkles } from "lucide-react";
import { useEffect, useState } from "react";
import { Link, Outlet, useLocation, useNavigate } from "react-router-dom";
import { BrandLogo } from "@/components/brand-logo";
import { DashboardSidebar } from "@/components/layout/dashboard-sidebar";
import { initial } from "@/components/layout/navigation";
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

/**
 * The signed-in area as a website: on a computer the red bar stays on the left (and shrinks to icons); on a
 * tablet or phone the menu button in the header opens the same bar as a drawer.
 */
export function AppShell() {
  const { user, logout } = useAuth();
  const navigate = useNavigate();
  const location = useLocation();
  const [collapsed, setCollapsed] = useState(() => readCollapsed());
  const [menuOpen, setMenuOpen] = useState(false);
  const account = user ? { name: user.name, plan: planLabel(user) } : null;

  // The drawer closes whenever the page changes: a link inside the page, the back button or the menu itself.
  useEffect(() => {
    setMenuOpen(false);
  }, [location.pathname]);

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

  // Tablet and phone: the logo takes the corner and the menu sits at the opposite end, after the account.
  const menuButton = (
    <div className="-mr-2 lg:hidden">
      <button
        type="button"
        className="icon-button"
        onClick={() => setMenuOpen(true)}
        aria-label="Abrir menu"
        aria-haspopup="dialog"
        aria-expanded={menuOpen}
      >
        <Menu className="size-5" aria-hidden />
      </button>
    </div>
  );

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
            account={account}
            returnPath={returnPath}
            onToggle={toggleCollapsed}
            onLogout={() => void handleLogout()}
          />
        </aside>

        <Dialog.Root open={menuOpen} onOpenChange={setMenuOpen}>
          <Dialog.Portal>
            <Dialog.Overlay className="sidebar-drawer-overlay fixed inset-0 z-50 bg-black/45 lg:hidden" />
            <Dialog.Content
              aria-describedby={undefined}
              className="sidebar-drawer fixed inset-y-0 right-0 z-50 w-[min(86vw,18rem)] bg-sidebar shadow-2xl focus:outline-none lg:hidden"
            >
              <Dialog.Title className="sr-only">Menu</Dialog.Title>
              <DashboardSidebar
                collapsed={false}
                role={user?.role ?? null}
                account={account}
                returnPath={returnPath}
                onClose={() => setMenuOpen(false)}
                onNavigate={() => setMenuOpen(false)}
                onLogout={() => {
                  setMenuOpen(false);
                  void handleLogout();
                }}
              />
            </Dialog.Content>
          </Dialog.Portal>
        </Dialog.Root>

        <div className={cn("min-h-screen transition-[padding] duration-200 motion-reduce:transition-none", collapsed ? "lg:pl-20" : "lg:pl-64")}>
          <header className="sticky top-0 z-30 flex h-14 items-center justify-between gap-3 border-b border-border bg-white/95 px-4 pt-[env(safe-area-inset-top)] backdrop-blur sm:h-16 sm:px-6 lg:px-8">
            <div className="flex min-w-0 items-center gap-2 sm:gap-3">
              {/* .icon-button sets its own display, so the breakpoint visibility lives on a wrapper. */}
              <div className="hidden lg:block">
                <button
                  type="button"
                  className="icon-button"
                  onClick={toggleCollapsed}
                  aria-label={collapsed ? "Expandir navegação" : "Recolher navegação"}
                  aria-expanded={!collapsed}
                >
                  <Menu className="size-5" aria-hidden />
                </button>
              </div>
              <Link
                to={user ? "/app" : "/"}
                aria-label="Gomo — início"
                className="shrink-0 rounded-md focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-focus lg:hidden"
              >
                <span className="sm:hidden">
                  <BrandLogo compact className="size-8" />
                </span>
                <span className="hidden sm:block">
                  <BrandLogo className="h-7 sm:h-7" />
                </span>
              </Link>
              <span className="hidden h-6 w-px shrink-0 bg-border sm:block lg:hidden" aria-hidden />
              <p className="hidden min-w-0 truncate text-base font-semibold text-foreground sm:block">{currentPage}</p>
            </div>
            {user ? (
              <div className="flex min-w-0 items-center gap-2 sm:gap-3">
                {!user.premium ? (
                  <Link
                    to="/assinar?origem=topo"
                    className="hidden min-h-9 items-center gap-1.5 rounded-full bg-primary-soft px-3 text-xs font-bold text-primary-dark hover:bg-primary-soft-strong focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-focus md:inline-flex"
                  >
                    <Sparkles className="size-3.5" aria-hidden />
                    Conhecer o Premium
                  </Link>
                ) : null}
                <NotificationBell />
                <Link
                  to="/app/perfil"
                  className="flex min-w-0 items-center gap-3 rounded-full focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-focus"
                  aria-label={`Perfil de ${user.name}`}
                >
                  <span className="hidden min-w-0 text-right md:block">
                    <span className="block max-w-48 truncate text-sm font-semibold text-foreground">{user.name}</span>
                    <span className="block text-xs text-muted">{planLabel(user)}</span>
                  </span>
                  <span className="grid size-9 shrink-0 place-items-center rounded-full bg-primary font-display text-base font-semibold text-white" aria-hidden>
                    {initial(user.name)}
                  </span>
                </Link>
                {menuButton}
              </div>
            ) : (
              <div className="flex items-center gap-2">
                <NativeButton to={`/entrar?depois=${returnPath}`} variant="ghost" size="sm" className="hidden sm:inline-flex">Entrar</NativeButton>
                <NativeButton to={`/entrar?modo=cadastro&depois=${returnPath}`} size="sm">Criar conta grátis</NativeButton>
                {menuButton}
              </div>
            )}
          </header>

          <main
            id="conteudo-principal"
            className="mx-auto w-full max-w-[96rem] p-4 pb-[calc(1.5rem+env(safe-area-inset-bottom))] sm:p-6 lg:p-8"
            tabIndex={-1}
          >
            <Outlet />
          </main>
        </div>
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
