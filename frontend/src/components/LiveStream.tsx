"use client";

import { useEffect, useRef, useState } from "react";
import type { PageResult } from "@/lib/api";
import { clock, host, padded, path } from "@/lib/format";
import { Empty } from "@/components/ui";

/** Hält das DOM bei langen Crawls klein – ältere Segmente bleiben im Speicher. */
const VISIBLE = 150;

/** Muss zur Dauer von `.fresh` in globals.css passen. */
const FRESH_MS = 1400;

const NONE: ReadonlySet<number> = new Set();

/** Gepunktete Rückenlinie, an der die Segmente hängen. */
const SPINE = {
  backgroundImage: "linear-gradient(var(--rule) 50%, transparent 50%)",
  backgroundSize: "1px 6px",
  backgroundPosition: "13px 0",
  backgroundRepeat: "repeat-y",
} as const;

/**
 * Markiert genau die Segmente, die seit dem letzten Tick dazugekommen sind.
 * Die erste Ladung bleibt bewusst ruhig – sonst blinkt beim Öffnen die ganze Liste.
 */
function useFreshSeqs(pages: PageResult[]): ReadonlySet<number> {
  const [fresh, setFresh] = useState<ReadonlySet<number>>(NONE);
  const highest = useRef<number | null>(null);

  useEffect(() => {
    const max = pages.length > 0 ? pages[pages.length - 1].seq : 0;
    const previous = highest.current;
    highest.current = max;
    if (previous === null || max <= previous) return;

    setFresh(new Set(pages.filter((p) => p.seq > previous).map((p) => p.seq)));
    const timer = setTimeout(() => setFresh(NONE), FRESH_MS);
    return () => clearTimeout(timer);
  }, [pages]);

  return fresh;
}

export function LiveStream({
  pages,
  streaming,
}: {
  pages: PageResult[];
  streaming: boolean;
}) {
  const fresh = useFreshSeqs(pages);

  if (pages.length === 0) {
    return (
      <Empty
        hint={
          streaming
            ? "Der Koordinator verteilt die Start-URL, sobald ein Worker nach Arbeit fragt."
            : undefined
        }
      >
        {streaming
          ? "Warte auf das erste Ergebnis eines Workers …"
          : "Für diesen Auftrag sind keine Seiten gespeichert."}
      </Empty>
    );
  }

  const shown = pages.slice(-VISIBLE).reverse();
  const hidden = pages.length - shown.length;

  return (
    <div className="card">
      <ul
        className="scroll-pane max-h-[68vh] overflow-y-auto"
        aria-live="polite"
        aria-relevant="additions"
      >
        {shown.map((page, i) => (
          <Segment
            key={page.seq}
            page={page}
            head={i === 0 && streaming}
            fresh={fresh.has(page.seq)}
          />
        ))}
      </ul>

      {hidden > 0 && (
        <p className="mono border-t border-[var(--rule)] bg-[var(--sunken)] px-4 py-2.5 text-[0.625rem] text-[var(--ink-3)]">
          {hidden} ältere Segmente ausgeblendet · gezeigt werden die letzten{" "}
          {VISIBLE}
        </p>
      )}
    </div>
  );
}

function Segment({
  page,
  head,
  fresh,
}: {
  page: PageResult;
  head: boolean;
  fresh: boolean;
}) {
  return (
    <li className={`relative pl-9 ${fresh ? "fresh" : ""}`} style={SPINE}>
      {fresh && (
        <span
          aria-hidden="true"
          className="fresh-mark absolute inset-y-0 left-0 w-[2px] bg-[var(--aqua-deep)]"
        />
      )}

      {/* Knoten und Bein – das Segment des Tausendfüßlers */}
      <span
        aria-hidden="true"
        className={`absolute left-[9px] top-[19px] h-2.5 w-2.5 rounded-full border ${head ? "pulse" : ""}`}
        style={{
          borderColor: head ? "var(--aqua-deep)" : "var(--rule)",
          background: head ? "var(--aqua-deep)" : "var(--surface)",
        }}
      />
      <span
        aria-hidden="true"
        className="absolute left-[20px] top-[24px] h-px w-[12px] bg-[var(--rule)]"
      />

      <div className="border-b border-[var(--rule-soft)] py-3 pr-4">
        <div className="flex items-baseline gap-3">
          <span className="mono shrink-0 text-[0.625rem] text-[var(--ink-4)]">
            #{padded(page.seq)}
          </span>
          <span
            className="mono shrink-0 border border-[rgba(44,113,137,0.3)] bg-[rgba(138,194,209,0.16)] px-1.5 text-[0.5625rem] uppercase tracking-[0.08em] text-[var(--aqua-deep)]"
            title={`Tiefe ${page.depth}`}
          >
            d{page.depth}
          </span>
          <p className="min-w-0 flex-1 truncate text-[0.9375rem]">
            {page.title?.trim() || (
              <span className="italic text-[var(--ink-4)]">ohne Titel</span>
            )}
          </p>
          <span className="mono shrink-0 text-[0.625rem] text-[var(--ink-4)]">
            {clock(page.crawledAt)}
          </span>
        </div>

        <a
          href={page.url}
          target="_blank"
          rel="noreferrer noopener"
          className="mono link-underline mt-1.5 block truncate text-[0.6875rem] text-[var(--ink-2)] hover:text-[var(--granat)]"
        >
          {host(page.url)}
          <span className="text-[var(--ink-4)]">{path(page.url)}</span>
        </a>

        {page.textSnippet && (
          <p className="mt-1.5 line-clamp-2 max-w-[86ch] text-[0.8125rem] leading-relaxed text-[var(--ink-2)]">
            {page.textSnippet}
          </p>
        )}
      </div>
    </li>
  );
}
