# Live-Transkription — Use Cases für Produktmanager

## Überblick

Die Live-Transkription von VoiceCapture ermöglicht Echtzeit-Einblicke während Meetings. Statt erst nach dem Meeting eine Aufnahme zu verarbeiten, sieht der PM während des Gesprächs: das laufende Transkript, eine automatische Zusammenfassung, erkannte Action Items und konkrete Coaching-Tipps.

**Verfügbare Modi:**
| Modus | Update-Intervall | Fokus |
|---|---|---|
| Original | Echtzeit (SpeechRecognizer) | Rohtranskript mit Partial-Text |
| Einfach | 60 Sekunden | Kompakte Zusammenfassung (3 Sätze) |
| Tief | 90 Sekunden | Block-Analyse mit 🎯/📌/⚠️ |

**Action Items**: Immer aktiv, 60-Sekunden-Intervall, sticky — werden nie automatisch entfernt.  
**PM Coach**: Alle 30 Sekunden, eine Empfehlung, verschwindet nach 15 Sekunden.

---

## 5 Kern-Use-Cases

### 1. Stakeholder-Interview
**Situation**: Juergen interviewt einen Kunden oder internen Stakeholder über Anforderungen für ein neues Feature.

**Empfohlener Modus**: **Original + Action Items**

**Warum**: Im Stakeholder-Interview ist jedes Wort wichtig — keine Zusammenfassung soll Nuancen verloren gehen lassen. Das Rohtranskript gibt volle Kontrolle. Action Items fangen Commitments des Stakeholders automatisch auf ("Ich schicke Ihnen die Nutzerzahlen bis morgen").

**PM Coach hilft bei**:
- "💡 Als Requirement erfassen" — wenn ein Pain Point genannt wird
- "💡 Nach Priorität fragen" — bei mehreren Features im Gespräch
- "💡 Beispiel anfordern" — bei abstrakten Aussagen

**Typischer Flow**: Interview → Action Items als Grundlage für Follow-up-E-Mail → Volltranskript für Analyse nach dem Meeting.

---

### 2. Sprint Planning
**Situation**: Dailys oder Sprint Planning mit dem Entwicklungsteam. Viele Tasks, Schätzungen, Abhängigkeiten.

**Empfohlener Modus**: **Einfach**

**Warum**: Sprint Planning produziert viele Informationen schnell. Die einfache Zusammenfassung kondensiert 60 Sekunden Sprint-Diskussion in 3 Sätze — der PM behält den Überblick ohne sich im Transkript zu verlieren. Action Items sind besonders wertvoll: "• Dev-Team: API-Endpoint bis Sprint-Ende", "• QA: Testfälle für Story-123 erstellen".

**PM Coach hilft bei**:
- "💡 Ownership klären" — wenn Task ohne klaren Verantwortlichen besprochen wird
- "💡 Schätzung abfragen" — wenn kein Story-Point-Wert genannt wird
- "💡 Abhängigkeit notieren" — wenn blockierende Dependencies erwähnt werden

**Typischer Flow**: Live Action Items → Kopie in Jira/Confluence nach dem Meeting.

---

### 3. Anforderungs-Workshop
**Situation**: 2-stündiger Workshop mit mehreren Stakeholdern zur Ausarbeitung eines Epics oder Produktbereichs.

**Empfohlener Modus**: **Tief (Block-Zusammenfassung)**

**Warum**: Lange Workshops haben Phasenwechsel — Problemdefinition, Lösungsraum, Priorisierung. Die Tief-Analyse erkennt jeden 90-Sekunden-Block als eigenständige Einheit und liefert:
- 🎯 Was war der Kern dieser Phase?
- 📌 Welche Details wurden festgelegt?
- ⚠️ Was bleibt offen?

Nach 2 Stunden hat der PM eine strukturierte Block-für-Block-Dokumentation als Basis für das Anforderungsdokument.

**PM Coach hilft bei**:
- "💡 Risiko dokumentieren" — bei technischen Unsicherheiten
- "💡 Budgetfreigabe klären" — wenn Kosten erwähnt werden
- "💡 Nach Datum fragen" — bei vagen Timeline-Aussagen

**Typischer Flow**: Block-Summaries → Export/Copy als Rohstruktur des Requirement-Dokuments.

