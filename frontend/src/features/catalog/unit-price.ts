import { formatCurrency } from "@/lib/brand";

const SIZE_PATTERN = /^(?:(\d+) × )?(\d+(?:,\d+)?) (ml|L|g|kg|un)$/;

export function measureName(measure: string) {
  switch (measure.toLowerCase()) {
    case "kg":
      return "quilo";
    case "l":
      return "litro";
    case "un":
      return "unidade";
    default:
      return measure;
  }
}

export function measureWords(measure: string) {
  switch (measure.toLowerCase()) {
    case "kg":
      return "o quilo";
    case "l":
      return "o litro";
    case "un":
      return "a unidade";
    case "m":
      return "o metro";
    default:
      return `por ${measure}`;
  }
}

export function priceByMeasure(value: number, measure: string) {
  return `${formatCurrency(value)} ${measureWords(measure)}`;
}

export function unitPriceFromSize(price: number, sizeLabel: string | null | undefined) {
  const match = SIZE_PATTERN.exec(sizeLabel?.trim() ?? "");

  if (!match || price <= 0) {
    return null;
  }

  const packs = match[1] ? Number(match[1]) : 1;
  const amount = Number(match[2].replace(",", "."));
  const unit = match[3];

  if (unit === "un") {
    const units = packs * amount;

    return units > 1 ? priceByMeasure(price / units, "un") : null;
  }

  const base = unit === "kg" || unit === "L" ? amount : amount / 1000;
  const total = packs * base;

  if (total <= 0) {
    return null;
  }

  return priceByMeasure(price / total, unit === "g" || unit === "kg" ? "kg" : "L");
}
