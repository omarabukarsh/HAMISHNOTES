# CLAUDE.md — MyNotes Pro (branch `pro`)

Read `claude/PRO_STATE.md` first (project state, history, pitfalls) and check `git log`/`git status` before redoing work. Update `claude/PRO_STATE.md` at the end of each task.

## Rules (from the user — mandatory)
- Work on branch `pro` only. NEVER modify `feature/premium-ui` (regular MyNotes) or `ios-app`.
- Every build must install as an update over the previous one: never change `applicationId` (`com.mynotes.app.pro`) or the signing key (`keystore/`).
- On-device AI must work fully offline (user: HUAWEI MatePad 11.5 S, 8 GB RAM, Gemma 4 E2B via LiteRT-LM on CPU). Keep model requests small and fast.
- Never ask the user for API keys in chat.
- AI features must work for any subject and any file type (Arabic and English), not only pharmacy and not tuned to one file.
- For big changes: read the code first, send a plan in Arabic (problems found, improvements by priority, files, speed impact on the device, how it will be verified, risks) and wait for «نفّذ».
- Priority: accuracy of summaries first, speed second.
- Answer the user in Arabic, VERY short (user, 2026-10-29): a few lines, no long explanations.

## Standing order: "build" / «ابنِ» (user, 2026-10-29) — do it with no further questions
1. `git fetch origin pro`, fast-forward, read PRO_STATE.md, `git log -5`/`git status`; never build a stale checkout; tell the user the commit hash.
2. If GitHub Actions runs: push if needed, wait for **Build APK**, give the artifact link (below). If Actions refuses (billing "recent account payments have failed"), build locally per PRO_STATE «How to build locally» with `GITHUB_RUN_NUMBER` higher than the last build in PRO_STATE.
3. Compile errors: fix only the root cause, re-read the diff, rebuild until it succeeds.
4. Verify with aapt2 + apksigner: package com.mynotes.app.pro, label «HAMISH PRO», cert SHA-256 = keystore/mynotes.jks (f0b818a6…9b38). Report truthfully.
5. Deliver the APK as a file, zipped if large. Keep ALL ABIs (arm64-only build 181 broke the app).
6. If AiStructure.kt / AiOrganize.kt / AiMindMap.kt / tools/ai-eval changed since the last eval: run the eval (CI or locally with the real model) and show before/after, reading the outputs.
7. Ignore "CI tests" (failing since before this work).
8. Update PRO_STATE.md (build number, commit, fixes, results) and push with [skip ci].
Be honest about what was verified; never claim on-device behaviour (e.g. image reading on Kirin) that only the user can confirm with «اختبر قراءة الصور».

## After every push to `pro`
- Wait for **Build APK** to succeed, then give the direct link:
  `https://github.com/omarabukarsh/Mynotes/actions/runs/<runId>/artifacts/<artifactId>` (artifact «MyNotes-Pro»).
- For AI changes (AiStructure.kt, AiOrganize.kt, AiMindMap.kt, tools/ai-eval): wait for **AI summary eval** and show before/after results; read the outputs themselves, not only the metrics.
- "CI tests" has been failing since before #166 (golden/SummaryRules unit tests + emulator); unrelated to AI work unless told otherwise.

## Build and checks
- GitHub Actions is the normal compiler; when it is blocked, build locally (PRO_STATE «How to build locally»). Re-read Kotlin diffs carefully before pushing.
- `python tools/ai-eval/eval.py --stub` runs the eval logic locally without the model; the real model runs only in CI.
- `tools/ai-eval/checks.py` and `pipeline.py` mirror the Kotlin AI logic: change both together.
- Cloud session cannot reach huggingface.co or GitHub artifact/log blob storage; read CI results via job logs or check-run annotations.
- Commit messages and code comments must not contain a model identifier.
