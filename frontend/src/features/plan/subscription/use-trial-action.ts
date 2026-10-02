import { useMutation, useQueryClient } from "@tanstack/react-query";
import { useAuth } from "@/features/auth/auth-context";
import { subscriptionApi } from "@/services/gomo-api";
import type { BillingCycle } from "@/types/api";

/** What the main button of the plans page does for this visitor. */
export type TrialAction =
  | { kind: "sign-up"; to: string }
  | { kind: "start-trial"; start: () => void; loading: boolean; error: string | null }
  | { kind: "has-premium"; trialEndsAt: string | null }
  | { kind: "trial-used" };

export function useTrialAction(billingCycle: BillingCycle): TrialAction {
  const { user, refreshUser } = useAuth();
  const queryClient = useQueryClient();
  const startTrial = useMutation({
    mutationFn: () => subscriptionApi.startTrial(billingCycle),
    onSuccess: async () => {
      // Every cached comparison was computed for the free plan.
      await refreshUser();
      await queryClient.invalidateQueries();
    },
  });

  if (!user) return { kind: "sign-up", to: "/entrar?modo=cadastro&depois=/assinar" };
  if (user.premium) return { kind: "has-premium", trialEndsAt: user.premiumSource === "TRIAL" ? user.trialEndsAt : null };
  if (!user.trialAvailable) return { kind: "trial-used" };
  return {
    kind: "start-trial",
    start: () => startTrial.mutate(),
    loading: startTrial.isPending,
    error: startTrial.error?.message ?? null,
  };
}
