# SDD ledger — plan: docs/superpowers/plans/2026-11-20-android-native-app.md

## Workspace
Remote isolated branch: `feature/android-native-app`. A local git worktree could not be created because cloning github.com failed with DNS resolution in this environment. All repository edits are made on the isolated GitHub branch; `main` remains untouched.

## Pre-flight
Task interfaces are sequential: Task 1 produces the tested Rust bridge helpers consumed by Tasks 2 and 4. Tasks 2 and 3 can proceed independently after Task 1's bridge API is stable. Task 5 depends on the same bridge dispatch contract. Task 6 depends on platform classification and app-private path handling.

## Progress
- Task 1: tests added first. CI run `44721514466` confirmed RED: missing `tool_catalog_json` / `dispatch_tool` helpers and the jni 0.22 `EnvUnowned` API mismatch. The helpers are now extracted and JNI uses `EnvUnowned::with_env`. Run `44722705865` passed the earlier bridge test revision. A newer run `44723291345` caught one test-only Rust type mismatch in the schema/consent catalogue test (`&[]` vs `&Vec<Value>`); fixed in commit `7758ec4` by using an explicitly typed empty vector. The catalogue exposes JSON input schemas, toolset titles, effect bits and `needs_consent()`. The consent boolean is forwarded through JNI to `CallOptions::with_consent` for one invocation only. The latest fix still needs a green run. The bridge crate retains a local `unsafe_code = deny` policy with one module-local allowance for JNI ABI symbol attributes; root workspace policy is unchanged.

- Task 2: four JVM tests are written (success, exact limit, oversized input cleanup, and mid-stream provider failure cleanup). `FileImport.copyBounded` is implemented and wired into the SAF picker; the UI now reports target name, kind and copied size. The Android workflow now runs `:app:testDebugUnitTest` before building the APK. Run `44722705865` has passed the Rust bridge tests and is currently in the Kotlin JVM test step; result/build verification is pending.

- CI setup note: the first CI run failed before Rust tests because the root `apps/*` Cargo workspace glob included the Gradle-only Android app directory. Fixed by explicitly listing the Rust app crates and retaining only `apps/craft-anything-android/rust-core` as the new workspace member.

- Task 4: `ToolsScreen.kt` now parses the real Rust catalogue, supports text search, shows the input schema and a JSON parameter editor, invokes tools and renders the latest result. Consent-gated tools show a per-invocation approval dialog; the validated JSON is preserved through the dialog, and approval is passed to the engine only for that single call. Four Android Compose UI tests cover registry rendering, filtering, consent gating and a safe invocation. The workflow now runs `:app:connectedDebugAndroidTest` on an API 35 x86_64 emulator; the first run of that new step is pending.

- Current CI runs: `44722705865` (Rust tests green; JVM tests in progress) and `44723291345` (Rust tests still in progress as last observed). The latest consent changes landed after those checkouts. Run `44724129779` is the first one covering the complete browser/consent/emulator configuration and is progressing through the Rust bridge test stage; fresh verification is still required after the Gradle build-graph optimization.


- Task 5 (first slice): The JNI bridge now accepts the Android app's `filesDir` on `nativeInitialize`, configures `Settings.projects_dir = filesDir/projects`, and creates that private directory. The Projects tab loads the actual `project_list` tool output, can refresh, opens a project with `project_show`, and displays status, request, stack, waiting questions and conversation counts. Added an Android instrumentation test for list parsing/rendering/navigation and a Rust test checking the configured project directory. The latest code changes need fresh CI verification; evidence browsing is still pending.
