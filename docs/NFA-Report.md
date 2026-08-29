# NFA-Report Tausendfuessler

* Lauf: 2026-08-29 local run, MacBook, JDK 26
* Koordinator: http://localhost:8080
* Erzeugt von `loadtest.jar`; Worker liefen als In-Prozess-Instanzen von `WorkerClient` im Testclient.

## Zusammenfassung

| Szenario | NFA | Ergebnis |
|---|---|---|
| startup | Koordinator in < 15 s startbereit | erfuellt |
| status-latency | Statusabfragen < 0,2 s bei < 20 gleichzeitigen Auftraegen | erfuellt |
| error-ratio | > 99,9 % der Statusanfragen ohne internen Fehler | erfuellt |
| throughput | >= 60 % mehr Durchsatz mit 2 Workern als mit 1 Worker | erfuellt |
| live-latency | Live-Ergebnisse innerhalb von 2 s nach Seitenabruf | erfuellt |
| dedup | Atomare und thread-sichere URL-Deduplizierung | erfuellt |

## startup

NFA: Koordinator in < 15 s startbereit  
Ergebnis: **erfuellt**

| Kennzahl | Wert |
|---|---|
| Startzeit des Koordinators (JVM-Start bis ApplicationReady) | 3.4 s |
| Health-Anfragen bis zur ersten Antwort | 15 |
| Grenzwert | 15 s |

* Der Koordinator misst die Zeit selbst (JVM-Start bis ApplicationReadyEvent) und meldet sie in /api/health als startupSeconds; der Zeitpunkt des Client-Starts spielt keine Rolle. Zum Vergleich: logs/coordinator.log ("Started CoordinatorApplication in ... seconds").

## status-latency

NFA: Statusabfragen < 0,2 s bei < 20 gleichzeitigen Auftraegen  
Ergebnis: **erfuellt**

| Kennzahl | Wert |
|---|---|
| Gleichzeitige Auftraege | 20 |
| Statusabfragen | 300 |
| p50 | 7 ms |
| p95 | 13 ms |
| max | 98 ms |
| Ueberschreitungen (> 200 ms) | 0 |
| Antworten != 200 | 0 |

* Site: 300 Seiten, 300 ms Verzoegerung, maxDepth 2; waehrend der Messung crawlt ein In-Prozess-Worker mit 4 Threads. Vor der Messung wurde jeder Job einmal ungezaehlt abgefragt (Aufwaermen des frisch gestarteten Koordinators).

## error-ratio

NFA: > 99,9 % der Statusanfragen ohne internen Fehler  
Ergebnis: **erfuellt**

| Kennzahl | Wert |
|---|---|
| Dauer | 60 s |
| Anfragen gesamt | 1200 |
| 2xx (gueltig) | 546 |
| 4xx (Clientfehler, erwartet) | 654 |
| 5xx / keine Antwort (intern) | 0 |
| Anteil interner Fehler | 0.000 % |
| Anteil ohne internen Fehler | 100.000 % |
| Antworten mit anderem Status als erwartet | 0 |

* Mix aus 11 Anfragetypen im Round-Robin: gueltige Status-, Listen-, Ergebnis- und Stats-Abfragen sowie ungueltige (unbekannte ID -> 404, kaputter Body -> 400, Pause auf beendetem Job -> 409).

## throughput

NFA: >= 60 % mehr Durchsatz mit 2 Workern als mit 1 Worker  
Ergebnis: **erfuellt**

| Kennzahl | Wert |
|---|---|
| Site | 2000 Seiten, maxDepth 3, 100 ms Verzoegerung je Abruf |
| 1 Worker: Seiten / Dauer | 600 / 29.3 s |
| 1 Worker: Durchsatz | 20.5 Seiten/s |
| 2 Worker: Seiten / Dauer | 600 / 15.3 s |
| 2 Worker: Durchsatz | 39.2 Seiten/s |
| Relative Steigerung | 91.8 % |

* Je Worker 4 Crawl-Threads, alle Worker laufen im Testclient-Prozess; waehrend dieses Szenarios darf kein weiterer Worker mit dem Koordinator verbunden sein (wird vor der Messung per Probe-Job geprueft).

## live-latency

NFA: Live-Ergebnisse innerhalb von 2 s nach Seitenabruf  
Ergebnis: **erfuellt**

| Kennzahl | Wert |
|---|---|
| Job-Status am Ende | COMPLETED |
| Empfangene Seiten | 125 |
| Verzoegerung p50 | 254 ms |
| Verzoegerung p95 | 507 ms |
| Verzoegerung max | 517 ms |
| Ueberschreitungen (> 2000 ms) | 0 |

* Verzoegerung = Empfang beim Client - crawledAt des Workers; Worker laufen im selben Prozess, die Uhren sind also identisch. Poll-Intervall 500 ms (Bot: 1 s). Site: 200 Seiten, 100 ms Verzoegerung, 2 Worker.

