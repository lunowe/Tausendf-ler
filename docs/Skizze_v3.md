# Prototyp-Skizze „Tausendfüßler“ (v3, Stand der Implementierung)

Modul *Entwicklung verteilter Anwendungen*, Universität Leipzig, SS26. Ersetzt Skizze v2 und beschreibt die
Architektur, wie sie im Repository umgesetzt ist; Abweichungen gegenüber v2 stehen am Ende.

## Beschreibung

„Tausendfüßler“ ist ein verteilter Webcrawler, der über einen Telegram-Bot gesteuert wird. Der Bot-Prozess nimmt
Crawl-Aufträge entgegen und reicht sie per REST an einen Koordinator weiter. Der Koordinator verteilt die zu
besuchenden URLs über TCP-Sockets auf mehrere Worker-Prozesse, die parallel Webseiten herunterladen, parsen und ihre
Ergebnisse zurückmelden. Aufträge und Seiten liegen in Postgres; der Bot holt neue Ergebnisse im Sekundentakt ab und
liefert sie als Live-Stream in den Telegram-Chat.

## Funktionale Anforderungen

### Benutzerinteraktion (via Telegram-Bot)

* `/crawl <URL> [Tiefe] [Filter…]`: Auftrag mit Start-URL, maximaler Tiefe und optionalen URL-Filtern anlegen.
* `/list`: eigene aktive und abgeschlossene Aufträge mit Kurzinfo.
* `/status <Job-ID>`: aktuelle Tiefe, besuchte Seiten, gefundene Links, Fehler, Status
  (PENDING/RUNNING/PAUSED/COMPLETED/ABORTED/FAILED).
* `/pause`, `/resume`, `/abort <Job-ID>`: Auftrag pausieren, fortsetzen, abbrechen; bei Abbruch bleiben die
  gespeicherten Seiten erhalten.
* Live-Stream neu gefundener Seiten (URL, Titel, Textanfang, Tiefe) während des Crawls; danach ein Report mit
  besuchten Seiten, extrahierten Links, Fehlern und Dauer.
* `/search <Begriff>`: Volltextsuche über Titel und Textanfang aller gespeicherten Seiten.
* `/stats`: Aufträge (gesamt/aktiv), gecrawlte Seiten, meistgecrawlte Domains.

### Systeminterne Abläufe (Koordinator & Worker)

* Der Koordinator nimmt Aufträge über `POST /api/jobs` an, legt sie in Postgres an und hält je Auftrag eine
  In-Memory-Frontier (eine Warteschlange pro Tiefe) sowie die Menge bereits gesehener URLs.
* Worker verbinden sich per TCP (`REGISTER`) und holen sich aktiv URL-Pakete (`REQUEST_WORK` mit freier Kapazität).
  Da ein Worker nur mit freien Slots fragt, erhält der Worker mit den wenigsten offenen URLs das nächste Paket
  (Least-Work-First); mehrere laufende Aufträge werden reihum bedient.
* Dedup pro Auftrag über ein `ConcurrentHashMap`-Key-Set (`visited.add(url)` als atomares „first one wins“), über
  Worker-Grenzen hinweg; jede URL wird genau einmal zugeteilt.
* Worker crawlen ein Paket im Thread-Pool, melden je URL ein `PAGE_RESULT` (Status, Titel, Textanfang, Links oder
  Fehler) und respektieren `JOB_SIGNAL` (PAUSE/RESUME/ABORT), das der Koordinator über die bestehende Verbindung
  pusht. Bei PAUSE werden laufende Abrufe noch gemeldet, dann wartet der Worker auf RESUME.
* Worker-Absturz (Socket-Fehler, EOF oder 60 s Funkstille): der Koordinator legt alle „in flight“-URLs dieses
  Workers zurück in die Frontier, protokolliert den Vorfall und entfernt den Worker; Worker reconnecten mit Backoff.
* Ergebnisse werden per JPA als `Page`-Zeilen mit je Auftrag streng monoton steigender `seq` gespeichert. Der Bot
  pollt jede Sekunde `GET /api/jobs/{id}/results?afterSeq=<n>` mit seinem letzten Cursor – das ist der Live-Stream.
* Frontier leer und nichts in flight ⇒ `COMPLETED`. Beim Neustart des Koordinators werden noch aktive Aufträge
  eines früheren Laufs als `FAILED` markiert (die Frontier lebt nur im Speicher).
* Auftragsstart/-ende, Worker-Verbindungen, Ausfälle und Requeues stehen in `logs/coordinator.log`.

## Wartung & optionale Erweiterungen

