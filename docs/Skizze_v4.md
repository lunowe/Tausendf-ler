# Skizze & Dokumentation „Tausendfüßler“ (v4 – Handout)

Modul *Entwicklung verteilter Anwendungen*, Universität Leipzig, SS26. Dieses Dokument ist die über den
Projektzeitraum fortgeschriebene Skizze (v1 → v4) und zugleich das Handout zur Präsentation. Es beschreibt die
Architektur, wie sie im Repository umgesetzt und getestet ist.

## Beschreibung

„Tausendfüßler“ ist ein verteilter Webcrawler, der über einen Telegram-Bot gesteuert wird. Der Bot-Prozess nimmt
Crawl-Aufträge entgegen und reicht sie per REST an einen Koordinator weiter. Der Koordinator verteilt die zu
besuchenden URLs über TCP-Sockets auf mehrere Worker-Prozesse, die parallel Webseiten herunterladen, parsen und ihre
Ergebnisse zurückmelden. Aufträge und Seiten liegen in Postgres. Der Bot holt neue Ergebnisse im Sekundentakt ab und
liefert sie als Live-Stream in den Telegram-Chat. Koordinator, Bot und Datenbank laufen in der Cloud (Railway).
Worker sind eigenständige Prozesse auf beliebigen Rechnern, die sich über das Internet verbinden. Die Verteilung
über Rechnergrenzen ist damit nicht nur theoretisch möglich, sondern der normale Betriebsmodus.

## Funktionale Anforderungen

### Benutzerinteraktion (via Telegram-Bot)

* `/crawl <URL> [Tiefe] [Filter…]`: Auftrag mit Start-URL, maximaler Tiefe und optionalen URL-Filtern anlegen.
* `/list`: eigene aktive und abgeschlossene Aufträge mit Kurzinfo.
* `/status <Job-ID>`: aktuelle/maximale Tiefe, besuchte Seiten, gefundene Links, Fehler, Status
  (PENDING/RUNNING/PAUSED/COMPLETED/ABORTED/FAILED).
* `/pause`, `/resume`, `/abort <Job-ID>`: Auftrag pausieren, fortsetzen, abbrechen; bei Abbruch bleiben die
  gespeicherten Seiten erhalten.
* Live-Stream neu gefundener Seiten (URL, Titel, Textanfang, Tiefe) während des Crawls; danach ein Report mit
  besuchten Seiten, extrahierten Links, Fehlern und Dauer.
* `/search <Begriff>`: Volltextsuche über Titel und Textanfang aller gespeicherten Seiten.
* `/stats`: Aufträge (gesamt/aktiv), gecrawlte Seiten, meistgecrawlte Domains.
* `/workers`: alle aktuell verbundenen Worker mit Thread-Zahl, URLs in Arbeit und Verbindungszeit – macht die
  Verteilung des Systems zur Laufzeit sichtbar (neu in v4).
* Zusätzlich zum Bot existiert ein Browser-Frontend (Next.js) auf derselben REST-API: Dashboard mit Worker-Tafel
  und Statistik, Auftrags-Detailseite mit Live-Stream und Steuerung, Volltextsuche.

### Systeminterne Abläufe (Koordinator & Worker)

* Der Koordinator nimmt Aufträge über `POST /api/jobs` an, legt sie in Postgres an und hält je Auftrag eine
  In-Memory-Frontier (eine Warteschlange pro Tiefe) sowie die Menge bereits gesehener URLs.
* Worker verbinden sich per TCP (`REGISTER`, inkl. Token, s. u.) und holen sich aktiv URL-Pakete (`REQUEST_WORK`
  mit freier Kapazität). Da ein Worker nur mit freien Slots fragt, erhält der Worker mit den wenigsten offenen URLs
  das nächste Paket (Least-Work-First); mehrere laufende Aufträge werden reihum bedient.
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

### Absicherung (minimal, wegen öffentlichem Betrieb)

Security ist laut Modul als NFA ausgeklammert. Da Koordinator und Bot aber öffentlich gehostet werden, wurde ein
bewusst minimales Schutzniveau ergänzt (jeweils ein Shared Secret, keine Nutzerverwaltung):

