import { Link } from "react-router-dom";
import { formatCurrency, formatDate } from "@/lib/brand";
import type { ShoppingComparisonItem } from "@/types/api";

const statusLabels = {
  KNOWN: "Preço atual",
  EXPIRED: "Preço desatualizado",
  OUT_OF_STOCK: "Indisponível",
  NO_OBSERVATION: "Sem preço nesta loja",
};

export function ComparisonItems({
  items,
  bestTotals,
}: {
  items: ShoppingComparisonItem[];
  bestTotals?: Map<string, number>;
}) {
  return (
    <div className="overflow-x-auto">
      <table className="w-full text-left text-sm">
        <thead>
          <tr className="border-b border-border text-muted">
            <th className="p-3 font-medium">Produto e situação</th>
            <th className="p-3 text-right font-medium">Qtd.</th>
            <th className="p-3 text-right font-medium">Unitário</th>
            <th className="p-3 text-right font-medium">Subtotal</th>
            {bestTotals ? (
              <th className="p-3 text-right font-medium">Economia possível</th>
            ) : null}
          </tr>
        </thead>
        <tbody>
          {items.map((item) => (
            <tr
              key={item.productId}
              className="border-b border-border last:border-0"
            >
              <td className="min-w-52 p-3">
                <p className="font-semibold">{item.productName}</p>
                {item.price.observation?.sourceProductName ||
                (item.matchedProduct && item.matchedProduct.id !== item.productId) ? (
                  <p className="mt-1 text-xs text-muted">
                    Nesta loja: {item.price.observation?.sourceProductName ?? item.matchedProduct?.name}
                  </p>
                ) : null}
                {item.hasPossibleMatches ? (
                  <Link
                    className="mt-1 inline-block text-xs text-primary underline"
                    to={`/app/produtos/${item.productId}`}
                  >
                    Conferir cadastros com variante ou embalagem não confirmada
                  </Link>
                ) : null}
                <p className="mt-1 text-xs text-muted">
                  {statusLabels[item.price.status]}
                  {item.price.status === "KNOWN" &&
                  item.price.availability === "UNKNOWN"
                    ? " · estoque não informado"
                    : ""}
                </p>
                {item.price.observation ? (
                  <details className="mt-1 text-xs text-muted">
                    <summary className="cursor-pointer">
                      Coleta: {formatDate(item.price.observation.collectedAt)}
                    </summary>
                    {item.price.observation.sourceProductReference ? (
                      <p className="mt-1 break-all">
                        Código na fonte:{" "}
                        {item.price.observation.sourceProductReference}
                      </p>
                    ) : null}
                    {item.price.observation.originUrl ? (
                      <a
                        className="mt-1 inline-block text-primary underline"
                        href={item.price.observation.originUrl}
                        target="_blank"
                        rel="noreferrer"
                      >
                        Conferir na fonte
                      </a>
                    ) : (
                      <p className="mt-1">
                        {item.price.observation.originType ===
                        "USER_CONTRIBUTION"
                          ? "Preço enviado pela comunidade."
                          : "Catálogo público da loja."}
                      </p>
                    )}
                    {item.price.observation.promotionalPrice !== null &&
                    !item.price.promotionApplied ? (
                      <p className="mt-1">
                        Promoção não aplicada:{" "}
                        {item.price.observation.promotionCondition ??
                          "validade não confirmada pela fonte"}
                        .
                      </p>
                    ) : null}
                  </details>
                ) : null}
              </td>
              <td className="p-3 text-right tabular-nums">
                {item.quantity.toLocaleString("pt-BR")}
              </td>
              <td className="whitespace-nowrap p-3 text-right tabular-nums">
                {item.price.unitPrice === null
                  ? "—"
                  : formatCurrency(item.price.unitPrice)}
              </td>
              <td className="whitespace-nowrap p-3 text-right font-semibold tabular-nums">
                {item.lineTotal === null ? "—" : formatCurrency(item.lineTotal)}
              </td>
              {bestTotals ? (
                <td className="whitespace-nowrap p-3 text-right tabular-nums">
                  {item.lineTotal === null ||
                  !bestTotals.has(item.productId) ? (
                    "—"
                  ) : item.lineTotal === bestTotals.get(item.productId) ? (
                    <span className="text-success">Menor preço</span>
                  ) : (
                    formatCurrency(
                      Math.max(
                        0,
                        item.lineTotal - bestTotals.get(item.productId)!,
                      ),
                    )
                  )}
                </td>
              ) : null}
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}
