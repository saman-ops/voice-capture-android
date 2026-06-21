# VoiceCapture 2.0 — Teststrategie

## Qualitaets-Gate: Drei unabhaengige Tester

Eine neue Version wird erst veroeffentlicht, wenn **alle drei Tester-Personas** unabhaengig voneinander alle zugewiesenen Test Cases ohne Abbruch durchlaufen und explizit "OK" gegeben haben.

Tester-Personas (stellvertretend fuer echte Nutzer):

| Persona | Profil | Fokus |
|---------|--------|-------|
| **Sarah** | iOS-Designerin, erste Android-App | UX, Intuitivitaet, visuelles Design |
| **Marcus** | Android-Power-User, produktivitaetsorientiert | Stabilitaet, Geschwindigkeit, Edge Cases |
| **Lena** | Product Manager, kennt die App-Idee | End-to-End-Workflow, Zuverlassigkeit |

---

## End-to-End Test Cases

Jede Testperson fuehrt alle acht Test Cases durch. Reihenfolge ist randomisiert.
Eine Aufnahme gilt als Ziel: Capacities, E-Mail, Webhook oder Claude.

---

### TC-001: Erste Aufnahme (Capacities)
**Ziel:** Standard-Workflow von App-Start bis Capacities-Eintrag

1. App oeffnen (Cold Start — App war nicht im Hintergrund)
2. Auf das Mikrofon-FAB tippen
3. Berechtigungen erteilen, wenn gefragt
4. Ca. 30 Sekunden sprechen (beliebiger Inhalt)
5. Aufnahme stoppen
6. Verarbeitungsanzeige beobachten (Spinner laeuft)
7. Warten auf Ergebnis-Preview
8. "Details anzeigen" antippen
9. Ausgabe-Text pruefen (sinnvoller Inhalt, kein Fehler)
10. Zurueck zur Startseite — Aufnahme erscheint in der Liste

**Erwartetes Ergebnis:** Eintrag in Capacities erscheint (save@capacities.io E-Mail empfangen)

---

### TC-002: Zweite Aufnahme direkt nach der ersten
**Ziel:** Kein Crash nach der ersten Aufnahme

1. TC-001 abgeschlossen, App ist noch offen
2. Direkt wieder auf FAB tippen (ohne App zu schliessen)
3. Weitere 20 Sekunden sprechen
4. Aufnahme stoppen
5. Ergebnis abwarten

**Erwartetes Ergebnis:** App bleibt stabil, zweite Aufnahme wird verarbeitet

---

### TC-003: App schliesssen und wieder oeffnen (zwischen Aufnahmen)
**Ziel:** Kein Crash beim Re-Open nach einer Aufnahme

1. Eine Aufnahme erstellen und abschliessen (wie TC-001)
2. App in den Hintergrund schieben
3. App vollstaendig schliessen (Aufraeumen)
4. App neu starten
5. Verlauf-Tab pruefen — frueherer Eintrag sichtbar?

**Erwartetes Ergebnis:** App startet sauber, Verlauf vorhanden

---

### TC-004: Lange Aufnahme (3 Minuten)
**Ziel:** Stabilitaet bei laengeren Aufnahmen

1. Aufnahme starten
2. Durchgehend 3 Minuten sprechen
3. Timer korrekt (03:00)?
4. Aufnahme stoppen
5. Verarbeitung beobachten (laenger als bei kurzen Aufnahmen)
6. Ergebnis pruefen

**Erwartetes Ergebnis:** Kein Timeout, vollstaendiges Transkript

---

### TC-005: Abbrechen einer Aufnahme
**Ziel:** Cancel-Funktion funktioniert korrekt

1. Aufnahme starten
2. Ca. 10 Sekunden warten
3. "Abbrechen" antippen
4. App kehrt zur Startseite zurueck
5. Kein neuer Eintrag im Verlauf

**Erwartetes Ergebnis:** Aufnahme verworfen, kein Fehler

---

### TC-006: Format wechseln (Detail-Ansicht)
**Ziel:** Neuformatierung einer bestehenden Aufnahme

1. Vorhandene Aufnahme im Verlauf antippen
2. Detail-Ansicht oeffnet sich
3. Format-Chip wechseln (z.B. von "Stichpunkte" zu "Protokoll")
4. Warten auf Neuformatierung (Fortschrittsbalken)
5. Inhalt hat sich geaendert?

**Erwartetes Ergebnis:** Neu formatierter Text erscheint im Ausgabe-Feld

---

### TC-007: Berechtigungen verweigern und neu erteilen
**Ziel:** Fehlerbehandlung bei fehlenden Berechtigungen

1. In Systemeinstellungen Mikrofon-Berechtigung fuer VoiceCapture entziehen
2. App oeffnen
3. Auf FAB tippen
4. Berechtigungsdialog erscheint?
5. Berechtigung erteilen
6. Aufnahme starten und stoppen

**Erwartetes Ergebnis:** App zeigt Dialog, erholt sich nach Erteilung

---

### TC-008: Vier aufeinanderfolgende Aufnahmen mit unterschiedlichen Zielen
**Ziel:** Vollastatest und Konsistenz ueber mehrere Sessions

1. Aufnahme 1 — Ziel: Capacities
2. Aufnahme 2 — Ziel: E-Mail (falls konfiguriert)
3. Aufnahme 3 — Format: Zusammenfassung
4. Aufnahme 4 — Format: Protokoll, Claude-Chat oeffnen
5. Nach jeder Aufnahme: App bleibt stabil?
6. Verlauf zeigt alle 4 Eintraege?

**Erwartetes Ergebnis:** Alle 4 Aufnahmen erfolgreich, App nie abgestuerzt

---

## Freigabe-Prozess

```
[ Sarah: TC-001 bis TC-008 ] ✅/❌
[ Marcus: TC-001 bis TC-008 ] ✅/❌
[ Lena: TC-001 bis TC-008 ] ✅/❌
```

**Freigabe:** Alle drei haben alle acht Test Cases mit ✅ markiert.

Wenn ein Tester ❌ meldet:
1. Genauer Schritt und Fehlerbild dokumentieren
2. Entwickler behebt den Bug
3. Neuer Build → Nur der betroffene Tester wiederholt den fehlgeschlagenen TC
4. Wenn alle drei wieder ✅: Neue Version veroeffentlichen

---

## Bekannte Bugs / Offene Punkte

- [ ] Erste Aufnahme nach App-Neustart kann noch sporadisch crashen (Crash-Logger aktiv)
- [ ] Lange Texte in der Ergebnis-Vorschau (TC-001) koennen abgeschnitten erscheinen
- [ ] Waveform-Animation bei langsamem Sprechen evtl. flach

---

## Automatische Tests (geplant)

- Unit Test: `ProcessingWorker` mit Mock-LlmClient
- Instrumented Test: `RecordingActivity` — Service-Binding und State-Machine
- UI Test: Espresso — Aufnahme-Start / Stop / Verlauf