* REST-API: Header `X-Api-Key` auf allen `/api/**`-Routen (außer `/api/health`). Falscher/fehlender Key ⇒
  `401 {error: "unauthorized"}`.
* Worker: `REGISTER` trägt ein Token. Stimmt es nicht, antwortet der Koordinator `ERROR unauthorized` und schließt
  den Socket, der Worker beendet sich ohne Reconnect (Exit-Code 3).
* Bot: Allowlist erlaubter Telegram-Chat-IDs. Fremde Chats erhalten nur „Dieser Bot ist privat“.
* Bekannte, akzeptierte Lücken (Prototyp): die TCP-Strecke ist unverschlüsselt (Token und Ergebnisse im Klartext),
  das Frontend läuft nur lokal, weil sein API-Key sonst im Browser-Bundle öffentlich wäre.

## Wartung & optionale Erweiterungen

* `/stats` wird per SQL aus den Tabellen `jobs` und `pages` berechnet (Top-5-Domains aus den URLs).
* `/search` nutzt Postgres `to_tsvector`/`plainto_tsquery`, sortiert nach `ts_rank`.
* Retention-Cleanup: beim Start löscht der Koordinator Aufträge samt Seiten, die älter als `RESULT_RETENTION_DAYS`
  sind (Standard 30 Tage).

## Nicht-funktionale Anforderungen

Annahme: die Messungen liefen mit Koordinator, Workern und Postgres auf einem handelsüblichen Laptop (mehrkernige
CPU). Im Regelbetrieb laufen Koordinator/Bot/Postgres in der Cloud und die Worker auf getrennten Rechnern.

1. **Zuverlässigkeit** – Der Koordinator beantwortet Status- und Steueranfragen zu > 99,9 % ohne internen
   Fehler (5xx).
2. **Performanz** – Eine Statusabfrage wird bei < 20 gleichzeitigen Aufträgen in unter 0,2 s beantwortet.
3. **Performanz / Ressourceneffizienz** – Bei hoher Auslastung (mehr offene URLs als Verarbeitungs-Slots) nutzt
   das System die verfügbaren Rechenressourcen effizient: die anstehende Arbeit verteilt sich auf alle Threads des
   Worker-Pools und auf alle verbundenen Worker, kein Verarbeitungsstrang liegt brach, und zusätzliche Ressourcen
   (Kerne, Worker) erhöhen den Durchsatz, statt ungenutzt zu bleiben.
4. **Skalierbarkeit / Robustheit unter konkurrierender Last** – Auch wenn viele gleichzeitige Meldungen dieselben
   URLs betreffen – etwa wenn mehrere Worker parallel Seiten verarbeiten, die zehntausendfach auf dieselben Ziele
   verlinken –, funktioniert das System einwandfrei: jede URL wird genau einmal gecrawlt und gespeichert, kein
   Ergebnis geht verloren, und das Ergebnis ist unabhängig davon, wie viele Worker beteiligt sind.
5. **Performanz** – Live-Ergebnisse erreichen den Bot innerhalb von 2 s nach Abschluss des Seitenabrufs.
6. **Skalierbarkeit** – Der Crawl-Durchsatz mit zwei Workern ist um mindestens 60 % höher als mit einem Worker
   (gleicher Auftrag).
7. **Verfügbarkeit** – Der Koordinator ist innerhalb von 15 s nach Prozessstart bereit (REST und Worker-Socket).
8. **Zuverlässigkeit** – Der Worker-Thread-Pool liefert unter gleichzeitiger Last alle Ergebnisse vollständig
   zurück (keine verlorenen Futures).
9. **Korrektheit** – Die Volltextsuche liefert für gespeicherte Seiten korrekte Treffer, auch während weitere
   Aufträge laufen.
10. **Zugriffsschutz (Minimum)** – Zugriffe ohne gültigen API-Key bzw. Worker-Token werden abgewiesen; darüber
    hinaus bleibt Security bewusst ausgeklammert (v4, wegen des öffentlichen Betriebs).

## Verteilung

### Nebenläufigkeit

