import { useQuery } from "@tanstack/react-query";
import { useLocation } from "react-router-dom";
import { planApi } from "@/services/gomo-api";

/**
 * For visitors on the open pages: where to create an account and come back to this page, and whether the open
 * beta gives that free account everything (so the copy can promise it instead of pointing to Premium).
 */
export function useVisitorOffer() {
  const { pathname, search } = useLocation();
  const offer = useQuery({ queryKey: ["plan-offer"], queryFn: planApi.offer, staleTime: 30 * 60 * 1000 });
  return {
    signUpPath: `/entrar?modo=cadastro&depois=${encodeURIComponent(pathname + search)}`,
    openBeta: Boolean(offer.data?.openBeta),
  };
}
