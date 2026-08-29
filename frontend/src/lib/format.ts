const NUMBER = new Intl.NumberFormat("de-DE");

const TIME = new Intl.DateTimeFormat("de-DE", {
  hour: "2-digit",
  minute: "2-digit",
  second: "2-digit",
});

const DATE_TIME = new Intl.DateTimeFormat("de-DE", {
  day: "2-digit",
  month: "2-digit",
  year: "numeric",
  hour: "2-digit",
  minute: "2-digit",
});

export function num(value: number | null | undefined): string {
  return value === null || value === undefined ? "–" : NUMBER.format(value);
}

/** Pads to a fixed width so counters do not jitter while polling. */
export function padded(value: number, width = 4): string {
  return String(value).padStart(width, "0");
}

export function clock(iso: string | null | undefined): string {
  const date = parse(iso);
  return date ? TIME.format(date) : "–";
}

export function dateTime(iso: string | null | undefined): string {
  const date = parse(iso);
  return date ? DATE_TIME.format(date) : "–";
}

/** "vor 12 s" / "vor 4 min" – short relative label for job lists. */
export function ago(iso: string | null | undefined, now = Date.now()): string {
  const date = parse(iso);
  if (!date) return "–";
  const seconds = Math.max(0, Math.round((now - date.getTime()) / 1000));
  if (seconds < 60) return `vor ${seconds} s`;
  if (seconds < 3600) return `vor ${Math.round(seconds / 60)} min`;
  if (seconds < 86400) return `vor ${Math.round(seconds / 3600)} h`;
  return `vor ${Math.round(seconds / 86400)} d`;
}

/** Wall-clock duration between two timestamps as "1:04 min" / "2,3 s". */
export function duration(from: string | null | undefined, to: string | null | undefined): string {
  const start = parse(from);
  const end = parse(to);
  if (!start || !end) return "–";
  const ms = Math.max(0, end.getTime() - start.getTime());
  if (ms < 10_000) return `${(ms / 1000).toFixed(1).replace(".", ",")} s`;
  const totalSeconds = Math.round(ms / 1000);
  if (totalSeconds < 60) return `${totalSeconds} s`;
  const minutes = Math.floor(totalSeconds / 60);
  const seconds = totalSeconds % 60;
  return `${minutes}:${String(seconds).padStart(2, "0")} min`;
}

/** Host of a URL, without a leading "www.". Falls back to the raw string. */
export function host(url: string): string {
  try {
    return new URL(url).host.replace(/^www\./, "");
  } catch {
    return url;
  }
}

/** Path of a URL including query, "/" for the root. */
export function path(url: string): string {
  try {
    const parsed = new URL(url);
    return `${parsed.pathname}${parsed.search}` || "/";
  } catch {
    return "";
  }
}

function parse(iso: string | null | undefined): Date | null {
  if (iso === null || iso === undefined) return null;
  const date = new Date(iso);
  return Number.isNaN(date.getTime()) ? null : date;
}
