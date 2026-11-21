package ai.storytold.craftanything

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProjectRuntimeRecoveryTest {
    @Test
    fun identifiesTheSpecificAndroidNodeRuntimeFailure() {
        val missing = listOf(
            ProjectConversationEntry(
                "Needs attention",
                "Couldn't run the build: npm isn't installed or isn't on your PATH.",
                error = true
            )
        )
        assertTrue(isMissingAndroidNodeRuntime(missing))
    }

    @Test
    fun doesNotOfferStaticFallbackForUnrelatedBuildFailures() {
        val failed = listOf(ProjectConversationEntry("Needs attention", "TypeScript type error in App.tsx", error = true))
        val ordinary = listOf(ProjectConversationEntry("Craft Anything", "I'll update the layout."))
        assertFalse(isMissingAndroidNodeRuntime(failed))
        assertFalse(isMissingAndroidNodeRuntime(ordinary))
    }
}
