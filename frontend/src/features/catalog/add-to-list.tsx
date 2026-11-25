import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { Check, Plus } from "lucide-react";
import { useEffect, useState, type FormEvent } from "react";
import { Link, useLocation, useNavigate } from "react-router-dom";
import { Pagination } from "@/components/page/page-elements";
import {
  ErrorState,
  InlineError,
  LoadingState,
} from "@/components/ui/feedback";
import { SelectField, TextField } from "@/components/ui/form-field";
import { NativeButton } from "@/components/ui/native-button";
import { Sheet } from "@/components/ui/sheet";
import { useAuth } from "@/features/auth/auth-context";
import { shoppingListApi } from "@/services/gomo-api";
import type { BasicItem, CatalogItem, Product } from "@/types/api";
import { PlanLimitNotice } from "@/features/plan/plan-locks";
import { useLimitUpsell } from "@/features/plan/use-limit-upsell";
import { planLimitOf } from "@/features/plan/use-plan";
import { useVisitorOffer } from "@/features/plan/use-visitor-offer";
import {
  DEFAULT_LIST_NAME,
  LIST_ITEM_ADDED_EVENT,
  clearAddedListItem,
  listItemKey,
  readAddedListItem,
  savePendingListItem,
  type ListItemTarget,
} from "./pending-list-item";

interface AddToListProps {
  product?: Product;
  catalogItem?: Pick<CatalogItem, "id" | "name">;
  basicItem?: Pick<BasicItem, "slug" | "name">;
}

interface ItemToAdd {
  key: string;
  name: string;
  target: ListItemTarget;
}

/**
 * Adds a generic catalog item, or a specific product when the screen is about one store's listing. Lists belong
 * to an account: a visitor is told why before signing up, and the item is added when they come back.
 */
export function AddToList(props: AddToListProps) {
  const { user } = useAuth();
  const item = itemToAdd(props);

  if (!user) {return <VisitorAddToList item={item} />;}

  return <AccountAddToList item={item} />;
}

function itemToAdd({ product, catalogItem, basicItem }: AddToListProps): ItemToAdd {
  const target: ListItemTarget = basicItem
    ? { basicItem: basicItem.slug }
    : catalogItem
      ? { catalogItemId: catalogItem.id }
      : { productId: product?.id };
  const name = basicItem
    ? `${basicItem.name} (qualquer marca)`
    : catalogItem?.name ?? product?.name ?? "";

  return { key: listItemKey(target), name, target };
}

function AddButton({ onClick }: { onClick: () => void }) {
  return (
    <NativeButton
      variant="secondary"
      size="sm"
      onClick={onClick}
    >
      <Plus
        className="size-4"
        aria-hidden
      />
      Adicionar à lista
    </NativeButton>
  );
}

function VisitorAddToList({ item }: { item: ItemToAdd }) {
  const [open, setOpen] = useState(false);
  const navigate = useNavigate();
  const { pathname, search } = useLocation();
  const { signUpPath } = useVisitorOffer();
  const loginPath = `/entrar?depois=${encodeURIComponent(pathname + search)}`;

  const continueTo = (path: string) => {
    savePendingListItem({ name: item.name, target: item.target });
    navigate(path);
  };

  return (
    <>
      <AddButton onClick={() => setOpen(true)} />

      <Sheet
        open={open}
        onOpenChange={setOpen}
        title="Adicionar à lista"
        description={item.name}
      >
        <div className="space-y-5">
          <p className="text-base">
            Para guardar sua lista, crie uma conta grátis. Leva 1 minuto e não pede cartão.
          </p>

          <p className="text-sm text-muted">
            Depois de entrar, o produto vai para a sua lista sozinho.
          </p>

          <div className="grid gap-3">
            <NativeButton onClick={() => continueTo(signUpPath)}>
              Criar conta grátis
            </NativeButton>

            <NativeButton
              variant="secondary"
              onClick={() => continueTo(loginPath)}
            >
              Já tenho conta: entrar
            </NativeButton>

            <NativeButton
              variant="ghost"
              onClick={() => setOpen(false)}
            >
              Agora não
            </NativeButton>
          </div>
        </div>
      </Sheet>
    </>
  );
}

function AccountAddToList({ item }: { item: ItemToAdd }) {
  const [open, setOpen] = useState(false);
  const [addedAfterSignIn, setAddedAfterSignIn] = useState(() => readAddedListItem(item.key));

  useEffect(() => {
    const refresh = () => {
      const added = readAddedListItem(item.key);

      if (added) {setAddedAfterSignIn(added);}
    };

    window.addEventListener(LIST_ITEM_ADDED_EVENT, refresh);

    return () => window.removeEventListener(LIST_ITEM_ADDED_EVENT, refresh);
  }, [item.key]);

  useEffect(() => {
    if (addedAfterSignIn) {clearAddedListItem(item.key);}
  }, [addedAfterSignIn, item.key]);

  if (addedAfterSignIn) {
    return (
      <>
        <NativeButton
          variant="secondary"
          size="sm"
          to={`/app/listas/${addedAfterSignIn.listId}`}
        >
          <Check
            className="size-4 text-success"
            aria-hidden
          />
          Na sua lista
        </NativeButton>
        <span
          role="status"
          className="sr-only"
        >
          {addedAfterSignIn.name} foi adicionado à sua lista.
        </span>
      </>
    );
  }

  return (
    <>
      <AddButton onClick={() => setOpen(true)} />

      <Sheet
        open={open}
        onOpenChange={setOpen}
        title="Adicionar à lista"
        description={item.name}
      >
        {open ? <AddToListForm item={item} onLimit={() => setOpen(false)} /> : null}
      </Sheet>
    </>
  );
}

