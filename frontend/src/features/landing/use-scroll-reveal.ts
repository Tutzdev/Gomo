import { useLayoutEffect, type RefObject } from "react";

/*
 * Os blocos da landing que aparecem ao rolar, e o jeito de cada um entrar. O movimento segue o tipo de bloco
 * (texto sobe, cartão pousa, moldura só aparece), não a seção: assim a página varia sem parecer que cada parte
 * é de um site diferente. Blocos irmãos (os cartões de um grid, as perguntas da FAQ) entram um depois do outro.
 * Os movimentos estão em scroll-reveal.css.
 */
export type Reveals = [selector: string, motion: RevealMotion][];

const PAGE_REVEALS: Reveals = [
  [".hero__title", "focus"],
  [".hero__lead", "rise"],
  [".hero__actions", "rise"],
  ["#como-funciona .landing-heading", "rise"],
  [".how-grid > li", "card"],
  [".how-cta", "rise"],
  [".plans", "card"],
  [".landing-faq .landing-heading", "rise"],
  [".faq-item", "rise"],
  // O rodapé fica parado: as últimas linhas da página nunca passam da borda de baixo da tela para "entrar".
  [".landing-closing", "card"],
];

export type RevealMotion = "focus" | "rise" | "card" | "fade";

/** Siblings past this one share the last delay, so long lists never make the visitor wait. */
const MAX_STAGGER_STEPS = 5;

/**
 * Brings each block of the landing into place, with its section's motion, the first time it scrolls into view. Runs before the first
 * paint, so nothing blinks; without IntersectionObserver or with reduced motion, the page just shows as is.
 */
export function useScrollReveal(rootRef: RefObject<HTMLElement | null>, reveals: Reveals = PAGE_REVEALS) {
  useLayoutEffect(() => {
    const root = rootRef.current;
    if (!root || !("IntersectionObserver" in window)) return;
    if (window.matchMedia("(prefers-reduced-motion: reduce)").matches) return;

    const motions = new Map<HTMLElement, RevealMotion>();
    for (const [selector, motion] of reveals) {
      for (const element of root.querySelectorAll<HTMLElement>(selector)) motions.set(element, motion);
    }
    const targets = [...motions.keys()];
    const observer = new IntersectionObserver(
      (entries) => {
        for (const entry of entries) {
          if (!entry.isIntersecting) continue;
          entry.target.classList.add("is-revealed");
          observer.unobserve(entry.target);
        }
      },
      // A little before the block reaches the bottom edge, so it is already moving when the eye gets there.
      { rootMargin: "0px 0px -8% 0px", threshold: 0.12 },
    );

    for (const target of targets) {
      const siblings = Array.from(target.parentElement?.children ?? []).filter((child) => targets.includes(child as HTMLElement));
      const step = Math.min(siblings.indexOf(target), MAX_STAGGER_STEPS);
      target.style.setProperty("--reveal-step", String(Math.max(step, 0)));
      target.dataset.reveal = motions.get(target);
      target.classList.add("reveal");
      observer.observe(target);
    }

    return () => {
      observer.disconnect();
      for (const target of targets) {
        target.classList.remove("reveal", "is-revealed");
        delete target.dataset.reveal;
      }
    };
  }, [rootRef, reveals]);
}
