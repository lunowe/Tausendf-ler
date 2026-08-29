import { COORDINATOR_URL } from "@/lib/api";

export function SiteFooter() {
  return (
    <footer className="border-t border-[var(--line)] bg-[rgba(4,9,15,0.6)]">
      <div className="mx-auto flex w-full max-w-[1180px] flex-wrap items-center gap-x-8 gap-y-2 px-6 py-5 md:px-10">
        <span className="label">
          EVA · Uni Leipzig · SS26
        </span>
        <span className="label">
          Bot ↔ Koordinator: REST · Koordinator ↔ Worker: TCP
        </span>
        <span className="label ml-auto">API: {COORDINATOR_URL}</span>
      </div>
    </footer>
  );
}
