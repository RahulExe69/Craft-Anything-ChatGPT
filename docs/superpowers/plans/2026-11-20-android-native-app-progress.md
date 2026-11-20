# SDD ledger — plan: docs/superpowers/plans/2026-11-20-android-native-app.md

## Workspace
Remote isolated branch: `feature/android-native-app`. A local git worktree could not be created because cloning github.com failed with DNS resolution in this environment. All repository edits are made on the isolated GitHub branch; `main` remains untouched.

## Pre-flight
Task interfaces are sequential: Task 1 produces the tested Rust bridge helpers consumed by Tasks 2 and 4. Tasks 2 and 3 can proceed independently after Task 1's bridge API is stable. Task 5 depends on the same bridge dispatch contract. Task 6 depends on platform classification and app-private path handling.

## Progress
- Task 1: tests added first. CI run `44721514466` confirmed RED: the missing `tool_catalog_json` / `dispatch_tool` helpers failed compilation, and the diagnostics also caught that jni 0.22's FFI argument is `EnvUnowned` rather than an API-capable `JNIEnv`. The bridge helpers are now extracted and the native methods use `EnvUnowned::with_env`. The bridge crate retains a local `unsafe_code = deny` policy with a single documented module allowance for JNI ABI symbol attributes; root workspace policy is unchanged. GREEN verification is pending.

- Task 2: four JVM tests are written (success, exact limit, oversized input cleanup, and mid-stream provider failure cleanup). `FileImport.copyBounded` is implemented and wired into the SAF picker; the UI now reports target name, kind and copied size. The Android workflow now runs `:app:testDebugUnitTest` before building the APK. Test/build verification is pending.

- CI setup note: the first CI run failed before Rust tests because the root `apps/*` Cargo workspace glob included the Gradle-only Android app directory. Fixed by explicitly listing the Rust app crates and retaining only `apps/craft-anything-android/rust-core` as the new workspace member.

- Latest Android workflow run observed before the Kotlin-test workflow update: `44722603256` (still in progress at its Android SDK setup step). Commit `cb6a49a` added the Kotlin unit-test step; a fresh run for that workflow revision is expected.
