import { Clock3, Wallet } from "lucide-react";
import { NativeButton } from "@/components/ui/native-button";
import { HeroShowcase } from "./hero-showcase";

const heroFacts = [
  { icon: Clock3, title: "Preço de hoje", description: "Coletado 4 vezes por dia." },
  { icon: Wallet, title: "Grátis para usar", description: "Sem cartão de crédito." },
];

export function HeroSection({ signUpPath }: { signUpPath: string }) {
  return (
    <section className="landing-hero" aria-labelledby="hero-title">
      <div className="landing-frame landing-hero__panel">
        <div className="landing-hero__copy">
          <h1 id="hero-title" className="landing-hero__title">
            <span>Faça a lista.</span>
            <span className="text-primary">A gente mostra onde sai mais barato.</span>
          </h1>

          <p className="landing-hero__lead">Os preços de hoje dos supermercados da sua cidade, lado a lado.</p>

          <div className="landing-hero__actions">
            <NativeButton to={signUpPath} size="lg" className="landing-button">
              Começar grátis
            </NativeButton>
            <a href="#como-funciona" className="landing-text-link">Ver como funciona</a>
          </div>

          <ul className="landing-hero__facts">
            {heroFacts.map(({ icon: Icon, title, description }) => (
              <li key={title}>
                <span className="landing-hero__fact-icon">
                  <Icon className="size-[1.125rem]" aria-hidden />
                </span>
                <span>
                  <strong>{title}</strong>
                  <span>{description}</span>
                </span>
              </li>
            ))}
          </ul>
        </div>

        <HeroShowcase />
      </div>
    </section>
  );
}
