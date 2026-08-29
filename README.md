# Tausendfüßler 🐛

Verteilter Webcrawler, gesteuert über einen Telegram-Bot. Prototyp für das Modul
*Entwicklung verteilter Anwendungen* (Uni Leipzig, SS26).

```
Telegram-API ──HTTP──▶ Bot (Spring Boot, kein Web-Server)
                         │ REST
Browser ─────────────────┤ REST   (optionales Frontend, Next.js :3000)
                         ▼
                   Koordinator (Spring Boot: REST-API + TCP-Server :9090)
                    ├── Job-Queue, Frontier, URL-Dedup, Least-Work-First-Scheduler
                    ├── JPA ──▶ Postgres (Jobs, Seiten, Volltextsuche)
                    └── Worker-Registry (Absturz-Erkennung, Re-Queue)
                         │ TCP, line-delimited JSON
             ┌───────────┼───────────┐
          Worker 1    Worker 2    Worker n   (Plain Java, Thread-Pool = CPU-Kerne)
```

Schnittstellen: [PROTOCOL.md](PROTOCOL.md). Skizze (aktuell: [docs/Skizze_v3.md](docs/Skizze_v3.md)) und Aufgabenstellung: [docs/](docs/).

## Module

| Modul | Technologie | Aufgabe |
|---|---|---|
| `common` | Plain Java + Jackson | Socket-Protokoll (`Message`-Records) |
| `coordinator` | Spring Boot Web + Data JPA | Nimmt Jobs per REST an, verteilt URLs an Worker, persistiert Ergebnisse |
| `worker` | Plain Java (kein Spring) | Verbindet sich zum Koordinator, crawlt URL-Pakete parallel |
| `bot` | Spring Boot + telegrambots | Telegram-Oberfläche: `/crawl`, `/list`, `/status`, `/pause`, `/resume`, `/abort`, `/search`, `/stats` |
| `frontend` | Next.js 16 + TypeScript + Tailwind | Optionaler Browser-Leitstand auf derselben REST-API (siehe [Frontend](#frontend)) |

## Voraussetzungen

* JDK 21+, Maven 3.9+
* Docker (für lokales Postgres, per Compose auf Host-Port **5433**, damit ein evtl. vorhandenes
  lokales Postgres auf 5432 nicht stört) – oder Zugangsdaten zur gemeinsamen Cloud-DB in `DB_URL`
* Telegram-Bot-Token von [@BotFather](https://t.me/BotFather)

## Starten

```bash
cp .env.example .env          # Werte eintragen
docker compose up -d          # Postgres lokal (entfällt bei Cloud-DB)
mvn -q package -DskipTests

set -a; source .env; set +a   # Umgebungsvariablen laden
java -jar coordinator/target/coordinator.jar
java -jar worker/target/worker.jar --coordinator localhost:9090      # beliebig oft, auch auf anderen Rechnern
java -jar bot/target/bot.jar
```

Tests: `mvn test` (Koordinator-Tests laufen gegen H2, kein Docker nötig).

## Frontend

Die Skizze nennt einen Browser-Client als mögliche Erweiterung – `frontend/` ist genau das: ein
Next.js-Leitstand, der ausschließlich die REST-API des Koordinators aus [PROTOCOL.md](PROTOCOL.md) §1
benutzt, also dieselbe Schnittstelle wie der Telegram-Bot. Kein eigenes Backend, keine zweite
Datenhaltung; alle Aufrufe passieren im Browser.

```bash
cd frontend
npm install
npm run dev            # http://localhost:3000, erwartet Koordinator auf :8080
```

* Seiten: `/` (Statistiken, Formular „Neuer Crawl“, Auftragsliste, Refresh alle 2 s),
  `/jobs/<id>` (Details, Pause/Fortsetzen/Abbrechen gemäß Zustandsautomat, Live-Stream mit
  1-s-Polling auf `/results?afterSeq=…`, Abschlussbericht) und `/search` (Volltextsuche mit Debounce).
* Browser-Aufträge laufen unter `owner = 0`; Telegram nutzt die Chat-ID, beide Welten teilen sich
  denselben Koordinator und dieselbe Datenbank.
* Konfiguration: `NEXT_PUBLIC_COORDINATOR_URL` (Standard `http://localhost:8080`), siehe
  `frontend/.env.example`.
* Der Koordinator muss die Origin erlauben: `tausendfuessler.cors-origins`
  (Standard `http://localhost:3000`, Umgebungsvariable `CORS_ORIGINS`).
* Build/Checks: `npm run build` (inkl. TypeScript) und `npm run lint`.

## Lasttest

Das Modul `loadtest` ist der Testclient aus der Skizze (Plain Java, kein Spring). Es misst die
nicht-funktionalen Anforderungen gegen einen laufenden Koordinator: Startzeit, Status-Latenz bei 20 Jobs,
Anteil interner Fehler, Durchsatz mit 1 vs. 2 Workern, Live-Latenz der Ergebnisse und URL-Dedup. Dafür startet
es selbst eine synthetische Website auf einem freien Port und – wo nötig – echte `WorkerClient`-Instanzen im
eigenen Prozess.

```bash
mvn -q package -DskipTests
java -jar coordinator/target/coordinator.jar          # Terminal 1 (Postgres läuft)
java -jar loadtest/target/loadtest.jar --scenario all --report docs/NFA-Report.md --run-label "$(date -Iseconds)"
```

* **Keine externen Worker** verbinden – das Durchsatz-Szenario braucht die eigenen In-Prozess-Worker als
  einzige Quelle und bricht ab, wenn es einen fremden Worker erkennt.
* Das Startup-Szenario liest die Startzeit aus `/api/health` (`startupSeconds`, JVM-Start bis
  ApplicationReady, vom Koordinator selbst gemessen) – der Client kann jederzeit gestartet werden.
* Optionen: `--coordinator http://localhost:8080`, `--worker-host localhost`, `--worker-port 9090`,
  `--scenario all|startup|status-latency|error-ratio|throughput|live-latency|dedup`, `--seconds 60`
  (Dauer des Fehlerquoten-Tests), `--pages 2000` (Größe der Durchsatz-Site), `--report <Pfad>`, `--run-label "…"`.
* Der Report enthält je Szenario die Messwerte und verweist für die restlichen NFAs auf
  `CrawlExecutorTest.handlesHighVolumeConcurrentCrawls` (Threadpool-Vollständigkeit) und die Thread-IDs in den
  Logs von Worker und Koordinator (Mehrkernauslastung).

## Status

- [x] Worker: Crawl-Kern (Fetch, jsoup-Extraktion, Thread-Pool) – getestet
- [x] Bot: Befehle, Registry, Live-Stream-Polling, REST-Client
- [x] Gemeinsames Protokoll, Multimodul-Build
- [x] Koordinator: Job-Verwaltung, Frontier/Dedup/Scheduler, TCP-Server, Worker-Recovery
- [x] Koordinator: REST-API, JPA-Persistenz, Suche, Stats, Cleanup
- [x] Worker: TCP-Client-Schleife, Pause/Resume/Abort
- [x] End-to-End-Test (`coordinator/.../EndToEndTest`: REST → Koordinator → 2 echte Worker → lokale Test-Site → H2)
      und manueller Smoke-Test gegen Postgres inkl. Worker-Absturz während eines Jobs
- [x] Lasttest-Client für die nicht-funktionalen Anforderungen (`loadtest`, Ergebnisse in [docs/NFA-Report.md](docs/NFA-Report.md))
- [x] Livetest über Telegram (2026-08-29): alle Befehle inkl. Live-Stream, Pause/Resume/Abort, Sitemap-Crawl mit 437 Seiten
- [x] Skizze auf finale Architektur aktualisiert ([docs/Skizze_v3.md](docs/Skizze_v3.md))
- [x] Browser-Frontend (`frontend/`) auf derselben REST-API, gegen laufenden Koordinator + Worker getestet
- [x] Präsentations-Baseline ([docs/presentation/Tausendfuessler.pptx](docs/presentation/Tausendfuessler.pptx), generiert aus `build_pptx.py`)
