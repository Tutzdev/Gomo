import gomoIcon from "../../../../gomo-icone.png";
import { formatCurrency } from "@/lib/brand";
import { cn } from "@/lib/cn";
import { formatCollectionTime, isToday, useBiggestPriceGap } from "./use-biggest-price-gap";

/* Desenhos dos cards: preços de exemplo, com os mesmos nomes genéricos de lojas da hero. */
const shelfTags = [
  { store: "Supermercado Avenida", price: 10.49 },
  { store: "Atacarejo Norte", price: 9.79 },
  { store: "Mercado Central", price: 8.99 },
];

const receiptItems = [
  { name: "Arroz tipo 1, 5 kg", price: 25.98 },
  { name: "Feijão carioca, 1 kg", price: 7.49 },
  { name: "Café torrado, 500 g", price: 17.89 },
  { name: "Leite integral, 6 L", price: 29.94 },
];

const features = [
  {
    title: "Compare um produto.",
    description: "Digite o que você quer e veja na hora quem cobra menos por ele.",
    illustration: <ShelfTags />,
  },
  {
    title: "Compare a lista inteira.",
    description: "Monte a compra da semana e descubra em qual mercado ela sai mais barata.",
    illustration: <Receipt />,
  },
  {
    title: "Seja avisado.",
    description: "Diga quanto quer pagar. Quando algum mercado chegar lá, você fica sabendo.",
    illustration: <PriceAlert />,
  },
];

export function FeaturesSection() {
  return (
    <section id="como-funciona" className="landing-band scroll-mt-20" aria-labelledby="features-title">
      <div className="landing-frame">
        <div className="features-intro">
          <h2 id="features-title" className="landing-heading__title">O mesmo produto não custa o mesmo em todo mercado.</h2>
          <PriceGapText />
        </div>

        <ul className="features-grid">
          {features.map(({ title, description, illustration }) => (
            <li key={title} className="feature">
              <div className="feature__art" aria-hidden>
                <div className="feature__object">{illustration}</div>
              </div>
              <div className="feature__copy">
                <h3 className="feature__title">{title}</h3>
                <p className="feature__text">{description}</p>
              </div>
            </li>
          ))}
        </ul>
      </div>
    </section>
  );
}

/** Um exemplo real da cidade, quando a API responde; sem dados, a frase continua verdadeira sem o número. */
function PriceGapText() {
  const gap = useBiggestPriceGap();
  const when = gap?.collectedAt && isToday(gap.collectedAt) ? "Hoje" : "Na última coleta";

  return (
    <div className="features-intro__text">
      {gap ? (
        <p>
          {when}, <strong>{gap.productName}</strong> sai por {formatCurrency(gap.lowest)} num mercado e{" "}
          {formatCurrency(gap.highest)} em outro da mesma cidade. O Gomo mostra essa diferença antes de você sair de casa.
        </p>
      ) : (
        <p>
          De um mercado para outro, o mesmo item pode custar bem mais. O Gomo mostra essa diferença antes de você sair de casa.
        </p>
      )}
      {gap?.collectedAt ? (
        <p className="features-intro__source">Preço real, coletado {formatCollectionTime(gap.collectedAt)}.</p>
      ) : null}
    </div>
  );
}

function ShelfTags() {
  return (
    <div className="art-tags">
      {shelfTags.map((tag, index) => (
        <span key={tag.store} className={cn("art-tag", index === shelfTags.length - 1 && "is-cheapest")}>
          <span>{tag.store}</span>
          <strong>{formatCurrency(tag.price)}</strong>
        </span>
      ))}
    </div>
  );
}

function Receipt() {
  const total = 287.4;
  return (
    <div className="art-receipt">
      <p className="art-receipt__store">Mercado Central</p>
      <ul>
        {receiptItems.map((item) => (
          <li key={item.name}>
            <span className="truncate">{item.name}</span>
            <span>{formatCurrency(item.price).replace("R$", "").trim()}</span>
          </li>
        ))}
        <li className="art-receipt__more">mais 14 itens</li>
      </ul>
      <p className="art-receipt__total">
        <span>Total</span>
        <strong>{formatCurrency(total)}</strong>
        {/* O círculo de caneta em volta do total, como quem marca o preço no encarte. */}
        <svg className="art-receipt__circle" viewBox="0 0 200 60" preserveAspectRatio="none">
          <path d="M14 34 C 12 14, 72 6, 118 8 C 168 10, 194 20, 188 36 C 182 52, 120 56, 74 54 C 30 52, 6 44, 16 26 C 22 16, 40 12, 56 11" />
        </svg>
      </p>
    </div>
  );
}

function PriceAlert() {
  return (
    <div className="art-alerts">
      <div className="art-alert art-alert--behind" />
      <div className="art-alert">
        <img src={gomoIcon} alt="" className="art-alert__icon" />
        <div className="min-w-0">
          <p className="art-alert__head">
            <strong>Gomo</strong>
            <span>agora</span>
          </p>
          <p className="art-alert__title">Café 500 g baixou para {formatCurrency(17.89)}</p>
          <p className="art-alert__text">Você queria pagar até {formatCurrency(18)}.</p>
        </div>
      </div>
    </div>
  );
}
