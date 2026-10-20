import { useEffect } from "react";

/** Endereço público do site, definido no build; sem ele as tags que pedem URL absoluta ficam de fora. */
export const SITE_URL = import.meta.env.VITE_SITE_URL?.trim().replace(/\/+$/, "") || null;

export function absoluteUrl(path: string) {
  return SITE_URL ? `${SITE_URL}${path}` : null;
}

interface PageMeta {
  title: string;
  description?: string;
  /** Caminho da página (ex.: /precos/...); vira o link canônico quando o endereço do site é conhecido. */
  canonicalPath?: string;
  /** Dados estruturados (schema.org) da página. */
  jsonLd?: object | null;
  noIndex?: boolean;
}

/**
 * Título, descrição, link canônico e JSON-LD de uma página pública. Ao sair dela, o head volta ao que era
 * (os valores padrão do index.html).
 */
export function usePageMeta({ title, description, canonicalPath, jsonLd, noIndex = false }: PageMeta) {
  const canonical = canonicalPath ? absoluteUrl(canonicalPath) : null;
  const structuredData = jsonLd ? JSON.stringify(jsonLd) : null;

  useEffect(() => {
    const previousTitle = document.title;
    document.title = title;
    const restore = [
      description ? setHeadElement("meta", { name: "description" }, { content: description }) : null,
      canonical ? setHeadElement("link", { rel: "canonical" }, { href: canonical }) : null,
      noIndex ? setHeadElement("meta", { name: "robots" }, { content: "noindex" }) : null,
      structuredData
        ? setHeadElement("script", { type: "application/ld+json", "data-page-meta": "" }, {}, structuredData)
        : null,
    ];
    return () => {
      document.title = previousTitle;
      restore.forEach((undo) => undo?.());
    };
  }, [title, description, canonical, noIndex, structuredData]);
}

/** Cria ou atualiza um elemento do head e devolve como desfazer a mudança. */
function setHeadElement(
  tag: "meta" | "link" | "script",
  key: Record<string, string>,
  attributes: Record<string, string>,
  text?: string,
) {
  const selector = tag + Object.entries(key).map(([name, value]) => `[${name}="${value}"]`).join("");
  const existing = document.head.querySelector<HTMLElement>(selector);
  const element = existing ?? document.createElement(tag);
  const previous = Object.fromEntries(Object.keys(attributes).map((name) => [name, element.getAttribute(name)]));
  const previousText = element.textContent;

  Object.entries({ ...key, ...attributes }).forEach(([name, value]) => element.setAttribute(name, value));
  if (text !== undefined) element.textContent = text;
  if (!existing) document.head.appendChild(element);

  return () => {
    if (!existing) {
      element.remove();
      return;
    }
    Object.entries(previous).forEach(([name, value]) =>
      value === null ? element.removeAttribute(name) : element.setAttribute(name, value),
    );
    if (text !== undefined) element.textContent = previousText;
  };
}
