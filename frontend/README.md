# Tausendfüßler – Browser-Leitstand

Next.js-Frontend für den verteilten Webcrawler. Es spricht **nur** die REST-API des Koordinators
(`PROTOCOL.md` §1) – dieselbe Schnittstelle wie der Telegram-Bot. Kein eigenes Backend, keine
Server-Actions, keine zweite Datenhaltung: jeder Aufruf geht direkt aus dem Browser an
`NEXT_PUBLIC_COORDINATOR_URL`.

Die maßgebliche Dokumentation steht im [Root-README](../README.md#frontend).

## Start

```bash
npm install
npm run dev     # http://localhost:3000
```

Voraussetzung: Koordinator läuft (Standard `http://localhost:8080`) und erlaubt die Origin über
`tausendfuessler.cors-origins`. Abweichende API-URL: `NEXT_PUBLIC_COORDINATOR_URL` in `.env.local`
setzen (Vorlage: `.env.example`).

## Seiten

| Route | Inhalt | API |
|---|---|---|
| `/` | Statistiken, Formular „Neuer Crawl“, Auftragsliste (Refresh alle 2 s) | `GET /api/stats`, `GET /api/jobs?owner=0`, `POST /api/jobs` |
| `/jobs/[id]` | Details, Steuerung, Live-Stream, Abschlussbericht | `GET /api/jobs/{id}`, `GET /api/jobs/{id}/results?afterSeq=…`, `POST …/pause\|resume\|abort` |
| `/search` | Volltextsuche mit 300 ms Debounce | `GET /api/search?q=…&limit=…` |

## Aufbau

```
src/lib/api.ts          Typen + Fetch-Wrapper für die REST-API, ApiError mit HTTP-Status
src/lib/usePolling.ts   Poll-Hook: nächster Tick erst nach dem vorigen, hält Daten bei Fehlern
src/lib/format.ts       Zahlen-, Zeit- und URL-Formatierung (de-DE)
src/components/         Kopf-/Fußzeile, UI-Primitive, Crawl-Formular, Live-Stream, Job-Ansicht
src/app/                Routen (App Router, alle Datenzugriffe clientseitig)
```

Details, die leicht übersehen werden:

* `/results` liefert höchstens 50 Zeilen pro Antwort. Ein Live-Tick holt deshalb in einer Schleife
  weiter, bis eine Antwort kürzer als 50 Zeilen ist (`useLiveResults` in `JobView.tsx`).
* Das Polling stoppt, sobald der Job einen Endzustand erreicht – vorher wird noch einmal
  vollständig nachgeladen, damit die letzten Seiten nicht fehlen.
* Pause/Fortsetzen/Abbrechen sind genau dann aktiv, wenn der Zustandsautomat des Koordinators den
  Übergang erlaubt; ein trotzdem abgelehnter Aufruf (409) wird als Fehlermeldung angezeigt.
* Browser-Aufträge nutzen `owner = 0`, damit sie sich nicht mit Telegram-Chat-IDs überschneiden.

## Gestaltung

Heller „Institutsblatt“-Look: warmes Papier, Haarlinien, kantige Flächen, Basalt als Schriftfarbe.
Akzente kommen aus dem Corporate Design der Universität Leipzig – Granat `#B02F2C` für Marke,
primäre Aktion und Fehler, Aquamarin `#8AC2D1` / `#2C7189` für alles Messbare (Tiefe, Live-Stream,
Kennzahlen). Alle Farben, Zustandsfarben und Bedienelement-Klassen stehen als CSS-Variablen in
`src/app/globals.css`; Tailwind übernimmt nur Layout.

* **Schrift**: IBM Plex in drei Stimmen – Serif für Titel, Sans für Fließtext, Mono für alles
  Gemessene (`.display`, `.label`, `.mono`).
* **Zustände**: Die sechs Auftragszustände haben eine gedeckte Farbskala (`--st-*`), alle mit
  mindestens 4,5:1 Kontrast auf Weiß. Die Beschriftungen sind wortgleich mit dem Telegram-Bot
  (Wartend, Läuft, Pausiert, Abgeschlossen, Abgebrochen, Fehlgeschlagen).
* **Bewegung** nur dort, wo sie etwas meldet: der pulsierende Punkt eines laufenden Auftrags, der
  Laufbalken im aktiven Tiefenfeld und ein kurzes Aufleuchten neuer Live-Stream-Segmente. Neue
  Segmente werden über ihre `seq` erkannt, die erste Ladung bleibt bewusst ruhig, und nichts
  verschiebt sich dabei. `prefers-reduced-motion` schaltet alles ab.
* **Zugänglichkeit**: sichtbarer Fokusring auf allen Bedienelementen, Sprunglink zum Inhalt,
  Tiefenauswahl als echte Radiogruppe (Pfeiltasten), `aria-live` für Trefferzahl und Live-Stream,
  Rückfrage vor dem Abbrechen.
