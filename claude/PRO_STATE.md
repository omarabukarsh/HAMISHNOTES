# PRO_STATE.md — MyNotes Pro (separate app with on-device AI)

Decision (user, 2026-10-23): standalone copy on its own branch, does not touch the main app. Device: HUAWEI MatePad 11.5 S (8 GB RAM, Kirin 9000WL, HarmonyOS 4.2). User uses the Google model (Gemma 4 E2B) on device.

## Rules (user, 2026-10-24)

- The Pro chat must NEVER modify regular MyNotes (feature/premium-ui) or ios-app. Every request in the Pro chat applies to Pro only.
- "Make Pro like the regular app" = merge feature/premium-ui INTO pro (pro only), keeping Pro identity and AI.
- After every build give the direct artifact link: https://github.com/omarabukarsh/Mynotes/actions/runs/<runId>/artifacts/<artifactId>.
- Whole-document AI reading must be fully offline (on-device model only).
- (2026-10-24) PRIORITY: accuracy of summaries over speed.
- (2026-10-24) AI must work for ANY subject and file type, not only pharmacy and not only the user's own highlights; send a plan and wait for «نفّذ» before big AI changes; never ask for API keys in chat.

## Branch and identity

- Branch pro. Merges of feature/premium-ui into pro: f858071 (2026-10-23), 80ea49a (2026-10-24). On merge keep: label «HAMISH PRO» (user, 2026-10-29: launcher label + visible "MyNotes Pro" texts renamed; applicationId, packages, prefs, artifact name «MyNotes-Pro» unchanged), AI settings section, summaryAiMode, Pro comments, and the Pro UI removals below.
- applicationId com.mynotes.app.pro, versionName pro-1.0.<run>, versionCode = 100 + GITHUB_RUN_NUMBER of build.yml. Committed keystore → installs as update. Install only the «MyNotes-Pro» artifact.
- Latest good build before the AI v4 round: #169 (8afc919), run 40127399922, artifact 12573704554.
- Latest build: #175 (8ff6dc6 = AI v4.3 + CLAUDE.md), run 40142039055, artifact 12579770101.
- (2026-10-24 23:36 UTC) GitHub Actions stopped starting jobs (Build APK + CI tests failed in 1 s with no log, run 176/84). Most likely the account's Actions minutes ran out; user asked to check github.com/settings/billing. Commits with [skip ci] avoid wasting minutes.
- "CI tests" has NEVER passed: the same 19/26 unit tests failed on its first run (22 Oct, c1bce74, audit session) — they are targets written from antibiotics.pdf, not regressions. Lint: 6 errors (e.g. dispatchKeyEvent restricted API). ci.yml now prints the golden report (expected vs produced) in the log.
- (2026-10-29 17:30 UTC) f6c9bcb (HAMISH PRO rename on top of 32794a2): Build APK #179 (run 40922030208, re-run attempt 2 at ~17:37), CI tests #87 and AI summary eval (dispatch, run 40922037974) all refused by GitHub: "The job was not started because recent account payments have failed or your spending limit needs to be increased". So 32794a2 is STILL NEVER COMPILED and the eval never ran. Next: re-run 40922030208 and dispatch ai-eval.yml once billing is really fixed (re-runs that are refused use no minutes).
- LOCAL BUILD (2026-10-29, user asked to build without GitHub): pro-1.0.179 / versionCode 279 from 76def17 (= 32794a2 + HAMISH PRO rename + compile fix). Only one compile error in 32794a2: AiOrganize.kt stray `private set` after the new cancelRunning() (belonged to stepFraction). Verified with aapt2/apksigner: package com.mynotes.app.pro, label «HAMISH PRO», cert SHA-256 f0b818a6…9b38 = keystore/mynotes.jks. APK sent to the user as a file (not a GitHub artifact). Next GitHub build numbers (≥180) stay above 279-100, so updates keep working.
- Build 182 (2026-10-29): arm64-only release (181, 74 MB) was REVERTED (user: it does not work); full ABIs again, delivered as a zip (206 MB APK → 86 MB zip). Do not drop ABIs again without asking.
- Build 190 (2026-10-29, local; Actions still refused for billing, run 189): commit e3c17c7 (= premium-ui merge b15cdd5 + CLAUDE.md), pro-1.0.190 / versionCode 290, all 4 ABIs, label «HAMISH PRO», cert f0b818a6…9b38 OK, no compile fixes needed. Delivered as zip (86 MB). Eval not rerun: AI files unchanged since the last eval (only textlayout.py, which the eval does not use). Next local build number must be > 190 and > the latest GitHub run number.
- How to build locally: sdkmanager from dl.google.com into /opt/android-sdk ("platforms;android-34" "build-tools;34.0.0"); Maven Central returns 429 through the proxy, so ~/.gradle/init.d/mirror.gradle swaps repo.maven.apache.org for https://maven-central.storage-download.googleapis.com/maven2/ (no repo change); `ANDROID_HOME=/opt/android-sdk GITHUB_RUN_NUMBER=<n> ./gradlew assembleRelease` (~5 min). huggingface.co is reachable now (302 to CDN), so tools/ai-eval can run locally with the real model (venv with litert-lm).
- Kotlin 2.4.0 on this branch. No Android SDK in the cloud session: CI is the compiler.
- Pitfalls: nested horizontal scroll rows crash (infinite width); `var x by mutableStateOf` with `private set` + a `fun setX(...)` → JVM "Platform declaration clash"; Compose offset is RTL-mirrored — use absoluteOffset when matching a Canvas; Java regex needs `[^\]]` (not `[^]]`); Android refuses to draw bitmaps over ~100 MB (PdfDoc.safeWidth caps page bitmaps at 12 MP).
- Cloud session cannot reach huggingface.co or GitHub artifact/log blob storage. CI results are read via check-run annotations (::notice) or get_job_logs.
- The workspace may be restored after a restart with work from an earlier run already committed — check git log/status before redoing work.

