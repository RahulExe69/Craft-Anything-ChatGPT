package ai.storytold.craftanything

/** Native entry points used by BuildExecutionService when no Activity is alive. */
internal class NativeBuildBridge {
    external fun nativeInitialize(appDataDir: String): String
    external fun nativeCallTool(toolId: String, paramsJson: String, consentGranted: Boolean): String

    companion object {
        init { System.loadLibrary("craft_anything_android") }
    }
}
