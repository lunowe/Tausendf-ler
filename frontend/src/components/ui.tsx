import type { ReactNode } from "react";
import type { JobStatus } from "@/lib/api";

/** Wortlaut wie im Telegram-Bot (StatusCommandHandler), nur ohne Emoji. */
const STATUS_LABEL: Record<JobStatus, string> = {
  PENDING: "Wartend",
  RUNNING: "Läuft",
  PAUSED: "Pausiert",
  COMPLETED: "Abgeschlossen",
  ABORTED: "Abgebrochen",
  FAILED: "Fehlgeschlagen",
};

export function statusLabel(status: JobStatus): string {
  return STATUS_LABEL[status];
}

export function statusColor(status: JobStatus): string {
  return `var(--st-${status})`;
}

/** Zustandspunkt; pulsiert nur, solange wirklich etwas passiert. */
export function Dot({
  color,
  live = false,
  className = "",
}: {
  color: string;
  live?: boolean;
  className?: string;
}) {
  return (
    <span
      aria-hidden="true"
      className={`inline-block h-[7px] w-[7px] shrink-0 rounded-full ${live ? "pulse" : ""} ${className}`}
      style={{ background: color }}
    />
  );
}

export function StatusBadge({
  status,
  size = "sm",
}: {
  status: JobStatus;
  size?: "sm" | "lg";
}) {
  const color = statusColor(status);
  return (
    <span
      className={`mono inline-flex shrink-0 items-center gap-2 border font-medium uppercase tracking-[0.1em] ${
        size === "lg" ? "px-2.5 py-1.5 text-[0.6875rem]" : "px-2 py-1 text-[0.625rem]"
      }`}
      style={{
        color,
        borderColor: `color-mix(in srgb, ${color} 38%, #fff)`,
        background: `color-mix(in srgb, ${color} 8%, #fff)`,
      }}
    >
      <Dot color={color} live={status === "RUNNING"} />
      {STATUS_LABEL[status]}
    </span>
  );
}

/**
 * Nummerierte Abschnittsmarke mit durchlaufender Haarlinie – wie die
 * Gliederung eines gedruckten Berichts.
 */
export function SectionLabel({
  index,
  children,
  right,
}: {
  index: string;
  children: ReactNode;
  right?: ReactNode;
}) {
  return (
    <div className="mb-4 flex flex-wrap items-baseline gap-x-3 gap-y-1">
      <span className="mono text-[0.6875rem] font-medium text-[var(--granat)]">
        {index}
      </span>
      <h2 className="label text-[var(--ink-2)]">{children}</h2>
      <span
        className="hidden h-px flex-1 -translate-y-[3px] bg-[var(--rule)] sm:block"
        aria-hidden="true"
      />
      {right}
    </div>
  );
}

export function Card({
  children,
  className = "",
}: {
  children: ReactNode;
  className?: string;
}) {
  return <div className={`card ${className}`}>{children}</div>;
}

export function ErrorNote({
  children,
  onRetry,
}: {
  children: ReactNode;
  onRetry?: () => void;
}) {
  return (
    <div
      role="alert"
      className="flex flex-wrap items-center gap-x-4 gap-y-2 border border-[rgba(176,47,44,0.35)] bg-[var(--granat-tint)] px-4 py-3"
    >
      <span className="label shrink-0 text-[var(--granat)]">Fehler</span>
      <p className="mono min-w-0 flex-1 text-[0.75rem] leading-relaxed text-[var(--ink)]">
        {children}
      </p>
      {onRetry && (
        <button type="button" className="btn btn-danger shrink-0" onClick={onRetry}>
          Erneut versuchen
        </button>
      )}
    </div>
  );
}

/** Leerer Zustand: sagt, was fehlt, und was als Nächstes zu tun ist. */
export function Empty({
  children,
  hint,
}: {
  children: ReactNode;
  hint?: ReactNode;
}) {
  return (
    <div className="hatch border border-dashed border-[var(--rule)] px-6 py-12 text-center">
      <p className="text-[0.875rem] text-[var(--ink-2)]">{children}</p>
      {hint && (
        <p className="mono mx-auto mt-2 max-w-[46ch] text-[0.6875rem] leading-relaxed text-[var(--ink-3)]">
          {hint}
        </p>
      )}
    </div>
  );
}

export function Skeleton({ className = "" }: { className?: string }) {
  return <span aria-hidden="true" className={`skeleton block ${className}`} />;
}

/** Beschriftetes Messfeld für die Kennzahlen-Raster. */
export function Metric({
  label,
  value,
  tone,
}: {
  label: string;
  value: ReactNode;
  tone?: "accent" | "warn";
}) {
  const color =
    tone === "accent"
      ? "var(--aqua-deep)"
      : tone === "warn"
        ? "var(--granat)"
        : "var(--ink)";
  return (
    <div className="border-l border-[var(--rule)] pl-3">
      <p className="label mb-1.5">{label}</p>
      <p className="mono text-[0.9375rem] font-medium" style={{ color }}>
        {value}
      </p>
    </div>
  );
}