* `/stats` wird per SQL aus den Tabellen `jobs` und `pages` berechnet (Top-5-Domains aus den URLs).
* `/search` nutzt Postgres `to_tsvector`/`plainto_tsquery`, sortiert nach `ts_rank`.
* Retention-Cleanup: beim Start löscht der Koordinator Aufträge samt Seiten, die älter als `RESULT_RETENTION_DAYS`
  sind (Standard 30 Tage).

## Nicht-funktionale Anforderungen

Annahme: Koordinator, Worker und Postgres laufen auf einem handelsüblichen Laptop (mehrkernige CPU).

1. Der Koordinator beantwortet Status- und Steueranfragen des Bots zu > 99,9 % ohne internen Fehler (5xx).
2. Eine Statusabfrage wird bei < 20 gleichzeitigen Aufträgen in unter 0,2 s beantwortet.
3. Koordinator und Worker nutzen Mehrkernsysteme dynamisch: Worker-Thread-Pool in Größe der verfügbaren Kerne, im
   Koordinator ein Handler-Thread je Worker-Verbindung.
4. URL-Deduplizierung und die gleichzeitige Entgegennahme von Ergebnissen mehrerer Worker sind atomar und
   thread-sicher; keine Doppel-Crawls, keine verlorenen Updates.
5. Live-Ergebnisse erreichen den Bot innerhalb von 2 s nach Abschluss des Seitenabrufs.
6. Der Crawl-Durchsatz mit zwei Workern ist um mindestens 60 % höher als mit einem Worker (gleicher Auftrag).
7. Der Koordinator ist innerhalb von 15 s nach Prozessstart bereit (REST und Worker-Socket).
8. Der Worker-Thread-Pool liefert unter gleichzeitiger Last alle Ergebnisse vollständig zurück.
9. Die Volltextsuche liefert für gespeicherte Seiten korrekte Treffer, auch während weitere Aufträge laufen
   (ersetzt „parallele Statistikberechnung aus JSON-Dateien“, siehe Änderungen).
10. Security bleibt als nicht-funktionale Anforderung explizit ausgeklammert.

## Verteilung

### Nebenläufigkeit

* Worker: paralleles Herunterladen und Parsen mit einem `ExecutorService` (Standard: ein Thread pro CPU-Kern, per
  `--threads` änderbar); der Koordinator gibt höchstens `2 × threads` URLs pro Paket.
* Koordinator: ein Thread je Worker-Verbindung; Frontier, Dedup-Set und In-flight-Map sind nebenläufige Strukturen.
  Nur die zusammengesetzten Übergänge (Zuteilung, Ergebnisverbuchung, Abschlussprüfung) sind pro Auftrag
  synchronisiert, damit ein Auftrag nie als fertig gilt, während eine URL zwischen Frontier und In-flight steht.
* REST-Anfragen des Bots bedient der Tomcat-Thread-Pool von Spring MVC parallel; der Bot pollt alle abonnierten
  Aufträge in einem Scheduler-Thread.

### Verteilung

* Bot ↔ Koordinator: REST (HTTP/JSON, Spring MVC, Port 8080). Der Bot ist eine dünne Oberfläche ohne Fachlogik;
  über dieselbe API könnte auch ein Browser-Frontend arbeiten. Erfüllt die Modulanforderung „REST-Services via
  Spring Boot“.
* Koordinator ↔ Worker: pro Worker eine persistente TCP-Verbindung (Port 9090, line-delimited JSON, Nachrichten-
  Records in `common`); beide Seiten dürfen jederzeit senden. Erfüllt die Modulanforderung „Kommunikation via Sockets“.
* Koordinator ↔ Postgres: JDBC/JPA; lokal per Docker Compose oder als gemeinsame Cloud-Instanz.
* Worker sind eigenständige Java-Prozesse und laufen auch auf anderen Rechnern (`--coordinator host:port`).

### Komponenten

* `common` (Plain Java + Jackson): Nachrichten-Records des Socket-Protokolls.
* `coordinator` (Spring Boot Web + Data JPA): REST-Controller, Job-/Result-/Stats-Services, Scheduler,
  Worker-Registry, TCP-Server, Entities `JobEntity`/`PageEntity`, Retention-Cleanup, Startup-Recovery.
* `worker` (Plain Java, kein Spring): TCP-Client-Schleife, `CrawlExecutor` (Thread-Pool), `PageFetcher`,
  `HtmlExtractor` (jsoup, inkl. Sitemap-`<loc>`). Spring Boot würde hier nur Start-Overhead bringen.
* `bot` (Spring Boot ohne Web-Server + telegrambots): Command-Registry mit einem Handler je Befehl,
  `CoordinatorClient` (REST), `ResultPoller` (Live-Stream). Die Telegram-HTTP-API ist ein externer Dienst.
* `loadtest` (Plain Java): Testclient für die NFAs mit synthetischer Website und In-Prozess-Workern.

## Skizze

