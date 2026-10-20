import path from "node:path";
import { fileURLToPath, URL } from "node:url";
import tailwindcss from "@tailwindcss/vite";
import react from "@vitejs/plugin-react";
import { defineConfig, loadEnv, type HtmlTagDescriptor, type Plugin } from "vite";

const repositoryRoot = path.resolve(fileURLToPath(new URL(".", import.meta.url)), "..");

const SHARE_TITLE = "Gomo — compare antes de comprar";
const SHARE_DESCRIPTION =
  "Monte sua lista e veja em qual mercado de Volta Redonda ela sai mais barata. Preços dos sites dos mercados, atualizados 4 vezes por dia.";

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

export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, process.cwd(), "VITE_");
  return {
    plugins: [react(), tailwindcss(), socialPreview(env.VITE_SITE_URL)],
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
