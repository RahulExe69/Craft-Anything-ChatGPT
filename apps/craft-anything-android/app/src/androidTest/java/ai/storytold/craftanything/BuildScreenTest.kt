package ai.storytold.craftanything

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import androidx.test.ext.junit.runners.AndroidJUnit4

@RunWith(AndroidJUnit4::class)
class BuildScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val backend = NativeAiBackend(
        name = "OpenAI",
        kind = "openai",
        model = "gpt-test",
        baseUrl = "",
        enabled = true,
        hasKey = true
    )

    private fun setBuildScreen() {
        composeRule.setContent {
            BuildScreen(
                backends = listOf(backend),
                selectedBackend = "OpenAI",
                onBackendSelected = {},
                starting = false,
                feedback = "",
                importedTargetPath = "",
                onChooseTarget = {},
                onStartBuild = {},
                onOpenProjects = {}
            )
        }
    }

    @Test
    fun selectingAStarterAddsAnEditableMobileAppPrompt() {
        setBuildScreen()

        composeRule.onNodeWithText("Mobile app").performClick()
        composeRule.onNodeWithText(
            "Build a complete mobile app with a cohesive visual system, intuitive navigation, and polished core user flows."
        ).assertIsDisplayed()
    }

    @Test
    fun websiteReferenceFieldCanBeShownOnDemand() {
        setBuildScreen()

        composeRule.onNodeWithText("Add website").performClick()
        composeRule.onNodeWithText("Website to use as a reference").assertIsDisplayed()
    }
}
