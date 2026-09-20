import { Store } from "lucide-react";

import { StatusBadge } from "@/components/page/page-elements";

import { formatCurrency } from "@/lib/brand";

import type { ProductComparison } from "@/types/api";

import { MeasurementPrice } from "./measurement-price";

import { PriceDetails } from "./price-details";

export function StoreOffers({
  stores,
}: {
  stores: ProductComparison["stores"]["content"];
}) {
  const sorted = [...stores].sort(
    (a, b) => (a.price.unitPrice ?? Infinity) - (b.price.unitPrice ?? Infinity),
  );

  const prices = sorted.flatMap((store) =>
    store.price.unitPrice === null ? [] : [store.price.unitPrice],
  );

  const lowest = prices.length ? Math.min(...prices) : null;

  const highest = prices.length ? Math.max(...prices) : null;

  const savings = lowest !== null && highest !== null ? highest - lowest : 0;

  const observed = sorted.filter((store) => store.price.observation);

  const missing = sorted.length - observed.length;

  return (
    <>
      {savings > 0 && highest !== null ? (
        <p className="mb-4 rounded-lg bg-success-soft p-4 text-sm text-success">
          Economize até <strong>{formatCurrency(savings)}</strong> por embalagem
          (
          {((savings / highest) * 100).toLocaleString("pt-BR", {
            maximumFractionDigits: 1,
          })}
          %) entre os preços atuais.
        </p>
      ) : null}

      {!sorted.length ? (
        <p className="surface p-5 text-sm text-muted">
          Ainda não há preços deste produto nas lojas da cidade escolhida.
        </p>
      ) : (
        <div className="grid gap-4 md:grid-cols-2 xl:grid-cols-3">
          {sorted.map((item) => (
            <article
              key={item.storeId}
              className={`surface min-w-0 p-5 ${lowest !== null && item.price.unitPrice === lowest ? "border-primary/40" : ""}`}
            >
              <div className="flex items-start justify-between gap-3">
                <Store className="size-5 shrink-0 text-muted" aria-hidden />

                {lowest !== null && item.price.unitPrice === lowest ? (
                  <StatusBadge tone="success">Menor preço</StatusBadge>
                ) : null}
              </div>

              <h3 className="mt-4 font-bold">{item.storeName}</h3>

              {item.matchedProduct ? (
                <p className="mt-2 text-sm text-muted">
                  {item.price.observation?.sourceProductName ?? item.matchedProduct.name}
                  {item.matchedProduct.packageDescription
                    ? ` · ${item.matchedProduct.packageDescription}`
                    : ""}
                </p>
              ) : null}

              <PriceDetails price={item.price} />

              {!item.price.observation ? (
                <p className="mt-2 text-xs text-muted">
                  {item.priceSourceNote ??
                    "Sem preço disponível para um produto com equivalência confirmada. Isso não confirma falta de estoque."}
                </p>
              ) : null}

              <div className="mt-2">
                <MeasurementPrice value={item.measurementPrice} />
              </div>

              {lowest !== null &&
              item.price.unitPrice !== null &&
              item.price.unitPrice > lowest ? (
                <p className="mt-3 text-xs text-muted">
                  {formatCurrency(item.price.unitPrice - lowest)} acima do menor
                  preço
                </p>
              ) : null}
            </article>
          ))}
        </div>
      )}

      {missing > 0 ? (
        <p className="mt-4 text-xs text-muted">
          {missing} loja(s) ainda não têm observação deste produto. Isso não
          confirma falta de estoque.
        </p>
      ) : null}
    </>
  );
}
