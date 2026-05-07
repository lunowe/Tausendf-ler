# Krakensuche — Implementation Plan

A distributed mini search engine built as the EVA module project at Universität
Leipzig (SS26). One **Master** (Spring Boot) coordinates multiple **Worker**
processes over TCP sockets. Workers crawl pages in parallel, the Master builds
an in-memory inverted index, and a **Client** drives the system over REST and
runs load simulations.

This document is the build spec. Work through it phase by phase. Acceptance
criteria at the end of each phase tell you when to move on.

---

## 1. Locked decisions

These are not up for discussion — pick exactly these versions and tools so the
project assembles cleanly. Do not introduce alternatives without a strong
reason.

| Concern | Decision |
|---|---|
| Language | Java 21 (use modern features: records, pattern matching, `var`, text blocks) |
| Build | Maven (multi-module, parent POM) |
| Framework (Master) | Spring Boot 3.3.x with `spring-boot-starter-web`, `spring-boot-starter-data-jpa` |
| Framework (Worker, Client) | Plain Java, no Spring |
| Persistence | H2 in-memory (`org.h2.Driver`, `jdbc:h2:mem:krakensuche`) |
| HTTP client (worker) | `java.net.http.HttpClient` (JDK built-in) |
| HTML parsing | `org.jsoup:jsoup:1.17.2` |
| JSON | `com.fasterxml.jackson.core:jackson-databind` (already pulled in by Spring) |
| Logging | SLF4J + Logback (Spring Boot default) |
| Tests | JUnit 5, AssertJ, Spring Boot Test |
| Java code style | Records for DTOs and protocol messages, constructor injection in Spring components, `final` fields where possible, no Lombok |
| Package root | `de.uni_leipzig.eva.krakensuche` |
| Default ports | Master REST `8080`, Master socket server `9090` |

**Do not add Lombok, Kotlin, Reactor, WebFlux, gRPC, Kafka, Redis, or any
distributed-systems library.** This project is supposed to demonstrate the raw
primitives.

---

## 2. Module layout

Maven multi-module project. Parent `pom.xml` defines versions and plugins;
each module has its own `pom.xml`.

```
krakensuche/
├── pom.xml                              # parent
├── krakensuche-common/                  # shared DTOs, socket message types
│   └── src/main/java/.../common/
│       ├── protocol/                    # SocketMessage and subtypes
│       ├── dto/                         # REST DTOs
│       └── util/
├── krakensuche-master/                  # Spring Boot
│   └── src/main/java/.../master/
│       ├── KrakenmasterApplication.java
│       ├── api/                         # @RestController classes
│       ├── service/                     # @Service classes
│       ├── socket/                      # SocketServer, WorkerConnection
│       ├── domain/                      # JPA entities
│       ├── repository/                  # Spring Data repos
│       ├── index/                       # InvertedIndex, Tokenizer, TfIdfScorer
│       └── config/                      # @Configuration
├── krakensuche-worker/                  # Plain Java
│   └── src/main/java/.../worker/
│       ├── WorkerApplication.java
│       ├── socket/                      # MasterConnection
│       ├── crawler/                     # PageFetcher, HtmlExtractor
│       └── pool/                        # CrawlExecutor
├── krakensuche-client/                  # Plain Java
│   └── src/main/java/.../client/
│       ├── ClientApplication.java
│       ├── rest/                        # RestApiClient
│       └── loadsim/                     # LoadSimulator + scenarios
└── krakensuche-testweb/                 # OPTIONAL Phase 9 — synthetic site
```

Each module's `groupId` is `de.uni-leipzig.eva`, `artifactId` is the directory
name, version `0.1.0-SNAPSHOT` until first release.

---

## 3. Domain model

JPA entities live in `krakensuche-master`. Use `jakarta.persistence`
annotations.

### `CrawlJob`

