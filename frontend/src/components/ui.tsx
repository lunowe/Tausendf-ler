import type { ReactNode } from "react";
import type { JobStatus } from "@/lib/api";

const STATUS_LABEL: Record<JobStatus, string> = {
  PENDING: "wartet",
  RUNNING: "läuft",
  PAUSED: "pausiert",
  COMPLETED: "fertig",
  ABORTED: "abgebrochen",
  FAILED: "fehlgeschlagen",
};

export function statusColor(status: JobStatus): string {
  return `var(--st-${status})`;
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
      className={`mono inline-flex shrink-0 items-center gap-2 border uppercase tracking-[0.18em] ${
        size === "lg" ? "px-3 py-1.5 text-[0.6875rem]" : "px-2 py-1 text-[0.5625rem]"
      }`}
      style={{ color, borderColor: color, background: "rgba(0,0,0,0.25)" }}
    >
      <span
        className={`inline-block h-1.5 w-1.5 rounded-full ${status === "RUNNING" ? "pulse" : ""}`}
        style={{ background: color, color }}
      />
      {STATUS_LABEL[status]}
    </span>
  );
}

/** Numbered small-caps section heading, like a callout on a technical drawing. */
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
    <div className="mb-4 flex items-baseline gap-3">
      <span className="mono text-[0.625rem] text-[var(--amber)]">{index}</span>
      <span className="label text-[var(--ink-dim)]">{children}</span>
      <span className="h-px flex-1 translate-y-[-3px] bg-[var(--line)]" />
      {right}
    </div>
  );
}

export function Panel({
  children,
  className = "",
}: {
  children: ReactNode;
  className?: string;
}) {
  return <div className={`panel ${className}`}>{children}</div>;
}

export function ErrorNote({ children }: { children: ReactNode }) {
  return (
    <p
      className="mono flex items-start gap-2 border border-[var(--coral)] bg-[rgba(255,122,99,0.08)] px-3 py-2 text-[0.75rem] text-[var(--coral)]"
      role="alert"
    >
      <span aria-hidden="true">!</span>
      <span>{children}</span>
    </p>
  );
}

export function Empty({ children }: { children: ReactNode }) {
  return (
    <div className="hatch flex items-center justify-center border border-dashed border-[var(--line)] px-6 py-14">
      <p className="mono text-[0.75rem] text-[var(--ink-faint)]">{children}</p>
    </div>
  );
}

/** Label/value row used for the job metadata grid. */
export function Metric({
  label,
  value,
  accent,
}: {
  label: string;
  value: ReactNode;
  accent?: boolean;
}) {
  return (
    <div className="border-l border-[var(--line)] pl-3">
      <p className="label mb-1.5">{label}</p>
      <p
        className="mono text-[0.9375rem]"
        style={{ color: accent ? "var(--amber)" : "var(--ink)" }}
      >
        {value}
      </p>
    </div>
  );
}
