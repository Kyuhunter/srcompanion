# ADR 2: Hexagonale Architektur je Modul

Oct 7, 2026 · @Adrian

## Status und Kontext

**Status:** Akzeptiert. Baut auf ADR 1 (ein Modul je Kontext) und ADR 12 (Logik des Uhrenprotokolls im Frontend) auf.

Die Module des Backends tragen sehr unterschiedlich viel Fachlogik. Die Vergütungsordnung in Unterlagen und der Abgleich in der Einsatzverwaltung enthalten echte Regeln. Wettkampfdaten besteht fast nur aus Übersetzung zwischen Fremdformat und eigenem Modell. Das Backend-Modul Uhrenprotokoll ist nach ADR 12 ein dünner Event-Store, Informationen ist reines CRUD.

Eine hexagonale Architektur (Ports and Adapters) hält die Fachlogik frei von Frameworks und macht sie ohne Spring testbar. Sie kostet aber zusätzliche Schnittstellen, Mapping-Code und Pakete. Die Frage ist, ob alle Module gleich streng geschnitten werden oder ob der Stil sich nach der Fachlogik richtet.

## Entscheidungskriterien

Die ersten beiden wiegen am schwersten: Die Architektur soll die Fachlogik schützen, ohne Module ohne Fachlogik zu belasten.

1. **Testbarkeit der Fachlogik:** Regeln lassen sich ohne Spring-Kontext und ohne Datenbank in Millisekunden testen.
2. **Aufwand im Verhältnis zum Nutzen:** Struktur nur dort, wo sie etwas schützt.
3. **Durchsetzbarkeit:** Der gewählte Stil wird automatisiert geprüft, nicht nur beschrieben.
4. **Wiedererkennbarkeit:** Wer ein Modul öffnet, erkennt sofort, nach welchem Stil es gebaut ist.
5. **Lernziele:** Die Abwägung zwischen Architekturstilen wird im Portfolio sichtbar.

## Optionen

Option C schützt die Fachlogik dort, wo es sie gibt, und hält einfache Module einfach; der Preis ist eine bewusst dokumentierte Uneinheitlichkeit.

| Option | Beschreibung | Testbarkeit Fachlogik | Aufwand | Durchsetzbarkeit | Wiedererkennbarkeit |
| --- | --- | --- | --- | --- | --- |
| A: Hexagonal überall | Jedes Modul mit Domäne, Ports und Adaptern | Hoch | Hoch, auch für CRUD und Event-Store | Eine Regel für alle | Sehr hoch |
| B: Schichten überall | Controller, Service, Repository; JPA-Entities als Domänenobjekte | Gering, Regeln hängen an Spring und JPA | Niedrig | Eine Regel für alle | Sehr hoch |
| C: Abgestuft nach Modultyp | Hexagonal für Module mit Fachlogik, schlank für Übersetzung, Event-Store und CRUD | Hoch, wo es zählt | Angemessen | Regeln je Stil | Hoch, wenn der Stil je Modul dokumentiert ist |

Option A würde Informationen und den Event-Store mit Ports und Mapping aufblähen, ohne dass dort Regeln zu schützen sind. Option B verfehlt das wichtigste Kriterium, weil die Vergütungsregeln nur mit Spring-Kontext testbar wären.

## Entscheidung

Wir wählen Option C: Der Architekturstil richtet sich nach der Fachlogik eines Moduls und wird je Modul festgelegt, dokumentiert und geprüft.

| Modul | Stil | Begründung |
| --- | --- | --- |
| assignments | Hexagonal | Statusübergänge und Abgleichsregeln (erkannt, bestätigt, verlegt, entfallen) |
| documents | Hexagonal | Vergütungsordnung (`FeeSchedule`) mit Honorar- und Fahrtkostensätzen; PDF-Erzeugung als ausgehender Adapter |
| leaguedata | Adapter-Modul | Übersetzung aus HTML; eingehend ein Scheduler, ausgehend ein HTTP-Client, dazwischen nur das Übersetzungsmodell |
| clocklog | Schlank | Event-Store mit Anhängen und Abfragen; Regeln leben nach ADR 12 im Frontend |
| information | Schlank | CRUD für Infoseite und News |

**Aufbau eines hexagonalen Moduls**