| Field | Type | Notes |
|---|---|---|
| `id` | `String` (UUID) | PK |
| `seedUrls` | `List<String>` | `@ElementCollection` |
| `allowedDomains` | `List<String>` | `@ElementCollection`, empty = no restriction |
| `maxDepth` | `int` | default 3 |
| `maxPages` | `int` | default 100 |
| `status` | `JobStatus` enum | see below |
| `pagesCrawled` | `int` | atomically incremented |
| `errorCount` | `int` | atomically incremented |
| `createdAt`, `updatedAt` | `Instant` | |

`JobStatus` enum: `PENDING`, `RUNNING`, `PAUSED`, `COMPLETED`, `FAILED`,
`CANCELLED`.

### `Document`

| Field | Type | Notes |
|---|---|---|
| `id` | `String` (UUID) | PK |
| `jobId` | `String` | FK to CrawlJob |
| `url` | `String` | unique per job (composite uniqueness) |
| `title` | `String` | nullable |
| `plainText` | `String` (`@Lob`) | extracted text content |
| `outgoingLinks` | `List<String>` | `@ElementCollection` |
| `httpStatus` | `int` | |
| `fetchedAt` | `Instant` | |

### `WorkerInfo` (NOT a JPA entity — kept in-memory)

A plain record/class in `WorkerRegistry`. Contains `workerId`, `remoteHost`,
`threadCount`, `activeTasks`, `lastHeartbeat`, `connection` reference. Don't
persist — workers come and go.

### Inverted index (NOT a JPA entity — kept in-memory)

```java
public final class InvertedIndex {
    private final ConcurrentHashMap<String, ConcurrentHashMap<String, PostingEntry>> index = new ConcurrentHashMap<>();
    // outer key = term, inner key = documentId
}

public record PostingEntry(String documentId, int termFrequency, List<Integer> positions) {}
```

This is the focal point for the thread-safety NFA. Use
`index.compute(term, ...)` and `inner.compute(docId, ...)` to atomically merge
posting entries when multiple documents are indexed concurrently.

---

## 4. Socket protocol (the wire spec)

This is the Master ↔ Worker protocol. **Line-delimited JSON over a single
persistent TCP connection** (one line = one message, terminated by `\n`).
Define every message as a Java record in `krakensuche-common` and serialize
with Jackson.

### Message envelope

Every message is a JSON object with a `type` discriminator. Use Jackson's
polymorphic deserialization with `@JsonTypeInfo(property = "type")` and
`@JsonSubTypes`.

```java
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
@JsonSubTypes({
    @JsonSubTypes.Type(value = RegisterMessage.class, name = "REGISTER"),
    @JsonSubTypes.Type(value = HeartbeatMessage.class, name = "HEARTBEAT"),
    @JsonSubTypes.Type(value = CrawlResultMessage.class, name = "CRAWL_RESULT"),
    @JsonSubTypes.Type(value = CrawlErrorMessage.class, name = "CRAWL_ERROR"),
    @JsonSubTypes.Type(value = CrawlTaskMessage.class, name = "CRAWL_TASK"),
    @JsonSubTypes.Type(value = StopJobMessage.class, name = "STOP_JOB"),
    @JsonSubTypes.Type(value = ShutdownMessage.class, name = "SHUTDOWN"),
})
public sealed interface SocketMessage permits ... { }
```

### Worker → Master

| Type | Fields | When |
|---|---|---|
| `REGISTER` | `workerId`, `threadCount` | First message after TCP connect |
| `HEARTBEAT` | `workerId`, `activeTasks` | Every 5 seconds |
| `CRAWL_RESULT` | `taskId`, `jobId`, `url`, `httpStatus`, `title`, `text`, `outgoingLinks` | After each successful crawl |
| `CRAWL_ERROR` | `taskId`, `jobId`, `url`, `error` | On HTTP/parse failure |

### Master → Worker

| Type | Fields | When |
|---|---|---|
| `CRAWL_TASK` | `taskId`, `jobId`, `url`, `currentDepth`, `maxDepth` | When dispatching work |
| `STOP_JOB` | `jobId` | On job cancel/pause; worker drops queued tasks for that job |
| `SHUTDOWN` | (none) | Graceful shutdown |

### Connection lifecycle

