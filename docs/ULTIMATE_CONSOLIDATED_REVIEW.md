# Ultimate consolidated review checkpoint — NOT A RELEASE
Base: GitHub build 46065010217, commit 20d9b30e11fe0e004e9dff3d3c088145f0dda327.

Includes the previously unpushed Ultimate 03 patch, a session-only webtoon auto-scroll control, local-library genre suggestions and possible cross-source duplicates, and improvements to notification accent, update progress and privacy.

The new features do not mutate library or chapter records; suggestions open the existing manga details screen. The duplicate list is conservative, based only on normalized titles in the local library; it is not a global cross-source search. Auto-scroll uses three speeds and stops on touch, background, zoom, menu, errors, and chapter transitions. Notification changes use system templates; the existing premium download notification is untouched.

Outstanding: dual-pane navigation for Fold, full reader preload controls and smart zoom, advanced download recovery/ETA validation, storage quota and cleanup preview, broader live alternative-source search, consistent custom notifications for all relevant categories, performance profiling and device tests. Do not treat this checkpoint as the final package or push it yet.

Verification scope: local pure Kotlin assertion harness and static checks, resource XML parsing and clean patch application on a locally reconstructed baseline with matching Git blob IDs for the edited existing files. The full exact repository and Android SDK are unavailable here. No Android Gradle build or on-device test was performed.

Continuation: the existing reader preload slider now offers 0–20 pages (0 disables speculative next-chapter requests, not on-demand chapter transitions). The near-end threshold is bounded by 5 pages. ETA arithmetic rejects invalid inputs and rounds up unfinished transfers without overflow. The local Kotlin harness ran 51 assertions and the health/grid static gate passed 15 checks. Android build attempt failed before compilation: Gradle distribution cannot be fetched (UnknownHostException: services.gradle.org); Android SDK is not installed. This is still a checkpoint, not a release.
