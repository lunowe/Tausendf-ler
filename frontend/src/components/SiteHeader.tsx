"use client";

import Link from "next/link";
import { usePathname } from "next/navigation";
import { api, COORDINATOR_URL } from "@/lib/api";
import { usePolling } from "@/lib/usePolling";
import { Millipede } from "@/components/Millipede";

const NAV = [
  { href: "/", label: "Leitstand" },
  { href: "/search", label: "Suche" },
] as const;

export function SiteHeader() {
  const pathname = usePathname();

  return (
    <header className="sticky top-0 z-50 border-b border-[var(--line)] bg-[rgba(4,9,15,0.82)] backdrop-blur-md">
      <div className="mx-auto flex w-full max-w-[1180px] items-center gap-6 px-6 py-3 md:px-10">
        <Link href="/" className="group flex items-center gap-3">
          <Millipede className="h-6 w-[68px] text-[var(--amber)] transition-opacity group-hover:opacity-70" />
          <span className="flex flex-col leading-none">
            <span className="display text-[1.4rem] tracking-tight">
              Tausendfüßler
            </span>
            <span className="label mt-1 text-[0.55rem]">
              Verteilter Crawler · Leitstand
            </span>
          </span>
        </Link>

        <nav className="ml-auto flex items-center gap-1">
          {NAV.map((item) => {
            const active =
              item.href === "/"
                ? pathname === "/" || pathname.startsWith("/jobs")
                : pathname.startsWith(item.href);
            return (
              <Link
                key={item.href}
                href={item.href}
                className={`mono border px-3 py-1.5 text-[0.6875rem] uppercase tracking-[0.18em] transition-colors ${
                  active
                    ? "border-[var(--amber)] bg-[var(--amber-soft)] text-[var(--amber)]"
                    : "border-transparent text-[var(--ink-dim)] hover:border-[var(--line)] hover:text-[var(--ink)]"
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
  const { data, error } = usePolling((signal) => api.health(signal), 5000);
  const up = !error && data?.status === "UP";

  return (
    <div
      className="hidden items-center gap-2 border border-[var(--line)] px-3 py-1.5 lg:flex"
      title={COORDINATOR_URL}
    >
      <span
        className={`inline-block h-1.5 w-1.5 rounded-full ${up ? "pulse" : ""}`}
        style={{
          background: up ? "var(--mint)" : "var(--coral)",
          color: up ? "var(--mint)" : "var(--coral)",
        }}
      />
      <span className="mono text-[0.625rem] uppercase tracking-[0.2em] text-[var(--ink-dim)]">
        {up ? "Koordinator" : "Offline"}
      </span>
      {up && data?.startupSeconds !== undefined && (
        <span className="mono text-[0.625rem] text-[var(--ink-faint)]">
          {data.startupSeconds.toFixed(1)} s
        </span>
      )}
    </div>
  );
}