1. Worker opens TCP connection to `master:9090`.
2. Worker sends `REGISTER`. Master adds it to `WorkerRegistry`.
3. Worker starts heartbeat thread (5 s interval).
4. Master may send `CRAWL_TASK` at any time.
5. Worker processes tasks in its thread pool. On completion, sends
   `CRAWL_RESULT` or `CRAWL_ERROR`.
6. If Master doesn't receive a heartbeat for 15 s, it considers the worker
   dead and removes it. Any in-flight tasks for that worker are re-queued.
7. On worker shutdown: worker closes its socket. Master detects EOF, removes
   worker.

### Framing detail

Use `BufferedReader.readLine()` on the read side and `BufferedWriter` +
explicit `\n` + `flush()` on the write side. **Synchronize all writes** to a
given socket (`synchronized` on the writer or use a single-thread executor
per connection) — concurrent writes corrupt the line-delimited stream.

---

## 5. REST API (Master)

Base path: `/api`. All requests/responses JSON. Errors return RFC 7807
problem+json via Spring's `ProblemDetail`.

### Jobs

#### `POST /api/jobs`

Create a new crawl job.

```json
// Request
{
  "seedUrls": ["http://localhost:8081/index.html"],
  "maxDepth": 3,
  "maxPages": 100,
  "allowedDomains": ["localhost"]
}

// Response 201 Created
{
  "id": "550e8400-e29b-41d4-a716-446655440000",
  "status": "PENDING",
  "seedUrls": [...],
  "maxDepth": 3,
  "maxPages": 100,
  "allowedDomains": [...],
  "pagesCrawled": 0,
  "errorCount": 0,
  "createdAt": "2026-04-30T10:15:00Z",
  "updatedAt": "2026-04-30T10:15:00Z"
}
```

Side effect: job moves to `RUNNING` as soon as a worker is available. The
`TaskDispatcher` picks it up.

#### `GET /api/jobs/{id}`

Returns the same shape as above, plus a derived `queueSize` (URLs waiting to
be dispatched).

#### `GET /api/jobs`

Returns all jobs (paginated optional, not required for the prototype).

#### `DELETE /api/jobs/{id}`

204 No Content. Sends `STOP_JOB` to all workers, marks job `CANCELLED`,
removes from queue.

#### `POST /api/jobs/{id}/pause`

200 with updated job. Status transitions `RUNNING` → `PAUSED`. Dispatcher
stops handing out new tasks for this job; in-flight tasks complete normally.

#### `POST /api/jobs/{id}/resume`

200 with updated job. Status transitions `PAUSED` → `RUNNING`.

### Search

#### `GET /api/search?q=<query>&limit=10`

```json
// Response 200
[
  {
    "documentId": "...",
    "url": "...",
    "title": "...",
    "score": 12.34,
    "snippet": "...some matching context with <em>highlighted</em> terms..."
  }
]
```

Empty array if nothing matches. `q` is required (400 if missing). `limit`
defaults to 10, max 100.

#### `GET /api/documents/{id}`

Returns the full document record.

### Stats

#### `GET /api/stats`

```json
{
  "totalDocuments": 142,
  "totalTerms": 8421,
  "activeWorkers": 2,
  "totalJobsCompleted": 5,
  "avgSearchLatencyMs": 38.2,
  "uptime": "PT1H23M"
}
```

#### `GET /api/stats/workers`

```json
[
  {
    "workerId": "w-abc123",
    "remoteHost": "127.0.0.1:54321",
    "threadCount": 8,
    "activeTasks": 3,
    "lastHeartbeat": "2026-04-30T10:20:15Z"
  }
]
```

---

## 6. Component breakdown

### `krakensuche-master`

