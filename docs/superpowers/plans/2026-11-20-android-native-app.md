# Native Android application implementation plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build a native Android Craft Anything app that reuses the Rust engine for real file inspection and progressively brings over the supported desktop workflows.

**Architecture:** Kotlin + Jetpack Compose owns mobile UI, lifecycle and Android permissions. A narrow JNI library calls the existing Rust engine and returns structured JSON; Android adapters handle SAF files and platform-specific capabilities. Desktop-only workflows remain explicitly companion-backed or unavailable until a safe Android implementation exists.

**Tech Stack:** Kotlin, Jetpack Compose, Material 3, Gradle 8.13, Android SDK 36, Android NDK 28.2, Rust workspace, JNI 0.22, GitHub Actions.

**Spec:** `docs/superpowers/specs/2026-11-20-android-native-app-design.md`

## Global Constraints

- Keep the Rust workspace's `unsafe_code = "forbid"` policy intact.
- Keep UI thin: tool execution, consent and evidence remain in `craft-anything-engine`.
- Use Android SAF for user-selected files; copy at most 512 MiB into app-private storage in the initial file-import flow.
- Target Android API 26+; compile against API 36; support arm64-v8a first and x86_64 for emulator builds.
- Preserve explicit consent for tools with `EXECUTES_FOREIGN_CODE` or `MUTATES_DEVICE`.
- Never claim desktop feature parity for unavailable Android APIs or external binaries.

## Review Focus

- A SAF provider returns a null stream, throws mid-copy, or supplies a file larger than 512 MiB; no partial file is opened and the UI reports a useful error.
- A target path contains spaces, Unicode or unusual characters; JSON/JNI dispatch preserves it exactly.
- A tool id is unknown or parameters are malformed; the bridge returns structured errors without panicking.
- A native library is missing or fails to load; the app remains explainable instead of crashing during startup.
- The Android process is recreated during work; no UI state or tool result is silently presented as durable when it was only in memory.

---

### Task 1: Make the Rust bridge independently testable

**Files:**
- Modify: `apps/craft-anything-android/rust-core/src/lib.rs`
- Modify: `apps/craft-anything-android/rust-core/Cargo.toml`

**Interfaces:**
- Produces `tool_catalog_json(engine: &Engine) -> String`.
- Produces `dispatch_tool(engine: &Engine, tool_id: &str, params_json: &str) -> String`, returning a JSON envelope with `ok`, `output`, or `error`.
- JNI entry points delegate to these helpers; no business logic remains in JNI wrappers.

- [ ] **Step 1: Write failing Rust tests** for the catalogue containing `core_target_open`, a successful `core_session_show` dispatch, and malformed JSON returning `ok: false` with a useful error.
- [ ] **Step 2: Run the bridge tests** with `cargo test -p craft-anything-android-bridge --lib`; confirm they fail because the helper API is not implemented.
- [ ] **Step 3: Extract the helpers** and make the JNI exports delegate to them.
- [ ] **Step 4: Re-run bridge tests** and the host workspace checks for the bridge.
- [ ] **Step 5: Commit** the bridge refactor and tests.

### Task 2: Finish the native file-import experience

**Files:**
- Modify: `apps/craft-anything-android/app/src/main/java/ai/storytold/craftanything/MainActivity.kt`
- Create: `apps/craft-anything-android/app/src/main/java/ai/storytold/craftanything/FileImport.kt`
- Create: `apps/craft-anything-android/app/src/test/java/ai/storytold/craftanything/FileImportTest.kt`
- Modify: `apps/craft-anything-android/app/build.gradle.kts`

**Interfaces:**
- Produces `FileImport.copyBounded(input: InputStream, destination: File, maxBytes: Long): Long`.
- The UI uses the Android OpenDocument contract, invokes `core_target_open` off the main thread and displays target name, kind, size and structured errors.

- [ ] **Step 1: Write failing unit tests** for successful copy, exact-limit copy, over-limit rejection and stream failure cleanup.
- [ ] **Step 2: Run the Kotlin unit tests** and confirm the helper is missing.
- [ ] **Step 3: Implement the bounded copy helper** and remove partial output on failure.
- [ ] **Step 4: Connect the helper to the SAF picker** and render the opened target's useful metadata.
- [ ] **Step 5: Run Kotlin unit tests** and build the debug APK.

### Task 3: Make the Android project reproducible for developers

