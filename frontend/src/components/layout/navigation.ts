import {
  CircleDollarSign,
  House,
  ListChecks,
  PiggyBank,
  Search,
  Settings,
  ShieldCheck,
  Store,
  Tag,
  type LucideIcon,
} from "lucide-react";
import type { UserRole } from "@/types/api";

export type NavigationItem = {
  label: string;
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
  { label: "Buscar preços", to: "/app/produtos", icon: Search, alsoActiveOn: ["/app/comparar"], open: true },
  { label: "Ofertas da semana", to: "/app/ofertas", icon: Tag, open: true },
  { label: "Minhas listas", to: "/app/listas", icon: ListChecks },
  { label: "Minha economia", to: "/app/economia", icon: PiggyBank },
  { label: "Alertas de preço", to: "/app/alertas", icon: CircleDollarSign },
  { label: "Supermercados", to: "/app/supermercados", icon: Store, open: true },
  { label: "Perfil", to: "/app/perfil", icon: Settings },
  { label: "Administração", to: "/app/admin", icon: ShieldCheck, admin: true },
];

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
