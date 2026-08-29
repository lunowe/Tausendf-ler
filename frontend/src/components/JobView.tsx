"use client";

import Link from "next/link";
import { useEffect, useRef, useState } from "react";
import {
  api,
  isTerminal,
  type JobDetail,
  type PageResult,
} from "@/lib/api";
import { usePolling } from "@/lib/usePolling";
import { clock, dateTime, duration, host, num, path } from "@/lib/format";
import { LiveStream } from "@/components/LiveStream";
import {
  Empty,
  ErrorNote,
  Metric,
  SectionLabel,
  StatusBadge,
  statusColor,
} from "@/components/ui";

const POLL_MS = 1000;
/** The coordinator answers /results with at most this many rows (JobService.RESULT_PAGE_SIZE). */
const RESULT_PAGE_SIZE = 50;

export function JobView({ jobId }: { jobId: string }) {
  const job = usePolling<JobDetail>((signal) => api.job(jobId, signal), POLL_MS);
  const detail = job.data;
  const streaming = detail !== null && !isTerminal(detail.status);

  const { pages, error: streamError } = useLiveResults(jobId, streaming);

  if (job.pending && !detail) {
    return <Empty>lade Auftrag …</Empty>;
  }
  if (!detail) {
    return (
      <div className="space-y-6">
        <BackLink />
        <ErrorNote>{job.error ?? "Auftrag nicht gefunden"}</ErrorNote>
      </div>
    );
  }

  return (
    <div className="space-y-16">
      <JobHeader detail={detail} onChanged={job.refresh} />

      <section>
        <SectionLabel index="02">Kennzahlen</SectionLabel>
        <Facts detail={detail} received={pages.length} />
      </section>

      <section>
        <SectionLabel
          index="03"
          right={
            <span
              className="mono flex items-center gap-2 text-[0.625rem] uppercase tracking-[0.18em]"
              style={{ color: streaming ? "var(--amber)" : "var(--ink-faint)" }}
            >
              <span
                className={`inline-block h-1.5 w-1.5 rounded-full ${streaming ? "pulse" : ""}`}
                style={{
                  background: streaming ? "var(--amber)" : "var(--ink-faint)",
                  color: "var(--amber)",
                }}
              />
              {streaming ? `Live · alle ${POLL_MS / 1000} s` : "Stream beendet"}
            </span>
          }
        >
          Live-Stream · {num(pages.length)} Segmente
        </SectionLabel>

        {streamError && (
          <div className="mb-4">
            <ErrorNote>{streamError}</ErrorNote>
          </div>
        )}
        <LiveStream pages={pages} streaming={streaming} />
      </section>

      {isTerminal(detail.status) && (
        <section>
          <SectionLabel index="04">Abschlussbericht</SectionLabel>
          <Report detail={detail} />
        </section>
      )}
    </div>
  );
}

/* ------------------------------------------------------------------ */

function BackLink() {
  return (
    <Link
      href="/"
      className="mono link-underline inline-block text-[0.6875rem] uppercase tracking-[0.18em] text-[var(--ink-dim)] hover:text-[var(--amber)]"
    >
      ← Leitstand
    </Link>
  );
}

function JobHeader({
  detail,
  onChanged,
}: {
  detail: JobDetail;
  onChanged: () => void;
}) {
  return (
    <section className="reveal pt-4">
      <div className="mb-6 flex flex-wrap items-center gap-4">
        <BackLink />
        <span className="mono text-[0.625rem] text-[var(--ink-faint)]">
          {detail.jobId}
        </span>
      </div>

      <div className="flex flex-wrap items-start justify-between gap-6">
        <div className="min-w-0">
          <h1 className="display break-words text-[2.75rem] leading-[1] md:text-[3.5rem]">
            {host(detail.url)}
          </h1>
          <a
            href={detail.url}
            target="_blank"
            rel="noreferrer noopener"
            className="mono link-underline mt-3 inline-block break-all text-[0.8125rem] text-[var(--ink-dim)] hover:text-[var(--amber)]"
          >
            {detail.url}
          </a>
        </div>
        <StatusBadge status={detail.status} size="lg" />
      </div>

      <DepthGauge detail={detail} />
      <Controls detail={detail} onChanged={onChanged} />
    </section>
  );
}

