import { useQuery } from "@tanstack/react-query";
import { LineChart, Lock } from "lucide-react";
import { useState } from "react";
import { EmptyState, ErrorState, LoadingState } from "@/components/ui/feedback";
import { SelectField } from "@/components/ui/form-field";
import { formatCurrency } from "@/lib/brand";
import { catalogApi } from "@/services/gomo-api";
import { PriceHistoryChart } from "./price-history-chart";
import { buildHistoryDays, formatDay } from "./price-history-data";
import { useUpsell } from "./upsell-context";
import { usePlan } from "./use-plan";

/** How the price of a generic item moved in the city: the chart for Premium, a locked preview for free. */
export function PriceHistoryPanel({ itemId, cityId, storeIds }: { itemId: string; cityId: string; storeIds?: string[] }) {
  const { premium } = usePlan();

  return (
    <section className="surface mt-5 overflow-hidden" aria-labelledby={`history-${itemId}`}>
      <header className="flex flex-wrap items-center justify-between gap-3 border-b border-border px-4 py-4 sm:px-5">
        <div>
          <h3 id={`history-${itemId}`} className="flex items-center gap-2 font-bold">
            <LineChart className="size-4 text-primary" aria-hidden />
            Variação de preço
          </h3>
          <p className="mt-0.5 text-sm text-muted">Menor preço da cidade em cada dia, nos últimos 90 dias.</p>
        </div>
        {!premium ? <span className="rounded-full bg-primary-soft px-2.5 py-1 text-xs font-bold text-primary-dark">Premium</span> : null}
      </header>
      {premium ? <PremiumHistory itemId={itemId} cityId={cityId} storeIds={storeIds} /> : <LockedPriceHistory />}
    </section>
  );
}

function PremiumHistory({ itemId, cityId, storeIds }: { itemId: string; cityId: string; storeIds?: string[] }) {
  const [selectedStoreId, setSelectedStoreId] = useState("");
  const history = useQuery({
    queryKey: ["catalog-item-history", itemId, cityId, storeIds],
    queryFn: () => catalogApi.catalogItemHistory(itemId, cityId, storeIds),
  });

  if (history.isPending) return <LoadingState label="Carregando histórico…" />;
  if (history.isError) return <ErrorState message={history.error.message} retry={() => void history.refetch()} />;
  if (!history.data.stores.length) {
    return (
      <div className="p-5">
        <EmptyState title="Ainda sem histórico" description="Assim que houver coletas deste produto nos mercados, a variação aparece aqui." />
      </div>
    );
  }

  const days = buildHistoryDays(history.data, selectedStoreId);
  const selectedStore = history.data.stores.find((store) => store.storeId === selectedStoreId) ?? null;

  return (
    <div className="p-4 sm:p-5">
      <div className="flex flex-wrap items-end justify-between gap-4">
        <ul className="flex flex-wrap gap-x-5 gap-y-2 text-xs text-muted" aria-label="Legenda">
          <li className="flex items-center gap-2"><span className="h-0.5 w-5 rounded-full bg-[#d62a23]" aria-hidden />Menor preço da cidade</li>
          {selectedStore ? (
            <li className="flex items-center gap-2"><span className="h-0.5 w-5 rounded-full bg-[#2a78d6]" aria-hidden />{selectedStore.storeName}</li>
          ) : null}
          <li className="flex items-center gap-2"><span className="h-3 w-5 rounded-sm bg-[#d62a23]/12" aria-hidden />Do mais barato ao mais caro</li>
        </ul>
        <div className="w-full max-w-xs">
          <SelectField id={`history-store-${itemId}`} label="Comparar com um mercado" value={selectedStoreId} onChange={(event) => setSelectedStoreId(event.target.value)}>
            <option value="">Nenhum</option>
            {history.data.stores.map((store) => <option key={store.storeId} value={store.storeId}>{store.storeName}</option>)}
          </SelectField>
        </div>
      </div>

      <div className="mt-4">
        <PriceHistoryChart days={days} selectedStoreName={selectedStore?.storeName ?? null} />
      </div>
      <p className="mt-2 text-xs text-muted">
        {days.length < 7
          ? `O Gomo tem preços deste produto desde ${formatDay(days[0].day)}; o gráfico cresce a cada coleta, até 90 dias.`
          : `Desde ${formatDay(days[0].day)}. Dias sem coleta não têm ponto.`}
      </p>

      <details className="mt-3 text-sm">
        <summary className="cursor-pointer font-semibold text-primary-dark">Ver como tabela</summary>
        <div className="mt-3 overflow-x-auto">
          <table className="data-table min-w-[30rem]">
            <caption className="sr-only">Menor e maior preço da cidade por dia</caption>
            <thead>
              <tr><th>Dia</th><th>Menor preço</th><th>Mercado</th><th>Maior preço</th>{selectedStore ? <th>{selectedStore.storeName}</th> : null}</tr>
            </thead>
            <tbody>
              {[...days].reverse().map((day) => (
                <tr key={day.day}>
                  <td>{formatDay(day.day)}</td>
                  <td className="tabular-nums">{formatCurrency(day.lowest)}</td>
                  <td>{day.lowestStore}</td>
                  <td className="tabular-nums">{formatCurrency(day.highest)}</td>
                  {selectedStore ? <td className="tabular-nums">{day.selected === null ? "Sem coleta" : formatCurrency(day.selected)}</td> : null}
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </details>
    </div>
  );
}

/** A decorative, blurred chart: no real prices are loaded for free accounts. */
export function LockedPriceHistory() {
  const { showUpsell } = useUpsell();
  return (
    <div className="locked-history">
      <svg className="locked-history__chart" viewBox="0 0 600 180" preserveAspectRatio="none" aria-hidden>
        {[30, 75, 120, 165].map((line) => <line key={line} x1="0" x2="600" y1={line} y2={line} />)}
        <path className="locked-history__band" d="M0,70 C80,50 140,90 220,60 C300,30 360,80 440,55 C500,40 560,60 600,45 L600,120 C540,135 480,110 420,130 C340,150 280,115 200,135 C120,150 60,120 0,130 Z" />
        <path className="locked-history__line" d="M0,120 C80,140 120,100 200,118 C280,135 330,95 420,112 C490,125 540,90 600,98" />
      </svg>
      <div className="locked-history__overlay">
        <span className="grid size-10 place-items-center rounded-full bg-white text-primary shadow-sm"><Lock className="size-5" aria-hidden /></span>
        <p className="mt-3 max-w-xs text-center font-display text-lg font-bold leading-snug">Veja como o preço variou nos últimos 90 dias</p>
        <p className="mt-1 max-w-xs text-center text-sm text-[#4d3f3e]">Saiba se o preço de hoje é promoção de verdade ou o mesmo de sempre.</p>
        <button type="button" className="locked-history__button" onClick={() => showUpsell("PRICE_HISTORY")}>
          Ver histórico no Premium
        </button>
      </div>
    </div>
  );
}
