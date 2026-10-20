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
              ? "Disponível"
              : "Disponibilidade a confirmar"}
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
            Promoção no site:{" "}
            <strong>{formatCurrency(observation.promotionalPrice)}</strong>
          </p>
          {observation.promotionCondition ? (
            <p className="mt-1">{observation.promotionCondition}</p>
          ) : null}
          <p className="mt-1 text-xs text-muted">
            {observation.promotionValidUntil
              ? `Válida até ${formatDate(observation.promotionValidUntil)}`
              : "O mercado não informou até quando vale a promoção."}
          </p>
          {!price.promotionApplied ? (
            <p className="mt-1 text-xs text-muted">
              Na comparação usamos o preço normal, porque a promoção tem condição ou prazo desconhecido.
            </p>
          ) : null}
        </div>
      ) : null}
      {observation ? (
        <>
          {observation.salesChannel === "ONLINE" || observation.salesChannel === "PHYSICAL_FLYER" ? (
            <p className="text-xs text-muted">
              {observation.salesChannel === "ONLINE"
                ? "Preço do site do mercado. Na loja física pode ser diferente."
                : "Oferta do encarte da loja."}
            </p>
          ) : null}
          <p className="flex items-center gap-2 text-xs text-muted">
            <Clock3 className="size-3.5" aria-hidden />
            Visto no site do mercado em {formatDate(observation.collectedAt)}
          </p>
          {price.expiresAt ? (
            <p className="text-xs text-muted">
              Considerado atual até {formatDate(price.expiresAt)}
            </p>
          ) : null}
          <details className="text-xs text-muted">
            <summary className="cursor-pointer font-semibold">
              De onde vem este preço
            </summary>
            <p className="mt-2">
              {observation.originType === "USER_CONTRIBUTION"
                ? "Preço enviado pela comunidade."
                : "Do site do próprio mercado."}
            </p>
            <p className="mt-2">
              O horário é quando o Gomo consultou o site. O mercado não informa
              quando mudou o preço.
            </p>
            {observation.sourceProductReference ? (
              <p className="mt-2 break-all">
                Código no site do mercado: {observation.sourceProductReference}
              </p>
            ) : null}
            {observation.originUrl ? (
              <a
                className="mt-2 inline-block font-semibold text-primary underline"
                href={observation.originUrl}
                target="_blank"
                rel="noreferrer"
              >
                Ver no site do mercado
              </a>
            ) : null}
          </details>
        </>
      ) : null}
    </div>
  );
}
