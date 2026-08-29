"use client";

import Link from "next/link";
import { api, WEB_OWNER, type JobSummary, type Stats } from "@/lib/api";
import { usePolling } from "@/lib/usePolling";
import { ago, host, num, padded, path } from "@/lib/format";
import { NewCrawlForm } from "@/components/NewCrawlForm";
import { Empty, ErrorNote, SectionLabel, StatusBadge } from "@/components/ui";

const REFRESH_MS = 2000;

export default function DashboardPage() {
  const stats = usePolling<Stats>((signal) => api.stats(signal), REFRESH_MS);
  const jobs = usePolling<JobSummary[]>(
    (signal) => api.listJobs(WEB_OWNER, signal),
    REFRESH_MS,
  );

  const offline = stats.error ?? jobs.error;

  return (
    <div className="space-y-20">
      <Hero stats={stats.data} offline={Boolean(offline)} />

      {offline && (
        <div className="-mt-14">
          <ErrorNote>{offline}</ErrorNote>
        </div>
      )}

      <section className="grid grid-cols-1 gap-10 lg:grid-cols-12">
        <div className="lg:col-span-7">
          <SectionLabel index="02">Auftrag anlegen</SectionLabel>
          <NewCrawlForm onCreated={jobs.refresh} />
        </div>
        <div className="lg:col-span-5">
          <SectionLabel index="03">Meistbesuchte Domains</SectionLabel>
          <TopDomains domains={stats.data?.topDomains ?? {}} />
        </div>
      </section>

      <section>
        <SectionLabel
          index="04"
          right={
            <span className="mono text-[0.625rem] text-[var(--ink-faint)]">
              alle {REFRESH_MS / 1000} s
            </span>
          }
        >
          Aufträge · owner {WEB_OWNER}
        </SectionLabel>
        <JobTable jobs={jobs.data} pending={jobs.pending} />
      </section>
    </div>
  );
}

function Hero({ stats, offline }: { stats: Stats | null; offline: boolean }) {
  return (
    <section className="grid grid-cols-1 gap-12 pt-6 lg:grid-cols-12 lg:gap-8">
      <div className="reveal lg:col-span-7">
        <SectionLabel index="01">Leitstand</SectionLabel>
        <h1 className="display text-[3.5rem] leading-[0.92] md:text-[4.75rem]">
          Ein Auftrag,
          <br />
          <span className="text-[var(--amber)] italic">hundert Beine.</span>
        </h1>
        <p className="mt-6 max-w-[46ch] text-[0.9375rem] leading-relaxed text-[var(--ink-dim)]">
          Dieselbe REST-Schnittstelle, die der Telegram-Bot benutzt – nur im
          Browser. Aufträge anlegen, den Schwarm beim Crawlen zusehen und die
          gesammelten Seiten durchsuchen.
        </p>
        <div className="mt-8 flex flex-wrap gap-x-8 gap-y-2">
          <span className="label">Koordinator :8080 · REST</span>
          <span className="label">Worker :9090 · TCP</span>
          <span className="label">Postgres · JPA</span>
        </div>
      </div>

      <div
        className="reveal lg:col-span-5"
        style={{ animationDelay: "0.12s" }}
      >
        <div className="panel h-full divide-y divide-[var(--line)]">
          <Counter
            label="Aufträge gesamt"
            value={stats?.totalJobs}
            offline={offline}
          />
          <Counter
            label="Aktiv (läuft / pausiert)"
            value={stats?.activeJobs}
            offline={offline}
            accent
          />
          <Counter
            label="Gecrawlte Seiten"
            value={stats?.totalPagesCrawled}
            offline={offline}
          />
        </div>
      </div>
    </section>
  );
}

function Counter({
  label,
  value,
  offline,
  accent,
}: {
  label: string;
  value: number | undefined;
  offline: boolean;
  accent?: boolean;
}) {
  const shown = offline || value === undefined ? "––––" : padded(value);
  return (
    <div className="flex items-baseline justify-between gap-4 px-6 py-5">
      <span className="label max-w-[13ch] leading-relaxed">{label}</span>
      <span
        className="display text-[3rem] tabular-nums leading-none"
        style={{ color: accent && !offline ? "var(--amber)" : undefined }}
      >
        {shown}
      </span>
    </div>
  );
}

function TopDomains({ domains }: { domains: Record<string, number> }) {
  const entries = Object.entries(domains);
  if (entries.length === 0) {
    return <Empty>Noch keine Seiten gecrawlt.</Empty>;
  }
  const max = Math.max(...entries.map(([, count]) => count));

  return (
    <ul className="panel divide-y divide-[var(--line)]">
      {entries.map(([domain, count], i) => (
        <li key={domain} className="relative px-5 py-3.5">
          <div
            className="hatch absolute inset-y-0 left-0 border-r border-[var(--amber)] opacity-40 transition-[width] duration-500"
            style={{ width: `${Math.max(6, (count / max) * 100)}%` }}
            aria-hidden="true"
          />
          <div className="relative flex items-baseline justify-between gap-4">
            <span className="mono truncate text-[0.8125rem]">
              <span className="mr-3 text-[var(--ink-faint)]">
                {padded(i + 1, 2)}
              </span>
              {domain}
            </span>
            <span className="mono shrink-0 text-[0.8125rem] text-[var(--amber)]">
              {num(count)}
            </span>
          </div>
        </li>
      ))}
    </ul>
  );
}

function JobTable({
  jobs,
  pending,
}: {
  jobs: JobSummary[] | null;
  pending: boolean;
}) {
  if (pending && jobs === null) {
    return <Empty>lade …</Empty>;
  }
  if (!jobs || jobs.length === 0) {
    return <Empty>Noch keine Aufträge aus dem Browser. Leg oben einen an.</Empty>;
  }

  return (
    <ul className="panel divide-y divide-[var(--line)]">
      {jobs.map((job, i) => (
        <li key={job.jobId}>
          <Link
            href={`/jobs/${job.jobId}`}
            className="group grid grid-cols-[auto_1fr_auto] items-center gap-4 px-5 py-4 transition-colors hover:bg-[rgba(255,178,76,0.05)] sm:grid-cols-[auto_1fr_auto_auto_auto] sm:gap-6"
          >
            <span className="mono text-[0.6875rem] text-[var(--ink-faint)] transition-colors group-hover:text-[var(--amber)]">
              {padded(jobs.length - i, 3)}
            </span>

            <span className="min-w-0">
              <span className="mono block truncate text-[0.875rem]">
                {host(job.url)}
                <span className="text-[var(--ink-faint)]">{path(job.url)}</span>
              </span>
              <span className="mono mt-1 block truncate text-[0.625rem] text-[var(--ink-faint)]">
                {job.jobId}
              </span>
            </span>

            <span className="mono hidden text-right text-[0.75rem] text-[var(--ink-dim)] sm:block">
              {num(job.pagesVisited)}
              <span className="ml-1 text-[var(--ink-faint)]">S.</span>
            </span>

            <span className="mono hidden w-[9ch] text-right text-[0.6875rem] text-[var(--ink-faint)] sm:block">
              {ago(job.createdAt)}
            </span>

            <StatusBadge status={job.status} />
          </Link>
        </li>
      ))}
    </ul>
  );
}
