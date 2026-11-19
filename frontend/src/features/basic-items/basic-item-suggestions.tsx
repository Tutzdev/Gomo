import { ChevronRight, Tags } from "lucide-react";
import { Link } from "react-router-dom";
import type { BasicItem } from "@/types/api";
import { basicItemCoverage, perMeasure } from "./basic-item-format";
import { useBasicItemSuggestions } from "./use-basic-item-suggestions";

/**
 * "Pão de hambúrguer" of any brand, shown above the exact products: the exact one is often sold in few markets,
 * while almost every market sells some pão de hambúrguer.
 */
export function BasicItemSuggestions({
  query,
  cityId,
  onChoose,
  excludedSlugs = [],
  stacked = false,
}: {
  query: string;
  cityId?: string;
  onChoose?: (item: BasicItem) => void;
  excludedSlugs?: string[];
  stacked?: boolean;
}) {
  const suggestions = useBasicItemSuggestions(query, cityId);
  const items = suggestions.data ?? [];
  if (!items.length) return null;
  return (
    <ul className={stacked ? "grid gap-2" : "grid gap-2 sm:grid-cols-2 lg:grid-cols-3"} aria-label="Qualquer marca">
      {items.map((item) => {
        const excluded = excludedSlugs.includes(item.slug);
        const content = (
          <>
            <Tags className="size-5 shrink-0 text-primary" aria-hidden />
            <span className="min-w-0 flex-1">
              <span className="block text-xs font-semibold text-primary">Qualquer marca</span>
              <span className="block font-bold leading-5">{item.name}</span>
              <span className="mt-0.5 block text-xs text-muted">
                {excluded ? "Já está na lista" : basicItemCoverage(item)}
                {!excluded && item.lowestPricePerMeasure !== null
                  ? ` · a partir de ${perMeasure(item.lowestPricePerMeasure, item)}`
                  : ""}
              </span>
            </span>
            <ChevronRight className="size-4 shrink-0 text-muted" aria-hidden />
          </>
        );
        const className =
          "flex w-full min-h-16 items-center gap-3 rounded-lg border border-primary/25 bg-primary-soft/50 px-3 py-2 text-left hover:bg-primary-soft focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-focus disabled:cursor-default disabled:opacity-60";
        return (
          <li key={item.slug}>
            {onChoose ? (
              <button type="button" className={className} disabled={excluded} onClick={() => onChoose(item)}>
                {content}
              </button>
            ) : (
              <Link className={className} to={`/app/basicos/${item.slug}${cityId ? `?cityId=${cityId}` : ""}`}>
                {content}
              </Link>
            )}
          </li>
        );
      })}
    </ul>
  );
}
