import { Link } from "react-router-dom";
import { BrandLogo } from "@/components/brand-logo";
import { PublicNavbar } from "@/components/layout/public-navbar";
import { useAuth } from "@/features/auth/auth-context";
import { ClosingSection } from "@/features/landing/closing-section";
import { FaqSection } from "@/features/landing/faq-section";
import { FeaturesSection } from "@/features/landing/features-section";
import { HeroSection } from "@/features/landing/hero-section";
import { PriceGapSection } from "@/features/landing/price-gap-section";
import { PricingSection } from "@/features/landing/pricing-section";
import "@/features/landing/landing.css";

export function LandingPage() {
  const { user } = useAuth();
  const platformPath = user ? "/app" : "/entrar";
  const signUpPath = user ? "/app/comparar" : "/entrar?modo=cadastro";

  return (
    <div className="landing min-h-screen text-foreground">
      <a href="#conteudo-principal" className="skip-link">Pular para o conteúdo</a>
      <PublicNavbar />

      <main id="conteudo-principal">
        <HeroSection signUpPath={signUpPath} />
        <PriceGapSection ctaPath={signUpPath} />
        <FeaturesSection />
        <PricingSection signUpPath={signUpPath} />
        <FaqSection />
        <ClosingSection signUpPath={signUpPath} />
      </main>

      <footer className="border-t border-border bg-white">
        <div className="mx-auto flex max-w-7xl flex-col gap-8 px-4 py-10 sm:px-6 md:flex-row md:items-end md:justify-between lg:px-8">
          <div>
            <BrandLogo />
            <p className="mt-4 max-w-sm text-sm leading-6 text-muted">Compare preços de supermercado antes de sair de casa.</p>
          </div>
          <nav aria-label="Rodapé" className="flex flex-wrap gap-x-6 gap-y-3 text-sm font-semibold text-muted">
            <a href="/#como-funciona" className="hover:text-foreground">Como funciona</a>
            <a href="/#preco" className="hover:text-foreground">Preço</a>
            <a href="/#duvidas" className="hover:text-foreground">Dúvidas</a>
            <Link to={platformPath} className="hover:text-foreground">Acessar plataforma</Link>
          </nav>
        </div>
      </footer>
    </div>
  );
}
