import { useQuery, useQueryClient } from "@tanstack/react-query";
import { ArrowLeft, Plus, Sparkles } from "lucide-react";
import { useEffect, useState } from "react";
import { Link, useSearchParams } from "react-router-dom";
import { BrandLogo } from "@/components/brand-logo";
import { useAuth } from "@/features/auth/auth-context";
import { PlanComparison } from "@/features/plan/subscription/plan-comparison";
import { PlanActions, PlanPicker } from "@/features/plan/subscription/plan-picker";
import { dailyCostLabel, priceLabel } from "@/features/plan/subscription/pricing";
import { useCheckoutAction } from "@/features/plan/subscription/use-checkout-action";
import { useTrialAction } from "@/features/plan/subscription/use-trial-action";
import { formatCurrency } from "@/lib/brand";
import "@/components/ui/animated-icons.css";
import { useDefaultCityId } from "@/lib/use-default-city";
import { subscriptionApi } from "@/services/gomo-api";
import type { BillingCycle, PremiumValue } from "@/types/api";

const objections = (paymentOpen: boolean) => [
  {
    question: "Posso cancelar?",
    answer:
      "Sim, quando quiser e sem multa, no seu perfil. O teste grátis termina na hora e a conta volta ao plano grátis, com suas listas preservadas. Na assinatura paga, o Premium continua até o fim do período que você já pagou.",
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
    answer: paymentOpen
      ? "A conta volta sozinha ao plano grátis. Nada é cobrado: o teste não pede cartão. Para continuar no Premium, é só assinar aqui quando quiser."
      : "A conta volta sozinha ao plano grátis. Nada é cobrado: o teste não pede cartão e o pagamento online ainda está em implantação.",
  },
];

/** Plans page: savings first, then Free x Premium, the price and the 7-day trial. */
export function SubscriptionPage() {
  const { user } = useAuth();
  const [cycle, setCycle] = useState<BillingCycle>("ANNUAL");
  const action = useTrialAction(cycle);
  const checkout = useCheckoutAction(cycle);
  const [searchParams] = useSearchParams();
  const returnedFromPayment = searchParams.get("pagamento") === "concluido";
  const cityId = useDefaultCityId({ signedIn: Boolean(user) });
  const premiumValue = useQuery({
    queryKey: ["premium-value", cityId],
    queryFn: () => subscriptionApi.premiumValue(cityId),
    enabled: Boolean(user && !user.premium && cityId),
  });
  const value = premiumValue.data?.savings ? premiumValue.data : null;

  return (
    <div className="subscription-page motion-icons min-h-screen bg-white text-foreground">
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
              {returnedFromPayment && user ? <PaymentReturnNotice /> : null}
              <PlanPicker cycle={cycle} onCycleChange={setCycle} action={action} checkout={checkout} />
            </div>
          </div>

          <div className="subscription-layout__main space-y-14">
            {value ? <ValueProof value={value} /> : null}
            <PlanComparison />
            <section aria-labelledby="objections-title">
              <h2 id="objections-title" className="font-display text-2xl font-bold tracking-[-0.02em]">Antes de começar</h2>
              <div className="mt-5 border-t border-border">
                {objections(checkout.available).map(({ question, answer }) => (
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
          <PlanActions action={action} checkout={checkout} cycle={cycle} compact />
        </div>
      </div>
    </div>
  );
}

const CONFIRMATION_CHECKS = 10;
const CONFIRMATION_INTERVAL_MS = 3000;

/**
 * Back from the payment page. Premium starts when the provider's confirmation reaches the server, usually in
 * seconds, so the account is reloaded a few times instead of trusting the return link.
 */
function PaymentReturnNotice() {
  const { user, refreshUser } = useAuth();
  const queryClient = useQueryClient();
  const [checks, setChecks] = useState(0);
  const confirmed = user?.premiumSource === "SUBSCRIPTION";

  useEffect(() => {
    if (confirmed) {
      // Every cached comparison was computed for the free plan.
      void queryClient.invalidateQueries();
      return;
    }
    if (checks >= CONFIRMATION_CHECKS) return;
    const timer = window.setTimeout(() => {
      void refreshUser().finally(() => setChecks((value) => value + 1));
    }, CONFIRMATION_INTERVAL_MS);
    return () => window.clearTimeout(timer);
  }, [checks, confirmed, queryClient, refreshUser]);

  return (
    <p role="status" className="mb-4 rounded-lg bg-success-soft px-4 py-3 text-sm font-semibold text-success">
      {confirmed
        ? "Pagamento confirmado. Seu Premium está ativo."
        : checks < CONFIRMATION_CHECKS
          ? "Pagamento recebido. Estamos ativando seu Premium, leva alguns segundos…"
          : "Ainda não recebemos a confirmação do pagamento. Ela pode levar alguns minutos; atualize a página daqui a pouco."}
    </p>
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
