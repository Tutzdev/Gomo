import { useQuery } from "@tanstack/react-query";
import { CalendarClock, TrendingDown, TrendingUp } from "lucide-react";
import { Link } from "react-router-dom";
import { formatCurrency } from "@/lib/brand";
import { recurringListApi } from "@/services/gomo-api";
import type { RecurringList } from "@/types/api";

/**
 * "Sua compra do mês hoje sai R$ 412 no Bramil (R$ 18 a menos que semana passada)": the weekly and monthly lists,
 * recalculated whenever the home page opens. Hidden when the person has none.
 */
export function RecurringListsCard({ cityId }: { cityId: string }) {
  const lists = useQuery({
    queryKey: ["recurring-lists", cityId],
    queryFn: () => recurringListApi.mine(cityId),
    enabled: Boolean(cityId),
  });
  const priced = lists.data?.filter((list) => list.total !== null) ?? [];
  if (!priced.length) return null;

  return (
    <section className="surface mb-4 overflow-hidden" aria-labelledby="recurring-title">
      <h2 id="recurring-title" className="flex items-center gap-2 border-b border-border px-5 py-3 font-bold">
        <CalendarClock className="size-5 text-primary" aria-hidden />
        Suas compras de sempre, hoje
      </h2>
      <ul className="divide-y divide-border">
        {priced.map((list) => <RecurringRow key={list.listId} list={list} />)}
      </ul>
    </section>
  );
}

function RecurringRow({ list }: { list: RecurringList }) {
  const name = `${list.shoppingType === "MONTHLY" ? "Sua lista do mês" : "Sua lista da semana"} “${list.name}”`;
  return (
    <li>
      <Link to={`/app/listas/${list.listId}`} className="flex flex-wrap items-center justify-between gap-3 px-5 py-4 hover:bg-[#fafafa] focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-focus">
        <div className="min-w-0">
          <p className="font-semibold">
            {name} hoje sai <span className="tabular-nums">{formatCurrency(list.total!)}</span> no {list.storeName}
          </p>
          <p className="mt-0.5 text-sm text-muted">
            {!list.complete ? `Com ${list.pricedItems} de ${list.requestedItems} itens; os outros não têm preço lá. ` : ""}
            {comparison(list)}
          </p>
        </div>
        {list.difference !== null && Math.abs(list.difference) >= 0.01 ? (
          <span className={list.difference < 0 ? "inline-flex items-center gap-1 rounded-full bg-success-soft px-2.5 py-1 text-sm font-bold text-success" : "inline-flex items-center gap-1 rounded-full bg-warning-soft px-2.5 py-1 text-sm font-bold text-warning"}>
            {list.difference < 0 ? <TrendingDown className="size-4" aria-hidden /> : <TrendingUp className="size-4" aria-hidden />}
            {list.difference < 0 ? "−" : "+"}{formatCurrency(Math.abs(list.difference))}
          </span>
        ) : null}
      </Link>
    </li>
  );
}

function comparison(list: RecurringList) {
  if (list.listChanged) return "A lista mudou desde a semana passada, então a comparação recomeça hoje.";
  if (list.difference === null) return "Na semana que vem o Gomo compara com hoje.";
  if (Math.abs(list.difference) < 0.01) return "O mesmo valor da semana passada.";
  return list.difference < 0
    ? `${formatCurrency(-list.difference)} a menos que na semana passada.`
    : `${formatCurrency(list.difference)} a mais que na semana passada${list.previousStoreName && list.previousStoreName !== list.storeName ? ` (era no ${list.previousStoreName})` : ""}.`;
}
