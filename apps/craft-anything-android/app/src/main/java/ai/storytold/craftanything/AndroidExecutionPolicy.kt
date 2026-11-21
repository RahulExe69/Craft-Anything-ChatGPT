package ai.storytold.craftanything

import org.json.JSONObject

internal const val ANDROID_EXECUTION_GUIDANCE_MARKER = "[Craft Anything Android execution constraints]"

private val ANDROID_EXECUTION_GUIDANCE = """
[Craft Anything Android execution constraints]
The coding/build environment is this Android app's private on-device process. The APK bundles the Craft Anything Rust engine, but it does NOT bundle Node.js/npm, pnpm, Yarn, Bun, Python, Go, Cargo, Gradle, Flutter or an Android SDK.
Before running any language or package-manager command, verify that exact executable with command -v. Do not repeatedly try a command that is absent. Never report a dependency installation, framework build or native APK build as successful unless the command actually ran and returned success.
For browser websites on this device, prefer a dependency-free app with root index.html, CSS and browser-native JavaScript modules. Do not require a bundler, TypeScript compile step, package install or bare imports from node_modules. Do not add a package.json solely to create a fake build step. Use the Craft Anything local Preview to inspect the actual result.
If the request genuinely needs a package-based framework, server process, native SDK or missing language runtime, explain that the APK cannot execute it locally and leave the source intact. Do not invent a hosted worker, remote API or fake terminal.
"""

internal fun applyAndroidExecutionGuidance(paramsJson: String): String = runCatching {
    val params = JSONObject(paramsJson)
    val request = params.optString("request")
    if (!request.contains(ANDROID_EXECUTION_GUIDANCE_MARKER)) {
        params.put("request", request.trimEnd() + "\n\n" + ANDROID_EXECUTION_GUIDANCE)
    }
    params.toString()
}.getOrDefault(paramsJson)
