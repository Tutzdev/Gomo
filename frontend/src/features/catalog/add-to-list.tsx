import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { Plus } from "lucide-react";
import { useState } from "react";
import { Link } from "react-router-dom";
import { Pagination } from "@/components/page/page-elements";
import {
  ErrorState,
  InlineError,
  LoadingState,
} from "@/components/ui/feedback";
import { SelectField, TextField } from "@/components/ui/form-field";
import { NativeButton } from "@/components/ui/native-button";
import { Sheet } from "@/components/ui/sheet";
import { shoppingListApi } from "@/services/gomo-api";
import type { CatalogItem, Product } from "@/types/api";

/** Adds a generic catalog item, or a specific product when the screen is about one store's listing. */
export function AddToList({
  product,
  catalogItem,
}: {
  product?: Product;
  catalogItem?: CatalogItem;
}) {
  const target = catalogItem
    ? { id: catalogItem.id, name: catalogItem.name }
    : { id: product?.id ?? "", name: product?.name ?? "" };
  const [open, setOpen] = useState(false);
  const [page, setPage] = useState(0);
  const [listId, setListId] = useState("");
  const [quantity, setQuantity] = useState(1);
  const client = useQueryClient();
  const lists = useQuery({
    queryKey: ["shopping-lists", "picker", page],
    queryFn: () => shoppingListApi.list(page, 20),
    enabled: open,
  });
  const add = useMutation({
    mutationFn: () =>
      shoppingListApi.addItem(
        listId,
        catalogItem
          ? { catalogItemId: catalogItem.id, quantity }
          : { productId: product?.id, quantity },
      ),
    onSuccess: async () => {
      await Promise.all([
        client.invalidateQueries({ queryKey: ["shopping-list", listId] }),
        client.invalidateQueries({ queryKey: ["shopping-lists"] }),
        client.invalidateQueries({
          queryKey: ["shopping-list-comparison", listId],
        }),
        client.invalidateQueries({
          queryKey: ["shopping-list-recommendation", listId],
        }),
      ]);
    },
  });

  return (
    <>
      <NativeButton
        variant="secondary"
        size="sm"
        onClick={() => {
          add.reset();
          setOpen(true);
        }}
      >
        <Plus className="size-4" aria-hidden />À lista
      </NativeButton>
      <Sheet
        open={open}
        onOpenChange={setOpen}
        title="Adicionar à lista"
        description={target.name}
      >
        {lists.isPending ? (
          <LoadingState label="Carregando suas listas…" />
        ) : lists.isError ? (
          <ErrorState retry={() => void lists.refetch()} />
        ) : !lists.data.content.length ? (
          <p className="text-sm">
            Você ainda não tem listas.{" "}
            <Link
              className="font-semibold text-primary underline"
              to="/app/listas"
            >
              Criar uma lista
            </Link>
          </p>
        ) : (
          <form
            className="space-y-5"
            onSubmit={(event) => {
              event.preventDefault();
              if (listId && Number.isInteger(quantity) && quantity >= 1)
                add.mutate();
            }}
          >
            <SelectField
              id={`add-list-${target.id}`}
              label="Sua lista"
              value={listId}
              onChange={(event) => {
                setListId(event.target.value);
                add.reset();
              }}
              required
            >
              <option value="">Selecione a lista</option>
              {lists.data.content.map((list) => (
                <option key={list.id} value={list.id}>
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
            <TextField
              id={`add-quantity-${target.id}`}
              label="Quantidade"
              type="number"
              min={1}
              step={1}
              value={quantity}
              onChange={(event) => {
                setQuantity(Number(event.target.value));
                add.reset();
              }}
              required
            />
            <InlineError>{add.error?.message}</InlineError>
            {add.isSuccess ? (
              <p role="status" className="text-sm text-success">
                Produto adicionado.{" "}
                <Link
                  className="font-semibold underline"
                  to={`/app/listas/${listId}`}
                >
                  Abrir lista
                </Link>
              </p>
            ) : null}
            <NativeButton
              type="submit"
              loading={add.isPending}
              disabled={
                !listId ||
                !Number.isInteger(quantity) ||
                quantity < 1 ||
                add.isSuccess
              }
            >
              Adicionar produto
            </NativeButton>
          </form>
        )}
      </Sheet>
    </>
  );
}
