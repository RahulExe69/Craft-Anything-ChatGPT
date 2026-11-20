# SDD ledger — plan: docs/superpowers/plans/2026-11-20-android-native-app.md

## Workspace
Remote isolated branch: `feature/android-native-app`. A local git worktree could not be created because cloning github.com failed with DNS resolution in this environment. All repository edits are made on the isolated GitHub branch; `main` remains untouched.

## Pre-flight
Task interfaces are sequential: Task 1 produces the tested Rust bridge helpers consumed by Tasks 2 and 4. Tasks 2 and 3 can proceed independently after Task 1's bridge API is stable. Task 5 depends on the same bridge dispatch contract. Task 6 depends on platform classification and app-private path handling.

## Progress
- Task 1: in progress. Contract tests for the engine catalogue, real session-status dispatch, and malformed JSON errors have been added in commit `0ae12c8`. Expected RED result: the tests do not compile until `tool_catalog_json` and `dispatch_tool` are extracted from the JNI wrappers. CI verification is pending.

- Debugging note: the first CI run failed before tests because the root `apps/*` Cargo workspace glob included the Gradle-only Android app directory. Fixed by enumerating the existing Rust app crates explicitly and retaining only `apps/craft-anything-android/rust-core` as the new workspace member. The next CI run must still demonstrate the intended missing-helper RED result.
