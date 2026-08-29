"use client";

import { api, type WorkerInfo } from "@/lib/api";
import { usePolling } from "@/lib/usePolling";
import { ago, clock, num, padded } from "@/lib/format";
import { Dot, Empty, ErrorNote, SectionLabel, Skeleton } from "@/components/ui";

const REFRESH_MS = 2000;

/**
 * Der Schwarm: jede verbundene Maschine als eigene Kachel. Das ist das Demo-
 * Herzstück – Laptops, die sich nacheinander verbinden, tauchen hier auf.
 */
export function WorkerPanel() {
  const workers = usePolling<WorkerInfo[]>((signal) => api.workers(signal), REFRESH_MS);
  const list = workers.data ?? [];
  const online = list.length;
  const threads = list.reduce((sum, w) => sum + w.threads, 0);
  const inFlight = list.reduce((sum, w) => sum + w.inFlight, 0);
  const busy = inFlight > 0;

  return (
    <section>
      <SectionLabel
        index="01"
        right={
          <span
            className="mono flex items-center gap-2 text-[0.625rem] uppercase tracking-[0.1em]"
            style={{ color: busy ? "var(--aqua-deep)" : "var(--ink-3)" }}
          >
            <Dot color={busy ? "var(--aqua-deep)" : "var(--ink-4)"} live={busy} />
            {busy ? "arbeitet" : "Leerlauf"} · alle {REFRESH_MS / 1000} s
          </span>
        }
      >
        Worker
      </SectionLabel>

      <div className="card">
        <Summary
          online={online}
          threads={threads}
          inFlight={inFlight}
          pending={workers.pending && workers.data === null}
          offline={Boolean(workers.error)}
        />

        {workers.error ? (
          <div className="border-t border-[var(--rule)] p-4">
            <ErrorNote onRetry={workers.refresh}>{workers.error}</ErrorNote>
          </div>
        ) : workers.pending && workers.data === null ? (
          <div className="grid grid-cols-1 gap-px border-t border-[var(--rule)] bg-[var(--rule-soft)] sm:grid-cols-2 lg:grid-cols-3">
            {[0, 1, 2].map((i) => (
              <div key={i} className="space-y-3 bg-[var(--surface)] px-5 py-4">
                <Skeleton className="h-3 w-2/3" />
                <Skeleton className="h-2 w-full" />
                <Skeleton className="h-3 w-1/2" />
              </div>
            ))}
            <span className="sr-only">Worker werden geladen</span>
          </div>
        ) : online === 0 ? (
          <div className="border-t border-[var(--rule)]">
            <Empty
              hint={
                <>
                  Auf einem beliebigen Rechner im Netz:{" "}
                  <span className="whitespace-nowrap">
                    java -jar worker.jar --coordinator &lt;host&gt;:9090
                  </span>{" "}
                  – er erscheint hier innerhalb von {REFRESH_MS / 1000} s.
                </>
              }
            >
              Keine Worker verbunden.
            </Empty>
          </div>
        ) : (
          <ul
            className="grid grid-cols-1 gap-px border-t border-[var(--rule)] bg-[var(--rule-soft)] sm:grid-cols-2 lg:grid-cols-3"
            aria-live="polite"
          >
            {list.map((worker, i) => (
              <WorkerTile key={worker.workerId} worker={worker} index={i + 1} />
            ))}
          </ul>
        )}
      </div>
    </section>
  );
}

function Summary({
  online,
  threads,
  inFlight,
  pending,
  offline,
}: {
  online: number;
  threads: number;
  inFlight: number;
  pending: boolean;
  offline: boolean;
}) {
  const muted = pending || offline;
  return (
    <div className="flex flex-wrap items-end gap-x-10 gap-y-4 px-5 py-5">
      <div>
        <p className="label">Online</p>
        <p
          className="display num mt-1 text-[3rem] leading-none md:text-[3.5rem]"
          style={{ color: !muted && online > 0 ? "var(--aqua-deep)" : undefined }}
        >
          {pending ? (
            <Skeleton className="h-[2.2rem] w-16" />
          ) : offline ? (
            <span className="text-[var(--ink-4)]">–</span>
          ) : (
            num(online)
          )}
        </p>
        <p className="mono mt-1 text-[0.6875rem] text-[var(--ink-3)]">
          Worker im Schwarm
        </p>
      </div>
      <SummaryFact label="Threads gesamt" value={muted ? "–" : num(threads)} />
      <SummaryFact
        label="URLs in Arbeit"
        value={muted ? "–" : num(inFlight)}
        accent={!muted && inFlight > 0}
      />
    </div>
  );
}

function SummaryFact({
  label,
  value,
  accent,
}: {
  label: string;
  value: string;
  accent?: boolean;
}) {
  return (
    <div className="border-l border-[var(--rule)] pl-4">
      <p className="label">{label}</p>
      <p
        className="mono mt-1.5 text-[1.25rem] font-medium leading-none"
        style={{ color: accent ? "var(--aqua-deep)" : undefined }}
      >
        {value}
      </p>
    </div>
  );
}

function WorkerTile({ worker, index }: { worker: WorkerInfo; index: number }) {
  const active = worker.inFlight > 0;
  /* Ein Paket umfasst höchstens 2 × Threads URLs (PROTOCOL.md) – das ist die Skala des Balkens. */
  const capacity = Math.max(1, worker.threads * 2);
  const load = Math.min(1, worker.inFlight / capacity);

  return (
    <li className="fade-in relative bg-[var(--surface)] px-5 py-4">
      <div className="flex items-baseline gap-3">
        <span className="mono shrink-0 text-[0.625rem] text-[var(--ink-4)]">
          {padded(index, 2)}
        </span>
        <span className="mono min-w-0 flex-1 truncate text-[0.9375rem] font-medium">
          {worker.workerId}
        </span>
        <span
          className="mono flex shrink-0 items-center gap-2 text-[0.625rem] uppercase tracking-[0.1em]"
          style={{ color: active ? "var(--aqua-deep)" : "var(--ink-3)" }}
        >
          <Dot color={active ? "var(--aqua-deep)" : "var(--st-COMPLETED)"} live={active} />
          {active ? "arbeitet" : "bereit"}
        </span>
      </div>

      <div
        className="relative mt-3 h-1.5 w-full overflow-hidden bg-[var(--sunken)]"
        role="img"
        aria-label={`${worker.inFlight} von höchstens ${capacity} URLs in Arbeit`}
      >
        <div
          className={`absolute inset-y-0 left-0 bg-[var(--aqua-deep)] transition-[width] duration-500 ${active ? "sweep" : ""}`}
          style={{ width: `${load * 100}%` }}
        />
      </div>

      <dl className="mono mt-3 grid grid-cols-3 gap-3 text-[0.6875rem]">
        <div>
          <dt className="label text-[0.5625rem]">Threads</dt>
          <dd className="mt-0.5 text-[0.8125rem] font-medium">{num(worker.threads)}</dd>
        </div>
        <div>
          <dt className="label text-[0.5625rem]">In Arbeit</dt>
          <dd
            className="mt-0.5 text-[0.8125rem] font-medium"
            style={{ color: active ? "var(--aqua-deep)" : undefined }}
          >
            {num(worker.inFlight)}
          </dd>
        </div>
        <div>
          <dt className="label text-[0.5625rem]">Seit</dt>
          <dd className="mt-0.5 text-[0.8125rem]" title={worker.connectedAt}>
            {clock(worker.connectedAt)}
            <span className="ml-1.5 text-[var(--ink-4)]">{ago(worker.connectedAt)}</span>
          </dd>
        </div>
      </dl>
    </li>
  );
}
