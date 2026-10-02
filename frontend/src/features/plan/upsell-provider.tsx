import * as Dialog from "@radix-ui/react-dialog";
import { Check, X } from "lucide-react";
import { useCallback, useMemo, useState, type ReactNode } from "react";
import { NativeButton } from "@/components/ui/native-button";
import { BRAND, formatCurrency } from "@/lib/brand";
import { UpsellApiContext, type UpsellApi } from "./upsell-context";
import { upsellCopy, type UpsellContext, type UpsellTrigger } from "./upsell-copy";
import { usePlan } from "./use-plan";

const INTERRUPTIVE_SHOWN_KEY = "gomo.upsell.interruptive-shown";

function interruptiveAlreadyShown() {
  try {
    return window.sessionStorage.getItem(INTERRUPTIVE_SHOWN_KEY) === "1";
  } catch {
    return false;
  }
}

function rememberInterruptiveShown() {
  try {
    window.sessionStorage.setItem(INTERRUPTIVE_SHOWN_KEY, "1");
  } catch {
    // Without session storage the cap simply resets on reload.
  }
}

export function UpsellProvider({ children }: { children: ReactNode }) {
  const [active, setActive] = useState<{ trigger: UpsellTrigger; context: UpsellContext } | null>(null);

  const showUpsell = useCallback<UpsellApi["showUpsell"]>((trigger, context = {}, options = {}) => {
    if (options.interruptive) {
      if (interruptiveAlreadyShown()) return false;
      rememberInterruptiveShown();
    }
    setActive({ trigger, context });
    return true;
  }, []);

  const api = useMemo(() => ({ showUpsell }), [showUpsell]);

  return (
    <UpsellApiContext.Provider value={api}>
      {children}
      <UpgradeDialog
        trigger={active?.trigger ?? null}
        context={active?.context ?? {}}
        onClose={() => setActive(null)}
      />
    </UpsellApiContext.Provider>
  );
}

function UpgradeDialog({
  trigger,
  context,
  onClose,
}: {
  trigger: UpsellTrigger | null;
  context: UpsellContext;
  onClose: () => void;
}) {
  const { trialAvailable } = usePlan();
  const copy = trigger ? upsellCopy(trigger, context) : null;
  const Icon = copy?.icon;
  const monthlyOnAnnual = BRAND.annualPrice / 12;

  return (
    <Dialog.Root open={Boolean(trigger)} onOpenChange={(open) => !open && onClose()}>
      <Dialog.Portal>
        <Dialog.Overlay className="upsell-overlay fixed inset-0 z-50 bg-black/45" />
        <Dialog.Content className="upsell-dialog fixed left-1/2 top-1/2 z-50 w-[min(92vw,27rem)] -translate-x-1/2 -translate-y-1/2 overflow-hidden rounded-2xl bg-white shadow-2xl focus:outline-none">
          {copy && Icon ? (
            <>
              <div className="upsell-dialog__header px-6 pb-5 pt-6">
                <div className="flex items-start justify-between gap-4">
                  <span className="grid size-11 place-items-center rounded-xl bg-white text-primary shadow-sm">
                    <Icon className="size-5" aria-hidden />
                  </span>
                  <Dialog.Close className="icon-button -mr-2 -mt-2" aria-label="Fechar">
                    <X className="size-5" aria-hidden />
                  </Dialog.Close>
                </div>
                <Dialog.Title className="mt-4 font-display text-[1.375rem] font-bold leading-tight tracking-[-0.02em]">
                  {copy.title}
                </Dialog.Title>
                <Dialog.Description className="mt-2 text-sm leading-6 text-[#4d3f3e]">
                  {copy.description}
                </Dialog.Description>
              </div>

              <div className="px-6 pb-6 pt-5">
                <p className="text-sm font-bold">O Premium libera</p>
                <ul className="mt-3 space-y-2.5">
                  {copy.unlocks.map((unlock) => (
                    <li key={unlock} className="flex gap-2.5 text-sm">
                      <Check className="mt-0.5 size-4 shrink-0 text-success" aria-hidden />
                      {unlock}
                    </li>
                  ))}
                </ul>
                <p className="mt-5 text-xs text-muted">
                  {formatCurrency(BRAND.monthlyPrice)}/mês, ou {formatCurrency(monthlyOnAnnual)}/mês no plano anual.
                </p>
                <div className="mt-4 grid gap-2">
                  <NativeButton to={`/assinar?origem=${trigger?.toLowerCase()}`} size="lg" glow className="w-full">
                    {trialAvailable ? "Testar 7 dias grátis" : "Conhecer o Premium"}
                  </NativeButton>
                  <Dialog.Close asChild>
                    <NativeButton variant="ghost" className="w-full">Agora não</NativeButton>
                  </Dialog.Close>
                </div>
                {trialAvailable ? (
                  <p className="mt-3 text-center text-xs text-muted">Sem cartão e sem cobrança no teste.</p>
                ) : null}
              </div>
            </>
          ) : null}
        </Dialog.Content>
      </Dialog.Portal>
    </Dialog.Root>
  );
}
