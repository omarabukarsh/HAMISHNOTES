#!/usr/bin/env bash
# يشغّل اختبارات الجهاز على المحاكي ثم يسحب التقارير ولقطات الشاشة وسجل logcat مهما كانت النتيجة.
set -u
OUT=app/build/device-reports
mkdir -p "$OUT"
adb logcat -c || true
./gradlew --no-daemon connectedDebugAndroidTest -Pandroid.injected.androidTest.leaveApksInstalledAfterRun=true
status=$?
adb pull /sdcard/Android/data/com.hamishnotes.app/files/test-reports "$OUT/" || true
adb logcat -d > "$OUT/logcat.txt" || true
adb exec-out screencap -p > "$OUT/final-screen.png" || true
exit $status
