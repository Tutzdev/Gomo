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
 * Right below the hero: the markets the Gomo compares in the visitor's city, as a moving row of their logos in
 * gray. Seeing the stores they already shop at is the first proof that the comparison is real.
 */
export function MarketsSection() {
  const cities = useCityMarkets();
  const defaultCityId = useDefaultCityId();
  const [pickedCityId, setPickedCityId] = useState<string | null>(null);
  const city = cities.find((item) => item.id === pickedCityId) ?? cities.find((item) => item.id === defaultCityId) ?? cities[0];

  if (!city) return <section className="markets markets--loading" aria-hidden />;
  return <MarketsContent cities={cities} city={city} onPickCity={setPickedCityId} />;
}

function MarketsContent({ cities, city, onPickCity }: { cities: CityMarkets[]; city: CityMarkets; onPickCity: (id: string) => void }) {
  const sectionRef = useRef<HTMLElement>(null);
  useScrollReveal(sectionRef, REVEALS);

  return (
    <section ref={sectionRef} className="markets" aria-labelledby="markets-title">
      <div className="markets__head">
        <h2 id="markets-title" className="markets__title">
          Mercados comparados em{" "}
          {cities.length > 1 ? <span className="sr-only">{city.name}</span> : <span className="markets__city">{city.name}</span>}
        </h2>
        {cities.length > 1 ? (
          <div className="markets__cities" role="group" aria-label="Cidade">
            {cities.map((item) => (
              <button
                key={item.id}
                type="button"
                className="markets__city-button"
                aria-pressed={item.id === city.id}
                onClick={() => onPickCity(item.id)}
              >
                {item.name}
              </button>
            ))}
          </div>
        ) : null}
      </div>
      <MarketRow key={city.id} chains={city.chains} />
    </section>
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
