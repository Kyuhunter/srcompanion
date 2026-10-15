# ADR 1: Modularer Monolith statt Microservices

Oct 7, 2026 · @Adrian

## Status und Kontext

**Status:** Akzeptiert, inklusive eigenem Datenbankschema je Modul mit eigenen Flyway-Migrationen. Grundlage für ADR 2 (hexagonale Architektur je Modul) und ADR 8 (Kommunikation über Events).

Das Event Storming hat fünf Kontexte ergeben, die sehr unterschiedlich gebaut werden: ein dünner Event-Store für das Uhrenprotokoll (ADR 12), ein reiches Java-Modell für die Vergütung, ein Anti-Corruption-Layer für den Ergebnisdienst und schlichtes CRUD für Informationen und News. Die Kontexte tauschen Domain Events aus, etwa "Einsatz bestätigt" oder "VorkommnisGemeldet".

Entwickelt und betrieben wird SR-Companion von einer Person. Die Last ist gering: einzelne Schiedsrichter, wenige Zugriffe, zeitgesteuerte Abrufe beim Ergebnisdienst. Es gibt keinen Bedarf, Teile unabhängig zu skalieren oder von getrennten Teams ausliefern zu lassen.

Die Frage ist, wie das Backend geschnitten und ausgeliefert wird, sodass die Kontextgrenzen sichtbar und belastbar bleiben, ohne dass der Betrieb einen Einzelentwickler überfordert.

## Entscheidungskriterien

Die ersten beiden wiegen am schwersten, weil sie die Qualitätsziele Wartbarkeit und Robustheit direkt betreffen.

1. **Erzwingbare Grenzen:** Ein Verstoß gegen eine Modulgrenze fällt automatisch auf, nicht erst im Review.
2. **Betriebsaufwand:** Eine Person kann bauen, ausliefern, überwachen und Fehler suchen.
3. **Evolvierbarkeit:** Ein Modul lässt sich später herauslösen, ohne die anderen umzuschreiben.
4. **Testbarkeit:** Module lassen sich einzeln und gemeinsam testen, ohne verteilte Testumgebung.
5. **Lernziele:** Die Architektur ist im Code sichtbar und lässt sich im Portfolio erklären.

## Optionen

Option C bietet erzwingbare Grenzen und einen Ausstiegspfad zu Services, ohne den Betriebsaufwand verteilter Systeme.

| Option | Beschreibung | Grenzen | Betrieb | Evolvierbarkeit | Testbarkeit |
| --- | --- | --- | --- | --- | --- |
| A: Microservices | Ein Service je Kontext, Events über einen Message Broker | Hart durch Prozessgrenzen | Mehrere Deployments, Broker, verteiltes Monitoring, Netzwerkfehler | Hoch | Aufwändig, verteilte Testumgebung |
| B: Schichtenmonolith | Pakete nach Schichten (Controller, Service, Repository) | Unsichtbar, Kontexte vermischen sich | Ein Deployment | Gering, Kopplung wächst unbemerkt | Einfach |
| C: Modularer Monolith mit Spring Modulith | Ein Paket je Kontext, Grenzen per Test geprüft, Events über Spring | Per Test erzwungen | Ein Deployment | Hoch, Events lassen sich später nach außen geben | Modul einzeln oder gemeinsam |
| D: Modularer Monolith mit Build-Modulen | Ein Gradle- oder Maven-Modul je Kontext | Hart zur Compile-Zeit | Ein Deployment | Hoch | Modul einzeln oder gemeinsam |

Option A löst Probleme, die SR-Companion nicht hat (Teams, Skalierung), und kauft dafür verteilte Fehlerfälle ein. Option B verfehlt das wichtigste Kriterium. Option D ist strenger als C, kostet aber deutlich mehr Build-Konfiguration für einen Gewinn, den die Verifikation in C weitgehend auch liefert.

## Entscheidung

Wir wählen Option C: ein Spring-Boot-Backend als modularer Monolith mit Spring Modulith, ein Modul je Bounded Context, Grenzen per Test erzwungen.

**Modulschnitt und Paketstruktur.** Jedes Modul ist ein direktes Unterpaket des Wurzelpakets. Was im obersten Paket eines Moduls liegt, ist seine öffentliche Schnittstelle (veröffentlichte Events, Ports für andere Module); Unterpakete sind intern und für andere Module unsichtbar.

```
de.srcompanion
├── assignments      Einsatzverwaltung: Einsatz, Abgleich, Einsatz offline verfügbar machen
├── leaguedata       Wettkampfdaten: Anti-Corruption-Layer zum Ergebnisdienst
├── clocklog         Uhrenprotokoll: Event-Store je Partie (ADR 12)
├── documents        Unterlagen: Spielbericht, Quittung, Vergütungsordnung, PDF
└── information      Informationen: Infoseite, News
```

**Wie die Grenzen erzwungen werden**

- Ein Test mit `ApplicationModules.of(SrCompanionApplication.class).verify()` schlägt fehl bei Zugriffen auf interne Pakete anderer Module oder bei zyklischen Abhängigkeiten. Er läuft in der CI-Pipeline.
- Module kommunizieren über veröffentlichte Events mit `@ApplicationModuleListener`; direkte Aufrufe nur über explizit veröffentlichte Schnittstellen.
- Die Event Publication Registry von Spring Modulith speichert Events in derselben Transaktion wie die fachliche Änderung, damit kein Event verloren geht.
- `@ApplicationModuleTest` startet einzelne Module isoliert für Integrationstests.

**Datenhaltung je Modul.** Eine PostgreSQL-Datenbank, aber ein eigenes Schema je Modul mit eigenen Flyway-Migrationen. Kein Modul liest oder schreibt Tabellen eines anderen. Das hält den Weg offen, ein Modul später mit eigener Datenbank herauszulösen.

**Dokumentation aus dem Code.** Der Documenter von Spring Modulith erzeugt Komponentendiagramme der Module; sie werden in der CI gebaut und fließen in die arc42-Dokumentation ein.

**Frontend.** Die Angular-App folgt demselben Schnitt: ein Feature-Bereich je Kontext, Modulgrenzen per ESLint-Regel geprüft (siehe ADR 12).

## Konsequenzen

SR-Companion wird als ein Artefakt gebaut und ausgeliefert; die Modularität steckt in Paketen, Schemas und Tests statt in Prozessgrenzen.

**Positiv**

- Ein Build, ein Deployment, ein Log: Betrieb und Fehlersuche bleiben für eine Person handhabbar.
- Grenzverletzungen fallen in der CI auf, nicht erst bei der nächsten Änderung.
- Modulgrenzen und Event-Flüsse sind im Code und in den generierten Diagrammen sichtbar.
- Module lassen sich isoliert testen, ohne verteilte Testumgebung.

**Negativ**

- Die Grenzen werden per Test erzwungen, nicht vom Compiler; wer den Verifikationstest entfernt, verliert den Schutz.
- Alle Module teilen sich Laufzeit, Ressourcen und Release-Zyklus; ein Fehler in einem Modul kann die ganze Anwendung betreffen.
- Spring Modulith ist eine zusätzliche Abhängigkeit mit eigener Lernkurve.

**Neu bewerten, wenn**

- ein Modul ein deutlich anderes Lastprofil bekommt; erster Kandidat ist Wettkampfdaten mit seinen zeitgesteuerten Abrufen,
- mehrere Personen oder Teams an getrennten Modulen arbeiten und unabhängig ausliefern wollen,
- Grenzverletzungen trotz Verifikationstest wiederholt auftreten; dann Build-Module (Option D) ergänzen.
