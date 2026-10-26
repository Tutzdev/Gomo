import { useQuery, useQueryClient } from "@tanstack/react-query";
import { ArrowLeft, Check, CloudOff, LoaderCircle, PiggyBank, ShoppingCart, Store } from "lucide-react";
import { useCallback, useEffect, useMemo, useState } from "react";
import { Link, useParams } from "react-router-dom";
import { ConfirmDialog } from "@/components/ui/confirm-dialog";
import { ErrorState, LoadingState } from "@/components/ui/feedback";
import { NativeButton } from "@/components/ui/native-button";
import { useAuth } from "@/features/auth/auth-context";
import { CatalogItemThumbnail } from "@/features/catalog/catalog-item-picker";
import { pendingCheck, type TripOperation } from "@/features/trip/trip-queue";
import { clearSession, loadSession, loadSnapshot, saveSession, saveSnapshot, type TripSession } from "@/features/trip/trip-session";
import { useTripQueue, type SyncState } from "@/features/trip/use-trip-queue";
import { formatCurrency } from "@/lib/brand";
import { cn } from "@/lib/cn";
import { contributionApi, tripApi } from "@/services/gomo-api";
import type { Purchase, TripItem } from "@/types/api";

const formatQuantity = (value: number) => value.toLocaleString("pt-BR", { maximumFractionDigits: 3 });

/**
 * "No mercado": the list as a big checklist at one market. Each item shows what it should cost there; a price
 * different on the shelf can be typed in (it goes to the purchase and, as a contribution, to everyone).
 * "Terminei a compra" records the purchase for the savings report. It keeps working when the signal drops:
 * changes wait in the phone and go out when it comes back.
 */
