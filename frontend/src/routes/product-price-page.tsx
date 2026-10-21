import { useQuery } from "@tanstack/react-query";
import { Clock3, ListPlus, Lock, Package, Store, Trophy } from "lucide-react";
import { useEffect, useState } from "react";
import { Link, useLocation, useNavigate, useParams } from "react-router-dom";
import { PublicNavbar } from "@/components/layout/public-navbar";
import { StatusBadge } from "@/components/page/page-elements";
import { EmptyState, ErrorState, LoadingState } from "@/components/ui/feedback";
import { NativeButton } from "@/components/ui/native-button";
import { ShareButton } from "@/components/ui/share-button";
import { useAuth } from "@/features/auth/auth-context";
import { LandingFooter } from "@/features/landing/landing-footer";
import { ApiError } from "@/lib/api";
import { BRAND, formatCurrency, formatDate } from "@/lib/brand";
import { cn } from "@/lib/cn";
import { absoluteUrl, usePageMeta } from "@/lib/page-meta";
import { productIdFromSlug, productPagePath } from "@/lib/product-page-path";
import { siteUrl } from "@/lib/site-url";
import { useDefaultCityId } from "@/lib/use-default-city";
import { catalogApi } from "@/services/gomo-api";
import type { CatalogItemDetail } from "@/types/api";
import "@/features/landing/landing.css";

/**
 * Página pública (sem login) com o preço atual de um produto em cada mercado da cidade, para quem chega
 * pelo Google. O sitemap do backend lista uma destas páginas por produto ativo do catálogo.
 */
export function ProductPricePage() {
  const { slug = "" } = useParams();
  const itemId = productIdFromSlug(slug);
  const cityId = useDefaultCityId({ signedIn: false });
  const cities = useQuery({
    queryKey: ["cities", "public"],
    queryFn: () => catalogApi.cities(undefined, 0, 100),
    staleTime: 10 * 60 * 1000,
  });
  const cityName = cities.data?.content.find((city) => city.id === cityId)?.name ?? null;
  const detail = useQuery({
    queryKey: ["public-catalog-item", itemId, cityId],
    queryFn: () => catalogApi.publicCatalogItem(itemId!, cityId),
    enabled: Boolean(itemId && cityId),
    retry: (count, error) => !(error instanceof ApiError && error.status === 404) && count < 1,
  });
  const notFound = !itemId || (detail.error instanceof ApiError && detail.error.status === 404);

  const canonicalPath = detail.data ? productPagePath(detail.data.item.id, detail.data.item.name) : undefined;
  const { pathname } = useLocation();
  const navigate = useNavigate();
  useEffect(() => {
    // Um nome antigo ou digitado errado no endereço leva ao endereço atual do produto.
    if (canonicalPath && pathname !== canonicalPath) navigate(canonicalPath, { replace: true });
  }, [canonicalPath, pathname, navigate]);

  usePageMeta(
    notFound
      ? { title: `Produto não encontrado | ${BRAND.name}`, noIndex: true }
      : detail.data
        ? productMeta(detail.data, cityName, canonicalPath!)
        : { title: `Preço do produto | ${BRAND.name}` },
  );

  return (
    <div className="landing min-h-screen bg-[#f7f7f8] text-foreground">
      <a href="#conteudo-principal" className="skip-link">Pular para o conteúdo</a>
      <PublicNavbar />
      <main id="conteudo-principal" className="mx-auto w-full max-w-4xl px-4 py-8 sm:px-6 sm:py-12">
        {notFound ? (
          <EmptyState
            title="Produto não encontrado"
            description="Este produto saiu do catálogo ou o endereço está incompleto."
            action={<NativeButton to="/">Ir para o início</NativeButton>}
          />
        ) : detail.isError ? (
          <ErrorState message={detail.error.message} retry={() => void detail.refetch()} />
        ) : !detail.data ? (
          <LoadingState label="Buscando os preços…" />
        ) : (
          <ProductPrices detail={detail.data} cityName={cityName} />
        )}
      </main>
      <LandingFooter />
    </div>
  );
}

