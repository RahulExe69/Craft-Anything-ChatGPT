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

The Gradle pre-build task invokes cargo-ndk for arm64-v8a and x86_64 and places the resulting native libraries under `app/src/main/jniLibs`.

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

- Native Compose app shell with Home, Projects, Tools and Settings navigation.
- Android Storage Access Framework file selection, bounded to 512 MiB and copied into app-private storage before the Rust engine receives a path.
- Rust JNI bridge for engine version, the real registered tool catalogue and tool dispatch.
- Searchable, toolset-grouped tool browser with engine-provided input schemas and a JSON parameter editor.
- One-time confirmation for tools whose Rust effect flags require consent (foreign-code execution or connected-device mutation); approval is forwarded only for that invocation.
- Structured tool results and error display.

## Platform boundaries

This is an incremental Android port, not a claim of full desktop parity. Android sandbox and lifecycle constraints mean some desktop process, external-engine, capture and filesystem workflows will need Android-specific adapters or a companion desktop connection. Unsupported operations must remain unavailable or explain why; they must not pretend to succeed.

## Build verification

The GitHub Actions workflow runs Rust bridge tests, Kotlin JVM tests, Compose instrumentation tests on an Android emulator, and produces a debug APK artifact when the complete pipeline succeeds. Check the pull request's latest workflow run before installing an APK.
