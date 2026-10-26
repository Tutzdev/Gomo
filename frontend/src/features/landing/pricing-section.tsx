import { Bell, BellRing, History, ListChecks, type LucideIcon, PackagePlus, PiggyBank, Route, Search, ShoppingCart, Store, Tag, Users } from "lucide-react";
import { NativeButton } from "@/components/ui/native-button";
import { BRAND, formatCurrency } from "@/lib/brand";
import "./pricing.css";

type PlanItem = { icon: LucideIcon; text: string };

const freeItems: PlanItem[] = [
  { icon: Store, text: "Os 3 mercados mais baratos de cada produto" },
  { icon: Search, text: "5 comparações completas por dia" },
  { icon: ListChecks, text: "1 lista com até 10 produtos" },
  { icon: Bell, text: "2 alertas de preço" },
  { icon: Tag, text: "Ofertas da semana em cada mercado" },
  { icon: ShoppingCart, text: "Modo “No mercado” e a economia do mês" },
];

const premiumItems: PlanItem[] = [
  { icon: Store, text: "Todos os mercados da cidade, sem limite diário" },
  { icon: Users, text: "Listas ilimitadas, compartilhadas com a casa" },
  { icon: Route, text: "Compra em até 2 mercados, com rota e quanto sobra depois da gasolina" },
  { icon: History, text: "Histórico de 90 dias com o veredito do preço" },
  { icon: BellRing, text: "Alertas ilimitados, também abaixo do preço de sempre" },
  { icon: PackagePlus, text: "Hora de estocar e equivalente mais barato por kg" },
  { icon: PiggyBank, text: "Economia do mês compra por compra" },
];

/** "R$ 14,90" vira "14,90": o símbolo da moeda é desenhado à parte, menor. */
const amount = (value: number) => formatCurrency(value).replace("R$", "").trim();

export function PricingSection({ signUpPath }: { signUpPath: string }) {
  return (
    <section id="preco" className="landing-section scroll-mt-20" aria-labelledby="pricing-title">
      <div className="landing-wide">
        <div className="plans">
          <div className="plans__intro">
            <h2 id="pricing-title" className="landing-heading__title">Comece grátis. Assine se valer a pena.</h2>
            <p className="plans__intro-text">
              O plano grátis já compara de verdade. O Premium mostra a cidade inteira e o caminho mais barato.
            </p>
          </div>

          <article className="plan" aria-labelledby="plan-free">
            <h3 id="plan-free" className="plan__name">Grátis</h3>
            <p className="plan__price" aria-label="R$ 0 por mês">
              <span className="plan__currency" aria-hidden>R$</span>
              <span aria-hidden>0</span>
              <span className="plan__period" aria-hidden>/mês</span>
            </p>
            <p className="plan__text">Para sempre, sem cartão.</p>
            <PlanList lead="Para começar:" items={freeItems} />
            <NativeButton to={signUpPath} variant="secondary" size="lg" className="landing-button plan__button">
              Criar conta grátis
            </NativeButton>
          </article>

          <article className="plan plan--premium" aria-labelledby="plan-premium">
            <div className="plan__head">
              <h3 id="plan-premium" className="plan__name">Premium</h3>
              <span className="plan__badge">7 dias grátis</span>
            </div>
            <p className="plan__price" aria-label={`${formatCurrency(BRAND.monthlyPrice)} por mês`}>
              <span className="plan__currency" aria-hidden>R$</span>
              <span aria-hidden>{amount(BRAND.monthlyPrice)}</span>
              <span className="plan__period" aria-hidden>/mês</span>
            </p>
            <p className="plan__text">
              Ou {formatCurrency(BRAND.annualPrice)} por ano, que sai a{" "}
              <strong>{formatCurrency(BRAND.annualPrice / 12)}/mês</strong>.
            </p>
            <PlanList lead="Tudo do Grátis, e mais:" items={premiumItems} />
            <NativeButton to="/assinar" size="lg" className="landing-button plan__button">
              Testar 7 dias grátis
            </NativeButton>
          </article>
        </div>
      </div>
    </section>
  );
}

function PlanList({ lead, items }: { lead: string; items: PlanItem[] }) {
  return (
    <div className="plan__features">
      <p className="plan__lead">{lead}</p>
      <ul className="plan__items">
        {items.map(({ icon: Icon, text }) => (
          <li key={text}>
            <Icon className="plan__icon" strokeWidth={1.75} aria-hidden />
            {text}
          </li>
        ))}
      </ul>
    </div>
  );
}
