import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { ArrowLeft, CircleDollarSign, LogOut, Plus, Trash2, Users } from "lucide-react";
import { useState } from "react";
import { Link, useNavigate, useParams } from "react-router-dom";
import {
  EmptyState,
  ErrorState,
  InlineError,
  LoadingState,
} from "@/components/ui/feedback";
import { SelectField, TextField } from "@/components/ui/form-field";
import { ConfirmDialog } from "@/components/ui/confirm-dialog";
import { NativeButton } from "@/components/ui/native-button";
import { useAuth } from "@/features/auth/auth-context";
import { PageHeading } from "@/components/page/page-elements";
import { ComparisonStoreFilter } from "@/features/catalog/comparison-store-filter";
import {
  CatalogItemPicker,
  CatalogItemThumbnail,
} from "@/features/catalog/catalog-item-picker";
import { GoodPricesCard } from "@/features/shopping-lists/good-prices-card";
import { ListComparison } from "@/features/shopping-lists/list-comparison";
import { ShareListSheet } from "@/features/shopping-lists/share-list-sheet";
import { SubstitutesCard } from "@/features/shopping-lists/substitutes-card";
import { formatDate } from "@/lib/brand";
import { catalogApi, sharingApi, shoppingListApi } from "@/services/gomo-api";
import type {
  CatalogItem,
  ShoppingList,
  ShoppingListItem,
  ShoppingType,
} from "@/types/api";
import { useDefaultCityId } from "@/lib/use-default-city";
import { PlanLimitNotice } from "@/features/plan/plan-locks";
import { useLimitUpsell } from "@/features/plan/use-limit-upsell";
import { planLimitOf, usePlan } from "@/features/plan/use-plan";

const FREE_LIST_ITEMS = 10;

