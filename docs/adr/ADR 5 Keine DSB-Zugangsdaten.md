# ADR 5: Keine DSB-Zugangsdaten in der App

Oct 7, 2026 · @Adrian

## Status und Kontext

**Status:** Akzeptiert. Ergänzt ADR 4, der nur den Abruf öffentlicher Seiten regelt.

Der Schiedsrichter trägt jedes Partieergebnis sofort nach Ende der Partie im Login-Bereich des Ergebnisdienstes ein. Dieser Ablauf ist eingespielt und bleibt bewusst manuell. Die App braucht die Ergebnisse trotzdem, etwa für den Spielbericht (`MatchReport`).

Eine Automatisierung der Meldung würde verlangen, dass die App mit den persönlichen DSB-Zugangsdaten des Schiedsrichters handelt. Die Ergebnismeldung ist eine offizielle Handlung des Schiedsrichters, und die Abstimmung mit dem DSB umfasst nur den Abruf öffentlicher Seiten, nicht den Login-Bereich.

Die Frage ist, ob die App an der Ergebnismeldung beteiligt ist und woher sie die Ergebnisse bekommt.

## Entscheidungskriterien

Die ersten beiden wiegen am schwersten, weil ein Fehler hier das Vertrauen der Schiedsrichter und des DSB kostet.

1. **Sicherheit:** Die App verwaltet keine Geheimnisse, deren Verlust anderen schadet.
2. **Verantwortung:** Die offizielle Meldung bleibt eine bewusste Handlung des Schiedsrichters.
3. **Rechtliche Klarheit:** Die App bleibt im mit dem DSB abgestimmten Rahmen.
4. **Robustheit:** Keine Abhängigkeit von Login-Abläufen, Sitzungen oder Formularen, die sich ändern können.
5. **Verfügbarkeit im Spiellokal:** Ergebnisse stehen in der App auch ohne Netz zur Verfügung (ADR 11).

## Optionen

Option E erfüllt alle Kriterien; ihr Preis ist eine bewusst doppelte Erfassung, die zugleich Tippfehler sichtbar macht.

| Option | Beschreibung | Sicherheit | Verantwortung | Rechtliche Klarheit | Robustheit | Offline verfügbar |
| --- | --- | --- | --- | --- | --- | --- |
| A: Zugangsdaten speichern, App meldet | SR erfasst Ergebnisse in der App, die App meldet sie weiter | Geheimnisse Dritter im eigenen System, Ziel für Angriffe | App handelt im Namen des SR | Außerhalb der Abstimmung | Abhängig von Login und Formular | Ja |
| B: Zugangsdaten je Vorgang | Wie A, Passwort wird bei jeder Meldung eingegeben und nicht gespeichert | Passwort läuft durch die App | App handelt im Namen des SR | Außerhalb der Abstimmung | Abhängig von Login und Formular | Ja |
| C: Browser-Erweiterung | Erweiterung füllt das Formular in der Sitzung des SR, er sendet selbst ab | Keine Zugangsdaten in der App | Bleibt beim SR | Grenzwertig | Formularwissen im Client, auf Mobilgeräten kaum möglich | Nein |
| D: Keine Beteiligung, Ergebnisse nur übernehmen | SR meldet wie gewohnt; die App lädt die öffentlichen Ergebnisse | Keine Zugangsdaten | Bleibt beim SR | Innerhalb der Abstimmung | Unabhängig vom Login-Bereich | Nein, ohne Netz fehlen sie |
| E: Keine Beteiligung, eigene Erfassung mit Abgleich | SR meldet wie gewohnt und erfasst das Ergebnis zusätzlich in der App; nach der Veröffentlichung wird verglichen | Keine Zugangsdaten | Bleibt beim SR | Innerhalb der Abstimmung | Unabhängig vom Login-Bereich | Ja |

Option D war die erste Wahl, scheitert aber im Spiellokal: Ohne Netz kann die App keine Ergebnisse laden. Die Optionen A bis C lösen ein Problem, das es nicht gibt, denn die Live-Meldung je Partie funktioniert und soll so bleiben.

## Entscheidung

Wir wählen Option E: SR-Companion ist an der Ergebnismeldung nicht beteiligt und speichert, verarbeitet und überträgt keine Zugangsdaten des Ergebnisdienstes. Der Schiedsrichter erfasst jedes Ergebnis bewusst doppelt: im Ergebnisdienst als offizielle Meldung und in der App für die Arbeit im Spiellokal.

**Ergebnis als Pflichtteil von "Partie beenden".** Eine Partie wird in der App immer mit Ergebnis beendet; das Ergebnis ist Teil des Events `GameEnded` (ADR 12). Die Regel folgt der Praxis: Wer Zeit hat, die App zu bedienen, hat auch Zeit, ein Ergebnis einzutragen. Sind mehrere Bretter gleichzeitig in Zeitnot, bleibt die Partie in der App einfach noch offen. Spätestens in der nächsten Messrunde beendet der Schiedsrichter sie dort mit Ergebnis, statt einen Zeitstand zu erfassen. Das Ergebnis ist damit offline verfügbar, ohne dass die App in kritischen Momenten Aufmerksamkeit fordert.

**Abgleich nach der Veröffentlichung.** Sobald `leaguedata` `ResultsPublished` meldet (ADR 4), vergleicht `documents` die öffentlichen Ergebnisse mit den erfassten und erzeugt `ResultsVerified` oder `ResultsDiscrepancyDetected`. Eine Abweichung zeigt die App mit den betroffenen Brettern an; welche Seite falsch ist, entscheidet der Schiedsrichter.

**Grundregel.** Die App speichert keine Zugangsdaten fremder Systeme. Im Code-Review wird jedes neue Eingabefeld für Passwörter oder Tokens Dritter gegen diesen ADR geprüft; ein Secret-Scan in der CI verhindert, dass Zugangsdaten versehentlich ins Repository gelangen.

## Konsequenzen

Die App trägt keine Verantwortung für fremde Zugangsdaten und funktioniert im Spiellokal vollständig; dafür gibt der Schiedsrichter jedes Ergebnis zweimal ein.

**Positiv**

- Kein Angriffsziel für Zugangsdaten, keine Haftungsfrage bei Missbrauch.
- Die offizielle Meldung bleibt eine bewusste Handlung des Schiedsrichters, im gewohnten Ablauf.
- Ergebnisse sind im Spiellokal auch ohne Netz verfügbar.
- Tippfehler in einer der beiden Erfassungen werden erkannt.
- Änderungen am Login-Bereich betreffen die App nicht.

**Negativ**

- Jedes Ergebnis wird zweimal eingegeben.
- Eine Abweichung ist erst nach Veröffentlichung und Synchronisation erkennbar.
- Abweichungen muss der Schiedsrichter selbst klären.

**Neu bewerten, wenn**

- der DSB eine offizielle Schnittstelle zur Ergebnismeldung mit delegierter Berechtigung anbietet, bei der die App kein Passwort sieht; dann könnte die App aus ihrer eigenen Erfassung melden,
- Schiedsrichter die doppelte Eingabe dauerhaft als größtes Ärgernis nennen; dann Option C prüfen und mit dem DSB abstimmen.
