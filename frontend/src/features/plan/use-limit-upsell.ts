import { useCallback, useState } from "react";
import type { PlanLimit } from "@/types/api";
import { useUpsell } from "./upsell-context";
import type { UpsellTrigger } from "./upsell-copy";

const LIMIT_MESSAGES: Record<Exclude<PlanLimit, "PRICE_HISTORY">, string> = {
  SHOPPING_LISTS: "No plano grátis você mantém 1 lista por vez.",
  LIST_ITEMS: "No plano grátis cada lista tem até 10 produtos.",
  ACTIVE_ALERTS: "No plano grátis você mantém até 2 alertas ativos.",
  BELOW_AVERAGE_ALERTS: "O alerta abaixo da média faz parte do Premium. No plano grátis, escolha o preço que você quer.",
  DEALS_FOR_MY_LISTS: "Ver só as ofertas das suas listas faz parte do Premium. As ofertas da cidade continuam abertas.",
  SHARED_LISTS: "Compartilhar a lista faz parte do Premium. Quem recebe o convite não precisa assinar.",
};

/**
 * Handles an action the free plan blocked. The first time in the session it opens the contextual modal;
 * after that the screen shows `notice` inline, so the person always learns why nothing happened.
 * `block` returns whether the modal opened.
 */
export function useLimitUpsell() {
  const { showUpsell } = useUpsell();
  const [notice, setNotice] = useState<string | null>(null);

  const block = useCallback(
    (limit: Exclude<PlanLimit, "PRICE_HISTORY">) => {
      const shown = showUpsell(limit satisfies UpsellTrigger, {}, { interruptive: true });
      setNotice(shown ? null : LIMIT_MESSAGES[limit]);
      return shown;
    },
    [showUpsell],
  );

  const clear = useCallback(() => setNotice(null), []);

  return { notice, block, clear };
}
