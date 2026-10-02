import { useEffect, useRef, useState, useSyncExternalStore } from "react";

const reducedMotionQuery = "(prefers-reduced-motion: reduce)";

function subscribeToReducedMotion(onChange: () => void) {
  const mediaQuery = window.matchMedia(reducedMotionQuery);
  mediaQuery.addEventListener("change", onChange);
  return () => mediaQuery.removeEventListener("change", onChange);
}

export function usePrefersReducedMotion() {
  return useSyncExternalStore(
    subscribeToReducedMotion,
    () => window.matchMedia(reducedMotionQuery).matches,
    () => false,
  );
}

/* Marca o elemento como visível uma única vez, para a revelação não se repetir a cada rolagem. */
export function useRevealOnce<T extends Element>(threshold = 0.3) {
  const ref = useRef<T>(null);
  const [revealed, setRevealed] = useState(false);

  useEffect(() => {
    const element = ref.current;
    if (!element || revealed) return;

    if (!("IntersectionObserver" in window)) {
      setRevealed(true);
      return;
    }

    const observer = new IntersectionObserver(
      ([entry]) => {
        if (entry.isIntersecting) {
          setRevealed(true);
          observer.disconnect();
        }
      },
      { threshold },
    );
    observer.observe(element);
    return () => observer.disconnect();
  }, [revealed, threshold]);

  return { ref, revealed };
}

/* Digita um texto caractere por caractere; com movimento reduzido mostra o texto completo. */
export function useTypedText(text: string, enabled: boolean, characterDelay = 45) {
  const [length, setLength] = useState(enabled ? 0 : text.length);

  useEffect(() => {
    if (!enabled) {
      setLength(text.length);
      return;
    }

    setLength(0);
    const interval = window.setInterval(() => {
      setLength((current) => {
        if (current >= text.length) {
          window.clearInterval(interval);
          return current;
        }
        return current + 1;
      });
    }, characterDelay);
    return () => window.clearInterval(interval);
  }, [text, enabled, characterDelay]);

  return { typed: text.slice(0, length), done: length >= text.length };
}
