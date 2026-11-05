import { useRef, useState, type CSSProperties, type PointerEvent } from "react";
import { useDefaultCityId } from "@/lib/use-default-city";
import { marketLogo } from "./market-logos";
import { usePrefersReducedMotion } from "./motion-hooks";
import { useCityMarkets, type CityMarkets } from "./use-city-markets";
import { useMarquee } from "./use-marquee";
import { useScrollReveal, type Reveals } from "./use-scroll-reveal";
import "./markets.css";

/** The section mounts after its data arrives, so it runs its own reveal instead of the page's. */
const REVEALS: Reveals = [
  [".markets__head", "rise"],
  [".markets__viewport, .markets__static", "fade"],
];

/** Slow enough to read a name while it passes. */
const PIXELS_PER_SECOND = 38;
/** Each copy of the row holds at least this many logos, so a city with few markets still fills the screen. */
const MIN_ITEMS_PER_COPY = 12;

/**
 * Right below the hero: every market the Gomo already compares, as a moving row of their logos in gray. Seeing
 * the stores they already shop at is the first proof that the comparison is real. The visitor's city comes
 * first, then the other cities' markets.
 */
export function MarketsSection() {
  const cities = useCityMarkets();
  const defaultCityId = useDefaultCityId();
  const chains = allChains(cities, defaultCityId);

  if (!chains.length) return <section className="markets markets--loading" aria-hidden />;
  return <MarketsContent chains={chains} />;
}

function MarketsContent({ chains }: { chains: CityMarkets["chains"] }) {
  const sectionRef = useRef<HTMLElement>(null);
  useScrollReveal(sectionRef, REVEALS);

  return (
    <section ref={sectionRef} className="markets" aria-labelledby="markets-title">
      <div className="markets__head">
        <h2 id="markets-title" className="markets__title">
          {chains.length === 1 ? (
            <>O mercado que já está no Gomo</>
          ) : (
            <>
              Os <strong className="markets__count">{chains.length} mercados</strong> que já estão no Gomo
            </>
          )}
        </h2>
        <p className="markets__text">Do atacado ao mercado do bairro, com o preço de cada um lado a lado.</p>
      </div>
      <MarketRow chains={chains} />
    </section>
  );
}

/** Each chain once across every city (Bramil in two cities shows one Bramil), the visitor's city first. */
function allChains(cities: CityMarkets[], firstCityId: string) {
  const ordered = [...cities].sort((a, b) => Number(b.id === firstCityId) - Number(a.id === firstCityId));
  const seen = new Set<string>();
  return ordered.flatMap((city) =>
    city.chains.filter((chain) => {
      if (seen.has(chain.id)) return false;
      seen.add(chain.id);
      return true;
    }),
  );
}

function MarketRow({ chains }: { chains: CityMarkets["chains"] }) {
  const reducedMotion = usePrefersReducedMotion();
  const [paused, setPaused] = useState(false);
  const trackRef = useRef<HTMLDivElement>(null);
  const copies = Math.max(1, Math.ceil(MIN_ITEMS_PER_COPY / chains.length));
  const contentKey = chains.map((chain) => chain.id).join(",");
  useMarquee(trackRef, { pixelsPerSecond: PIXELS_PER_SECOND, paused: paused || reducedMotion, contentKey });

  if (reducedMotion) {
    return (
      <ul className="markets__static">
        {chains.map((chain) => (
          <li key={chain.id}>
            <MarketLogoItem name={chain.name} />
          </li>
        ))}
      </ul>
    );
  }

  const group = (hidden: boolean) => (
    <ul className="markets__group" aria-hidden={hidden || undefined}>
      {Array.from({ length: copies }, (_, copy) =>
        chains.map((chain) => (
          // Only the first pass is read by screen readers; the repeats exist for the loop.
          <li key={`${copy}-${chain.id}`} aria-hidden={copy > 0 || undefined}>
            <MarketLogoItem name={chain.name} />
          </li>
        )),
      )}
    </ul>
  );

  return (
    <div
      className="markets__viewport"
      onPointerEnter={(event: PointerEvent) => event.pointerType === "mouse" && setPaused(true)}
      onPointerLeave={() => setPaused(false)}
    >
      <div ref={trackRef} className="markets__track">
        {group(false)}
        {group(true)}
      </div>
    </div>
  );
}

function MarketLogoItem({ name }: { name: string }) {
  const logo = marketLogo(name);
  if (!logo) {
    return <span className="market-logo market-logo--name">{displayName(name)}</span>;
  }
  const style = { "--logo-scale": logo.scale ?? 1 } as CSSProperties;
  if (logo.kind === "wordmark") {
    return (
      <span className={logo.light ? "market-logo market-logo--light" : "market-logo"} style={style}>
        <img src={logo.src} alt={name} className="market-logo__wordmark" loading="lazy" decoding="async" />
      </span>
    );
  }
  return (
    <span className="market-logo market-logo--mark" style={style}>
      <img src={logo.src} alt="" className="market-logo__mark" loading="lazy" decoding="async" />
      <span className="market-logo__name">{name}</span>
    </span>
  );
}

/** "Royal Supermercados" and "MAX Supermercado" are called by the first word; "Mercado Bom Jesus" keeps it. */
function displayName(chainName: string) {
  return chainName.replace(/\s+Supermercados?$/i, "");
}
