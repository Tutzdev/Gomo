import { describe, expect, it } from "vitest";
import type { ShoppingListItem, ShoppingListSummary } from "@/types/api";
import {
  addPendingListItem,
  addToFirstList,
  listItemKey,
  savePendingListItem,
  clearAddedListItem,
  readAddedListItem,
  takePendingListItem,
  type ListItemTarget,
  type PendingListApi,
} from "./pending-list-item";

const NOW = 1_000_000;

function memoryStorage() {
  const memory = new Map<string, string>();

  return {
    getItem: (key: string) => memory.get(key) ?? null,
    setItem: (key: string, value: string) => void memory.set(key, value),
    removeItem: (key: string) => void memory.delete(key),
  };
}

function summary(id: string, owned: boolean) {
  return { id, owned } as ShoppingListSummary;
}

function fakeApi(lists: ShoppingListSummary[]) {
  const added: { listId: string; input: ListItemTarget & { quantity: number } }[] = [];
  const created: string[] = [];
  const api: PendingListApi = {
    list: async () => ({ content: lists }),
    create: async (input) => {
      created.push(input.name);

      return { id: "new-list" };
    },
    addItem: async (listId, input) => {
      added.push({ listId, input });

      return {} as ShoppingListItem;
    },
  };

  return { api, added, created };
}

describe("listItemKey", () => {
  it("tells a basic item, a catalog item and a store product apart", () => {
    expect(listItemKey({ basicItem: "arroz-branco" })).toBe("basic:arroz-branco");
    expect(listItemKey({ catalogItemId: "c1" })).toBe("catalog:c1");
    expect(listItemKey({ productId: "p1" })).toBe("product:p1");
  });
});

describe("pending list item", () => {
  it("is taken once", () => {
    const storage = memoryStorage();
    savePendingListItem({ name: "Coca-Cola 2 L", target: { catalogItemId: "c1" } }, storage, NOW);

    expect(takePendingListItem(storage, NOW)?.name).toBe("Coca-Cola 2 L");
    expect(takePendingListItem(storage, NOW)).toBeNull();
  });

  it("expires when the sign up took more than half an hour", () => {
    const storage = memoryStorage();
    savePendingListItem({ name: "Café", target: { catalogItemId: "c2" } }, storage, NOW);

    expect(takePendingListItem(storage, NOW + 31 * 60 * 1000)).toBeNull();
  });

  it("ignores a broken value", () => {
    const storage = memoryStorage();
    storage.setItem("gomo:pending-list-item", "{not json");

    expect(takePendingListItem(storage, NOW)).toBeNull();
  });
});

describe("addToFirstList", () => {
  it("uses the person's own list before a shared one", async () => {
    const { api, added, created } = fakeApi([summary("shared", false), summary("mine", true)]);

    await addToFirstList(api, { catalogItemId: "c1" });

    expect(created).toEqual([]);
    expect(added).toEqual([{ listId: "mine", input: { catalogItemId: "c1", quantity: 1 } }]);
  });

  it("creates \"Minha lista\" when there is none", async () => {
    const { api, added, created } = fakeApi([]);

    const listId = await addToFirstList(api, { basicItem: "arroz-branco" }, 2);

    expect(listId).toBe("new-list");
    expect(created).toEqual(["Minha lista"]);
    expect(added[0].input).toEqual({ basicItem: "arroz-branco", quantity: 2 });
  });
});

describe("addPendingListItem", () => {
  it("adds the item saved before the sign up and leaves a note for its button", async () => {
    const storage = memoryStorage();
    const { api, added } = fakeApi([summary("mine", true)]);
    savePendingListItem({ name: "Café", target: { catalogItemId: "c2" } }, storage, NOW);

    const result = await addPendingListItem(api, storage, NOW);

    expect(result).toEqual({ key: "catalog:c2", name: "Café", listId: "mine" });
    expect(added).toHaveLength(1);
    expect(readAddedListItem("catalog:c1", storage)).toBeNull();
    expect(readAddedListItem("catalog:c2", storage)?.listId).toBe("mine");

    clearAddedListItem("catalog:c1", storage);
    expect(readAddedListItem("catalog:c2", storage)).not.toBeNull();

    clearAddedListItem("catalog:c2", storage);
    expect(readAddedListItem("catalog:c2", storage)).toBeNull();
  });

  it("does nothing without a saved item", async () => {
    const { api, added } = fakeApi([summary("mine", true)]);

    expect(await addPendingListItem(api, memoryStorage(), NOW)).toBeNull();
    expect(added).toEqual([]);
  });
});
