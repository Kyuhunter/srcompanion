# ADR 3: Sprache im Code

Oct 7, 2026 · @Adrian

## Status und Kontext

**Status:** Akzeptiert. Gilt für Backend, Frontend, Datenbank, API, Dokumentation und Git.

Die Fachsprache von SR-Companion ist Deutsch und teilweise DSB-spezifisch: Ansetzung, Spielbericht, Quittung, Spiellokal, Vergütungsordnung, Ergebnisdienst. Für diese Begriffe gibt es keine etablierten englischen Entsprechungen. Der Teil rund um die Uhr hat dagegen eine offizielle englische Terminologie aus den FIDE-Regeln (time control, increment, illegal move, clock malfunction).

Frameworks, Bibliotheken und ihre Konventionen sind englisch: Spring Data leitet Abfragen aus Methodennamen wie `findByStatus` ab, Jackson und JPA erwarten `get`- und `is`-Präfixe. Das Glossar aus dem Event Storming ist auf Deutsch und soll im Code wiedererkennbar sein (Ubiquitous Language).

Ausschlaggebend ist, dass das Regelwerk der FIDE einschließlich des Arbiters' Manual auf Englisch verfasst ist und die meisten Tätigkeiten eines Schiedsrichters dort mit englischen Begriffen beschrieben werden. Englisch im Code ist damit keine Übersetzung, sondern die Nutzung der offiziellen Fachterminologie. Deutsch bleibt die Sprache der Oberfläche und der DSB-spezifischen Verwaltung.

Die Frage ist, in welcher Sprache Code, Schnittstellen, Datenbank und Dokumentation benannt werden.

## Entscheidungskriterien

Die ersten beiden wiegen am schwersten: Der Code soll die Fachsprache sprechen, ohne gegen die Frameworks zu arbeiten.

1. **Ubiquitous Language:** Ein Begriff aus dem Glossar steht unverändert im Code, ohne Übersetzung im Kopf.
2. **Verträglichkeit mit Frameworks:** Konventionen von Spring Data, JPA und Jackson funktionieren ohne Sonderkonfiguration.
3. **Eindeutigkeit:** Eine einfache Regel entscheidet in jedem Fall, welche Sprache gilt.
4. **Lesbarkeit für Dritte:** Reviewer und Bewerbungsgesprächspartner finden sich schnell zurecht.
5. **Werkzeugsicherheit:** Bezeichner funktionieren in jedem Betriebssystem, Dateisystem und CI-System.

## Optionen

Option A nutzt die offizielle englische Fachterminologie der FIDE und vermeidet gemischte Bezeichner; ihr Risiko liegt bei den DSB-spezifischen Begriffen, die ein festgelegtes englisches Gegenstück im Glossar brauchen.

| Option | Beschreibung | Ubiquitous Language | Frameworks | Eindeutigkeit | Lesbarkeit für Dritte |
| --- | --- | --- | --- | --- | --- |
| A: Alles Englisch | Fachbegriffe übersetzt (Assignment, MatchReport, Receipt) | Bruch: Übersetzung zwischen Gespräch und Code, DSB-Begriffe ohne Entsprechung | Problemlos | Hoch | International hoch |
| B: Alles Deutsch | Auch technische Begriffe deutsch (EinsatzSpeicher, findeNachStatus) | Hoch | Bricht Konventionen, abgeleitete Abfragen nur mit englischen Schlüsselwörtern | Hoch | Ungewohnt |
| C: Fachsprache Deutsch, Technik Englisch | Fachbegriffe deutsch, technische Rollen und Framework-Konventionen englisch (EinsatzRepository, findByStatus) | Hoch | Problemlos | Gut mit klarer Regel | Im deutschsprachigen Raum verbreitet |

Die Bewertung "Bruch" bei Option A gilt nur für die DSB-spezifischen Begriffe. Für den Kern der Domäne ist Englisch die Originalsprache der Regeln. Option C wäre die Wahl, wenn die Fachsprache rein deutsch wäre; Option B kämpft gegen jedes eingesetzte Framework.

## Entscheidung

Wir wählen Option A: Code, Schnittstellen und Datenbank sind englisch und folgen der Terminologie der FIDE; die Oberfläche ist deutsch. Ein zweisprachiges Glossar verbindet beide Welten.

