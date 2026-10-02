import { useAuth } from "@/features/auth/auth-context";
import { ApiError } from "@/lib/api";
import type { PlanLimit } from "@/types/api";

/** The signed-in person's plan as the app needs it. */
export function usePlan() {
  const { user } = useAuth();
  return {
    premium: Boolean(user?.premium),
    onTrial: user?.premiumSource === "TRIAL",
    trialEndsAt: user?.trialEndsAt ?? null,
    trialAvailable: Boolean(user?.trialAvailable),
  };
}

/** The free-plan limit behind an API error, if that is what blocked the request. */
export function planLimitOf(error: unknown): PlanLimit | null {
  if (error instanceof ApiError && error.problem.code === "PLAN_LIMIT") {
    return error.problem.limit ?? null;
  }
  return null;
}
