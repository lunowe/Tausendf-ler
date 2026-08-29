"use client";

import Link from "next/link";
import { Fragment, useEffect, useRef, useState } from "react";
import { api, type SearchHit } from "@/lib/api";
import { host, num, padded, path } from "@/lib/format";
import { Empty, ErrorNote, SectionLabel } from "@/components/ui";

const DEBOUNCE_MS = 300;
const LIMIT = 25;

/** A settled search: kept together with its term so stale results are never shown. */
type Outcome =
  | { term: string; hits: SearchHit[]; error?: undefined }
  | { term: string; hits?: undefined; error: string };

export default function SearchPage() {
  const [query, setQuery] = useState("");
  const [outcome, setOutcome] = useState<Outcome | null>(null);
  const inputRef = useRef<HTMLInputElement>(null);

  useEffect(() => {
    inputRef.current?.focus();
  }, []);

  const term = query.trim();
  const current = outcome?.term === term ? outcome : null;
  const hits = current?.hits ?? null;
  const error = current?.error ?? null;
  const busy = term.length > 0 && current === null;

  useEffect(() => {
    if (term.length === 0) return;

    const controller = new AbortController();
    const timer = setTimeout(async () => {
      try {
        const found = await api.search(term, LIMIT, controller.signal);
        if (!controller.signal.aborted) setOutcome({ term, hits: found });
      } catch (e) {
        if (controller.signal.aborted) return;
        setOutcome({ term, error: e instanceof Error ? e.message : String(e) });
      }
    }, DEBOUNCE_MS);

    return () => {
      clearTimeout(timer);
      controller.abort();
    };
  }, [term]);

  return (
    <div className="space-y-12">
      <section className="reveal pt-6">
        <SectionLabel index="01">Volltextsuche</SectionLabel>
        <h1 className="display text-[3.5rem] leading-[0.95] md:text-[4.25rem]">
          Was hat der
          <br />
          <span className="italic text-[var(--amber)]">Schwarm gefunden?</span>
        </h1>
        <p className="mt-5 max-w-[52ch] text-[0.9375rem] leading-relaxed text-[var(--ink-dim)]">
          Durchsucht Titel und Textanfang aller gespeicherten Seiten – über alle
          Aufträge hinweg, per Postgres-Volltextsuche im Koordinator.
        </p>
      </section>

      <section>
        <div className="panel flex items-center gap-3 px-5 py-1">
          <span
            className="mono text-[1.125rem] text-[var(--amber)]"
            aria-hidden="true"
          >
            ›
          </span>
          <input
            ref={inputRef}
            className="mono w-full bg-transparent py-4 text-[1.0625rem] text-[var(--ink)] outline-none placeholder:text-[var(--ink-faint)]"
            type="search"
            placeholder="Suchbegriff …"
            value={query}
            onChange={(e) => setQuery(e.target.value)}
            aria-label="Suchbegriff"
            autoComplete="off"
            spellCheck={false}
          />
          <span
            className="mono shrink-0 text-[0.625rem] uppercase tracking-[0.2em] text-[var(--ink-faint)]"
            aria-live="polite"
          >
            {busy ? "sucht …" : hits ? `${num(hits.length)} Treffer` : `max ${LIMIT}`}
          </span>
        </div>

        {error && (
          <div className="mt-4">
            <ErrorNote>{error}</ErrorNote>
          </div>
        )}
      </section>

      <section>
        <SectionLabel index="02">Treffer</SectionLabel>
        {term.length === 0 ? (
          <Empty>Tippe einen Begriff – gesucht wird ab dem ersten Zeichen.</Empty>
        ) : busy ? (
          <Empty>sucht …</Empty>
        ) : error ? (
          <Empty>Suche fehlgeschlagen.</Empty>
        ) : hits && hits.length > 0 ? (
          <ol className="panel divide-y divide-[var(--line)]">
            {hits.map((hit, i) => (
              <Hit
                key={`${hit.jobId}-${hit.url}-${i}`}
                hit={hit}
                index={i + 1}
                term={term}
              />
            ))}
          </ol>
        ) : (
          <Empty>Keine Seite enthält „{term}“.</Empty>
        )}
      </section>
    </div>
  );
}

function Hit({
  hit,
  index,
  term,
}: {
  hit: SearchHit;
  index: number;
  term: string;
}) {
  return (
    <li className="group px-5 py-4 transition-colors hover:bg-[rgba(255,178,76,0.04)]">
      <div className="flex items-baseline gap-4">
        <span className="mono shrink-0 text-[0.625rem] text-[var(--ink-faint)] group-hover:text-[var(--amber)]">
          {padded(index, 2)}
        </span>
        <div className="min-w-0 flex-1">
          <a
            href={hit.url}
            target="_blank"
            rel="noreferrer noopener"
            className="block truncate text-[1rem] text-[var(--ink)] hover:text-[var(--amber)]"
          >
            {hit.title?.trim() || (
              <span className="italic text-[var(--ink-faint)]">ohne Titel</span>
            )}
          </a>
          <a
            href={hit.url}
            target="_blank"
            rel="noreferrer noopener"
            className="mono link-underline mt-1 block truncate text-[0.6875rem] text-[var(--ink-dim)] hover:text-[var(--amber)]"
          >
            {host(hit.url)}
            <span className="text-[var(--ink-faint)]">{path(hit.url)}</span>
          </a>
          {hit.textSnippet && (
            <p className="mt-2 line-clamp-3 max-w-[86ch] text-[0.8125rem] leading-relaxed text-[var(--ink-dim)]">
              <Highlight text={hit.textSnippet} term={term} />
            </p>
          )}
        </div>
        <Link
          href={`/jobs/${hit.jobId}`}
          className="mono link-underline hidden shrink-0 text-[0.625rem] uppercase tracking-[0.18em] text-[var(--ink-faint)] hover:text-[var(--amber)] sm:block"
          title={hit.jobId}
        >
          Auftrag ↗
        </Link>
      </div>
    </li>
  );
}

/** Marks the search words inside a snippet – purely cosmetic, the ranking stays server-side. */
function Highlight({ text, term }: { text: string; term: string }) {
  const words = term.split(/\s+/).filter((w) => w.length > 1).map(escapeRegExp);
  if (words.length === 0) return <>{text}</>;

  const parts = text.split(new RegExp(`(${words.join("|")})`, "gi"));
  const matcher = new RegExp(`^(${words.join("|")})$`, "i");

  return (
    <>
      {parts.map((part, i) =>
        matcher.test(part) ? (
          <mark
            key={i}
            className="bg-transparent text-[var(--amber)]"
            style={{ boxShadow: "inset 0 -1px 0 var(--amber)" }}
          >
            {part}
          </mark>
        ) : (
          <Fragment key={i}>{part}</Fragment>
        ),
      )}
    </>
  );
}

function escapeRegExp(value: string): string {
  return value.replace(/[.*+?^${}()|[\]\\]/g, "\\$&");
}
