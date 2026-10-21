/*
 * Medição de uso e de erros, desligada por padrão. Sem as variáveis VITE_ANALYTICS_* e VITE_SENTRY_DSN no
 * build, nenhum script de terceiros é baixado. A política de privacidade (routes/legal-pages.tsx) descreve
 * o que cada um mede.
 *
 * Os links de confirmação de e-mail e de nova senha levam o token na query string, por isso nada aqui envia
 * a query: o analytics guarda só o caminho e o Sentry recebe o endereço sem "?".
 */

type AnalyticsProvider = "plausible" | "umami";

export interface AnalyticsSettings {
  provider: AnalyticsProvider;
  scriptUrl: string;
  siteId: string;
}

export function analyticsSettings(env: ImportMetaEnv = import.meta.env): AnalyticsSettings | null {
  const provider = env.VITE_ANALYTICS_PROVIDER?.trim().toLowerCase();
  const scriptUrl = env.VITE_ANALYTICS_SCRIPT_URL?.trim();
  const siteId = env.VITE_ANALYTICS_SITE_ID?.trim();
  if ((provider !== "plausible" && provider !== "umami") || !scriptUrl || !siteId) return null;
  if (!/^https:\/\//i.test(scriptUrl) && !scriptUrl.startsWith("/")) return null;
  return { provider, scriptUrl, siteId };
}

/** Plausible e Umami não usam cookie nem guardam IP; os dois acompanham sozinhos a troca de página do app. */
function startAnalytics(settings: AnalyticsSettings) {
  const script = document.createElement("script");
  script.defer = true;
  script.src = settings.scriptUrl;
  if (settings.provider === "plausible") {
    // O Plausible registra só o caminho da página, sem a query string.
    script.dataset.domain = settings.siteId;
  } else {
    script.dataset.websiteId = settings.siteId;
    script.dataset.excludeSearch = "true";
    script.dataset.excludeHash = "true";
    script.dataset.doNotTrack = "true";
  }
  document.head.appendChild(script);
}

export function withoutQuery(url: string) {
  return url.split(/[?#]/, 1)[0];
}

async function startErrorReporting(dsn: string) {
  // Import dinâmico: o código do Sentry só é baixado quando há DSN.
  const Sentry = await import("@sentry/browser");
  Sentry.init({
    dsn,
    environment: import.meta.env.VITE_SENTRY_ENVIRONMENT?.trim() || import.meta.env.MODE,
    sendDefaultPii: false,
    // Só erros: sem rastreamento de desempenho, sem gravação de sessão e sem o histórico de cliques e pedidos.
    tracesSampleRate: 0,
    maxBreadcrumbs: 0,
    beforeSend(event) {
      if (event.request) {
        event.request = { url: event.request.url ? withoutQuery(event.request.url) : undefined };
      }
      delete event.user;
      return event;
    },
  });
}

export function startObservability() {
  const analytics = analyticsSettings();
  if (analytics) startAnalytics(analytics);

  const dsn = import.meta.env.VITE_SENTRY_DSN?.trim();
  if (dsn) void startErrorReporting(dsn).catch(() => undefined);
}
