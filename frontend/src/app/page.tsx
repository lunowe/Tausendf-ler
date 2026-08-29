"use client";

import Link from "next/link";
import { api, WEB_OWNER, type JobSummary, type Stats } from "@/lib/api";
import { usePolling } from "@/lib/usePolling";
import { ago, host, num, padded, path } from "@/lib/format";
import { NewCrawlForm } from "@/components/NewCrawlForm";
import {
  Empty,
  ErrorNote,
  SectionLabel,
  Skeleton,
  StatusBadge,
} from "@/components/ui";

const REFRESH_MS = 2000;

export default function DashboardPage() {
  const stats = usePolling<Stats>((signal) => api.stats(signal), REFRESH_MS);
  const jobs = usePolling<JobSummary[]>(
    (signal) => api.listJobs(WEB_OWNER, signal),
    REFRESH_MS,
  );

  const offline = stats.error ?? jobs.error;

  function retry() {
    stats.refresh();
    jobs.refresh();
  }

  return (
    <div className="space-y-12 md:space-y-16">
      <section>
        <p className="label mb-3">Leitstand · Browser</p>
        <h1 className="display max-w-[20ch] text-[2.25rem] md:text-[2.75rem]">
          Aufträge anlegen, dem Schwarm zusehen, Ergebnisse durchsuchen.
        </h1>
        <p className="mt-4 max-w-[62ch] text-[0.9375rem] leading-relaxed text-[var(--ink-2)]">
          Dieselbe REST-Schnittstelle wie im Telegram-Bot – nur im Browser. Der
          Koordinator verteilt die URLs an die Worker, diese Seite fragt ihn alle{" "}
          {REFRESH_MS / 1000} Sekunden nach dem aktuellen Stand.
        </p>

        <div className="mt-8 grid grid-cols-1 gap-px bg-[var(--rule)] sm:grid-cols-3">
          <Counter
            label="Aufträge gesamt"
            value={stats.data?.totalJobs}
            pending={stats.pending}
            offline={Boolean(offline)}
          />
          <Counter
            label="Aktive Aufträge"
            value={stats.data?.activeJobs}
            pending={stats.pending}
            offline={Boolean(offline)}
            accent
          />
          <Counter
            label="Seiten gesamt"
            value={stats.data?.totalPagesCrawled}
            pending={stats.pending}
            offline={Boolean(offline)}
          />
        </div>

        {offline && (
          <div className="mt-6">
            <ErrorNote onRetry={retry}>
              {offline}. Läuft der Koordinator, und steht diese Origin in{" "}
              <span className="whitespace-nowrap">tausendfuessler.cors-origins</span>?
            </ErrorNote>
          </div>
        )}
      </section>

      <section className="grid grid-cols-1 gap-10 lg:grid-cols-12 lg:gap-8">
        <div className="lg:col-span-7">
          <SectionLabel index="01">Auftrag anlegen</SectionLabel>
          <NewCrawlForm onCreated={jobs.refresh} />
        </div>
        <div className="lg:col-span-5">
          <SectionLabel index="02">Meist gecrawlte Domains</SectionLabel>
          <TopDomains domains={stats.data?.topDomains ?? {}} />
        </div>
      </section>

      <section>
        <SectionLabel
          index="03"
          right={
            <span className="mono text-[0.625rem] text-[var(--ink-3)]">
              owner {WEB_OWNER} · Aktualisierung alle {REFRESH_MS / 1000} s
            </span>
          }
        >
          Aufträge aus dem Browser
        </SectionLabel>
        <JobList jobs={jobs.data} pending={jobs.pending} />
      </section>
    </div>
  );
}

