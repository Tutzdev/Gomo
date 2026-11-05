import { useLayoutEffect, type RefObject } from "react";

/*
 * Os blocos de cada parte da landing que aparecem ao rolar, e o jeito de cada parte entrar: cada seção tem o seu
 * movimento, para a página não repetir o mesmo "sobe e aparece" do começo ao fim. Blocos irmãos (os cartões de um
 * grid, as perguntas da FAQ, as colunas do rodapé) entram um depois do outro. Os movimentos estão em
 * scroll-reveal.css.
 */
export type Reveals = [selector: string, motion: RevealMotion][];

const PAGE_REVEALS: Reveals = [
  [".hero__title", "focus"],
  [".hero__lead", "rise"],
  [".hero__actions", "pop"],
  [".how-intro", "from-left"],
  [".how-grid > li", "hinge"],
  [".how-cta", "pop"],
  [".plans > *", "zoom"],
  [".landing-faq .landing-heading", "from-left"],
  [".faq-item", "drop"],
  [".landing-closing__panel", "unfold"],
  [".landing-footer__columns > *", "fade"],
  [".landing-footer__bottom", "fade"],
];

export type RevealMotion = "focus" | "rise" | "pop" | "glide" | "from-left" | "hinge" | "zoom" | "drop" | "unfold" | "fade";

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
