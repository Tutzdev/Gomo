import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { ArrowLeft, CircleDollarSign, Plus, Trash2 } from "lucide-react";
import { useState } from "react";
import { Link, useParams } from "react-router-dom";
import {
  EmptyState,
  ErrorState,
  InlineError,
  LoadingState,
} from "@/components/ui/feedback";
import { SelectField, TextField } from "@/components/ui/form-field";
import { NativeButton } from "@/components/ui/native-button";
import { PageHeading } from "@/components/page/page-elements";
import { ComparisonStoreFilter } from "@/features/catalog/comparison-store-filter";
import { ProductPicker } from "@/features/catalog/product-picker";
import { ListComparison } from "@/features/shopping-lists/list-comparison";
import { formatDate } from "@/lib/brand";
import {
  catalogApi,
  preferenceApi,
  shoppingListApi,
} from "@/services/gomo-api";
import type { ShoppingList, ShoppingListItem } from "@/types/api";

export function ShoppingListDetailPage() {
  const { id = "" } = useParams();
  const queryClient = useQueryClient();
  const [productId, setProductId] = useState("");
  const [quantity, setQuantity] = useState("1");
  const [storeIds, setStoreIds] = useState<string[]>([]);
  const [comparedStoreIds, setComparedStoreIds] = useState<string[]>([]);
  const [cityId, setCityId] = useState("");
  const [compareCityId, setCompareCityId] = useState("");
  const [addedName, setAddedName] = useState("");
  const [editingName, setEditingName] = useState(false);
  const list = useQuery({
    queryKey: ["shopping-list", id],
    queryFn: () => shoppingListApi.get(id),
    enabled: Boolean(id),
  });
  const cities = useQuery({
    queryKey: ["cities", "list-compare"],
    queryFn: () => catalogApi.cities(),
  });
  const preferences = useQuery({
    queryKey: ["preferences"],
    queryFn: preferenceApi.get,
  });
  const selectedCity =
    cityId ||
    preferences.data?.preferredCityId ||
    cities.data?.content[0]?.id ||
    "";
  const invalidate = async () => {
    await Promise.all([
      queryClient.invalidateQueries({ queryKey: ["shopping-list", id] }),
      queryClient.invalidateQueries({ queryKey: ["shopping-lists"] }),
      queryClient.invalidateQueries({
        queryKey: ["shopping-list-comparison", id],
      }),
      queryClient.invalidateQueries({
        queryKey: ["shopping-list-recommendation", id],
      }),
    ]);
  };
  const addItem = useMutation({
    mutationFn: () =>
      shoppingListApi.addItem(id, { productId, quantity: Number(quantity) }),
    onSuccess: async (item) => {
      setAddedName(item.productName);
      setProductId("");
      setQuantity("1");
      await invalidate();
    },
  });
  if (list.isLoading) return <LoadingState />;
  if (list.isError || !list.data)
    return <ErrorState retry={() => void list.refetch()} />;

  return (
    <>
      <Link
        to="/app/listas"
        className="mb-4 inline-flex items-center gap-2 text-sm font-semibold text-muted hover:text-foreground"
      >
        <ArrowLeft className="size-4" aria-hidden />
        Voltar às listas
      </Link>
      <PageHeading
        title={list.data.name}
        description={`${list.data.items.length} produtos · Atualizada em ${formatDate(list.data.updatedAt)}`}
        action={
          <NativeButton
            variant="secondary"
            onClick={() => setEditingName(!editingName)}
          >
            {editingName ? "Cancelar edição" : "Renomear lista"}
          </NativeButton>
        }
      />
      {editingName ? (
        <RenameList
          list={list.data}
          onSaved={async () => {
            setEditingName(false);
            await invalidate();
          }}
        />
      ) : null}
      <div className="grid items-start gap-6 xl:grid-cols-[1fr_0.65fr]">
        <section className="surface min-w-0">
          <div className="border-b border-border p-5">
            <h2 className="font-bold">Itens da lista</h2>
            <form
              className="mt-4 space-y-4"
              onSubmit={(event) => {
                event.preventDefault();
                if (productId && Number(quantity) > 0) addItem.mutate();
              }}
            >
              <ProductPicker
                cityId={selectedCity}
                storeIds={storeIds}
                id="list-product"
                value={productId}
                onChange={setProductId}
                excludedIds={list.data.items.map((item) => item.productId)}
              />
              <div className="flex items-end gap-3">
                <TextField
                  id="list-quantity"
                  label="Quantidade"
                  type="number"
                  min="1"
                  max="999999"
                  step="1"
                  required
                  value={quantity}
                  onChange={(event) => setQuantity(event.target.value)}
                />
                <NativeButton
                  type="submit"
                  loading={addItem.isPending}
                  disabled={!productId}
                >
                  <Plus className="size-4" aria-hidden />
                  Adicionar
                </NativeButton>
              </div>
            </form>
            <p role="status" className="mt-3 text-sm text-success">
              {addedName ? `${addedName} adicionado à lista.` : ""}
            </p>
            <InlineError>{addItem.error?.message}</InlineError>
          </div>
          {!list.data.items.length ? (
            <div className="p-5">
              <EmptyState
                title="Lista vazia"
                description="Busque produtos de qualquer parte do catálogo para começar."
              />
            </div>
          ) : (
            <ul className="divide-y divide-border">
              {list.data.items.map((item) => (
                <ListItemRow
                  key={`${item.id}-${item.quantity}`}
                  listId={id}
                  item={item}
                  onSaved={invalidate}
                />
              ))}
            </ul>
          )}
        </section>
        <section className="surface p-5">
          <h2 className="font-bold">Comparar a lista</h2>
          <p className="mt-1 text-sm text-muted">
            Veja preços por item, cobertura de cada loja e a melhor combinação
            entre mercados.
          </p>
          <div className="mt-5">
            {cities.isError ? (
              <ErrorState retry={() => void cities.refetch()} />
            ) : (
              <SelectField
                id="list-city"
                label="Cidade"
                value={selectedCity}
                onChange={(event) => {
                  setCityId(event.target.value);
                  setCompareCityId("");
                  setStoreIds([]);
                }}
              >
                <option value="">Selecione</option>
                {cities.data?.content.map((city) => (
                  <option key={city.id} value={city.id}>
                    {city.name}
                  </option>
                ))}
              </SelectField>
            )}
            <div className="mt-4">
              <ComparisonStoreFilter
                cityId={selectedCity}
                value={storeIds}
                onChange={(ids) => {
                  setStoreIds(ids);
                  setCompareCityId("");
                }}
              />
            </div>
            <NativeButton
              className="mt-4 w-full"
              disabled={!selectedCity || !list.data.items.length}
              onClick={() => {
                setCompareCityId(selectedCity);
                setComparedStoreIds(storeIds);
                void queryClient.invalidateQueries({
                  queryKey: ["shopping-list-comparison", id],
                });
                void queryClient.invalidateQueries({
                  queryKey: ["shopping-list-recommendation", id],
                });
              }}
            >
              <CircleDollarSign className="size-4" aria-hidden />
              Comparar lista
            </NativeButton>
          </div>
          <p className="mt-4 text-xs text-muted">
            Produtos sem preço, desatualizados ou indisponíveis aparecem como
            faltantes, nunca como R$ 0,00. Os itens adicionados e as quantidades
            confirmadas ficam salvos na sua conta.
          </p>
        </section>
      </div>
      {compareCityId && list.data.items.length ? (
        <section className="mt-8">
          <h2 className="mb-4 text-xl font-extrabold">
            Comparação da sua lista
          </h2>
          <ListComparison
            key={`${compareCityId}-${comparedStoreIds.join(",")}`}
            storeIds={comparedStoreIds}
            id={id}
            cityId={compareCityId}
            items={list.data.items}
          />
        </section>
      ) : null}
    </>
  );
}