* Worker: paralleles Herunterladen und Parsen mit einem `ExecutorService` (Standard: ein Thread pro CPU-Kern, per
  `--threads` änderbar); der Koordinator gibt höchstens `2 × threads` URLs pro Paket, sodass der Pool unter Last
  durchgehend gefüllt ist, ohne dass ein Worker Arbeit hortet.
* Koordinator: ein Thread je Worker-Verbindung; Frontier, Dedup-Set und In-flight-Map sind nebenläufige Strukturen.
  Nur die zusammengesetzten Übergänge (Zuteilung, Ergebnisverbuchung, Abschlussprüfung) sind pro Auftrag
  synchronisiert, damit ein Auftrag nie als fertig gilt, während eine URL zwischen Frontier und In-flight steht.
* REST-Anfragen bedient der Tomcat-Thread-Pool von Spring MVC parallel; der Bot pollt alle abonnierten Aufträge in
  einem Scheduler-Thread.

### Verteilung

* Bot ↔ Koordinator: REST (HTTP/JSON, Spring MVC, Port 8080). Der Bot ist eine dünne Oberfläche ohne Fachlogik;
  das Browser-Frontend nutzt dieselbe API. Erfüllt die Modulanforderung „REST-Services via Spring Boot“.
* Koordinator ↔ Worker: pro Worker eine persistente TCP-Verbindung (Port 9090, line-delimited JSON, Nachrichten-
  Records in `common`); beide Seiten dürfen jederzeit senden. Erfüllt die Modulanforderung „Kommunikation via
  Sockets“.
* Koordinator ↔ Postgres: JDBC/JPA; lokal per Docker Compose, im Betrieb als Cloud-Datenbank.
* **Betrieb über Rechnergrenzen (v4):** Koordinator, Bot und Postgres laufen als drei Services auf Railway
  (Docker-Images aus Multi-Stage-Builds). Die REST-API ist über eine öffentliche HTTPS-Domain erreichbar, der
  Worker-Socket über einen TCP-Proxy. Worker starten auf beliebigen Rechnern mit
  `java -jar worker.jar --coordinator <proxy-host>:<port> --token <…>` und erscheinen sekundenschnell in
  `/workers`. Verifiziert am 2026-08-29: Telegram → Cloud-Bot → Cloud-Koordinator → Laptop-Worker →
  Cloud-Postgres → Live-Stream zurück in den Chat.

### Komponenten

* `common` (Plain Java + Jackson): Nachrichten-Records des Socket-Protokolls.
* `coordinator` (Spring Boot Web + Data JPA): REST-Controller, Job-/Result-/Stats-Services, Scheduler,
  Worker-Registry, TCP-Server, Entities `JobEntity`/`PageEntity`, API-Key-Filter, Retention-Cleanup,
  Startup-Recovery.
* `worker` (Plain Java, kein Spring): TCP-Client-Schleife, `CrawlExecutor` (Thread-Pool), `PageFetcher`,
  `HtmlExtractor` (jsoup, inkl. Sitemap-`<loc>`). Spring Boot würde hier nur Start-Overhead bringen.
* `bot` (Spring Boot ohne Web-Server + telegrambots): Command-Registry mit einem Handler je Befehl,
  `CoordinatorClient` (REST + API-Key), `ResultPoller` (Live-Stream), Chat-Allowlist. Die Telegram-HTTP-API ist
  ein externer Dienst.
* `loadtest` (Plain Java): Testclient für die NFAs mit synthetischer Website und In-Prozess-Workern.
* `frontend` (Next.js, optional): Browser-Leitstand auf derselben REST-API; nur lokal betrieben.

## Skizze

