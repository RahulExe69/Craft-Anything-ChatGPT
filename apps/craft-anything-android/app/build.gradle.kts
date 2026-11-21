import org.gradle.api.tasks.Exec

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

val rustCoreDir = layout.projectDirectory.dir("../rust-core")
val rustJniLibs = layout.projectDirectory.dir("src/main/jniLibs")
val workspaceRoot = rootProject.projectDir.resolve("../..")

android {
    namespace = "ai.storytold.craftanything"
    compileSdk = 36

    defaultConfig {
        applicationId = "ai.storytold.craftanything"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    splits {
        abi {
            isEnable = true
            reset()
            include("arm64-v8a")
            isUniversalApk = false
        }
    }

    buildTypes {
        release {
            signingConfig = signingConfigs.getByName("debug")
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    packaging {
        jniLibs {
            useLegacyPackaging = false
        }
    }

    buildFeatures { compose = true }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2025.10.00")
    implementation(composeBom)
    androidTestImplementation(composeBom)
    implementation("androidx.activity:activity-compose:1.11.0")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.9.4")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.9.4")
    implementation("androidx.navigation:navigation-compose:2.9.5")
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
    testImplementation("junit:junit:4.13.2")
    // Android org.json methods are stubbed in local JVM tests; use the real implementation.
    testImplementation("org.json:json:20240303")
    androidTestImplementation("androidx.test.ext:junit:1.3.0")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.7.0")
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
}

val buildRustAndroid by tasks.registering(Exec::class) {
    group = "build"
    description = "Cross-compiles the Craft Anything Rust core for ARM64 Android phones."
    workingDir = rootProject.projectDir
    commandLine(
        "cargo", "ndk",
        "-t", "arm64-v8a",
        "-o", rustJniLibs.asFile.absolutePath,
        "build", "--release", "--locked",
        "--manifest-path", rustCoreDir.file("Cargo.toml").asFile.absolutePath
    )
    // Declare every source and toolchain input so native libraries are safe to cache across CI runs.
    inputs.property("androidNdkVersion", "28.2.13676358")
    inputs.property("rustToolchainVersion", "1.100.0")
    inputs.property("androidTarget", "aarch64-linux-android")
    inputs.dir(rustCoreDir)
    inputs.dir(workspaceRoot.resolve("crates"))
    inputs.dir(workspaceRoot.resolve("vendor/wgpu-hal"))
    inputs.files(
        workspaceRoot.resolve("Cargo.toml"),
        workspaceRoot.resolve("Cargo.lock"),
        workspaceRoot.resolve(".cargo/config.toml")
    )
    outputs.dir(rustJniLibs)
    outputs.cacheIf("Native libraries are reproducible from the declared Rust and NDK inputs") { true }
}

tasks.matching {
    it.name == "mergeDebugJniLibFolders" || it.name == "mergeReleaseJniLibFolders"
}.configureEach {
    dependsOn(buildRustAndroid)
}
