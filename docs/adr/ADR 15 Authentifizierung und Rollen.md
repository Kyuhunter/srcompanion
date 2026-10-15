# ADR 15: Authentifizierung und Rollen

Oct 9, 2026 · @Adrian

## Status und Kontext

**Status:** Akzeptiert. Ergänzt ADR 11 (Offline) und Kapitel 7 der arc42-Dokumentation (Verteilung).

SR-Companion hat mehrere Schiedsrichter als Nutzer und eine Redakteursrolle für Infoseite und News. Jeder Schiedsrichter darf nur seine eigenen Einsätze, Uhrenprotokolle und Unterlagen sehen. Gespeichert werden personenbezogene Daten, darunter die Adresse des Schiedsrichters.

Die App ist eine installierbare PWA, die im Spiellokal ohne Netz arbeitet; eine abgelaufene Anmeldung darf die Offline-Arbeit nie blockieren (ADR 11). Betrieben wird alles auf einem eigenen Server mit Docker hinter Caddy, auf einer gemeinsamen Herkunft für PWA und API.

Die Frage ist, wer die Identitäten verwaltet, wie Browser und Backend die Anmeldung austauschen und wie Rollen und Berechtigungen durchgesetzt werden.

## Entscheidungskriterien

Die ersten beiden wiegen am schwersten: Eigene Sicherheitslücken bei Passwörtern wären der teuerste Fehler, und die Anmeldung darf die Offline-Pflicht nicht brechen.

1. **Sicherheit:** Bewährte Verfahren für Passwörter, Zurücksetzen, Sperren nach Fehlversuchen; keine Tokens, die per Script im Browser gestohlen werden können.
2. **Offline-Verträglichkeit:** Lokale Arbeit ohne gültige Anmeldung, erneute Anmeldung erst zum Synchronisieren.
3. **Datenschutz:** Identitätsdaten bleiben auf dem eigenen Server in der EU.
4. **Betriebsaufwand:** Ein Entwickler kann es betreiben und aktualisieren.
5. **Lernwert:** Standards, die im Berufsalltag verbreitet sind (OAuth 2.0, OpenID Connect, Spring Security).

## Optionen

Zwei Fragen sind getrennt zu entscheiden: wer die Identitäten verwaltet und wo die Anmeldedaten im Browser landen.

**Wer verwaltet die Identitäten?**

| Option | Sicherheit | Datenschutz | Betrieb | Lernwert |
| --- | --- | --- | --- | --- |
| A: Eigene Benutzerverwaltung mit Spring Security | Registrierung, Zurücksetzen, Sperren und E-Mail-Prüfung selbst gebaut, hohes Fehlerrisiko | Eigener Server | Gering, aber viel eigener Code | Mittel, wenig übertragbar |
| B: Keycloak selbst betrieben | Ausgereift, inkl. Sperren, Zurücksetzen, optional zweitem Faktor | Eigener Server | Ein weiterer Container, Updates nötig, ca. 0,5 bis 1 GB Arbeitsspeicher | Hoch, OpenID Connect und verbreitetes Produkt |
| C: Externer Anbieter (z. B. Auth0) | Ausgereift | Daten bei einem Dritten, oft außerhalb der EU | Sehr gering | Mittel, Bindung an einen Anbieter |

**Wo landen die Anmeldedaten im Browser?**

| Option | Sicherheit | Offline | Aufwand |
| --- | --- | --- | --- |
| Tokens im Browser | Tokens sind für Scripts erreichbar; ein Script-Angriff kann sie stehlen | Möglich | Angular-Bibliothek für OpenID Connect, Token-Erneuerung im Client |
| Backend for Frontend (BFF) | Tokens bleiben im Backend, der Browser hält nur ein HttpOnly-Session-Cookie | Möglich, Cookie spielt offline keine Rolle | Spring Security als OAuth2-Client, CSRF-Schutz nötig |

Option A baut genau die Teile selbst, bei denen Fehler am teuersten sind. Option C widerspricht dem Datenschutzziel. Das BFF-Muster ist die heute empfohlene Variante für Browser-Anwendungen und passt besonders gut, weil PWA und API über Caddy dieselbe Herkunft haben.

**Weitere betrachtete Produkte.** Authentik und Zitadel wären als selbst betriebene Alternativen zu Keycloak gleichwertig geeignet; Authelia ist schwach bei Selbstregistrierung, Ory erfordert eigene Oberflächen. Keycloak wird wegen seiner Verbreitung im Java-Unternehmensumfeld gewählt. Die vorhandene Authentik-Instanz auf dem Heimserver wurde verworfen: Die Anmeldung hätte an der Verfügbarkeit des Heimanschlusses gehangen, läge unter einer anderen Herkunft als die App (Risiko für die Anmeldung in der installierten App auf iOS), würde öffentliche Konten mit privater Infrastruktur mischen und wäre für Leser des Portfolios nicht nachvollziehbar.

