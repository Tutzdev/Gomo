import { BellRing, CalendarRange, LineChart, ListPlus, Lock, Route, Store, type LucideIcon } from "lucide-react";
import { formatCurrency } from "@/lib/brand";

/** The moment that led to the upgrade message. Each one gets copy about what the person just tried to do. */
export type UpsellTrigger =
  | "LIST_ITEMS"
  | "SHOPPING_LISTS"
  | "ACTIVE_ALERTS"
  | "PRICE_HISTORY"
  | "LOCKED_STORES"
  | "SPLIT_PURCHASE"
  | "DAILY_COMPARISONS";

export interface UpsellContext {
  lockedStores?: number;
  savings?: number;
  storeCount?: number;
}

interface UpsellCopy {
  icon: LucideIcon;
  title: string;
  description: string;
  unlocks: string[];
}

export function upsellCopy(trigger: UpsellTrigger, context: UpsellContext = {}): UpsellCopy {
  switch (trigger) {
    case "LIST_ITEMS":
      return {
        icon: ListPlus,
        title: "Sua lista chegou a 10 produtos",
        description:
          "No plano grátis cada lista tem até 10 itens. Com o Premium a compra do mês inteira cabe numa lista só e é comparada de uma vez.",
        unlocks: ["Produtos ilimitados em cada lista", "Listas separadas por nome", "Todos os mercados da cidade na comparação"],
      };
    case "SHOPPING_LISTS":
      return {
        icon: ListPlus,
        title: "Quer separar suas compras em mais listas?",
        description:
          "No plano grátis você mantém 1 lista por vez. No Premium, crie quantas quiser e organize por nome: semana, mês, churrasco.",
        unlocks: ["Listas ilimitadas, organizadas por nome", "Produtos ilimitados em cada lista", "Rota até os mercados da sua compra"],
      };
    case "ACTIVE_ALERTS":
      return {
        icon: BellRing,
        title: "Você já tem 2 alertas ativos",
        description:
          "No Premium os alertas não têm limite e podem avisar a cada coleta de preços, até 4 vezes ao dia. No grátis, o aviso chega no máximo 1 vez ao dia.",
        unlocks: ["Alertas ilimitados", "Aviso a cada coleta, até 4 vezes ao dia", "Histórico de 90 dias pra definir o preço certo"],
      };
    case "PRICE_HISTORY":
      return {
        icon: LineChart,
        title: "Veja se esse preço está bom de verdade",
        description:
          "O histórico mostra como o preço variou nos últimos 90 dias em cada mercado. Assim você sabe se a promoção é promoção mesmo.",
        unlocks: ["Gráfico de variação de 90 dias", "Menor preço da cidade em cada dia", "Comparação com o mercado que você escolher"],
      };
    case "LOCKED_STORES": {
      const count = context.lockedStores ?? 0;
      return {
        icon: Store,
        title: count === 1 ? "Mais 1 mercado tem preço pra este produto" : `Mais ${count} mercados têm preço pra este produto`,
        description:
          "Você já está vendo os 3 mais baratos. No Premium aparecem todos os mercados da cidade, inclusive aquele que fica no seu caminho.",
        unlocks: ["Todos os mercados da cidade", "Comparações ilimitadas por dia", "Histórico de preços de 90 dias"],
      };
    }
    case "SPLIT_PURCHASE":
      return {
        icon: Route,
        title:
          context.savings && context.savings > 0
            ? `Dividindo a compra, você economiza ${formatCurrency(context.savings)}`
            : "Descubra onde comprar cada item",
        description: `O Premium mostra o que comprar em cada um dos ${context.storeCount ?? 2} mercados e monta a rota até eles a partir de onde você está.`,
        unlocks: ["O que comprar em cada mercado", "Rota com a distância até cada um", "Todos os mercados da cidade"],
      };
    case "DAILY_COMPARISONS":
      return {
        icon: CalendarRange,
        title: "Você usou as 5 comparações de hoje",
        description:
          "Até amanhã mostramos só o mercado mais barato de cada produto novo. Os que você já comparou hoje continuam completos. No Premium não há limite diário.",
        unlocks: ["Comparações ilimitadas", "Todos os mercados da cidade", "Histórico de preços de 90 dias"],
      };
    default:
      return { icon: Lock, title: "Recurso do Premium", description: "", unlocks: [] };
  }
}
