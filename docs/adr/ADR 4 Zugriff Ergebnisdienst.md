# ADR 4: Zugriff auf den Ergebnisdienst per HTML-Adapter

Oct 7, 2026 · @Adrian

## Status und Kontext

**Status:** Akzeptiert. Betrifft das Modul `leaguedata` (Adapter-Modul nach ADR 2) und dessen Zusammenspiel mit `assignments`.

Der DSB-Ergebnisdienst ist die einzige Quelle für Ansetzungen mit eingeteiltem Schiedsrichter, Mannschaftsaufstellungen, Tabellen, Spiellokale und Ergebnisse. Einen Export gibt es nicht; die Daten stehen nur als öffentliches HTML zur Verfügung. Der automatisierte Abruf ist mit dem DSB abgestimmt.

Die Seitenstruktur kann sich ohne Ankündigung ändern. Änderungen an Ansetzungen, etwa ein Schiedsrichtertausch oder eine Verlegung, müssen vor dem Spieltag erkannt werden; Ergebnisse erscheinen Partie für Partie, sobald der Schiedsrichter sie nach Partieende im Login-Bereich eingetragen hat. Die Bedenkzeit steht nur in der Turnierordnung als PDF und wird nicht automatisch ausgelesen (siehe Haupttab).

Die Frage ist, wie und wann die App die Daten holt, wo das Wissen über das HTML lebt und wie Änderungen zuverlässig erkannt werden.

## Entscheidungskriterien

Die ersten beiden wiegen am schwersten: Ein falsch erkannter oder verpasster Einsatz ist der teuerste Fehler der App.

1. **Verlässlichkeit:** Änderungen werden rechtzeitig erkannt, und ein Abruffehler erzeugt keine falschen Meldungen.
2. **Robustheit gegen Formatänderungen:** Eine geänderte Seite bricht genau eine Stelle und fällt sofort auf.
3. **Schonung des Fremdsystems:** So wenige Anfragen wie möglich, unabhängig von der Zahl der Nutzer.
4. **Isolation:** Außerhalb von `leaguedata` kennt kein Code das HTML des Ergebnisdienstes.
5. **Testbarkeit:** Tests laufen deterministisch und ohne Netz.

## Optionen

Option B ist die einzige, die Änderungen erkennen kann, ohne dass der Abruf mit der Zahl der Nutzer wächst.

| Option | Beschreibung | Verlässlichkeit | Robustheit | Schonung | Testbarkeit |
| --- | --- | --- | --- | --- | --- |
| A: Live-Abruf je Anfrage | Backend holt die Seite, wenn ein Nutzer Daten braucht; keine lokale Kopie | Keine Änderungserkennung ohne vorherigen Stand; fällt aus, wenn der Ergebnisdienst nicht erreichbar ist | Fehler erst bei Nutzung sichtbar | Last wächst mit den Nutzern | Gut mit Fixtures |
| B: Zeitgesteuerte Synchronisation mit Diff | Backend ruft nach Zeitplan ab, speichert den letzten Stand und meldet Unterschiede als Events | Hoch, Änderungen werden aktiv erkannt | Fehler fallen beim nächsten Lauf auf | Unabhängig von der Nutzerzahl, planbar | Gut mit Fixtures |
| C: Abruf im Frontend | Browser jedes Nutzers lädt die Seiten selbst | Wie A | HTML-Wissen im Frontend verteilt | Last je Nutzer und Gerät | Schwach |
| D: Nur manuelle Eingabe | Keine Integration, der SR tippt alles ab | Abhängig vom SR | Keine Abhängigkeit | Keine Last | Trivial |

Option C scheitert zusätzlich an der Same-Origin-Policy der Browser, die Anfragen an fremde Seiten ohne passende Freigabe blockiert. Option D verliert den Kernnutzen der App, bleibt aber als Rückfallebene für einzelne Einsätze erhalten.

## Entscheidung

Wir wählen Option B: `leaguedata` synchronisiert den Ergebnisdienst zeitgesteuert, speichert den letzten vollständigen Stand und veröffentlicht Unterschiede als Events; `assignments` entscheidet, welche davon einen Schiedsrichter betreffen.

**Aufgabenteilung.** `leaguedata` kennt keine Nutzer und keine Einsätze. Es veröffentlicht neutrale Events wie `FixtureRescheduled`, `FixtureRefereeChanged` und `ResultsPublished`. `assignments` gleicht die Schiedsrichternamen ab und erzeugt daraus `AssignmentDetected`, `AssignmentRescheduled` oder `AssignmentCancelled`. So bleibt die Integration unabhängig davon, wie viele Nutzer es gibt.

