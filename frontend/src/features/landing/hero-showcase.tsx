import { BatteryFull, Signal, Wifi } from "lucide-react";

/*
 * Prints reais do app, tirados com uma conta de demonstração e os preços coletados em 05/10/2026.
 * Cada print existe em várias larguras, com a largura no nome do arquivo ("app-comparar-712.webp").
 * As versões 1x já têm o tamanho exato exibido na página, para o navegador não precisar redimensionar
 * (é isso que mantém o texto nítido). Para atualizar, gere os arquivos nas mesmas larguras.
 */
const compareScreens = import.meta.glob<string>("./images/app-comparar-*.webp", { eager: true, import: "default" });
const listPhoneScreens = import.meta.glob<string>("./images/app-lista-celular-*.webp", { eager: true, import: "default" });

/** Imagem transparente de 1 px: no celular a tela do computador fica escondida e não deve ser baixada. */
const EMPTY_IMAGE = "data:image/gif;base64,R0lGODlhAQABAIAAAAAAAP///yH5BAEAAAAALAAAAAABAAEAAAIBRAA7";

export function HeroShowcase() {
  return (
    <div className="hero-showcase">
      <picture className="hero-desktop">
        <source
          media="(min-width: 40rem)"
          srcSet={toSrcSet(compareScreens)}
          sizes="(min-width: 80rem) 44.5rem, min(52rem, 100vw)"
        />
        <img
          src={EMPTY_IMAGE}
          alt="Tela de comparação do Gomo: óleo de soja Liza 900 ml em quatro mercados, de R$ 8,79 a R$ 11,59"
          width={712}
          height={765}
          fetchPriority="high"
        />
      </picture>

      <div className="hero-phone">
        <div className="hero-phone__screen">
          <div className="hero-phone__status" aria-hidden>
            <span>9:41</span>
            <span className="hero-phone__island" />
            <span className="hero-phone__signal">
              <Signal className="size-3" />
              <Wifi className="size-3" />
              <BatteryFull className="size-3.5" />
            </span>
          </div>
          <img
            src={listPhoneScreens["./images/app-lista-celular-448.webp"]}
            srcSet={toSrcSet(listPhoneScreens)}
            sizes="(min-width: 80rem) 14rem, (min-width: 40rem) calc(min(52rem, 100vw) / 4 + 2.5rem), 14rem"
            alt="Comparação da lista no celular: o Spani Volta Redonda tem 9 dos 10 itens da compra da semana por R$ 100,28"
            width={224}
            height={402}
          />
        </div>
      </div>
    </div>
  );
}

/** Monta o srcset com a largura que está no nome de cada arquivo: "app-comparar-712.webp" vira "712w". */
function toSrcSet(files: Record<string, string>) {
  return Object.entries(files)
    .map(([path, url]) => `${url} ${path.match(/-(\d+)\.webp$/)?.[1]}w`)
    .join(", ");
}