/** Depth ladder: one cell per level, filled up to `currentDepth`. */
function DepthGauge({ detail }: { detail: JobDetail }) {
  const levels = Array.from({ length: detail.maxDepth + 1 }, (_, i) => i);
  const running = detail.status === "RUNNING";
  const color = statusColor(detail.status);

  return (
    <div className="mt-8">
      <div className="mb-2 flex items-baseline justify-between">
        <span className="label">
          Tiefe {detail.currentDepth} von {detail.maxDepth}
        </span>
        <span className="mono text-[0.6875rem] text-[var(--ink-faint)]">
          {num(detail.pagesVisited)} Seiten · {num(detail.linksFound)} Links
        </span>
      </div>
      <div className="flex gap-px">
        {levels.map((level) => {
          const done = level <= detail.currentDepth;
          return (
            <div
              key={level}
              className={`relative h-2 flex-1 overflow-hidden ${
                done && running && level === detail.currentDepth
                  ? "crawler-bar"
                  : ""
              }`}
              style={{
                background: done ? color : "var(--line-soft)",
                opacity: done ? (level === detail.currentDepth ? 1 : 0.45) : 1,
              }}
              title={`Tiefe ${level}`}
            />
          );
        })}
      </div>
    </div>
  );
}

function Controls({
  detail,
  onChanged,
}: {
  detail: JobDetail;
  onChanged: () => void;
}) {
  const [busy, setBusy] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);

  const active = detail.status === "RUNNING" || detail.status === "PAUSED";

  async function send(action: "pause" | "resume" | "abort") {
    setBusy(action);
    setError(null);
    try {
      await api.signal(detail.jobId, action);
      onChanged();
    } catch (e) {
      setError(e instanceof Error ? e.message : String(e));
    } finally {
      setBusy(null);
    }
  }

  return (
    <div className="mt-8 flex flex-wrap items-center gap-3">
      <button
        className="btn"
        disabled={detail.status !== "RUNNING" || busy !== null}
        onClick={() => send("pause")}
      >
        Pause
      </button>
      <button
        className="btn"
        disabled={detail.status !== "PAUSED" || busy !== null}
        onClick={() => send("resume")}
      >
        Fortsetzen
      </button>
      <button
        className="btn btn-danger"
        disabled={!active || busy !== null}
        onClick={() => send("abort")}
      >
        Abbrechen
      </button>
      <span className="mono text-[0.625rem] leading-relaxed text-[var(--ink-faint)]">
        POST /api/jobs/{"{id}"}/…
        <br />
        Ungültige Übergänge beantwortet der Koordinator mit 409.
      </span>
      {error && (
        <div className="w-full">
          <ErrorNote>{error}</ErrorNote>
        </div>
      )}
    </div>
  );
}

function Facts({ detail, received }: { detail: JobDetail; received: number }) {
  return (
    <div className="grid grid-cols-2 gap-x-6 gap-y-7 md:grid-cols-4">
      <Metric label="Besuchte Seiten" value={num(detail.pagesVisited)} accent />
      <Metric label="Gefundene Links" value={num(detail.linksFound)} />
      <Metric
        label="Fehler"
        value={
          <span style={{ color: detail.errors > 0 ? "var(--coral)" : undefined }}>
            {num(detail.errors)}
          </span>
        }
      />
      <Metric
        label="Tiefe akt. / max"
        value={`${detail.currentDepth} / ${detail.maxDepth}`}
      />
      <Metric label="Angelegt" value={dateTime(detail.createdAt)} />
      <Metric label="Gestartet" value={clock(detail.startedAt)} />
      <Metric
        label="Beendet"
        value={detail.finishedAt ? clock(detail.finishedAt) : "läuft"}
      />
      <Metric
        label="Im Stream empfangen"
        value={`${num(received)} / ${num(detail.pagesVisited)}`}
      />
    </div>
  );
}