**Zwei Schritte im Adapter.** Je Seitentyp (Rundenseite, Tabelle, Mannschaftsseite, Spiellokale) gibt es einen Parser, der mit jsoup und zentral definierten CSS-Selektoren ein Rohmodell erzeugt. Ein Übersetzer macht daraus das eigene Modell (`Fixture`, `Lineup`, `Standing`, `Venue`). Ändert sich eine Seite, ist nur ihr Parser betroffen.

**Abrufregeln (Startwerte, im Betrieb anpassbar)**

- Umfang: alle Ligen des Ergebnisdienstes mit Schiedsrichtereinteilung. Ihre Zahl ist überschaubar, daher ist keine Auswahl durch den Schiedsrichter nötig; Ligen ohne Schiedsrichterspalte werden übersprungen.
- Ansetzungen der kommenden Runden: einmal täglich, am Spieltag zusätzlich einige Stunden vor Spielbeginn.
- Ergebnisse: während und nach dem Wettkampf in Abständen, bis alle Partien gemeldet sind oder eine Frist abläuft.
- Höchstens eine Anfrage pro Sekunde, User-Agent mit Projektname und Kontaktadresse, `robots.txt` wird beachtet.
- Seiten werden zwischengespeichert; ein Nutzer löst nie selbst einen Abruf aus.

**Fehlerbehandlung**

- Ein Diff entsteht nur aus einem vollständig und fehlerfrei geparsten Lauf. Ein Parse-Fehler führt nie zu "Einsatz entfallen".
- Verschwindet ein Schiedsrichter aus einer Ansetzung, wird das erst nach zwei aufeinanderfolgenden Läufen gemeldet.
- Bei einem Parse-Fehler wird die Rohseite gespeichert; sie dient der Fehlersuche und wird danach zur neuen Test-Fixture.
- Ein Health-Indikator zeigt den letzten erfolgreichen Lauf je Seitentyp; ein Ausfall löst eine Benachrichtigung aus.

**Namensabgleich.** Namen werden normalisiert (Groß- und Kleinschreibung, Titelkürzel, Umlautschreibweisen). Jeder Schiedsrichter kann Namensvarianten hinterlegen. Ein Treffer ist immer nur ein Vorschlag, den der Schiedsrichter bestätigt.

**Tests**

- Parser-Tests gegen gespeicherte HTML-Seiten im Repository, ohne Netz.
- Diff-Tests mit Paaren aus altem und neuem Stand, inklusive Schiedsrichtertausch und Verlegung.
- Ein zeitgesteuerter CI-Job ruft je Seitentyp eine Live-Seite ab und meldet, wenn ein Parser sie nicht mehr versteht.

**Ablauf**

&#91;embedded content: Synchronisation · leaguedata und assignments\]

Ein Parse-Fehler endet im Fehlerfall und erzeugt keine Fixture-Events; nur ein fehlerfreier Lauf wird mit dem letzten Stand verglichen.

## Konsequenzen

Die App erkennt Änderungen an Einsätzen aktiv und belastet den Ergebnisdienst planbar; dafür trägt sie die Verantwortung für eine Kopie fremder Daten und einen fragilen Parser.

**Positiv**

- Schiedsrichtertausch und Verlegungen werden ohne Zutun des Nutzers erkannt.
- Die Last auf dem Ergebnisdienst hängt vom Zeitplan ab, nicht von der Nutzerzahl.
- Formatänderungen brechen genau einen Parser und fallen im CI-Job oder am Health-Indikator auf.
- Die App funktioniert mit dem letzten Stand weiter, wenn der Ergebnisdienst nicht erreichbar ist.

**Negativ**

- Daten sind so aktuell wie der letzte Lauf; eine kurzfristige Änderung kann bis zum nächsten Lauf unbemerkt bleiben.
- Parser müssen bei jeder Formatänderung gepflegt werden.
- Die gespeicherte Kopie enthält Namen von Spielern und Schiedsrichtern und fällt unter das Datenschutzkonzept; nicht mehr benötigte Stände werden gelöscht.

**Neu bewerten, wenn**

- der DSB einen Export oder eine Schnittstelle anbietet; dann ersetzt ein neuer Adapter die Parser, die Events bleiben gleich,
- der DSB die Abstimmung zum automatisierten Abruf zurücknimmt; dann bleibt Option D,
- der Pflegeaufwand für Parser dauerhaft höher ist als der Nutzen der automatischen Erkennung.
