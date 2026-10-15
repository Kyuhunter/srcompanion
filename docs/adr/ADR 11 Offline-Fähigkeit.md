# ADR 11: Offline-Fähigkeit

Oct 7, 2026 · @Adrian

## Status und Kontext

**Status:** Akzeptiert. Setzt die Offline-Pflicht aus dem Event Storming technisch um und baut auf ADR 5 und ADR 12 auf.

Im Spiellokal gibt es oft kein verlässliches Netz. Trotzdem muss der Schiedsrichter dort das Uhrenprotokoll führen und Partien mit Ergebnis beenden. ADR 12 hat festgelegt, dass die Logik des Uhrenprotokolls im Browser läuft und Events später an den Server angehängt werden; ADR 5 hat ergänzt, dass auch die Ergebnisse offline erfasst werden. Der Spielbericht ist offline wünschenswert, aber nicht wichtig, weil das Papierdokument zählt.

Schiedsrichter arbeiten am Brett mit dem Smartphone. Android und iOS müssen zwingend unterstützt werden; der Autor selbst arbeitet mit einem iPhone. Die Daten, die im Spiellokal gebraucht werden (Ansetzung, Aufstellungen, Bedenkzeit, Spiellokal), kommen aus dem Ergebnisdienst und müssen vorher auf das Gerät.

Die Frage ist, mit welcher Technik die App offline läuft, welche Daten offline verfügbar sind und wie Daten zuverlässig zwischen Gerät und Server fließen.

## Entscheidungskriterien

Die ersten beiden wiegen am schwersten: Ein verlorenes Uhrenprotokoll wäre im Streitfall ein ernstes Problem.

1. **Kein Datenverlust:** Erfasste Daten überleben Neustart, Verbindungsabbruch und App-Update.
2. **Volle Funktion ohne Netz:** Uhrenprotokoll und Partieende funktionieren ohne Verzögerung offline.
3. **Plattformabdeckung:** Android und iOS zwingend, Desktop-Browser zusätzlich, mit einer Codebasis.
4. **Aufwand und Toolchain:** Ein Einzelentwickler kann bauen, testen und ausliefern.
5. **Lernziele:** Die Lösung vertieft Angular und Webstandards statt einer Spezialplattform.

## Optionen

Option A erfüllt die Kriterien mit dem geringsten Aufwand; ihr Risiko ist der Umgang von iOS mit Webspeicher, für den Option B als Ausweg bereitsteht.

| Option | Beschreibung | Kein Datenverlust | Ohne Netz | Plattformen | Aufwand |
| --- | --- | --- | --- | --- | --- |
| A: Progressive Web App | Angular mit Service Worker, Daten in IndexedDB, Installation auf dem Startbildschirm | Gut, wenn persistenter Speicher gewährt und die App installiert ist | Voll | Alle mit einem Browser | Gering, ein Build |
| B: Hybride App mit Capacitor | Dieselbe Angular-App in einer nativen Hülle, Daten in SQLite | Sehr gut, Speicher gehört der App | Voll | Android und iOS über App-Stores, Web zusätzlich | Mittel: Store-Veröffentlichung, Apple-Entwicklerkonto (99 USD im Jahr), iOS-Builds nur mit macOS oder macOS-Runnern in der CI |
| C: Online mit Wiederholungen | Klassische Web-App, die fehlgeschlagene Anfragen wiederholt | Schwach, Daten liegen nur im Arbeitsspeicher | Nein | Alle | Gering |

Option C erfüllt die Offline-Pflicht nicht. Option B ist robuster, verdoppelt aber die Auslieferungswege; da sie dieselbe Angular-App verpackt, bleibt sie ein später möglicher Schritt ohne Neuentwicklung.

## Entscheidung

Wir wählen Option A: SR-Companion ist eine installierbare Progressive Web App; was im Spiellokal gebraucht wird, liegt vorher auf dem Gerät, und alles, was dort entsteht, wird lokal gespeichert und später synchronisiert.

**Offline-Schnitt**

| Bereich | Offline | Richtung |
| --- | --- | --- |
| Uhrenprotokoll inkl. Partieende mit Ergebnis | Pflicht | Schreiben, später synchronisieren |
| Einsatzdaten (Ansetzung, Aufstellungen, Bedenkzeit, Spiellokal) | Pflicht | Lesen, vorher geladen |
| Spielbericht und Vorkommnisse | Wünschenswert, spätere Iteration | Schreiben, später synchronisieren |
| Infoseite | Wünschenswert, letzter Stand | Lesen |
| Einsatz bestätigen, Quittung, News pflegen | Nein | Online |

**Technik**

- Angular Service Worker für die App-Shell: Alle Dateien der Anwendung werden bei der Installation vorab geladen.
- IndexedDB über die Bibliothek Dexie.js für fachliche Daten: typisierte Tabellen, versionierte Schemata mit Migrationen, Transaktionen.
- Beim ersten Start fordert die App mit `navigator.storage.persist()` persistenten Speicher an, damit der Browser die Daten nicht bei Speicherknappheit löscht.
- Die App fordert zur Installation auf dem Startbildschirm auf; Safari auf iOS löscht Webspeicher nicht installierter Seiten nach einigen Tagen ohne Nutzung.

**iOS als gleichwertige Plattform**