export function TripPage() {
  const { id: listId = "", storeId = "" } = useParams();
  const { user } = useAuth();
  const queryClient = useQueryClient();
  const [session, setSession] = useState<TripSession>(() => loadSession(listId, storeId));
  const [editing, setEditing] = useState<string | null>(null);
  const [confirming, setConfirming] = useState(false);
  const [recorded, setRecorded] = useState<{ purchase: Purchase; listRestarted: boolean } | null>(null);
  const trip = useQuery({
    queryKey: ["trip", listId, storeId],
    queryFn: async () => {
      const loaded = await tripApi.trip(listId, storeId);
      saveSnapshot(loaded);
      return loaded;
    },
    initialData: () => loadSnapshot(listId, storeId),
    initialDataUpdatedAt: 0,
    // Someone else may be shopping with the same list.
    refetchInterval: 20_000,
  });

  const updateSession = useCallback(
    (change: (current: TripSession) => TripSession) => {
      setSession((current) => {
        const next = change(current);
        saveSession(listId, storeId, next);
        return next;
      });
    },
    [listId, storeId],
  );

  const send = useCallback(
    async (operation: TripOperation) => {
      switch (operation.kind) {
        case "check":
          return tripApi.check(listId, operation.itemId);
        case "uncheck":
          return tripApi.uncheck(listId, operation.itemId);
        case "contribution":
          return contributionApi.submit({
            productId: operation.productId,
            storeId: operation.storeId,
            regularPrice: operation.regularPrice,
            currency: "BRL",
            observedAt: operation.observedAt,
            availability: "AVAILABLE",
          });
        case "purchase": {
          const result = await tripApi.recordPurchase(listId, {
            id: operation.purchaseId,
            storeId: operation.storeId,
            items: operation.items,
          });
          clearSession(listId, storeId);
          setSession({ boughtHere: [], shelfPrices: {} });
          setRecorded(result);
          await Promise.all([
            queryClient.invalidateQueries({ queryKey: ["trip", listId] }),
            queryClient.invalidateQueries({ queryKey: ["shopping-list", listId] }),
            queryClient.invalidateQueries({ queryKey: ["savings"] }),
          ]);
          return result;
        }
      }
    },
    [listId, storeId, queryClient],
  );
  const { queue, state, add } = useTripQueue(listId, send);
  // A purchase still in the queue after a reload is still on its way.
  const purchaseWaiting = queue.some((operation) => operation.kind === "purchase");
  useKeepScreenOn();

  const items = useMemo(
    () =>
      (trip.data?.items ?? []).map((item) => ({
        ...item,
        inCart: pendingCheck(queue, item.itemId) ?? item.checkedAt !== null,
        paidPrice: session.shelfPrices[item.itemId] ?? item.unitPrice,
      })),
    [trip.data, queue, session.shelfPrices],
  );

  if (!trip.data) {
    return trip.isError ? <ErrorState message={trip.error.message} retry={() => void trip.refetch()} /> : <LoadingState label="Abrindo a lista no mercado…" />;
  }

  const toPick = items.filter((item) => !item.inCart);
  const inCart = items.filter((item) => item.inCart);
  const cartTotal = inCart.reduce((sum, item) => sum + (item.paidPrice === null ? 0 : item.paidPrice * item.quantity), 0);
  const toggle = (item: TripItem & { inCart: boolean }) => {
    add({ kind: item.inCart ? "uncheck" : "check", itemId: item.itemId });
    updateSession((current) => ({
      ...current,
      boughtHere: item.inCart
        ? current.boughtHere.filter((itemId) => itemId !== item.itemId)
        : [...new Set([...current.boughtHere, item.itemId])],
    }));
  };
  const saveShelfPrice = (item: TripItem, price: number) => {
    updateSession((current) => ({ ...current, shelfPrices: { ...current.shelfPrices, [item.itemId]: price } }));
    // A typed price helps everyone once a moderator approves it; contributions need a confirmed e-mail.
    if (user?.emailVerified && price !== item.unitPrice) {
      add({ kind: "contribution", productId: item.storeProductId, storeId, regularPrice: price, observedAt: new Date().toISOString() });
    }
    setEditing(null);
  };
  const finish = () => {
    const boughtHere = new Set(session.boughtHere);
    const bought = inCart.filter((item) => boughtHere.size === 0 || boughtHere.has(item.itemId));
    add({
      kind: "purchase",
      purchaseId: crypto.randomUUID(),
      storeId,
      items: bought.map((item) => ({ itemId: item.itemId, shelfPrice: session.shelfPrices[item.itemId] })),
    });
    setConfirming(false);
  };

  return (
    <div className="mx-auto max-w-2xl pb-32">
      <Link to={`/app/listas/${listId}`} className="mb-4 inline-flex items-center gap-2 text-sm font-semibold text-muted hover:text-foreground">
        <ArrowLeft className="size-4" aria-hidden />
        Voltar à lista
      </Link>
      <header className="flex flex-wrap items-start justify-between gap-3">
        <div className="min-w-0">
          <p className="text-sm font-semibold text-muted">No mercado · {trip.data.listName}</p>
          <h1 className="mt-1 flex items-center gap-2 text-2xl font-extrabold leading-tight">
            <Store className="size-6 shrink-0 text-primary" aria-hidden />
            {trip.data.storeName}
          </h1>
        </div>
        <SyncBadge state={state} waiting={queue.length} />
      </header>

      <div className="mt-4" aria-live="polite">
        <div className="flex items-baseline justify-between text-sm">
          <span className="font-semibold">{inCart.length} de {items.length} no carrinho</span>
          <span className="text-muted">Previsto aqui: {formatCurrency(trip.data.expectedTotal)}</span>
        </div>
        <div className="mt-2 h-2 overflow-hidden rounded-full bg-surface-strong" aria-hidden>
          <div className="h-full rounded-full bg-success transition-[width] motion-reduce:transition-none" style={{ width: `${items.length ? (inCart.length / items.length) * 100 : 0}%` }} />
        </div>
      </div>

      {recorded ? <PurchaseRecorded result={recorded} listId={listId} /> : null}
      {purchaseWaiting && state !== "sending" ? (
        <p className="mt-5 rounded-lg border border-warning/25 bg-warning-soft p-4 text-sm">
          Compra guardada no aparelho. Ela é registrada assim que o sinal voltar; pode sair do mercado tranquilo.
        </p>
      ) : null}

      <section className="mt-6" aria-labelledby="to-pick">
        <h2 id="to-pick" className="text-sm font-bold uppercase tracking-wide text-muted">Falta pegar ({toPick.length})</h2>
        {toPick.length ? (
          <ul className="mt-2 space-y-2">
            {toPick.map((item) => (
              <TripRow key={item.itemId} item={item} shelfPrice={session.shelfPrices[item.itemId]} editing={editing === item.itemId} onToggle={() => toggle(item)} onEdit={() => setEditing(editing === item.itemId ? null : item.itemId)} onSavePrice={(price) => saveShelfPrice(item, price)} me={user?.id} />
            ))}
          </ul>
        ) : (
          <p className="mt-2 rounded-lg bg-success-soft p-4 text-sm font-semibold text-success">Tudo no carrinho.</p>
        )}
      </section>

      {inCart.length ? (
        <section className="mt-6" aria-labelledby="in-cart">
          <h2 id="in-cart" className="text-sm font-bold uppercase tracking-wide text-muted">No carrinho ({inCart.length})</h2>
          <ul className="mt-2 space-y-2">
            {inCart.map((item) => (
              <TripRow key={item.itemId} item={item} shelfPrice={session.shelfPrices[item.itemId]} editing={editing === item.itemId} onToggle={() => toggle(item)} onEdit={() => setEditing(editing === item.itemId ? null : item.itemId)} onSavePrice={(price) => saveShelfPrice(item, price)} me={user?.id} />
            ))}
          </ul>
        </section>
      ) : null}

      <div className="fixed inset-x-0 bottom-0 z-20 border-t border-border bg-white/95 px-4 py-3 backdrop-blur supports-[backdrop-filter]:bg-white/85">
        <div className="mx-auto flex max-w-2xl items-center justify-between gap-3">
          <div>
            <p className="text-xs text-muted">No carrinho</p>
            <p className="text-xl font-extrabold tabular-nums">{formatCurrency(cartTotal)}</p>
          </div>
          <NativeButton size="lg" disabled={!inCart.length || purchaseWaiting} onClick={() => setConfirming(true)}>
            <ShoppingCart className="size-5" aria-hidden />
            Terminei a compra
          </NativeButton>
        </div>
      </div>

      <ConfirmDialog
        open={confirming}
        onOpenChange={setConfirming}
        title="Registrar a compra?"
        description={`${inCart.length} ${inCart.length === 1 ? "item" : "itens"} no carrinho, ${formatCurrency(cartTotal)} no ${trip.data.storeName}. O Gomo compara com a média dos mercados da cidade e soma na sua economia do mês.`}
        confirmLabel="Registrar compra"
        onConfirm={finish}
      />
    </div>
  );
}

