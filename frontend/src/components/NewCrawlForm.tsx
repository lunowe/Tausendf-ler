"use client";

import { useState } from "react";
import { useRouter } from "next/navigation";
import { api, WEB_OWNER } from "@/lib/api";
import { ErrorNote } from "@/components/ui";

const DEPTHS = [1, 2, 3, 4, 5, 6, 7, 8, 9, 10];

export function NewCrawlForm({ onCreated }: { onCreated?: () => void }) {
  const router = useRouter();
  const [url, setUrl] = useState("");
  const [maxDepth, setMaxDepth] = useState(2);
  const [filters, setFilters] = useState("");
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  async function submit(event: React.FormEvent) {
    event.preventDefault();
    setError(null);
    setBusy(true);
    try {
      const created = await api.createJob({
        url: url.trim(),
        maxDepth,
        filters: filters
          .split(",")
          .map((f) => f.trim())
          .filter(Boolean),
        owner: WEB_OWNER,
      });
      onCreated?.();
      router.push(`/jobs/${created.jobId}`);
    } catch (e) {
      setError(e instanceof Error ? e.message : String(e));
      setBusy(false);
    }
  }

  return (
    <form onSubmit={submit} className="panel p-6">
      <div className="mb-5 flex items-baseline justify-between gap-4">
        <h2 className="display text-[1.75rem]">Neuer Crawl</h2>
        <span className="mono text-[0.625rem] text-[var(--ink-faint)]">
          POST /api/jobs
        </span>
      </div>

      <label className="label mb-2 block" htmlFor="crawl-url">
        Start-URL
      </label>
      <input
        id="crawl-url"
        className="field mb-5"
        type="url"
        required
        placeholder="https://example.com"
        value={url}
        onChange={(e) => setUrl(e.target.value)}
        autoComplete="off"
        spellCheck={false}
      />

      <div className="mb-2 flex items-baseline justify-between">
        <span className="label">Maximale Tiefe</span>
        <span className="mono text-[0.75rem] text-[var(--amber)]">
          {maxDepth}
        </span>
      </div>
      <div
        className="mb-5 flex items-end gap-px border border-[var(--line)] p-1"
        role="group"
        aria-label="Maximale Tiefe"
      >
        {DEPTHS.map((depth) => {
          const on = depth <= maxDepth;
          return (
            <button
              key={depth}
              type="button"
              onClick={() => setMaxDepth(depth)}
              aria-pressed={depth === maxDepth}
              title={`Tiefe ${depth}`}
              className="mono group relative flex-1 pt-3 text-[0.625rem] transition-colors"
              style={{ color: depth === maxDepth ? "var(--amber)" : "var(--ink-faint)" }}
            >
              <span
                className="mb-1 block transition-all"
                style={{
                  height: `${6 + depth * 2.2}px`,
                  background: on ? "var(--amber)" : "var(--line)",
                  opacity: on ? (depth === maxDepth ? 1 : 0.55) : 1,
                }}
              />
              {depth}
            </button>
          );
        })}
      </div>

      <label className="label mb-2 block" htmlFor="crawl-filters">
        Filter · optional, kommasepariert
      </label>
      <input
        id="crawl-filters"
        className="field mb-5"
        type="text"
        placeholder="/blog, /docs"
        value={filters}
        onChange={(e) => setFilters(e.target.value)}
        autoComplete="off"
        spellCheck={false}
      />

      {error && <div className="mb-4">
        <ErrorNote>{error}</ErrorNote>
      </div>}

      <div className="flex items-center justify-between gap-4">
        <span className="mono text-[0.625rem] leading-relaxed text-[var(--ink-faint)]">
          owner = {WEB_OWNER} · Browser
          <br />
          Telegram-Jobs nutzen die Chat-ID
        </span>
        <button type="submit" className="btn btn-primary" disabled={busy}>
          {busy ? "sendet …" : "Crawl starten"}
        </button>
      </div>
    </form>
  );
}
