import { CircleSlash } from "lucide-react";
import type { MissingStore } from "@/types/api";
import { missingReason } from "./basic-item-format";

export function MissingStores({ stores, what }: { stores: MissingStore[]; what: string }) {
  if (!stores.length) return null;
  return (
    <section className="border-t border-border px-4 py-4 sm:px-5" aria-label={`Mercados sem preço de ${what}`}>
      <h3 className="text-sm font-bold">
        {stores.length === 1 ? "1 mercado sem preço online" : `${stores.length} mercados sem preço online`}
      </h3>
      <ul className="mt-2 space-y-2">
        {stores.map((store) => (
          <li key={store.storeId} className="flex gap-2 text-sm">
            <CircleSlash className="mt-0.5 size-4 shrink-0 text-muted" aria-hidden />
            <span>
              <span className="font-semibold">{store.storeName}</span>
              <span className="block text-xs text-muted">{missingReason(store)}</span>
            </span>
          </li>
        ))}
      </ul>
    </section>
  );
}
