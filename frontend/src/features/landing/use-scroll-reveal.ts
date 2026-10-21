import { useLayoutEffect, type RefObject } from "react";

/*
 * Os blocos de cada parte da landing que aparecem ao rolar, na ordem da página. Blocos irmãos (os cartões de um
 * grid, as perguntas da FAQ, as colunas do rodapé) entram um depois do outro.
 */
const REVEAL_TARGETS = [
  ".hero__title",
  ".hero__lead",
  ".hero__actions",
  ".how-intro",
  ".how-grid > li",
  ".how-cta",
  ".testimonials-head",
  ".testimonials",
  ".testimonials-grid",
  ".testimonials-note",
  ".plans > *",
  ".landing-faq .landing-heading",
  ".faq-item",
  ".landing-closing__panel",
  ".landing-footer__columns > *",
  ".landing-footer__bottom",
].join(", ");

/** Siblings past this one share the last delay, so long lists never make the visitor wait. */
const MAX_STAGGER_STEPS = 5;

/**
 * Fades and lifts each block of the landing into place the first time it scrolls into view. Runs before the first
 * paint, so nothing blinks; without IntersectionObserver or with reduced motion, the page just shows as is.
 */
export function useScrollReveal(rootRef: RefObject<HTMLElement | null>) {
  useLayoutEffect(() => {
    const root = rootRef.current;
    if (!root || !("IntersectionObserver" in window)) return;
    if (window.matchMedia("(prefers-reduced-motion: reduce)").matches) return;

    const targets = Array.from(root.querySelectorAll<HTMLElement>(REVEAL_TARGETS));
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
      target.classList.add("reveal");
      observer.observe(target);
    }

    return () => {
      observer.disconnect();
      for (const target of targets) target.classList.remove("reveal", "is-revealed");
    };
  }, [rootRef]);
}
