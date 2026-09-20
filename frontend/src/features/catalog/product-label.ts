import type { Product } from "@/types/api";

export function formatMeasurement(
  quantity: number | null,
  unit: string | null,
) {
  if (quantity === null || !unit) return "";
  const larger = quantity >= 1000 && (unit === "ML" || unit === "G");
  const amount = larger ? quantity / 1000 : quantity;
  const label = larger
    ? unit === "ML"
      ? "L"
      : "kg"
    : ({ ML: "ml", G: "g", UN: "un." }[unit] ?? unit.toLowerCase());
  return `${amount.toLocaleString("pt-BR", { maximumFractionDigits: 4 })} ${label}`;
}

export function productMetadata(product: Product) {
  const measurement = product.packageDescription?.includes(" X ")
    ? product.packageDescription
    : formatMeasurement(product.quantity, product.unit);
  return [
    ...new Set([
      measurement || product.packageDescription,
      product.brand,
      product.category,
    ]),
  ]
    .filter(Boolean)
    .join(" · ");
}

export function productDisplayName(product: Product) {
  if (
    !product.quantity ||
    !["ML", "G"].includes(product.unit ?? "") ||
    /\b(kit|pack|combo)\b/i.test(product.name)
  )
    return product.name;
  return product.name
    .replace(/\s*\d+(?:[.,]\d+)?\s*(ml|litros?|l|gramas?|g|kg)\s*$/i, "")
    .trim();
}
