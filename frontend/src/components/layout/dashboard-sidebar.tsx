import { ChevronLeft, ChevronRight, LogIn, LogOut } from "lucide-react";
import { NavLink, useLocation } from "react-router-dom";
import { BrandLogo } from "@/components/brand-logo";
import { isNavigationActive, visibleNavigation } from "@/components/layout/navigation";
import { cn } from "@/lib/cn";
import type { UserRole } from "@/types/api";

interface DashboardSidebarProps {
  collapsed: boolean;
  /** `null` for a visitor, who sees only the open pages. */
  role: UserRole | null;
  /** Where a visitor returns after signing in. */
  returnPath: string;
  onToggle?: () => void;
  onNavigate?: () => void;
  onLogout: () => void;
  mobile?: boolean;
}

export function DashboardSidebar({
  collapsed,
  role,
  returnPath,
  onToggle,
  onNavigate,
  onLogout,
  mobile = false,
}: DashboardSidebarProps) {
  const { pathname } = useLocation();
  const items = visibleNavigation(role);
  const actionClass = cn(
    "dashboard-nav-action flex min-h-11 w-full items-center gap-3 rounded-lg px-3 text-sm font-semibold text-white/78 hover:bg-white/10 hover:text-white focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-white",
    collapsed && "justify-center px-0",
  );

  return (
    <div className="flex h-full min-h-0 flex-col">
      <div className={cn("flex h-18 items-center border-b border-white/10 px-4", collapsed ? "justify-center" : "justify-between")}>
        <NavLink
          to={role === null ? "/" : "/app"}
          aria-label="Gomo — início"
          onClick={onNavigate}
          className="rounded-md focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-white"
        >
          <BrandLogo compact={collapsed} className={collapsed ? "" : "h-8 brightness-0 invert"} />
        </NavLink>
        {!collapsed && !mobile && onToggle ? (
          <button type="button" className="sidebar-icon-button" onClick={onToggle} aria-label="Recolher menu lateral">
            <ChevronLeft className="size-4" aria-hidden />
          </button>
        ) : null}
      </div>

      {collapsed && !mobile && onToggle ? (
        <button type="button" className="sidebar-icon-button mx-auto mt-3" onClick={onToggle} aria-label="Expandir menu lateral">
          <ChevronRight className="size-4" aria-hidden />
        </button>
      ) : null}

      <nav aria-label="Área autenticada" className="min-h-0 flex-1 overflow-y-auto px-3 py-4">
        <ul className="space-y-1">
          {items.map((item) => {
            const Icon = item.icon;
            return (
              <li key={item.to}>
                <NavLink
                  to={item.to}
                  end={item.to === "/app"}
                  onClick={onNavigate}
                  title={collapsed ? item.label : undefined}
                  className={cn(
                    "dashboard-nav-action flex min-h-11 items-center gap-3 rounded-lg px-3 text-sm font-semibold",
                    "focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-white focus-visible:ring-offset-2 focus-visible:ring-offset-sidebar",
                    isNavigationActive(item, pathname)
                      ? "bg-white text-sidebar"
                      : "text-white/78 hover:bg-white/10 hover:text-white",
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

      <div className="border-t border-white/10 p-3">
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