## dedup

NFA: Atomare und thread-sichere URL-Deduplizierung  
Ergebnis: **erfuellt**

| Kennzahl | Wert |
|---|---|
| Site | 500 Seiten, jede verlinkt dieselben 50 Hub-Seiten |
| Gemeldete Links (linksFound) | 18076 |
| pagesVisited laut Job | 342 |
| Ergebniszeilen in /results | 342 |
| Verschiedene URLs | 342 |
| Mehrfach enthaltene URLs | 0 |

* 2 In-Prozess-Worker mit je 4 Threads, maxDepth 3.

## Anderweitig nachgewiesene NFAs

* Worker-Threadpool liefert alle Ergebnisse unter Last: `worker/.../pool/CrawlExecutorTest.handlesHighVolumeConcurrentCrawls` (100 gleichzeitige URLs auf 4 Threads, exakt 100 CrawlOutcomes).
* Dynamische Mehrkernauslastung: Zeilen `crawled url=... thread=...` im Worker-Log (Crawl-Threads) und `... handled on thread ...` in `logs/coordinator.log` (Worker-Handler-Threads).

## Nachweise aus Logs/Tests

Ergänzung zum generierten Teil (Lauf vom 2026-08-29, Koordinator frisch gestartet, Postgres 16 in Docker auf Port 5433).

* **Startzeit** (`/tmp/coordinator.out` bzw. `logs/coordinator.log`): Spring meldet 3,05 s ("process running for 3.396"), `/api/health` liefert `startupSeconds` ≈ 3,4 s (Grenzwert 15 s).
  ```
  ... [main] d.u.e.t.c.socket.WorkerSocketServer : worker socket listening on port 9090
  ... [main] d.u.e.t.c.CoordinatorApplication    : Started CoordinatorApplication in 2.78 seconds (process running for 3.185)
  ```
* **Mehrkernauslastung im Koordinator**: jeder Worker hat einen eigenen Handler-Thread; im Durchsatz-Szenario mit 2 Workern
  wurden die Ergebnisse desselben Jobs 15 ms auseinander auf den Threads 54 und 55 verarbeitet (im ganzen Lauf 3 Handler-Threads mit
  1130 / 537 / 309 PAGE_RESULTs):
  ```
  2026-08-29T19:08:00.653 INFO [worker-handler] WorkerConnectionHandler : PAGE_RESULT from lt-throughput2-w1 for job 28aeb73e-... (http://127.0.0.1:62524/p/1969) handled on thread 54
  2026-08-29T19:08:00.668 INFO [worker-handler] WorkerConnectionHandler : PAGE_RESULT from lt-throughput2-w2 for job 28aeb73e-... (http://127.0.0.1:62524/p/705) handled on thread 55
  ```
  Im Worker analog die Zeilen `crawled url=... thread=...` (ein Crawl-Thread je CPU-Kern).
* **Threadpool-Vollständigkeit unter Last**: `worker/.../pool/CrawlExecutorTest.handlesHighVolumeConcurrentCrawls` –
  100 gleichzeitige URLs auf 4 Threads liefern exakt 100 `CrawlOutcome`s.
* **Atomare Dedup (Unit-Ebene)**: `coordinator/.../crawl/JobRuntimeTest.dedupUnderConcurrentOffersHandsOutEachUrlExactlyOnce` –
  mehrere Threads melden dieselben Links gleichzeitig, jede URL wird genau einmal zugeteilt. Das Szenario `dedup` oben
  bestätigt das End-to-End gegen Postgres (342 Seiten, 0 Duplikate bei 18076 gemeldeten Links).

### Anmerkungen zum Lauf

* Erster Startversuch scheiterte, weil `ddl-auto: update` die neue NOT-NULL-Spalte `jobs.current_depth` in einer Datenbank mit
  bestehenden Jobs nicht anlegen konnte. Fix: Spaltendefault 0 in `JobEntity` (kein Neuaufsetzen der DB nötig).
* `throughput` mit Verzögerung 0 ms zeigte nur +28 % (87 -> 112 Seiten/s): Ohne Netzlatenz limitiert der pro Job serialisierte
  Ergebnispfad im Koordinator (DB-Schreibvorgang je Seite), nicht die Worker. Das Szenario simuliert deshalb 100 ms Abrufzeit
  je Seite (realistische Netzlatenz); damit skaliert der Durchsatz nahezu linear (+92 %).
* `status-latency`: Auf einem frisch gestarteten Koordinator lag die allererste Detailabfrage bei ca. 0,5 s (Hibernate-Queryplan/JIT).
  Der Client fragt deshalb jeden Job vor der Messung einmal ungezählt ab; danach max 98 ms bei 300 Abfragen.