```
 Telegram-API ──HTTPS (Long Polling)──▶ ┌─────────────────────────────┐
                                        │ Bot (Spring Boot)           │   Cloud (Railway)
                                        │ CommandRegistry, Handler    │◀─ Chat-Allowlist
                                        │ CoordinatorClient (API-Key) │
                                        │ ResultPoller (1 s, seq)     │
                                        └──────────────┬──────────────┘
   Browser-Frontend (Next.js, lokal) ──────────────────┤ REST :8080, X-Api-Key
                                                       │ (/api/jobs, /results?afterSeq, /pause|resume|abort,
                                                       │  /search, /stats, /workers)
                                        ┌──────────────▼──────────────┐        ┌──────────────┐
                                        │ Koordinator (Spring Boot)   │──JPA──▶│ Postgres     │
                                        │ ApiKeyFilter                │        │ jobs, pages  │  Cloud (Railway)
                                        │ JobService / ResultService  │        │ Volltextsuche│
                                        │ JobRuntime: Frontier, Dedup │        └──────────────┘
                                        │ Scheduler (Least-Work-First)│
                                        │ WorkerRegistry, Requeue     │
                                        │ WorkerSocketServer :9090    │
                                        └───┬──────────┬──────────┬───┘
                      TCP-Proxy · line-delimited JSON (REGISTER+Token, REQUEST_WORK,
                              WORK_PACKAGE, PAGE_RESULT, JOB_SIGNAL)
                                            │          │          │
                                     ┌──────▼───┐ ┌────▼─────┐ ┌──▼───────┐  Plain Java, beliebige
                                     │ Worker 1 │ │ Worker 2 │ │ Worker n │  Rechner (Laptops),
                                     └──────────┘ └──────────┘ └──────────┘  Thread-Pool = CPU-Kerne
                                            │ HTTP GET     │             │
                                            ▼              ▼             ▼
                                                     Ziel-Websites
```

Abbildung 1: Systemarchitektur im Betrieb – Bot, Koordinator und Postgres in der Cloud; Worker verbinden sich von
beliebigen Rechnern über den TCP-Proxy.

## Last-Simulation & Test der nicht-funktionalen Anforderungen

Testclient ist das Modul `loadtest` (`java -jar loadtest.jar --scenario all --report docs/NFA-Report.md`). Es
startet eine synthetische Website auf einem freien Port und, wo nötig, echte `WorkerClient`-Instanzen im eigenen
Prozess. Messwerte aus dem Lauf vom 2026-08-29 (MacBook, Postgres 16 in Docker), Details in `docs/NFA-Report.md`.

