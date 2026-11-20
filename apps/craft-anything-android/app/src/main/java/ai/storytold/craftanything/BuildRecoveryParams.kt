package ai.storytold.craftanything

import org.json.JSONObject

/** Builds the minimal request used to resume a saved project after process death. */
internal object BuildRecoveryParams {
    fun forResume(originalJson: String, projectFolder: String): String {
        val original = JSONObject(originalJson)
        val resumed = JSONObject()
            .put("request", original.optString("request"))
            .put("project", projectFolder)
            .put("resume", true)
        original.optString("backend").takeIf { it.isNotBlank() }?.let { resumed.put("backend", it) }
        original.opt("rounds")?.takeUnless { it == JSONObject.NULL }?.let { resumed.put("rounds", it) }
        return resumed.toString()
    }
}