**Files:**
- Create: Gradle wrapper scripts and wrapper JAR under `apps/craft-anything-android/gradle/wrapper/`
- Modify: `apps/craft-anything-android/README.md`
- Modify: `.github/workflows/android.yml`

**Interfaces:**
- Developers can build with `./gradlew :app:assembleDebug` after installing JDK 17, Android SDK/NDK and cargo-ndk.
- CI uploads an arm64-v8a and x86_64 debug APK artifact.

- [ ] **Step 1: Add wrapper files** pinned to Gradle 8.13.
- [ ] **Step 2: Update documentation** to use the wrapper and list exact SDK/NDK prerequisites.
- [ ] **Step 3: Run the CI build** and inspect the actual job logs; fix only verified build failures.
- [ ] **Step 4: Commit** the reproducible build setup.

### Task 4: Add a mobile tool browser and invocation surface

**Files:**
- Modify: `apps/craft-anything-android/app/src/main/java/ai/storytold/craftanything/MainActivity.kt`
- Create: `apps/craft-anything-android/app/src/main/java/ai/storytold/craftanything/ToolsScreen.kt`
- Create: `apps/craft-anything-android/app/src/test/java/ai/storytold/craftanything/ToolsScreenTest.kt`

**Interfaces:**
- Display registered tools from `tool_catalog_json`, grouped by toolset and searchable by id/title/summary.
- Invoke only selected registered tools through `dispatch_tool`; show structured output and errors.
- Display consent-required effects before invocation; never silently auto-approve them.

- [ ] **Step 1: Write failing UI tests** for registry rendering, search filtering and consent-required action gating.
- [ ] **Step 2: Run UI tests** and confirm the expected screen behavior is missing.
- [ ] **Step 3: Implement the searchable tool browser** and structured invocation UI.
- [ ] **Step 4: Run UI tests** and verify the screen on an Android emulator.

### Task 5: Add project and evidence workflows

**Files:**
- Create: `apps/craft-anything-android/app/src/main/java/ai/storytold/craftanything/ProjectsScreen.kt`
- Create: `apps/craft-anything-android/app/src/main/java/ai/storytold/craftanything/EvidenceScreen.kt`
- Modify: Rust bridge and Android navigation.

**Interfaces:**
- Expose project list/open/create and evidence browse/export through existing engine tools.
- Persist user-visible state using app-private storage; do not present process-memory-only results as durable.

- [ ] **Step 1: Write failing bridge tests** for project list and evidence list responses.
- [ ] **Step 2: Run tests** and confirm unsupported bridge paths.
- [ ] **Step 3: Implement only the bridge calls required by the screens.**
- [ ] **Step 4: Add Compose screens and state restoration tests.**
- [ ] **Step 5: Run the bridge and UI suites.**

### Task 6: Android platform adapters and AI integration

**Files:**
- Modify: `crates/platform/**` only where Android behavior belongs.
- Modify: `crates/ai/**` for an Android-safe credential provider and AI hub lifecycle.
- Modify: Android app settings and permission flows.

**Interfaces:**
- Store secrets with Android Keystore; do not silently fall back to plaintext storage.
- Add explicit MediaProjection consent for screen capture.
- Keep external CLI tools and heavyweight desktop engines companion-backed unless an Android-compatible binary and sandbox-safe execution path are verified.
- Provide backend API configuration and chat/rebuild workflow through the existing `AiHub` APIs only after Android network, credential and lifecycle tests exist.

- [ ] **Step 1: Write failing tests** for Android credential availability and platform capability classification.
- [ ] **Step 2: Verify the failures** on host and Android targets where possible.
- [ ] **Step 3: Implement Android-specific adapters** without changing desktop behavior.
- [ ] **Step 4: Add AI chat/rebuild screens** behind explicit setup and consent.
- [ ] **Step 5: Run host and Android CI suites** and document remaining companion/unavailable capabilities.

## Execution Notes

- The current branch already contains an initial shell, JNI proof-of-concept, SAF file-open path, process-watcher shell selection and an Android CI workflow. Treat those as unverified scaffolding until the tests and CI runs above prove them.
- This harness has GitHub repository access but the local clone failed because DNS/network access to github.com was unavailable. Do not claim a build passed until GitHub Actions or a local Android build provides actual logs.
- Keep work on `feature/android-native-app`; never commit directly to `main`.
