# Craft Anything for Android

The Android edition is a native Kotlin + Jetpack Compose app backed by the shared Rust engine and AI runtime through JNI.

## Prerequisites

- JDK 17
- Android SDK Platform 36 and Android Build Tools 36.0.0
- Android NDK 28.2.13676358
- Rust toolchain matching the workspace
- Rust target `aarch64-linux-android`
- `cargo-ndk` installed and available on PATH
- Gradle 8.13 (a Gradle wrapper has not been committed yet)

Install the Rust targets and helper:

```sh
rustup target add aarch64-linux-android
cargo install cargo-ndk --locked
```

## Build

From this directory:

```sh
gradle --build-cache --parallel :app:assembleDebug
gradle --build-cache --parallel :app:assembleRelease
```

The phone-first build produces an `arm64-v8a` APK for ARM64 Android phones. The `x86_64` target is intentionally deferred with emulator testing; it can be added back when emulator support becomes a priority.

The release-preview variant enables R8 minification, resource shrinking and Rust symbol stripping. It is signed with the Android debug key so it can be installed directly for testing; it is **not a production-signed release**. A production release still needs a dedicated release keystore and signing configuration.

The Gradle native-library packaging task invokes cargo-ndk for `arm64-v8a` and places the resulting shared library under `app/src/main/jniLibs`. Its declared inputs and outputs are Gradle-build-cacheable, so unchanged Rust native libraries can be restored without recompilation on warm CI runs. JVM-only tests do not need to cross-compile the Rust engine.

## Tests

JVM tests:

```sh
gradle --build-cache --parallel :app:testDebugUnitTest
```

Compose UI tests require a booted API 35 x86_64 Android emulator:

```sh
gradle --build-cache --parallel :app:connectedDebugAndroidTest
```

## Current implementation

- Native Compose experience with Home, Build, Projects, Evidence, Tools, AI conversation and Settings.
- App-private Android session: the engine's config directory, projects and engine store live under `filesDir`; Android app backup is disabled.
- The Android engine is created with `Engine::shared(..., craft_anything_ai::tools())`, so its real catalogue includes the AI rebuild tools as well as the core engine tools.
- Guided Build screen invokes the real `rebuild_start` tool, selects a configured Builder backend, accepts a website URL or an imported local reference, and asks for consent before starting the rebuild.
- Projects screen reads actual project metadata and pending inbox items from the engine. It can open project overviews and answer inbox questions or allow/deny a pending project action.
- Evidence screen reads the active session's evidence bundle and unknowns list from the engine.
- Android Storage Access Framework import is bounded to 512 MiB. A dedicated adapter copies the selected file into app-private storage, preserves a sanitized extension, opens the target in the engine, and returns the engine-owned reference path to the builder.
- AI settings support OpenAI, Anthropic, OpenRouter, Google Gemini and OpenAI-compatible endpoints. The first saved provider is assigned to Lead and Builder automatically; roles can be changed per provider.
- AI conversations use the shared Rust AI runtime and stream events to the Compose chat screen. Provider keys are never returned through JNI. Android stores the key file in private app storage; desktop platforms continue using their system keychain where available.
- Searchable, toolset-grouped tool browser with engine-provided schemas, JSON parameter editor, structured results and one-invocation consent.
- Generated adaptive launcher icon, Android 13+ monochrome themed icon, and dark launch theme.

## Android-specific boundaries still open

This is an incremental port, not a claim of full desktop parity. Desktop command-line agents cannot run inside the Android sandbox and are not offered as Android providers. Project source files remain in app-private storage; user-facing export/share is still open. Long-running rebuilds do not yet have a dedicated foreground service and durable resume flow, so keep the app open while a build is running. The device does not bundle Node, desktop browsers, compilers or every package manager; some build and validation commands will need an Android-native or remote-build adapter.

The app allows cleartext traffic so a user can point an OpenAI-compatible provider at a local HTTP server. HTTP requests are unencrypted; use only a trusted local network and never send public provider credentials to an untrusted endpoint.

## Build verification

GitHub Actions produces the ARM64 debug APK and runs Android JVM unit tests in parallel. Repository CI runs the Rust workspace checks. Optimized release-preview APKs are available through a manual workflow dispatch; emulator instrumentation testing is deferred until the phone-first APK is stable.
