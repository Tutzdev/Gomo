import { Plus } from "lucide-react";
import { BRAND, formatCurrency } from "@/lib/brand";

const faq = [
  {
    question: "Preciso pagar para usar?",
    answer: "Não. O plano grátis compara os 3 mercados mais baratos de cada produto, com 1 lista de até 10 itens e 2 alertas. O Premium libera todos os mercados, listas ilimitadas, a rota da compra e o histórico de preços.",
  },
  {
    question: "Como os preços são obtidos?",
    answer: "O Gomo coleta os preços publicados pelos supermercados e também recebe contribuições da comunidade. As contribuições passam por moderação antes de virar um registro de preço.",
  },
  {
    question: "Por que algumas lojas não aparecem na comparação?",
    answer: "Só entram na comparação as lojas com preço atual para aquele produto. Uma loja sem preço recente ficaria parecendo mais barata ou mais cara sem motivo, por isso ela fica de fora.",
  },
  {
    question: "Quanto custa?",
    answer: `O Premium custa ${formatCurrency(BRAND.monthlyPrice)} por mês, ou ${formatCurrency(BRAND.annualPrice)} por ano (${formatCurrency(BRAND.annualPrice / 12)} por mês). Dá para testar 7 dias grátis, sem cartão. O pagamento online ainda está em implantação.`,
  },
  {
    question: "Quais cidades e supermercados estão disponíveis?",
    answer: "O Gomo compara os mercados das cidades onde já coleta preços, e novas cidades entram aos poucos. Depois de entrar, você vê as cidades e lojas disponíveis.",
  },
  {
    question: "Com que frequência os preços são atualizados?",
    answer: "Os preços são coletados quatro vezes ao dia, e cada um mostra quando foi coletado. Mercado sem preço recente fica fora da comparação.",
  },
];

export function FaqSection() {
  return (
    <section id="duvidas" className="landing-band scroll-mt-20" aria-labelledby="faq-title">
      <div className="landing-frame landing-faq">
        <div className="landing-heading">
          <h2 id="faq-title" className="landing-heading__title">Dúvidas frequentes</h2>
          <p className="landing-heading__text">O que costumam perguntar antes de assinar.</p>
        </div>

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