| Bereich | Sprache | Beispiel |
| --- | --- | --- |
| Klassen, Methoden, Events | Englisch, FIDE-Begriffe wo vorhanden | `Assignment`, `ClockReading`, `confirm()`, `AssignmentConfirmed` |
| Pakete und Module | Englisch | `assignments.adapter.out.persistence` |
| Datenbank | Englisch, snake\_case | Schema `assignments`, Spalte `venue_address` |
| REST-Pfade und JSON-Felder | Englisch | `/api/assignments`, `"timeControl"` |
| Eigennamen externer Systeme | Original | `ErgebnisdienstClient` |
| Oberfläche und Meldungen an Nutzer | Deutsch über Angular i18n | "Nur erkannte Einsätze können bestätigt werden" |
| Fehler aus der Domäne | Englischer Code, deutscher Text in der Oberfläche | `ASSIGNMENT_NOT_CONFIRMABLE` |
| Commit-Nachrichten | Englisch, Conventional Commits | `feat(assignments): confirm assignment` |
| ADRs, arc42 | Deutsch; README englisch | – |

**Das Glossar ist die Quelle.** Ein Fachbegriff wird zuerst im Glossar mit deutschem und englischem Namen festgelegt, dann im Code verwendet. Englische Begriffe kommen bevorzugt aus den FIDE-Regeln und dem Arbiters' Manual; für DSB-spezifische Begriffe wird ein englischer Name festgelegt und nicht wieder geändert.

**Vorschlag für das Glossar (vor Übernahme gegen das Arbiters' Manual prüfen)**

| Deutsch | Englisch im Code | Anmerkung |
| --- | --- | --- |
| Einsatz | Assignment |  |
| Ansetzung | Fixture | Üblich für Begegnungen im Ligabetrieb |
| Mannschaftskampf | Match |  |
| Partie | Game |  |
| Bedenkzeit | TimeControl | Gesamtes Modell mit Perioden und Inkrement |
| Zeitkontrolle | PeriodEnd | Im Englischen heißt auch dieser Punkt "time control"; eigener Name nötig, um Verwechslung zu vermeiden |
| Inkrement | Increment |  |
| Zeitstand | ClockReading |  |
| Messrunde | ReadingRound |  |
| Unterbrechung | ClockStop |  |
| Regelwidriger Zug | IllegalMove | FIDE-Begriff |
| Uhrenausfall | ClockMalfunction | FIDE-Begriff |
| Rückrechnung | ClockAdjustment |  |
| Uhreinstellung | ClockSetting |  |
| Spielbericht | MatchReport |  |
| Vorkommnis | Incident |  |
| Quittung | Receipt |  |
| Vergütungsordnung | FeeSchedule |  |
| Spiellokal | Venue |  |

**Durchsetzung**

- Checkstyle im Backend und ESLint im Frontend lehnen Bezeichner mit Nicht-ASCII-Zeichen ab.
- Angular i18n ist ab dem Walking Skeleton aktiv; Texte für Nutzer stehen nie direkt im Template.
- Commit-Nachrichten werden in der CI gegen das Conventional-Commits-Format geprüft.

## Konsequenzen

Der Code spricht die offizielle Sprache des Schachregelwerks und ist international lesbar; die deutsche Fachsprache lebt in Oberfläche und Glossar weiter.

**Positiv**

- Einheitliche Bezeichner ohne Sprachmischung, passend zu allen Framework-Konventionen.
- Begriffe lassen sich direkt mit FIDE-Regeln und Arbiters' Manual abgleichen.
- Das Portfolio ist für deutsch- und englischsprachige Leser zugänglich.
- Oberfläche und Code sind entkoppelt; eine englische Oberfläche wäre später nur eine weitere Übersetzungsdatei.

**Negativ**

- Für DSB-spezifische Begriffe müssen englische Namen festgelegt werden, die außerhalb des Projekts niemand kennt.
- Zwischen Gesprächen mit deutschen Schiedsrichtern und dem Code liegt eine Übersetzung; das Glossar muss deshalb gepflegt werden.
- Bestehende Dokumente (Event Storming, ADR 1, ADR 2, ADR 12) nennen Module und Klassen noch deutsch und müssen nachgezogen werden.

**Sprache der Dokumentation.** ADRs und arc42 bleiben deutsch, weil Abwägungen in der eigenen Sprache präziser formuliert werden. Die README ist englisch und verweist auf die deutsche Dokumentation; Code-Bezeichner in der Dokumentation stehen immer in ihrer englischen Form.

**Neu bewerten, wenn**

- sich herausstellt, dass für wesentliche Teile der Domäne keine FIDE-Begriffe existieren und die erfundenen Namen überwiegen,
- Übersetzungsfehler zwischen Glossar und Code wiederholt zu Missverständnissen führen.
