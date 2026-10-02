import { createContext, useContext } from "react";
import type { UpsellContext, UpsellTrigger } from "./upsell-copy";

export interface UpsellOptions {
  /**
   * An interruptive upsell appears because a limit blocked an action. Only one per session is shown;
   * after that the caller explains the limit inline. Clicking a locked element is not interruptive.
   */
  interruptive?: boolean;
}

export interface UpsellApi {
  /** Returns false when the modal was not shown (interruptive upsell already used this session). */
  showUpsell: (trigger: UpsellTrigger, context?: UpsellContext, options?: UpsellOptions) => boolean;
}

export const UpsellApiContext = createContext<UpsellApi | null>(null);

export function useUpsell(): UpsellApi {
  const api = useContext(UpsellApiContext);
  if (!api) throw new Error("useUpsell precisa estar dentro de UpsellProvider.");
  return api;
}