```
assignments
├── AssignmentConfirmed        veröffentlichtes Event (Modul-API, ADR 1)
├── domain                     Aggregate, Value Objects, Regeln – kein Spring, kein JPA
├── application                Use Cases, eingehende und ausgehende Ports
└── adapter
    ├── in/web                 REST-Controller
    ├── in/events              Listener für Events anderer Module
    └── out/persistence        JPA-Entities, Repositories, Mapping
```

**Eigenes Persistenzmodell mit Spring Data JPA im Adapter.** In hexagonalen Modulen tragen Domänenobjekte keine JPA-Annotationen; der Persistenz-Adapter hat eigene JPA-Entities. Ausschlaggebend ist, dass die Persistenztechnik damit ein austauschbares Detail des Adapters wird. JPA wird gewählt, weil es der verbreitete Standard in Spring-Projekten ist; seine Eigenheiten (parameterloser Konstruktor, nicht-finale Felder, Proxys, Lazy Loading) bleiben im Adapter. In schlanken Modulen dürfen JPA-Entities direkt verwendet werden.

**Betrachtete Alternativen für die Persistenz**

| Alternative | Warum nicht gewählt |
| --- | --- |
| JPA-Annotationen direkt an Domänenobjekten | JPA bestimmt die Form der Fachklassen: keine finalen Felder, Invarianten nicht vollständig per Konstruktor erzwingbar, Proxys in der Fachlogik |
| Spring Data JDBC | Passt fachlich gut zu kleinen Aggregaten, ist aber weniger verbreitet; bleibt Ausweichoption, da der Adapter austauschbar ist |

**Umsetzungsregeln**

- Mapping von Hand, kein Mapping-Framework; ein Framework wird erst bei spürbarem Aufwand mit eigenem ADR eingeführt.
- Updates über die geladene Entity: Der Adapter lädt die vorhandene JPA-Entity, überträgt die geänderten Werte und speichert. Optimistisches Sperren per `@Version` und Dirty Checking bleiben im Adapter, die Domäne kennt keine technischen Versionsfelder.
- Zwei Fabrikmethoden je Aggregat: eine fachliche (z. B. `Assignment.detect`, mit Regeln und Event) und eine paketinterne zum Wiederherstellen aus der Datenbank (ohne Regeln, ohne Event).
- Round-Trip-Test für jeden Mapper: speichern, laden, vergleichen, als Integrationstest mit Testcontainers gegen PostgreSQL. Das prüft zugleich die Flyway-Migrationen.

**Durchsetzung**

- ArchUnit-Regeln für hexagonale Module: `domain` hängt nicht von `org.springframework` oder `jakarta.persistence` ab; `domain` und `application` hängen nicht von `adapter` ab.
- Welche Module hexagonal sind, wird im Code markiert (z. B. per Annotation in `package-info.java`, optional mit jMolecules), sodass die Regeln genau dort greifen.
- Die Tabelle oben wird Teil der arc42-Dokumentation; ein neues Modul bekommt seinen Stil dort, bevor es Code bekommt.

## Konsequenzen

Zwei der fünf Module sind hexagonal; die anderen drei bleiben bewusst schlank, und diese Abstufung ist selbst Teil der Architektur.

**Positiv**

- Die Regeln der Vergütung und der Einsatzverwaltung sind ohne Spring und Datenbank in Millisekunden testbar.
- Einfache Module bleiben einfach; kein Mapping und keine Ports, wo nichts zu schützen ist.
- Die Abwägung ist im Code, in den ArchUnit-Regeln und in der Stiltabelle nachvollziehbar.

**Negativ**

- Die Codebasis ist uneinheitlich; wer ein Modul öffnet, muss erst den Stil kennen.
- In hexagonalen Modulen entsteht Mapping-Code zwischen JPA-Entities und Domänenobjekten.
- Die Einstufung eines Moduls kann sich als falsch erweisen und muss dann geändert werden.

**Neu bewerten, wenn**

- ein schlankes Modul eigene Regeln bekommt, etwa Informationen mit Freigabe-Workflows; dann wird es hexagonal umgebaut,
- der Mapping-Code in einem hexagonalen Modul mehr Pflege verursacht als die Fachlogik; dann wird dessen Persistenz-Adapter auf Spring Data JDBC umgestellt, ohne die Domäne zu ändern,
- ein Reviewer die Uneinheitlichkeit wiederholt als Verwirrung statt als Absicht liest; dann die Stiltabelle prominenter in die README.
