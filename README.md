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

Skizze/Handout (aktuell: [docs/Skizze_v4.md](docs/Skizze_v4.md)).
## Module

| Modul | Technologie | Aufgabe |
|---|---|---|
| `common` | Plain Java + Jackson | Socket-Protokoll (`Message`-Records) |
| `coordinator` | Spring Boot Web + Data JPA | Nimmt Jobs per REST an, verteilt URLs an Worker, persistiert Ergebnisse |
| `worker` | Plain Java (kein Spring) | Verbindet sich zum Koordinator, crawlt URL-Pakete parallel |
| `bot` | Spring Boot + telegrambots | Telegram-Oberfläche: `/crawl`, `/list`, `/status`, `/pause`, `/resume`, `/abort`, `/search`, `/stats`, `/workers` |
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

Lokal läuft alles ohne Secrets. Sobald `API_KEY` bzw. `WORKER_TOKEN` in `.env` gesetzt sind, verlangt der
Koordinator den Header `X-Api-Key` auf `/api/**` (Bot, Frontend und Lasttest lesen `API_KEY` aus der Umgebung)
und das Feld `token` in `REGISTER` (Worker: `--token <t>` oder `WORKER_TOKEN`), siehe [PROTOCOL.md](PROTOCOL.md).

## Bot

Befehle: `/crawl <URL> [Tiefe] [Filter …]`, `/list`, `/status <Job-ID>`, `/pause`, `/resume`, `/abort`,
`/search <Text>`, `/stats`, `/workers` (verbundene Worker mit Threads, URLs in Arbeit und Verbindungszeit), `/help`.

* `API_KEY`: wird als `X-Api-Key` an den Koordinator geschickt; leer = kein Header (Koordinator ohne Key).
* `TELEGRAM_ALLOWED_CHATS`: kommagetrennte Chat-IDs; nur diese dürfen den Bot benutzen, alle anderen bekommen
  „⛔ Dieser Bot ist privat.“ Leer = öffentlich (Warnung im Log).

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

* Seiten: `/` (Statistiken, **Worker-Tafel** mit allen verbundenen Workern – ID, Threads, URLs in
  Arbeit, verbunden seit – Refresh alle 2 s über `GET /api/workers`, Formular „Neuer Crawl“,
  Auftragsliste), `/jobs/<id>` (Details, Pause/Fortsetzen/Abbrechen gemäß Zustandsautomat,
  Live-Stream mit 1-s-Polling auf `/results?afterSeq=…`, Abschlussbericht) und `/search`
  (Volltextsuche mit Debounce).
* Browser-Aufträge laufen unter `owner = 0`; Telegram nutzt die Chat-ID, beide Welten teilen sich
  denselben Koordinator und dieselbe Datenbank.
* Konfiguration in `frontend/.env.local` (Vorlage `frontend/.env.example`):
  `NEXT_PUBLIC_COORDINATOR_URL` (Standard `http://localhost:8080`) und `NEXT_PUBLIC_API_KEY`
  (wird als `X-Api-Key` mitgeschickt; leer lassen, wenn der Koordinator ohne `API_KEY` läuft).
  Antwortet der Koordinator mit `401`, zeigt das Frontend ein Banner „API-Key fehlt oder falsch“.
* **Nur lokal betreiben**: `NEXT_PUBLIC_*`-Werte landen im Browser-Bundle, der API-Key wäre also
  für jeden Besucher lesbar. Das Frontend ist ein Leitstand für den eigenen Rechner bzw. die
  Demo im LAN, kein öffentlich gehosteter Dienst.
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
  (Dauer des Fehlerquoten-Tests), `--pages 2000` (Größe der Durchsatz-Site), `--report <Pfad>`, `--run-label "…"`,
  `--api-key` / `--worker-token` (Standard: Umgebungsvariablen `API_KEY` / `WORKER_TOKEN`).
* Der Report enthält je Szenario die Messwerte und verweist für die restlichen NFAs auf
  `CrawlExecutorTest.handlesHighVolumeConcurrentCrawls` (Threadpool-Vollständigkeit) und die Thread-IDs in den
  Logs von Worker und Koordinator (Mehrkernauslastung).

## Deployment auf Railway

