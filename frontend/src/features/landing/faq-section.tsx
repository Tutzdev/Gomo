import { Plus } from "lucide-react";
import { BRAND, formatCurrency } from "@/lib/brand";
import { joinNames, useCityMarkets } from "./use-city-markets";

const CITIES_FALLBACK = "O Gomo compara os mercados das cidades onde já coleta preços, e novas cidades entram aos poucos.";

export function FaqSection() {
  const cities = useCityMarkets().map((city) => city.name);
  const citiesAnswer = cities.length
    ? `Hoje o Gomo compara os mercados de ${joinNames(cities)}. Novas cidades da região entram aos poucos, e cada uma aparece aqui quando os preços dela começam a ser coletados.`
    : CITIES_FALLBACK;

  const faq = [
    {
      question: "Preciso pagar para usar?",
      answer: "Não. O plano grátis compara os 3 mercados mais baratos de cada produto, com 1 lista de até 10 itens e 2 alertas. O Premium libera todos os mercados, listas ilimitadas, a rota da compra e o histórico de preços.",
    },
    {
      question: "Como os preços são obtidos?",
      answer: "A maior parte vem dos sites dos próprios supermercados, consultados várias vezes por dia. Nos mercados que não vendem pela internet, o preço é conferido na prateleira da loja. Cada preço mostra quando foi visto.",
    },
    {
      question: "Por que algumas lojas não aparecem na comparação?",
      answer: "Só entram na comparação as lojas com preço atual para aquele produto. Uma loja sem preço recente ficaria parecendo mais barata ou mais cara sem motivo, por isso ela fica de fora.",
    },
    {
      question: "Quanto custa?",
      answer: `O Premium custa ${formatCurrency(BRAND.monthlyPrice)} por mês, ou ${formatCurrency(BRAND.annualPrice)} por ano (${formatCurrency(BRAND.annualPrice / 12)} por mês). Dá para testar 7 dias grátis, sem cartão. O pagamento online ainda está em implantação.`,
    },
    { question: "Quais cidades e supermercados estão disponíveis?", answer: citiesAnswer },
    {
      question: "Com que frequência os preços são atualizados?",
      answer: "Os preços dos sites são coletados quatro vezes ao dia. Os conferidos na loja valem por 7 dias. Depois disso, o mercado sai da comparação até ter um preço novo.",
    },
  ];

  return (
    <section id="duvidas" className="landing-section scroll-mt-20" aria-labelledby="faq-title">
      <div className="landing-wide landing-faq">
        <header className="landing-heading">
          <p className="landing-eyebrow">Dúvidas</p>
          <h2 id="faq-title" className="landing-heading__title">Perguntas frequentes</h2>
          <p className="landing-heading__text">O que costumam perguntar antes de começar.</p>
        </header>

        <div className="landing-faq__list">
          {faq.map(({ question, answer }) => (
            <details key={question} className="faq-item">
              <summary>
                {question}
                <span className="faq-item__icon" aria-hidden>
                  <Plus className="size-4" />
                </span>
              </summary>
              <p>{answer}</p>
            </details>
          ))}
        </div>
      </div>
    </section>
  );
}
