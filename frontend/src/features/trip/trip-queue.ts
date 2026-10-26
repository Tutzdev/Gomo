import { ApiError } from "@/lib/api";

/**
 * What the "No mercado" mode still has to tell the server. Inside a market the signal comes and goes, so every
 * change goes first into this queue (kept in localStorage) and is sent in order; whatever does not get through
 * waits for the next try. The server side of each operation is idempotent: a check sent twice, or the same
 * purchase id sent twice, changes nothing.
 */
export type TripOperation =
  | { kind: "check"; itemId: string }
  | { kind: "uncheck"; itemId: string }
  | {
      kind: "contribution";
      productId: string;
      storeId: string;
      regularPrice: number;
      observedAt: string;
    }
  | {
      kind: "purchase";
      purchaseId: string;
      storeId: string;
      items: Array<{ itemId: string; shelfPrice?: number }>;
    };

export interface QueueStorage {
  getItem(key: string): string | null;
  setItem(key: string, value: string): void;
}

/** The queue of one list, read and written as a whole. */
export interface QueueStore {
  load(): TripOperation[];
  save(queue: TripOperation[]): void;
}

export type FlushOutcome = { sent: number; dropped: TripOperation[]; waiting: number };

export function listQueue(listId: string, storage: QueueStorage = safeStorage()): QueueStore {
  const key = `gomo:trip-queue:${listId}`;
  return {
    load() {
      try {
        const stored = JSON.parse(storage.getItem(key) ?? "[]") as unknown;
        return Array.isArray(stored) ? (stored as TripOperation[]) : [];
      } catch {
        return [];
      }
    },
    save(queue) {
      try {
        storage.setItem(key, JSON.stringify(queue));
      } catch {
        // Private windows and blocked storage: the queue lasts while the page is open.
      }
    },
  };
}

/**
 * Adds an operation. A check followed by an uncheck of the same item (or the other way round) cancels out
 * instead of sending both. The first `inFlight` operations are being sent right now and are never touched.
 */
export function enqueue(queue: TripOperation[], operation: TripOperation, inFlight = 0): TripOperation[] {
  if (operation.kind === "check" || operation.kind === "uncheck") {
    const last = lastCheckOf(queue, operation.itemId);
    if (last >= inFlight && queue[last].kind === operation.kind) return queue;
    if (last >= inFlight) return [...queue.slice(0, last), ...queue.slice(last + 1)];
  }
  return [...queue, operation];
}

/** No answer or a server error: worth trying again later. A 4xx will not change by resending. */
export function isRetryable(error: unknown) {
  return !(error instanceof ApiError) || error.status === 0 || error.status >= 500;
}

/**
 * Sends the queue in order, one operation at a time, and stops at the first one that may succeed later, so a
 * purchase never reaches the server before the checks made before it. Operations the server refused are
 * dropped and returned. Whatever is added while it runs is sent too.
 */
export async function flushQueue(
  store: QueueStore,
  send: (operation: TripOperation) => Promise<unknown>,
): Promise<FlushOutcome> {
  const dropped: TripOperation[] = [];
  let sent = 0;
  for (;;) {
    const [head] = store.load();
    if (!head) return { sent, dropped, waiting: 0 };
    try {
      await send(head);
      sent++;
    } catch (error) {
      if (isRetryable(error)) return { sent, dropped, waiting: store.load().length };
      dropped.push(head);
    }
    // The head stays first while it is being sent: enqueue never touches an operation in flight.
    store.save(store.load().slice(1));
  }
}

/** The check state of an item once the operations still in the queue reach the server. */
export function pendingCheck(queue: TripOperation[], itemId: string): boolean | null {
  const last = lastCheckOf(queue, itemId);
  return last < 0 ? null : queue[last].kind === "check";
}

function lastCheckOf(queue: TripOperation[], itemId: string) {
  for (let index = queue.length - 1; index >= 0; index--) {
    const operation = queue[index];
    if ((operation.kind === "check" || operation.kind === "uncheck") && operation.itemId === itemId) return index;
  }
  return -1;
}

function safeStorage(): QueueStorage {
  try {
    return window.localStorage;
  } catch {
    const memory = new Map<string, string>();
    return { getItem: (name) => memory.get(name) ?? null, setItem: (name, value) => void memory.set(name, value) };
  }
}
