/** An address for links people share: the production site (VITE_SITE_URL) when known, this page's origin otherwise. */
export function siteUrl(path = "/") {
  const base = (import.meta.env.VITE_SITE_URL || window.location.origin).replace(/\/+$/, "");
  return `${base}${path}`;
}
