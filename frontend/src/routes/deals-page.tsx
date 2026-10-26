import { useQuery } from "@tanstack/react-query";
import { Link } from "react-router-dom";
import { PublicNavbar } from "@/components/layout/public-navbar";
import { DealsBoard } from "@/features/deals/deals-board";
import { LandingFooter } from "@/features/landing/landing-footer";
import { UpsellProvider } from "@/features/plan/upsell-provider";
import { BRAND } from "@/lib/brand";
import { usePageMeta } from "@/lib/page-meta";
import { useDefaultCityId } from "@/lib/use-default-city";
import { catalogApi } from "@/services/gomo-api";
import "@/features/landing/landing.css";

/**
 * Página pública (sem login) com as ofertas da cidade: o que está pelo menos 10% abaixo do preço de sempre em
 * cada mercado agora. Está no sitemap e é o link que dá para mandar no grupo da família.
 */
export function DealsPage() {
  const cityId = useDefaultCityId();
  const cities = useQuery({
    queryKey: ["cities", "public"],
    queryFn: () => catalogApi.cities(undefined, 0, 100),
    staleTime: 10 * 60 * 1000,
  });
  const cityName = cities.data?.content.find((city) => city.id === cityId)?.name ?? null;
  const inCity = cityName ? ` em ${cityName}` : "";

  usePageMeta({
    title: `Ofertas da semana${inCity} | ${BRAND.name}`,
    description: `O que está abaixo do preço de sempre${inCity} em cada mercado agora, com o menor preço dos últimos 90 dias. Atualizado 4 vezes ao dia.`,
    canonicalPath: "/ofertas",
  });

  return (
    <UpsellProvider>
      <div className="landing min-h-screen bg-[#f7f7f8] text-foreground">
        <a href="#conteudo-principal" className="skip-link">Pular para o conteúdo</a>
        <PublicNavbar />
        <main id="conteudo-principal" className="mx-auto w-full max-w-6xl px-4 py-8 sm:px-6 sm:py-12">
          <nav aria-label="Você está em" className="mb-5 text-sm text-muted">
            <Link to="/" className="font-semibold hover:text-foreground hover:underline">{BRAND.name}</Link>
            <span aria-hidden> › </span>
            <span>Ofertas{inCity}</span>
          </nav>
          <h1 className="text-3xl font-extrabold leading-tight tracking-tight sm:text-4xl">Ofertas da semana{inCity}</h1>
          <p className="mt-2 max-w-2xl text-muted">
            Não é o folheto do mercado: é o que está mais barato que o normal agora, comparando com o preço de cada
            dia do último mês.
          </p>
          <div className="mt-6">
            {cityId ? <DealsBoard cityId={cityId} cityName={cityName} inApp={false} /> : null}
          </div>
        </main>
        <LandingFooter />
      </div>
    </UpsellProvider>
  );
}
