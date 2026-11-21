package ai.storytold.craftanything

internal data class DependencySetup(
    val ecosystem: String,
    val manifest: String,
    val manager: String,
    val command: String,
    val lockfile: String?,
    val caution: String?
)

internal fun detectDependencySetups(filesJson: String): List<DependencySetup> {
    val paths = parseProjectFiles(filesJson).map { it.path.replace('\\', '/') }.toSet()
    fun has(name: String) = name in paths
    val setups = mutableListOf<DependencySetup>()

    if (has("package.json")) {
        val locks = listOf("pnpm-lock.yaml", "yarn.lock", "bun.lock", "bun.lockb", "package-lock.json").filter(::has)
        val lock = locks.firstOrNull()
        val manager = when (lock) {
            "pnpm-lock.yaml" -> "pnpm"
            "yarn.lock" -> "Yarn"
            "bun.lock", "bun.lockb" -> "Bun"
            "package-lock.json", null -> "npm"
            else -> "npm"
        }
        val command = when (manager) {
            "pnpm" -> "pnpm install --frozen-lockfile"
            "Yarn" -> "yarn install"
            "Bun" -> "bun install --frozen-lockfile"
            else -> if (lock == "package-lock.json") "npm ci" else "npm install"
        }
        val caution = if (locks.size > 1) {
            "Multiple JavaScript lockfiles are present (" + locks.joinToString() + "). Ask the builder to confirm the intended package manager before installing."
        } else null
        setups += DependencySetup("JavaScript / Node.js", "package.json", manager, command, lock, caution)
    }

    if (has("pyproject.toml") || has("requirements.txt")) {
        val lock = listOf("uv.lock", "poetry.lock", "Pipfile.lock").firstOrNull(::has)
        val manager = when (lock) {
            "uv.lock" -> "uv"
            "poetry.lock" -> "Poetry"
            "Pipfile.lock" -> "Pipenv"
            else -> "pip"
        }
        val command = when (manager) {
            "uv" -> "uv sync --frozen"
            "Poetry" -> "poetry install"
            "Pipenv" -> "pipenv sync"
            else -> if (has("requirements.txt")) "python -m pip install -r requirements.txt" else "python -m pip install -e ."
        }
        setups += DependencySetup("Python", if (has("pyproject.toml")) "pyproject.toml" else "requirements.txt", manager, command, lock, null)
    }

    if (has("Cargo.toml")) {
        val locked = has("Cargo.lock")
        setups += DependencySetup("Rust", "Cargo.toml", "Cargo", if (locked) "cargo build --locked" else "cargo build", if (locked) "Cargo.lock" else null, null)
    }
    if (has("go.mod")) {
        setups += DependencySetup("Go", "go.mod", "Go modules", "go mod download", null, null)
    }
    if (has("pubspec.yaml")) {
        setups += DependencySetup("Flutter / Dart", "pubspec.yaml", "Pub", "flutter pub get", null, null)
    }
    if (has("composer.json")) {
        setups += DependencySetup("PHP", "composer.json", "Composer", "composer install", if (has("composer.lock")) "composer.lock" else null, null)
    }
    if (has("Gemfile")) {
        setups += DependencySetup("Ruby", "Gemfile", "Bundler", "bundle install", if (has("Gemfile.lock")) "Gemfile.lock" else null, null)
    }
    if (has("build.gradle") || has("build.gradle.kts") || has("gradlew")) {
        setups += DependencySetup("Gradle / JVM", if (has("build.gradle.kts")) "build.gradle.kts" else "build.gradle", "Gradle", if (has("gradlew")) "./gradlew build" else "gradle build", if (has("gradle.lockfile")) "gradle.lockfile" else null, null)
    }

    return setups.distinctBy { it.ecosystem }
}


internal fun androidStaticWebsitePrompt(): String = """
Convert this existing web project into a dependency-free website that can run in Craft Anything's on-device Android preview.

Requirements:
- Preserve the current product, content, visual identity, interactions and important user flows. Do not replace it with a generic demo.
- Make it browser-native: a root app/index.html, CSS files and browser JavaScript modules that modern Android WebView supports.
- Do not require Node.js, npm, pnpm, Yarn, Bun, Vite, Next.js, webpack, TypeScript compilation, package installation or any build step.
- Replace framework-only components with equivalent browser-native HTML, CSS and JavaScript where necessary. Keep responsive behaviour and accessible controls.
- Do not use bare package imports or assume a package from node_modules exists. Use native browser APIs and local files. Do not add external services or deploy a backend.
- If a requested feature fundamentally needs a server, database, authentication service or native module, do not fake it: keep the UI honest and explain the integration boundary.
- Keep existing assets and useful source material. Remove package manifests and lockfiles only after all required website behaviour has been migrated and no package-based code is still used.
- Do not run package-manager commands or claim a package build passed. Use the available project preview to verify the real index.html and report any limitation precisely.
""".trim()

internal fun dependencyInstallPrompt(setup: DependencySetup): String = buildString {
    appendLine("Prepare and install dependencies for this project on its actual execution target.")
    appendLine("Ecosystem: " + setup.ecosystem + ". Manifest: " + setup.manifest + ". Detected manager: " + setup.manager + ".")
    appendLine("Recommended command: " + setup.command + ".")
    setup.lockfile?.let { appendLine("Existing lockfile: " + it + ". Preserve its resolved versions.") }
    setup.caution?.let { appendLine("Important: " + it + " Do not guess or delete lockfiles; explain the conflict and ask before changing them.") }
    appendLine("First check whether the required runtime and package manager are actually installed and compatible. Use the existing build command tool for installation and follow its permission/approval gates. Do not claim success if the executable is missing or the command fails.")
    appendLine("Prefer the existing lockfile and manager. Do not silently switch package managers, rewrite lockfiles, or run arbitrary cleanup commands. After installation, run the project's relevant build or test script, then report the exact commands, exit status, useful output and anything still blocked.")
}
