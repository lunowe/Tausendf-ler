# Tausendfüßler – Schnittstellen

Zwei Schnittstellen, beide JSON:

1. **Bot → Koordinator: REST** (HTTP, Spring MVC, Port 8080)
2. **Koordinator ↔ Worker: TCP-Socket** (line-delimited JSON, Port 9090)

Die Java-Typen der Socket-Nachrichten liegen in `common`
(`de.uni_leipzig.eva.tausendfuessler.common.protocol.Message`). Die REST-DTOs des Bots liegen in
`bot/.../dto`; der Koordinator muss dieselben Feldnamen liefern.

---

## 1. REST-API des Koordinators

| Methode | Pfad | Body / Query | Antwort |
|---|---|---|---|
| `POST` | `/api/jobs` | `{url, maxDepth, filters[], owner}` | `{jobId, status, message}` |
| `GET` | `/api/jobs?owner=<chatId>` | – | `[{jobId, url, status, pagesVisited, createdAt}]` |
| `GET` | `/api/jobs/{id}` | – | `{jobId, url, maxDepth, currentDepth, status, pagesVisited, linksFound, errors, startedAt, finishedAt}` |
| `GET` | `/api/jobs/{id}/results?afterSeq=<n>` | – | `[{seq, url, title, textSnippet, depth, crawledAt}]` aufsteigend nach `seq` |
| `POST` | `/api/jobs/{id}/pause` | – | 204 |
| `POST` | `/api/jobs/{id}/resume` | – | 204 |
| `POST` | `/api/jobs/{id}/abort` | – | 204 |
| `GET` | `/api/stats` | – | `{totalJobs, activeJobs, totalPagesCrawled, topDomains{domain: count}}` |
| `GET` | `/api/search?q=<text>&limit=<n>` | – | `[{url, title, textSnippet, jobId}]` (Postgres-Volltextsuche) |
| `GET` | `/api/workers` | – | `[{workerId, threads, inFlight, connectedAt}]` verbundene Worker, älteste Verbindung zuerst |
| `GET` | `/api/health` | – | `{status: "UP", time, startupSeconds}` (JVM-Start bis ApplicationReady) |

* **Authentifizierung**: Jeder Aufruf unter `/api/**` trägt den Header `X-Api-Key: <API_KEY>`
  (Shared Secret aus der Umgebungsvariable `API_KEY` des Koordinators). Fehlt der Header oder ist der
  Schlüssel falsch → `401 {error: "unauthorized"}`. Ausnahmen: `GET /api/health` (Monitoring) und
  `OPTIONS` (CORS-Preflight). Ist `API_KEY` beim Koordinator leer, ist die Prüfung abgeschaltet
  (lokale Entwicklung; wird beim Start als WARN geloggt). Implementierung: ein einzelner
  Servlet-Filter (`coordinator/.../config/ApiKeyFilter`), kein Spring Security.
* `status` ∈ `PENDING, RUNNING, PAUSED, COMPLETED, ABORTED, FAILED`
* `owner` = Telegram-Chat-ID; `/list` zeigt nur eigene Jobs.
* `currentDepth` = höchste Tiefe, für die bereits ein Seitenergebnis eingegangen ist (bleibt nach Job-Ende stehen).
* `seq` ist eine pro Job streng monoton steigende Nummer. Der Bot pollt jede Sekunde mit dem
  zuletzt gesehenen `seq` – das ist der Live-Stream.
* Fehler: `404` unbekannter Job, `409` ungültiger Zustandsübergang (z. B. Pause auf beendetem Job),
  `400` ungültige Eingabe. Body `{error: "..."}`.
* **CORS**: Der Bot ruft die API serverseitig auf und braucht kein CORS. Für das Browser-Frontend
  (`frontend/`, Next.js) erlaubt der Koordinator `/api/**` für die Origins aus
  `tausendfuessler.cors-origins` (Standard `http://localhost:3000`, Methoden `GET, POST, OPTIONS`,
  Header `Content-Type, X-Api-Key`) – siehe `coordinator/.../config/WebCorsConfig`. Andere Origins
  bekommen `403` auf den Preflight.

---

## 2. Socket-Protokoll Koordinator ↔ Worker

* Eine persistente TCP-Verbindung pro Worker, Worker verbindet sich zum Koordinator.
* Jede Zeile (`\n`-terminiert, UTF-8) ist genau eine Nachricht; das Feld `type` bestimmt den Typ.
* Beide Seiten dürfen jederzeit senden (Koordinator pusht Signale, ohne dass der Worker fragt).

### Nachrichten

