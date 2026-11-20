# Android builder parity plan

Date: 2026-11-21  
Branch: feature/android-native-app

## Product target

Make the Android app a credible mobile-native Craft Anything workspace. Parity means preserving the same user outcomes and engine contracts, not forcing desktop UI or desktop-only operating-system capabilities onto Android.

The source product's main loop is Study → Build → Check → Ready. Its workbench keeps project files, builder conversation, checklist, approvals and questions, command output, preview, checks and follow-up edits attached to one saved workspace.

## What was inspected

- Source desktop UI: crates/ui-egui home, make, project, prompt_box, chat and workbench.
- Shared rebuild engine, stack catalogue, toolchain detection, build tools and project activity log.
- Android Compose screens, JNI bridge, foreground build service, project file reader and preview screen.
- Latest CI-produced ARM64 APK, downloaded and inspected as a ZIP/APK. It contains the Rust bridge and Android native libraries; it does not bundle Node.js, npm, pnpm, Yarn or Bun.
- No Android emulator/ADB is available in this build environment, so visual device rendering cannot be certified here.
- v0's current sandbox documentation describes one project environment shared by files, editor, terminal/logs and preview, with Node and common package managers preinstalled. Its terminal operates in that same sandbox and uses permission modes.
- Replit's current troubleshooting guidance treats checkout, package installation, setup and app start as separate failure stages, and recommends preserving the first useful error.

## Gap audit

| Capability | Source product / modern builder expectation | Android before this slice | Required direction |
|---|---|---|---|
| Create | Idea-first prompt, references, builder choice, target/stack | Prompt form and starter cards | Keep mobile entry simple; enter a real workspace after creation |
| Workspace | Chat, files, editor, preview, console/logs | Chat / Preview / Files / Activity; files read-only | Add editable source and a dedicated package/build console |
| Dependencies | Detect manifests/lockfiles, choose the existing manager, install in the same project environment | No dependency pane; no Node/npm-family runtime bundled in APK | Detect manifests/lockfiles; hand install to builder with correct command; expose missing runtime honestly |
| Build activity | Checklist, commands, output, failure cause and approvals | Status and activity polling | Preserve output in a console; retain approval gates |
| Preview | Live application from same filesystem, reload and viewport sizing | Local static website preview | Add viewport controls and clearer unsupported-target messaging |
| Source changes | Editor, diff/history | Read-only text viewer | Add bounded, path-safe editing; then diff/restore |
| Ship | Export/share/deploy | No project export/share | Add SAF export and target-specific publish flows |
| Runtime | Isolated per-project environment with supported toolchains | Rust engine runs on Android; JS/Python/Java toolchains not bundled | Choose a real Android runtime or explicitly configured worker; never fake terminal success |
| Coding agents | Local/remote agents operate on project safely | Android API builder; CLI agents rejected by bridge | Keep API-builder contract; evaluate agent adapters separately |

## Reference workflow distilled

1. Create or open a project and keep a stable workspace identity.
2. Detect application root, framework manifest, lockfile and declared scripts.
3. Verify the runtime and package manager exist before attempting installation.
4. Install with the repository's manager and lockfile (npm ci, pnpm install --frozen-lockfile, etc.); do not silently switch managers or delete lockfiles.
5. Start preview against the same files.
6. Show logs and the first useful failure, not only a generic failed badge.
7. Edit source in place, save, rerun relevant checks and keep evidence/status attached to the project.
8. Export/share source and artifacts with platform limitations made clear.

## This implementation slice

- Make text source files editable in the project workspace, with a 256 KB cap, path traversal rejection, symlink rejection and atomic save.
- Add a Console tab that detects common ecosystem manifests and lockfiles, recommends a manager command, and asks the existing AI builder to perform installation/checks.
- Show the source product's Study → Build → Check → Ready lifecycle in the native project workspace, with explicit Needs you / Paused states.
- Show recent build activity/output in a monospaced console.
- Explicitly explain that the current APK does not bundle Node.js or desktop package managers; the builder must check its actual execution environment and report missing commands rather than claim success.
- Add parser and safe-write tests.

## Follow-on milestones (ordered)

### P0 — Android workspace integrity
- Verify actual generated projects on a physical ARM64 device: create, follow-up, stop/recover, source save/reopen, preview and SAF export.
- Add preview viewport profiles and robust preview lifecycle management.
- Add project ZIP export through Android Storage Access Framework.
- Add file diff/restore with snapshots before AI edits.

### P1 — Real build/runtime capability
- Inventory which shared toolchains can execute on Android (process availability, ABI, Bionic compatibility, storage, network).
- Prototype an isolated Node.js runtime for ARM64 Android; evaluate binary size, npm TLS/registry support, lifecycle scripts, native add-ons, memory and app sandbox permissions. Do not mark Node projects supported until this passes.
- Evaluate Python, Go, Rust and Gradle runtimes individually; a toolchain manifest is not proof a binary is installed.
- For APK creation, evaluate an on-device Android SDK/Gradle toolchain versus a user-configured build worker. Android SDK/NDK and signing are a separate storage/security problem from npm.

### P2 — Product parity
- Recreate the source Study / Build / Check / Ready stepper and compact live checklist.
- Add a true code editor with syntax highlighting, multiple open files and diffs.
- Add terminal sessions tied to the same project filesystem, including streaming output, cancellation and per-command consent.
- Add preview browser/device profiles and visual/error checks for generated output.
- Add snapshots, rollback, import/export, Git branch/commit/PR workflows and publishing where supported.
- Add parity tests and physical-device acceptance runs; label features Native, Adapted, Worker-backed or Unavailable.

## Definition of done

A feature is not done because a control is drawn. An Android-device test must prove the real command or file action occurred, the user sees the actual result/error, permissions are correct, and the project can be reopened without losing state.
