import { LayoutList, MapPin, ShoppingBasket, Timer } from "lucide-react";
import type { CSSProperties } from "react";
import { NativeButton } from "@/components/ui/native-button";
import { BRAND, formatCurrency } from "@/lib/brand";
import { HeroShowcase } from "./hero-showcase";

const heroHighlights = [
  { icon: LayoutList, title: "Todos numa tela", description: "Sem abrir o site de cada mercado." },
  { icon: Timer, title: "Preço de agora", description: "Só entra mercado com preço atualizado." },
  { icon: ShoppingBasket, title: "A lista inteira", description: "Veja onde a compra toda sai mais barata." },
];

export function HeroSection({ signUpPath }: { signUpPath: string }) {
  return (
    <section className="landing-hero" aria-labelledby="hero-title">
      <div className="landing-frame">
        <div className="landing-hero__panel">
          <div className="landing-hero__copy">
            <p className="landing-pill hero-rise" style={{ "--rise-index": 0 } as CSSProperties}>
              <MapPin className="size-4 text-primary" aria-hidden />
              Compare os supermercados da sua cidade
            </p>

            <h1 id="hero-title" className="landing-hero__title">
              <span className="hero-rise" style={{ "--rise-index": 1 } as CSSProperties}>Busque uma vez.</span>
              <span className="hero-rise text-primary" style={{ "--rise-index": 2 } as CSSProperties}>Veja quem cobra menos.</span>
            </h1>

            <p className="landing-hero__lead hero-rise" style={{ "--rise-index": 3 } as CSSProperties}>
              Digite o produto e veja na hora quanto ele custa em cada supermercado da sua cidade.
            </p>

            <div className="landing-hero__actions hero-rise" style={{ "--rise-index": 4 } as CSSProperties}>
              <NativeButton to={signUpPath} size="lg" glow className="landing-button">
                Começar grátis
              </NativeButton>
              <NativeButton href="#como-funciona" variant="secondary" size="lg" className="landing-button">
                Ver como funciona
              </NativeButton>
            </div>
            <p className="landing-hero__note hero-rise" style={{ "--rise-index": 5 } as CSSProperties}>
              Grátis para sempre. Premium por {formatCurrency(BRAND.monthlyPrice)}/mês.
            </p>
          </div>

          <HeroShowcase />

          <ul className="landing-hero__highlights">
            {heroHighlights.map(({ icon: Icon, title, description }) => (
              <li key={title}>
                <span className="landing-hero__highlight-icon">
                  <Icon className="size-5" aria-hidden />
                </span>
                <span>
                  <strong>{title}</strong>
                  <span>{description}</span>
                </span>
              </li>
            ))}
          </ul>
        </div>
      </div>
    </section>
  );
}
