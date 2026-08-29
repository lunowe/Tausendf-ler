"use client";

import Link from "next/link";
import { usePathname } from "next/navigation";
import { api, COORDINATOR_URL } from "@/lib/api";
import { usePolling } from "@/lib/usePolling";
import { Millipede } from "@/components/Millipede";
import { Dot } from "@/components/ui";

const NAV = [
  { href: "/", label: "Leitstand" },
  { href: "/search", label: "Suche" },
] as const;

export function SiteHeader() {
  const pathname = usePathname();

  return (
    <header className="sticky top-0 z-50 border-b border-[var(--rule)] bg-[rgba(250,249,246,0.9)] backdrop-blur-md">
      <div className="mx-auto flex w-full max-w-[1160px] items-center gap-3 px-4 py-3 sm:gap-5 sm:px-6 md:px-10">
        <Link href="/" className="group flex min-w-0 items-center gap-3">
          <Millipede className="hidden h-6 w-[62px] shrink-0 text-[var(--granat)] transition-opacity group-hover:opacity-60 sm:block" />
          <span className="flex min-w-0 flex-col leading-none">
            <span className="display truncate text-[1.15rem] md:text-[1.375rem]">
              Tausendfüßler
            </span>
            <span className="label mt-1 hidden text-[0.625rem] md:block">
              Verteilter Crawler · Leitstand
            </span>
          </span>
        </Link>

        <nav aria-label="Hauptnavigation" className="ml-auto flex items-center gap-1">
          {NAV.map((item) => {
            const active =
              item.href === "/"
                ? pathname === "/" || pathname.startsWith("/jobs")
                : pathname.startsWith(item.href);
            return (
              <Link
                key={item.href}
                href={item.href}
                aria-current={active ? "page" : undefined}
                className={`mono border px-2.5 py-1.5 text-[0.6875rem] uppercase tracking-[0.1em] transition-colors sm:px-3 ${
                  active
                    ? "border-[rgba(176,47,44,0.35)] bg-[var(--granat-tint)] text-[var(--granat)]"
                    : "border-transparent text-[var(--ink-2)] hover:border-[var(--rule)] hover:text-[var(--ink)]"
                }`}
              >
                {item.label}
              </Link>
            );
          })}
        </nav>

        <HealthPill />
      </div>
    </header>
  );
}

function HealthPill() {
  const { data, error, pending } = usePolling((signal) => api.health(signal), 5000);
  const up = !error && data?.status === "UP";
  const color = pending
    ? "var(--ink-4)"
    : up
      ? "var(--st-COMPLETED)"
      : "var(--granat)";
  const text = pending ? "prüfe …" : up ? "Koordinator online" : "Koordinator offline";

  return (
    <span
      className="flex shrink-0 items-center gap-2 border border-[var(--rule)] bg-[var(--surface)] px-2 py-1.5 md:px-3"
      title={`${text} · ${COORDINATOR_URL}`}
    >
      <Dot color={color} live={up} />
      <span className="sr-only">{text}</span>
      <span
        aria-hidden="true"
        className="mono hidden text-[0.625rem] uppercase tracking-[0.12em] text-[var(--ink-2)] lg:inline"
      >
        {up ? "Koordinator" : text}
      </span>
      {up && data?.startupSeconds !== undefined && (
        <span
          aria-hidden="true"
          className="mono hidden text-[0.625rem] text-[var(--ink-3)] lg:inline"
        >
          {data.startupSeconds.toFixed(1).replace(".", ",")} s
        </span>
      )}
    </span>
  );
}
