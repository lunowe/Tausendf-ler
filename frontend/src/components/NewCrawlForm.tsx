"use client";

import { useState } from "react";
import { useRouter } from "next/navigation";
import { api, WEB_OWNER } from "@/lib/api";
import { ErrorNote } from "@/components/ui";

const DEPTHS = [1, 2, 3, 4, 5, 6, 7, 8, 9, 10];

/** Wortlaut wie im Bot (CrawlCommandHandler). */
const INVALID_URL = "Ungültige URL – bitte mit http:// oder https:// beginnen.";

/** Ergänzt ein fehlendes Schema, damit „example.com“ nicht am Formular scheitert. */
function normalize(raw: string): string {
  const trimmed = raw.trim();
  if (trimmed === "" || /^https?:\/\//i.test(trimmed)) return trimmed;
  return `https://${trimmed}`;
}

function isValid(url: string): boolean {
  try {
    const parsed = new URL(url);
    return parsed.protocol === "http:" || parsed.protocol === "https:";
  } catch {
    return false;
  }
}

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

    const target = normalize(url);
    if (!isValid(target)) {
      setError(INVALID_URL);
      return;
    }
    setUrl(target);
    setBusy(true);
    try {
      const created = await api.createJob({
        url: target,
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
    <form onSubmit={submit} className="card p-5 md:p-6" noValidate>
      <div className="mb-5 flex items-baseline justify-between gap-4 border-b border-[var(--rule-soft)] pb-4">
        <h3 className="display text-[1.375rem]">Neuer Crawl</h3>
        <span className="mono text-[0.625rem] text-[var(--ink-3)]">
          POST /api/jobs
        </span>
      </div>

      <label className="label mb-2 block" htmlFor="crawl-url">
        Start-URL
      </label>
      <input
        id="crawl-url"
        className="field"
        type="text"
        inputMode="url"
        required
        placeholder="https://example.com"
        value={url}
        onChange={(e) => setUrl(e.target.value)}
        aria-describedby="crawl-url-hint"
        autoComplete="off"
        spellCheck={false}
      />
      <p id="crawl-url-hint" className="mono mt-2 text-[0.625rem] text-[var(--ink-3)]">
        Ohne Schema wird https:// ergänzt. Tiefe 0 ist die Start-URL selbst.
      </p>

      <fieldset className="mt-5">
        <div className="mb-2 flex items-baseline justify-between gap-4">
          <legend className="label">Maximale Tiefe</legend>
          <span className="mono text-[0.75rem] font-medium text-[var(--aqua-deep)]">
            {maxDepth}
          </span>
        </div>
        <div className="flex items-end gap-px border border-[var(--rule)] p-1">
          {DEPTHS.map((depth) => {
            const filled = depth <= maxDepth;
            const current = depth === maxDepth;
            return (
              <label key={depth} className="relative flex-1">
                <input
                  className="peer sr-only"
                  type="radio"
                  name="maxDepth"
                  value={depth}
                  checked={current}
                  onChange={() => setMaxDepth(depth)}
                />
                <span className="flex cursor-pointer flex-col items-center gap-1 pb-0.5 pt-2 peer-focus-visible:outline peer-focus-visible:outline-2 peer-focus-visible:outline-offset-2 peer-focus-visible:outline-[var(--focus)]">
                  <span
                    aria-hidden="true"
                    className="w-full transition-[height,background-color] duration-200"
                    style={{
                      height: `${6 + depth * 2.4}px`,
                      background: filled
                        ? current
                          ? "var(--aqua-deep)"
                          : "var(--aquamarin)"
                        : "var(--rule-soft)",
                    }}
                  />
                  <span
                    className="mono text-[0.625rem]"
                    style={{
                      color: current ? "var(--aqua-deep)" : "var(--ink-4)",
                      fontWeight: current ? 500 : 400,
                    }}
                  >
                    {depth}
                  </span>
                </span>
              </label>
            );
          })}
        </div>
      </fieldset>

      <label className="label mb-2 mt-5 block" htmlFor="crawl-filters">
        Filter · optional
      </label>
      <input
        id="crawl-filters"
        className="field"
        type="text"
        placeholder="/blog, /docs"
        value={filters}
        onChange={(e) => setFilters(e.target.value)}
        aria-describedby="crawl-filters-hint"
        autoComplete="off"
        spellCheck={false}
      />
      <p
        id="crawl-filters-hint"
        className="mono mt-2 text-[0.625rem] leading-relaxed text-[var(--ink-3)]"
      >
        Kommasepariert. Verfolgt werden nur Links, die einen dieser Textbausteine
        enthalten.
      </p>

      {error && (
        <div className="mt-5">
          <ErrorNote>{error}</ErrorNote>
        </div>
      )}

      <div className="mt-6 flex flex-wrap items-center justify-between gap-4 border-t border-[var(--rule-soft)] pt-5">
        <span className="mono text-[0.625rem] leading-relaxed text-[var(--ink-3)]">
          owner = {WEB_OWNER} · Browser
          <br />
          Aufträge aus Telegram laufen unter der Chat-ID.
        </span>
        <button
          type="submit"
          className="btn btn-primary"
          disabled={busy || url.trim() === ""}
        >
          {busy ? "Sendet …" : "Crawl starten"}
        </button>
      </div>
    </form>
  );
}