## AI

- On device: LiteRT-LM 0.18.0, CPU only (also vision), temperature 0.2. Two models only, shown as «AI» (E2B) and «AI أقوى» (E4B) with no model names. Qwen, imported file, GPU switch and all cloud code (CloudAi.kt, Claude/ChatGPT) were REMOVED (2026-10-29, user request); old Qwen files are deleted on first start and the saved choice migrates to «AI».
- All prompts are subject-neutral (no "pharmacy" wording) since VERSION 4: summary, review, mind map, cards, comparison table, edit, explain.

### Structured summary (AiStructure.kt, VERSION 4)
- Headings by line height / bold short lines / AI page reading / outline. Main heading is searched 3 pages back always and up to 8 pages back only through pages that are all read (an unread page may hold a newer heading). A sub-heading with no main heading after another section is promoted to "# " (it no longer appears inside the previous section).
- Highlight text (markLines): lines of one sentence are joined until the sentence ends (also across a blank line), so the fallback never splits a sentence into two bullets; headings, table rows and numbered/bulleted items stay separate lines (letter bullets only "a)".."h)").
- Tables by code: page tables with header from the page (Part.headed), and NEW: 3+ consecutive "name: detail" / "name — detail" lines with distinct short names → "Item | Details" / «البند | التفصيل» table without the model.
- Model text per (heading, subheading); SYSTEM has 3 generic examples (rock types table, bullets, Arabic negation).
- Checks (generic word normalisation for Arabic + English: no tashkeel, unified hamza, prefixes ال/و/ب/ل/ف stripped, plural/tense suffixes; generic stop words): suspicious (new words/numbers in whole output), badLines (EVERY line must be grounded: no new numbers, ≤1/4 new content words, 0 for short lines; and faithful: ≥50% of consecutive word pairs in the same order as the notes → catches rewording that changes meaning, incl. Arabic), dropped (missing number, >20% missing content words in any language, lost negation). Any failure → fallback = notes as bullets (tidied).
- tidy(): lone table row → bullet, "|" inside a bullet → ";", plain line → bullet, long bullet split at sentence ends. Words are never changed.
- Self-review only when the draft still needs it (needsReview: not wellFormed or a bullet > 160 chars); correction accepted only if it passes all checks and is wellFormed as written. Toggle «مراجعة ذاتية» still works.
- Dedupe of repeated lines is per section only (same point in two sections stays in both).
- Calibrated on eval run #8 outputs: catches the invented "Penicillin V … is used for:" and the reworded Arabic «يعطّل البنسلين الجدار…», no false positives on the correct lines.

