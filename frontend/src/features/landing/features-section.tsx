import { BellRing, Check, ListChecks, Search, Trophy } from "lucide-react";
import { formatCurrency } from "@/lib/brand";
import { cn } from "@/lib/cn";

/* Mini telas ilustrativas, com os mesmos nomes genéricos de lojas da demonstração da hero. */
const productRanking = [
  { store: "Mercado Central", price: 8.99 },
  { store: "Atacarejo Norte", price: 9.79 },
  { store: "Supermercado Avenida", price: 10.49 },
];

const listTotals = [
  { store: "Mercado Central", total: 287.4 },
  { store: "Supermercado Avenida", total: 301.15 },
  { store: "Atacarejo Norte", total: 309.8 },
];

const priceAlerts = [
  { product: "Café 500 g", price: 17.89, target: 18 },
  { product: "Azeite 500 ml", price: 29.9, target: 32 },
];

/** Os três jeitos de usar o Gomo, cada um com uma frase e uma mini tela. */
export function FeaturesSection() {
  return (
    <section id="como-funciona" className="landing-section scroll-mt-24" aria-labelledby="features-title">
      <div className="landing-container">
        <h2 id="features-title" className="landing-heading__title max-w-2xl">Três jeitos de pagar menos</h2>

        <div className="ways">
          <article className="way">
            <span className="way__icon"><Search className="size-5" aria-hidden /></span>
            <h3 className="way__title">Compare um produto</h3>
            <p className="way__text">Digite o que você quer. Na hora você vê quem cobra menos.</p>
            <ol className="way__screen" aria-hidden>
              {productRanking.map((row, index) => (
                <li key={row.store} className={cn("way__rank", index === 0 && "is-best")}>
                  <span className="way__rank-place">{index === 0 ? <Trophy className="size-3.5" /> : index + 1}</span>
                  <span className="min-w-0 flex-1 truncate">{row.store}</span>
                  <strong className="tabular-nums">{formatCurrency(row.price)}</strong>
                </li>
              ))}
            </ol>
          </article>

          <article className="way way--featured">
            <span className="way__icon"><ListChecks className="size-5" aria-hidden /></span>
            <h3 className="way__title">Compare a lista inteira</h3>
            <p className="way__text">Monte a compra da semana e veja quanto ela custa em cada mercado.</p>
            <div className="way__screen" aria-hidden>
              {listTotals.map((row, index) => (
                <div key={row.store} className="way__total">
                  <div className="flex items-baseline justify-between gap-3">
                    <span className="truncate">{row.store}</span>
                    <strong className="tabular-nums">{formatCurrency(row.total)}</strong>
                  </div>
                  <span className="way__total-bar" style={{ width: `${(row.total / listTotals[2].total) * 100}%` }} data-best={index === 0} />
                </div>
              ))}
              <p className="way__note">
                <Check className="size-3.5" />
                {formatCurrency(listTotals[1].total - listTotals[0].total)} a menos que a segunda opção
              </p>
            </div>
          </article>

          <article className="way">
            <span className="way__icon"><BellRing className="size-5" aria-hidden /></span>
            <h3 className="way__title">Seja avisado</h3>
            <p className="way__text">Diga quanto quer pagar. Quando um mercado chegar lá, você fica sabendo.</p>
            <ul className="way__screen way__alerts" aria-hidden>
              {priceAlerts.map((alert) => (
                <li key={alert.product} className="way__alert">
                  <span className="way__alert-icon"><BellRing className="size-4" /></span>
                  <div className="min-w-0">
                    <p className="font-bold">{alert.product} baixou para {formatCurrency(alert.price)}</p>
                    <p className="text-xs text-muted">Você queria pagar até {formatCurrency(alert.target)}</p>
                  </div>
                </li>
              ))}
            </ul>
          </article>
        </div>
        <p className="mt-4 text-xs text-muted">Telas ilustrativas.</p>
      </div>
    </section>
  );
}
