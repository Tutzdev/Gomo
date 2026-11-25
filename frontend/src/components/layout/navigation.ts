import {
  Bell,
  House,
  ListChecks,
  PiggyBank,
  Search,
  Settings,
  ShieldCheck,
  Store,
  Tag,
  TrendingDown,
  type LucideIcon,
} from "lucide-react";
import type { UserRole } from "@/types/api";

export type NavigationItem = {
  label: string;
  /** Fits a tab of the installed app's bottom bar. */
  shortLabel?: string;
  to: string;
  icon: LucideIcon;
  /** Other pages that belong to this item, so it stays highlighted on them. */
  alsoActiveOn?: string[];
  admin?: boolean;
  /** Open to visitors without an account. */
  open?: boolean;
};

/**
 * Kept short on purpose: searching and comparing are one task, so they share one entry. Notifications live
 * behind the bell in the header, and price contributions stay out of the menu until there is time to moderate them.
 */
export const navigation: NavigationItem[] = [
  { label: "Início", to: "/app", icon: House },
  { label: "Buscar preços", shortLabel: "Buscar", to: "/app/produtos", icon: Search, alsoActiveOn: ["/app/comparar"], open: true },
  { label: "Minhas listas", shortLabel: "Listas", to: "/app/listas", icon: ListChecks },
  { label: "Ofertas da semana", shortLabel: "Ofertas", to: "/app/ofertas", icon: Tag, open: true },
  { label: "Alertas de preço", shortLabel: "Alertas", to: "/app/alertas", icon: TrendingDown },
  { label: "Supermercados", shortLabel: "Mercados", to: "/app/supermercados", icon: Store, open: true },
  { label: "Minha economia", shortLabel: "Economia", to: "/app/economia", icon: PiggyBank },
  { label: "Perfil", to: "/app/perfil", icon: Settings },
  { label: "Administração", shortLabel: "Admin", to: "/app/admin", icon: ShieldCheck, admin: true },
];

/** In the installed app the bell's page sits in the "Mais" drawer, with the same unread count. */
export const notificationsItem: NavigationItem = { label: "Notificações", to: "/app/notificacoes", icon: Bell };

/** The pages a visitor (`null`) or an account with this role can open. */
export function visibleNavigation(role: UserRole | null) {
  return navigation.filter((item) => (role === null ? item.open : !item.admin || role === "ADMIN"));
}

export function isNavigationActive(item: NavigationItem, pathname: string) {
  if (item.to === "/app") return pathname === "/app";
  return (
    pathname === item.to ||
    pathname.startsWith(`${item.to}/`) ||
    (item.alsoActiveOn?.some((path) => pathname.startsWith(path)) ?? false)
  );
}

/** The letter in the round avatar of the header and of the sidebar. */
export function initial(name: string) {
  return name.trim().charAt(0).toUpperCase() || "G";
}
