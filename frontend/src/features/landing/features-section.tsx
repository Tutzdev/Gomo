import { ArrowRight } from "lucide-react";
import type { ReactNode } from "react";
import { NativeButton } from "@/components/ui/native-button";
import { useAuth } from "@/features/auth/auth-context";
import { formatCurrency } from "@/lib/brand";
import { cn } from "@/lib/cn";
import { isToday, useBiggestPriceGap } from "./use-biggest-price-gap";
import "./how-it-works.css";

/* Quatro passos, cada um com um objeto desenhado. A sacola (a lista mais barata) é o destaque em vermelho. */
const steps: { text: string; art: ReactNode; accent?: boolean }[] = [
  { text: "Busque o produto que você quer comprar.", art: <SearchArt /> },
  { text: "Veja o preço em cada mercado da cidade.", art: <TagsArt /> },
  { text: "Monte a lista e descubra onde ela sai mais barata.", art: <BagArt />, accent: true },
  { text: "Seja avisado quando o preço baixar.", art: <BellArt /> },
];

export function FeaturesSection() {
  const { user } = useAuth();
  const signUpPath = user ? "/app/produtos" : "/entrar?modo=cadastro";

  return (
    <section id="como-funciona" className="landing-section scroll-mt-20" aria-labelledby="how-title">
      <div className="landing-wide">
        <header className="how-intro">
          <p className="how-intro__eyebrow">Como funciona</p>
          <h2 id="how-title" className="landing-heading__title">Você pesquisa uma vez. O Gomo compara a cidade toda.</h2>
        </header>

        <ArtDefs />
        <ol className="how-grid">
          {steps.map(({ text, art, accent }, index) => (
            <li key={text} className="how-card">
              <span className="how-card__step" aria-hidden>{index + 1}</span>
              <div className={cn("how-art", accent && "how-art--accent")} aria-hidden>
                {art}
              </div>
              <p className="how-card__text">{text}</p>
            </li>
          ))}
        </ol>

        <div className="how-cta">
          <NativeButton to={signUpPath} size="lg" className="landing-button">
            Comparar preços agora
            <ArrowRight className="size-4" aria-hidden />
          </NativeButton>
          <PriceGapNote />
        </div>
      </div>
    </section>
  );
}

/** Um exemplo real da cidade quando a API responde; sem dados, fica a frase de apoio sem número. */
function PriceGapNote() {
  const gap = useBiggestPriceGap();

  if (!gap) {
    return (
      <p className="how-cta__note">
        <strong>Grátis para começar.</strong> Sem cartão de crédito.
      </p>
    );
  }

  const when = gap.collectedAt && isToday(gap.collectedAt) ? "Hoje" : "Na última coleta";
  return (
    <p className="how-cta__note">
      <strong>{when},</strong> {gap.productName} vai de {formatCurrency(gap.lowest)} a {formatCurrency(gap.highest)} na
      mesma cidade.
    </p>
  );
}

/* Degradês compartilhados pelos desenhos: creme para os objetos neutros, vermelho para o destaque. */
function ArtDefs() {
  return (
    <svg className="how-defs" aria-hidden focusable="false">
      <defs>
        <linearGradient id="how-face-cream" x1="0" y1="0" x2="0.35" y2="1">
          <stop offset="0" className="how-stop how-stop--cream-hi" />
          <stop offset="1" className="how-stop how-stop--cream-lo" />
        </linearGradient>
        <linearGradient id="how-face-red" x1="0" y1="0" x2="0.35" y2="1">
          <stop offset="0" className="how-stop how-stop--red-hi" />
          <stop offset="1" className="how-stop how-stop--red-lo" />
        </linearGradient>
        <radialGradient id="how-ground-shadow">
          <stop offset="0" className="how-stop how-stop--shadow" />
          <stop offset="1" className="how-stop how-stop--shadow-fade" />
        </radialGradient>
      </defs>
    </svg>
  );
}

/* Cada objeto é desenhado duas vezes: a cópia deslocada faz a lateral, a de cima leva o degradê. */

