# Craft Anything for Android

This directory contains the native Android app and its Rust JNI bridge.

## Prerequisites

- JDK 17
- Android SDK Platform 36 and Android Build Tools
- Android NDK installed through Android Studio
- Rust toolchain matching the workspace (1.97 or newer)
- Android Rust targets: `aarch64-linux-android` and `x86_64-linux-android`
- `cargo-ndk` installed and available on PATH

Install the Android Rust targets and helper:

```sh
rustup target add aarch64-linux-android x86_64-linux-android
cargo install cargo-ndk
```

Build a debug APK:

```sh
cd apps/craft-anything-android
gradle :app:assembleDebug
```

The Gradle pre-build task invokes cargo-ndk and places the resulting shared libraries under
`app/src/main/jniLibs`.

## Current scope

The first milestone proves the native app shell and JNI tool-registry connection. It is not yet a
feature-complete Android port. The next slice will add Android file selection, target opening,
tool invocation, evidence display, mobile-safe settings and the Android platform adapters.
