import { useQuery } from "@tanstack/react-query";
import { ChevronRight, LayoutGrid, LogIn, LogOut, Sparkles } from "lucide-react";
import { useState } from "react";
import { Link, NavLink, useLocation } from "react-router-dom";
import {
  isNavigationActive,
  notificationsItem,
  visibleNavigation,
  type NavigationItem,
} from "@/components/layout/navigation";
import { Sheet } from "@/components/ui/sheet";
import { cn } from "@/lib/cn";
import { alertApi } from "@/services/gomo-api";
import type { User } from "@/types/api";

/** The tabs a thumb reaches without stretching; everything else sits one tap away in "Mais". */
const ACCOUNT_TABS = ["/app", "/app/produtos", "/app/listas", "/app/ofertas"];
const VISITOR_TABS = ["/app/produtos", "/app/ofertas", "/app/supermercados"];

interface MobileTabBarProps {
  user: User | null;
  planLabel: string | null;
  returnPath: string;
  onLogout: () => void;
}

/**
 * Phone navigation: a fixed bar at the bottom of the screen, like the apps people already use every day. Hidden
 * from the large breakpoint up, where the sidebar takes over.
 */
export function MobileTabBar({ user, planLabel, returnPath, onLogout }: MobileTabBarProps) {
  const { pathname } = useLocation();
  const [moreOpen, setMoreOpen] = useState(false);
  const items = visibleNavigation(user?.role ?? null);
  const tabPaths = user ? ACCOUNT_TABS : VISITOR_TABS;
  const tabs = tabPaths
    .map((path) => items.find((item) => item.to === path))
    .filter((item): item is NavigationItem => Boolean(item));
  const moreItems = [...items.filter((item) => !tabPaths.includes(item.to)), notificationsItem];
  const moreActive = user ? moreItems.some((item) => isNavigationActive(item, pathname)) : false;
  // The bell lives in the header on a computer; on the phone the "Mais" tab carries the same badge.
  const unread = useQuery({
    queryKey: ["notifications", "unread"],
    queryFn: alertApi.unreadNotifications,
    refetchInterval: 5 * 60 * 1000,
    enabled: Boolean(user),
  });
  const unreadCount = unread.data?.unread ?? 0;

  return (
    <>
      <nav aria-label="Navegação principal" className="mobile-tab-bar lg:hidden">
        <ul className="mx-auto grid max-w-lg" style={{ gridTemplateColumns: `repeat(${tabs.length + 1}, minmax(0, 1fr))` }}>
          {tabs.map((item) => (
            <li key={item.to}>
              <TabLink item={item} active={isNavigationActive(item, pathname)} />
            </li>
          ))}
          <li>
            {user ? (
              <button
                type="button"
                className="mobile-tab"
                data-active={moreActive || moreOpen}
                aria-haspopup="dialog"
                aria-expanded={moreOpen}
                onClick={() => setMoreOpen(true)}
              >
                <span className="mobile-tab__icon relative">
                  <LayoutGrid className="size-5" aria-hidden />
                  {unreadCount > 0 ? <span className="mobile-tab__dot" aria-hidden /> : null}
                </span>
                <span className="mobile-tab__label">Mais</span>
                {unreadCount > 0 ? <span className="sr-only">, {unreadCount} notificações novas</span> : null}
              </button>
            ) : (
              <Link to={`/entrar?depois=${returnPath}`} className="mobile-tab" data-active={false}>
                <span className="mobile-tab__icon">
                  <LogIn className="size-5" aria-hidden />
                </span>
                <span className="mobile-tab__label">Entrar</span>
              </Link>
            )}
          </li>
        </ul>
      </nav>

      {user ? (
        <Sheet open={moreOpen} onOpenChange={setMoreOpen} title="Mais opções" side="bottom">
          <div className="space-y-5">
            <div className="flex items-center gap-3 rounded-2xl bg-surface-strong p-3">
              <span className="grid size-11 shrink-0 place-items-center rounded-full bg-primary font-display text-lg font-semibold text-white" aria-hidden>
                {user.name.trim().charAt(0).toUpperCase() || "G"}
              </span>
              <div className="min-w-0 flex-1">
                <p className="truncate font-semibold text-foreground">{user.name}</p>
                {planLabel ? <p className="text-xs text-muted">{planLabel}</p> : null}
              </div>
              {!user.premium ? (
                <Link
                  to="/assinar?origem=menu-celular"
                  onClick={() => setMoreOpen(false)}
                  className="inline-flex min-h-9 shrink-0 items-center gap-1.5 rounded-full bg-primary-soft px-3 text-xs font-bold text-primary-dark focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-focus"
                >
                  <Sparkles className="size-3.5" aria-hidden />
                  Premium
                </Link>
              ) : null}
            </div>

            <ul className="grid grid-cols-3 gap-2">
              {moreItems.map((item) => {
                const Icon = item.icon;
                const active = isNavigationActive(item, pathname);
                const badge = item === notificationsItem && unreadCount > 0 ? unreadCount : 0;
                return (
                  <li key={item.to}>
                    <NavLink
                      to={item.to}
                      onClick={() => setMoreOpen(false)}
                      className={cn(
                        "more-tile relative flex min-h-24 flex-col items-center justify-center gap-2 rounded-2xl border p-2 text-center text-xs font-semibold leading-tight",
                        active
                          ? "border-primary/30 bg-primary-soft text-primary-dark"
                          : "border-border bg-white text-foreground",
                      )}
                    >
                      <span className={cn("grid size-10 place-items-center rounded-xl", active ? "bg-white text-primary" : "bg-primary-soft text-primary")}>
                        <Icon className="size-5" aria-hidden />
                      </span>
                      {item.label}
                      {badge > 0 ? (
                        <span className="absolute right-2 top-2 grid min-w-5 place-items-center rounded-full bg-primary px-1 text-[0.65rem] font-bold leading-5 text-white tabular-nums">
                          {badge > 99 ? "99+" : badge}
                        </span>
                      ) : null}
                    </NavLink>
                  </li>
                );
              })}
            </ul>

            <button
              type="button"
              onClick={() => {
                setMoreOpen(false);
                onLogout();
              }}
              className="flex min-h-12 w-full items-center justify-between rounded-2xl border border-border px-4 text-sm font-semibold text-danger focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-focus"
            >
              <span className="flex items-center gap-3">
                <LogOut className="size-5" aria-hidden />
                Sair da conta
              </span>
              <ChevronRight className="size-4 text-muted" aria-hidden />
            </button>
          </div>
        </Sheet>
      ) : null}
    </>
  );
}

function TabLink({ item, active }: { item: NavigationItem; active: boolean }) {
  const Icon = item.icon;
  return (
    <Link to={item.to} className="mobile-tab" data-active={active} aria-current={active ? "page" : undefined}>
      <span className="mobile-tab__icon">
        <Icon className="size-5" aria-hidden />
      </span>
      <span className="mobile-tab__label">{item.shortLabel ?? item.label}</span>
    </Link>
  );
}
