import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { ClipboardList, Plus, Store as StoreIcon, Trash2 } from "lucide-react";
import { useEffect, useMemo, useState } from "react";
import { ErrorState, InlineError, SkeletonRows } from "@/components/ui/feedback";
import { SelectField, TextField } from "@/components/ui/form-field";
import { NativeButton } from "@/components/ui/native-button";
import { StatusBadge } from "@/components/page/page-elements";
import { ProductPicker } from "@/features/catalog/product-picker";
import { ApiError } from "@/lib/api";
import { formatDate } from "@/lib/brand";
import { adminApi, catalogApi } from "@/services/gomo-api";
import { STORE_VISIT_BASKET, STORE_VISIT_BASKET_SIZE } from "./store-visit-basket";

interface Reading {
  productId: string;
  price: string;
}

interface Draft {
  observedAt: string;
  readings: Record<string, Reading>;
  extras: string[];
}

const EXTRA_AISLE = "Outros produtos";

function localNow() {
  const now = new Date();
  return new Date(now.getTime() - now.getTimezoneOffset() * 60_000).toISOString().slice(0, 16);
}

function emptyDraft(): Draft {
  return { observedAt: localNow(), readings: {}, extras: [] };
}

// A visit takes about an hour at the shelf: the draft survives a closed tab or a lost connection.
function draftKey(storeId: string) {
  return `gomo:store-visit:${storeId}`;
}

function loadDraft(storeId: string): Draft {
  try {
    const saved = window.localStorage.getItem(draftKey(storeId));
    return saved ? { ...emptyDraft(), ...(JSON.parse(saved) as Draft) } : emptyDraft();
  } catch {
    return emptyDraft();
  }
}

function saveDraft(storeId: string, draft: Draft | null) {
  try {
    if (draft) window.localStorage.setItem(draftKey(storeId), JSON.stringify(draft));
    else window.localStorage.removeItem(draftKey(storeId));
  } catch {
    // Without storage the visit still works; only the draft is lost on reload.
  }
}