function Counter({
  label,
  value,
  pending,
  offline,
  accent,
}: {
  label: string;
  value: number | undefined;
  pending: boolean;
  offline: boolean;
  accent?: boolean;
}) {
  return (
    <div className="bg-[var(--surface)] px-5 py-4">
      <p className="label">{label}</p>
      <p
        className="display num mt-2 text-[2.25rem] leading-none"
        style={{
          color: accent && !offline && value ? "var(--aqua-deep)" : undefined,
        }}
      >
        {pending && value === undefined ? (
          <Skeleton className="h-[1.6rem] w-16" />
        ) : offline || value === undefined ? (
          <span className="text-[var(--ink-4)]">–</span>
        ) : (
          num(value)
        )}
      </p>
    </div>
  );
}

function TopDomains({ domains }: { domains: Record<string, number> }) {
  const entries = Object.entries(domains);
  if (entries.length === 0) {
    return (
      <Empty hint="Sobald der erste Crawl Seiten liefert, steht hier die Verteilung nach Domain.">
        Noch nichts gecrawlt.
      </Empty>
    );
  }
  const max = Math.max(...entries.map(([, count]) => count));

  return (
    <ol className="card divide-y divide-[var(--rule-soft)]">
      {entries.map(([domain, count], i) => (
        <li key={domain} className="relative px-4 py-3">
          <div
            className="absolute inset-y-0 left-0 border-r border-[var(--aqua-deep)] bg-[var(--aqua-tint)] transition-[width] duration-500"
            style={{ width: `${Math.max(4, (count / max) * 100)}%` }}
            aria-hidden="true"
          />
          <div className="relative flex items-baseline justify-between gap-4">
            <span className="mono min-w-0 truncate text-[0.8125rem]">
              <span className="mr-3 text-[var(--ink-4)]">{padded(i + 1, 2)}</span>
              {domain}
            </span>
            <span className="mono shrink-0 text-[0.8125rem] font-medium">
              {num(count)}
            </span>
          </div>
        </li>
      ))}
    </ol>
  );
}

function JobList({
  jobs,
  pending,
}: {
  jobs: JobSummary[] | null;
  pending: boolean;
}) {
  if (pending && jobs === null) {
    return (
      <div className="card divide-y divide-[var(--rule-soft)]">
        {[0, 1, 2].map((i) => (
          <div key={i} className="flex items-center gap-5 px-4 py-4 sm:px-5">
            <Skeleton className="h-3 w-8" />
            <Skeleton className="h-3 flex-1" />
            <Skeleton className="h-5 w-24" />
          </div>
        ))}
        <span className="sr-only">Aufträge werden geladen</span>
      </div>
    );
  }
  if (!jobs || jobs.length === 0) {
    return (
      <Empty hint="Aufträge aus Telegram laufen unter der jeweiligen Chat-ID und erscheinen deshalb nicht in dieser Liste.">
        Noch keine Aufträge aus dem Browser.
      </Empty>
    );
  }

  return (
    <ul className="card divide-y divide-[var(--rule-soft)]">
      {jobs.map((job, i) => (
        <li key={job.jobId}>
          <Link
            href={`/jobs/${job.jobId}`}
            className="group flex items-start gap-3 px-4 py-3.5 transition-colors hover:bg-[var(--sunken)] sm:gap-5 sm:px-5"
          >
            <span className="mono mt-0.5 w-[2.2rem] shrink-0 text-[0.6875rem] text-[var(--ink-4)] transition-colors group-hover:text-[var(--granat)]">
              {padded(jobs.length - i, 3)}
            </span>

            <span className="min-w-0 flex-1">
              <span className="mono block truncate text-[0.875rem] font-medium">
                {host(job.url)}
                <span className="font-normal text-[var(--ink-3)]">
                  {path(job.url)}
                </span>
              </span>
              <span className="mono mt-1 block truncate text-[0.625rem] text-[var(--ink-4)]">
                {job.jobId}
              </span>
            </span>

            <span className="flex shrink-0 flex-col items-end gap-1.5">
              <StatusBadge status={job.status} />
              <span className="mono text-[0.625rem] text-[var(--ink-3)]">
                {num(job.pagesVisited)} Seiten · {ago(job.createdAt)}
              </span>
            </span>
          </Link>
        </li>
      ))}
    </ul>
  );
}
