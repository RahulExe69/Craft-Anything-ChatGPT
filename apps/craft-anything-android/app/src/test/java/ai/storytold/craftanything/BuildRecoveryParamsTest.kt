package ai.storytold.craftanything

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BuildRecoveryParamsTest {
    @Test
    fun recoveryResumesTheSameProjectAndPreservesBuilderChoice() {
        val recovered = JSONObject(BuildRecoveryParams.forResume(
            """{"request":"Build a dashboard","project_folder":"/private/projects/build-1","target":"-","backend":"Gemini","rounds":3,"pictures":["/private/ref.png"]}""",
            "/private/projects/build-1"
        ))
        assertEquals("/private/projects/build-1", recovered.getString("project"))
        assertEquals("Build a dashboard", recovered.getString("request"))
        assertTrue(recovered.getBoolean("resume"))
        assertEquals("Gemini", recovered.getString("backend"))
        assertEquals(3, recovered.getInt("rounds"))
        assertFalse(recovered.has("project_folder"))
        assertFalse(recovered.has("target"))
        assertFalse(recovered.has("pictures"))
    }

    @Test
    fun recoveryDoesNotInventOptionalBuilderSettings() {
        val recovered = JSONObject(BuildRecoveryParams.forResume(
            """{"request":"Build a game","project":"/private/projects/game","resume":false}""",
            "/private/projects/game"
        ))
        assertEquals("/private/projects/game", recovered.getString("project"))
        assertTrue(recovered.getBoolean("resume"))
        assertFalse(recovered.has("backend"))
        assertFalse(recovered.has("rounds"))
    }
}
