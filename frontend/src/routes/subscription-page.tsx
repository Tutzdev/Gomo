import { useQuery } from "@tanstack/react-query";
import { ArrowLeft, Plus, Sparkles } from "lucide-react";
import { useState } from "react";
import { Link } from "react-router-dom";
import { BrandLogo } from "@/components/brand-logo";
import { useAuth } from "@/features/auth/auth-context";
import { PlanComparison } from "@/features/plan/subscription/plan-comparison";
import { PlanPicker, TrialButton } from "@/features/plan/subscription/plan-picker";
import { dailyCostLabel, priceLabel } from "@/features/plan/subscription/pricing";
import { useTrialAction } from "@/features/plan/subscription/use-trial-action";
import { formatCurrency } from "@/lib/brand";
import { useDefaultCityId } from "@/lib/use-default-city";
import { subscriptionApi } from "@/services/gomo-api";
import type { BillingCycle, PremiumValue } from "@/types/api";

const objections = [
  {
    question: "Posso cancelar?",
    answer:
      "Sim, quando quiser e sem multa. O teste grátis é cancelado no seu perfil e a conta volta ao plano grátis na hora, com suas listas preservadas.",
  },
  {
    question: "Os preços são atualizados mesmo?",
    answer:
      "Sim. O Gomo coleta os preços publicados pelos mercados quatro vezes ao dia, às 6h, 11h, 16h e 21h, e cada preço mostra quando foi coletado. Mercado sem preço recente fica fora da comparação, em vez de aparecer com um valor velho.",
  },
  {
    question: "Funciona na minha cidade?",
    answer:
      "O Gomo compara as cidades onde já coleta preços, e novas cidades entram aos poucos. No plano grátis você já vê quais mercados da sua cidade têm preço. Se a sua cidade ainda não aparece, o Premium não muda isso, então vale conferir no grátis primeiro.",
  },
  {
    question: "O que acontece depois dos 7 dias?",
    answer:
      "A conta volta sozinha ao plano grátis. Nada é cobrado: o teste não pede cartão e o pagamento online ainda está em implantação.",
  },
];

/** Plans page: savings first, then Free x Premium, the price and the 7-day trial. */
export function SubscriptionPage() {
  const { user } = useAuth();
  const [cycle, setCycle] = useState<BillingCycle>("ANNUAL");
  const action = useTrialAction(cycle);
  const cityId = useDefaultCityId({ signedIn: Boolean(user) });
  const premiumValue = useQuery({
    queryKey: ["premium-value", cityId],
    queryFn: () => subscriptionApi.premiumValue(cityId),
    enabled: Boolean(user && !user.premium && cityId),
  });
  const value = premiumValue.data?.savings ? premiumValue.data : null;

  return (
    <div className="subscription-page min-h-screen bg-white text-foreground">
      <header className="mx-auto flex max-w-6xl items-center justify-between gap-4 px-4 py-5 sm:px-6">
        <Link to="/" aria-label="Gomo, página inicial" className="rounded-md focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-focus">
          <BrandLogo />
        </Link>
        <Link to={user ? "/app" : "/"} className="inline-flex min-h-10 items-center gap-2 text-sm font-semibold text-muted hover:text-foreground">
          <ArrowLeft className="size-4" aria-hidden />
          {user ? "Voltar ao app" : "Voltar"}
        </Link>
      </header>

      <main>
        <section className="subscription-hero">
          <div className="mx-auto max-w-6xl px-4 pb-14 pt-8 sm:px-6 sm:pt-12">
            <p className="inline-flex items-center gap-2 rounded-full border border-primary/20 bg-white px-3 py-1.5 text-sm font-semibold text-primary-dark">
              <Sparkles className="size-4" aria-hidden />
              7 dias grátis, sem cartão
            </p>
            <h1 className="mt-5 max-w-3xl font-display text-[clamp(2.25rem,5vw,3.75rem)] font-bold leading-[1.04] tracking-[-0.04em] text-balance">
              {value ? `Economize ${formatCurrency(value.savings!)} na sua próxima compra` : "Pague menos no mercado sem pesquisar loja por loja"}
            </h1>
            <p className="mt-5 max-w-2xl text-lg leading-8 text-[#4d3f3e]">
              Compare todos os mercados da sua cidade e nunca mais pague caro.
            </p>
          </div>
        </section>

        <div className="subscription-layout mx-auto max-w-6xl px-4 pb-28 sm:px-6 lg:pb-20">
          <div className="subscription-layout__aside">
            <div className="lg:sticky lg:top-6">
              <PlanPicker cycle={cycle} onCycleChange={setCycle} action={action} />
            </div>
          </div>

          <div className="subscription-layout__main space-y-14">
            {value ? <ValueProof value={value} /> : null}
            <PlanComparison />
            <section aria-labelledby="objections-title">
              <h2 id="objections-title" className="font-display text-2xl font-bold tracking-[-0.02em]">Antes de começar</h2>
              <div className="mt-5 border-t border-border">
                {objections.map(({ question, answer }) => (
                  <details key={question} className="subscription-faq group border-b border-border">
                    <summary className="flex min-h-14 cursor-pointer list-none items-center justify-between gap-4 py-3 font-bold focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-focus">
                      {question}
                      <Plus className="size-5 shrink-0 text-primary transition-transform duration-200 group-open:rotate-45 motion-reduce:transition-none" aria-hidden />
                    </summary>
                    <p className="max-w-2xl pb-5 pr-8 text-sm leading-6 text-muted">{answer}</p>
                  </details>
                ))}
              </div>
            </section>
          </div>
        </div>
      </main>

      <div className="subscription-sticky-cta" role="region" aria-label="Assinar o Premium">
        <div className="min-w-0">
          <p className="truncate text-sm font-bold">Premium {cycle === "ANNUAL" ? "anual" : "mensal"}</p>
          <p className="truncate text-xs text-muted">{priceLabel(cycle)}, {dailyCostLabel(cycle)}</p>
        </div>
        <div className="w-44 shrink-0">
          <TrialButton action={action} compact />
        </div>
      </div>
    </div>
  );
}

/** Real savings from the person's own list, computed from current prices. */
function ValueProof({ value }: { value: PremiumValue }) {
  return (
    <section className="value-proof" aria-labelledby="value-proof-title">
      <p id="value-proof-title" className="text-sm font-bold text-primary-dark">Calculado com a sua lista</p>
      <p className="mt-2 font-display text-2xl font-bold leading-tight tracking-[-0.02em]">
        Na lista “{value.listName}”, o Premium encontra {formatCurrency(value.savings!)} de economia.
      </p>
      <p className="mt-2 text-sm leading-6 text-[#4d3f3e]">
        É quanto sai mais barato comprar cada item onde ele custa menos, dividindo entre {value.storeCount} mercados, do que fazer tudo no
        melhor mercado sozinho. Valores com os preços atuais.
      </p>
    </section>
  );
}