function TripRow({
  item,
  shelfPrice,
  editing,
  onToggle,
  onEdit,
  onSavePrice,
  me,
}: {
  item: TripItem & { inCart: boolean; paidPrice: number | null };
  shelfPrice: number | undefined;
  editing: boolean;
  onToggle: () => void;
  onEdit: () => void;
  onSavePrice: (price: number) => void;
  /** The person using the phone: their own checks need no name. */
  me: string | undefined;
}) {
  const [typed, setTyped] = useState(shelfPrice !== undefined ? String(shelfPrice) : "");
  return (
    <li className={cn("surface overflow-hidden", item.inCart && "opacity-75")}>
      <div className="flex items-center gap-3 p-3">
        <button
          type="button"
          role="checkbox"
          aria-checked={item.inCart}
          aria-label={item.inCart ? `Tirar ${item.productName} do carrinho` : `Pôr ${item.productName} no carrinho`}
          onClick={onToggle}
          className={cn(
            "grid size-12 shrink-0 place-items-center rounded-xl border-2 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-focus",
            item.inCart ? "border-success bg-success text-white" : "border-border bg-white text-transparent hover:border-success",
          )}
        >
          <Check className="size-7" aria-hidden />
        </button>
        <CatalogItemThumbnail imageUrl={item.imageUrl} size="sm" />
        <div className="min-w-0 flex-1">
          <p className={cn("font-semibold leading-snug", item.inCart && "line-through")}>{item.productName}</p>
          <p className="text-sm text-muted">{formatQuantity(item.quantity)} {item.quantity === 1 ? "unidade" : "unidades"}</p>
          {item.inCart && item.checkedByName && item.checkedBy !== me ? <p className="text-xs font-semibold text-success">{item.checkedByName} pôs no carrinho</p> : null}
        </div>
        <div className="shrink-0 text-right">
          {item.paidPrice !== null ? (
            <p className="font-bold tabular-nums">{formatCurrency(item.paidPrice)}</p>
          ) : (
            <p className="text-xs text-muted">Sem preço aqui</p>
          )}
          {shelfPrice !== undefined ? <p className="text-xs text-muted">da gôndola</p> : item.promotion ? <p className="text-xs font-semibold text-success">promoção</p> : null}
          <button type="button" onClick={onEdit} className="mt-1 text-xs font-semibold text-primary-dark hover:underline">
            {item.unitPrice === null && shelfPrice === undefined ? "Informar preço" : "Preço diferente?"}
          </button>
        </div>
      </div>
      {editing ? (
        <form
          className="flex items-end gap-2 border-t border-border bg-surface-strong p-3"
          onSubmit={(event) => {
            event.preventDefault();
            const price = Number(typed.replace(",", "."));
            if (price > 0) onSavePrice(Math.round(price * 100) / 100);
          }}
        >
          <label className="min-w-0 flex-1 text-sm font-semibold" htmlFor={`shelf-${item.itemId}`}>
            Preço na gôndola (R$)
            <input
              id={`shelf-${item.itemId}`}
              inputMode="decimal"
              autoFocus
              value={typed}
              onChange={(event) => setTyped(event.target.value)}
              className="mt-1 block min-h-11 w-full rounded-lg border border-border bg-white px-3 text-base tabular-nums focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-focus"
              placeholder={item.unitPrice === null ? "0,00" : String(item.unitPrice).replace(".", ",")}
            />
          </label>
          <NativeButton type="submit" disabled={!(Number(typed.replace(",", ".")) > 0)}>Salvar</NativeButton>
        </form>
      ) : null}
    </li>
  );
}

