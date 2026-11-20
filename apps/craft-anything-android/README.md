# Craft Anything for Android

The Android edition is a native Kotlin + Jetpack Compose app backed by the existing Rust engine through a small JNI library.

## Prerequisites

- JDK 17
- Android SDK Platform 36 and Android Build Tools 36.0.0
- Android NDK 28.2.13676358
- Rust toolchain matching the workspace
- Rust targets `aarch64-linux-android` and `x86_64-linux-android`
- `cargo-ndk` installed and available on PATH
- Gradle 8.13 (a Gradle wrapper is planned; it has not been committed yet)

Install the Rust targets and helper:

```sh
rustup target add aarch64-linux-android x86_64-linux-android
cargo install cargo-ndk --locked
```

## Build

From this directory:

```sh
gradle :app:assembleDebug
```

The Gradle native-library packaging tasks invoke cargo-ndk for arm64-v8a and x86_64 and place the resulting shared libraries under `app/src/main/jniLibs`. JVM-only tests do not need to cross-compile the Rust engine.

## Tests

JVM tests for bounded SAF import:

```sh
gradle :app:testDebugUnitTest
```

Compose UI tests require a booted API 35 x86_64 Android emulator:

```sh
gradle :app:connectedDebugAndroidTest
```

## Current implementation

- Native Compose app shell with Home, Projects, Evidence, Tools and Settings navigation.
- Android app initialisation configures the Rust engine's default project location under app-private `filesDir/projects`, so project metadata stays inside the app sandbox.
- Projects screen lists projects from the real engine registry, shows status and pending questions, opens a project overview, and lets the user answer inbox questions or allow/deny a pending project action through `project_inbox_answer`.
- Evidence screen reads the active session's compact evidence bundle and unknowns list from the engine, showing each record's confidence, coverage, limitations and each recorded question's severity/status.
- Android Storage Access Framework file selection, bounded to 512 MiB and copied into app-private storage by a dedicated Android import adapter before the Rust engine receives a path; a sanitized original file extension is preserved for format detection. The adapter removes redundant copies when the engine reuses an already-open target.
- Rust JNI bridge for engine version, the real registered tool catalogue and tool dispatch.
- Searchable, toolset-grouped tool browser with engine-provided input schemas and a JSON parameter editor.
- One-time confirmation for tools whose Rust effect flags require consent (foreign-code execution or connected-device mutation); approval is forwarded only for that invocation.
- Structured tool results and error display.

## Platform boundaries

This is an incremental Android port, not a claim of full desktop parity. Android sandbox and lifecycle constraints mean some desktop process, external-engine, capture and filesystem workflows will need Android-specific adapters or a companion desktop connection. Unsupported operations must remain unavailable or explain why; they must not pretend to succeed.

## Build verification

The GitHub Actions workflow runs Rust bridge tests, Kotlin JVM tests, Compose instrumentation tests on an Android emulator, and produces a debug APK artifact when the complete pipeline succeeds. Check the pull request's latest workflow run before installing an APK.