### Review cards (AiOrganize.kt + DocSession.putAiCards)
- Built from the checked summary itself (not raw highlights): bullets go to the model per main title (cards2 memo key), table rows become cards by code (AiStructure.tableCards, up to 12 per table; generic headers → "topic: item?").
- Every AI card checked (AiStructure.cardOk): answer ≤ 25 words / 180 chars and grounded in the summary part, no new numbers in the question, not yes/no, answer not inside the question, no invented negation. Near-duplicates removed by meaning (sameCard: question similarity ≥ 0.6, or same answer + similar question).
- putAiCards: drops cards that duplicate an existing card by meaning; a new card keeps the id (review progress, star) of the old AI card with the same or a very similar (≥ 0.75) question.

### Mind map (MindMap.kt)
- «المهم فقط» = MindMapAi (cache key mm2): table rows → points by code ("item: key cell", ≤ 7 words); sections with ≤ 3 lines → by code; longer sections → model gets numbered LINES and answers "- N: phrase"; each phrase must come from line N (content words, numbers, negation) else replaced by a code phrase of that line. brief()/cut() never cut inside a word and never end on "of/the/في…". Generic importance score (definitions, causes, rules/exceptions, negation, numbers, plus medical cues).
- «كل النقاط» = code tree. Zoom, fold, PDF export as A4 landscape pages.

