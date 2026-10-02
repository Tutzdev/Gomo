import {
  BellRing,
  Check,
  GitCompareArrows,
  Home,
  ListChecks,
  Pause,
  Play,
  Search,
  Store,
  UserRound,
} from "lucide-react";
import { useEffect, useState, type CSSProperties } from "react";
import gomoIcon from "../../../../gomo-icone.png";
import { formatCurrency } from "@/lib/brand";
import { cn } from "@/lib/cn";
import { demoListTotals, demoProducts, sortByPrice, type DemoProduct } from "./demo-data";
import { usePrefersReducedMotion, useRevealOnce, useTypedText } from "./motion-hooks";

const AUTOPLAY_INTERVAL_MS = 5200;

const sidebarItems = [
  { icon: Home, label: "Início" },
  { icon: GitCompareArrows, label: "Comparar", active: true },
  { icon: ListChecks, label: "Listas" },
  { icon: BellRing, label: "Alertas" },
  { icon: Store, label: "Lojas" },
];

export function HeroShowcase() {
  const reducedMotion = usePrefersReducedMotion();
  const { ref, revealed: inView } = useRevealOnce<HTMLDivElement>(0.2);
  const [activeIndex, setActiveIndex] = useState(0);
  const [autoplay, setAutoplay] = useState(true);

  const product = demoProducts[activeIndex];
  const { typed, done } = useTypedText(product.name, !reducedMotion);
  const playing = autoplay && !reducedMotion && inView;

  useEffect(() => {
    if (!playing || !done) return;
    const timeout = window.setTimeout(
      () => setActiveIndex((current) => (current + 1) % demoProducts.length),
      AUTOPLAY_INTERVAL_MS,
    );
    return () => window.clearTimeout(timeout);
  }, [playing, done, activeIndex]);

  function selectProduct(index: number) {
    setAutoplay(false);
    setActiveIndex(index);
  }

  return (
    <div ref={ref} className="hero-showcase">
      <div className="hero-showcase__tilt">
        <div className="hero-window" role="group" aria-label="Demonstração da comparação de preços com dados ilustrativos">
          <div className="hero-window__bar">
            <span aria-hidden />
            <span aria-hidden />
            <span aria-hidden />
            <p aria-hidden>gomo.app/comparar</p>
            <div className="hero-showcase__meta">
              <span className="hero-showcase__tag">Exemplo ilustrativo</span>
              {!reducedMotion ? (
                <button
                  type="button"
                  className="hero-showcase__pause"
                  onClick={() => setAutoplay((current) => !current)}
                  aria-label={autoplay ? "Pausar a troca automática de exemplos" : "Retomar a troca automática de exemplos"}
                >
                  {autoplay ? <Pause className="size-3" aria-hidden /> : <Play className="size-3" aria-hidden />}
                </button>
              ) : null}
            </div>
          </div>

          <div className="hero-window__body">
            <nav className="hero-window__sidebar" aria-hidden>
              <img src={gomoIcon} alt="" className="size-7" />
              {sidebarItems.map(({ icon: Icon, label, active }) => (
                <span key={label} className={cn("hero-window__nav-item", active && "is-active")}>
                  <Icon className="size-4" />
                  {label}
                </span>
              ))}
            </nav>

            <div className="hero-window__main">
              <div className="hero-search" aria-hidden>
                <Search className="size-4 shrink-0 text-muted" />
                <span className="truncate">{typed}</span>
                {!done ? <span className="hero-search__caret" /> : null}
              </div>

              <div className="hero-chips" role="group" aria-label="Escolher exemplo de produto">
                {demoProducts.map((item, index) => (
                  <button
                    key={item.id}
                    type="button"
                    className="hero-chip"
                    aria-pressed={index === activeIndex}
                    onClick={() => selectProduct(index)}
                  >
                    {item.shortName}
                  </button>
                ))}
              </div>

              <ComparisonResult product={product} ready={done} />
            </div>
          </div>
        </div>
      </div>

      <ListPhone />
    </div>
  );
}

function ComparisonResult({ product, ready }: { product: DemoProduct; ready: boolean }) {
  const sorted = sortByPrice(product.prices);
  const highest = sorted[sorted.length - 1].price;
  const lowest = sorted[0].price;

  return (
    <div className="hero-result">
      <div className="hero-result__header">
        <div className="min-w-0 flex-[1_1_11rem]">
          <p className="truncate font-bold">{product.name}</p>
          <p className="text-xs text-muted">{sorted.length} lojas com preço atual</p>
        </div>
        <span className="hero-result__count">{ready ? `Até ${formatCurrency(highest - lowest)} de diferença` : "Comparando…"}</span>
      </div>

      <ol className="hero-result__rows">
        {sorted.map((entry, index) => (
          <li
            key={`${product.id}-${entry.store}`}
            className={cn("hero-row", index === 0 && "is-cheapest", ready && "is-ready")}
            style={{ "--row-index": index, "--bar-scale": (entry.price / highest).toFixed(3) } as CSSProperties}
          >
            <span className="hero-row__store">
              {entry.store}
              {index === 0 ? (
                <span className="hero-row__badge">
                  <Check className="size-3" aria-hidden />
                  Menor preço
                </span>
              ) : null}
            </span>
            <span className="hero-row__bar" aria-hidden>
              <span />
            </span>
            <span className="hero-row__price">{formatCurrency(entry.price)}</span>
          </li>
        ))}
      </ol>
    </div>
  );
}

function ListPhone() {
  const totals = sortByPrice(demoListTotals);
  const highest = totals[totals.length - 1].price;
  const savings = totals[1].price - totals[0].price;

  return (
    <div className="hero-phone" aria-hidden>
      <div className="hero-phone__notch" />
      <p className="hero-phone__title">Compra do mês</p>
      <p className="hero-phone__subtitle">18 itens na lista</p>

      <ol className="hero-phone__totals">
        {totals.map((entry, index) => (
          <li key={entry.store} className={cn(index === 0 && "is-cheapest")}>
            <div className="flex items-baseline justify-between gap-2">
              <span className="truncate">{entry.store}</span>
              <strong>{formatCurrency(entry.price)}</strong>
            </div>
            <span className="hero-phone__bar" style={{ "--bar-scale": (entry.price / highest).toFixed(3) } as CSSProperties}>
              <span />
            </span>
          </li>
        ))}
      </ol>

      <p className="hero-phone__savings">
        {formatCurrency(savings)} a menos que a segunda opção
      </p>

      <div className="hero-phone__tabs">
        <Home className="size-4" />
        <ListChecks className="size-4 text-primary" />
        <BellRing className="size-4" />
        <UserRound className="size-4" />
      </div>
    </div>
  );
}
