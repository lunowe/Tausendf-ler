# Tausendfüßler 🐛

Verteilter Webcrawler, gesteuert über einen Telegram-Bot. Prototyp für das Modul
*Entwicklung verteilter Anwendungen* (Uni Leipzig, SS26).

```
Telegram-API ──HTTP──▶ Bot (Spring Boot, kein Web-Server)
                         │ REST
                         ▼
                   Koordinator (Spring Boot: REST-API + TCP-Server :9090)
                    ├── Job-Queue, Frontier, URL-Dedup, Least-Work-First-Scheduler
                    ├── JPA ──▶ Postgres (Jobs, Seiten, Volltextsuche)
                    └── Worker-Registry (Absturz-Erkennung, Re-Queue)
                         │ TCP, line-delimited JSON
             ┌───────────┼───────────┐
          Worker 1    Worker 2    Worker n   (Plain Java, Thread-Pool = CPU-Kerne)
```

Schnittstellen: [PROTOCOL.md](PROTOCOL.md). Skizze und Aufgabenstellung: [docs/](docs/).

## Module

| Modul | Technologie | Aufgabe |
|---|---|---|
| `common` | Plain Java + Jackson | Socket-Protokoll (`Message`-Records) |
| `coordinator` | Spring Boot Web + Data JPA | Nimmt Jobs per REST an, verteilt URLs an Worker, persistiert Ergebnisse |
| `worker` | Plain Java (kein Spring) | Verbindet sich zum Koordinator, crawlt URL-Pakete parallel |
| `bot` | Spring Boot + telegrambots | Telegram-Oberfläche: `/crawl`, `/list`, `/status`, `/pause`, `/resume`, `/abort`, `/stats` |

## Voraussetzungen

* JDK 21+, Maven 3.9+
* Docker (für lokales Postgres) – oder Zugangsdaten zur gemeinsamen Cloud-DB
  (Achtung: läuft lokal schon ein Postgres auf 5432, z. B. via Homebrew, `DB_URL` auf die Cloud-DB
  oder auf einen anderen Port zeigen lassen)
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

## Status

- [x] Worker: Crawl-Kern (Fetch, jsoup-Extraktion, Thread-Pool) – getestet
- [x] Bot: Befehle, Registry, Live-Stream-Polling, REST-Client
- [x] Gemeinsames Protokoll, Multimodul-Build
- [x] Koordinator: Job-Verwaltung, Frontier/Dedup/Scheduler, TCP-Server, Worker-Recovery
- [x] Koordinator: REST-API, JPA-Persistenz, Suche, Stats, Cleanup
- [x] Worker: TCP-Client-Schleife, Pause/Resume/Abort
- [x] End-to-End-Test (`coordinator/.../EndToEndTest`: REST → Koordinator → 2 echte Worker → lokale Test-Site → H2)
      und manueller Smoke-Test gegen Postgres inkl. Worker-Absturz während eines Jobs
- [ ] Lasttest-Client für die nicht-funktionalen Anforderungen
