# VoiceCapture — Senior Review (Dev × Design × PM)

Reviewers: Senior Android Dev (15y), Senior Product Designer, Senior PM.
Scope: full app audit for Juergen (PM @ Frequentis), daily meeting-capture workflow.
Date of review: against build 44 (commit `9efa23d`).

---

## Executive summary

The app has strong bones — Live recording with a 3-agent advisor panel is genuinely differentiated — but it fails the user at the **last mile of every workflow**: turning captured content into something usable elsewhere. The single biggest theme is **broken/inconsistent output handling**: (1) Google Tasks integration silently does nothing (the `tasks.google.com/tasks/create` deep link cannot create tasks — it only opens the app), (2) markdown is never rendered so summaries show literal `**`, `*`, `[ ]` noise, and (3) copy/share exist only for the formatted-output box and are missing from transcript, summaries, and "all artifacts." Secondarily, **action items are a second-class citizen**: they only exist for Live sessions, never for normal Gemini recordings, even though extracting tasks from meetings is the user's core job. The Home screen's three stacked floating buttons with hand-tuned margins are the most visible design debt. Fixing the output last-mile + promoting action items to a first-class, app-wide concept would make this "the best app for daily PM work."

---

## P0 — Critical (broken or blocking daily use)

| # | Area | Concrete change | File(s) | Effort | Flagged by |
|---|------|-----------------|---------|--------|-----------|
| 1 | Google Tasks | The deep link `tasks.google.com/tasks/create?title=` does **not** create a task — it just opens the app. Replace with a real mechanism: POST the task to the existing Apps Script webhook (which already runs under the user's Google account) and have the script call the Tasks API. Add a `googleTasksWebhookUrl` pref (or reuse the Doc webhook with an `action` field). | `DetailActionItemAdapter.kt`, `live/ActionItemAdapter.kt`, `LiveRecordingActivity.kt:127-133`, `data/PrefsManager.kt`, new `data/GoogleTasksClient.kt`, + Apps Script (user-side) | M | Dev, PM |
| 2 | Formatting render | Output, transcript, and all live summaries display raw markdown. Render `**bold**` → bold, `*x*`/`- x` → bullet, `[ ]`/`[x]` → ☐/☑, `#` headers. Add a `MarkdownFormatter` util returning `Spanned` (or use the already-present approach with `SpannableStringBuilder`; avoid adding a heavy lib). Apply in DetailActivity output preview, transcript, simple/deep summary TextViews. | new `util/MarkdownRenderer.kt`, `DetailActivity.kt:observeState`, `activity_detail.xml` (output as rendered TextView + edit toggle) | M | Designer, PM |
| 3 | Action item status in Verlauf | User reports "im Verlauf nicht ersichtlich welche Tasks schon bei Google Tasks sind." Verify `item_detail_action_item.xml` renders the sent-state clearly: sent → ☑ + struck-through + muted; unsent → ☐ + teal + tappable 📋. Current icons (`checkbox_on_background` / `ic_menu_share`) are unclear system glyphs — replace with explicit ☐/☑ + "📋 An Tasks" label so state is unmistakable. | `ui/detail/DetailActionItemAdapter.kt`, `item_detail_action_item.xml` | S | PM, Designer |
| 4 | Copy artifacts in Verlauf | Detail only copies `et_output`. Add per-section copy (transcript, summary, deep analysis) and a "Alles kopieren" that assembles a clean markdown doc (title + date + summary + action items + transcript). | `activity_detail.xml`, `DetailActivity.kt:setupActions` | S | PM |

---

## P1 — High (consistency, workflow, polish)

| # | Area | Concrete change | File(s) | Effort | Flagged by |
|---|------|-----------------|---------|--------|-----------|
| 5 | Action items app-wide | Normal Gemini recordings extract **no** action items. Run action-item extraction during `ProcessingWorker` for every recording and store in `liveActionItems` (rename concept to `actionItems`), so the Detail action-items section appears for all recordings, not just Live. | `worker/ProcessingWorker.kt`, `api/LlmClient.kt`, `data/db/RecordingEntity.kt`, `DetailActivity.kt` | M | PM |
| 6 | Home FAB clutter | Three floating buttons (`btn_suggestions`, `btn_live_record`, `fab_record`) positioned with hand-tuned margins (92/152/84dp) overlap and break on small screens. Replace with one primary FAB ("＋ Aufnahme") opening a small chooser (Standard / 🔴 Live), and move "Vorschläge" into the toolbar/overflow. | `fragment_home.xml`, `HomeFragment.kt` | M | Designer, Dev |
| 7 | Duplicate Tasks logic | `openGoogleTasks` is implemented 3× (DetailActionItemAdapter, live ActionItemAdapter, LiveRecordingActivity). Extract to one `GoogleTasksClient`. | `ui/detail/DetailActionItemAdapter.kt`, `live/ActionItemAdapter.kt`, `live/LiveRecordingActivity.kt` | S | Dev |
| 8 | Send-to-Tasks from Live persists nowhere | In Live, tapping 📋 fires the (broken) intent but never marks the item sent; on save the `sentToTasks` flag isn't carried into the stored JSON. Unify on the `DetailActionItem` model with `sentToTasks` and persist from Live too. | `live/ActionItemAdapter.kt`, `live/LiveViewModel.kt:stopLive`, `live/LiveTranscriptionState.kt` | M | Dev, PM |
| 9 | Chip styling inconsistency | Theme sets `chipStyle = Choice`; Live uses `Chip.Filter` explicitly; Detail/Chat build chips in code with hardcoded `0xFF6366F1`. Define one `Widget.VoiceCapture.Chip` style and one `styledChip()` helper reused everywhere. | `themes.xml`, `DetailActivity.kt`, `HistoryFragment.kt`, `activity_live_recording.xml` | M | Designer, Dev |
| 10 | Hardcoded colors everywhere | `0xFFEF4444`, `0xFF6B7280`, `#1F2937`, `#1E293B`, `#A78BFA`, `0xFF374151` etc. bypass `colors.xml`. Replace with semantic resources; add the few missing tokens. | `RecordingAdapter.kt`, `DetailActivity.kt`, `bottom_sheet_suggestions.xml`, `activity_live_recording.xml` | M | Designer |
| 11 | History date grouping + search | Long history is a flat list. Add sticky date headers (Heute / Gestern / diese Woche / älter) and a search field filtering title+transcript. | `fragment_history.xml`, `HistoryFragment.kt`, `HistoryViewModel.kt`, `RecordingAdapter.kt` | L | PM, Designer |
| 12 | Live summaries copy/share | Live simple/deep summaries (`simple_summary_content`, `deep_summary_content`) are read-only with no copy. Fold into the P0-#4 per-section copy affordance. | `activity_detail.xml`, `DetailActivity.kt` | S | PM |

---

## P2 — Nice-to-have (refinement)

| # | Area | Concrete change | File(s) | Effort | Flagged by |
|---|------|-----------------|---------|--------|-----------|
| 13 | Empty states | Action-items section and live sections have no empty/placeholder state. Add subtle "Keine Action Items erkannt" lines. | `activity_detail.xml` | S | Designer |
| 14 | `sans-serif-bold` invalid alias | `title_editable` uses `fontFamily="sans-serif-bold"` (not a real system family → silent fallback). Use `sans-serif-medium` + `textStyle="bold"`. | `activity_detail.xml`, `fragment_home.xml` | S | Dev |
| 15 | Settings: test + reveal | API-key fields have no reveal toggle and no "Verbindung testen" for webhook/SMTP. | `fragment_settings.xml`, `SettingsFragment.kt` | M | PM |
| 16 | `notifyDataSetChanged()` on selection | `RecordingAdapter.selectedIds` setter calls `notifyDataSetChanged()`; use payload diffing. | `RecordingAdapter.kt` | S | Dev |
| 17 | Bottom nav label | Only 3 destinations; consider adding contextual elevation/active-tint (active item uses `accent_voice`). | `activity_main.xml` | S | Designer |

---

## Design system recommendations (concrete values)

Existing palette in `res/values/colors.xml` is good; the problem is it's bypassed. Enforce it and add tokens:

- **Add semantic tokens** to `colors.xml`:
  - `accent_pm_coach` `#10B981` (reuse accent_green), `accent_workflow` `#818CF8` (accent_voice_light), `accent_berater` `#F59E0B` (accent_amber) — so the 3 advisor cards derive from the palette instead of `#1A2A1A`/`#1A1A2A`/`#2A1A1A` one-offs.
  - `surface_pressed` `#1C2333` (already = surface_elevated; reuse).
- **Spacing scale**: standardize on 4/8/12/16/20/24/32. Screen horizontal padding is inconsistently 12dp (lists) vs 20dp (content) — pick 16dp gutters for cards, 20dp for headers, and keep it.
- **Typography**: replace invalid `sans-serif-bold` with `sans-serif-medium`+`textStyle=bold`. Title sizes: screen title 26sp/black (keep), section label 11sp/medium muted (keep — this is a nice consistent pattern, extend it), body 14sp, meta 12sp.
- **Corner radius**: cards use 16dp (item_recording) but advisor cards 8dp and coach 10dp. Standardize: list cards 16dp, inline cards/inputs 12dp.
- **Touch targets**: several icon buttons are 28–32dp; bump to 40dp min (advisor dismiss buttons, live 📋/✕). Bottom-bar buttons are already 40dp (good — keep the recent "move controls to bottom" change).
- **Status color mapping** (use everywhere a status is shown): done→`accent_green`, processing→`accent_amber`, error→`accent_red`, live→`accent_red`.

---

## Workflow consistency matrix

Capability availability today (✅ has / ⚠️ partial / ❌ missing) vs. target (→).

| Capability | Home | Verlauf list | Detail (normal) | Detail (Live) | Live recording |
|------------|------|--------------|-----------------|---------------|----------------|
| Copy output | — | ❌ | ✅ | ✅ | — |
| Copy transcript | — | ❌ | ❌ → ✅ | ❌ → ✅ | — |
| Copy summary | — | ❌ | n/a | ❌ → ✅ | — |
| Copy ALL artifacts | — | ❌ → ✅ (swipe/long-press) | ❌ → ✅ | ❌ → ✅ | — |
| Share | — | ❌ → ✅ | ✅ | ✅ | — |
| Send to Google Tasks | — | — | ❌(broken) → ✅ | ❌(broken) → ✅ | ❌(broken) → ✅ |
| Tasks "sent" status visible | — | ❌ → ✅ badge | ⚠️ unclear → ✅ | ⚠️ unclear → ✅ | ⚠️ → ✅ |
| Markdown rendered | — | n/a | ❌ → ✅ | ❌ → ✅ | ❌ → ✅ |
| Action items shown | — | ❌ → ✅ count badge | ❌ → ✅ | ✅ | ✅ |
| Edit title/output | — | — | ✅ | ✅ | — |

Target principle: **every artifact (transcript, summary, action items) is copyable and shareable wherever it appears, and action items are sendable-to-Tasks with persistent sent-state, on every screen they appear.**

---

## Top 5 quick wins (<1h each, high impact)

1. **Fix the misleading Tasks icons (P0-#3)** — swap the ambiguous system glyphs for ☐/☑ + struck-through sent items in `item_detail_action_item.xml` + adapter bind. Instantly makes "what's already sent" obvious.
2. **"Alles kopieren" button (P0-#4)** — one button in Detail assembling `title + date + summary + action items + transcript` as markdown to clipboard. Highest daily value for a PM pasting into notes/Capacities.
3. **Markdown bold + checkbox render (P0-#2, minimal)** — even a 30-line `SpannableStringBuilder` pass handling `**bold**` and `[ ]/[x]` removes the ugliest visible noise from every summary.
4. **Action-item count badge in Verlauf list** — show `📋 3` on `item_recording.xml` when a recording has action items; turns the list into a triage surface.
5. **Replace `sans-serif-bold` + worst hardcoded colors (P2-#14, P1-#10 subset)** — quick correctness/polish fix in `activity_detail.xml` and `RecordingAdapter.kt`.

---

## Architectural notes (Dev)

- **Two action-item models** (`live/ActionItem` with `id/done` vs `ui/detail/DetailActionItem` with `sentToTasks/done`) will keep diverging. Converge on one model with `id, text, done, sentToTasks` and one adapter parameterized by context.
- **`RecordingEntity.liveActionItems`** naming presumes Live-only; once action items become app-wide (P1-#5) rename to `actionItemsJson` to avoid confusion.
- **Webhook reuse**: the Google Doc sync in `ProcessingWorker.sendToGoogleDoc` is the right pattern for the Tasks fix — same auth model (Apps Script under user's account + `AGENT_INTERNAL_TOKEN`). Mirror it for tasks rather than inventing OAuth in-app.
- **State observation**: DetailActivity rebinds action items inside `recording.collect`; guard already exists but is fragile. After model convergence, drive the list from a single `StateFlow<List<ActionItem>>` in `DetailViewModel`.