function SearchArt() {
  return (
    <svg viewBox="0 0 120 120">
      <ellipse className="how-ground" cx="62" cy="106" rx="34" ry="5" />
      <g className="how-lens">
        <g transform="translate(2.5 3.5)">
          <line className="how-side-stroke" x1="74" y1="74" x2="94" y2="94" strokeWidth="15" strokeLinecap="round" />
          <circle className="how-side-stroke" cx="52" cy="52" r="26" strokeWidth="12" fill="none" />
        </g>
        <line className="how-face-stroke" x1="74" y1="74" x2="94" y2="94" strokeWidth="15" strokeLinecap="round" />
        <circle className="how-glass" cx="52" cy="52" r="20" />
        <circle className="how-face-stroke" cx="52" cy="52" r="26" strokeWidth="12" fill="none" />
        <path className="how-light-stroke" d="M34 38 A24 24 0 0 1 46 29" strokeWidth="3.5" strokeLinecap="round" fill="none" />
        <path className="how-glint" d="M41 50 A12 12 0 0 1 49 41" strokeWidth="4" strokeLinecap="round" fill="none" />
      </g>
    </svg>
  );
}

function PriceTag() {
  return (
    <>
      <path
        className="how-side how-side-stroke"
        transform="translate(2.5 3.5)"
        d="M-20 -17 H26 V17 H-20 L-34 0 Z"
        strokeWidth="6"
        strokeLinejoin="round"
      />
      <path className="how-face how-face-stroke" d="M-20 -17 H26 V17 H-20 L-34 0 Z" strokeWidth="6" strokeLinejoin="round" />
      <circle className="how-deep" cx="-20" cy="0" r="4" />
      <rect className="how-side" x="-8" y="-8" width="28" height="6" rx="3" />
      <rect className="how-side" x="-8" y="3" width="17" height="6" rx="3" />
    </>
  );
}

function TagsArt() {
  return (
    <svg viewBox="0 0 120 120">
      <ellipse className="how-ground" cx="60" cy="106" rx="36" ry="5" />
      <g transform="translate(66 46) rotate(-26)">
        <g className="how-tag how-tag--back">
          <PriceTag />
        </g>
      </g>
      <g transform="translate(58 76) rotate(-6)">
        <g className="how-tag how-tag--front">
          <PriceTag />
        </g>
      </g>
    </svg>
  );
}

function BagArt() {
  return (
    <svg viewBox="0 0 120 120">
      <ellipse className="how-ground" cx="60" cy="108" rx="34" ry="5" />
      <g className="how-bag">
        <path className="how-side-stroke" d="M44 54 V42 A16 16 0 0 1 76 42 V54" strokeWidth="7" strokeLinecap="round" fill="none" />
        <path className="how-side" transform="translate(3 4)" d="M28 50 H92 L88 94 Q87 102 79 102 H41 Q33 102 32 94 Z" />
        <path className="how-face" d="M28 50 H92 L88 94 Q87 102 79 102 H41 Q33 102 32 94 Z" />
        <path className="how-light-stroke" d="M33 56 H87" strokeWidth="3" strokeLinecap="round" />
        <circle className="how-deep" cx="44" cy="58" r="3" />
        <circle className="how-deep" cx="76" cy="58" r="3" />
        <path className="how-check" d="M47 79 L56 88 L74 70" strokeWidth="7" strokeLinecap="round" strokeLinejoin="round" fill="none" />
      </g>
    </svg>
  );
}

function BellArt() {
  return (
    <svg viewBox="0 0 120 120">
      <ellipse className="how-ground" cx="60" cy="108" rx="32" ry="5" />
      <g className="how-bell">
        <g transform="translate(2.5 3.5)">
          <circle className="how-side" cx="60" cy="92" r="8" />
          <path
            className="how-side how-side-stroke"
            d="M60 24 C44 24 35 37 35 53 V68 L27 81 H93 L85 68 V53 C85 37 76 24 60 24 Z"
            strokeWidth="5"
            strokeLinejoin="round"
          />
        </g>
        <circle className="how-face" cx="60" cy="92" r="8" />
        <path
          className="how-face how-face-stroke"
          d="M60 24 C44 24 35 37 35 53 V68 L27 81 H93 L85 68 V53 C85 37 76 24 60 24 Z"
          strokeWidth="5"
          strokeLinejoin="round"
        />
        <rect className="how-side" x="25" y="78" width="70" height="7" rx="3.5" />
        <circle className="how-face" cx="60" cy="20" r="5.5" />
        <path className="how-light-stroke" d="M44 46 C45 38 50 32 56 30" strokeWidth="4" strokeLinecap="round" fill="none" />
      </g>
    </svg>
  );
}
