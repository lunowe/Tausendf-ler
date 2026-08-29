/**
 * One flag for the whole app: did the coordinator reject our last request with 401?
 * Kept free of React so `api.ts` can be imported from server components too;
 * `AuthBanner` subscribes via `useSyncExternalStore`.
 */

let unauthorized = false;
const listeners = new Set<() => void>();

export function setUnauthorized(next: boolean) {
  if (unauthorized === next) return;
  unauthorized = next;
  listeners.forEach((listener) => listener());
}

export function isUnauthorized(): boolean {
  return unauthorized;
}

export function subscribeUnauthorized(listener: () => void) {
  listeners.add(listener);
  return () => {
    listeners.delete(listener);
  };
}
