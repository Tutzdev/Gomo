import { useMutation, useQuery } from "@tanstack/react-query";
import { ArrowRight, Lock, Replace } from "lucide-react";
import { InlineError } from "@/components/ui/feedback";
import { NativeButton } from "@/components/ui/native-button";
import { CatalogItemThumbnail } from "@/features/catalog/catalog-item-picker";
import { useUpsell } from "@/features/plan/upsell-context";
import { formatCurrency } from "@/lib/brand";
import { shoppingListApi } from "@/services/gomo-api";
import type { ListSubstitutes } from "@/types/api";

type Substitution = ListSubstitutes["items"][number];

const MEASURE = { KG: "kg", L: "litro", UN: "unidade" } as const;

/**
 * "Equivalente mais barato": the same kind of product from another brand or in another size, cheaper per kg,
 * litre or unit, with a button that swaps it in the list. Hidden when there is none.
 */
export function SubstitutesCard({ listId, cityId, onChanged }: { listId: string; cityId: string; onChanged: () => Promise<void> }) {
  const { showUpsell } = useUpsell();
  const substitutes = useQuery({
    queryKey: ["shopping-list-substitutes", listId, cityId],
    queryFn: () => shoppingListApi.substitutes(listId, cityId),
    enabled: Boolean(cityId),
  });
  if (!substitutes.data || substitutes.data.substitutes === 0) return null;
  const { locked, substitutes: count, items } = substitutes.data;
  const title = count === 1 ? "1 item da lista tem um equivalente mais barato" : `${count} itens da lista têm um equivalente mais barato`;

  if (locked) {
    return (
      <section className="surface mb-6 flex flex-wrap items-center justify-between gap-4 p-5">
        <p className="flex min-w-0 items-start gap-3 text-sm">
          <Replace className="mt-0.5 size-5 shrink-0 text-primary" aria-hidden />
          <span><strong>{title}</strong>, de outra marca ou tamanho, pelo preço por kg ou litro.</span>
        </p>
        <NativeButton variant="secondary" size="sm" onClick={() => showUpsell("SUBSTITUTES")}>
          <Lock className="size-4" aria-hidden />
          Ver quais no Premium
        </NativeButton>
      </section>
    );
  }

  return (
    <section className="surface mb-6 overflow-hidden" aria-labelledby="substitutes-title">
      <h2 id="substitutes-title" className="flex items-center gap-2 border-b border-border px-5 py-3 font-bold">
        <Replace className="size-5 text-primary" aria-hidden />
        {title}
      </h2>
      <p className="px-5 pt-3 text-sm text-muted">O mesmo tipo de produto, comparado pelo preço por kg, litro ou unidade. A troca é sua: marca também é gosto.</p>
      <ul className="divide-y divide-border">
        {items.map((item) => <SubstitutionRow key={item.itemId} listId={listId} item={item} onChanged={onChanged} />)}
      </ul>
    </section>
  );
}

function SubstitutionRow({ listId, item, onChanged }: { listId: string; item: Substitution; onChanged: () => Promise<void> }) {
  const replace = useMutation({
    mutationFn: () => shoppingListApi.replaceItem(listId, item.itemId, { catalogItemId: item.substituteCatalogItemId, quantity: item.suggestedQuantity }),
    onSuccess: onChanged,
  });
  const measure = MEASURE[item.measureUnit];
  return (
    <li className="p-5">
      <div className="flex flex-wrap items-center gap-x-3 gap-y-2 text-sm">
        <span className="min-w-0">
          <span className="font-semibold">{item.productName}</span>
          <span className="block text-xs text-muted">{formatCurrency(item.pricePerMeasure)}/{measure}</span>
        </span>
        <ArrowRight className="size-4 shrink-0 text-muted" aria-label="por" />
        <span className="flex min-w-0 items-center gap-2">
          <CatalogItemThumbnail imageUrl={item.substituteImageUrl} size="sm" />
          <span className="min-w-0">
            <span className="font-semibold">{item.substituteName}</span>
            <span className="block text-xs text-muted">
              {formatCurrency(item.substitutePrice)} no {item.substituteStoreName} · {formatCurrency(item.substitutePricePerMeasure)}/{measure}
            </span>
          </span>
        </span>
      </div>
      <div className="mt-3 flex flex-wrap items-center justify-between gap-3">
        <span className="rounded-full bg-success-soft px-2.5 py-1 text-xs font-bold text-success">{item.savingsPercent}% mais barato por {measure}</span>
        <NativeButton size="sm" variant="secondary" loading={replace.isPending} onClick={() => replace.mutate()}>
          Trocar na lista{item.suggestedQuantity !== item.quantity ? ` (${item.suggestedQuantity.toLocaleString("pt-BR")})` : ""}
        </NativeButton>
      </div>
      <InlineError>{replace.error?.message}</InlineError>
    </li>
  );
}
