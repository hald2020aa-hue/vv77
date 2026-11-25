#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
TMP="$(mktemp -d)"
trap 'rm -rf "$TMP"' EXIT
K="$ROOT/app/src/main/java/eu/kanade/tachiyomi/ui"

# ReaderV6Policy only needs these two data shapes for its pure policy test.
# Keep the stub in the same package without compiling Android/ML Kit services.
cat > "$TMP/TranslationPageTestStub.kt" <<'KOTLIN'
package eu.kanade.tachiyomi.ui.reader.translation

data class TranslatedTextBlock(val arabic: String)
data class TranslatedReaderPage(val error: String? = null, val blocks: List<TranslatedTextBlock> = emptyList())
KOTLIN

kotlinc \
  "$TMP/TranslationPageTestStub.kt" \
  "$K/reader/translation/ReaderV6Policy.kt" \
  "$K/reader/translation/ReaderV7Policy.kt" \
  "$K/enhanced/AdaptiveReaderWidth.kt" \
  "$K/enhanced/DownloadQueueSort.kt" \
  "$K/enhanced/ReadingProgress.kt" \
  "$K/enhanced/NeonUiMath.kt" \
  "$ROOT/qa/v6/ReaderV6PolicyChecks.kt" \
  "$ROOT/qa/v7/StandaloneV7Checks.kt" \
  "$ROOT/qa/v8/StandaloneV8Checks.kt" \
  "$ROOT/qa/neon/AdaptiveReaderChecks.kt" \
  "$ROOT/qa/neon/DownloadSortChecks.kt" \
  "$ROOT/qa/neon/ReadingProgressChecks.kt" \
  "$ROOT/qa/neon/V5UiMathChecks.kt" \
  -include-runtime -d "$TMP/policies.jar"

java -cp "$TMP/policies.jar" ReaderV6PolicyChecksKt
java -cp "$TMP/policies.jar" qa.v7.StandaloneV7ChecksKt
java -cp "$TMP/policies.jar" StandaloneV8ChecksKt | tail -1
java -cp "$TMP/policies.jar" AdaptiveReaderChecksKt
java -cp "$TMP/policies.jar" DownloadSortChecksKt
java -cp "$TMP/policies.jar" ReadingProgressChecksKt
java -cp "$TMP/policies.jar" V5UiMathChecksKt
