package ai.storytold.craftanything

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ProjectProgressTest {
    @Test
    fun mapsSharedEngineStatesToTheFourCraftStages() {
        assertEquals(0, projectPhaseIndex("new"))
        assertEquals(0, projectPhaseIndex("inspecting"))
        assertEquals(1, projectPhaseIndex("building"))
        assertEquals(2, projectPhaseIndex("checking"))
        assertEquals(3, projectPhaseIndex("ready"))
    }

    @Test
    fun needsYouKeepsTheLastCheckStageVisibleInsteadOfGreyingTheWholeStepper() {
        assertEquals(2, projectPhaseIndex("needs_you", hasCheck = true))
        assertEquals(1, projectPhaseIndex("needs_you", hasCheck = false))
        assertEquals(1, projectPhaseIndex("stopped"))
    }

    @Test
    fun trulyUnknownStatesDoNotPretendToBeACompletedStage() {
        assertNull(projectPhaseIndex("unknown"))
    }

    @Test
    fun waitingAndPausedStatesHaveUsefulMessages() {
        assertEquals(
            "This project needs your answer or approval before it can continue.",
            projectPhaseMessage("needs_you")
        )
        assertEquals(
            "This build is paused. You can continue it from the project conversation.",
            projectPhaseMessage("stopped")
        )
    }
}