function ListItemRow({
  listId,
  item,
  onSaved,
}: {
  listId: string;
  item: ShoppingListItem;
  onSaved: () => Promise<void>;
}) {
  const [quantity, setQuantity] = useState(String(item.quantity));
  const update = useMutation({
    mutationFn: () =>
      shoppingListApi.updateItem(listId, item.id, Number(quantity)),
    onSuccess: onSaved,
  });
  const remove = useMutation({
    mutationFn: () => shoppingListApi.removeItem(listId, item.id),
    onSuccess: onSaved,
  });
  return (
    <li className="p-5">
      <Link
        to={`/app/produtos/${item.productId}`}
        className="font-semibold hover:text-primary"
      >
        {item.productName}
      </Link>
      <form
        className="mt-3 flex flex-wrap items-end gap-3"
        onSubmit={(event) => {
          event.preventDefault();
          update.mutate();
        }}
      >
        <div className="w-28">
          <TextField
            id={`quantity-${item.id}`}
            label="Quantidade"
            aria-label={`Quantidade de ${item.productName}`}
            type="number"
            min="1"
            max="999999"
            step="1"
            required
            value={quantity}
            onChange={(event) => setQuantity(event.target.value)}
          />
        </div>
        <NativeButton
          type="submit"
          variant="secondary"
          size="sm"
          loading={update.isPending}
          disabled={Number(quantity) === item.quantity || remove.isPending}
        >
          Salvar quantidade
        </NativeButton>
        <NativeButton
          variant="ghost"
          size="icon"
          loading={remove.isPending}
          disabled={update.isPending}
          aria-label={`Remover ${item.productName}`}
          onClick={() => remove.mutate()}
        >
          <Trash2 className="size-4" aria-hidden />
        </NativeButton>
      </form>
      <InlineError>{(update.error ?? remove.error)?.message}</InlineError>
    </li>
  );
}

function RenameList({
  list,
  onSaved,
}: {
  list: ShoppingList;
  onSaved: () => Promise<void>;
}) {
  const [name, setName] = useState(list.name);
  const rename = useMutation({
    mutationFn: () =>
      shoppingListApi.update(list.id, {
        name: name.trim(),
        shoppingType: list.shoppingType,
      }),
    onSuccess: onSaved,
  });
  return (
    <form
      className="surface mb-5 space-y-3 p-5"
      onSubmit={(event) => {
        event.preventDefault();
        rename.mutate();
      }}
    >
      <TextField
        id="list-name"
        label="Nome da lista"
        value={name}
        maxLength={120}
        required
        onChange={(event) => setName(event.target.value)}
      />
      <NativeButton
        type="submit"
        loading={rename.isPending}
        disabled={!name.trim()}
      >
        Salvar nome
      </NativeButton>
      <InlineError>{rename.error?.message}</InlineError>
    </form>
  );
}
