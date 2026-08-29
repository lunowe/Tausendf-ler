"use client";

import { useCallback, useEffect, useRef, useState } from "react";

export interface Polled<T> {
  data: T | null;
  error: string | null;
  /** True until the first response (success or failure) has arrived. */
  pending: boolean;
  refresh: () => void;
}

/**
 * Calls `load` immediately and then every `intervalMs` while `enabled`.
 * The next tick is scheduled only after the previous one settled, so a slow
 * coordinator cannot pile up requests. Keeps the last good data on errors.
 */
export function usePolling<T>(
  load: (signal: AbortSignal) => Promise<T>,
  intervalMs: number,
  enabled = true,
): Polled<T> {
  const [data, setData] = useState<T | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [pending, setPending] = useState(true);
  const [nonce, setNonce] = useState(0);

  // The caller passes a fresh closure on every render; keeping it in a ref lets the
  // timer loop below use the latest one without restarting on each render.
  const loadRef = useRef(load);
  useEffect(() => {
    loadRef.current = load;
  }, [load]);

  useEffect(() => {
    if (!enabled) return;

    let cancelled = false;
    const controller = new AbortController();
    let timer: ReturnType<typeof setTimeout> | undefined;

    const tick = async () => {
      try {
        const next = await loadRef.current(controller.signal);
        if (cancelled) return;
        setData(next);
        setError(null);
      } catch (e) {
        if (cancelled || controller.signal.aborted) return;
        setError(e instanceof Error ? e.message : String(e));
      } finally {
        if (!cancelled) {
          setPending(false);
          timer = setTimeout(tick, intervalMs);
        }
      }
    };

    void tick();

    return () => {
      cancelled = true;
      controller.abort();
      if (timer) clearTimeout(timer);
    };
  }, [intervalMs, enabled, nonce]);

  const refresh = useCallback(() => setNonce((n) => n + 1), []);

  return { data, error, pending, refresh };
}