Koordinator, Bot und Postgres laufen als drei Services in einem [Railway](https://railway.app)-Projekt; die
Worker laufen weiterhin auf den Laptops und verbinden sich über Railways TCP-Proxy. Die Images entstehen aus
`coordinator/Dockerfile` und `bot/Dockerfile` (Multi-Stage: Maven baut nur die nötigen Module, Runtime ist ein
schlankes JRE-Image ohne Root). Lokal prüfen: `docker build -f coordinator/Dockerfile .` bzw. `docker build -f bot/Dockerfile .`
aus dem Repo-Root.

1. **Projekt anlegen** und darin einen **Postgres**-Service hinzufügen (Railway-Template). Railway stellt dessen
   Verbindungsdaten als Variablen `PGHOST`, `PGPORT`, `PGDATABASE`, `PGUSER`, `PGPASSWORD` bereit.
2. **Koordinator-Service** aus dem GitHub-Repo anlegen. Unter *Settings → Build* den *Dockerfile Path* auf
   `coordinator/Dockerfile` setzen (Root Directory bleibt `/`, das Dockerfile braucht das Root-`pom.xml`).
   Variablen (*Variables → Raw Editor*), die Datenbank als Railway-Referenz auf den Postgres-Service:

   ```
   DATABASE_URL=${{Postgres.DATABASE_URL}}
   API_KEY=<zufälliges Secret, z. B. openssl rand -hex 24>
   WORKER_TOKEN=<zweites zufälliges Secret>
   COORDINATOR_WORKER_PORT=9090
   CORS_ORIGINS=http://localhost:3000
   ```

   (`DATABASE_URL` im Format `postgres://user:pw@host:port/db` übersetzt der Koordinator selbst in die
   JDBC-Einstellungen – `config/DatabaseUrlEnvironmentPostProcessor`. Alternativ weiterhin `DB_URL`
   als JDBC-URL plus `DB_USER`/`DB_PASSWORD`; ist `DB_URL` gesetzt, hat es Vorrang – auch `DB_URL` darf die
   `postgres://`-Form haben.) Unter *Settings → Networking* für den REST-Port 8080 eine
   *Public Domain* erzeugen und deren **Target Port auf 8080** stellen (sonst antwortet Railway mit „Application not found“; Railways `PORT`-Variable wird bewusst ignoriert, weil sie mit dem TCP-Proxy auf 9090 kollidiert) und zusätzlich einen **TCP Proxy auf Port 9090** anlegen; Railway zeigt dann
   `<proxy-host>:<proxy-port>` an – das ist die Adresse für die Worker.
3. **Bot-Service** aus demselben Repo anlegen, *Dockerfile Path* `bot/Dockerfile`, Variablen:

   ```
   TELEGRAM_BOT_TOKEN=<von @BotFather>
   TELEGRAM_BOT_USERNAME=<Bot-Name>
   COORDINATOR_BASE_URL=http://<private Domain des Koordinators>:8080
   API_KEY=<derselbe Wert wie beim Koordinator>
   TELEGRAM_ALLOWED_CHATS=<eigene Chat-IDs, kommasepariert; leer = alle>
   ```

   Die private Domain (z. B. `coordinator.railway.internal`) steht beim Koordinator unter *Networking →
   Private Networking*; der Bot spricht damit über Railways internes Netz und nie über das Internet.
   **Wörtlich eintragen** (`http://coordinator.railway.internal:8080`) – eine Variablen-Referenz wie
   `${{Coordinator.RAILWAY_PRIVATE_DOMAIN}}` wurde in der Praxis nicht aufgelöst („Bad authority"). Notfalls
   tut es auch die Public Domain per `https://`.
4. **Worker auf jedem Laptop** starten (JDK 21 reicht, `mvn -q package -DskipTests` einmal ausführen):

   ```bash
   java -jar worker/target/worker.jar --coordinator <proxy-host>:<proxy-port> --token <WORKER_TOKEN>
   ```

   Ein falscher Token wird als ERROR geloggt und der Worker beendet sich mit Exit-Code 3 statt zu reconnecten.
5. Prüfen: `curl https://<public domain>/api/health` (ohne Key), `curl -H "X-Api-Key: …" https://<public domain>/api/workers`
   listet die verbundenen Laptops; im Telegram-Chat `/workers`.

Das Frontend (`frontend/`) bleibt bewusst **lokal**: `npm run dev` mit `NEXT_PUBLIC_COORDINATOR_URL=https://<public domain>`
und `NEXT_PUBLIC_API_KEY=<API_KEY>`. Es wird nicht deployt, weil `NEXT_PUBLIC_*`-Werte im Browser-Bundle landen und
der API-Key damit öffentlich wäre.

### Security (bewusst minimal)

Prototyp-Niveau, in der Prüfung erklärbar, mehr nicht:

* **Zwei Shared Secrets** statt Nutzerverwaltung: `API_KEY` schützt die REST-API (Header `X-Api-Key`, ein
  Servlet-Filter mit konstantzeitigem Vergleich, kein Spring Security), `WORKER_TOKEN` schützt die
  Worker-Registrierung (Feld `token` in `REGISTER`). Beide leer → Prüfung aus, WARN im Log – nur lokal.
* **Worker-Verkehr ist Klartext-TCP.** Der Token und die Crawl-Ergebnisse laufen unverschlüsselt über Railways
  TCP-Proxy; TLS auf dem Socket war nicht Teil des Umfangs. Die REST-API ist über Railways Public Domain per HTTPS
  erreichbar, Bot ↔ Koordinator bleibt im privaten Netz.
* **Frontend nur lokal**, siehe oben. `TELEGRAM_ALLOWED_CHATS` beschränkt den Bot auf bekannte Chats.

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
- [x] Skizze/Handout final (v4, [docs/Skizze_v4.md](docs/Skizze_v4.md); v3/v2 dokumentieren die Historie)
- [x] Browser-Frontend (`frontend/`) auf derselben REST-API, gegen laufenden Koordinator + Worker getestet
- [x] Präsentations-Baseline ([docs/presentation/Tausendfuessler.pptx](docs/presentation/Tausendfuessler.pptx), generiert aus `build_pptx.py`)
