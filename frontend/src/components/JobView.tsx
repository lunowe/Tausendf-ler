"use client";

import Link from "next/link";
import { useEffect, useRef, useState } from "react";
import { api, isTerminal, type JobDetail, type PageResult } from "@/lib/api";
import { usePolling } from "@/lib/usePolling";
import { clock, dateTime, duration, host, num, path } from "@/lib/format";
import { LiveStream } from "@/components/LiveStream";
import {
  Dot,
  Empty,
  ErrorNote,
  Metric,
  SectionLabel,
  Skeleton,
  StatusBadge,
  statusColor,
  statusLabel,
} from "@/components/ui";

const POLL_MS = 1000;
/** Der Koordinator liefert pro /results-Antwort höchstens so viele Zeilen (JobService.RESULT_PAGE_SIZE). */
const RESULT_PAGE_SIZE = 50;

export function JobView({ jobId }: { jobId: string }) {
  const job = usePolling<JobDetail>((signal) => api.job(jobId, signal), POLL_MS);
  const detail = job.data;
  const streaming = detail !== null && !isTerminal(detail.status);

  const { pages, error: streamError } = useLiveResults(jobId, streaming);

  if (job.pending && !detail) {
    return (
      <div className="space-y-6">
        <BackLink />
        <Skeleton className="h-10 w-2/3 max-w-sm" />
        <Skeleton className="h-3 w-1/3 max-w-xs" />
      </div>
    );
  }
  if (!detail) {
    return (
      <div className="space-y-6">
        <BackLink />
        <ErrorNote onRetry={job.refresh}>
          {job.error ?? "Auftrag nicht gefunden"}
        </ErrorNote>
        <Empty hint="Prüfe die Job-ID, oder öffne den Auftrag über die Liste im Leitstand.">
          Zu dieser ID liegt kein Auftrag vor.
        </Empty>
      </div>
    );
  }

  return (
    <div className="space-y-12 md:space-y-14">
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
              className="mono flex items-center gap-2 text-[0.625rem] uppercase tracking-[0.1em]"
              style={{ color: streaming ? "var(--aqua-deep)" : "var(--ink-3)" }}
            >
              <Dot
                color={streaming ? "var(--aqua-deep)" : "var(--ink-4)"}
                live={streaming}
              />
              {streaming ? `Live · alle ${POLL_MS / 1000} s` : "Stream beendet"}
            </span>
          }
        >
          Live-Stream · {num(pages.length)} Seiten
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
      className="mono link-underline inline-block text-[0.6875rem] uppercase tracking-[0.1em] text-[var(--ink-2)] hover:text-[var(--granat)]"
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
    <section>
      <div className="mb-5 flex flex-wrap items-center gap-x-4 gap-y-2">
        <BackLink />
        <span className="mono truncate text-[0.625rem] text-[var(--ink-4)]">
          {detail.jobId}
        </span>
      </div>

      <div className="flex flex-wrap items-start justify-between gap-x-6 gap-y-4">
        <div className="min-w-0">
          <p className="label mb-2">Auftrag</p>
          <h1 className="display break-words text-[2rem] md:text-[2.75rem]">
            {host(detail.url)}
          </h1>
          <a
            href={detail.url}
            target="_blank"
            rel="noreferrer noopener"
            className="mono link-underline mt-3 inline-block break-all text-[0.8125rem] text-[var(--ink-2)] hover:text-[var(--granat)]"
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

/** Tiefenleiter: eine Zelle je Ebene, gefüllt bis `currentDepth`. */
function DepthGauge({ detail }: { detail: JobDetail }) {
  const levels = Array.from({ length: detail.maxDepth + 1 }, (_, i) => i);
  const running = detail.status === "RUNNING";
  const color = statusColor(detail.status);

  return (
    <div className="mt-8">
      <div className="mb-2 flex flex-wrap items-baseline justify-between gap-x-4 gap-y-1">
        <span className="label">
          Tiefe {detail.currentDepth} von {detail.maxDepth}
        </span>
        <span className="mono text-[0.6875rem] text-[var(--ink-3)]">
          {num(detail.pagesVisited)} Seiten · {num(detail.linksFound)} Links
        </span>
      </div>
      <div
        className="flex gap-px"
        role="img"
        aria-label={`Tiefe ${detail.currentDepth} von ${detail.maxDepth} erreicht`}
      >
        {levels.map((level) => {
          const done = level <= detail.currentDepth;
          const active = done && running && level === detail.currentDepth;
          return (
            <div
              key={level}
              className={`relative h-2 flex-1 overflow-hidden ${active ? "sweep" : ""}`}
              style={{
                background: done ? color : "var(--rule-soft)",
                opacity: done && level !== detail.currentDepth ? 0.42 : 1,
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
  const [confirmAbort, setConfirmAbort] = useState(false);

  const active = detail.status === "RUNNING" || detail.status === "PAUSED";
  // Abgeleitet statt gespeichert: endet der Auftrag von selbst, verschwindet auch die Rückfrage.
  const asking = confirmAbort && active;

  async function send(action: "pause" | "resume" | "abort") {
    setBusy(action);
    setError(null);
    try {
      await api.signal(detail.jobId, action);
      setConfirmAbort(false);
      onChanged();
    } catch (e) {
      setError(e instanceof Error ? e.message : String(e));
    } finally {
      setBusy(null);
    }
  }

  return (
    <div className="mt-8 border-t border-[var(--rule-soft)] pt-6">
      <div className="flex flex-wrap items-center gap-3">
        <button
          className="btn"
          disabled={detail.status !== "RUNNING" || busy !== null}
          title={
            detail.status === "RUNNING"
              ? "Keine neuen URLs mehr verteilen"
              : `Nur möglich, solange der Auftrag läuft (aktuell: ${statusLabel(detail.status)})`
          }
          onClick={() => send("pause")}
        >
          Pause
        </button>
        <button
          className="btn"
          disabled={detail.status !== "PAUSED" || busy !== null}
          title={
            detail.status === "PAUSED"
              ? "Verteilung wieder aufnehmen"
              : `Nur möglich, wenn der Auftrag pausiert ist (aktuell: ${statusLabel(detail.status)})`
          }
          onClick={() => send("resume")}
        >
          Fortsetzen
        </button>

        {asking ? (
          <span className="flex flex-wrap items-center gap-2">
            <span className="mono text-[0.6875rem] text-[var(--granat)]">
              Wirklich abbrechen?
            </span>
            <button
              className="btn btn-primary"
              disabled={busy !== null}
              onClick={() => send("abort")}
            >
              {busy === "abort" ? "Sendet …" : "Ja, abbrechen"}
            </button>
            <button
              className="btn"
              disabled={busy !== null}
              onClick={() => setConfirmAbort(false)}
            >
              Zurück
            </button>
          </span>
        ) : (
          <button
            className="btn btn-danger"
            disabled={!active || busy !== null}
            title={
              active
                ? "Auftrag beenden – gesammelte Ergebnisse bleiben erhalten"
                : `Der Auftrag ist bereits beendet (${statusLabel(detail.status)})`
            }
            onClick={() => setConfirmAbort(true)}
          >
            Abbrechen
          </button>
        )}
      </div>

      <p className="mono mt-3 text-[0.625rem] leading-relaxed text-[var(--ink-3)]">
        POST /api/jobs/{"{id}"}/pause | resume | abort · ungültige Übergänge
        beantwortet der Koordinator mit 409.
      </p>

      {error && (
        <div className="mt-4">
          <ErrorNote>{error}</ErrorNote>
        </div>
      )}
    </div>
  );
}

function Facts({ detail, received }: { detail: JobDetail; received: number }) {
  return (
    <div className="grid grid-cols-2 gap-x-6 gap-y-7 md:grid-cols-4">
      <Metric label="Besuchte Seiten" value={num(detail.pagesVisited)} tone="accent" />
      <Metric label="Extrahierte Links" value={num(detail.linksFound)} />
      <Metric
        label="Fehler"
        value={num(detail.errors)}
        tone={detail.errors > 0 ? "warn" : undefined}
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
    ["Status", statusLabel(detail.status)],
    ["Start-URL", `${host(detail.url)}${path(detail.url)}`],
    ["Besuchte Seiten", num(detail.pagesVisited)],
    ["Extrahierte Links", num(detail.linksFound)],
    ["Fehler", num(detail.errors)],
    ["Erreichte Tiefe", `${detail.currentDepth} von ${detail.maxDepth}`],
    ["Dauer", duration(detail.startedAt, detail.finishedAt)],
    ["Beendet", dateTime(detail.finishedAt)],
  ];

  const headline =
    detail.status === "COMPLETED"
      ? "Crawl abgeschlossen"
      : detail.status === "ABORTED"
        ? "Crawl abgebrochen"
        : "Crawl fehlgeschlagen";

  return (
    <div className="card p-5 md:p-6">
      <div className="mb-5 flex flex-wrap items-baseline justify-between gap-x-4 gap-y-2 border-b border-[var(--rule-soft)] pb-4">
        <h3 className="display text-[1.5rem]">{headline}</h3>
        <span
          className="mono text-[0.625rem] uppercase tracking-[0.12em]"
          style={{ color: statusColor(detail.status) }}
        >
          Bericht
        </span>
      </div>
      <dl className="grid grid-cols-1 sm:grid-cols-2 sm:gap-x-10">
        {rows.map(([label, value]) => (
          <div
            key={label}
            className="flex items-baseline justify-between gap-4 border-b border-[var(--rule-soft)] py-2.5"
          >
            <dt className="label">{label}</dt>
            <dd className="mono truncate text-[0.8125rem] font-medium">{value}</dd>
          </div>
        ))}
      </dl>
      {detail.status === "ABORTED" && (
        <p className="mt-5 max-w-[70ch] text-[0.8125rem] leading-relaxed text-[var(--ink-2)]">
          Die bereits gesammelten Ergebnisse bleiben erhalten – der Abbruch stoppt
          nur die Verteilung neuer URLs an die Worker.
        </p>
      )}
    </div>
  );
}

/* ------------------------------------------------------------------ */

/**
 * Der Live-Stream: fragt `/results?afterSeq=<letzte seq>` einmal pro Sekunde ab und hängt
 * alles Neue an. Ein Tick leert die Warteschlange vollständig (der Endpunkt deckelt eine
 * Antwort bei {@link RESULT_PAGE_SIZE} Zeilen). Erreicht der Auftrag einen Endzustand,
 * kippt `streaming` auf false – das löst einen letzten Durchlauf aus und stoppt den Timer.
 */
function useLiveResults(jobId: string, streaming: boolean) {
  const [pages, setPages] = useState<PageResult[]>([]);
  const [error, setError] = useState<string | null>(null);
  // Als Instanzzustand sicher: die Route keyt <JobView> nach Job-ID, ein anderer Auftrag
  // ist also immer eine frische Komponente und erbt diesen Cursor nie.
  const seq = useRef(0);

  useEffect(() => {
    let cancelled = false;
    const controller = new AbortController();
    let timer: ReturnType<typeof setTimeout> | undefined;

    const drain = async () => {
      // Begrenzte Schleife, damit ein schneller Crawl einen Tick nicht endlos beschäftigt.
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
