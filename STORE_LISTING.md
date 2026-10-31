# Hamish — store listing and publishing checklist

App name on the store: **Hamish – Study PDF Notes** (Arabic: **هامش – مذاكرة وملاحظات PDF**).
Do not mention any other app or brand (title, description, keywords, screenshots).

## Short description (max 80 characters)

- EN: Write, highlight and summarize your PDF lectures with your pen.
- AR: اكتب وميّز ولخّص محاضراتك بصيغة PDF بالقلم.

## Full description — English

Hamish turns your PDF lectures into a study notebook.

- Write on any PDF with a fountain pen, ballpoint, brush pen or pencil, with pressure, line styles and stabilization.
- Highlight text that snaps to the words, and get an automatic summary of everything you highlighted.
- Shapes, arrows and connectors, sticky notes you can write or type on, text boxes and images.
- Lasso to move, resize, recolor or convert handwriting to typed text.
- Record a lecture while you write and tap your notes later to hear that moment.
- Flashcards, exam countdown, study timer and daily goal.
- Two documents side by side, page links, bookmarks and an editable outline.
- Works offline. Your files stay on your device.

## Full description — Arabic

«هامش» يحوّل محاضراتك بصيغة PDF إلى دفتر مذاكرة.

- اكتب على أي ملف PDF بقلم حبر أو جاف أو فرشاة أو رصاص، مع الضغط ونمط الخط وتثبيته.
- ميّز النص فيلتصق بالكلمات، واحصل على ملخص تلقائي لكل ما ميّزته.
- أشكال وأسهم وموصلات، وملاحظات لاصقة تكتب عليها بالقلم أو بلوحة المفاتيح، ومربعات نص وصور.
- حدّد كتابتك لتحريكها أو تكبيرها أو تغيير لونها أو تحويلها إلى نص مكتوب.
- سجّل المحاضرة أثناء الكتابة، ثم اختر كتابتك لتسمع ما قيل لحظتها.
- بطاقات مراجعة، وعدّاد للامتحان، ومؤقت مذاكرة وهدف يومي.
- مستندان جنبًا إلى جنب، وروابط بين الصفحات، وعلامات، وفهرس قابل للتعديل.
- يعمل دون إنترنت، وملفاتك تبقى على جهازك.

## Category and rating

- Category: Education (or Productivity).
- Content rating questionnaire: no violence, no user-generated content shared with others, no ads → expected rating: Everyone / 3+.
- Target audience: 13+ (students). Not designed for children.
- Ads: No. In-app purchases: none (unless added later).

## Data safety form (answer honestly; you are responsible for the final answers)

Your own app code collects and shares nothing. Google ML Kit (bundled) sends diagnostics to Google:

- Data collected: **Yes** (by the ML Kit SDK).
  - App info and performance → Diagnostics, Other app performance data (latency, errors, API configuration). Purpose: Analytics. Not shared with third parties. Not required for the user to choose (collected automatically).
  - Device or other IDs → per-installation identifier. Purpose: Analytics.
- Data shared with third parties: **No** (Google acts as the SDK service provider).
- Encrypted in transit: **Yes**.
- Users can request deletion: no account exists; data on the device is removed by uninstalling.
- Microphone/audio: recorded and stored **only on the device**, never transmitted → not "collected" under Play's definition.
- Files and docs, photos: processed on device only → not collected.
- Source: https://developers.google.com/ml-kit/android-data-disclosure

## Permissions to explain in Play Console

- RECORD_AUDIO: lecture recording, saved only on the device.
- Notifications / exact alarm / boot completed: daily study reminder.
- Biometric: optional document lock.

## Publishing steps

1. Create a Google Play developer account (one-time USD 25 fee) and verify your identity. Libya supports developer and merchant registration (default currency USD) — check the official list again before you start: https://support.google.com/googleplay/android-developer/answer/9306917
2. If you will sell the app or add in-app purchases: create a payments profile (merchant account). Payouts require a bank account that can receive international transfers in USD.
3. Host PRIVACY_POLICY.md and TERMS.md publicly (GitHub Pages or Google Sites) and add your contact email in both (and in the app's About texts).
4. Create a new **upload key** (keystore) outside the repository, keep two backups, and enroll in **Play App Signing**. Do not commit it. (The current committed keystore is for test builds only.)
5. Build an Android App Bundle (.aab) with the upload key and upload it to the Internal testing track first.
6. Fill in: store listing (texts above), screenshots of your own app only, app icon 512×512, feature graphic 1024×500, content rating, target audience, data safety, privacy policy URL, ads = no.
7. New personal developer accounts must run a closed test with testers for a period before production access — follow the Play Console instructions.
8. Before going commercial: trademark search for "Hamish" / «هامش» (WIPO Global Brand Database + Libyan trademark office), and a lawyer review of name, listing, privacy policy and terms.
