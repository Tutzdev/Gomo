import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import { enqueue, flushQueue, listQueue, type TripOperation } from "./trip-queue";

export type SyncState = "idle" | "sending" | "waiting";

/**
 * The queue of the "No mercado" mode as React state: `add` stores an operation and tries to send it right away;
 * what does not get through is sent again when the browser says it is back online, every 30 seconds while
 * something waits, and the next time the page opens.
 */
export function useTripQueue(listId: string, send: (operation: TripOperation) => Promise<unknown>) {
  const store = useMemo(() => listQueue(listId), [listId]);
  const [queue, setQueue] = useState<TripOperation[]>(() => store.load());
  const [state, setState] = useState<SyncState>("idle");
  const [dropped, setDropped] = useState<TripOperation[]>([]);
  const flushing = useRef(false);
  const sendRef = useRef(send);

  useEffect(() => {
    sendRef.current = send;
  }, [send]);

  const flush = useCallback(async () => {
    if (flushing.current) return;
    flushing.current = true;
    setState("sending");
    try {
      const outcome = await flushQueue(store, (operation) => sendRef.current(operation));
      if (outcome.dropped.length) setDropped((all) => [...all, ...outcome.dropped]);
      setState(outcome.waiting ? "waiting" : "idle");
    } finally {
      flushing.current = false;
      setQueue(store.load());
    }
  }, [store]);

  const add = useCallback(
    (operation: TripOperation) => {
      const next = enqueue(store.load(), operation, flushing.current ? 1 : 0);
      store.save(next);
      setQueue(next);
      void flush();
    },
    [store, flush],
  );

  useEffect(() => {
    void flush();
    const retry = () => void flush();
    window.addEventListener("online", retry);
    const timer = window.setInterval(() => {
      if (store.load().length) void flush();
    }, 30_000);
    return () => {
      window.removeEventListener("online", retry);
      window.clearInterval(timer);
    };
  }, [flush, store]);

  return { queue, state, dropped, add };
}
