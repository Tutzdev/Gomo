import { ChevronLeft, ChevronRight, LogIn, LogOut, X } from "lucide-react";
import { NavLink, useLocation } from "react-router-dom";
import { BrandLogo } from "@/components/brand-logo";
import { isNavigationActive, visibleNavigation } from "@/components/layout/navigation";
import { cn } from "@/lib/cn";
import type { UserRole } from "@/types/api";

interface DashboardSidebarProps {
  collapsed: boolean;
  /** `null` for a visitor, who sees only the open pages. */
  role: UserRole | null;
  /** The signed-in person, shown above "Sair"; `null` for a visitor. */
  account: { name: string; plan: string } | null;
  /** Where a visitor returns after signing in. */
  returnPath: string;
  /** Desktop: shrinks the bar to icons. */
  onToggle?: () => void;
  /** Drawer on smaller screens: closes it. */
  onClose?: () => void;
  onNavigate?: () => void;
  onLogout: () => void;
}

/** The red bar of the signed-in area: fixed on a computer, a drawer opened by the menu button on smaller screens. */
export function DashboardSidebar({
  collapsed,
  role,
  account,
  returnPath,
  onToggle,
  onClose,
  onNavigate,
  onLogout,
}: DashboardSidebarProps) {
  const { pathname } = useLocation();
  const items = visibleNavigation(role);
  const actionClass = cn(
    "dashboard-nav-action flex min-h-11 w-full items-center gap-3 rounded-lg px-3 text-sm font-semibold text-white/78 hover:bg-white/10 hover:text-white focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-white",
    collapsed && "justify-center px-0",
  );

  return (
    <div className="flex h-full min-h-0 flex-col">
      <div className={cn("flex h-16 shrink-0 items-center border-b border-white/10 px-4 lg:h-18", collapsed ? "justify-center" : "justify-between")}>
        <NavLink
          to={role === null ? "/" : "/app"}
          aria-label="Gomo — início"
          onClick={onNavigate}
          className="rounded-md focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-white"
        >
          <BrandLogo compact={collapsed} className={collapsed ? "" : "h-8 brightness-0 invert"} />
        </NavLink>
        {onClose ? (
          <button type="button" className="sidebar-icon-button" onClick={onClose} aria-label="Fechar menu">
            <X className="size-5" aria-hidden />
          </button>
        ) : !collapsed && onToggle ? (
          <button type="button" className="sidebar-icon-button" onClick={onToggle} aria-label="Recolher menu lateral">
            <ChevronLeft className="size-4" aria-hidden />
          </button>
        ) : null}
      </div>

      {collapsed && onToggle ? (
        <button type="button" className="sidebar-icon-button mx-auto mt-3" onClick={onToggle} aria-label="Expandir menu lateral">
          <ChevronRight className="size-4" aria-hidden />
        </button>
      ) : null}

      <nav aria-label="Navegação principal" className="min-h-0 flex-1 overflow-y-auto overscroll-contain px-3 py-4">
        <ul className="space-y-1">
          {items.map((item) => {
            const Icon = item.icon;
            const active = isNavigationActive(item, pathname);
            return (
              <li key={item.to}>
                <NavLink
                  to={item.to}
                  end={item.to === "/app"}
                  onClick={onNavigate}
                  title={collapsed ? item.label : undefined}
                  aria-current={active ? "page" : undefined}
                  className={cn(
                    "dashboard-nav-action flex min-h-11 items-center gap-3 rounded-lg px-3 text-sm font-semibold",
                    "focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-white focus-visible:ring-offset-2 focus-visible:ring-offset-sidebar",
                    active ? "bg-white text-sidebar" : "text-white/78 hover:bg-white/10 hover:text-white",
                    collapsed && "justify-center px-0",
                  )}
                >
                  <Icon className="size-5 shrink-0" aria-hidden />
                  {!collapsed ? <span>{item.label}</span> : <span className="sr-only">{item.label}</span>}
                </NavLink>
              </li>
            );
          })}
        </ul>
      </nav>

      <div className="shrink-0 border-t border-white/10 p-3 pb-[calc(0.75rem+env(safe-area-inset-bottom))]">
        {account && !collapsed ? (
          <div className="mb-2 flex items-center gap-3 rounded-lg bg-white/8 px-3 py-2.5">
            <span className="grid size-9 shrink-0 place-items-center rounded-full bg-white font-display text-base font-semibold text-sidebar" aria-hidden>
              {initial(account.name)}
            </span>
            <div className="min-w-0">
              <p className="truncate text-sm font-semibold text-white">{account.name}</p>
              <p className="truncate text-xs text-white/65">{account.plan}</p>
            </div>
          </div>
        ) : null}
        {role === null ? (
          <NavLink to={`/entrar?depois=${returnPath}`} onClick={onNavigate} title={collapsed ? "Entrar" : undefined} className={actionClass}>
            <LogIn className="size-5 shrink-0" aria-hidden />
            {!collapsed ? <span>Entrar ou criar conta</span> : <span className="sr-only">Entrar ou criar conta</span>}
          </NavLink>
        ) : (
          <button type="button" onClick={onLogout} title={collapsed ? "Sair" : undefined} className={actionClass}>
            <LogOut className="size-5 shrink-0" aria-hidden />
            {!collapsed ? <span>Sair</span> : <span className="sr-only">Sair</span>}
          </button>
        )}
      </div>
    </div>
  );
}

export function initial(name: string) {
  return name.trim().charAt(0).toUpperCase() || "G";
}