**Austauschbarkeit.** Die App spricht ausschließlich OpenID Connect. Ein Wechsel des Produkts ist reine Konfiguration und ändert keinen Code.

## Entscheidung

Wir wählen Keycloak (Option B) mit Spring Boot als Backend for Frontend: Keycloak verwaltet die Identitäten, das Backend führt die Anmeldung nach OpenID Connect durch und hält die Tokens, der Browser bekommt nur ein Session-Cookie.

**Aufbau**

- Keycloak läuft als weiterer Container auf dem Server, mit eigenem Schema in der vorhandenen PostgreSQL-Datenbank, und ist über Caddy unter derselben Herkunft erreichbar (z. B. unter `/auth`).
- Spring Security meldet den Nutzer als OAuth2-Client mit Authorization Code und PKCE an und speichert die Tokens in der Server-Session.
- Der Browser hält nur ein Session-Cookie mit `HttpOnly`, `Secure` und `SameSite=Lax`; ändernde Anfragen sind mit CSRF-Token geschützt.
- Angular kennt keine Tokens; es fragt beim Start einen Endpunkt mit Name und Rollen des Nutzers ab.

**Rollen und Berechtigungen**

| Rolle | Darf |
| --- | --- |
| `ARBITER` | Eigene Einsätze, Uhrenprotokolle, Unterlagen und Namensvarianten bearbeiten; Infoseite und News lesen |
| `EDITOR` | Infoseite und News pflegen |

Rollen sagen, welche Funktionen jemand nutzen darf; welche Datensätze, entscheidet jedes Modul selbst über den Besitz: Jeder Einsatz und jedes Uhrenprotokoll gehört genau einem Schiedsrichter. Die Prüfung liegt in den Use Cases, nicht nur im Controller, und ist je Modul getestet. Nutzer werden in der Verwaltungsoberfläche von Keycloak gepflegt; die App braucht keine eigene Administratorrolle.

**Registrierung**

- Schiedsrichter registrieren sich selbst über die Registrierungsseite von Keycloak und erhalten automatisch die Rolle `ARBITER`.
- Ein Konto ist erst nach Bestätigung der E-Mail-Adresse nutzbar. Keycloak sperrt nach wiederholten Fehlversuchen, Caddy begrenzt die Zahl der Anfragen an Registrierung und Anmeldung.
- `EDITOR` wird nie durch Registrierung vergeben, sondern von Hand in Keycloak.
- Ein Fremder, der sich unter dem Namen eines Schiedsrichters registriert, sieht nur Einsatzvorschläge aus ohnehin öffentlichen Ansetzungen, nie die Daten eines anderen Kontos.
- Bei der Registrierung wird auf die Datenschutzerklärung verwiesen. Jeder Nutzer kann sein Konto in der App löschen: Die Module löschen seine Daten, danach entfernt das Backend das Konto über die Admin-API von Keycloak.

**Offline**

- Name und Rollen des Nutzers werden lokal gespeichert, damit die App offline weiß, wem die Daten gehören.
- Ist die Session beim Synchronisieren abgelaufen, führt die App zur Anmeldung und synchronisiert danach; die Outbox bleibt bis dahin erhalten (ADR 11).
- Die Anmeldung läuft unter derselben Herkunft, damit sie auf iOS in der installierten App bleibt und nicht in ein separates Browserfenster wechselt. Das wird am Testspieltag aus ADR 11 geprüft.

**E-Mail.** Für Bestätigung und Zurücksetzen des Passworts braucht Keycloak einen Mailversand über SMTP.

## Konsequenzen

Passwörter und Anmeldeabläufe liegen in einem ausgereiften Produkt statt in eigenem Code, und im Browser gibt es keine Tokens; dafür kommt ein weiterer Dienst auf den Server.

**Positiv**

- Bewährte Verfahren für Passwörter, Sperren und Zurücksetzen ohne eigenen Code; ein zweiter Faktor ist später nur eine Einstellung.
- Ein Script-Angriff im Browser kann keine Tokens stehlen.
- Angular bleibt frei von Anmeldelogik; die Offline-Arbeit hängt nicht an der Session.
- Alle Identitätsdaten bleiben auf dem eigenen Server.

**Negativ**

- Keycloak braucht Arbeitsspeicher, Updates und eine Sicherung seines Schemas (vom nächtlichen Dump abgedeckt).
- Ein Mailversand muss eingerichtet werden.
- Server-Sessions machen das Backend zustandsbehaftet; bei einer Instanz unproblematisch.
- CSRF-Schutz ist Pflicht und muss für alle ändernden Anfragen funktionieren, auch für die Synchronisation.

**Neu bewerten, wenn**

- Keycloak den Server spürbar an seine Grenzen bringt; dann eine leichtere Alternative wie Zitadel oder Authentik prüfen,
- das Backend auf mehrere Instanzen skaliert werden muss; dann Sessions zentral speichern,
- die Anmeldung auf iOS trotz gleicher Herkunft die installierte App verlässt.