| Class | Stereotype | Responsibility |
|---|---|---|
| `KrakenmasterApplication` | `@SpringBootApplication` | Entry point |
| `JobController` | `@RestController` | Job REST endpoints |
| `SearchController` | `@RestController` | Search + document endpoints |
| `StatsController` | `@RestController` | Stats endpoints |
| `CrawlJobService` | `@Service` | Job lifecycle, URL dedup per job (`Set<String> seenUrls`), URL queue per job |
| `IndexService` | `@Service` | Tokenize + index incoming docs (parallel via `ExecutorService`) |
| `SearchService` | `@Service` | TF-IDF scoring, snippet extraction (parallel scoring for large indexes) |
| `StatsService` | `@Service` | Aggregates metrics |
| `Tokenizer` | plain class | Lowercase, strip punctuation, split on whitespace, drop stop words |
| `TfIdfScorer` | plain class | Score(doc, query) computation |
| `InvertedIndex` | `@Component` | The `ConcurrentHashMap` structure |
| `WorkerRegistry` | `@Component` | Active worker map, heartbeat tracking, dead-worker eviction |
| `SocketServer` | `@Component` | `@PostConstruct` starts ServerSocket on port 9090; one accept loop, one thread per connection |
| `WorkerConnection` | plain class | Wraps a single socket + reader thread + write lock |
| `TaskDispatcher` | `@Component` | Scheduled / event-driven; pulls URLs from job queues, picks idle worker, sends `CRAWL_TASK` |

### `krakensuche-worker`

| Class | Responsibility |
|---|---|
| `WorkerApplication` | `main()`, parses args (`--master-host`, `--master-port`, `--threads`), starts everything |
| `MasterConnection` | TCP socket to master, reader thread, heartbeat scheduler, write lock |
| `CrawlExecutor` | `ExecutorService` with N threads (default `Runtime.getRuntime().availableProcessors()`) |
| `PageFetcher` | Uses `HttpClient` to GET URLs with timeout, user-agent, follow redirects |
| `HtmlExtractor` | jsoup-based extraction: title, plain text, outgoing links (resolve relative URLs) |

Each `CRAWL_TASK` message is submitted to `CrawlExecutor`. The task fetches +
parses + sends back `CRAWL_RESULT` or `CRAWL_ERROR`. Submit count is tracked
for the heartbeat's `activeTasks` field.

### `krakensuche-client`

| Class | Responsibility |
|---|---|
| `ClientApplication` | CLI entry; subcommands: `crawl`, `search`, `stats`, `loadsim` |
| `RestApiClient` | Thin wrapper over `HttpClient`, JSON via Jackson |
| `LoadSimulator` | Runs scenarios from §8 NFA verification |

---

## 7. Implementation phases

Each phase has a goal, files to create, and acceptance criteria. Don't move on
until the criteria are met. Run `mvn verify` between phases.

### Phase 1 — Bootstrap

**Goal:** Multi-module skeleton compiles and starts the empty Master.

**Tasks:**
- Create parent `pom.xml` with `<modules>` for all four modules.
- Pin `<java.version>21</java.version>` and Spring Boot 3.3.x in
  `<dependencyManagement>`.
- Create empty `KrakenmasterApplication`, `WorkerApplication`,
  `ClientApplication` with just `main(String[] args)`.
- Add `application.yml` for the Master:
  ```yaml
  spring:
    datasource:
      url: jdbc:h2:mem:krakensuche
    h2:
      console:
        enabled: true
    jpa:
      hibernate:
        ddl-auto: create-drop
      show-sql: false
  server:
    port: 8080
  krakensuche:
    socket:
      port: 9090
    worker:
      heartbeat-timeout: PT15S
  ```
- `.gitignore` (target/, .idea/, *.iml, .DS_Store)
- Top-level `README.md` with how to build and run.

**Acceptance:**
- `mvn clean verify` from the root succeeds with no tests yet failing.
- `mvn -pl krakensuche-master spring-boot:run` starts and listens on 8080.
- `curl localhost:8080/actuator/health` returns 200 (add
  `spring-boot-starter-actuator` if you want this; otherwise just verify the
  port is open).

### Phase 2 — Domain + Job CRUD REST

**Goal:** Create, read, update, delete crawl jobs over REST. No crawling yet.

