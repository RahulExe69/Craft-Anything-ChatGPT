package ai.storytold.craftanything

import androidx.compose.ui.test.assertDoesNotExist
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import java.util.concurrent.atomic.AtomicReference
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import androidx.test.ext.junit.runners.AndroidJUnit4

@RunWith(AndroidJUnit4::class)
class ToolsScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val catalogue = """
        [
          {
            "id": "core_session_show",
            "title": "Show session",
            "summary": "View the active session status",
            "toolset": "core",
            "toolset_title": "Essentials",
            "effects": 512,
            "requires_consent": false,
            "input_schema": {"type": "object", "properties": {}}
          },
          {
            "id": "runtime_capture",
            "title": "Capture process",
            "summary": "Execute code from the selected target",
            "toolset": "runtime",
            "toolset_title": "Running programs",
            "effects": 128,
            "requires_consent": true,
            "input_schema": {"type": "object", "properties": {}}
          }
        ]
    """.trimIndent()

    @Test
    fun rendersRegisteredToolsFromTheEngineCatalogue() {
        composeRule.setContent {
            ToolsScreen(catalogue, "Rust core v0.6.1", "", { _, _ -> })
        }

        composeRule.onNodeWithText("Show session").assertIsDisplayed()
        composeRule.onNodeWithText("Capture process").assertIsDisplayed()
    }

    @Test
    fun searchFiltersByToolNameAndHidesNonMatches() {
        composeRule.setContent {
            ToolsScreen(catalogue, "Rust core v0.6.1", "", { _, _ -> })
        }

        composeRule.onNodeWithText("Search tools").performTextInput("Capture")
        composeRule.onNodeWithText("Capture process").assertIsDisplayed()
        composeRule.onNodeWithText("Show session").assertDoesNotExist()
    }

    @Test
    fun consentGatedToolCannotRunUntilTheUserApprovesIt() {
        val invokedTool = AtomicReference<String?>(null)
        composeRule.setContent {
            ToolsScreen(
                catalogue,
                "Rust core v0.6.1",
                "",
                { toolId, _ -> invokedTool.set(toolId) }
            )
        }

        composeRule.onNodeWithText("Capture process").performClick()
        composeRule.onNodeWithText("Review and run").performClick()
        composeRule.onNodeWithText("Approval required").assertIsDisplayed()
        assertEquals(null, invokedTool.get())

        composeRule.onNodeWithText("Approve once and run").performClick()
        composeRule.runOnIdle {
            assertEquals("runtime_capture", invokedTool.get())
        }
    }

    @Test
    fun safeToolCanBeInvokedWithoutAConsentDialog() {
        val invokedTool = AtomicReference<String?>(null)
        composeRule.setContent {
            ToolsScreen(
                catalogue,
                "Rust core v0.6.1",
                "",
                { toolId, _ -> invokedTool.set(toolId) }
            )
        }

        composeRule.onNodeWithText("Show session").performClick()
        composeRule.onNodeWithText("Run tool").performClick()
        composeRule.runOnIdle {
            assertEquals("core_session_show", invokedTool.get())
        }
        composeRule.onNodeWithText("Approval required").assertDoesNotExist()
    }
}
