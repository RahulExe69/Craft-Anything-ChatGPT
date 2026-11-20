# Native Android application port

## Product direction

Build a real Android application with a native Kotlin + Jetpack Compose interface. Do not ship
the existing browser viewer inside a WebView and call it feature-complete. The browser viewer is
intentionally a limited, wasm-safe file inspector and does not include the full engine, AI layer,
native platform services or workbench.

The Android app should feel designed for a phone: bottom navigation, compact project/workspace
surfaces, touch-first tool discovery, mobile-safe sheets and dialogs, responsive evidence views,
and a clear permission/consent flow. Keep the Craft Anything identity and terminology, but do not
force the desktop layout onto a narrow screen.

## Architecture decision

- **UI:** Kotlin, Jetpack Compose, Material 3, lifecycle-aware state and navigation.
- **Core:** reuse the existing Rust crates through a narrow Android bridge. Prefer a stable,
  typed API that exposes session creation, tool catalogue/availability, tool invocation,
  progress, cancellation, project state and evidence. The Kotlin layer must not reach into Rust
  internals or duplicate orchestration.
- **Bridge:** make a short proof-of-compatibility spike before committing to JNI versus UniFFI.
  JNI is a straightforward first option; UniFFI is worth evaluating for generated Kotlin
  bindings and typed data. Keep the public bridge small either way.
- **Android platform adapters:** use Storage Access Framework for user-selected files and
  folders; MediaProjection only for explicitly approved screen capture; Android foreground
  services/work scheduling only for work that genuinely must continue; Android Keystore for
  secrets; and app-private storage for settings and project metadata.
- **Build:** Gradle Android app + Android NDK Rust cross-compilation for arm64-v8a first. Add
  x86_64 emulator builds for CI and debugging. Produce debug APKs early; add signed release
  bundles only after device testing and permission review.

## Feature-parity rules

The goal is maximum useful parity, not a dishonest promise that desktop behavior can be copied
1:1. Android's app sandbox, scoped storage, background execution limits, permission model and
lack of desktop window/process APIs change what can be done.

Every existing capability must be classified as one of:

1. **Native on Android:** works on-device with Android APIs and an Android-compatible Rust build.
2. **Adapted:** same user outcome, implemented with an Android-specific adapter or workflow.
3. **Companion/remote:** needs a separate machine or explicitly configured remote worker (for
   example, desktop coding-agent CLIs or heavyweight external reverse-engineering tools).
4. **Unavailable on Android:** cannot be provided safely or reliably on the phone; explain why
   in the UI rather than showing a broken control.

Never silently claim a capability ran if Android could not perform it. Preserve the engine's
consent and evidence contracts.

## Delivery phases

### Phase 0 — compatibility and inventory
- Build a dependency map for the engine, AI, platform and optional engine adapters on
  `aarch64-linux-android`.
- Identify unsupported target-specific dependencies, OS assumptions, process APIs, filesystem
  assumptions, credential storage and external binaries.
- Run a minimal Rust Android library build before building the UI around an unproven bridge.
- Produce a tool-by-tool feature-parity matrix from the existing registry.

### Phase 1 — Android shell and bridge
- Create the Gradle/Compose app, app identity, navigation shell, theme and lifecycle handling.
- Add the Rust bridge with explicit initialization, structured errors, typed tool metadata,
  asynchronous calls, progress events and cancellation.
- Add a small vertical slice: open a user-selected file, inspect it with supported Rust tools,
  display evidence and save/reopen a project.

### Phase 2 — core workflows
- Add project list/detail, target selection, beginner workflow, tool search, evidence ledger,
  questions/approvals, job progress and settings.
- Adapt settings and credentials to Android storage and Keystore.
- Ensure expensive work is off the UI thread and survives configuration changes safely.

### Phase 3 — Android-specific capabilities
- Add screen capture only through explicit MediaProjection consent.
- Add Android package/APK inspection and device workflows where supported by existing tools.
- Provide clear companion/remote setup for workflows that require desktop binaries or a coding
  agent that cannot run inside Android's sandbox.

### Phase 4 — verification and release
- Test on a real arm64 Android device and emulator.
- Verify permissions, process cancellation, background/foreground transitions, large-file
  limits, memory pressure, offline behavior, data deletion and error recovery.
- Add CI for Rust cross-compilation, Gradle tests and APK artifacts.
- Document what is native, adapted, companion-backed or unavailable before calling the port
  feature-complete.

## Non-goals for the first APK

- Do not bundle a Linux desktop environment or pretend arbitrary desktop executables can run
  natively on Android.
- Do not expose unrestricted shell execution or bypass Android's sandbox.
- Do not send personal files to a remote service without a clear user action and explanation.
- Do not port every desktop-only feature before proving the Rust core can build and run on
  Android.

## First technical gate

The first implementation milestone is not a pretty mock screen. It is a reproducible arm64
Android build of the smallest useful Rust core, with an explicit list of blockers and a tested
Kotlin-to-Rust call. Only then should the full Compose UI be layered on top.