export function ShoppingListDetailPage() {
  const { id = "" } = useParams();
  const queryClient = useQueryClient();
  const [catalogItem, setCatalogItem] = useState<CatalogItem | null>(null);
  const [quantity, setQuantity] = useState("1");
  const [storeIds, setStoreIds] = useState<string[]>([]);
  const [comparedStoreIds, setComparedStoreIds] = useState<string[]>([]);
  const [cityId, setCityId] = useState("");
  const [compareCityId, setCompareCityId] = useState("");
  const [addedName, setAddedName] = useState("");
  const [editingName, setEditingName] = useState(false);
  const [sharing, setSharing] = useState(false);
  const [leaving, setLeaving] = useState(false);
  const navigate = useNavigate();
  const list = useQuery({
    queryKey: ["shopping-list", id],
    queryFn: () => shoppingListApi.get(id),
    enabled: Boolean(id),
    // A shared list changes in other hands too.
    refetchInterval: (query) => (query.state.data && (!query.state.data.owned || query.state.data.members.length) ? 15_000 : false),
  });
  const { user } = useAuth();
  const leave = useMutation({
    mutationFn: () => sharingApi.removeMember(id, user!.id),
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: ["shopping-lists"] });
      navigate("/app/listas", { replace: true });
    },
  });
  const cities = useQuery({
    queryKey: ["cities", "list-compare"],
    queryFn: () => catalogApi.cities(),
  });
  const defaultCityId = useDefaultCityId();
  const selectedCity = cityId || defaultCityId;
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
      queryClient.invalidateQueries({
        queryKey: ["shopping-list-good-prices", id],
      }),
      queryClient.invalidateQueries({
        queryKey: ["shopping-list-substitutes", id],
      }),
    ]);
  };
  const { premium } = usePlan();
  const itemLimit = useLimitUpsell();
  const addItem = useMutation({
    mutationFn: () =>
      shoppingListApi.addItem(id, {
        catalogItemId: catalogItem?.id,
        quantity: Number(quantity),
      }),
    onMutate: () => itemLimit.clear(),
    onError: (error) => {
      if (planLimitOf(error) === "LIST_ITEMS") itemLimit.block("LIST_ITEMS");
    },
    onSuccess: async (item) => {
      setAddedName(item.productName);
      setCatalogItem(null);
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
          list.data.owned ? (
            <div className="flex flex-wrap gap-2">
              <NativeButton variant="secondary" onClick={() => setSharing(true)}>
                <Users className="size-4" aria-hidden />
                {list.data.members.length ? `Compartilhada (${list.data.members.length + 1})` : "Compartilhar"}
              </NativeButton>
              <NativeButton
                variant="secondary"
                onClick={() => setEditingName(!editingName)}
              >
                {editingName ? "Cancelar edição" : "Editar lista"}
              </NativeButton>
            </div>
          ) : (
            <NativeButton variant="secondary" onClick={() => setLeaving(true)}>
              <LogOut className="size-4" aria-hidden />
              Sair da lista
            </NativeButton>
          )
        }
      />
      {!list.data.owned ? (
        <p className="mb-5 flex items-center gap-2 rounded-lg bg-primary-soft px-4 py-3 text-sm">
          <Users className="size-4 shrink-0 text-primary" aria-hidden />
          Lista de {list.data.ownerName ?? "outra pessoa"}, compartilhada com você: o que você muda aparece para todos.
        </p>
      ) : null}
      {list.data.owned ? <ShareListSheet list={list.data} open={sharing} onOpenChange={setSharing} /> : null}
      <ConfirmDialog
        open={leaving}
        onOpenChange={setLeaving}
        title="Sair da lista?"
        description={`A lista "${list.data.name}" some das suas listas. Para voltar, peça um convite novo a ${list.data.ownerName ?? "quem compartilhou"}.`}
        confirmLabel="Sair da lista"
        loading={leave.isPending}
        error={leave.error?.message}
        onConfirm={() => leave.mutate()}
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
      {selectedCity && list.data.items.length ? (
        <>
          <GoodPricesCard listId={id} cityId={selectedCity} onChanged={invalidate} />
          <SubstitutesCard listId={id} cityId={selectedCity} onChanged={invalidate} />
        </>
      ) : null}
      <div className="grid items-start gap-6 xl:grid-cols-[1fr_0.65fr]">
        <section className="surface min-w-0">
          <div className="border-b border-border p-5">
            <div className="flex flex-wrap items-baseline justify-between gap-2">
              <h2 className="font-bold">Itens da lista</h2>
              {!premium ? (
                <p className="text-xs text-muted">
                  {Math.min(list.data.items.length, FREE_LIST_ITEMS)} de {FREE_LIST_ITEMS} produtos no plano grátis
                </p>
              ) : null}
            </div>
            <form
              className="mt-4 space-y-4"
              onSubmit={(event) => {
                event.preventDefault();
                if (catalogItem && Number(quantity) > 0) addItem.mutate();
              }}
            >
              <CatalogItemPicker
                id="list-product"
                cityId={selectedCity}
                storeIds={storeIds}
                value={catalogItem}
                onChange={setCatalogItem}
                excludedIds={list.data.items.flatMap((item) =>
                  item.catalogItemId ? [item.catalogItemId] : [],
                )}
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
                  disabled={!catalogItem}
                >
                  <Plus className="size-4" aria-hidden />
                  Adicionar
                </NativeButton>
              </div>
            </form>
            <p role="status" className="mt-3 text-sm text-success">
              {addedName ? `${addedName} adicionado à lista.` : ""}
            </p>
            <InlineError>{planLimitOf(addItem.error) ? null : addItem.error?.message}</InlineError>
            {itemLimit.notice ? <PlanLimitNotice>{itemLimit.notice}</PlanLimitNotice> : null}
          </div>
          {!list.data.items.length ? (
            <div className="p-5">
              <EmptyState
                title="Lista vazia"
                description="Busque pelo nome do produto, como “coca cola 2 litros”. Comparamos o mesmo item em todos os mercados."
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
            Veja o total em cada mercado, o preço de cada item e quanto dá para
            economizar dividindo a compra.
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
      <div className="flex items-center gap-3">
        <CatalogItemThumbnail imageUrl={item.imageUrl} size="sm" />
        <Link
          to={
            item.catalogItemId
              ? `/app/comparar/${item.catalogItemId}`
              : `/app/produtos/${item.productId}`
          }
          className="min-w-0 font-semibold hover:text-primary"
        >
          {item.productName}
        </Link>
        {item.checkedAt ? (
          <span className="ml-auto shrink-0 rounded-full bg-success-soft px-2 py-0.5 text-xs font-semibold text-success">
            No carrinho{item.checkedByName ? ` · ${item.checkedByName}` : ""}
          </span>
        ) : null}
      </div>
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
  const [shoppingType, setShoppingType] = useState<ShoppingType>(list.shoppingType);
  const rename = useMutation({
    mutationFn: () =>
      shoppingListApi.update(list.id, {
        name: name.trim(),
        shoppingType,
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
      <SelectField
        id="list-frequency"
        label="Frequência"
        hint="Semanal e mensal: o Início mostra quanto sai hoje e compara com a semana passada."
        value={shoppingType}
        onChange={(event) => setShoppingType(event.target.value as ShoppingType)}
      >
        <option value="CUSTOM">Personalizada</option>
        <option value="DAILY">Diária</option>
        <option value="WEEKLY">Semanal</option>
        <option value="MONTHLY">Mensal</option>
      </SelectField>
      <NativeButton
        type="submit"
        loading={rename.isPending}
        disabled={!name.trim()}
      >
        Salvar
      </NativeButton>
      <InlineError>{rename.error?.message}</InlineError>
    </form>
  );
}
