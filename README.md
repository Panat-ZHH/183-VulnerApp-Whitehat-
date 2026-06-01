# Vulnerapp – Whitehat Edition

Eine Spring Boot Applikation, die mit verschiedenen Sicherheitsmassnahmen abgesichert wurde.

## Starten

```console
./gradlew bootRun
```

Dann im Browser: http://localhost:8080/

Test-User:
- `admin` / `Admin@Secure123!` (Rolle: ADMIN + USER)
- `user` / `User@Secure123!` (Rolle: USER)

## Tests ausführen

```console
./gradlew test
```

---

## Diskussion und Selbstevaluation

### 1. Implementierte Sicherheitsmechanismen

**Session-basierte Authentifizierung (Spring Form Login)**

Ich habe Spring Securitys Form Login verwendet. Nach dem Login bekommt der Browser ein `JSESSIONID`-Cookie, das bei jedem Request automatisch mitgeschickt wird. Der Server weiss damit, welcher User die Anfrage stellt. Das ist sicherer als Basic Auth, weil das Passwort nur einmal beim Login übertragen wird und danach nicht mehr. Die Session läuft ab oder wird beim Logout gelöscht.

**Passwort-Hashing mit BCrypt**

Passwörter werden nie im Klartext in der Datenbank gespeichert. `BCryptPasswordEncoder` hashst das Passwort vor dem Speichern. BCrypt hat absichtlich einen langsamen Algorithmus, was Brute-Force-Angriffe auf eine gestohlene Datenbank erschwert. Zusätzlich gibt es Passwortregeln mit Hibernate Validator: min. 12 Zeichen, mindestens ein Grossbuchstabe, ein Kleinbuchstabe, eine Ziffer und ein Sonderzeichen.

**CSRF-Schutz (Double-Submit Cookie Pattern)**

Ich verwende `CookieCsrfTokenRepository.withHttpOnlyFalse()`. Spring setzt beim ersten Request ein `XSRF-TOKEN`-Cookie, das JavaScript lesen kann (nicht httpOnly). Das Frontend liest diesen Cookie-Wert und schickt ihn als `X-XSRF-TOKEN`-Header bei jedem State-ändernden Request (POST, PUT, DELETE) mit.

Warum das funktioniert: Spring validiert, dass der Wert im Header gleich dem Wert im Cookie ist. Ein Angreifer von einer fremden Domain kann zwar einen Cross-Site-Request auslösen, aber er kann den Cookie nicht lesen (Same-Origin Policy des Browsers) und daher den Header nicht richtig setzen. Ohne korrekten Header lehnt Spring den Request mit HTTP 403 ab.

**RBAC (Role-Based Access Control)**

Es gibt zwei Rollen: `ROLE_USER` und `ROLE_ADMIN`. Normale User können Blogs lesen und schreiben sowie ihr eigenes Profil abrufen (`/api/user/whoami`). Admin-Endpoints (`/api/admin/**`) sind nur für Admins zugänglich – normale User bekommen HTTP 403. Anonyme User bekommen für geschützte API-Endpunkte HTTP 401.

**Input-Validierung mit Hibernate Validator**

Alle Eingaben werden validiert:
- `BlogEntity`: Titel und Body dürfen nicht leer sein, Titel max. 200 Zeichen, Body max. 10'000 Zeichen.
- `UserEntity`: Username min. 3, max. 50 Zeichen; Fullname max. 100 Zeichen.
- `CreateUserRequest`: Passwort muss eine Regex-Regel erfüllen.

Dadurch landen keine ungültigen Daten in der Datenbank und gewisse Injection-Angriffe werden schon auf Validierungsebene verhindert.

**Behobene Sicherheitslücken**

- **SQL Injection**: Spring Data JPA verwendet intern Prepared Statements, wodurch SQL-Injection nicht möglich ist.
- **XSS (Cross-Site Scripting)**: Im Frontend (`script.js`) wird `textContent` statt `innerHTML` benutzt. Dadurch wird User-Input nie als HTML interpretiert und ausgeführt.
- **CSRF**: Durch den CookieCsrfTokenRepository abgesichert (siehe oben).
- **SSRF (Server-Side Request Forgery)**: Der alte `HealthService` hat einen user-kontrollierten `host`-Parameter entgegengenommen und damit eine HTTP-Verbindung aufgebaut. Das wäre ein SSRF-Angriff möglich gewesen. Ich habe den Service entfernt, da er nirgends benutzt wurde und der Actuator-Endpoint schon direkt via `application.yaml` konfiguriert ist.

---

