import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { PackagePlus } from "lucide-react";
import { useState } from "react";
import { InlineError } from "@/components/ui/feedback";
import { SelectField, TextField } from "@/components/ui/form-field";
import { NativeButton } from "@/components/ui/native-button";
import { StatusBadge } from "@/components/page/page-elements";
import { ApiError } from "@/lib/api";
import { formatDate } from "@/lib/brand";
import { catalogApi, suggestionApi } from "@/services/gomo-api";

const EMPTY = { name: "", brand: "", packageSize: "", barcode: "", storeId: "", price: "", note: "" };

/** "Não achou o produto?": the shopper describes it and the Gomo team reviews it before it enters the catalog. */
export function ProductSuggestionForm() {
  const queryClient = useQueryClient();
  const [open, setOpen] = useState(false);
  const [form, setForm] = useState(EMPTY);
  const [error, setError] = useState<string | null>(null);
  const mine = useQuery({ queryKey: ["product-suggestions"], queryFn: () => suggestionApi.listMine(), enabled: open });
  const stores = useQuery({ queryKey: ["stores", "suggestions"], queryFn: () => catalogApi.stores(undefined, 0, 200), enabled: open });
  const submit = useMutation({
    mutationFn: () => suggestionApi.submit({
      name: form.name,
      brand: form.brand || undefined,
      packageSize: form.packageSize || undefined,
      barcode: form.barcode || undefined,
      storeId: form.storeId || undefined,
      price: form.price ? Number(form.price) : undefined,
      note: form.note || undefined,
    }),
    onSuccess: async () => {
      setForm(EMPTY);
      setError(null);
      await queryClient.invalidateQueries({ queryKey: ["product-suggestions"] });
    },
    onError: (failure) => setError(failure instanceof ApiError ? failure.message : "Não foi possível enviar a sugestão."),
  });
  const set = (field: keyof typeof EMPTY) => (event: { target: { value: string } }) => setForm({ ...form, [field]: event.target.value });

  return (
    <section className="surface mb-6 p-4 sm:p-5">
      <button type="button" className="flex min-h-11 w-full items-center gap-3 text-left" aria-expanded={open} onClick={() => setOpen(!open)}>
        <PackagePlus className="size-5 text-primary" aria-hidden />
        <span className="font-bold">Não achou o produto? Sugira para a equipe</span>
      </button>
      {open ? (
        <>
          <form className="mt-4 grid gap-4 sm:grid-cols-2 xl:grid-cols-3" onSubmit={(event) => { event.preventDefault(); submit.mutate(); }}>
            <TextField id="suggestion-name" label="Produto" placeholder="Ex.: Biscoito de polvilho" maxLength={200} value={form.name} onChange={set("name")} required />
            <TextField id="suggestion-brand" label="Marca (opcional)" maxLength={120} value={form.brand} onChange={set("brand")} />
            <TextField id="suggestion-size" label="Tamanho (opcional)" placeholder="Ex.: 500 g" maxLength={80} value={form.packageSize} onChange={set("packageSize")} />
            <TextField id="suggestion-barcode" label="Código de barras (opcional)" inputMode="numeric" pattern="[0-9]{8,14}" maxLength={14} value={form.barcode} onChange={set("barcode")} />
            <SelectField id="suggestion-store" label="Mercado onde viu (opcional)" value={form.storeId} onChange={set("storeId")}>
              <option value="">Não sei</option>
              {stores.data?.content.map((store) => <option key={store.id} value={store.id}>{store.name}</option>)}
            </SelectField>
            <TextField id="suggestion-price" label="Preço (opcional)" type="number" min="0.01" step="0.01" inputMode="decimal" value={form.price} onChange={set("price")} disabled={!form.storeId} />
            <TextField id="suggestion-note" label="Observação (opcional)" maxLength={500} value={form.note} onChange={set("note")} className="sm:col-span-2 xl:col-span-3" />
            <div className="sm:col-span-2 xl:col-span-3">
              <InlineError>{error}</InlineError>
              <NativeButton type="submit" loading={submit.isPending} disabled={!form.name.trim()}>Enviar sugestão</NativeButton>
            </div>
          </form>
          {mine.data?.content.length ? (
            <ul className="mt-6 grid gap-2">
              {mine.data.content.map((item) => (
                <li key={item.id} className="flex flex-wrap items-center justify-between gap-2 rounded-lg border border-border p-3 text-sm">
                  <span><span className="font-semibold">{item.name}</span> · enviada em {formatDate(item.submittedAt)}</span>
                  <StatusBadge tone={item.status === "APPROVED" ? "success" : item.status === "REJECTED" ? "danger" : "warning"}>
                    {item.status === "APPROVED" ? "Aceita" : item.status === "REJECTED" ? "Recusada" : "Em análise"}
                  </StatusBadge>
                </li>
              ))}
            </ul>
          ) : null}
        </>
      ) : null}
    </section>
  );
}
