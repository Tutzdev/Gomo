import { useEffect, useRef, useState, type KeyboardEvent, type PointerEvent } from "react";
import { formatCurrency } from "@/lib/brand";
import { dayTime, formatDay, type HistoryDay } from "./price-history-data";

const HEIGHT = 220;
const MARGIN = { top: 14, right: 16, bottom: 28, left: 56 };

function useElementWidth<T extends HTMLElement>() {
  const ref = useRef<T>(null);
  const [width, setWidth] = useState(0);
  useEffect(() => {
    const element = ref.current;
    if (!element) return;
    const observer = new ResizeObserver(([entry]) => setWidth(Math.floor(entry.contentRect.width)));
    observer.observe(element);
    return () => observer.disconnect();
  }, []);
  return { ref, width };
}

/** Round price steps (R$ 0,50, R$ 1, R$ 2...) inside the visible range, so the axis reads like money. */
function niceTicks(min: number, max: number, count = 4) {
  const rawStep = (max - min) / (count - 1);
  const magnitude = 10 ** Math.floor(Math.log10(rawStep));
  const step = [1, 2, 2.5, 5, 10].map((multiplier) => multiplier * magnitude).find((candidate) => candidate >= rawStep) ?? rawStep;
  const ticks: number[] = [];
  for (let tick = Math.ceil(min / step) * step; tick <= max + 1e-9; tick += step) ticks.push(Number(tick.toFixed(2)));
  return ticks;
}

export function PriceHistoryChart({ days, selectedStoreName }: { days: HistoryDay[]; selectedStoreName: string | null }) {
  const { ref, width } = useElementWidth<HTMLDivElement>();
  const [activeIndex, setActiveIndex] = useState<number | null>(null);
  const plotWidth = Math.max(width - MARGIN.left - MARGIN.right, 1);
  const plotHeight = HEIGHT - MARGIN.top - MARGIN.bottom;

  const firstTime = dayTime(days[0].day);
  const lastTime = dayTime(days[days.length - 1].day);
  const values = days.flatMap((day) => [day.lowest, day.highest, ...(day.selected === null ? [] : [day.selected])]);
  const padding = Math.max((Math.max(...values) - Math.min(...values)) * 0.15, 0.5);
  const yMin = Math.max(0, Math.min(...values) - padding);
  const yMax = Math.max(...values) + padding;

  const x = (day: string) =>
    lastTime === firstTime ? plotWidth / 2 : ((dayTime(day) - firstTime) / (lastTime - firstTime)) * plotWidth;
  const y = (value: number) => plotHeight - ((value - yMin) / (yMax - yMin)) * plotHeight;

  const lowestPath = days.map((day, index) => `${index ? "L" : "M"}${x(day.day)},${y(day.lowest)}`).join(" ");
  const bandPath =
    days.map((day, index) => `${index ? "L" : "M"}${x(day.day)},${y(day.highest)}`).join(" ") +
    " " +
    [...days].reverse().map((day) => `L${x(day.day)},${y(day.lowest)}`).join(" ") +
    " Z";
  const selectedDays = days.filter((day) => day.selected !== null);
  const selectedPath = selectedDays.map((day, index) => `${index ? "L" : "M"}${x(day.day)},${y(day.selected!)}`).join(" ");
  const xTicks = days.length <= 6 ? days : [days[0], days[Math.floor(days.length / 2)], days[days.length - 1]];

  const nearestIndex = (pointerX: number) => {
    let best = 0;
    days.forEach((day, index) => {
      if (Math.abs(x(day.day) - pointerX) < Math.abs(x(days[best].day) - pointerX)) best = index;
    });
    return best;
  };

  const handlePointer = (event: PointerEvent<SVGRectElement>) => {
    const bounds = event.currentTarget.getBoundingClientRect();
    setActiveIndex(nearestIndex(event.clientX - bounds.left));
  };

  const handleKey = (event: KeyboardEvent<HTMLDivElement>) => {
    if (event.key !== "ArrowLeft" && event.key !== "ArrowRight") return;
    event.preventDefault();
    setActiveIndex((current) => {
      const start = current ?? (event.key === "ArrowLeft" ? days.length : -1);
      return Math.min(days.length - 1, Math.max(0, start + (event.key === "ArrowRight" ? 1 : -1)));
    });
  };

  const active = activeIndex === null ? null : days[activeIndex];
  const latest = days[days.length - 1];

  return (
    <div
      ref={ref}
      className="price-history-chart"
      tabIndex={0}
      role="group"
      aria-label={`Gráfico do menor preço da cidade por dia. Último dia: ${formatCurrency(latest.lowest)} em ${latest.lowestStore}. Use as setas para percorrer os dias.`}
      onKeyDown={handleKey}
      onBlur={() => setActiveIndex(null)}
    >
      {width > 0 ? (
        <svg width={width} height={HEIGHT} aria-hidden>
          <g transform={`translate(${MARGIN.left},${MARGIN.top})`}>
            {niceTicks(yMin, yMax).map((tick) => (
              <g key={tick}>
                <line x1={0} x2={plotWidth} y1={y(tick)} y2={y(tick)} className="price-history-chart__grid" />
                <text x={-10} y={y(tick)} dy="0.32em" textAnchor="end" className="price-history-chart__axis">
                  {formatCurrency(tick)}
                </text>
              </g>
            ))}
            {xTicks.map((day) => (
              <text key={day.day} x={x(day.day)} y={plotHeight + 20} textAnchor="middle" className="price-history-chart__axis">
                {formatDay(day.day)}
              </text>
            ))}
            <path d={bandPath} className="price-history-chart__band" />
            {selectedDays.length ? <path d={selectedPath} className="price-history-chart__line price-history-chart__line--selected" /> : null}
            <path d={lowestPath} className="price-history-chart__line price-history-chart__line--lowest" />
            {days.map((day) => (
              <circle key={day.day} cx={x(day.day)} cy={y(day.lowest)} r={4} className="price-history-chart__dot price-history-chart__dot--lowest" />
            ))}
            {selectedDays.map((day) => (
              <circle key={day.day} cx={x(day.day)} cy={y(day.selected!)} r={4} className="price-history-chart__dot price-history-chart__dot--selected" />
            ))}
            {active ? (
              <line x1={x(active.day)} x2={x(active.day)} y1={0} y2={plotHeight} className="price-history-chart__crosshair" />
            ) : null}
            <rect
              width={plotWidth}
              height={plotHeight}
              fill="transparent"
              onPointerMove={handlePointer}
              onPointerDown={handlePointer}
              onPointerLeave={() => setActiveIndex(null)}
            />
          </g>
        </svg>
      ) : (
        <div style={{ height: HEIGHT }} />
      )}
      {active ? (
        <div
          className="price-history-chart__tooltip"
          style={{ left: Math.min(Math.max(MARGIN.left + x(active.day), 90), Math.max(width - 90, 90)) }}
          role="status"
        >
          <p className="font-bold">{formatDay(active.day)}</p>
          <p>
            Menor: <strong className="tabular-nums">{formatCurrency(active.lowest)}</strong> em {active.lowestStore}
          </p>
          {selectedStoreName && active.selected !== null ? (
            <p>
              {selectedStoreName}: <strong className="tabular-nums">{formatCurrency(active.selected)}</strong>
            </p>
          ) : null}
          <p className="text-muted">Maior: {formatCurrency(active.highest)}</p>
        </div>
      ) : null}
    </div>
  );
}