| Nr. | NFA | Überprüfung (wie umgesetzt) | Ergebnis |
|---|---|---|---|
| 1 | > 99,9 % ohne internen Fehler | Szenario `error-ratio`: 60 s Round-Robin über 11 Anfragetypen (gültige Status-/Listen-/Ergebnis-/Stats-Abfragen sowie 404/400/409-Fälle); gezählt werden 5xx und ausbleibende Antworten. | 1200 Anfragen, 0 interne Fehler – erfüllt |
| 2 | Statusabfrage < 0,2 s bei < 20 Aufträgen | Szenario `status-latency`: 20 Aufträge auf einer 300-Seiten-Site (300 ms Verzögerung), währenddessen 300 getaktete `GET /api/jobs/{id}`; Überschreitungen gezählt. | p50 7 ms, p95 13 ms, max 98 ms, 0 Überschreitungen – erfüllt |
| 3 | Effiziente Ressourcennutzung bei hoher Auslastung | Zwei Belege unter Volllast (Frontier deutlich größer als Slot-Zahl): (a) Thread-IDs in den Logs zeigen, dass Ergebnisse desselben Auftrags über alle Pool-Threads und alle Worker-Handler verteilt verarbeitet werden; (b) das Durchsatz-Szenario zeigt, dass zusätzliche Ressourcen den Durchsatz nahezu proportional erhöhen (s. Nr. 6) – brachliegende Ressourcen würden das verhindern. | Handler-Threads 1130/537/309 Ergebnisse; +92 % bei Ressourcenverdopplung – erfüllt (Verteilung qualitativ, ohne formales Varianzmaß) |
| 4 | Korrektes Verhalten bei n-fach konkurrierenden identischen URLs | Unit: `JobRuntimeTest` bietet 1000 URLs in 4 Schreibweisen aus 8 Threads gleichzeitig an – jede wird genau einmal vergeben. End-to-End: Szenario `dedup` – 500-Seiten-Site, jede Seite verlinkt dieselben 50 Hub-Seiten, 2 Worker × 4 Threads melden dadurch 18 076 konkurrierende Link-Funde; `/results` wird auf Duplikate und Vollständigkeit (`count == pagesVisited`) geprüft. | 342 Seiten, 0 Duplikate, kein verlorenes Ergebnis – erfüllt |
| 5 | Live-Ergebnisse < 2 s | Szenario `live-latency`: Client pollt wie der Bot mit `afterSeq` (500 ms) und misst `Empfang − crawledAt` des Workers (gleiche Uhr, da In-Prozess). | p50 254 ms, p95 507 ms, max 517 ms – erfüllt |
| 6 | ≥ 60 % mehr Durchsatz mit 2 Workern | Szenario `throughput`: derselbe Auftrag (2000-Seiten-Site, maxDepth 3, 100 ms Abrufzeit) erst mit 1, dann mit 2 In-Prozess-Workern (je 4 Threads). | 20,5 → 39,2 Seiten/s (+92 %) – erfüllt |
| 7 | Koordinator < 15 s startbereit | Szenario `startup`: der Koordinator misst JVM-Start bis `ApplicationReady` selbst und meldet `startupSeconds` in `/api/health`; Abgleich mit dem Spring-Startup-Log. | 3,4 s – erfüllt |
| 8 | Thread-Pool liefert alle Ergebnisse | `CrawlExecutorTest.handlesHighVolumeConcurrentCrawls`: 100 gleichzeitige URLs auf 4 Threads, exakt 100 `CrawlOutcome`. | 100/100 – erfüllt |
| 9 | Volltextsuche liefert korrekte Treffer | `EndToEndTest` (REST → Koordinator → 2 Worker → Test-Site → H2 mit LIKE-Fallback) fragt `/api/search` nach einem bekannten Begriff ab; gegen Postgres (`tsvector`) im Livetest über Telegram geprüft. | Treffer korrekt; eine Latenzgrenze wird nicht behauptet, da nicht gemessen |
| 10 | Zugriffsschutz (Minimum) | Negativtests gegen die laufende Cloud-Instanz: `GET /api/stats` ohne/mit falschem Key ⇒ 401; Worker mit falschem Token ⇒ `ERROR unauthorized`, Socket zu, Exit-Code 3; korrekte Secrets ⇒ 200 bzw. Registrierung. Abgedeckt durch `ApiKeyFilterTest` und `WorkerSocketServerTest` (Token-Fälle). | Alle Negativ- und Positivfälle wie erwartet – erfüllt |

Einschränkung zu Nr. 6 (und damit auch Nr. 3): ohne simulierte Netzlatenz (0 ms Abrufzeit) lag die Steigerung nur
bei +28 % (87 → 112 Seiten/s), weil der Koordinator die Ergebnisse je Auftrag serialisiert verbucht (ein
DB-Schreibvorgang pro Seite) und so zum Engpass wird; bei realistischen 100 ms je Abruf skaliert der Durchsatz
nahezu linear. Das ist zugleich das wichtigste bekannte Verbesserungspotenzial (Batch-Schreiben bzw. Verbuchung
außerhalb des Auftrags-Locks).

Ergänzend wurde das Gesamtsystem zweimal live getestet (2026-08-29): lokal über Telegram (alle Befehle inkl.
Live-Stream, Pause/Resume/Abort, Sitemap-Crawl mit 437 Seiten) und anschließend in der Cloud-Topologie
(Railway-Koordinator und -Bot, Laptop-Worker über TCP-Proxy, `example.com`-Crawl in 1,4 s Ende-zu-Ende).

## Verbesserungspotenzial

* Ergebnisverbuchung im Koordinator entkoppeln (Batch-Inserts bzw. Schreiben außerhalb des Auftrags-Locks) – hebt
  die Durchsatzgrenze bei schnellen Quellen (s. Einschränkung zu NFA 6).
* Frontier persistieren, damit laufende Aufträge einen Koordinator-Neustart überleben (heute: `FAILED`).
* Server-Push (SSE) statt Polling für den Live-Stream; Heartbeat im Socket-Protokoll statt 60-s-Read-Timeout.
* TLS auf der Worker-Strecke; pausierte Aufträge sollten keine Pool-Threads belegen.