| Richtung | `type` | Felder | Bedeutung |
|---|---|---|---|
| W → K | `REGISTER` | `workerId, threads, token` | Erste Nachricht nach Connect; `token` = Shared Secret `WORKER_TOKEN` (`null`, wenn der Koordinator keins verlangt) |
| K → W | `REGISTERED` | `workerId` | Bestätigung |
| W → K | `REQUEST_WORK` | `workerId, capacity` | Worker hat `capacity` freie Slots |
| K → W | `WORK_PACKAGE` | `jobId, depth, urls[], filters[]` | URL-Paket; alle URLs haben dieselbe Tiefe |
| K → W | `NO_WORK` | `retryAfterMs` | Nichts zu tun, später erneut fragen |
| W → K | `PAGE_RESULT` | `workerId, jobId, url, depth, httpStatus, title, textSnippet, links[], error, crawledAtEpochMs` | Ein Ergebnis pro URL; `error != null` = fehlgeschlagen |
| K → W | `JOB_SIGNAL` | `jobId, signal` ∈ `PAUSE, RESUME, ABORT` | Steuersignal |
| beide | `ERROR` | `message` | Protokollfehler |

### Ablauf

```
Worker                                   Koordinator
  │── REGISTER {w1, 8, token} ─────────────▶│  prüft token, registriert w1
  │◀─ REGISTERED {w1} ──────────────────────│  (falsch: ERROR {unauthorized} + Socket zu)
  │── REQUEST_WORK {w1, 8} ────────────────▶│  Least-Work-First: w1 hat 0 offen
  │◀─ WORK_PACKAGE {job, 0, [urlA], []} ────│  markiert urlA als "in flight @ w1"
  │   (Thread-Pool crawlt)                  │
  │── PAGE_RESULT {job, urlA, links…} ─────▶│  dedupliziert links, füllt Frontier Tiefe 1,
  │                                         │  speichert Page (DB), seq++ → Bot kann pollen
  │── REQUEST_WORK {w1, 8} ────────────────▶│
  │◀─ WORK_PACKAGE {job, 1, [urlB, urlC…]} ─│
  │◀─ JOB_SIGNAL {job, PAUSE} ──────────────│  (Nutzer hat /pause geschickt)
  │   Worker beendet laufende Abrufe,       │
  │   sendet deren PAGE_RESULTs noch,       │
  │   fragt nicht mehr nach Arbeit für job  │
  │◀─ JOB_SIGNAL {job, RESUME} ─────────────│
  │── REQUEST_WORK … ──────────────────────▶│
```

### Regeln

* **Worker-Token**: Ist beim Koordinator `WORKER_TOKEN` gesetzt, muss `REGISTER.token` exakt
  übereinstimmen (konstantzeitiger Vergleich). Sonst antwortet der Koordinator mit
  `ERROR {message: "unauthorized"}` und schließt den Socket. Der Worker loggt das als ERROR und beendet
  sich mit Exit-Code 3 – **kein** Reconnect, ein Tippfehler soll den Server nicht dauerhaft hämmern.
  Leeres `WORKER_TOKEN` = jeder Worker wird angenommen (WARN im Log).
* **Paketgröße**: Koordinator gibt höchstens `capacity` URLs, typischerweise `min(capacity, 2 × threads)`.
* **Verteilung**: Least-Work-First – der Worker mit den wenigsten offenen (zugeteilt, noch nicht
  beantwortet) URLs bekommt das nächste Paket. Bei Gleichstand Round-Robin.
* **Dedup**: pro Job ein thread-sicheres `Set<String>` normalisierter URLs im Koordinator. Eine URL
  wird genau einmal zugeteilt, egal von welchem Worker sie gemeldet wurde.
* **Tiefe**: Tiefe 0 = Start-URL. Links aus einer Seite der Tiefe `d` kommen in die Frontier für
  `d + 1`, sofern `d + 1 ≤ maxDepth`.
* **Filter**: Wenn gesetzt, werden nur Links weiterverfolgt, deren URL mindestens einen Filter-String
  enthält (einfacher `contains`, kein Regex).
* **Worker-Absturz**: Socket-Fehler, EOF oder 60 s Funkstille (Read-Timeout; ein lebender Worker
  sendet ständig `REQUEST_WORK`/`PAGE_RESULT`) im Handler-Thread → alle "in flight"-URLs dieses
  Workers zurück in die Frontier, Vorfall ins Log, Worker aus der Registry.
* **Worker-Reconnect**: Worker versucht bei Verbindungsverlust mit Backoff (1 s … 10 s) erneut.
* **Job-Ende**: Frontier leer und keine URLs in flight → `COMPLETED`. `ABORT` → `ABORTED`,
  bereits gespeicherte Seiten bleiben erhalten.
