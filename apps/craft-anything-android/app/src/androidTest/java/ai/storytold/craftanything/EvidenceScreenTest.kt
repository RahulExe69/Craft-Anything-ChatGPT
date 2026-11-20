package ai.storytold.craftanything

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import androidx.test.ext.junit.runners.AndroidJUnit4

@RunWith(AndroidJUnit4::class)
class EvidenceScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val evidenceResponse = """
        {
          "ok": true,
          "output": {
            "result": {
              "total": 1,
              "records": [
                {
                  "evidence_id": "ev_abc123",
                  "operation": "native_function_pseudocode",
                  "summary": "The function validates the input before writing the result.",
                  "confidence": "observed",
                  "coverage": {"status": "partial"},
                  "limitations": [{"code": "stripped", "message": "Symbols are missing"}]
                }
              ]
            }
          }
        }
    """.trimIndent()

    private val unknownsResponse = """
        {
          "ok": true,
          "output": {
            "result": {
              "total": 1,
              "items": [
                {
                  "id": "unk_1234",
                  "question": "Does this app store credentials locally?",
                  "domain": "storage",
                  "severity": "high",
                  "status": "investigating"
                }
              ]
            }
          }
        }
    """.trimIndent()

    @Test
    fun parsesEngineEvidenceAndUnknowns() {
        val evidence = parseEvidence(evidenceResponse)
        val unknowns = parseUnknowns(unknownsResponse)

        assertEquals("native_function_pseudocode", evidence.single().operation)
        assertEquals("partial", evidence.single().coverage)
        assertEquals(1, evidence.single().limitations)
        assertEquals("Does this app store credentials locally?", unknowns.single().question)
        assertEquals("high", unknowns.single().severity)
    }

    @Test
    fun rendersEvidenceAndOutstandingQuestions() {
        composeRule.setContent {
            EvidenceScreen(
                evidenceJson = evidenceResponse,
                unknownsJson = unknownsResponse,
                loading = false,
                error = "",
                onRefresh = {}
            )
        }

        composeRule.onNodeWithText("Evidence & questions").assertIsDisplayed()
        composeRule.onNodeWithText("native_function_pseudocode").assertIsDisplayed()
        composeRule.onNodeWithText("The function validates the input before writing the result.").assertIsDisplayed()
        composeRule.onNodeWithText("Does this app store credentials locally?").assertIsDisplayed()
        composeRule.onNodeWithText("HIGH").assertIsDisplayed()
    }

    @Test
    fun showsHelpfulEmptyStatesWhenNoEvidenceHasBeenRecorded() {
        composeRule.setContent {
            EvidenceScreen(
                evidenceJson = """{"ok":true,"output":{"result":{"records":[]}}}""",
                unknownsJson = """{"ok":true,"output":{"result":{"items":[]}}}""",
                loading = false,
                error = "",
                onRefresh = {}
            )
        }

        composeRule.onNodeWithText("No evidence recorded yet").assertIsDisplayed()
        composeRule.onNodeWithText("No open questions").assertIsDisplayed()
    }
}
