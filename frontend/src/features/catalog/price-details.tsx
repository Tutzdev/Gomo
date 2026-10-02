import { Clock3 } from "lucide-react";
import { StatusBadge } from "@/components/page/page-elements";
import { formatCurrency, formatDate } from "@/lib/brand";
import type { PriceQuote } from "@/types/api";

export function PriceDetails({ price }: { price: PriceQuote }) {
  const observation = price.observation;
  const unavailableMessage =
    price.status === "OUT_OF_STOCK"
      ? "Produto indisponível"
      : price.status === "EXPIRED"
        ? "Preço desatualizado"
        : "Sem preço atual";

  return (
    <div className="mt-4 space-y-3">
      {price.unitPrice !== null ? (
        <>
          <p className="text-2xl font-extrabold">
            {formatCurrency(price.unitPrice)}
          </p>
          <StatusBadge
            tone={price.availability === "AVAILABLE" ? "success" : "warning"}
          >
            {price.availability === "AVAILABLE"
              ? "Em estoque"
              : "Estoque não informado"}
          </StatusBadge>
        </>
      ) : (
        <p className="rounded-lg bg-warning-soft p-3 text-sm font-semibold text-warning">
          {unavailableMessage}
        </p>
      )}
      {observation?.promotionalPrice != null ? (
        <div className="rounded-lg bg-surface-strong p-3 text-sm">
          <p>
            Promoção observada:{" "}
            <strong>{formatCurrency(observation.promotionalPrice)}</strong>
          </p>
          {observation.promotionCondition ? (
            <p className="mt-1">{observation.promotionCondition}</p>
          ) : null}
          <p className="mt-1 text-xs text-muted">
            {observation.promotionValidUntil
              ? `Validade informada: ${formatDate(observation.promotionValidUntil)}`
              : "Validade da promoção não informada pela fonte."}
          </p>
          {!price.promotionApplied ? (
            <p className="mt-1 text-xs text-muted">
              Esse valor promocional não compõe o preço atual da comparação.
            </p>
          ) : null}
        </div>
      ) : null}
      {observation ? (
        <>
          {observation.salesChannel === "ONLINE" || observation.salesChannel === "PHYSICAL_FLYER" ? (
            <p className="text-xs text-muted">
              {observation.salesChannel === "ONLINE"
                ? "Preço da loja online; pode diferir do preço na loja física."
                : "Oferta de encarte da loja física."}
            </p>
          ) : null}
          <p className="flex items-center gap-2 text-xs text-muted">
            <Clock3 className="size-3.5" aria-hidden />
            Coletado em {formatDate(observation.collectedAt)}
          </p>
          {price.expiresAt ? (
            <p className="text-xs text-muted">
              Limite de atualização: {formatDate(price.expiresAt)}
            </p>
          ) : null}
          <details className="text-xs text-muted">
            <summary className="cursor-pointer font-semibold">
              Origem da observação
            </summary>
            <p className="mt-2">
              {observation.originType === "USER_CONTRIBUTION"
                ? "Preço enviado pela comunidade."
                : "Preço obtido de uma fonte pública da loja."}
            </p>
            <p className="mt-2">
              A coleta indica quando consultamos a fonte. A data de alteração do
              preço pela loja não foi informada.
            </p>
            {observation.sourceProductReference ? (
              <p className="mt-2 break-all">
                Código na fonte: {observation.sourceProductReference}
              </p>
            ) : null}
            {observation.originUrl ? (
              <a
                className="mt-2 inline-block font-semibold text-primary underline"
                href={observation.originUrl}
                target="_blank"
                rel="noreferrer"
              >
                Conferir na fonte
              </a>
            ) : null}
          </details>
        </>
      ) : null}
    </div>
  );
}
