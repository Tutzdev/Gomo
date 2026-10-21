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
  /** Analytics sem cookie, desligado sem as três: "plausible" ou "umami". */
  readonly VITE_ANALYTICS_PROVIDER?: string;
  /** Endereço https do script (ex.: https://plausible.io/js/script.js) ou caminho no próprio domínio. */
  readonly VITE_ANALYTICS_SCRIPT_URL?: string;
  /** Domínio cadastrado no Plausible ou website id do Umami. */
  readonly VITE_ANALYTICS_SITE_ID?: string;
  /** Erros do site no Sentry; sem ele o Sentry não é baixado. */
  readonly VITE_SENTRY_DSN?: string;
  readonly VITE_SENTRY_ENVIRONMENT?: string;
}

declare module "*.png" {
  const source: string;
  export default source;
}
