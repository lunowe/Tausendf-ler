/**
 * Thin client for the coordinator REST API (PROTOCOL.md section 1).
 * Everything runs in the browser, so the coordinator must allow this origin via
 * `tausendfuessler.cors-origins`.
 *
 * Authentication: when `NEXT_PUBLIC_API_KEY` is set, every request carries it as
 * `X-Api-Key`. The key is inlined into the client bundle at build time, which is
 * why this frontend is meant for local use only (see README).
 */

import { setUnauthorized } from "@/lib/authState";

export const COORDINATOR_URL = (
  process.env.NEXT_PUBLIC_COORDINATOR_URL ?? "http://localhost:8080"
).replace(/\/+$/, "");

export const API_KEY = (process.env.NEXT_PUBLIC_API_KEY ?? "").trim();

export const UNAUTHORIZED_MESSAGE =
  "API-Key fehlt oder falsch – NEXT_PUBLIC_API_KEY setzen";

/** Owner id used by the web frontend. The bot uses the Telegram chat id; 0 is reserved for the browser. */
export const WEB_OWNER = 0;

export type JobStatus =
  | "PENDING"
  | "RUNNING"
  | "PAUSED"
  | "COMPLETED"
  | "ABORTED"
  | "FAILED";

export const TERMINAL_STATUS: readonly JobStatus[] = [
  "COMPLETED",
  "ABORTED",
  "FAILED",
];

export function isTerminal(status: JobStatus | undefined): boolean {
  return status !== undefined && TERMINAL_STATUS.includes(status);
}

export interface JobSummary {
  jobId: string;
  url: string;
  status: JobStatus;
  pagesVisited: number;
  createdAt: string;
}

export interface JobDetail {
  jobId: string;
  url: string;
  maxDepth: number;
  currentDepth: number;
  status: JobStatus;
  pagesVisited: number;
  linksFound: number;
  errors: number;
  startedAt: string | null;
  finishedAt: string | null;
  createdAt: string;
}

export interface PageResult {
  seq: number;
  url: string;
  title: string | null;
  textSnippet: string | null;
  depth: number;
  crawledAt: string;
}

export interface SearchHit {
  url: string;
  title: string | null;
  textSnippet: string | null;
  jobId: string;
}

export interface Stats {
  totalJobs: number;
  activeJobs: number;
  totalPagesCrawled: number;
  topDomains: Record<string, number>;
}

export interface Health {
  status: string;
  time: string;
  startupSeconds?: number;
}

/** One connected worker (`GET /api/workers`), sorted by `connectedAt` on the server. */
export interface WorkerInfo {
  workerId: string;
  threads: number;
  inFlight: number;
  connectedAt: string;
}

export interface CreateJobRequest {
  url: string;
  maxDepth: number;
  filters: string[];
  owner: number;
}

export interface JobCreated {
  jobId: string;
  status: JobStatus;
  message: string;
}

/** Error carrying the HTTP status and the coordinator's `{error: "..."}` body. */
export class ApiError extends Error {
  readonly status: number;

  constructor(status: number, message: string) {
    super(message);
    this.name = "ApiError";
    this.status = status;
  }
}

async function request<T>(path: string, init?: RequestInit): Promise<T> {
  const headers: Record<string, string> = {
    ...(init?.headers as Record<string, string> | undefined),
  };
  if (init?.body) headers["Content-Type"] = "application/json";
  if (API_KEY) headers["X-Api-Key"] = API_KEY;

  let response: Response;
  try {
    response = await fetch(`${COORDINATOR_URL}${path}`, {
      ...init,
      cache: "no-store",
      headers,
    });
  } catch {
    throw new ApiError(0, `Koordinator unter ${COORDINATOR_URL} nicht erreichbar`);
  }

  if (response.status === 401) {
    setUnauthorized(true);
    throw new ApiError(401, UNAUTHORIZED_MESSAGE);
  }
  setUnauthorized(false);

  if (!response.ok) {
    throw new ApiError(response.status, await errorMessage(response));
  }
  if (response.status === 204) {
    return undefined as T;
  }
  return (await response.json()) as T;
}

async function errorMessage(response: Response): Promise<string> {
  try {
    const body = (await response.json()) as { error?: string };
    if (body?.error) return body.error;
  } catch {
    /* body was not the documented {error} shape */
  }
  return `HTTP ${response.status}`;
}

export const api = {
  health: (signal?: AbortSignal) => request<Health>("/api/health", { signal }),

  stats: (signal?: AbortSignal) => request<Stats>("/api/stats", { signal }),

  workers: (signal?: AbortSignal) =>
    request<WorkerInfo[]>("/api/workers", { signal }),

  listJobs: (owner: number, signal?: AbortSignal) =>
    request<JobSummary[]>(`/api/jobs?owner=${owner}`, { signal }),

  job: (id: string, signal?: AbortSignal) =>
    request<JobDetail>(`/api/jobs/${encodeURIComponent(id)}`, { signal }),

  results: (id: string, afterSeq: number, signal?: AbortSignal) =>
    request<PageResult[]>(
      `/api/jobs/${encodeURIComponent(id)}/results?afterSeq=${afterSeq}`,
      { signal },
    ),

  createJob: (body: CreateJobRequest) =>
    request<JobCreated>("/api/jobs", { method: "POST", body: JSON.stringify(body) }),

  signal: (id: string, action: "pause" | "resume" | "abort") =>
    request<void>(`/api/jobs/${encodeURIComponent(id)}/${action}`, { method: "POST" }),

  search: (q: string, limit: number, signal?: AbortSignal) =>
    request<SearchHit[]>(
      `/api/search?q=${encodeURIComponent(q)}&limit=${limit}`,
      { signal },
    ),
};