### Other
- AI panel (#166): toggles in «⚙ إعدادات» (auto-continue, self-review, whole-document reading, auto page reading, auto cards). Auto mode after new highlights (20 s + pen idle).
- ArabicFix.kt: «لا» → «ال» extraction fix. OCR cache version 4.
- Whole-document background reading (AiDocReader.kt): on-device only, each page once.
- In-app Arabic: «✦ عدّل الملخص بالذكاء» accepts Arabic instructions; «✦ اشرح» answers in Arabic. No free chat window yet («اسأل الذكاء» offered, not requested).

## AI rounds (2026-10-24, after #169)
- v4 (#170): subject-neutral prompts, line-level grounding/faithfulness/dropped checks (AR+EN), keyed tables, joined sentence lines, conditional self-review, mind map mm2, cards from the summary.
- v4.1 (#171): moved-label check ("Pen G: Food…"), mind-map word-order check, LaTeX in cards, English question for Arabic answer rejected, sameCard needs similar answer too.
- v4.2 (#172/#173): Arabic cards — language stated in the prompt, "What is X?"→«ما هو X؟»; short lines not deduped (lost "not stable"); "X:" + sub-bullets → one line.
- v4.3 (#174/#175): salvage — remove only the unsupported bullet and re-check; "item: detail" bullets → cards by code; no model call for < 2 bullets.
- Eval totals (before → after): #10 summary 1070→477 s, mind map 334→200 s, cards 845→629 s, invented words 24→4, wrong section 15→1, mm cut mid-word 25→0; #13 (v4.3, 14 highlight sets): summary 363→184 s, mm 128→75 s, cards 450→226 s, invented 20→6, negations lost 1→0, wrong section 16→0, fallbacks 8→8 (+2 salvaged), yes/no 6→0, answer-in-question 11→0, near-dup 15→0. Remaining: English questions for Arabic notes (7 rejected), fallbacks ~8/28.

## Investigation of bad PDF summaries (user screenshots, NSAIDs lecture)
- Root causes found in TextLayout (shared by the regular and the AI summary): bullet glyph «•» + gap becomes a table cell; a gap left by an arrow/image inside a sentence makes a one-row "table" (startsTable accepts a single row with 3 cells or with a continuation line); single-word slide title needs 1.6× body to be a heading so «Inflammation» merged into the next sentence; separate bullet sentences merged into one paragraph; Arabic mixed-direction word order reversed; Arabic letters inside words damaged («ألن», «خصوًصا», «باألسئلة»); hyphen line-break not joined; wrapped cells in both table columns; «t1⁄2»; two colours on one sentence.
- In AiStructure: normal text size = median of LINES, so small figure labels make slide body lines look like headings («associated with:»). Use a word-weighted median.
- tools/ai-eval/textlayout.py = faithful Python port of TextLayout.kt; fixed=True applies F1 (bullet glyph not a cell), F2 (one-row table only if an aligned multi-cell neighbour exists on the page), F3 (short bold line ≥1.2× body = heading). golden.py runs AntibioticsCases on it (port: 3/17 pass like the app's 4/17; F1–F3 break none). slides.py → docs/slides_nsaids.pdf reproduces the user's slides (titles, «•», text arrows, drawn arrows, figure labels).
- Plan sent to the user (waiting for «نفّذ» and GitHub Actions): A = F1–F3 + bullet paragraph split + hyphen + fraction + word-weighted size; B = Arabic order and letters; C = wrapped cells, two colours; D = image-reading experiment (image decides layout/roles, words stay from the PDF; LiteRT-LM Python supports Content.ImageFile + vision_backend).
- User cannot share the NSAIDs PDF; use slides_nsaids.pdf.

## Tidy summary not organised (user screenshot 2026-10-29, NSAIDs slide 7 on build 179) — FIXED in local build 180 (user said «نفّذ»)
- Reproduced exactly with the Python port (scratch slide: bold underlined title 1.19× body, «•» bullets). Causes: (1) TextLayout heading needs ≥1.25× body; title is bold+underlined but slightly bigger → paragraph; F3 (bold ≥1.2×) also misses it. (2) OcrStyle.underlined searches only BELOW the word box (PowerPoint underline sits inside the descender area) and rejects ink extending past the WORD, so a whole-line underline is never detected; fix = search from 35% above box bottom, measure extension past the LINE (tested: title U, body none). (3) Summary.continues merges whenever the previous paragraph has no final punctuation (slide bullets never have it) → title + bullet 1 + bullet 2 glued; TextTidy.continues is already stricter (lowercase / connector / comma). (4) «•» deleted as junk by TextTidy.clean, so bullet starts are lost. (5) "eg." not an abbreviation (only "e.g") → split after "eg." in TextLayout (short line ending ".") and in sentences(); also split before "(Endocrine topic)".
- Done (build 180, pro-1.0.180 / versionCode 280, same cert): TextTidy.continues (shared by Summary) needs evidence of continuation (lowercase / connector / comma / open-ended last word; Arabic keeps old merge; never before a bullet or after ":"); bullet glyph kept as «• » (dash only with a space after, so «–ve»/«-5» are not bullets); abbreviations + eg/ie/cf/viz/esp/incl; no sentence split before "("; TextLayout: lone bullet glyph is not a table cell, bullet row starts a paragraph, "eg." at line end is not an end, slide title = bold or fully underlined, ≤10 words, ≥1.1× body, no final punctuation, not a bullet; OcrStyle underline searched from 35% inside the word box, extension measured against the LINE; OCR cache ver *31+6; DocSession.refreshMarkTexts rebuilds a page's mark texts after each fresh read only if the letters are identical and the mark has no SummaryEdits keys. textlayout.py mirrors all of it (fixed= now only F2).
- Verified: unit tests 29 run / same 18 old failures (3 new tests pass), jvm-golden report byte-identical; golden.py 3/17 unchanged; slides_nsaids.pdf: titles detected, «• | Pain» false table gone, bullets separated; scratch slide → heading + 2 bullet paragraphs. NOT verified on device (real PowerPoint underline position, Wingdings bullets in the user's file).
- Mark text is stored at highlight time: Summary/TextTidy fixes reach old highlights at once; TextLayout/underline fixes only new highlights unless mark text is rebuilt (SummaryEdits keys "t:id#pi" may shift → rebuild only marks without manual edits).

## Eval (tools/ai-eval, v4)
- Runs the same Gemma 4 E2B .litertlm on CPU; BEFORE (pro@8afc919 logic, prompts copied in legacy_prompts.py) and AFTER (prompts read from the Kotlin files) in the same run, same highlights.
- Documents: antibiotics.pdf (fixed highlights as v1–v3 + random highlights) and docs/*.txt laid out as pages by layout.py (history EN, cell biology AR, sorting algorithms EN, economics AR+EN terms, contract law AR, physics EN) with random highlights (seed EVAL_SEED=2026): whole paragraphs, partial lines, table row runs, two paragraphs at once, 15% double-highlighted phrases.
- Metrics independent of the app checks: invented words, coverage, numbers kept, negations lost, broken-sentence bullets, lines in the wrong section, tables; mind map points / >7 words / cut mid-word / not from one line; cards / answer not in notes / yes-no / answer in question / near-duplicate pairs; model time per stage.
- Workflow runs on changes to tools/ai-eval or AiStructure/AiOrganize/MindMap; results in annotations (3 steps: metrics, summaries, mind map + cards) and in the job log.
- `python tools/ai-eval/eval.py --stub` runs locally without the model (logic check).

## Round 2026-10-29 (OCR + visual summary + explicit AI) — COMPILES (local build 179); UNVERIFIED on device (visual reading on Kirin CPU unknown until the user runs «اختبر قراءة الصور»)
- Automatic AI triggers (auto-continue after highlight, page read before organize, whole-doc background read, auto cards) are kept but OFF by default; toggles in Settings > AI (and the drawer's AI settings). Explicit request = the button in the summaries drawer AI tab; it shows a blocking dialog (AiBlockingDialog, hosted in ReaderScreen) with Cancel. Auto runs never block.
- Explicit organize reads highlighted pages visually (AiPageReader.readVisual: page image + numbered colored frames, one image per conversation, max 8 frames) ONLY if Ai.visionOk; each accepted transcript replaces that mark's text (length sanity check, no "[unreadable]"), else the OCR text stays; final message states how many highlights came from the image. CPU vision on Kirin is unproven: Settings has «اختبر قراءة الصور» (Ai.visionTest, 2 fresh conversations, shows result/time/PSS). If it fails nothing claims visual reading.
- Non-highlighted pages: stored page text only as Part.context (<=900/500 chars, "CONTEXT ... never copy"), included in cacheKey. Grounding checks still use Part.notes only.
- AiPageReader results are keyed by hash of the page layout text (not page index), old *_pN.txt files are deleted once; API result(s,page)/save(s,page,..)/context(s,page,max). Inflight set prevents double reads.
- OCR: OcrWord has italic/underline/color/conf/block; OcrPage.shapes (long rules). OcrStyle.kt analyzes the page bitmap (ink color, underline, bold by ink density, horizontal/vertical rules). PdfTextLayer detects italic from the font. OcrEngine.recognizeBest re-reads a contrast-enhanced copy when mean confidence < 0.6. OCR cache version = versionOf*31+5 (re-reads pages once). ML Kit is still Latin-only; Arabic scans rely on the PDF text layer or visual reading.
- Selection bar: «اجعله بطاقة» is one tap (front = nearest heading or first words, back = selected text, no dialog); «إلى الخريطة الذهنية» stores DocData.mindNotes ("mind" in the doc JSON) shown by MindMap.withNotes under their heading.
- tools/ai-eval mirrors were NOT changed (SYSTEM/prompt without context are identical).

## Eval of build 179 logic (run LOCALLY 2026-10-29 on 4 CPU cores, same E2B .litertlm, seed 2026; results.md committed)
- Totals before → after: summary 538→248 s, mind map 178→80 s, cards 634→319 s; invented words 18→7, negations lost 1→0, broken-sentence bullets 13→6, wrong section 17→0, fallbacks 8→8 (+2 salvaged), self-reviews 30→1, mm >7 words 48→8, mm cut mid-word 30→0, mm not from one line 96→0, cards yes/no 8→0, answer-in-question 9→0, near-dup 13→0, answer not in notes 4→3. Same picture as #13 (v4.3): 32794a2 did not change the checks; the eval does NOT exercise Part.context or visual reading (mirrors unchanged).
- Read outputs: (1) REGRESSION SEEN: antibiotics fixed set, «Prophylactic uses» → model line "Penicillin V is used for: Strepococcal disease …" ACCEPTED (label not in notes — the list is Pen G/V; also typo "Strepococcal"); in old #13 run this part fell back. The check gap (a new leading "X is used for:" label over a joined list passes the ≤1/4 new-words rule) predates 32794a2; needs a plan before fixing. (2) Arabic "invented" words are verb-form changes only («يفكك» for «تفكك», «يراضى» for «يتراضى»). (3) Text-layout issues still visible in antibiotics random set (diagram labels as a table "passive diffusion | ACTIVE transport | subunit", «الالهوائيات») = stage A/B of the reading plan. (4) Loose cards: "When do Penicillins inactivate bacteria? → active wall synthesis occurs"; «من أين تنشأ الخلايا؟ → لا تنشأ الخلايا من مادة غير حية».

## Merge of feature/premium-ui into pro (2026-10-30) — NOT COMPILED yet
- Merged 9 regular-app commits (library redesign with Lib colors/covers/stickers/Caveat font, Motion.kt animated AlertDialog/Dialog wrappers, share/export (ShareExport.kt, EditablePdf), smoother ink (LiveInk.kt), scrubber/zoom, recent/pages drawer). Merge base 3916478.
- Conflicts resolved by hand: AppController.shareOrSaveSummary (regular signature with docId + withDocSession; kept Pro «✦ ذكاء» export of the AI summary, returns -1 when none), LibraryRepository (took regular's parseDocData/docDataJson refactor and re-added "mind" notes read/write), LibraryScreen (regular layout, title «HAMISH PRO»).
- Visible texts in merged files that said «هامش»/Hamish now say HAMISH PRO. FileProvider authority uses packageName, so applicationId is untouched.

## 2026-11-03: blocking AI dialog REMOVED (user request)
- AiBlockingDialog and AiOrganizer.blocking deleted; organizing shows progress only inside the summaries drawer (AI tab, with Stop). The app stays usable while the AI runs.
- 2026-11-03 («نفّذ»): ported regular-app FEATURES only from feature/premium-ui (c5e3eb1) — NOT COMPILED yet (Actions billing; this container cannot reach dl.google.com for a local build):
  - cherry-picked: c14b4c3 ink delay, ce4cd76 tab drag-reorder, 579bceb/54f61fd stats redesign, 83be151 settings sidebar/stats/toolbar redesign WITHOUT Google sign-in (Account page replaced by «✦ الذكاء الاصطناعي» page with AiSettings; SignInScreen/GoogleAuth not ported; Pro launcher icon kept).
  - post-rename commits applied as patches with com.hamishnotes.app → com.mynotes.app: f722a33 laser/shape corners, dd44141 save-to-device 0 bytes, 0115923/7e29ebe/c50c726 pen detection & pressure, 5751dac/12f98af manual mind map (MindMap.kt/MindMapUi.kt), c5e3eb1 handwriting reading (HandwritingWords.kt).
  - NOT ported: Premium/paywall/3-doc limit, Play Billing/Integrity, Supabase, Google sign-in, account deletion, privacy/terms for accounts, package rename, target SDK 36 change, fonts revert pair, 90e9905 test removal (conflicted, tests only).
  - Pro AI mind map renamed: MindMap.kt → AiMindMap.kt (object AiMap, AiMindMapDialog) because the regular app now owns MindMap/MindMapDialog. ai-eval.yml paths and pipeline.py _kt("AiMindMap.kt") updated.
  - DocSession.ocrPage merged: handwriting reader path + Pro OcrStyle analysis (baseWords/baseShapes cache); ocrVersion = (versionOf*31+7)*4+hwLang (re-reads pages once); refreshMarkTexts and scheduleInkRead both kept.
  - WebOutline.kt (new, OFF by default, Settings > AI): on explicit organize only, sends the topic name (file title cleaned / first PDF outline entry) to Wikipedia search+parse(sections); cached per doc in files/ai/web (failures not cached). AiStructure.parts(..., web) → pageHeadings.applyWeb: promotes a detected heading to level 1 if it matches a toclevel-1 section, adds short page lines that exactly match a section as headings; never adds text that is not on the page, never demotes. Empty map = old behaviour, so the eval mirror is unchanged. About privacy text updated.

## Pro UI
- Templates + TemplatePicker, PagePanel (⋯), tab bar contrast, document settings, toolbar customization page, default toolbar + ✦ AI button, library multi-select, «manual edits» bar removed from summaries.
- #168: bottom snackbar shows ONLY copy confirmations. #169: highlight-count badge on the summaries drawer button removed.

## Verified on device by user
- #132 template picker crash (fixed #136). #163: mind map PDF crash → fixed #164.

## Next steps
- Wait for GitHub Actions and «نفّذ», then stage A (text reading fixes) verified with golden.py and slides before pushing.
- User installs #175 and reports real time per summary on the MatePad.
