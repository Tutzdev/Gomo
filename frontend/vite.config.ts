import path from "node:path";
import { fileURLToPath, URL } from "node:url";
import tailwindcss from "@tailwindcss/vite";
import react from "@vitejs/plugin-react";
import { defineConfig, loadEnv, type HtmlTagDescriptor, type Plugin } from "vite";

const frontendRoot = fileURLToPath(new URL(".", import.meta.url));
const repositoryRoot = path.resolve(frontendRoot, "..");

const SHARE_TITLE = "Gomo — compare antes de comprar";
const SHARE_DESCRIPTION =
  "Monte sua lista e veja em qual mercado de Volta Redonda ela sai mais barata. Preços dos sites dos mercados, atualizados 4 vezes por dia.";

export default defineConfig(({ mode }) => {
  const siteUrl = loadEnv(mode, frontendRoot, "VITE_").VITE_SITE_URL;
  return {
    plugins: [react(), tailwindcss(), socialPreview(siteUrl), robotsTxt(siteUrl)],
    resolve: {
      alias: {
        "@": fileURLToPath(new URL("./src", import.meta.url)),
      },
    },
    server: {
      fs: {
        allow: [repositoryRoot],
      },
    },
  };
});

/**
 * The tags WhatsApp, Facebook and X read to draw a link preview. Their crawlers do not run JavaScript, so the
 * tags have to be in the HTML itself. The image and the canonical address must be absolute URLs: they are only
 * written when VITE_SITE_URL says where the site lives.
 */
function socialPreview(siteUrl: string | undefined): Plugin {
  const base = siteUrl?.trim().replace(/\/+$/, "");
  return {
    name: "gomo-social-preview",
    transformIndexHtml() {
      const meta = (attribute: "property" | "name", key: string, content: string): HtmlTagDescriptor => ({
        tag: "meta",
        attrs: { [attribute]: key, content },
        injectTo: "head",
      });
      const tags = [
        meta("property", "og:type", "website"),
        meta("property", "og:site_name", "Gomo"),
        meta("property", "og:locale", "pt_BR"),
        meta("property", "og:title", SHARE_TITLE),
        meta("property", "og:description", SHARE_DESCRIPTION),
        meta("name", "twitter:card", "summary_large_image"),
        meta("name", "twitter:title", SHARE_TITLE),
        meta("name", "twitter:description", SHARE_DESCRIPTION),
      ];
      if (base) {
        tags.push(
          meta("property", "og:url", `${base}/`),
          meta("property", "og:image", `${base}/og-image.png`),
          meta("property", "og:image:width", "1200"),
          meta("property", "og:image:height", "630"),
          meta("property", "og:image:alt", "Gomo: descubra em qual mercado sua lista sai mais barata"),
          meta("name", "twitter:image", `${base}/og-image.png`),
        );
      }
      return tags;
    },
  };
}

/**
 * robots.txt gerado no build: o app e a API ficam fora da busca, menos o sitemap e as consultas públicas que a
 * página /precos faz para mostrar os preços (o Google não indexa uma página cujos dados ele não pode buscar).
 * O Sitemap precisa de URL absoluta, então só entra quando VITE_SITE_URL está definida.
 */
function robotsTxt(siteUrl: string | undefined): Plugin {
  const site = siteUrl?.trim().replace(/\/+$/, "");
  const lines = [
    "User-agent: *",
    "Disallow: /app",
    "Disallow: /api/",
    "Allow: /api/v1/sitemap.xml",
    "Allow: /api/v1/catalog/items/",
    "Allow: /api/v1/stores?",
    "Allow: /api/v1/cities?",
    "Allow: /api/v1/deals?",
    ...(site ? ["", `Sitemap: ${site}/api/v1/sitemap.xml`] : []),
  ];
  const content = `${lines.join("\n")}\n`;
  return {
    name: "gomo-robots-txt",
    configureServer(server) {
      server.middlewares.use("/robots.txt", (_request, response) => {
        response.setHeader("Content-Type", "text/plain; charset=utf-8");
        response.end(content);
      });
    },
    generateBundle() {
      this.emitFile({ type: "asset", fileName: "robots.txt", source: content });
    },
  };
}
