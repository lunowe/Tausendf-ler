"use client";

import { useSyncExternalStore } from "react";
import { API_KEY, UNAUTHORIZED_MESSAGE } from "@/lib/api";
import { isUnauthorized, subscribeUnauthorized } from "@/lib/authState";

/**
 * Shown under the header as soon as the coordinator answers 401. Disappears with the
 * next accepted request (e.g. after the key was fixed and the page reloaded).
 */
export function AuthBanner() {
  const unauthorized = useSyncExternalStore(
    subscribeUnauthorized,
    isUnauthorized,
    () => false,
  );
  if (!unauthorized) return null;

  return (
    <div
      role="alert"
      className="border-b border-[rgba(176,47,44,0.35)] bg-[var(--granat-tint)]"
    >
      <div className="mx-auto flex w-full max-w-[1160px] flex-wrap items-baseline gap-x-4 gap-y-1 px-4 py-2.5 sm:px-6 md:px-10">
        <span className="label shrink-0 text-[var(--granat)]">401 · Nicht autorisiert</span>
        <p className="mono min-w-0 flex-1 text-[0.75rem] leading-relaxed text-[var(--ink)]">
          {UNAUTHORIZED_MESSAGE}
          {API_KEY
            ? " – der gesetzte Key wird vom Koordinator abgelehnt (API_KEY dort prüfen)."
            : " – in frontend/.env.local eintragen und den Dev-Server neu starten."}
        </p>
      </div>
    </div>
  );
}
