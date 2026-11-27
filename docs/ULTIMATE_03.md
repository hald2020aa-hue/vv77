# Ultimate 03 — source health and responsive cover grids

Base: 20d9b30e11fe0e004e9dff3d3c088145f0dda327 (successful Actions run 46065010217), branch design/neon-reference-refined.

- One foreground scan gate for full, single, and deep source checks; four concurrent base probes within a full scan. Completed base results publish immediately. Refresh retains matching source results, including deep probe outcomes; changing a base address invalidates old results. Malformed request construction is caught within the individual probe. No change to the actual extension APIs, catalogue/chapter retrieval, cookies, translation or data storage.
- Add status counters, tested progress, status filters, response latency sorting, and Arabic resources. Fastest means the measured base URL response, not image download throughput or a guarantee of working chapters. No new automatic background probes.
- Neon library/source cover grid uses its own width after horizontal padding and recalculates across narrow window changes, not just large physical display changes. Preserves selected 2/3/5 density, and validates the current layout manager and position before restoring the visible anchor. No database or download changes.

Verification: four existing file blob hashes matched the GitHub base; pure Kotlin test methods ran with a local assertion shim; static regression/resource checks and patch applicability on those exact files. Full Android compilation, actual source requests and physical Fold open/close/rotation tests remain pending. Dual-pane layout and continuity of reader on fold are not part of this patch.