```
 Telegram-API ──HTTPS (Long Polling)──▶ ┌─────────────────────────────┐
                                        │ Bot (Spring Boot)           │
                                        │ CommandRegistry, Handler    │
                                        │ CoordinatorClient           │
                                        │ ResultPoller (1 s, seq)     │
                                        └──────────────┬──────────────┘
                                                       │ REST :8080  (/api/jobs, /results?afterSeq,
                                                       │              /pause|resume|abort, /search, /stats)
                                        ┌──────────────▼──────────────┐        ┌──────────────┐
                                        │ Koordinator (Spring Boot)   │──JPA──▶│ Postgres     │
                                        │ JobService / ResultService  │        │ jobs, pages  │
                                        │ JobRuntime: Frontier, Dedup │        │ Volltextsuche│
                                        │ Scheduler (Least-Work-First)│        └──────────────┘
                                        │ WorkerRegistry, Requeue     │
                                        │ WorkerSocketServer :9090    │
                                        └───┬──────────┬──────────┬───┘
                        TCP, line-delimited JSON (REGISTER, REQUEST_WORK, WORK_PACKAGE,
                                    PAGE_RESULT, JOB_SIGNAL)
                                            │          │          │
                                     ┌──────▼───┐ ┌────▼─────┐ ┌──▼───────┐
                                     │ Worker 1 │ │ Worker 2 │ │ Worker n │  Plain Java,
                                     └──────────┘ └──────────┘ └──────────┘  Thread-Pool = CPU-Kerne
                                            │ HTTP GET     │             │
                                            ▼              ▼             ▼
                                                     Ziel-Websites
```

Abbildung 1: Systemarchitektur – Telegram-API → Bot → (REST) Koordinator → (TCP) Worker; Persistenz in Postgres.

## Last-Simulation & Test der nicht-funktionalen Anforderungen

Testclient ist das Modul `loadtest` (`java -jar loadtest.jar --scenario all --report docs/NFA-Report.md`). Es
startet eine synthetische Website auf einem freien Port und, wo nötig, echte `WorkerClient`-Instanzen im eigenen
Prozess. Messwerte aus dem Lauf vom 2026-08-29 (MacBook, Postgres 16 in Docker), Details in `docs/NFA-Report.md`.

| Nr. | NFA | Überprüfung (wie umgesetzt) | Ergebnis |
|---|---|---|---|
| 1 | > 99,9 % ohne internen Fehler | Szenario `error-ratio`: 60 s Round-Robin über 11 Anfragetypen (gültige Status-/Listen-/Ergebnis-/Stats-Abfragen sowie 404/400/409-Fälle); gezählt werden 5xx und ausbleibende Antworten. | 1200 Anfragen, 0 interne Fehler – erfüllt |
| 2 | Statusabfrage < 0,2 s bei < 20 Aufträgen | Szenario `status-latency`: 20 Aufträge auf einer 300-Seiten-Site (300 ms Verzögerung), währenddessen 300 getaktete `GET /api/jobs/{id}`; Überschreitungen gezählt. | p50 7 ms, p95 13 ms, max 98 ms, 0 Überschreitungen – erfüllt |
| 3 | Dynamische Mehrkernauslastung | Worker loggen `crawled url=… thread=…`, der Koordinator `handled on thread …` je Worker-Handler; im Durchsatz-Lauf werden Ergebnisse desselben Auftrags auf verschiedenen Threads verarbeitet. | 3 Handler-Threads mit 1130/537/309 Ergebnissen – erfüllt (qualitativ, keine Varianzgrenze) |
| 4 | Atomare, thread-sichere URL-Dedup | Unit: `JobRuntimeTest.dedupUnderConcurrentOffersHandsOutEachUrlExactlyOnce`. End-to-End: Szenario `dedup` – 500 Seiten, jede verlinkt dieselben 50 Hub-Seiten, 2 Worker × 4 Threads; `/results` wird auf doppelte URLs geprüft. | 18 076 gemeldete Links, 342 Seiten, 0 Duplikate – erfüllt |
| 5 | Live-Ergebnisse < 2 s | Szenario `live-latency`: Client pollt wie der Bot mit `afterSeq` (500 ms) und misst `Empfang − crawledAt` des Workers (gleiche Uhr, da In-Prozess). | p50 254 ms, p95 507 ms, max 517 ms – erfüllt |
| 6 | ≥ 60 % mehr Durchsatz mit 2 Workern | Szenario `throughput`: derselbe Auftrag (2000-Seiten-Site, maxDepth 3, 100 ms Abrufzeit) erst mit 1, dann mit 2 In-Prozess-Workern (je 4 Threads). | 20,5 → 39,2 Seiten/s (+92 %) – erfüllt |
| 7 | Koordinator < 15 s startbereit | Szenario `startup`: der Koordinator misst JVM-Start bis `ApplicationReady` selbst und meldet `startupSeconds` in `/api/health`; Abgleich mit dem Spring-Startup-Log. | 3,4 s – erfüllt |
| 8 | Thread-Pool liefert alle Ergebnisse | `CrawlExecutorTest.handlesHighVolumeConcurrentCrawls`: 100 gleichzeitige URLs auf 4 Threads, exakt 100 `CrawlOutcome`. | 100/100 – erfüllt |
| 9 | Volltextsuche liefert korrekte Treffer | `EndToEndTest` (REST → Koordinator → 2 Worker → Test-Site → H2 mit LIKE-Fallback) fragt `/api/search` nach einem bekannten Begriff ab; gegen Postgres (`tsvector`) im Livetest über Telegram geprüft. | Treffer korrekt; eine Latenzgrenze wird nicht behauptet, da nicht gemessen |

