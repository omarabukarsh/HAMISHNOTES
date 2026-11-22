# Store listing images

Every screen in these images is drawn by the app itself: the real Compose screens, rendered
by `app/src/test/java/com/hamishnotes/app/store/StoreScreenshots.kt`. Only the caption,
background and device frame are added on top.

## What to upload (Google Play Console → Main store listing)

| Folder | Size | Play Console slot |
|---|---|---|
| `listing/tablet-en/` | 2560×1600 (8 images) | 10-inch tablet screenshots, English |
| `listing/tablet-ar/` | 2560×1600 (8 images) | 10-inch tablet screenshots, Arabic |
| `listing/tabletp-en/` | 1600×2560 (7 images) | 7-inch tablet screenshots (or 10-inch portrait), English |
| `listing/tabletp-ar/` | 1600×2560 (7 images) | 7-inch tablet screenshots, Arabic |
| `listing/feature-graphic-1024x500.png` | 1024×500 | Feature graphic |

Order: 01 write on lectures · 02 summary · 03 library · 04 mind map · 05 flashcards ·
06 split view (landscape only) · stats · dark mode.

**Phone screenshots are not included yet** (Play needs at least 2). At phone width
(412 dp) the headless renderer draws the toolbar's scroll fade as a black block, and some
headings are cut off in the app itself ("Rec…" in the library, "Stud…" in Study stats).
Take phone screenshots on a real phone, or fix those headings first.

## Regenerate

1. Sample lectures and handwriting are in `tools/lectures/` (copy `app/src/sharedTest/assets/antibiotics.pdf`
   in too). `tools/make.js` rebuilds the lecture PDFs; `tools/notes.py` rebuilds the
   handwriting (`pip install Hershey-Fonts`).
2. Render the screens (needs poppler's `pdftoppm`/`pdfinfo`; one variant per Gradle run,
   because the app keeps its state in singletons):
   ```
   ./gradlew :app:testDebugUnitTest --tests '*StoreScreenshots.tabletEn' -PstoreShots=true \
     -PstoreShotsInput=$PWD/store/tools/lectures -PstoreShotsDir=$PWD/store/tools/shots
   ```
   Variants: `tabletEn`, `tabletAr`, `tabletPortraitEn`, `tabletPortraitAr`, `phoneEn`, `phoneAr`.
3. Frame them: `cd store/tools && node frame.js` (Playwright). `feature.html` is the feature graphic.

The screenshot test is skipped in normal test runs. `-PstoreShots=true` adds Robolectric's
native graphics runtime (≈160 MB) for that run only.
