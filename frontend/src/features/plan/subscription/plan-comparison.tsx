import { Check, Lock } from "lucide-react";

type Cell = { available: true; text: string } | { available: false; text: string };

const rows: Array<{ feature: string; free: Cell; premium: Cell }> = [
  {
    feature: "Mercados na comparação",
    free: { available: true, text: "Os 3 mais baratos" },
    premium: { available: true, text: "Todos os mercados da cidade" },
  },
  {
    feature: "Comparações completas por dia",
    free: { available: true, text: "5" },
    premium: { available: true, text: "Ilimitadas" },
  },
  {
    feature: "Listas de compras",
    free: { available: true, text: "1 lista" },
    premium: { available: true, text: "Ilimitadas, organizadas por nome" },
  },
  {
    feature: "Produtos por lista",
    free: { available: true, text: "Até 10" },
    premium: { available: true, text: "Ilimitados" },
  },
  {
    feature: "Onde comprar cada item da lista",
    free: { available: false, text: "Só o valor da economia" },
    premium: { available: true, text: "Item por item, em cada mercado" },
  },
  {
    feature: "Rota até os mercados",
    free: { available: false, text: "Não incluída" },
    premium: { available: true, text: "Ordem de visita e distância" },
  },
  {
    feature: "Alertas de preço",
    free: { available: true, text: "2 ativos, aviso 1 vez ao dia" },
    premium: { available: true, text: "Ilimitados, a cada coleta (4 ao dia)" },
  },
  {
    feature: "Histórico com gráfico de 90 dias",
    free: { available: false, text: "Não incluído" },
    premium: { available: true, text: "Incluído" },
  },
];

/** Free and Premium side by side; on small screens each row keeps both plans next to each other. */
export function PlanComparison() {
  return (
    <section aria-labelledby="plan-comparison-title">
      <h2 id="plan-comparison-title" className="font-display text-2xl font-bold tracking-[-0.02em]">Grátis ou Premium</h2>
      <p className="mt-2 text-sm text-muted">O grátis já compara de verdade. O Premium mostra a cidade inteira e o caminho mais barato.</p>

      <div className="plan-table mt-6" role="table" aria-label="Recursos do plano grátis e do Premium">
        <div className="plan-table__head" role="row">
          <span role="columnheader" className="plan-table__feature-head">Recurso</span>
          <span role="columnheader" className="plan-table__plan-head">Grátis</span>
          <span role="columnheader" className="plan-table__plan-head plan-table__plan-head--premium">
            Premium
            <span className="plan-table__badge">Recomendado</span>
          </span>
        </div>
        {rows.map((row) => (
          <div key={row.feature} className="plan-table__row" role="row">
            <span role="rowheader" className="plan-table__feature">{row.feature}</span>
            <PlanCell cell={row.free} />
            <PlanCell cell={row.premium} premium />
          </div>
        ))}
      </div>
    </section>
  );
}

function PlanCell({ cell, premium = false }: { cell: Cell; premium?: boolean }) {
  return (
    <span role="cell" className={premium ? "plan-table__cell plan-table__cell--premium" : "plan-table__cell"}>
      {cell.available ? (
        <Check className={premium ? "size-4 shrink-0 text-primary" : "size-4 shrink-0 text-success"} aria-hidden />
      ) : (
        <Lock className="size-4 shrink-0 text-muted" aria-hidden />
      )}
      <span>
        <span className="sr-only">{cell.available ? "Incluído: " : "Bloqueado: "}</span>
        {cell.text}
      </span>
    </span>
  );
}