function SyncBadge({ state, waiting }: { state: SyncState; waiting: number }) {
  if (state === "sending") {
    return (
      <span className="inline-flex items-center gap-1.5 rounded-full bg-surface-strong px-3 py-1 text-xs font-semibold text-muted">
        <LoaderCircle className="size-3.5 animate-spin motion-reduce:animate-none" aria-hidden />
        Salvando…
      </span>
    );
  }
  if (waiting > 0) {
    return (
      <span className="inline-flex items-center gap-1.5 rounded-full bg-warning-soft px-3 py-1 text-xs font-semibold text-warning" role="status">
        <CloudOff className="size-3.5" aria-hidden />
        Sem sinal: guardado no aparelho
      </span>
    );
  }
  return (
    <span className="inline-flex items-center gap-1.5 rounded-full bg-success-soft px-3 py-1 text-xs font-semibold text-success">
      <Check className="size-3.5" aria-hidden />
      Salvo
    </span>
  );
}

function PurchaseRecorded({ result, listId }: { result: { purchase: Purchase; listRestarted: boolean }; listId: string }) {
  const { purchase } = result;
  return (
    <section className="mt-5 rounded-lg border border-success/25 bg-success-soft p-4" role="status">
      <p className="flex items-center gap-2 font-bold">
        <PiggyBank className="size-5 text-success" aria-hidden />
        Compra registrada: {formatCurrency(purchase.totalPaid)} no {purchase.storeName}
      </p>
      <p className="mt-1 text-sm">
        {purchase.comparedItems === 0
          ? "Nenhum item tinha preço em outro mercado da cidade para comparar."
          : purchase.savings >= 0
            ? `Você pagou ${formatCurrency(purchase.savings)} a menos que a média da cidade nos ${purchase.comparedItems} itens comparados.`
            : `Desta vez saiu ${formatCurrency(-purchase.savings)} acima da média da cidade nos ${purchase.comparedItems} itens comparados.`}
        {result.listRestarted ? " A lista foi desmarcada para a próxima compra." : ""}
      </p>
      <div className="mt-3 flex flex-wrap gap-2">
        <NativeButton to="/app/economia" size="sm">Ver minha economia do mês</NativeButton>
        <NativeButton to={`/app/listas/${listId}`} size="sm" variant="secondary">Voltar à lista</NativeButton>
      </div>
    </section>
  );
}

/** A phone that locks every 30 seconds in the middle of the aisle is the main reason to give up on a checklist. */
function useKeepScreenOn() {
  useEffect(() => {
    let lock: WakeLockSentinel | null = null;
    let released = false;
    navigator.wakeLock
      ?.request("screen")
      .then((sentinel) => {
        if (released) void sentinel.release();
        else lock = sentinel;
      })
      .catch(() => undefined);
    return () => {
      released = true;
      void lock?.release();
    };
  }, []);
}
