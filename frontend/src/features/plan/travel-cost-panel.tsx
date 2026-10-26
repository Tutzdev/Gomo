import { Bus, Car, Footprints, type LucideIcon } from "lucide-react";
import { useState } from "react";
import { TextField } from "@/components/ui/form-field";
import { formatCurrency } from "@/lib/brand";
import { cn } from "@/lib/cn";
import {
  estimateTravel,
  loadTravelSettings,
  saveTravelSettings,
  type TravelMode,
  type TravelSettings,
} from "./travel-cost";

const modes: Array<{ mode: TravelMode; label: string; icon: LucideIcon }> = [
  { mode: "CAR", label: "Carro", icon: Car },
  { mode: "BUS", label: "Ônibus", icon: Bus },
  { mode: "WALK", label: "A pé", icon: Footprints },
];

const formatKm = (value: number) =>
  `${value.toLocaleString("pt-BR", { minimumFractionDigits: 1, maximumFractionDigits: 1 })} km`;

/**
 * Whether the split still pays off once the trip is counted: "R$ 12 cheaper" can become R$ 4 after the fuel of a
 * detour across town, or nothing at all.
 */
export function TravelCostPanel({
  savings,
  splitRoundTripKm,
  singleRoundTripKm,
  stops,
  referenceName,
}: {
  savings: number;
  splitRoundTripKm: number;
  singleRoundTripKm: number;
  stops: number;
  referenceName: string;
}) {
  const [settings, setSettings] = useState<TravelSettings>(loadTravelSettings);
  const update = (change: Partial<TravelSettings>) => {
    const next = { ...settings, ...change };
    setSettings(next);
    saveTravelSettings(next);
  };
  const estimate = estimateTravel(settings, savings, splitRoundTripKm, singleRoundTripKm, stops);
  const worthIt = estimate.netSavings > 0;

  return (
    <div className="mt-4 rounded-lg border border-border p-4">
      <p className="text-sm font-semibold" id="travel-mode-label">Como você vai?</p>
      <div className="mt-2 inline-flex rounded-full bg-surface-strong p-1" role="group" aria-labelledby="travel-mode-label">
        {modes.map(({ mode, label, icon: Icon }) => (
          <button
            key={mode}
            type="button"
            aria-pressed={settings.mode === mode}
            onClick={() => update({ mode })}
            className={cn(
              "inline-flex min-h-9 items-center gap-1.5 rounded-full px-3 text-sm font-semibold focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-focus",
              settings.mode === mode ? "bg-primary text-white" : "text-muted hover:text-foreground",
            )}
          >
            <Icon className="size-4" aria-hidden />
            {label}
          </button>
        ))}
      </div>

      <p className={cn("mt-4 text-lg font-extrabold tabular-nums", worthIt ? "text-success" : "text-warning")}>
        {worthIt
          ? `Economia real: ${formatCurrency(estimate.netSavings)}`
          : "Não compensa dividir"}
      </p>
      <p className="mt-1 text-sm text-muted">
        {settings.mode === "WALK"
          ? `A pé o trajeto não custa nada: são ${formatKm(estimate.extraKm)} a mais, ida e volta.`
          : settings.mode === "CAR"
            ? `${formatKm(estimate.extraKm)} a mais de carro, ida e volta, gastam cerca de ${formatCurrency(estimate.extraCost)} de combustível.`
            : `${stops - 1 === 1 ? "1 ônibus a mais custa" : `${stops - 1} ônibus a mais custam`} ${formatCurrency(estimate.extraCost)}.`}{" "}
        {worthIt
          ? `Dos ${formatCurrency(savings)} economizados, sobram ${formatCurrency(estimate.netSavings)}.`
          : `O deslocamento come os ${formatCurrency(savings)} de economia: compre tudo no ${referenceName}.`}
      </p>

      {settings.mode !== "WALK" ? (
        <details className="mt-3 text-sm">
          <summary className="cursor-pointer font-semibold text-primary-dark">Ajustar valores</summary>
          <div className="mt-3 grid gap-3 sm:grid-cols-2">
            {settings.mode === "CAR" ? (
              <>
                <TextField
                  id="travel-km-per-liter"
                  label="Consumo (km por litro)"
                  type="number"
                  min="1"
                  step="0.1"
                  defaultValue={settings.kmPerLiter}
                  onChange={(event) => Number(event.target.value) > 0 && update({ kmPerLiter: Number(event.target.value) })}
                />
                <TextField
                  id="travel-fuel-price"
                  label="Preço do litro (R$)"
                  type="number"
                  min="0.01"
                  step="0.01"
                  defaultValue={settings.fuelPrice}
                  onChange={(event) => Number(event.target.value) > 0 && update({ fuelPrice: Number(event.target.value) })}
                />
              </>
            ) : (
              <TextField
                id="travel-bus-fare"
                label="Passagem (R$)"
                type="number"
                min="0.01"
                step="0.01"
                defaultValue={settings.busFare}
                onChange={(event) => Number(event.target.value) > 0 && update({ busFare: Number(event.target.value) })}
              />
            )}
          </div>
          <p className="mt-2 text-xs text-muted">
            Ruas somam cerca de 30% à distância em linha reta. Os valores ficam só neste navegador.
          </p>
        </details>
      ) : null}
    </div>
  );
}