/** Shelf prices of a market without an online store, written down item by item during a visit. */
export function StoreVisitPanel() {
  const queryClient = useQueryClient();
  const stores = useQuery({ queryKey: ["stores", "in-store"], queryFn: () => catalogApi.stores(undefined, 0, 200) });
  const inStore = useMemo(
    () => (stores.data?.content ?? []).filter((store) => store.priceChannel === "IN_STORE"),
    [stores.data],
  );
  const [storeId, setStoreId] = useState("");
  const [draft, setDraft] = useState<Draft>(emptyDraft);
  const [aisle, setAisle] = useState(STORE_VISIT_BASKET[0].aisle);
  const [message, setMessage] = useState<string | null>(null);

  useEffect(() => {
    if (storeId) setDraft(loadDraft(storeId));
  }, [storeId]);
  useEffect(() => {
    if (storeId) saveDraft(storeId, draft);
  }, [storeId, draft]);

  const filled = Object.values(draft.readings).filter((reading) => reading.productId && Number(reading.price) > 0);
  const duplicated = filled.length !== new Set(filled.map((reading) => reading.productId)).size;
  const store = inStore.find((candidate) => candidate.id === storeId);

  const submit = useMutation({
    mutationFn: () =>
      adminApi.recordStoreVisit(storeId, {
        observedAt: new Date(draft.observedAt).toISOString(),
        items: filled.map((reading) => ({ productId: reading.productId, price: Number(reading.price) })),
      }),
    onSuccess: async (result) => {
      saveDraft(storeId, null);
      setDraft(emptyDraft());
      setMessage(`${result.recordedPrices} preços gravados. Valem até ${formatDate(result.currentUntil)}.`);
      await queryClient.invalidateQueries({ queryKey: ["stores"] });
    },
    onError: (error) => setMessage(error instanceof ApiError ? error.message : "Não foi possível gravar a visita."),
  });

  const update = (key: string, change: Partial<Reading>) =>
    setDraft((current) => ({
      ...current,
      readings: { ...current.readings, [key]: { ...(current.readings[key] ?? { productId: "", price: "" }), ...change } },
    }));
  const addExtra = () =>
    setDraft((current) => ({ ...current, extras: [...current.extras, `extra-${Date.now()}`] }));
  const removeExtra = (key: string) =>
    setDraft((current) => {
      const readings = { ...current.readings };
      delete readings[key];
      return { ...current, readings, extras: current.extras.filter((extra) => extra !== key) };
    });

  const aisles = [...STORE_VISIT_BASKET.map((section) => section.aisle), EXTRA_AISLE];
  const rows =
    aisle === EXTRA_AISLE
      ? draft.extras.map((key) => ({ key, term: "" }))
      : (STORE_VISIT_BASKET.find((section) => section.aisle === aisle)?.terms ?? []).map((term) => ({
          key: `basket:${term}`,
          term,
        }));
  const filledIn = (name: string) =>
    name === EXTRA_AISLE
      ? draft.extras.filter((key) => Number(draft.readings[key]?.price) > 0 && draft.readings[key]?.productId).length
      : (STORE_VISIT_BASKET.find((section) => section.aisle === name)?.terms ?? []).filter((term) => {
          const reading = draft.readings[`basket:${term}`];
          return reading?.productId && Number(reading.price) > 0;
        }).length;

  if (stores.isLoading) return <div className="surface p-5"><SkeletonRows rows={4} /></div>;
  if (stores.isError) return <div className="surface p-5"><ErrorState retry={() => void stores.refetch()} /></div>;

  return (
    <section className="surface anim-trigger p-4 sm:p-6">
      <div className="flex items-center gap-3">
        <ClipboardList className="size-5 text-primary" aria-hidden />
        <div>
          <h2 className="font-bold">Preço na loja</h2>
          <p className="text-sm text-muted">
            Para mercados sem loja online. Anote o preço da etiqueta de cada item; os preços valem por 7 dias.
          </p>
        </div>
      </div>

      <div className="mt-6 grid gap-4 sm:grid-cols-2">
        <SelectField id="visit-store" label="Mercado" value={storeId} onChange={(event) => { setStoreId(event.target.value); setMessage(null); }}>
          <option value="">Selecione</option>
          {inStore.map((candidate) => <option key={candidate.id} value={candidate.id}>{candidate.name}</option>)}
        </SelectField>
        <TextField id="visit-observed-at" label="Quando você viu os preços" type="datetime-local" value={draft.observedAt}
          max={localNow()} disabled={!storeId}
          onChange={(event) => setDraft((current) => ({ ...current, observedAt: event.target.value }))} />
      </div>
      {!inStore.length ? <p className="mt-4 text-sm text-muted">Nenhum mercado sem loja online cadastrado.</p> : null}
      {store ? (
        <p className="mt-3 flex items-start gap-2 text-sm text-muted">
          <StoreIcon className="mt-0.5 size-4 shrink-0" aria-hidden />
          <span>{store.address ?? store.name}{store.lastPriceCollectedAt ? ` · última visita em ${formatDate(store.lastPriceCollectedAt)}` : " · ainda sem visita"}</span>
        </p>
      ) : null}

      {storeId ? (
        <>
          <div className="mt-6 flex gap-2 overflow-x-auto pb-1" role="tablist" aria-label="Seções do mercado">
            {aisles.map((name) => (
              <button key={name} type="button" role="tab" aria-selected={aisle === name}
                className={`min-h-11 shrink-0 rounded-full border px-3 text-sm font-semibold ${aisle === name ? "border-primary bg-primary-soft text-primary" : "border-border text-muted hover:text-foreground"}`}
                onClick={() => setAisle(name)}>
                {name} <span className="ml-1 text-xs">{filledIn(name)}</span>
              </button>
            ))}
          </div>

          <ul className="mt-4 grid gap-4">
            {rows.map(({ key, term }) => {
              const reading = draft.readings[key] ?? { productId: "", price: "" };
              return (
                <li key={key} className="grid gap-3 rounded-lg border border-border p-3 sm:grid-cols-[1fr_9rem_auto] sm:items-end">
                  <ProductPicker id={`visit-${key.replace(/[^a-z0-9]+/gi, "-")}`} label={term || "Produto"} initialSearch={term}
                    value={reading.productId} onChange={(productId) => update(key, { productId })} />
                  <TextField id={`visit-price-${key.replace(/[^a-z0-9]+/gi, "-")}`} label="Preço (R$)" type="number" inputMode="decimal"
                    min="0.01" step="0.01" value={reading.price} onChange={(event) => update(key, { price: event.target.value })} />
                  {aisle === EXTRA_AISLE ? (
                    <NativeButton type="button" variant="danger" size="sm" onClick={() => removeExtra(key)} aria-label="Remover produto">
                      <Trash2 className="size-4" aria-hidden />
                    </NativeButton>
                  ) : reading.productId && Number(reading.price) > 0 ? <StatusBadge tone="success">Anotado</StatusBadge> : <span />}
                </li>
              );
            })}
          </ul>
          {aisle === EXTRA_AISLE ? (
            <NativeButton type="button" className="mt-4" onClick={addExtra}><Plus className="size-4" aria-hidden />Adicionar produto</NativeButton>
          ) : null}

          <div className="sticky bottom-[var(--app-bottom-inset,0px)] mt-6 flex flex-wrap items-center gap-3 border-t border-border bg-surface py-3">
            <p className="text-sm font-semibold">{filled.length} de {STORE_VISIT_BASKET_SIZE + draft.extras.length} itens anotados</p>
            <NativeButton type="button" loading={submit.isPending} disabled={!filled.length || duplicated || !draft.observedAt}
              onClick={() => { setMessage(null); submit.mutate(); }}>
              Gravar visita
            </NativeButton>
            {duplicated ? <InlineError>O mesmo produto foi escolhido em dois itens.</InlineError> : null}
          </div>
        </>
      ) : null}
      {message ? <p className="mt-3 text-sm" role="status">{message}</p> : null}
    </section>
  );
}