**Tasks:**
- Implement `CrawlJob` and `Document` entities with JPA annotations.
- `JobStatus` enum.
- `CrawlJobRepository extends JpaRepository<CrawlJob, String>`.
- `DocumentRepository extends JpaRepository<Document, String>`.
- `CrawlJobService` with `create`, `findById`, `findAll`, `cancel`, `pause`,
  `resume`. Generate UUIDs in service, set timestamps, validate input
  (non-empty seedUrls, maxDepth ≥ 1, maxPages ≥ 1).
- DTOs in `krakensuche-common`: `CreateJobRequest`, `JobResponse`. Map
  entity ↔ DTO in the controller.
- `JobController` with all five endpoints.
- `@ControllerAdvice` returning `ProblemDetail` for validation errors and
  `IllegalArgumentException`.
- Tests: `@SpringBootTest` + `MockMvc` covering each endpoint, including 404
  on unknown job and 400 on invalid input.

**Acceptance:**
- All five job endpoints work end-to-end against a running Master.
- `mvn test` is green with at least one test per endpoint.
- Created jobs survive in H2 until restart (verify via H2 console).

### Phase 3 — Worker process (standalone, no socket yet)

**Goal:** A Worker process that crawls a single URL given on the command line
and prints the result. Lays the crawl logic before plumbing it through
sockets.

**Tasks:**
- `PageFetcher` with `HttpClient` (timeout 10 s, follow redirects, user-agent
  `Krakensuche/0.1`). Returns body + status.
- `HtmlExtractor` using jsoup: extract `<title>`, plain text via
  `Element.text()`, outgoing absolute links. Filter to `http(s)` only.
- `CrawlExecutor` wraps an `ExecutorService` of N threads. `submit(url)`
  returns a `CompletableFuture<CrawlOutcome>`.
- `WorkerApplication` accepts `--url <url>` and `--threads <n>` args, runs a
  single crawl, prints result, exits.
