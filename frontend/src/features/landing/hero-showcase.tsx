import type { CSSProperties } from "react";
import {
  ArrowDownRight,
  ArrowUpRight,
  CircleDollarSign,
  Clock3,
  House,
  ListChecks,
  Lock,
  MapPin,
  Search,
  Settings,
  Store,
  Trophy,
  type LucideIcon,
} from "lucide-react";
import gomoLogo from "../../../../gomo-logo.png";
import { formatCurrency } from "@/lib/brand";
import { cn } from "@/lib/cn";
import type { HeroComparison } from "./use-hero-comparison";

/* O menu lateral do dashboard, com os mesmos nomes, ícones e ordem. */
const sidebar: { label: string; icon: LucideIcon; active?: boolean }[] = [
  { label: "Início", icon: House },
  { label: "Buscar preços", icon: Search, active: true },
  { label: "Minhas listas", icon: ListChecks },
  { label: "Alertas de preço", icon: CircleDollarSign },
  { label: "Supermercados", icon: Store },
  { label: "Perfil", icon: Settings },
];

/** Rows shown in the ranking; the panel crops the bottom anyway. */
const VISIBLE_ROWS = 6;

/*
 * O painel do Gomo desenhado em código (nítido em qualquer tela), com a mesma comparação da frase de cima.
 * É uma figura: o leitor de tela ouve a legenda, não cada número da tela.
 */
export function HeroShowcase({ comparison }: { comparison: HeroComparison }) {
  const { product, offers, lowest, highest, cityName } = comparison;
  const savings = highest - lowest;
  const savingsPercent = Math.round((savings / highest) * 100);
  const rows = offers.slice(0, VISIBLE_ROWS);
  const updatedAt = comparison.collectedAt
    ? new Intl.DateTimeFormat("pt-BR", { hour: "2-digit", minute: "2-digit" }).format(comparison.collectedAt)
    : null;

  return (
    <figure className="hero-board">
      <figcaption className="sr-only">
        O painel do Gomo comparando {product.name} em {comparison.pricedStores} mercados de {cityName}, de{" "}
        {formatCurrency(lowest)} a {formatCurrency(highest)}.
      </figcaption>

      <div className="hero-board__frame" aria-hidden>
        <div className="hero-board__app">
          <aside className="hero-board__sidebar">
            <img src={gomoLogo} alt="" className="hero-board__logo" />
            <span className="hero-board__city">
              <MapPin />
              {cityName}
            </span>
            <ul>
              {sidebar.map(({ label, icon: Icon, active }) => (
                <li key={label} className={active ? "is-active" : undefined}>
                  <Icon />
                  {label}
                </li>
              ))}
            </ul>
          </aside>

          {/* A troca de produto refaz a entrada do painel, como quando o app carrega uma busca nova. */}
          <div key={product.id} className="hero-board__main">
            <div className="hero-board__toolbar">
              <span className="hero-board__search">
                <Search />
                <span className="truncate">{product.name}</span>
              </span>
              <span className="hero-board__chip">
                <Clock3 />
                {updatedAt ? `Atualizado às ${updatedAt}` : "4 coletas por dia"}
              </span>
            </div>

            <div className="hero-board__summary">
              <div className="hero-board__stat hero-board__stat--best">
                <span className="hero-board__label">Mais barato</span>
                <Money value={lowest} />
                <span className="hero-board__trend is-down">
                  <ArrowDownRight />
                  {offers[0].store}
                </span>
                <span className="hero-board__badge">
                  <Trophy />
                </span>
              </div>
              <div className="hero-board__stat">
                <span className="hero-board__label">Mais caro</span>
                <Money value={highest} />
                <span className="hero-board__trend is-up">
                  <ArrowUpRight />+{savingsPercent}% que o mais barato
                </span>
              </div>
            </div>

            <dl className="hero-board__facts">
              <div>
                <dt>Mercados com preço</dt>
                <dd>{comparison.pricedStores}</dd>
              </div>
              <div>
                <dt>Diferença</dt>
                <dd>{formatCurrency(savings)}</dd>
              </div>
              <div>
                <dt>Economia</dt>
                <dd className="text-primary">{savingsPercent}%</dd>
              </div>
              <div>
                <dt>Cidade</dt>
                <dd>{cityName}</dd>
              </div>
            </dl>

            <div className="hero-board__ranking">
              <p className="hero-board__ranking-title">
                <Store />
                Preço em cada mercado
              </p>
              <ol>
                {rows.map((offer, index) => (
                  <li
                    key={offer.store}
                    className={cn(index === 0 && "is-cheapest")}
                    style={{ "--row": index, "--share": offer.price / highest } as CSSProperties}
                  >
                    <span className="hero-board__rank">{index + 1}</span>
                    <span className="hero-board__store">{offer.store}</span>
                    <span className="hero-board__bar" />
                    <strong>{formatCurrency(offer.price)}</strong>
                  </li>
                ))}
                {comparison.lockedStores > 0 ? (
                  <li className="hero-board__locked" style={{ "--row": rows.length } as CSSProperties}>
                    <Lock />
                    Mais {comparison.lockedStores} mercados no Premium
                  </li>
                ) : null}
              </ol>
            </div>
          </div>
        </div>
      </div>
    </figure>
  );
}

/** "R$ 10,49" with the cents smaller, like the totals in the app. */
function Money({ value }: { value: number }) {
  const [reais, cents] = formatCurrency(value).replace("R$", "").trim().split(",");
  return (
    <span className="hero-board__money">
      <span className="hero-board__currency">R$</span>
      {reais}
      <span className="hero-board__cents">,{cents}</span>
    </span>
  );
}