### 2. Weitere mögliche Sicherheitsmechanismen

**Rate Limiting / Brute-Force-Schutz**

Im Moment kann man beliebig viele Login-Versuche machen. Man könnte einen Filter implementieren, der nach z.B. 5 Fehlversuchen den Account für eine gewisse Zeit sperrt oder die IP blockiert. Mit Spring könnte man das mit einem eigenen `AuthenticationFailureHandler` und einem In-Memory-Counter (oder Redis) umsetzen.

**Security HTTP-Headers (CSP, etc.)**

Spring Security setzt automatisch einige Security-Header (`X-Content-Type-Options`, `X-Frame-Options`, etc.). Aber `Content-Security-Policy` (CSP) müsste man selbst konfigurieren. Ein striktes CSP würde XSS-Angriffe weiter einschränken, z.B. indem man Inline-Scripts verbietet und nur eigene Quellen erlaubt.

**HTTPS erzwingen**

In Production sollte man nur HTTPS erlauben. Spring Security kann HTTP-Requests automatisch auf HTTPS weiterleiten. Ausserdem sollte das Session-Cookie `Secure`-Flag haben, damit es nur über HTTPS gesendet wird.

**JWT als Cookie anstelle von JSESSIONID**

Statt einer Server-seitigen Session könnte man einen JWT Token als httpOnly-Cookie speichern. Vorteil: Der Server muss keine Session-Daten speichern (stateless). Nachteil: Das Invalidieren eines Tokens ist komplizierter.

---

### 3. Schwierigkeiten und was ich anders machen würde

Die grösste Schwierigkeit war das CSRF-Token-Handling in den Tests. Mit `WebTestClient` muss man den XSRF-TOKEN-Cookie manuell aus der Response lesen und als Header bei der nächsten Request mitschicken. Das war am Anfang nicht klar, wie das Double-Submit-Cookie-Pattern in Tests funktioniert. Die Hilfsmethode `loginAs()` und `getCsrfToken()` in den Tests mussten mehrmals angepasst werden, bis alles funktioniert hat.

Der `HealthService` war auch ein Problem. Er hat versucht, den BCrypt-Hash des Admin-Passworts direkt in einem Basic-Auth-Header zu verwenden – das hätte sowieso nie funktioniert, weil der Hash kein Klartext-Passwort ist. Ausserdem war der `host`-Parameter eine SSRF-Lücke. Ich habe den Service gelöscht, weil er nirgends verwendet wurde und der Actuator-Health-Endpoint ohne ihn einwandfrei funktioniert.

Was ich anders machen würde: Früher verstehen, wie Spring Securitys Filter-Chain aufgebaut ist. Die Reihenfolge der Filter und wo genau die CSRF-Validierung stattfindet, war anfangs verwirrend.

---

### 4. Aufwand und Ertrag von Sicherheitsmassnahmen

Es gibt Massnahmen, die relativ wenig Aufwand kosten aber sehr viel bringen:

| Massnahme | Aufwand | Nutzen |
|-----------|---------|--------|
| BCrypt Passwort-Hashing | Gering (1-2 Zeilen Config) | Sehr hoch – schützt alle Passwörter bei einem DB-Leak |
| CSRF-Schutz | Gering | Hoch – verhindert eine ganze Klasse von Angriffen |
| Input-Validierung | Gering (Annotations) | Mittel – verhindert ungültige Daten, reduziert Angriffsfläche |
| HTTPS | Mittel (Zertifikat, Config) | Sehr hoch – schützt alle Daten in der Übertragung |
| Rate Limiting | Mittel | Mittel – erschwert Brute-Force |
| OAuth2/OIDC | Hoch | Hoch – delegiert Auth an bewährten Provider |

Meine Erfahrung aus diesem Projekt: Die einfachen Sachen (BCrypt, CSRF, Validation) sollte man immer machen, egal wie klein das Projekt ist. Der Aufwand ist minimal und der Schutz ist enorm. Ein Passwort-Leak ohne Hashing kann einen Betrieb in ernsthafte Probleme bringen.

Die aufwändigeren Sachen wie OAuth2 lohnen sich, wenn man sowieso eine externe Authentifizierungslösung braucht oder wenn sehr sensible Daten verarbeitet werden. Im Betrieb macht es keinen Sinn, alles selbst zu implementieren – man sollte bewährte Libraries und Standards verwenden, weil man selbst immer Fehler macht.

Ein Sicherheitsvorfall kostet ein Unternehmen typischerweise viel mehr als die Implementierung dieser Massnahmen. Deshalb ist der Ertrag fast immer grösser als der Aufwand.
