/**
 * Endereço público da página de preço de um produto: o nome legível seguido do id, como em
 * /precos/coca-cola-2-l-<id>. O sitemap do backend monta o mesmo slug (seo/ProductPagePath.java).
 */
const MAXIMUM_SLUG_LENGTH = 80;
const TRAILING_ID = /([0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12})$/i;

export function productPagePath(id: string, name: string) {
  return `/precos/${productSlug(name)}-${id}`;
}

export function productIdFromSlug(slug: string) {
  return TRAILING_ID.exec(slug)?.[1]?.toLowerCase() ?? null;
}

function productSlug(name: string) {
  let plain = name
    .normalize("NFD")
    .replace(/\p{M}/gu, "")
    .toLowerCase()
    .replace(/[^a-z0-9]+/g, "-")
    .replace(/^-+|-+$/g, "");
  if (plain.length > MAXIMUM_SLUG_LENGTH) plain = plain.slice(0, MAXIMUM_SLUG_LENGTH).replace(/-+$/, "");
  return plain || "produto";
}
