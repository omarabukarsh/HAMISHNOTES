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

## After every push to `pro`
- Wait for **Build APK** to succeed, then give the direct link:
  `https://github.com/omarabukarsh/Mynotes/actions/runs/<runId>/artifacts/<artifactId>` (artifact «MyNotes-Pro»).
- For AI changes (AiStructure.kt, AiOrganize.kt, MindMap.kt, tools/ai-eval): wait for **AI summary eval** and show before/after results; read the outputs themselves, not only the metrics.
- "CI tests" has been failing since before #166 (golden/SummaryRules unit tests + emulator); unrelated to AI work unless told otherwise.

## Build and checks
- No Android SDK in the cloud session: GitHub Actions is the compiler. Re-read Kotlin diffs carefully before pushing.
- `python tools/ai-eval/eval.py --stub` runs the eval logic locally without the model; the real model runs only in CI.
- `tools/ai-eval/checks.py` and `pipeline.py` mirror the Kotlin AI logic: change both together.
- Cloud session cannot reach huggingface.co or GitHub artifact/log blob storage; read CI results via job logs or check-run annotations.
- Commit messages and code comments must not contain a model identifier.
