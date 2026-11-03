import { useEffect, useRef } from "react";

/** Back to the top when the page number changes, so "Próxima" shows the new page from its first item. */
export function useScrollToTopOnChange(value: unknown) {
  const first = useRef(true);
  useEffect(() => {
    if (first.current) {
      first.current = false;
      return;
    }
    const reduceMotion = window.matchMedia("(prefers-reduced-motion: reduce)").matches;
    window.scrollTo({ top: 0, behavior: reduceMotion ? "auto" : "smooth" });
  }, [value]);
}
