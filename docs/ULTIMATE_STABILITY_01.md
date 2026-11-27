# Ultimate — stability increment 01

Base: `4ce371a26669ceb845cf43de4cec45456bc0f85e` on `design/neon-reference-refined` (successful run 46045899788). Five production Kotlin files; all baseline Git blob hashes checked against GitHub. No database, source extensions, translation, resources or application ID changes.

- Download queue: one observer group per active chapter; release on pause, error, completion, removal or clear; copy-on-write listener list.
- Downloader: propagate cancellation, check cancellation between stream reads and writes, close HTTP responses, retain existing retry delays and clamp concurrency to existing settings ranges (sources 1–10; pages 1–15). A blocked network read can still wait for the HTTP timeout; no claim of instant pause.
- Reader: prioritize visible page, avoid duplicate active requests, fall back to source when cached chapter page list is empty. Current in-flight network request is not forcibly cancelled when moving to a different page.
- Download center: rate limit full list snapshots to about 8/s, cache counts once per manga per snapshot. Existing per-row transfer StateFlows remain independent.
- Retry button: confirm retry of failed, non-manually-paused chapters using existing resume API. Starting the normal scheduler can also process already queued items. Does not delete complete files.

Validation: static regression checks and patch replay; actual Android build and phone testing pending. This is the first increment, not the complete Ultimate roadmap. No measured performance improvement asserted. Follow-up: storage, library, source health, foldable layout and startup/battery profiling remain. Runtime test: pause/resume repeatedly, cancel mid-transfer, retry failed chapter, open empty-cached chapter and quickly switch pages, check queue and library preservation.
