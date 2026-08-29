"use client";

import Link from "next/link";
import { Fragment, useEffect, useRef, useState } from "react";
import { api, type SearchHit } from "@/lib/api";
import { host, num, padded, path } from "@/lib/format";
import { Empty, ErrorNote, SectionLabel } from "@/components/ui";

const DEBOUNCE_MS = 300;
const LIMIT = 25;

/** Ein abgeschlossener Suchlauf – zusammen mit seinem Begriff, damit nie veraltete Treffer stehen. */
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
    <div className="space-y-10 md:space-y-12">
      <section>
        <p className="label mb-3">Volltextsuche</p>
        <h1 className="display max-w-[18ch] text-[2.25rem] md:text-[2.75rem]">
          Was hat der Schwarm gefunden?
        </h1>
        <p className="mt-4 max-w-[62ch] text-[0.9375rem] leading-relaxed text-[var(--ink-2)]">
          Durchsucht Titel und Textanfang aller gespeicherten Seiten – über alle
          Aufträge hinweg, per Postgres-Volltextsuche im Koordinator. Höchstens{" "}
          {LIMIT} Treffer.
        </p>
      </section>

      <section>
        <div className="card flex items-center gap-3 px-4 focus-within:border-[var(--aqua-deep)] sm:px-5">
          <span className="mono text-[1rem] text-[var(--granat)]" aria-hidden="true">
            ›
          </span>
          <input
            ref={inputRef}
            className="mono w-full bg-transparent py-4 text-[1rem] text-[var(--ink)] outline-none placeholder:text-[var(--ink-4)]"
            type="search"
            placeholder="Suchbegriff …"
            value={query}
            onChange={(e) => setQuery(e.target.value)}
            aria-label="Suchbegriff"
            autoComplete="off"
            spellCheck={false}
          />
          <span
            className="mono shrink-0 whitespace-nowrap text-[0.625rem] uppercase tracking-[0.12em] text-[var(--ink-3)]"
            aria-live="polite"
          >
            {busy
              ? "sucht …"
              : hits
                ? `${num(hits.length)} Treffer`
                : `max ${LIMIT}`}
          </span>
        </div>

        {error && (
          <div className="mt-4">
            <ErrorNote>{error}</ErrorNote>
          </div>
        )}
      </section>

      <section>
        <SectionLabel index="01">Treffer</SectionLabel>
        {term.length === 0 ? (
          <Empty hint="Gesucht wird ab dem ersten Zeichen, 300 ms nach der letzten Eingabe.">
            Tippe einen Begriff.
          </Empty>
        ) : busy ? (
          <Empty>Sucht …</Empty>
        ) : error ? (
          <Empty hint="Die Fehlermeldung des Koordinators steht oben.">
            Suche fehlgeschlagen.
          </Empty>
        ) : hits && hits.length > 0 ? (
          <ol className="card divide-y divide-[var(--rule-soft)]">
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
          <Empty hint="Durchsucht werden nur Titel und der gespeicherte Textanfang, nicht die vollständige Seite.">
            Keine Treffer für „{term}“.
          </Empty>
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
    <li className="group px-4 py-4 transition-colors hover:bg-[var(--sunken)] sm:px-5">
      <div className="flex items-baseline gap-3 sm:gap-4">
        <span className="mono shrink-0 text-[0.625rem] text-[var(--ink-4)] transition-colors group-hover:text-[var(--granat)]">
          {padded(index, 2)}
        </span>
        <div className="min-w-0 flex-1">
          <a
            href={hit.url}
            target="_blank"
            rel="noreferrer noopener"
            className="block truncate text-[1rem] font-medium hover:text-[var(--granat)]"
          >
            {hit.title?.trim() || (
              <span className="italic font-normal text-[var(--ink-4)]">
                ohne Titel
              </span>
            )}
          </a>
          <a
            href={hit.url}
            target="_blank"
            rel="noreferrer noopener"
            className="mono link-underline mt-1 block truncate text-[0.6875rem] text-[var(--ink-2)] hover:text-[var(--granat)]"
          >
            {host(hit.url)}
            <span className="text-[var(--ink-4)]">{path(hit.url)}</span>
          </a>
          {hit.textSnippet && (
            <p className="mt-2 line-clamp-3 max-w-[88ch] text-[0.8125rem] leading-relaxed text-[var(--ink-2)]">
              <Highlight text={hit.textSnippet} term={term} />
            </p>
          )}
        </div>
        <Link
          href={`/jobs/${hit.jobId}`}
          className="mono link-underline hidden shrink-0 text-[0.625rem] uppercase tracking-[0.1em] text-[var(--ink-3)] hover:text-[var(--granat)] sm:block"
          title={`Auftrag ${hit.jobId}`}
        >
          Auftrag ↗
        </Link>
      </div>
    </li>
  );
}

/** Hebt die Suchwörter im Textausschnitt hervor – rein optisch, die Rangfolge bleibt serverseitig. */
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
            className="bg-[var(--aqua-tint)] px-0.5 text-[var(--ink)]"
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
