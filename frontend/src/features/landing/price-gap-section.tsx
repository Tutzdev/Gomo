import { useQuery } from "@tanstack/react-query";
import type { CSSProperties } from "react";
import { NativeButton } from "@/components/ui/native-button";
import { useAuth } from "@/features/auth/auth-context";
import { formatCurrency } from "@/lib/brand";
import { useDefaultCityId } from "@/lib/use-default-city";
import { catalogApi } from "@/services/gomo-api";
import type { CatalogItem } from "@/types/api";
import { useRevealOnce } from "./motion-hooks";

/** The catalog lists the products sold in the most stores first; one page of them is enough to find real gaps. */
const CANDIDATE_ITEMS = 60;
const ROWS_SHOWN = 4;
/** Only products priced in several stores, so the gap reflects the city and not one odd listing. */
const MIN_PRICED_STORES = 5;

interface PriceGap {
  item: CatalogItem;
  lowest: number;
  highest: number;
  percent: number;
}

/**
 * Real current prices: how much more the same product costs from one market to another in the same city
 * (the visitor's preferred city, or one that has markets). The section never names a city, since the
 * product is not tied to one. Nothing here is illustrative; without data it keeps the message and drops
 * the numbers.
 */
export function PriceGapSection({ ctaPath }: { ctaPath: string }) {
  const gaps = usePriceGaps();
  const { ref, revealed } = useRevealOnce<HTMLDivElement>(0.05);
  const biggest = gaps.data[0];
  const collectedAt = latestCollection(gaps.data);

  return (
    <section className="price-gap" aria-labelledby="price-gap-title">
      <div className="landing-container price-gap__grid">
        <div className="price-gap__copy">
          <p className="price-gap__live">
            <span className="price-gap__pulse" aria-hidden />
            Preços atuais, direto dos mercados
          </p>
          <h2 id="price-gap-title" className="price-gap__title">
            {biggest
              ? `O mesmo produto custa até ${biggest.percent}% mais no mercado ao lado.`
              : "O mesmo produto muda muito de preço de um mercado para o outro."}
          </h2>
          <p className="price-gap__text">
            Quem compra sempre no mesmo lugar paga essa diferença sem perceber. O Gomo mostra antes de você sair de casa.
          </p>
          <NativeButton to={ctaPath} size="lg" glow className="landing-button mt-8">
            Comparar meus produtos
          </NativeButton>
        </div>

        <div ref={ref} className="price-gap__board" data-revealed={revealed}>
          {gaps.isLoading ? (
            <PriceGapSkeleton />
          ) : gaps.data.length ? (
            <>
              <ol className="price-gap__rows">
                {gaps.data.map((gap, index) => (
                  <PriceGapRow key={gap.item.id} gap={gap} index={index} maxPercent={biggest.percent} />
                ))}
              </ol>
              {collectedAt ? (
                <p className="price-gap__footnote">
                  Menor e maior preço atual entre mercados de uma mesma cidade. Atualizado {collectedAt}.
                </p>
              ) : null}
            </>
          ) : null}
        </div>
      </div>
    </section>
  );
}

function PriceGapRow({ gap, index, maxPercent }: { gap: PriceGap; index: number; maxPercent: number }) {
  const style = { "--gap-index": index, "--gap-width": `${Math.max(gap.percent / maxPercent, 0.28) * 100}%` } as CSSProperties;
  return (
    <li className="price-gap__row" style={style}>
      <div className="flex items-baseline justify-between gap-3">
        <p className="truncate font-bold">{gap.item.name}</p>
        <p className="price-gap__percent">+{gap.percent}%</p>
      </div>
      <div className="price-gap__track" aria-hidden>
        <span className="price-gap__bar" />
      </div>
      <div className="flex justify-between gap-3">
        <p className="price-gap__price">
          <span>Mais barato</span>
          <strong className="text-success">{formatCurrency(gap.lowest)}</strong>
        </p>
        <p className="price-gap__price text-right">
          <span>Mais caro</span>
          <strong>{formatCurrency(gap.highest)}</strong>
        </p>
      </div>
    </li>
  );
}

function PriceGapSkeleton() {
  return (
    <div className="price-gap__rows" aria-label="Carregando preços atuais">
      {Array.from({ length: ROWS_SHOWN }, (_, index) => (
        <div key={index} className="price-gap__row price-gap__row--skeleton">
          <span />
          <span />
        </div>
      ))}
    </div>
  );
}

function usePriceGaps() {
  const { user } = useAuth();
  const cityId = useDefaultCityId({ signedIn: Boolean(user) });
  // One request instead of a search per product: each catalog query prices the whole city on the server.
  const candidates = useQuery({
    queryKey: ["landing-price-gaps", cityId],
    queryFn: () => catalogApi.catalogItems({ cityId, size: CANDIDATE_ITEMS }),
    enabled: Boolean(cityId),
    staleTime: 10 * 60 * 1000,
  });

  const gaps = (candidates.data?.content ?? [])
    .flatMap((item) => {
      const gap = toPriceGap(item);
      return gap ? [gap] : [];
    })
    .sort((first, second) => second.percent - first.percent)
    .slice(0, ROWS_SHOWN);

  return { data: gaps, isLoading: !cityId || candidates.isPending };
}

function toPriceGap(item: CatalogItem): PriceGap | null {
  if (item.lowestPrice === null || item.highestPrice === null || item.pricedStores < MIN_PRICED_STORES) return null;
  if (item.highestPrice <= item.lowestPrice) return null;
  return {
    item,
    lowest: item.lowestPrice,
    highest: item.highestPrice,
    percent: Math.round((item.highestPrice / item.lowestPrice - 1) * 100),
  };
}

function latestCollection(gaps: PriceGap[]) {
  const times = gaps.flatMap((gap) => (gap.item.pricesCollectedAt ? [new Date(gap.item.pricesCollectedAt).getTime()] : []));
  if (!times.length) return null;
  const latest = new Date(Math.max(...times));
  const time = new Intl.DateTimeFormat("pt-BR", { hour: "2-digit", minute: "2-digit" }).format(latest);
  if (latest.toDateString() === new Date().toDateString()) return `hoje às ${time}`;
  return `em ${new Intl.DateTimeFormat("pt-BR", { day: "2-digit", month: "2-digit" }).format(latest)} às ${time}`;
}
