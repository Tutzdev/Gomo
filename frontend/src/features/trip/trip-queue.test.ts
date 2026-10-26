import { describe, expect, it } from "vitest";
import { ApiError } from "@/lib/api";
import { enqueue, flushQueue, listQueue, pendingCheck, type TripOperation } from "./trip-queue";

const check = (itemId: string): TripOperation => ({ kind: "check", itemId });
const uncheck = (itemId: string): TripOperation => ({ kind: "uncheck", itemId });
const purchase: TripOperation = { kind: "purchase", purchaseId: "p1", storeId: "s1", items: [{ itemId: "rice" }] };

function memoryQueue(initial: TripOperation[] = []) {
  const memory = new Map<string, string>();
  const store = listQueue("list", {
    getItem: (key) => memory.get(key) ?? null,
    setItem: (key, value) => void memory.set(key, value),
  });
  store.save(initial);
  return { store, memory };
}

describe("enqueue", () => {
  it("a check undone before it was sent cancels out", () => {
    expect(enqueue([check("rice")], uncheck("rice"))).toEqual([]);
  });

  it("checking twice keeps one operation", () => {
    expect(enqueue([check("rice")], check("rice"))).toEqual([check("rice")]);
  });

  it("never cancels the operation being sent: the undo goes after it", () => {
    expect(enqueue([check("rice")], uncheck("rice"), 1)).toEqual([check("rice"), uncheck("rice")]);
  });

  it("keeps the order of different items and of the purchase", () => {
    expect(enqueue(enqueue([check("rice")], check("beans")), purchase)).toEqual([check("rice"), check("beans"), purchase]);
  });
});

describe("flushQueue", () => {
  it("stops at a network failure so the purchase never goes before the checks", async () => {
    const { store } = memoryQueue([check("rice"), check("beans"), purchase]);
    const sent: TripOperation[] = [];

    const outcome = await flushQueue(store, async (operation) => {
      if (operation.kind === "check" && operation.itemId === "beans") throw new ApiError(0, {});
      sent.push(operation);
    });

    expect(sent).toEqual([check("rice")]);
    expect(store.load()).toEqual([check("beans"), purchase]);
    expect(outcome).toEqual({ sent: 1, dropped: [], waiting: 2 });
  });

  it("drops what the server refused and keeps going", async () => {
    const { store } = memoryQueue([check("gone"), purchase]);

    const outcome = await flushQueue(store, async (operation) => {
      if (operation.kind === "check") throw new ApiError(404, {});
    });

    expect(store.load()).toEqual([]);
    expect(outcome.dropped).toEqual([check("gone")]);
    expect(outcome.sent).toBe(1);
  });

  it("a server error is retried later", async () => {
    const { store } = memoryQueue([purchase]);

    await flushQueue(store, async () => {
      throw new ApiError(502, {});
    });

    expect(store.load()).toEqual([purchase]);
  });

  it("sends what was added while it was running", async () => {
    const { store } = memoryQueue([check("rice")]);
    const sent: TripOperation[] = [];

    await flushQueue(store, async (operation) => {
      if (sent.length === 0) store.save(enqueue(store.load(), purchase, 1));
      sent.push(operation);
    });

    expect(sent).toEqual([check("rice"), purchase]);
    expect(store.load()).toEqual([]);
  });
});

describe("pendingCheck", () => {
  it("is the last operation of the item, or null when none is waiting", () => {
    expect(pendingCheck([check("rice"), uncheck("beans")], "rice")).toBe(true);
    expect(pendingCheck([check("rice"), uncheck("beans")], "beans")).toBe(false);
    expect(pendingCheck([purchase], "rice")).toBeNull();
  });
});

describe("listQueue", () => {
  it("survives a reload and ignores what is not a queue", () => {
    const { store, memory } = memoryQueue([check("rice")]);
    expect(store.load()).toEqual([check("rice")]);

    memory.set("gomo:trip-queue:list", "not json");
    expect(store.load()).toEqual([]);
  });
});
