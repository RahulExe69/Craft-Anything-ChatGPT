# SDD ledger — plan: docs/superpowers/plans/2026-11-20-android-native-app.md

## Workspace
Remote isolated branch: `feature/android-native-app`. A local git worktree could not be created because cloning github.com failed with DNS resolution in this environment. All repository edits are made on the isolated GitHub branch; `main` remains untouched.

## Pre-flight
Task interfaces are sequential: Task 1 produces the tested Rust bridge helpers consumed by Tasks 2 and 4. Tasks 2 and 3 can proceed independently after Task 1's bridge API is stable. Task 5 depends on the same bridge dispatch contract. Task 6 depends on platform classification and app-private path handling.

## Progress
- Task 1: tests added first. CI run `44721514466` confirmed RED: missing `tool_catalog_json` / `dispatch_tool` helpers and the jni 0.22 `EnvUnowned` API mismatch. The helpers are now extracted and JNI uses `EnvUnowned::with_env`. Run `44722705865` passed the earlier bridge test revision. A newer run `44723291345` caught one test-only Rust type mismatch in the schema/consent catalogue test (`&[]` vs `&Vec<Value>`); fixed in commit `7758ec4` by using an explicitly typed empty vector. The catalogue exposes JSON input schemas, toolset titles, effect bits and `needs_consent()`. The consent boolean is forwarded through JNI to `CallOptions::with_consent` for one invocation only. The latest fix still needs a green run. The bridge crate retains a local `unsafe_code = deny` policy with one module-local allowance for JNI ABI symbol attributes; root workspace policy is unchanged.

- Task 2: six JVM tests now cover successful copy, exact limit, oversized-input cleanup, mid-stream provider failure cleanup, and safe filename suffix handling. `FileImport.copyBounded` is implemented and wired into the SAF picker; the UI now reports target name, kind and copied size. The Android workflow now runs `:app:testDebugUnitTest` before building the APK. Run `44722705865` has passed the Rust bridge tests and is currently in the Kotlin JVM test step; result/build verification is pending.

- CI setup note: the first CI run failed before Rust tests because the root `apps/*` Cargo workspace glob included the Gradle-only Android app directory. Fixed by explicitly listing the Rust app crates and retaining only `apps/craft-anything-android/rust-core` as the new workspace member.

- Task 4: `ToolsScreen.kt` now parses the real Rust catalogue, supports text search, shows the input schema and a JSON parameter editor, invokes tools and renders the latest result. Consent-gated tools show a per-invocation approval dialog; the validated JSON is preserved through the dialog, and approval is passed to the engine only for that single call. Four Android Compose UI tests cover registry rendering, filtering, consent gating and a safe invocation. The workflow now runs `:app:connectedDebugAndroidTest` on an API 35 x86_64 emulator; the first run of that new step is pending.

- Current CI runs: `44722705865` (Rust tests green; JVM tests in progress) and `44723291345` (Rust tests still in progress as last observed). The latest consent changes landed after those checkouts. Run `44724129779` is the first one covering the complete browser/consent/emulator configuration and is progressing through the Rust bridge test stage; fresh verification is still required after the Gradle build-graph optimization.


- Task 5 (first slice): The JNI bridge now accepts the Android app's `filesDir` on `nativeInitialize`, configures `Settings.projects_dir = filesDir/projects`, and creates that private directory. The Projects tab loads the actual `project_list` tool output, can refresh, opens a project with `project_show`, and displays status, request, stack, waiting questions and conversation counts. Added an Android instrumentation test for list parsing/rendering/navigation and a Rust test checking the configured project directory. The latest code changes need fresh CI verification; evidence browsing is still pending.


- Task 5 (evidence slice): Added `EvidenceScreen.kt` and `EvidenceScreenTest.kt`. The tab fetches `core_evidence_show` with `detail=summary` plus `core_unknowns_show`; the view surfaces operation, summary, confidence, coverage, limitations, and unknown question/domain/severity/status. Tests cover parsing, populated rendering and empty states. Evidence is scoped to the live engine session; project-level historical evidence is not yet merged into this view. The Projects screen separately answers pending inbox requests through `project_inbox_answer`. Latest recorded Android workflow run `44726500084` is still in its host Rust test step as last inspected; it checks out an earlier revision than the latest inbox-response and extension-preservation commits. Emulator UI tests and APK packaging remain unverified.


- SAF format-detection correction: The engine's `Target::sniff` uses the filename as a tie-breaker for ZIP/APK/JAR and several extension-only formats. `FileImport.safeSuffix` now preserves a short alphanumeric extension in lowercase and falls back to `.bin` for hidden, absent, malformed or excessive extensions. Tests were added before the implementation.
- Projects inbox correction: The engine's `Request` type is `Request { id, kind, title, detail, choices, allow_text, action, created_ms }`. The Projects screen now presents the right title/detail, choice buttons or typed-answer field, and approval Allow/Deny actions. MainActivity submits answers using `project_inbox_answer`, then reloads the project and project list. No `always=true` grant is offered.


## Follow-up review and CI reliability fixes

- Reviewed all 14 CodeRabbit findings against the current source. Addressed the actionable issues: KVM permissions before emulator boot; APK build/upload before the emulator step; Rust task input tracking across the workspace; Android INTERNET permission; Compose test callback arity and invalid import; SAF stream closure on directory failure; response-envelope validation; stale project-detail generation guards; system Back handling; stable inbox card keys; bounded scrollable tool output; duplicate target-import cleanup; and the app-level importer extraction.
- Added `AndroidTargetImporter` to own bounded file copy, engine hand-off and cleanup. `ContentResolver.openInputStream(uri)` remains in the activity, where Android exposes provider streams.
- Removed lazy default-engine creation. Native tool calls now return a structured not-initialized error until `nativeInitialize` has successfully configured the app-private projects directory.
- Replaced hand-written unsafe JNI export-name attributes with jni-rs 0.22.4's `jni_mangle` proc macro and set the bridge crate's `unsafe_code = "forbid"`, in line with AGENTS.md.
- Added JVM tests for AndroidTargetImporter success, failure cleanup and duplicate-target relocation cleanup.
- The previous Android workflow run passed host Rust bridge tests and JVM unit tests, then failed because the emulator never appeared on ADB and timed out after the default boot wait. The APK build/upload steps were skipped in that older workflow ordering. The workflow now applies the documented KVM udev rule and builds/uploads before emulator tests.
- The previous repo Gate run failed rustfmt on the bridge source. The bridge has been reformatted to the exact output shown by that run; confirmation is pending in the next Gate run.
- Current branch changes after these fixes have not yet been verified by a completed Android APK workflow. No APK is claimed.