function Report({ detail }: { detail: JobDetail }) {
  const rows: Array<[string, string]> = [
    ["Status", detail.status],
    ["Start-URL", `${host(detail.url)}${path(detail.url)}`],
    ["Besuchte Seiten", num(detail.pagesVisited)],
    ["Extrahierte Links", num(detail.linksFound)],
    ["Fehler", num(detail.errors)],
    ["Erreichte Tiefe", `${detail.currentDepth} von ${detail.maxDepth}`],
    ["Gesamtdauer", duration(detail.startedAt, detail.finishedAt)],
    ["Beendet am", dateTime(detail.finishedAt)],
  ];

  return (
    <div className="panel p-6 md:p-8">
      <div className="mb-6 flex items-baseline justify-between gap-4">
        <h2 className="display text-[1.75rem]">
          {detail.status === "COMPLETED"
            ? "Crawl abgeschlossen"
            : detail.status === "ABORTED"
              ? "Crawl abgebrochen"
              : "Crawl fehlgeschlagen"}
        </h2>
        <span
          className="mono text-[0.625rem] uppercase tracking-[0.2em]"
          style={{ color: statusColor(detail.status) }}
        >
          Report
        </span>
      </div>
      <dl className="grid grid-cols-1 gap-px bg-[var(--line)] sm:grid-cols-2">
        {rows.map(([label, value]) => (
          <div
            key={label}
            className="flex items-baseline justify-between gap-4 bg-[var(--panel)] px-4 py-3"
          >
            <dt className="label">{label}</dt>
            <dd className="mono truncate text-[0.8125rem]">{value}</dd>
          </div>
        ))}
      </dl>
      {detail.status === "ABORTED" && (
        <p className="mono mt-5 text-[0.6875rem] leading-relaxed text-[var(--ink-faint)]">
          Bereits gesammelte Ergebnisse bleiben erhalten – der Abbruch stoppt nur
          die Verteilung neuer URLs an die Worker.
        </p>
      )}
    </div>
  );
}

/* ------------------------------------------------------------------ */

/**
 * The live stream: polls `/results?afterSeq=<letzte seq>` once per second and appends
 * everything new. A single tick drains all pending pages (the endpoint caps a response
 * at {@link RESULT_PAGE_SIZE} rows). When the job reaches a terminal state, `streaming`
 * flips to false, which triggers one last drain and then stops the timer.
 */
function useLiveResults(jobId: string, streaming: boolean) {
  const [pages, setPages] = useState<PageResult[]>([]);
  const [error, setError] = useState<string | null>(null);
  // Safe as instance state: the route keys <JobView> by job id, so a different job
  // is always a fresh component instance and never inherits this cursor.
  const seq = useRef(0);

  useEffect(() => {
    let cancelled = false;
    const controller = new AbortController();
    let timer: ReturnType<typeof setTimeout> | undefined;

    const drain = async () => {
      // Bounded loop so a fast crawl cannot keep one tick spinning forever.
      for (let round = 0; round < 20; round++) {
        const batch = await api.results(jobId, seq.current, controller.signal);
        if (cancelled || batch.length === 0) return;
        seq.current = batch[batch.length - 1].seq;
        setPages((prev) => [...prev, ...batch]);
        if (batch.length < RESULT_PAGE_SIZE) return;
      }
    };

    const tick = async () => {
      try {
        await drain();
        if (!cancelled) setError(null);
      } catch (e) {
        if (!cancelled && !controller.signal.aborted) {
          setError(e instanceof Error ? e.message : String(e));
        }
      } finally {
        if (!cancelled && streaming) {
          timer = setTimeout(tick, POLL_MS);
        }
      }
    };

    void tick();

    return () => {
      cancelled = true;
      controller.abort();
      if (timer) clearTimeout(timer);
    };
  }, [jobId, streaming]);

  return { pages, error };
}