Einschränkung zu Nr. 6: ohne simulierte Netzlatenz (0 ms Abrufzeit) lag die Steigerung nur bei +28 % (87 → 112
Seiten/s), weil der Koordinator die Ergebnisse je Auftrag serialisiert verbucht (ein DB-Schreibvorgang pro Seite) und
so zum Engpass wird; bei realistischen 100 ms je Abruf skaliert der Durchsatz nahezu linear.

Ergänzend wurde das Gesamtsystem am 2026-08-29 live über Telegram getestet: alle Befehle inkl. Live-Stream,
Pause/Resume/Abort (Ergebnisse bleiben erhalten) und ein Sitemap-Crawl mit 437 Seiten.

## Änderungen gegenüber Skizze v2

| Änderung | Grund |
|---|---|
| Bot ↔ Koordinator per REST statt TCP-Socket; Koordinator daher mit Spring Web MVC | Das Modul verlangt REST-Komponenten mit Spring Boot; der Bot ist eine dünne Oberfläche, die API wäre auch für ein Browser-Frontend nutzbar. Die Socket-Anforderung erfüllt Koordinator ↔ Worker. |
| Live-Stream per Polling (1 s, `seq`-Cursor) statt Server-Push | Bei REST die einfachste robuste Lösung; gemessene Verzögerung p95 0,5 s liegt deutlich unter 2 s. |
| Persistenz in Postgres über JPA statt JSON-Dateien | Ermöglicht Suche, Statistiken und Retention per SQL; der Poll-Cursor `seq` ist eine Spalte. Neue Befehle `/search` und `/stats`. |
| NFA 9 „parallele Statistikberechnung aus JSON-Dateien“ ersetzt durch Korrektheit der Volltextsuche | Ohne JSON-Dateien gibt es keinen parallelen Datei-Parser mehr. Ein Latenzziel für `/search` wurde nicht gemessen und daher nicht formuliert. |
| Dedup-Test über Hub-Site statt „1000 identische URLs über zwei Worker injizieren“; Mehrkern-Nachweis qualitativ über Thread-IDs statt Varianzgrenze | Worker melden im Protokoll nur echte Seitenergebnisse; die Hub-Site erzeugt die Mehrfachmeldung realistisch. Ein Varianzmaß wurde nicht implementiert. |
| Startzeit vom Koordinator selbst gemessen (`startupSeconds` in `/api/health`) | Eine externe Messung hing vom Startzeitpunkt des Testclients ab. |
| Least-Work-First ergibt sich aus dem Worker-Pull; Round-Robin nur über Aufträge | Worker fragen nur mit freier Kapazität; kein separater Warteschlangen-Zustand nötig. |
| Absturzerkennung zusätzlich über 60-s-Read-Timeout; Koordinator-Neustart markiert offene Aufträge als FAILED | Geschlossene Sockets werden nicht immer sofort erkannt; die Frontier ist bewusst nur im Speicher (Prototyp-Umfang). |
| Sitemap-`<loc>`-Einträge werden als Links extrahiert | Aus dem Livetest: `sitemap.xml` als Start-URL lieferte sonst keine Folge-URLs. |

### Arbeitsaufteilung

Drei Personen. Der Worker-Crawl-Kern (`PageFetcher`, `HtmlExtractor`, `CrawlExecutor`, Tests) stammt von
Kanan Namazov; das Bot-Grundgerüst (Command-Registry, Handler, DTOs, erste Fassung von `CoordinatorClient` und
`ResultPoller`) vom zweiten Teammitglied (Git-Autor „debian“). Koordinator, Socket-Protokoll, Integration der
Module, End-to-End-Test, Lasttest-Modul und die Fehlerbehebungen in Worker und Bot wurden von Luca Wegner unter
Einsatz von KI-Werkzeugen (Claude Code) entwickelt.
