import { useEffect, useState } from "react";

/**
 * VTEX stores (Hortifruti, Atacadão…) resize on their own CDN: `/arquivos/ids/{id}-{w}-{h}/`. The catalog shows
 * photos at 40–112 px, and the originals reach 1200 px and 200 KB, so asking for the size on screen turns a
 * search page of ~1 MB of photos into a few dozen KB. Other hosts have no resizer and keep the original.
 */
const VTEX_IMAGE = /^(https:\/\/[^/]+\.(?:vtexassets\.com|vteximg\.com\.br)\/arquivos\/ids\/)(\d+)(?:-\d+-\d+)?\//;

export function productImageUrl(url: string, displayPixels: number) {
  // Twice the CSS size keeps the photo sharp on phone screens.
  const pixels = Math.ceil(displayPixels * 2);
  return url.replace(VTEX_IMAGE, (_, prefix: string, id: string) => `${prefix}${id}-${pixels}-${pixels}/`);
}

/**
 * The photo at the size it is shown; if the resized copy fails it tries the original once, and only then the
 * caller shows its placeholder (`src` becomes null).
 */
export function useProductImage(url: string | null | undefined, displayPixels: number) {
  const [failed, setFailed] = useState<{ url: string; original: boolean } | null>(null);
  if (!url) return { src: null, onError: () => {} };
  const resized = productImageUrl(url, displayPixels);
  const stage = failed?.url === url ? failed : null;
  const src = !stage ? resized : !stage.original && resized !== url ? url : null;
  const onError = () => setFailed({ url, original: stage !== null || resized === url });
  return { src, onError };
}

/**
 * True once the element comes within a short distance of the screen. The browser's own `loading="lazy"` starts
 * up to 2500 px early on a slow connection, which on a search page means every photo of the page at once; the
 * photos come from the markets' sites at up to 200 KB each and cannot be resized, so they wait until they are
 * about to be seen.
 */
export function useNearViewport<T extends Element>(rootMargin = "300px") {
  const [element, setElement] = useState<T | null>(null);
  const [near, setNear] = useState(false);
  useEffect(() => {
    if (near || !element) return;
    if (typeof IntersectionObserver === "undefined") {
      setNear(true);
      return;
    }
    const observer = new IntersectionObserver(
      (entries) => {
        if (entries.some((entry) => entry.isIntersecting)) setNear(true);
      },
      { rootMargin },
    );
    observer.observe(element);
    return () => observer.disconnect();
  }, [element, near, rootMargin]);
  return { ref: setElement, near };
}
