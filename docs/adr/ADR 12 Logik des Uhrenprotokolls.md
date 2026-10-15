# ADR 12: Wo lebt die Logik des Uhrenprotokolls?

Oct 7, 2026 · @Adrian

## Status und Kontext

**Status:** Akzeptiert. Ausschlaggebend war, dass eine nachvollziehbare Architekturentscheidung wichtiger ist als ein hoher Java-Anteil im Kern. Ersetzt keinen früheren ADR; baut auf ADR 6 (Aggregat Partie), ADR 7 (Event Sourcing) und ADR 11 (Offline-Pflicht) auf.

Das Uhrenprotokoll muss im Spiellokal ohne Internet funktionieren. Seine Policies müssen am Brett sofort Ergebnisse liefern: Plausibilitätsprüfung eines Zeitstands, Rückrechnungsvarianten nach Regelwidrigkeit oder Uhrenausfall, neue Uhreinstellung, hergeleitete Unterbrechungsdauer. Ein Server ist in diesem Moment nicht erreichbar, also muss diese Logik im Browser laufen.

Gleichzeitig ist das Backend in Java mit Spring Boot geplant, und das Projekt soll Java-Domänenmodellierung zeigen. Die Frage ist, ob die Logik zusätzlich im Backend existieren muss und wenn ja, wie sie mit der Frontend-Logik konsistent bleibt.

Rahmenbedingungen: Je Partie schreibt genau ein Schiedsrichter. Die App schlägt vor, der SR entscheidet; verbindlich ist erst sein bestätigtes Ergebnis. Das Papierdokument bleibt maßgeblich.

## Entscheidungskriterien

Abgeleitet aus den Qualitätszielen; die ersten drei wiegen am schwersten.

1. **Korrektheit:** Jede Rechnung liefert überall dasselbe Ergebnis; keine zwei Implementierungen, die auseinanderlaufen können.
2. **Offline-Tauglichkeit:** Alle Policies laufen ohne Netz und ohne spürbare Verzögerung.
3. **Wartbarkeit:** Eine Regeländerung (z. B. neues Regelwerk) wird an genau einer Stelle gemacht.
4. **Einfache Toolchain:** Keine exotischen Compiler oder Laufzeiten, die Build, Debugging und CI verkomplizieren.
5. **Lernziele:** Das Projekt zeigt Architektur in Angular/TypeScript und in Java/Spring Boot.

## Optionen

Option A erfüllt die drei wichtigsten Kriterien ohne Abstriche; ihr einziger Nachteil betrifft das Lernziel Java und lässt sich in anderen Modulen ausgleichen.

| Option | Beschreibung | Korrektheit | Offline | Wartbarkeit | Toolchain | Lernziele |
| --- | --- | --- | --- | --- | --- | --- |
| A: Nur TypeScript | Logik als frameworkfreies TS-Modul im Frontend; Backend speichert und verteilt Events, rechnet nicht nach | Eine Implementierung | Voll | Eine Stelle | Standard | Java-Anteil im Core gering |
| B: Doppelt mit gemeinsamen Testdaten | Logik in TS und Java; beide Test-Suites laufen gegen dieselben Testfälle (CSV/JSON) in der CI | Abweichungen möglich zwischen Testfällen | Voll | Jede Regel zweimal | Standard | Beide Sprachen |
| C: Eine Codebasis, zwei Laufzeiten | Z. B. Kotlin Multiplatform oder Java nach JavaScript/WebAssembly kompiliert | Eine Implementierung | Voll | Eine Stelle | Exotisch, schwer zu debuggen | Weder idiomatisches TS noch Spring |
| D: Serverzentriert mit Offline-Puffer | Client speichert nur Rohdaten, Server rechnet nach Synchronisation | Eine Implementierung | Prüfung und Rückrechnung fehlen am Brett | Eine Stelle | Standard | Java im Core |

Option D scheidet aus, weil sie die Kernfunktion im entscheidenden Moment nicht liefert. Option C löst ein Problem, das A gar nicht erst entstehen lässt, und kostet dafür die einfache Toolchain.

## Entscheidung

Wir wählen Option A: Die Logik des Uhrenprotokolls lebt ausschließlich in einem frameworkfreien TypeScript-Modul; das Backend speichert den Event-Stream und verteilt ihn weiter, rechnet aber nie nach.

**Events tragen Ergebnisse, nicht nur Eingaben.** Damit der Server nichts nachrechnen muss, enthalten Events das bestätigte Resultat. "Rückrechnung bestätigt" trägt etwa die gewählte Variante und die neue Uhreinstellung, "Unplausibler Zeitstand festgestellt" die berechnete Abweichung. So kann der Server Spielbericht-Vorschläge oder PDFs erzeugen, ohne die Regeln zu kennen.

**Der Event-Vertrag ist die Schnittstelle.** Die Events werden als JSON Schema im Repository definiert und versioniert. Aus dem Schema werden TypeScript-Typen und Java-Klassen generiert, und beide Seiten prüfen in der CI gegen dieselben Beispiel-Events. Neue Event-Versionen werden beim Lesen auf die aktuelle Version angehoben (Upcasting), gespeicherte Events werden nie verändert.

**Hexagonale Architektur im Frontend.** Das Domänenmodul kennt weder Angular noch IndexedDB. Angular-Services, die IndexedDB-Ablage und der Sync-Client sind Adapter. ESLint-Regeln für Modulgrenzen schlagen fehl, wenn das Domänenmodul Angular oder Browser-APIs importiert.

**Synchronisation**

1. Jedes Event bekommt im Client eine UUID und eine fortlaufende Nummer je Partie.
2. Events landen zuerst in einer lokalen Outbox in IndexedDB.
3. Bei Netz schickt der Client sie an den Server, mit der zuletzt bekannten Nummer als erwarteter Version.
4. Der Server hängt idempotent an: Bekannte UUIDs werden ignoriert, eine abweichende erwartete Version wird abgelehnt.
5. Eine Ablehnung bedeutet, dass dieselbe Partie auf einem zweiten Gerät bearbeitet wurde; die App zeigt den Konflikt an, statt still zu überschreiben.

## Konsequenzen

Die Core Domain wird in TypeScript gebaut; Java-Domänenmodellierung zeigt das Projekt in der Vergütungsordnung (ADR 13) und der Einsatzverwaltung.

**Positiv**

- Eine einzige Implementierung jeder Regel, keine Abweichungen zwischen Client und Server.
- Volle Offline-Fähigkeit ohne Kompromisse am Brett.
- Das Backend-Modul Uhrenprotokoll bleibt dünn: ein Event-Store mit Append, Abfrage und Weiterleitung.
- Der Event-Vertrag ist ein eigenes, gut präsentierbares Architekturartefakt.

**Negativ**

- Der Server vertraut den Ergebnissen des Clients. Vertretbar, weil nur der SR seine eigene Partie schreibt und seine Entscheidung ohnehin maßgeblich ist.
- Jede Event-Änderung erfordert Schema-Versionierung und Upcasting auf beiden Seiten.
- Der Java-Anteil an der Core Domain ist gering.

**Neu bewerten, wenn**

- mehrere Personen dieselbe Partie bearbeiten sollen,
- das Uhrenprotokoll offiziell als Nachweis dienen soll und der Server Ergebnisse unabhängig prüfen muss,
- ein zweiter Verbraucher die Regeln serverseitig ausführen muss, etwa eine Schnittstelle für Verbände.