- Unit tests: mock `HttpClient` (or use a small embedded server like Spark or
  Java's `com.sun.net.httpserver.HttpServer`) to verify extractor handles
  redirects, missing titles, relative links.

**Acceptance:**
- `java -jar krakensuche-worker.jar --url https://example.com` prints title,
  text length, link count.
- Tests cover: success, 404, redirect, malformed HTML, relative-link
  resolution.

### Phase 4 — Socket layer

**Goal:** Worker connects to Master, registers, sends heartbeats. Master
tracks active workers and exposes them at `/api/stats/workers`.

**Tasks:**
- In `krakensuche-common`: define `SocketMessage` sealed interface and all
  seven subtype records (§4). Annotate for Jackson polymorphism.
- Helper: `MessageCodec` with `String encode(SocketMessage)` and
  `SocketMessage decode(String line)`.
- Master `SocketServer`: `@PostConstruct` starts a thread that runs an accept
  loop on port 9090. Each accepted socket spawns a new thread that reads
  lines, decodes, and calls `WorkerRegistry.handle(message, connection)`.
- Master `WorkerConnection`: holds the `Socket`, a `BufferedReader`, a
  `BufferedWriter`, and a `synchronized` `send(SocketMessage)` method.
- Master `WorkerRegistry` (`@Component`):
  - `register(RegisterMessage, WorkerConnection)`
  - `heartbeat(HeartbeatMessage)` updates `lastHeartbeat`
  - Scheduled task every 5 s evicts workers whose `lastHeartbeat` is older
    than `heartbeat-timeout`. Closes their connection.
  - `findIdle()` returns a worker with `activeTasks < threadCount`.
- Worker `MasterConnection`: connects on start; sends `REGISTER`; spawns
  reader thread; spawns heartbeat thread (`ScheduledExecutorService`, 5 s).
- Worker uses `--master-host` / `--master-port` args (defaults
  `localhost`/`9090`).
- `StatsController.getWorkers()` returns the registry's snapshot.

**Acceptance:**
- Start Master, then start one Worker. `GET /api/stats/workers` shows the
  worker after registration.
- Stop the Worker (Ctrl+C). Within ~15 s the registry evicts it.
- Start two Workers — both show up.
- Logs on both sides clearly show register/heartbeat traffic.

### Phase 5 — TaskDispatcher + end-to-end crawl

**Goal:** A POSTed crawl job runs through dispatch, work, results, and
persistence. **No indexing yet** — just store documents.

**Tasks:**
- `CrawlJobService`: maintain a per-job `Deque<CrawlTask>` (URL + depth) and
  `Set<String> seenUrls`. When a job is created, seed URLs go in the deque.
- `TaskDispatcher` (`@Component`, `@Scheduled(fixedDelay = 100)` or
  event-driven):
  - For every `RUNNING` job with a non-empty queue and a non-empty pool of
    idle workers, pop a task and `worker.send(CrawlTaskMessage)`.
  - Track in-flight tasks (`Map<taskId, jobInfo>`).
- On `CRAWL_RESULT`:
  - Persist `Document` (skip if duplicate URL within job).
  - For each outgoing link: if depth + 1 ≤ maxDepth, domain allowed, URL not
    in `seenUrls`, and pages-crawled < maxPages, enqueue.
  - Increment `pagesCrawled`. If `pagesCrawled >= maxPages`, mark
    `COMPLETED`.
- On `CRAWL_ERROR`: increment `errorCount`, log, do not enqueue children.
- On worker death: re-enqueue any of its in-flight tasks.
- On `DELETE /api/jobs/{id}`: send `STOP_JOB` to all workers, clear queue,
  mark `CANCELLED`.

**Acceptance:**
- Start Master + 1 Worker. Run the test webserver from Phase 9 OR point at a
  small known site. `POST /api/jobs` with a 5-page seed, `maxDepth=2`,
  `maxPages=20`. Within seconds, `GET /api/jobs/{id}` shows
  `pagesCrawled > 0` and eventually `COMPLETED`.
- `GET /api/documents/{id}` returns crawled content.
- Cancel works mid-crawl.
- Two workers split the work (logs show alternating worker assignments).

### Phase 6 — Indexing

**Goal:** Documents are tokenized and indexed as they arrive. Index is
queryable by exact term.

**Tasks:**
- `Tokenizer`: lowercase, replace non-letters with spaces, split on
  whitespace, drop tokens shorter than 2 chars, drop stop words. Provide a
  small German + English stop word list (~50 words each, hardcoded as
  `Set.of(...)`).
- `InvertedIndex` `@Component` with the nested `ConcurrentHashMap` structure.
  Methods:
  - `void index(Document doc)` — tokenize text, for each (term, position)
    update the posting list **atomically** using `compute()`.
  - `Map<String, PostingEntry> postings(String term)` — return inner map.
  - `int documentCount()`, `int termCount()`.
- `IndexService.indexAsync(Document doc)`:
  - Submits to a dedicated `ExecutorService` (size = `availableProcessors()`).
  - Called by `TaskDispatcher` after persisting a document.
- Add a tiny test endpoint `GET /api/_debug/postings/{term}` returning the
  postings — useful for the load test in Phase 8. Mark with a comment that
  it can be removed before submission.

**Critical correctness check:** Write a test that submits 1000 identical
`Document` objects to `IndexService.indexAsync` concurrently and verifies the
final posting list has exactly the expected count. This is the
thread-safety NFA test.

**Acceptance:**
- After a crawl, `GET /api/_debug/postings/<some common word>` returns
  multiple documents.
- The 1000-concurrent-index test passes.
- `GET /api/stats` shows `totalTerms` and `totalDocuments` matching reality.

### Phase 7 — Search

**Goal:** TF-IDF-ranked search with snippets.

**Tasks:**
- `TfIdfScorer.score(query, docId, index, totalDocs)`:
  - For each query term: `tf = postings(term).get(docId).termFrequency`
  - `idf = log((totalDocs + 1) / (df + 1))` where `df = postings(term).size()`
  - Sum tf×idf across query terms.
- `SearchService.search(query, limit)`:
  - Tokenize query (same `Tokenizer`).
  - Find candidate doc IDs (union of postings).
  - Score each candidate **in parallel** using `parallelStream()` or
    `CompletableFuture.allOf(...)`.
  - Sort by score desc, take top `limit`.
  - For each result, fetch the document and extract a snippet: window of ±50
    chars around the first matching term, with `<em>` around matched terms.
- `SearchController` endpoints `GET /api/search` and
  `GET /api/documents/{id}`.

**Acceptance:**
- After crawling the test web (Phase 9) or any small site, search returns
  ranked results in <100 ms for an index of ~100 docs.
- Multi-term queries work (`q=hello+world`).
- Snippets contain the matched terms.
- Unit tests for `TfIdfScorer` with hand-computed expected values.

### Phase 8 — Stats + Client + Load simulation

**Goal:** Client tool that drives the system and measures all six NFAs from
the Skizze.

**Tasks (Master side):**
- `StatsService` aggregates: doc count, term count, active workers, jobs
  completed, search latency moving average (record latencies in a ring
  buffer of size 1000).
- Wire the rest of `/api/stats` and `/api/stats/workers`.

**Tasks (Client side):**
- `RestApiClient` covering every endpoint.
- `ClientApplication` subcommands:
  - `crawl <url>` — quick one-shot crawl
  - `search <query>` — search + print
  - `stats` — print stats
  - `loadsim <scenario>` — run one of the scenarios below
- Scenarios in `LoadSimulator`:
  1. **`error-rate`** — fire 1000 mixed requests (jobs + searches +
     doc-lookups) at a controlled rate. Report errors / total.
  2. **`search-latency`** — first crawl ~1000 pages from the test web, then
     fire 20 search RPS for 30 s. Report p50, p95, max latency.
  3. **`scaling`** — run the same crawl twice (1 worker, then 2 workers),
     measure pages/sec for each, report ratio.
  4. **`thread-distribution`** — analyze the worker logs for thread-id
     distribution after a crawl. Report std-dev / mean.
  5. **`atomic-index`** — POST 1000 identical docs to the debug index
     endpoint concurrently, verify exact count.
  6. **`startup-time`** — restart the Master via API call (or just measure
     externally), measure time until first 200 from `/api/stats`.

**Acceptance:**
- `loadsim search-latency` runs and produces a report. p95 should be under
  200 ms on a corpus of 1000 docs.
- `loadsim scaling` shows ≥1.6× throughput with 2 workers vs 1 worker.
- All six scenarios produce a clear pass/fail line.

### Phase 9 — Test webserver (optional but recommended)

**Goal:** A reproducible synthetic web for crawling. Without this you depend
on the live internet, which is flaky and not great for the demo.

**Tasks:**
- Create `krakensuche-testweb` module — a tiny Spring Boot app on port 8081.
- Generates N synthetic pages on startup, each with 3–8 random outgoing links
  to other pages and a paragraph of lorem-ipsum-style text seeded from a
  small word pool (so search has hits).
- Routes: `GET /page/{id}` returns the HTML.
- Configurable via `application.yml`: page count (default 1000), seed.
- Always-deterministic given the seed.

**Acceptance:**
- Start testweb. Crawl it from the Master with `maxPages=500`. The crawl
  completes deterministically with the same docs every run.
- All Phase 8 load scenarios use this module instead of the live web.

---

## 8. Logging conventions

Every component logs on these key events. Use SLF4J with structured
key=value pairs.

| Event | Logger | Level | Message |
|---|---|---|---|
| Worker register | `Master:WorkerRegistry` | INFO | `worker registered workerId=... threads=... remote=...` |
| Worker evicted | `Master:WorkerRegistry` | WARN | `worker evicted workerId=... reason=heartbeat-timeout` |
| Job created | `Master:JobController` | INFO | `job created jobId=... seeds=...` |
| Task dispatched | `Master:TaskDispatcher` | DEBUG | `task dispatched taskId=... jobId=... url=... worker=...` |
| Crawl result received | `Master:TaskDispatcher` | DEBUG | `crawl result taskId=... status=... links=...` |
| Document indexed | `Master:IndexService` | TRACE | `indexed docId=... terms=... thread=...` |
| Search executed | `Master:SearchService` | DEBUG | `search q=... hits=... latencyMs=...` |
| Worker fetch | `Worker:PageFetcher` | DEBUG | `fetched url=... status=... bytes=... thread=...` |

Crucially, the `thread=` field on indexer and fetcher logs is what proves the
"dynamic mehrkernig"-NFA. Set the format to include `%thread` in
`logback.xml` and the load simulator just greps for it.

---

## 9. Build & run

### Build everything

```sh
mvn clean verify
```

### Run Master

```sh
mvn -pl krakensuche-master spring-boot:run
# or after package:
java -jar krakensuche-master/target/krakensuche-master-0.1.0-SNAPSHOT.jar
```

### Run Worker

```sh
java -jar krakensuche-worker/target/krakensuche-worker-0.1.0-SNAPSHOT.jar \
  --master-host=localhost --master-port=9090 --threads=8
```

### Run Client

```sh
java -jar krakensuche-client/target/krakensuche-client-0.1.0-SNAPSHOT.jar \
  search "verteilte systeme"

java -jar krakensuche-client/target/krakensuche-client-0.1.0-SNAPSHOT.jar \
  loadsim search-latency
```

### Run test web (optional)

```sh
mvn -pl krakensuche-testweb spring-boot:run
```

---

## 10. Working agreements for Claude Code

Things to keep in mind while implementing:

- **One phase at a time.** Don't start Phase 5 logic in Phase 3. If you find
  yourself needing something from a later phase, stub it.
- **Write the test first for any concurrency-critical code.** The 1000-doc
  atomic-index test and the dedup test for `CrawlJobService` are both
  required.
- **Records over classes for DTOs and messages.** Records over Lombok.
- **Constructor injection only** in Spring components. No field injection,
  no `@Autowired`.
- **Don't catch and swallow `InterruptedException`.** Restore the interrupt
  flag and exit the loop.
- **Don't use `System.out.println` outside `main` methods and CLI output.**
  Use SLF4J.
- **`final` everywhere** it makes sense — fields, parameters, local vars
  that aren't reassigned.
- **Don't pull in extra dependencies** without checking against §1. Adding
  Guava, Apache Commons, Lombok, etc. is a regression.
- **The socket protocol is the API contract.** Once Phase 4 is done, don't
  silently change message shapes — both sides need to agree.
- **`@Transactional` on service methods** that touch the repository. Keep
  the index updates outside the transaction (it's not JPA-managed).
- **No `Thread.sleep` in tests** — use `Awaitility` or `CountDownLatch` /
  `CompletableFuture` for synchronization.

---

## 11. Out of scope

Explicitly **not** building:

- Authentication, authorization, TLS (security NFA is excluded per the
  course rules).
- A web frontend (course rules: "Kein Frontend").
- Persistent storage of the inverted index (it's rebuilt on restart from
  documents in H2 — and that's fine for a prototype, but a small
  rebuild-on-startup hook is a nice-to-have if there's time).
- Distributed indexing across multiple masters.
- robots.txt handling (acknowledge in the presentation as a known
  limitation).
- Crawl politeness / rate limiting per domain (mention as
  "Verbesserungspotenzial" in the final talk).

---

## 12. Definition of project done

- All 8 phases complete (9 if test web is included).
- `mvn clean verify` green from a fresh clone.
- The three module requirements are demonstrably met:
  - **Nebenläufig** — `CrawlExecutor` thread pool in worker AND parallel
    indexer in master AND parallel scorer in search.
  - **Verteilt** — Master ↔ Worker over TCP sockets with custom
    line-delimited JSON protocol.
  - **Komponentenbasiert** — Spring Boot `@Service`/`@Component`/
    `@RestController` separation in the master.
- All six load scenarios pass their thresholds.
- README documents how to build, run, and reproduce the load tests.
- Presentation slides cover: idea, architecture, who built what, demo,
  problems encountered, improvement ideas (per the EVA presentation
  criteria).
