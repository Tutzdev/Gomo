import { Check } from "lucide-react";
import { NativeButton } from "@/components/ui/native-button";
import { BRAND, formatCurrency } from "@/lib/brand";

const freeItems = [
  "Os 3 mercados mais baratos de cada produto",
  "5 comparações completas por dia",
  "1 lista com até 10 produtos",
  "2 alertas de preço",
];

const premiumItems = [
  "Todos os mercados da cidade, sem limite diário",
  "Listas ilimitadas, com o que comprar em cada mercado",
  "Rota até os mercados da sua compra",
  "Histórico de 90 dias e alertas ilimitados",
];

export function PricingSection({ signUpPath }: { signUpPath: string }) {
  const [reais, cents] = formatCurrency(BRAND.monthlyPrice).replace("R$", "").trim().split(",");

  return (
    <section id="preco" className="landing-pricing scroll-mt-24" aria-labelledby="pricing-title">
      <div className="landing-container">
        <div className="landing-pricing__intro">
          <h2 id="pricing-title" className="landing-heading__title text-white">Comece grátis. Assine se valer a pena.</h2>
          <p className="landing-pricing__text">
            O plano grátis já compara de verdade. O Premium mostra a cidade inteira e o caminho mais barato.
          </p>
        </div>

        <div className="landing-pricing__plans">
          <article className="pricing-card pricing-card--free">
            <p className="pricing-card__name">Grátis</p>
            <p className="pricing-card__price">
              <span className="pricing-card__currency">R$</span>0
            </p>
            <p className="text-sm text-muted">Para sempre, sem cartão.</p>
            <ul className="pricing-card__items">
              {freeItems.map((item) => (
                <li key={item}><Check className="mt-0.5 size-4 shrink-0 text-success" aria-hidden />{item}</li>
              ))}
            </ul>
            <NativeButton to={signUpPath} variant="secondary" size="lg" className="landing-button w-full">
              Criar conta grátis
            </NativeButton>
          </article>

          <article className="pricing-card pricing-card--premium">
            <div className="flex items-center justify-between gap-3">
              <p className="pricing-card__name">Premium</p>
              <span className="rounded-full bg-primary px-2.5 py-1 text-xs font-bold text-white">7 dias grátis</span>
            </div>
            <p className="pricing-card__price" aria-label={`${formatCurrency(BRAND.monthlyPrice)} por mês`}>
              <span className="pricing-card__currency" aria-hidden>R$</span>
              <span aria-hidden>{reais}</span>
              <span className="pricing-card__cents" aria-hidden>,{cents}</span>
              <span className="pricing-card__period" aria-hidden>/mês</span>
            </p>
            <p className="text-sm text-muted">
              Ou {formatCurrency(BRAND.annualPrice)} por ano, que sai a {formatCurrency(BRAND.annualPrice / 12)}/mês.
            </p>
            <ul className="pricing-card__items">
              {premiumItems.map((item) => (
                <li key={item}><Check className="mt-0.5 size-4 shrink-0 text-primary" aria-hidden />{item}</li>
              ))}
            </ul>
            <NativeButton to="/assinar" size="lg" glow className="landing-button w-full">
              Testar 7 dias grátis
            </NativeButton>
          </article>
        </div>
      </div>
    </section>
  );
}
