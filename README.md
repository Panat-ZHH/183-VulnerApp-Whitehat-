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

### 1. Welche Sicherheitsmechanismen wurden implementiert?

**Session-basierte Authentifizierung**

Ich habe Spring Securitys Form Login eingebaut. Nach dem Login kriegt der Browser ein `JSESSIONID`-Cookie, das er automatisch bei jedem Request mitschickt. Der Server weiss damit wer die Anfrage stellt. Das Passwort wird nur einmal beim Login übertragen und danach nicht mehr. Beim Logout wird die Session serverseitig gelöscht.

**Passwort-Hashing mit BCrypt**

Passwörter werden nie im Klartext in der Datenbank gespeichert, sondern mit `BCryptPasswordEncoder` gehasht. BCrypt ist absichtlich langsam, was Brute-Force auf gestohlene Datenbanken erschwert. Ausserdem gibt es Passwortregeln via Hibernate Validator: mindestens 12 Zeichen, Gross-/Kleinbuchstaben, eine Ziffer und ein Sonderzeichen.

**CSRF-Schutz (Double-Submit Cookie Pattern)**

Hier verwende ich `CookieCsrfTokenRepository.withHttpOnlyFalse()`. Spring setzt dabei ein `XSRF-TOKEN`-Cookie, das JavaScript lesen kann. Das Frontend liest diesen Wert und schickt ihn als `X-XSRF-TOKEN`-Header bei jedem POST mit. Spring prüft dann ob Header == Cookie-Wert.

Das funktioniert weil ein Angreifer von einer anderen Domain den Cookie nicht lesen kann (Same-Origin Policy). Er kann zwar einen Cross-Site-Request schicken, aber ohne den richtigen Header kommt HTTP 403 zurück.

**RBAC**

Es gibt `ROLE_USER` und `ROLE_ADMIN`. Normale User können Blogs lesen und schreiben sowie ihr Profil unter `/api/user/whoami` abrufen. Die Admin-Endpoints (`/api/admin/**`) sind nur für Admins zugänglich. Anonyme User kriegen 401, falsche Rolle gibt 403.

**Input-Validierung mit Hibernate Validator**

Alle Eingaben werden mit Annotations wie `@NotBlank`, `@Size` und `@Pattern` geprüft. Leere Titel, zu lange Felder oder Passwörter die die Regeln nicht erfüllen werden mit HTTP 400 abgelehnt bevor sie überhaupt in die Datenbank kommen.

**Behobene Sicherheitslücken**

- **SQL Injection**: Spring Data JPA benutzt Prepared Statements, dadurch ist SQLi nicht möglich.
- **XSS**: Im Frontend wird `textContent` statt `innerHTML` verwendet. User-Input wird also nie als HTML geparst und ausgeführt.
- **CSRF**: Mit dem CookieCsrfTokenRepository abgesichert (s.o.).
- **SSRF**: Es gab einen `HealthService` der einen `host`-Parameter vom User entgegengenommen und direkt für eine HTTP-Verbindung benutzt hat. Das ist klassisches SSRF. Den habe ich gelöscht, er wurde sowieso nirgends verwendet.

---

### 2. Weitere mögliche Sicherheitsmechanismen

**Brute-Force-Schutz / Rate Limiting**

Aktuell kann man unbegrenzt Login-Versuche machen. Sinnvoll wäre ein Filter der nach z.B. 5 Fehlversuchen den Account kurz sperrt oder die IP blockiert. Das könnte man mit einem eigenen `AuthenticationCountingHandler` umsetzen, der Fehlversuche pro IP zählt.

**Content Security Policy (CSP)**

Spring Security setzt zwar einige Security-Header automatisch, aber CSP muss man selbst konfigurieren. Mit einem strikten CSP kann man Inline-Scripts komplett verbieten, was XSS nochmals deutlich einschränkt.

**HTTPS erzwingen**

In Production sollte nur noch HTTPS erlaubt sein. Spring Security kann HTTP-Anfragen automatisch auf HTTPS umleiten. Das Session-Cookie sollte ausserdem das `Secure`-Flag bekommen damit es nie über HTTP gesendet wird.

