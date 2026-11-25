import { priceByMeasure } from "./unit-price";
import type { MeasurementPrice as Measurement } from "@/types/api";

export function MeasurementPrice({
  value,
}: {
  value: Measurement | null | undefined;
}) {
  if (!value) {
    return null;
  }

  return (
    <p className="text-xs text-muted">
      {priceByMeasure(value.amount, value.unit)}
    </p>
  );
}