function ProductPrices({ detail, cityName }: { detail: CatalogItemDetail; cityName: string | null }) {
  const { item, offers, access } = detail;
  const { user } = useAuth();
  const inCity = cityName ? ` em ${cityName}` : "";
  const pricedStores = offers.length + access.lockedStores;
  const cheapest = offers[0]?.price.unitPrice ?? null;
  const details = [item.brand, item.sizeLabel, item.category].filter(Boolean).join(" · ");

  return (
    <>
      <nav aria-label="Você está em" className="mb-5 text-sm text-muted">
        <Link to="/" className="font-semibold hover:text-foreground hover:underline">{BRAND.name}</Link>
        <span aria-hidden> › </span>
        <span>Preços{inCity}</span>
      </nav>

      <article className="surface overflow-hidden">
        <header className="flex flex-col gap-5 border-b border-border p-5 sm:flex-row sm:items-center sm:p-6">
          <ProductPhoto imageUrl={item.imageUrl} />
          <div className="min-w-0 flex-1">
            <h1 className="text-2xl font-extrabold leading-tight tracking-tight sm:text-3xl">{item.name}</h1>
            {details ? <p className="mt-1 text-sm text-muted">{details}</p> : null}
            {item.lowestPrice !== null ? (
              <p className="mt-3 text-base">
                A partir de{" "}
                <strong className="text-xl font-extrabold tabular-nums text-success">
                  {formatCurrency(item.lowestPrice)}
                </strong>
                {item.lowestPriceStore ? <> no {item.lowestPriceStore}</> : null}
              </p>
            ) : null}
            <p className="mt-2 flex flex-wrap items-center gap-x-2 text-sm text-muted">
              <span>
                {pricedStores === 1
                  ? `Preço atual em 1 mercado${inCity}`
                  : pricedStores > 1
                    ? `Preço atual em ${pricedStores} mercados${inCity}`
                    : `Sem preço atual nos mercados${inCity}`}
              </span>
              {item.pricesCollectedAt ? (
                <span className="inline-flex items-center gap-1">
                  <Clock3 className="size-3.5" aria-hidden />
                  Atualizado em {formatDate(item.pricesCollectedAt)}
                </span>
              ) : null}
            </p>
            {item.lowestPrice !== null ? (
              <div className="mt-4">
                <ShareButton
                  text={`${item.name} está ${formatCurrency(item.lowestPrice)}${item.lowestPriceStore ? ` no ${item.lowestPriceStore}` : ""}. Vi no Gomo:`}
                  url={siteUrl(productPagePath(item.id, item.name))}
                />
              </div>
            ) : null}
          </div>
        </header>

        {!offers.length ? (
          <div className="p-5">
            <EmptyState
              title="Nenhum mercado com preço atual agora"
              description="Os preços são atualizados várias vezes ao dia. Volte mais tarde."
            />
          </div>
        ) : (
          <ol className="divide-y divide-border">
            {offers.map((offer, index) => {
              const price = offer.price.unitPrice!;
              const observation = offer.price.observation;
              return (
                <li
                  key={offer.storeId}
                  className={cn("flex flex-wrap items-center gap-x-4 gap-y-2 px-5 py-4 sm:px-6", index === 0 && "bg-primary-soft/40")}
                >
                  <span
                    className={cn(
                      "grid size-8 shrink-0 place-items-center rounded-full text-sm font-bold tabular-nums",
                      index === 0 ? "bg-success text-white" : "bg-surface-strong text-muted",
                    )}
                    aria-hidden
                  >
                    {index === 0 ? <Trophy className="size-4" /> : index + 1}
                  </span>
                  <div className="min-w-0 flex-1">
                    <h2 className="flex items-center gap-1.5 font-semibold">
                      <Store className="size-4 shrink-0 text-muted" aria-hidden />
                      {offer.storeName}
                      {index === 0 ? <span className="sr-only">(mais barato)</span> : null}
                    </h2>
                    <div className="mt-1 flex flex-wrap items-center gap-2 text-xs text-muted">
                      {offer.price.promotionApplied ? <StatusBadge tone="success">Promoção</StatusBadge> : null}
                      {observation ? <span>Visto em {formatDate(observation.collectedAt)}</span> : null}
                    </div>
                  </div>
                  <div className="text-right">
                    <p className={cn("text-xl font-extrabold tabular-nums", index === 0 && "text-success")}>
                      {formatCurrency(price)}
                    </p>
                    {offer.price.promotionApplied && observation ? (
                      <p className="text-xs text-muted line-through tabular-nums">
                        {formatCurrency(observation.regularPrice)}
                      </p>
                    ) : index > 0 && cheapest !== null ? (
                      <p className="text-xs text-muted tabular-nums">+{formatCurrency(price - cheapest)}</p>
                    ) : null}
                  </div>
                </li>
              );
            })}
          </ol>
        )}

        {access.lockedStores > 0 ? (
          <p className="flex items-center gap-2 border-t border-border px-5 py-3 text-sm text-muted sm:px-6">
            <Lock className="size-4 shrink-0" aria-hidden />
            {access.lockedStores === 1
              ? "Mais 1 mercado tem preço deste produto. Veja todos dentro do Gomo."
              : `Mais ${access.lockedStores} mercados têm preço deste produto. Veja todos dentro do Gomo.`}
          </p>
        ) : null}
      </article>

      <p className="mt-3 text-xs text-muted">
        Preços publicados pelos próprios mercados nos seus sites e atualizados várias vezes por dia. Confira no mercado
        antes de comprar.
      </p>

      <section className="surface mt-8 flex flex-col items-start gap-4 p-5 sm:flex-row sm:items-center sm:p-6">
        <ListPlus className="size-8 shrink-0 text-primary" aria-hidden />
        <div className="min-w-0 flex-1">
          <h2 className="text-lg font-extrabold">Monte sua lista e veja onde a compra inteira sai mais barata</h2>
          <p className="mt-1 text-sm text-muted">
            O {BRAND.name} soma os preços da sua lista em cada mercado{inCity} e mostra a diferença. É grátis.
          </p>
        </div>
        <NativeButton
          to={user ? `/app/comparar/${item.id}` : "/entrar?modo=cadastro"}
          className="landing-button w-full sm:w-auto"
        >
          {user ? "Abrir no Gomo" : "Criar conta grátis"}
        </NativeButton>
      </section>
    </>
  );
}