**JWT statt Session-Cookie**

Alternativ könnte man statt dem `JSESSIONID`-Cookie einen JWT als httpOnly-Cookie speichern. Der Server müsste dann keine Session-Daten mehr halten (stateless). Nachteil ist dass man Tokens nicht einfach invalidieren kann wenn zum Beispiel ein User gesperrt wird.

---

### 3. Schwierigkeiten und Reflexion

Ich muss ehrlich sein: Ich habe am Anfang nicht wirklich mitgemacht. Die Lektionen zu Spring Security habe ich so halb mitverfolgt und hatte dann gegen Ende einen ziemlich grossen Rückstand. Das Aufholen auf den letzten Drücker war stressig und nicht die beste Idee.

Der grösste Rückschlag war, dass ich viele Konzepte einfach nicht auf Anhieb verstanden habe weil mir der Kontext gefehlt hat. Was genau eine Filter-Chain ist, warum CSRF überhaupt ein Problem ist, wie Sessions funktionieren – das alles musste ich mir dann selbst erarbeiten statt es im Unterricht mitzunehmen. Die Spring Security Dokumentation ist zwar vollständig aber ohne Grundlagenwissen auch nicht einfach zu lesen.

Konkret schwierig war das CSRF-Token-Handling in den Tests. Ich habe nicht gecheckt warum ich erst ein GET machen muss um das XSRF-TOKEN-Cookie zu kriegen, bevor ich einen POST schicken kann. Bis ich das Double-Submit-Cookie-Pattern wirklich verstanden hatte hat das gut eine Stunde gedauert.

Den `HealthService` habe ich auch erst spät analysiert. Der hat den BCrypt-Hash des Admin-Passworts direkt in einem Basic-Auth-Header verwendet, was nie funktioniert hätte weil das kein Klartext-Passwort ist. Und der `host`-Parameter war eine offensichtliche SSRF-Lücke. Habe ihn dann einfach entfernt.

Was ich definitiv anders machen würde: von Anfang an mitmachen. Die Konzepte bauen aufeinander auf. Wenn man bei den Grundlagen nicht dabei war steht man später ziemlich blöd da. Ich habe am Ende viel gelernt, aber es wäre um einiges einfacher gewesen mit einer soliden Basis von Anfang an.

---

### 4. Aufwand vs. Ertrag

| Massnahme | Aufwand | Nutzen |
|-----------|---------|--------|
| BCrypt Hashing | Sehr gering | Sehr hoch – ohne das sind alle Passwörter bei einem DB-Leak verbrannt |
| CSRF-Schutz | Gering | Hoch – verhindert eine ganze Angriffskategorie |
| Input-Validierung | Gering (Annotations) | Mittel – verhindert Müll in der DB, reduziert Angriffsfläche |
| HTTPS | Mittel | Sehr hoch |
| Rate Limiting | Mittel | Mittel |
| OAuth2 / OIDC | Hoch | Hoch |

Das wichtigste was ich aus diesem Projekt mitgenommen habe: BCrypt und CSRF kosten fast nichts, schützen aber wirklich. Es gibt keinen einzigen Grund diese Sachen nicht zu machen. Wenn eine Datenbank mit Klartext-Passwörtern leakt ist der Schaden riesig. Mit BCrypt ist er zumindest begrenzt.

Die aufwändigeren Sachen wie OAuth2 lohnen sich je nach Projekt und Risiko. Im Betrieb würde ich Auth nicht selbst implementieren sondern auf einen etablierten Identity Provider wie Keycloak setzen, einfach weil die das schon richtig gemacht haben.

Was ich generell gelernt habe: Sicherheit nachträglich einbauen ist immer teurer als sie von Anfang an einzuplanen. Das habe ich in diesem Projekt selbst gemerkt, weil ich zuerst verstehen musste was die ursprüngliche App alles falsch macht, bevor ich anfangen konnte zu fixen.