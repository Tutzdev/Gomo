import type { ShoppingList, ShoppingListItem, ShoppingListSummary } from "@/types/api";

export const DEFAULT_LIST_NAME = "Minha lista";

export const LIST_ITEM_ADDED_EVENT = "gomo:list-item-added";

const PENDING_KEY = "gomo:pending-list-item";
const ADDED_KEY = "gomo:added-list-item";
const PENDING_LIFETIME_MS = 30 * 60 * 1000;

export interface ListItemTarget {
  productId?: string;
  catalogItemId?: string;
  basicItem?: string;
}

export interface PendingListItem {
  key: string;
  name: string;
  target: ListItemTarget;
  savedAt: number;
}

export interface AddedListItem {
  key: string;
  name: string;
  listId: string;
}

export interface PendingListApi {
  list: (page: number, size: number) => Promise<{ content: ShoppingListSummary[] }>;
  create: (input: { name: string; shoppingType: "CUSTOM" }) => Promise<Pick<ShoppingList, "id">>;
  addItem: (listId: string, input: ListItemTarget & { quantity: number }) => Promise<ShoppingListItem>;
}

type KeyValueStorage = Pick<Storage, "getItem" | "setItem" | "removeItem">;

export function listItemKey(target: ListItemTarget) {
  if (target.basicItem) {return `basic:${target.basicItem}`;}

  if (target.catalogItemId) {return `catalog:${target.catalogItemId}`;}

  return `product:${target.productId ?? ""}`;
}

export function savePendingListItem(
  item: { name: string; target: ListItemTarget },
  storage: KeyValueStorage = window.sessionStorage,
  now = Date.now(),
) {
  const pending: PendingListItem = {
    key: listItemKey(item.target),
    name: item.name,
    target: item.target,
    savedAt: now,
  };

  storage.setItem(PENDING_KEY, JSON.stringify(pending));
}

export function takePendingListItem(
  storage: KeyValueStorage = window.sessionStorage,
  now = Date.now(),
): PendingListItem | null {
  const pending = readJson<PendingListItem>(storage, PENDING_KEY);
  storage.removeItem(PENDING_KEY);

  if (!pending?.key || !pending.target) {return null;}

  if (now - pending.savedAt > PENDING_LIFETIME_MS) {return null;}

  return pending;
}

export function readAddedListItem(
  key: string,
  storage: KeyValueStorage = window.sessionStorage,
): AddedListItem | null {
  const added = readJson<AddedListItem>(storage, ADDED_KEY);

  return added?.key === key ? added : null;
}

export function clearAddedListItem(
  key: string,
  storage: KeyValueStorage = window.sessionStorage,
) {
  if (readAddedListItem(key, storage)) {storage.removeItem(ADDED_KEY);}
}

export async function addToFirstList(
  api: PendingListApi,
  target: ListItemTarget,
  quantity = 1,
) {
  const lists = await api.list(0, 20);
  const own = lists.content.find((list) => list.owned) ?? lists.content[0];
  const listId = own?.id ?? (await api.create({ name: DEFAULT_LIST_NAME, shoppingType: "CUSTOM" })).id;

  await api.addItem(
    listId,
    { ...target, quantity },
  );

  return listId;
}

export async function addPendingListItem(
  api: PendingListApi,
  storage: KeyValueStorage = window.sessionStorage,
  now = Date.now(),
): Promise<AddedListItem | null> {
  const pending = takePendingListItem(storage, now);

  if (!pending) {return null;}

  const listId = await addToFirstList(api, pending.target);
  const added: AddedListItem = { key: pending.key, name: pending.name, listId };
  storage.setItem(ADDED_KEY, JSON.stringify(added));

  return added;
}

function readJson<T>(storage: KeyValueStorage, key: string): T | null {
  try {
    const raw = storage.getItem(key);

    return raw ? (JSON.parse(raw) as T) : null;
  } catch {
    return null;
  }
}
