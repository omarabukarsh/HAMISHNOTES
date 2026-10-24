# PRO_STATE.md — MyNotes Pro (separate app with on-device AI)

Decision (user, 2026-10-23): standalone copy on its own branch, does not touch the main app. Device: HUAWEI MatePad 11.5 S (8 GB RAM, Kirin 9000WL, HarmonyOS 4.2). User uses the Google model (Gemma 4 E2B) on device.

## Rules (user, 2026-10-24)

- The Pro chat must NEVER modify regular MyNotes (feature/premium-ui) or ios-app. Every request in the Pro chat applies to Pro only.
- "Make Pro like the regular app" = merge feature/premium-ui INTO pro (pro only), keeping Pro identity and AI.
- After every build give the direct artifact link: https://github.com/omarabukarsh/Mynotes/actions/runs/<runId>/artifacts/<artifactId>.
- Whole-document AI reading must be fully offline (on-device model only).
- (2026-10-24) AI must work for ANY subject and file type, not only pharmacy and not only the user's own highlights; send a plan and wait for «نفّذ» before big AI changes; never ask for API keys in chat.

## Branch and identity

- Branch pro. Merges of feature/premium-ui into pro: f858071 (2026-10-23), 80ea49a (2026-10-24). On merge keep: label «MyNotes Pro» (no «هامش / Hamish»), AI settings section, summaryAiMode, Pro comments, and the Pro UI removals below.
- applicationId com.mynotes.app.pro, versionName pro-1.0.<run>, versionCode = 100 + GITHUB_RUN_NUMBER of build.yml. Committed keystore → installs as update. Install only the «MyNotes-Pro» artifact.
- Latest good build before the AI v4 round: #169 (8afc919), run 40127399922, artifact 12573704554.
- "CI tests" workflow (unit tests/lint + emulator) has been failing since before #166 (runs 73–75); Build APK and AI eval succeed. Not investigated.
- Kotlin 2.4.0 on this branch. No Android SDK in the cloud session: CI is the compiler.
- Pitfalls: nested horizontal scroll rows crash (infinite width); `var x by mutableStateOf` with `private set` + a `fun setX(...)` → JVM "Platform declaration clash"; Compose offset is RTL-mirrored — use absoluteOffset when matching a Canvas; Java regex needs `[^\]]` (not `[^]]`); Android refuses to draw bitmaps over ~100 MB (PdfDoc.safeWidth caps page bitmaps at 12 MP).
- Cloud session cannot reach huggingface.co or GitHub artifact/log blob storage. CI results are read via check-run annotations (::notice) or get_job_logs.
- The workspace may be restored after a restart with work from an earlier run already committed — check git log/status before redoing work.

## AI

- On device: LiteRT-LM 0.18.0, CPU only, temperature 0.2. Qwen3-1.7B, Qwen3-0.6B, Gemma 4 E2B (vision), imported file. Cloud: CloudAi.kt with user's own key (Claude / OpenAI), Ai.budget(x) = x*8.
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

## Eval (tools/ai-eval, v4)
- Runs the same Gemma 4 E2B .litertlm on CPU; BEFORE (pro@8afc919 logic, prompts copied in legacy_prompts.py) and AFTER (prompts read from the Kotlin files) in the same run, same highlights.
- Documents: antibiotics.pdf (fixed highlights as v1–v3 + random highlights) and docs/*.txt laid out as pages by layout.py (history EN, cell biology AR, sorting algorithms EN, economics AR+EN terms, contract law AR, physics EN) with random highlights (seed EVAL_SEED=2026): whole paragraphs, partial lines, table row runs, two paragraphs at once, 15% double-highlighted phrases.
- Metrics independent of the app checks: invented words, coverage, numbers kept, negations lost, broken-sentence bullets, lines in the wrong section, tables; mind map points / >7 words / cut mid-word / not from one line; cards / answer not in notes / yes-no / answer in question / near-duplicate pairs; model time per stage.
- Workflow runs on changes to tools/ai-eval or AiStructure/AiOrganize/MindMap; results in annotations (3 steps: metrics, summaries, mind map + cards) and in the job log.
- `python tools/ai-eval/eval.py --stub` runs locally without the model (logic check).

## Pro UI
- Templates + TemplatePicker, PagePanel (⋯), tab bar contrast, document settings, toolbar customization page, default toolbar + ✦ AI button, library multi-select, «manual edits» bar removed from summaries.
- #168: bottom snackbar shows ONLY copy confirmations. #169: highlight-count badge on the summaries drawer button removed.

## Verified on device by user
- #132 template picker crash (fixed #136). #163: mind map PDF crash → fixed #164.

## Next steps
- User installs the AI v4 build and reports: summary quality/tables, mind map, cards, and real time per summary on the MatePad.
