import { PublicNavbar } from "@/components/layout/public-navbar";
import { useAuth } from "@/features/auth/auth-context";
import { ClosingSection } from "@/features/landing/closing-section";
import { FaqSection } from "@/features/landing/faq-section";
import { FeaturesSection } from "@/features/landing/features-section";
import { HeroSection } from "@/features/landing/hero-section";
import { LandingFooter } from "@/features/landing/landing-footer";
import { PricingSection } from "@/features/landing/pricing-section";
import { TestimonialsSection } from "@/features/landing/testimonials-section";
import "@/features/landing/landing.css";

export function LandingPage() {
  const { user } = useAuth();
  const signUpPath = user ? "/app/comparar" : "/entrar?modo=cadastro";

  return (
    <div className="landing min-h-screen text-foreground">
      <a href="#conteudo-principal" className="skip-link">Pular para o conteúdo</a>
      <PublicNavbar />

      <main id="conteudo-principal">
        <HeroSection signUpPath={signUpPath} />
        <FeaturesSection />
        <TestimonialsSection />
        <PricingSection signUpPath={signUpPath} />
        <FaqSection />
        <ClosingSection signUpPath={signUpPath} />
      </main>

      <LandingFooter />
    </div>
  );
}
