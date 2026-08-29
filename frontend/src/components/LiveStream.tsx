"use client";

import type { PageResult } from "@/lib/api";
import { clock, host, padded, path } from "@/lib/format";
import { Empty } from "@/components/ui";

/** Keeps the DOM bounded on long crawls – the older segments stay in memory. */
const VISIBLE = 200;

export function LiveStream({
  pages,
  streaming,
}: {
  pages: PageResult[];
  streaming: boolean;
}) {
  if (pages.length === 0) {
    return (
      <Empty>
        {streaming
          ? "warte auf das erste Ergebnis eines Workers …"
          : "keine Seiten gespeichert"}
      </Empty>
    );
  }

  const shown = pages.slice(-VISIBLE).reverse();
  const hidden = pages.length - shown.length;

  return (
    <div className="relative">
      {/* the spine the segments hang on */}
      <div
        className="absolute bottom-4 left-[13px] top-2 w-px"
        style={{
          backgroundImage:
            "linear-gradient(var(--line) 50%, transparent 50%)",
          backgroundSize: "1px 7px",
        }}
        aria-hidden="true"
      />

      <ul className="space-y-px">
        {shown.map((page, i) => (
          <Segment key={page.seq} page={page} head={i === 0 && streaming} />
        ))}
      </ul>

      {hidden > 0 && (
        <p className="mono mt-4 pl-9 text-[0.625rem] text-[var(--ink-faint)]">
          … {hidden} ältere Segmente ausgeblendet
        </p>
      )}
    </div>
  );
}

function Segment({ page, head }: { page: PageResult; head: boolean }) {
  return (
    <li className="segment-in relative pl-9">
      {/* node + legs */}
      <span
        className={`absolute left-[8px] top-[18px] h-2.5 w-2.5 rounded-full border ${head ? "pulse" : ""}`}
        style={{
          borderColor: head ? "var(--amber)" : "var(--line)",
          background: head ? "var(--amber)" : "var(--ground)",
          color: "var(--amber)",
        }}
        aria-hidden="true"
      />
      <span
        className="absolute left-[19px] top-[23px] h-px w-[13px]"
        style={{ background: "var(--line)" }}
        aria-hidden="true"
      />

      <div className="border-b border-[var(--line-soft)] py-3 pr-1 transition-colors hover:bg-[rgba(127,216,240,0.04)]">
        <div className="flex items-baseline gap-3">
          <span className="mono shrink-0 text-[0.625rem] text-[var(--ink-faint)]">
            #{padded(page.seq)}
          </span>
          <span
            className="mono shrink-0 border px-1.5 text-[0.5625rem] uppercase tracking-[0.12em]"
            style={{ borderColor: "var(--line)", color: "var(--cyan)" }}
            title="Tiefe"
          >
            d{page.depth}
          </span>
          <p className="min-w-0 flex-1 truncate text-[0.9375rem] text-[var(--ink)]">
            {page.title?.trim() || (
              <span className="text-[var(--ink-faint)] italic">ohne Titel</span>
            )}
          </p>
          <span className="mono shrink-0 text-[0.625rem] text-[var(--ink-faint)]">
            {clock(page.crawledAt)}
          </span>
        </div>

        <a
          href={page.url}
          target="_blank"
          rel="noreferrer noopener"
          className="mono link-underline mt-1.5 block truncate text-[0.6875rem] text-[var(--ink-dim)] hover:text-[var(--amber)]"
        >
          {host(page.url)}
          <span className="text-[var(--ink-faint)]">{path(page.url)}</span>
        </a>

        {page.textSnippet && (
          <p className="mt-1.5 line-clamp-2 max-w-[80ch] text-[0.8125rem] leading-relaxed text-[var(--ink-dim)]">
            {page.textSnippet}
          </p>
        )}
      </div>
    </li>
  );
}
