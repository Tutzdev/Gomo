import atacadao from "@/assets/markets/atacadao.svg";
import bramil from "@/assets/markets/bramil.webp";
import hortifruti from "@/assets/markets/hortifruti.webp";
import nagumo from "@/assets/markets/nagumo.webp";
import pame from "@/assets/markets/pame.webp";
import perola from "@/assets/markets/perola.webp";
import spani from "@/assets/markets/spani.webp";
import supermarket from "@/assets/markets/supermarket.webp";
import ville from "@/assets/markets/ville.webp";

/**
 * How each chain appears in the landing carousel, taken from the chain's own website:
 * - "wordmark": the full logo, name included;
 * - "mark": only a symbol is available in good quality, so the name is written beside it in the Gomo type;
 * - no entry: the market has no logo online, and its name alone is shown.
 * `light` logos are white (made for dark headers) and are drawn as a silhouette instead of in gray.
 */
export type MarketLogo = {
  kind: "wordmark" | "mark";
  src: string;
  /** Visual weight correction, so a tall emblem and a wide wordmark look the same size. */
  scale?: number;
  light?: boolean;
};

const LOGOS: Record<string, MarketLogo> = {
  atacadao: { kind: "mark", src: atacadao },
  bramil: { kind: "mark", src: bramil },
  hortifruti: { kind: "wordmark", src: hortifruti, scale: 1.45 },
  nagumo: { kind: "wordmark", src: nagumo, light: true, scale: 0.9 },
  pame: { kind: "wordmark", src: pame, light: true, scale: 0.8 },
  perola: { kind: "mark", src: perola },
  // Royal's only image online is a 35 px icon with the name inside; the name alone reads better.
  spani: { kind: "wordmark", src: spani, scale: 0.8 },
  supermarket: { kind: "wordmark", src: supermarket, scale: 1.2 },
  ville: { kind: "wordmark", src: ville, scale: 1.1 },
};

/** Chain names come from the collectors ("Pérola", "Royal Supermercados"); accents and case do not matter. */
export function marketLogo(chainName: string): MarketLogo | null {
  return LOGOS[normalize(chainName)] ?? null;
}

function normalize(name: string) {
  return name.normalize("NFD").replace(/[̀-ͯ]/g, "").trim().toLowerCase();
}