- Die lokale Speicherung liegt hinter einem Port (`LocalStore`) im Frontend; Dexie.js ist nur ein Adapter. Für Option B kann ein SQLite-Adapter ihn ersetzen, ohne Domäne oder Oberfläche zu ändern.
- Jede Version wird vor der Veröffentlichung auf einem echten iPhone und einem echten Android-Gerät getestet; in der CI laufen die End-to-End-Tests zusätzlich mit der WebKit-Engine von Playwright als Näherung an Safari.
- Die App synchronisiert bei jeder kurzen Verbindung sofort, damit das Gerät möglichst selten die einzige Kopie hält.
- Ein Notfall-Export legt das Uhrenprotokoll eines Wettkampfs als Datei ab oder teilt es über das Teilen-Menü des Geräts.
- **Prüfpunkt vor dem ersten produktiven Einsatz:** ein Testspieltag auf iPhone und Android, mit Flugmodus, Neustart des Geräts und mehreren Tagen Pause zwischen Vorbereitung und Spieltag. Geht dabei auf einem Gerät Daten verloren, wird vor dem Start auf Option B gewechselt.

**Fehlersuche ohne Debugger.** Auf dem verfügbaren Mac stehen keine Entwicklerwerkzeuge wie Xcode oder der Web Inspector von Safari zur Verfügung. Die App muss sich deshalb selbst diagnostizieren können:

- Eine Diagnoseansicht in der App zeigt App-Version, Synchronisationsstand, Inhalt der Outbox, ob persistenter Speicher gewährt wurde und die letzten Fehler.
- Testversionen enthalten eine eingebettete Konsole (z. B. Eruda), die Logs, Netzwerk und Speicher direkt auf dem iPhone anzeigt; Produktionsversionen nicht.
- Fehler werden ohne personenbezogene Daten an einen Endpunkt des Backends gemeldet, sobald Netz da ist.
- Für schwer nachstellbare Fälle stehen Cloud-Dienste mit echten iOS-Geräten und Entwicklerwerkzeugen bereit.
- Sollte Option B nötig werden, entstehen iOS-Builds auf macOS-Runnern in der CI und werden über TestFlight verteilt; ein lokales Xcode ist dafür nicht nötig.

**Offline-Paket je Einsatz.** Für bestätigte Einsätze der nächsten Tage lädt die App automatisch ein Paket aus Ansetzung, Aufstellungen, Bedenkzeit und Spiellokal, sobald Netz da ist. Jeder Einsatz zeigt "Offline bereit" mit dem Zeitpunkt des Stands; ein Knopf lädt manuell nach. Spielernamen bleiben offline bearbeitbar, weil die tatsächliche Aufstellung erst im Spiellokal feststeht.

**Synchronisation.** Events aus dem Uhrenprotokoll laufen über die Outbox aus ADR 12. Die App synchronisiert beim Start, wenn das Gerät wieder online ist, wenn die App in den Vordergrund kommt und auf Knopfdruck. Die Background Sync API wird nicht verwendet, weil Safari sie nicht unterstützt. Ein Symbol zeigt dauerhaft, ob noch nicht synchronisierte Daten vorhanden sind.

**Anmeldung.** Eine abgelaufene Anmeldung blockiert nie die Arbeit offline; lokale Daten bleiben nutzbar, nur die Synchronisation wartet auf eine neue Anmeldung. Beim Abmelden werden lokale Daten erst gelöscht, wenn alles synchronisiert ist; sonst warnt die App.

**App-Updates.** Eine neue Version wird im Hintergrund geladen, aber erst aktiviert, wenn kein Uhrenprotokoll läuft; die App zeigt einen Hinweis statt neu zu laden. Der Server akzeptiert ältere Event-Versionen (Upcasting nach ADR 12), damit ein Gerät mit alter Version noch synchronisieren kann.

**Tests**

- Unit-Tests der Speicherschicht mit einer IndexedDB-Attrappe im Testlauf.
- End-to-End-Tests mit Playwright, die das Netz während eines Wettkampfs abschalten, Daten erfassen, die Seite neu laden und nach dem Wiedereinschalten die Synchronisation prüfen.

## Konsequenzen

Die App funktioniert im Spiellokal vollständig mit einer einzigen Web-Codebasis; der Preis ist, dass das Gerät zeitweise die einzige Kopie von Daten hält und die App das sichtbar machen muss.

**Positiv**

- Ein Build und ein Auslieferungsweg für alle Plattformen, ohne App-Stores.
- Uhrenprotokoll und Partieende funktionieren ohne Netz und ohne Verzögerung.
- Synchronisationsstand und Offline-Bereitschaft sind für den Schiedsrichter jederzeit sichtbar.
- Der Weg zu einer hybriden App bleibt offen, weil dieselbe Angular-App verpackt werden kann.

**Negativ**

- Bis zur Synchronisation liegen Daten nur auf dem Gerät; ein verlorenes Smartphone kostet nicht synchronisierte Protokolle.
- Auf iOS hängt die Zuverlässigkeit davon ab, dass die App installiert ist und persistenter Speicher gewährt wird.
- Fehlersuche auf iOS ist ohne Debugger mühsamer; Diagnoseansicht und Fehlerberichte müssen von Anfang an mitgebaut werden.
- Offline-Pakete, Schema-Migrationen und Update-Steuerung sind zusätzliche Komplexität, die getestet werden muss.
- Personenbezogene Daten (Spielernamen) liegen auf dem Gerät und fallen unter das Datenschutzkonzept.

**Neu bewerten, wenn**

- der Prüfpunkt vor dem ersten produktiven Einsatz oder der spätere Betrieb auf iOS Datenverluste zeigt; dann die App mit Capacitor verpacken (Option B),
- Apple Web-Apps auf dem Startbildschirm einschränkt; für die EU wurde das 2024 bereits angekündigt und wieder zurückgenommen,
- Schiedsrichter das Gerät regelmäßig ohne Synchronisation wechseln wollen,
- Browser die Speicher- oder Service-Worker-Regeln so ändern, dass persistenter Speicher nicht mehr zuverlässig gewährt wird.
