# SDD ledger — plan: docs/superpowers/plans/2026-11-20-android-native-app.md

## Workspace
Remote isolated branch: `feature/android-native-app`. A local git worktree could not be created because cloning github.com failed with DNS resolution in this environment. All repository edits are made on the isolated GitHub branch; `main` remains untouched.

## Pre-flight
Task interfaces are sequential: Task 1 produces the tested Rust bridge helpers consumed by Tasks 2 and 4. Tasks 2 and 3 can proceed independently after Task 1's bridge API is stable. Task 5 depends on the same bridge dispatch contract. Task 6 depends on platform classification and app-private path handling.

## Progress
- Task 1: tests added first. CI run `44721514466` confirmed RED: the missing `tool_catalog_json` / `dispatch_tool` helpers failed compilation, and the diagnostics also caught that jni 0.22's FFI argument is `EnvUnowned` rather than an API-capable `JNIEnv`. The bridge helpers are now extracted and the native methods use `EnvUnowned::with_env`. Run `44722705865` completed the Rust bridge test step successfully after that migration. The catalogue now also exports the real JSON input schema, toolset title, effects and `needs_consent()` bit. A one-call consent flag is forwarded through JNI into `CallOptions::with_consent`; the latest schema/consent changes still need fresh CI confirmation. The bridge crate retains a local `unsafe_code = deny` policy with a single documented module allowance for JNI ABI symbol attributes; root workspace policy is unchanged.

- Task 2: four JVM tests are written (success, exact limit, oversized input cleanup, and mid-stream provider failure cleanup). `FileImport.copyBounded` is implemented and wired into the SAF picker; the UI now reports target name, kind and copied size. The Android workflow now runs `:app:testDebugUnitTest` before building the APK. Run `44722705865` has passed the Rust bridge tests and is currently in the Kotlin JVM test step; result/build verification is pending.

- CI setup note: the first CI run failed before Rust tests because the root `apps/*` Cargo workspace glob included the Gradle-only Android app directory. Fixed by explicitly listing the Rust app crates and retaining only `apps/craft-anything-android/rust-core` as the new workspace member.

- Task 4: `ToolsScreen.kt` now parses the real Rust catalogue, supports text search, shows the input schema and a JSON parameter editor, invokes tools and renders the latest result. Consent-gated tools show a per-invocation approval dialog; the validated JSON is preserved through the dialog, and approval is passed to the engine only for that single call. Four Android Compose UI tests cover registry rendering, filtering, consent gating and a safe invocation. A CI instrumentation test step still needs to be added; current workflow only runs JVM unit tests.

- Current CI runs: `44722705865` (Rust tests green; JVM tests in progress) and `44723291345` (Rust tests still in progress as last observed). The latest consent changes landed after those checkouts, so they must still be verified on a newer run.