---

### 4. Kunden-Feedback-Session
**Situation**: Juergen führt eine Produktdemo oder User-Feedback-Session durch. Kunden geben Live-Feedback zu einem Feature.

**Empfohlener Modus**: **Einfach + PM Coach aktiv**

**Warum**: Kundenfeedback ist oft emotional und sprunghaft. Die Einfach-Zusammenfassung destilliert die Kernaussagen. Der PM Coach ist hier besonders wertvoll:
- Erkennt wenn ein Wettbewerber erwähnt wird → "💡 Wettbewerbs-Insight festhalten"
- Erkennt unklare Feature-Wünsche → "💡 Als Requirement erfassen"
- Erkennt Frustration → "💡 Ursache vertiefen"

Action Items fangen Kundenwünsche ("Können Sie mir das nach dem Call schicken?") und interne Follow-ups ("Ich kläre das mit dem Team") auf.

**Typischer Flow**: Zusammenfassung → Feedback-Report → Priorisierungs-Backlog.

---

### 5. Board-Präsentation / Executive Update
**Situation**: Juergen präsentiert vor dem Management oder Vorstand. Wichtig: Fragen und Antworten tracken.

**Empfohlener Modus**: **Original, Action Items als Fokus**

**Warum**: In Executive-Meetings zählt Präzision. Der PM will das Rohtranskript der Fragen für spätere Referenz. Action Items erfassen Board-Commitments ("Wir genehmigen das Budget für Q4") und zugewiesene Aufgaben ("Juergen, erstellen Sie ein detailliertes Business Case bis nächste Woche").

**PM Coach hilft bei**:
- "💡 Entscheidung dokumentieren" — bei formellen Commitments
- "💡 Frist abfragen" — wenn Budget/Ressourcen ohne Timeline genehmigt werden

**Wichtig**: Im Executive-Meeting kann der PM das Gerät unauffällig auf dem Tisch liegen lassen — die App läuft still im Hintergrund und erfasst alles.

---

## Bekannte Limitierungen

### Technische Grenzen
- **SpeechRecognizer-Qualität**: Google Cloud ASR — sehr gut bei Standard-Deutsch, schlechter bei Dialekten, starkem Akzent oder leiser Stimme. Empfehlung: Gerät möglichst nah am Sprecher halten.
- **Mehrsprecher**: Kein automatisches Speaker Diarization — alle Sprecher erscheinen im Transkript zusammengeführt.
- **API-Latenz**: Claude Haiku braucht 1–3s pro Zusammenfassung. Bei schlechter Verbindung kann der Intervall überschritten werden.
- **Wortanzahl-Mindest**: Zusammenfassung startet erst ab 50 erkannten Wörtern (~30–45 Sekunden Sprechzeit).
- **Lange Meetings (>2h)**: Der Zusammenfassungs-Input ist auf die letzten 2000 Wörter begrenzt, um API-Kontextlimits zu respektieren. Das vollständige Rohtranskript bleibt aber erhalten.

### Inhaltliche Grenzen
- **Action Items**: Können falsch-positive Ergebnisse liefern bei indirekter Sprache. PM sollte Vorschläge immer prüfen.
- **PM Coach**: Kontextfenster ist begrenzt (letzte 100 Wörter). Kann übergeordnete Meeting-Themen nicht berücksichtigen.
- **Kein Offline-Betrieb**: Sowohl SpeechRecognizer (Google ASR) als auch Zusammenfassung (Claude API) erfordern Internetverbindung.

## Datenschutz-Hinweis

| Daten | Wo verarbeitet |
|---|---|
| Audio-Stream | Google Cloud Speech API (über Android SpeechRecognizer) |
| Erkannter Text | Anthropic Claude API (für Zusammenfassung, Action Items, Coach) |
| Gespeicherte Daten | Nur lokal auf dem Gerät (Room-Datenbank) |

**Hinweis für vertrauliche Meetings**: In hochsensiblen Settings (M&A, HR-Gespräche, NDA-geschützte Informationen) sollte die Live-Transkription deaktiviert werden, da Audio über Google-Server läuft. Für solche Fälle die Normal-Aufnahme verwenden und das Transkript nur lokal mit Gemini verarbeiten.
