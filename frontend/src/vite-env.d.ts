/// <reference types="vite/client" />

interface ImportMetaEnv {
  readonly VITE_API_BASE_URL?: string;
  /**
   * Endereço público do site (ex.: https://gomo.com.br): prévias de link, links compartilhados, link canônico,
   * JSON-LD e o Sitemap do robots.txt.
   */
  readonly VITE_SITE_URL?: string;
  /** Nome (pessoa ou empresa) responsável pelo Gomo, exibido nos Termos e na Política de Privacidade. */
  readonly VITE_LEGAL_RESPONSIBLE?: string;
  /** E-mail para pedidos sobre dados pessoais (LGPD). */
  readonly VITE_LEGAL_CONTACT_EMAIL?: string;
}

declare module "*.png" {
  const source: string;
  export default source;
}