function ProductPhoto({ imageUrl }: { imageUrl: string | null }) {
  const [failed, setFailed] = useState(false);
  return (
    <span className="grid size-28 shrink-0 place-items-center overflow-hidden rounded-lg bg-white ring-1 ring-border">
      {imageUrl && !failed ? (
        <img
          src={imageUrl}
          alt=""
          referrerPolicy="no-referrer"
          width={112}
          height={112}
          className="size-full object-contain p-1"
          onError={() => setFailed(true)}
        />
      ) : (
        <Package className="size-9 text-muted" aria-hidden />
      )}
    </span>
  );
}

function productMeta({ item, offers, access }: CatalogItemDetail, cityName: string | null, canonicalPath: string) {
  const inCity = cityName ? ` em ${cityName}` : "";
  const pricedStores = offers.length + access.lockedStores;
  const description =
    item.lowestPrice !== null
      ? `${item.name}: a partir de ${formatCurrency(item.lowestPrice)}${item.lowestPriceStore ? ` no ${item.lowestPriceStore}` : ""}. `
        + `Compare o preço em ${pricedStores} ${pricedStores === 1 ? "mercado" : "mercados"}${inCity}, atualizado várias vezes por dia.`
      : `Compare o preço de ${item.name} nos mercados${inCity}, atualizado várias vezes por dia.`;
  const url = absoluteUrl(canonicalPath);
  return {
    title: `${item.name}: preço${inCity} | ${BRAND.name}`,
    description,
    canonicalPath,
    jsonLd: {
      "@context": "https://schema.org",
      "@type": "Product",
      name: item.name,
      ...(item.imageUrl ? { image: [item.imageUrl] } : {}),
      ...(item.brand ? { brand: { "@type": "Brand", name: item.brand } } : {}),
      ...(item.category ? { category: item.category } : {}),
      ...(url ? { url } : {}),
      // Sem preço atual não há oferta para descrever.
      ...(item.lowestPrice !== null && item.highestPrice !== null
        ? {
            offers: {
              "@type": "AggregateOffer",
              priceCurrency: BRAND.currency,
              lowPrice: item.lowestPrice,
              highPrice: item.highestPrice,
              offerCount: item.pricedStores,
              availability: "https://schema.org/InStock",
            },
          }
        : {}),
    },
  };
}
