import type { CSSProperties } from "react";
import {
  CircleDollarSign,
  Clock3,
  House,
  ListChecks,
  Lock,
  MapPin,
  Plus,
  Search,
  Settings,
  Share2,
  Store,
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
 * O miolo é a tela de comparação do app: o produto, quanto dá para economizar e o ranking dos mercados.
 * É uma figura: o leitor de tela ouve a legenda, não cada número da tela.
 */
export function HeroShowcase({ comparison }: { comparison: HeroComparison }) {
  const { product, offers, lowest, highest, cityName } = comparison;
  const rows = offers.slice(0, VISIBLE_ROWS);
  const cheapest = rows[0]?.price ?? lowest;
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

            <section className="hero-board__compare">
              <header className="hero-board__product">
                <div className="min-w-0">
                  <p className="hero-board__product-name">{product.name}</p>
                  <p className="hero-board__product-meta">
                    Preço atual em {comparison.pricedStores} mercados de {cityName}
                  </p>
                </div>
                <span className="hero-board__actions">
                  <span className="hero-board__button">
                    <Plus />À lista
                  </span>
                  <span className="hero-board__button hero-board__button--icon">
                    <Share2 />
                  </span>
                </span>
              </header>

              {highest > lowest ? (
                <p className="hero-board__savings">
                  Comprando no mais barato você economiza até <strong>{formatCurrency(highest - lowest)}</strong> por
                  unidade.
                </p>
              ) : null}

              <ol className="hero-board__ranking">
                {rows.map((offer, index) => (
                  <li
                    key={offer.store}
                    className={cn(index === 0 && "is-cheapest")}
                    style={{ "--row": index } as CSSProperties}
                  >
                    <span className="hero-board__rank">{index + 1}</span>
                    <span className="hero-board__store">
                      <Store />
                      <span className="truncate">{offer.store}</span>
                    </span>
                    <span className="hero-board__price">
                      <strong>{formatCurrency(offer.price)}</strong>
                      <span>{index === 0 ? "Mais barato" : `+${formatCurrency(offer.price - cheapest)}`}</span>
                    </span>
                  </li>
                ))}
                {comparison.lockedStores > 0 ? (
                  <li className="hero-board__locked" style={{ "--row": rows.length } as CSSProperties}>
                    <Lock />
                    Mais {comparison.lockedStores} mercados no Premium
                  </li>
                ) : null}
              </ol>
            </section>
          </div>
        </div>
      </div>
    </figure>
  );
}
