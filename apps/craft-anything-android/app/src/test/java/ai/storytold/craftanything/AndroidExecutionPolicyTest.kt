package ai.storytold.craftanything

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AndroidExecutionPolicyTest {
    @Test
    fun addsTheAndroidRuntimeLimitsToEveryBuilderRequest() {
        val result = JSONObject(applyAndroidExecutionGuidance("""{"request":"Build a website","rounds":2}"""))
        val request = result.getString("request")
        assertTrue(request.startsWith("Build a website"))
        assertTrue(request.contains("does NOT bundle Node.js/npm"))
        assertTrue(request.contains("Do not repeatedly try a command that is absent"))
        assertEquals(2, result.getInt("rounds"))
    }

    @Test
    fun doesNotDuplicateAndroidRuntimeGuidance() {
        val once = applyAndroidExecutionGuidance("""{"request":"Build a website"}""")
        val twice = applyAndroidExecutionGuidance(once)
        assertEquals(1, Regex(ANDROID_EXECUTION_GUIDANCE_MARKER).findAll(twice).count())
    }
}
