import { useMutation, useQuery } from "@tanstack/react-query";
import { useAuth } from "@/features/auth/auth-context";
import { subscriptionApi } from "@/services/gomo-api";
import type { BillingCycle } from "@/types/api";

/** Paid Premium: offered only to a signed-in account without a paid plan, and only when the server takes payments. */
export type CheckoutAction =
  | { available: false }
  | { available: true; start: () => void; loading: boolean; error: string | null };

export function useCheckoutAction(billingCycle: BillingCycle): CheckoutAction {
  const { user } = useAuth();
  const status = useQuery({
    queryKey: ["subscription-status", user?.id],
    queryFn: subscriptionApi.status,
    enabled: Boolean(user),
  });
  const checkout = useMutation({
    mutationFn: () => subscriptionApi.checkout(billingCycle),
    // The payment page belongs to the provider; Premium starts when its confirmation reaches the server.
    onSuccess: ({ checkoutUrl }) => window.location.assign(checkoutUrl),
  });

  const paying = user?.premiumSource === "SUBSCRIPTION" || user?.premiumSource === "ADMIN";
  if (!user || paying || !status.data?.checkoutAvailable) return { available: false };
  return {
    available: true,
    start: () => checkout.mutate(),
    // Stays busy while the browser leaves for the payment page.
    loading: checkout.isPending || checkout.isSuccess,
    error: checkout.error?.message ?? null,
  };
}
