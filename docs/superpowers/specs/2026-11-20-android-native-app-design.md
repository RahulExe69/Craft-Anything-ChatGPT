# Native Android application design

**Date:** 2026-11-20  
**Status:** Approved direction — implementation in progress

## Intent

Create a real Android edition of Craft Anything for people who want to inspect software and work with its evidence from a phone. The app should feel native to Android rather than squeezing the desktop workbench into a small screen. It should reuse the existing Rust engine wherever Android supports the required capabilities.

## Goals

- Ship a Kotlin + Jetpack Compose Android application with a phone-first navigation and interaction model.
- Keep the existing Rust engine's tool registry, consent rules, and evidence model as the source of truth.
- Open user-selected files through Android's Storage Access Framework and copy them into bounded app-private storage before passing a filesystem path to Rust.
- Expose typed, narrow native bridge operations for tool discovery, tool invocation, progress, and cancellation.
- Build reproducible arm64-v8a debug APKs in CI; keep x86_64 emulator support for development.
- Classify every desktop capability as native, adapted, companion/remote, or unavailable on Android, and explain limitations honestly in the UI.

## Non-goals

- Wrapping the limited browser viewer in a WebView and claiming it is the full product.
- Promising 1:1 desktop feature parity where Android's sandbox and background limits make that impossible.
- Shipping an unrestricted shell or bypassing Android's app sandbox.
- Sending user files to a hosted service by default.
- Bundling a desktop Linux environment or assuming desktop executables can run on Android.

## Existing system and constraints

- The desktop app is a Rust/egui application. The reusable engine lives in `crates/engine`; the AI layer is in `crates/ai`; platform process, filesystem and capture services are in `crates/platform`.
- The existing browser app is deliberately limited: it depends only on wasm-safe evidence, formats and analysis crates, not the engine, AI, platform services or desktop UI.
- The engine is headless-first: UI clients call registered tools through `Engine::call`; the UI must not duplicate orchestration or persistence.
- The repository forbids unsafe Rust in first-party code and requires explicit consent for tools that execute foreign code or mutate devices.
- Android uses scoped storage, a restricted process environment, foreground/background execution limits, and runtime permissions. A SAF-selected `content://` URI is not a normal filesystem path, so the app must copy selected bytes into its own private storage before calling `core_target_open`.
- External programs and heavyweight desktop tools may need Android-specific adapters or a companion machine. Unsupported features must return honest availability/errors, not fake success.

## Approaches considered

1. **Kotlin + Jetpack Compose UI with a narrow Rust bridge — chosen.** Best Android UX, native permission handling and lifecycle integration while preserving the existing engine as the source of truth. It requires a bridge and Android platform adapters, but those seams are explicit and testable.
2. **Port the existing egui/eframe desktop UI to Android.** Could reuse more UI code, but the current UI depends on native file dialogs, clipboard, desktop window behavior and desktop assumptions. It would still need significant Android-specific work and would constrain mobile UX.
3. **Wrap the browser viewer in a WebView.** Fastest demo, but it omits the engine, AI, platform layer and full tool registry. Rejected because it does not meet the requested feature set.

## Architecture decisions

- UI: Kotlin, Jetpack Compose, Material 3, lifecycle-aware state and Android Navigation.
- Rust bridge: JNI for the initial vertical slice, kept narrow and isolated in `apps/craft-anything-android/rust-core`. If Android compatibility or ABI maintenance makes JNI too costly, evaluate UniFFI before expanding the API.
- Core initialization: one process-scoped engine instance, initialized with app-private storage paths rather than desktop home-directory assumptions.
- Files: SAF picker → bounded stream copy into app-private storage → Rust engine `core_target_open`.
- Build: Gradle Android app + Android NDK Rust cross-compilation, initially arm64-v8a and x86_64.
- Security: never expose arbitrary process execution as a UI convenience; preserve engine consent gates and keep secrets out of project files/logs.

## Success criteria

The first meaningful milestone is a reproducible Android build that loads the Rust shared library, returns the real Rust tool catalogue, opens a SAF-selected file through `core_target_open`, and displays the resulting target metadata. Later milestones add projects/evidence, AI workflows, Android capture, credential storage, companion support and the full feature-parity matrix.
