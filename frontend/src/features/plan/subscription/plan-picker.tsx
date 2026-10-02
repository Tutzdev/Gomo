import { CheckCircle2, ShieldCheck } from "lucide-react";
import { InlineError } from "@/components/ui/feedback";
import { NativeButton } from "@/components/ui/native-button";
import { BRAND, formatCurrency } from "@/lib/brand";
import { cn } from "@/lib/cn";
import type { BillingCycle } from "@/types/api";
import { dailyCostLabel } from "./pricing";
import type { TrialAction } from "./use-trial-action";

const ANNUAL_DISCOUNT = Math.round((1 - BRAND.annualPrice / (BRAND.monthlyPrice * 12)) * 100);

const formatDate = (value: string) =>
  new Intl.DateTimeFormat("pt-BR", { day: "2-digit", month: "long" }).format(new Date(value));

/** The plan choice (annual selected by default) and the trial button. */
export function PlanPicker({
  cycle,
  onCycleChange,
  action,
}: {
  cycle: BillingCycle;
  onCycleChange: (cycle: BillingCycle) => void;
  action: TrialAction;
}) {
  return (
    <section className="plan-picker" aria-labelledby="plan-picker-title">
      <div className="flex items-center justify-between gap-3">
        <h2 id="plan-picker-title" className="font-display text-xl font-bold">Gomo Premium</h2>
        <span className="rounded-full bg-primary px-2.5 py-1 text-xs font-bold text-white">Recomendado</span>
      </div>

      <fieldset className="mt-6 grid gap-4">
        <legend className="sr-only">Forma de pagamento</legend>
        <PlanOption
          value="ANNUAL"
          selected={cycle === "ANNUAL"}
          onSelect={onCycleChange}
          title="Anual"
          price={`${formatCurrency(BRAND.annualPrice)}/ano`}
          detail={`Equivale a ${formatCurrency(BRAND.annualPrice / 12)}/mês`}
          badge={`Melhor valor, economize ${ANNUAL_DISCOUNT}%`}
        />
        <PlanOption
          value="MONTHLY"
          selected={cycle === "MONTHLY"}
          onSelect={onCycleChange}
          title="Mensal"
          price={`${formatCurrency(BRAND.monthlyPrice)}/mês`}
          detail="Sem fidelidade"
        />
      </fieldset>
      <p className="mt-3 text-center text-xs text-muted">Isso dá {dailyCostLabel(cycle)}.</p>

      <div className="mt-5">
        <TrialButton action={action} />
      </div>

      <ul className="mt-5 space-y-2 border-t border-border pt-4 text-sm">
        <li className="flex gap-2"><CheckCircle2 className="mt-0.5 size-4 shrink-0 text-success" aria-hidden />7 dias grátis, sem cartão e sem cobrança</li>
        <li className="flex gap-2"><ShieldCheck className="mt-0.5 size-4 shrink-0 text-success" aria-hidden />Cancele quando quiser, sem multa</li>
      </ul>
      <p className="mt-3 text-xs leading-5 text-muted">
        Ao fim do teste, sua conta volta ao plano grátis. Nada é cobrado automaticamente: o pagamento online ainda está em implantação e
        a forma escolhida fica guardada para quando ele abrir.
      </p>
    </section>
  );
}

function PlanOption({
  value,
  selected,
  onSelect,
  title,
  price,
  detail,
  badge,
}: {
  value: BillingCycle;
  selected: boolean;
  onSelect: (cycle: BillingCycle) => void;
  title: string;
  price: string;
  detail: string;
  badge?: string;
}) {
  return (
    <label className={cn("plan-option", selected && "is-selected")}>
      <input type="radio" name="billing-cycle" value={value} checked={selected} onChange={() => onSelect(value)} className="plan-option__input" />
      <span className="plan-option__radio" aria-hidden />
      {badge ? <span className="plan-option__ribbon">{badge}</span> : null}
      <span className="min-w-0 flex-1">
        <span className="block font-bold">{title}</span>
        <span className="mt-0.5 block text-xs text-muted">{detail}</span>
      </span>
      <span className="text-right font-extrabold tabular-nums">{price}</span>
    </label>
  );
}

export function TrialButton({ action, compact = false }: { action: TrialAction; compact?: boolean }) {
  switch (action.kind) {
    case "sign-up":
      return (
        <div className="grid gap-2">
          <NativeButton to={action.to} size="lg" glow className="pill-button w-full">Começar 7 dias grátis</NativeButton>
          {!compact ? <p className="text-center text-xs text-muted">Crie sua conta grátis e ative o teste em seguida.</p> : null}
        </div>
      );
    case "start-trial":
      return (
        <div className="grid gap-2">
          <NativeButton size="lg" glow className="pill-button w-full" loading={action.loading} onClick={action.start}>
            Começar 7 dias grátis
          </NativeButton>
          {action.error ? <InlineError>{action.error}</InlineError> : null}
        </div>
      );
    case "has-premium":
      return (
        <div className="grid gap-2">
          <p role="status" className="rounded-lg bg-success-soft px-3 py-2.5 text-center text-sm font-semibold text-success">
            {action.trialEndsAt ? `Premium ativo até ${formatDate(action.trialEndsAt)}` : "Sua conta já tem o Premium"}
          </p>
          <NativeButton to="/app/comparar" size="lg" className="pill-button w-full">Ir comparar preços</NativeButton>
        </div>
      );
    case "trial-used":
      return (
        <p className="rounded-lg bg-surface-strong px-3 py-2.5 text-center text-sm">
          Seu teste grátis já foi usado. A assinatura paga abre em breve; até lá o plano grátis continua disponível.
        </p>
      );
  }
}