function AddToListForm({ item, onLimit }: { item: ItemToAdd; onLimit: () => void }) {
  const [page, setPage] = useState(0);
  const [listId, setListId] = useState("");
  const [listName, setListName] = useState(DEFAULT_LIST_NAME);
  const [quantity, setQuantity] = useState(1);
  const [addedTo, setAddedTo] = useState<string | null>(null);
  const [createdListId, setCreatedListId] = useState<string | null>(null);
  const client = useQueryClient();
  const itemLimit = useLimitUpsell();
  const lists = useQuery({
    queryKey: ["shopping-lists", "picker", page],
    queryFn: () => shoppingListApi.list(page, 20),
  });
  const content = lists.data?.content ?? [];
  const chosenListId = listId || (content.length === 1 ? content[0].id : "");
  const validQuantity = Number.isInteger(quantity) && quantity >= 1;

  const createList = async () => {
    const created = await shoppingListApi.create({
      name: listName.trim() || DEFAULT_LIST_NAME,
      shoppingType: "CUSTOM",
    });
    setCreatedListId(created.id);

    return created.id;
  };

  const add = useMutation({
    mutationFn: async () => {
      const targetListId = content.length
        ? chosenListId
        : createdListId ?? (await createList());

      await shoppingListApi.addItem(
        targetListId,
        { ...item.target, quantity },
      );

      return targetListId;
    },
    onMutate: () => itemLimit.clear(),
    onError: (error) => {
      if (planLimitOf(error) !== "LIST_ITEMS") {return;}

      if (itemLimit.block("LIST_ITEMS")) {onLimit();}
    },
    onSuccess: async (targetListId) => {
      setAddedTo(targetListId);

      await Promise.all([
        client.invalidateQueries({ queryKey: ["shopping-list", targetListId] }),
        client.invalidateQueries({ queryKey: ["shopping-lists"] }),
        client.invalidateQueries({ queryKey: ["shopping-list-comparison", targetListId] }),
        client.invalidateQueries({ queryKey: ["shopping-list-recommendation", targetListId] }),
      ]);
    },
  });

  const submit = (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();

    if (!validQuantity) {return;}

    if (content.length && !chosenListId) {return;}

    add.mutate();
  };

  if (lists.isPending) {return <LoadingState label="Carregando suas listas…" />;}

  if (lists.isError) {return <ErrorState retry={() => void lists.refetch()} />;}

  const hasLists = content.length > 0;

  return (
    <form
      className="space-y-5"
      onSubmit={submit}
    >
      {hasLists ? (
        <>
          <SelectField
            id={`add-list-${item.key}`}
            label="Sua lista"
            value={chosenListId}
            onChange={(event) => {
              setListId(event.target.value);
              add.reset();
              setAddedTo(null);
            }}
            required
          >
            <option value="">Selecione a lista</option>
            {content.map((list) => (
              <option
                key={list.id}
                value={list.id}
              >
                {list.name}
              </option>
            ))}
          </SelectField>

          {lists.data.totalPages > 1 ? (
            <Pagination
              page={page}
              totalPages={lists.data.totalPages}
              onChange={(next) => {
                setPage(next);
                setListId("");
              }}
            />
          ) : null}
        </>
      ) : (
        <>
          <p className="text-base">Você ainda não tem lista. Vamos criar a primeira?</p>

          <TextField
            id={`new-list-${item.key}`}
            label="Nome da lista"
            value={listName}
            onChange={(event) => setListName(event.target.value)}
            disabled={Boolean(addedTo)}
          />
        </>
      )}

      <TextField
        id={`add-quantity-${item.key}`}
        label="Quantidade"
        type="number"
        inputMode="numeric"
        min={1}
        step={1}
        value={quantity}
        onChange={(event) => {
          setQuantity(Number(event.target.value));
          add.reset();
          setAddedTo(null);
        }}
        required
      />

      <InlineError>{planLimitOf(add.error) ? null : add.error?.message}</InlineError>

      {itemLimit.notice ? <PlanLimitNotice>{itemLimit.notice}</PlanLimitNotice> : null}

      {addedTo ? (
        <p
          role="status"
          className="text-sm text-success"
        >
          Produto adicionado.{" "}
          <Link
            className="font-semibold underline"
            to={`/app/listas/${addedTo}`}
          >
            Abrir lista
          </Link>
        </p>
      ) : null}

      <NativeButton
        type="submit"
        loading={add.isPending}
        disabled={(hasLists && !chosenListId) || !validQuantity || Boolean(addedTo)}
      >
        {hasLists ? "Adicionar à lista" : "Criar lista e adicionar"}
      </NativeButton>
    </form>
  );
}
