import { COORDINATOR_URL } from "@/lib/api";

export function SiteFooter() {
  return (
    <footer className="rule-top bg-[var(--surface)]">
      <div className="mx-auto flex w-full max-w-[1160px] flex-wrap items-baseline gap-x-8 gap-y-2 px-4 py-5 sm:px-6 md:px-10">
        <span className="label">EVA · Universität Leipzig · SS26</span>
        <span className="label">
          Bot ↔ Koordinator: REST · Koordinator ↔ Worker: TCP
        </span>
        <span className="label sm:ml-auto">API: {COORDINATOR_URL}</span>
      </div>
    </footer>
  );
}
