package ai.storytold.craftanything

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import java.util.concurrent.atomic.AtomicReference
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import androidx.test.ext.junit.runners.AndroidJUnit4

@RunWith(AndroidJUnit4::class)
class ProjectFilesScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val files = """
        {
          "ok": true,
          "output": {
            "result": {
              "truncated": false,
              "files": [
                {"path":"src/App.kt","name":"App.kt","size":128,"kind":"text"},
                {"path":"index.html","name":"index.html","size":64,"kind":"text"}
              ]
            }
          }
        }
    """.trimIndent()

    @Test
    fun listsProjectFilesAndOpensTheSelectedSourceFile() {
        val opened = AtomicReference<String?>(null)
        composeRule.setContent {
            ProjectFilesPanel(
                filesJson = files,
                selectedFileJson = "",
                loading = false,
                error = "",
                onRefresh = {},
                onOpenFile = { opened.set(it) },
                onCloseFile = {}
            )
        }

        composeRule.onNodeWithText("Project files").assertIsDisplayed()
        composeRule.onNodeWithText("App.kt").performClick()
        composeRule.runOnIdle { assertEquals("src/App.kt", opened.get()) }
    }

    @Test
    fun showsReadOnlySourceContentAndReturnsToTheFileList() {
        var closed = false
        val content = """
            {
              "ok": true,
              "output": {
                "result": {
                  "path": "src/App.kt",
                  "content": "fun main() = Unit",
                  "size": 18
                }
              }
            }
        """.trimIndent()
        composeRule.setContent {
            ProjectFilesPanel(
                filesJson = files,
                selectedFileJson = content,
                loading = false,
                error = "",
                onRefresh = {},
                onOpenFile = {},
                onCloseFile = { closed = true }
            )
        }

        composeRule.onNodeWithText("src/App.kt").assertIsDisplayed()
        composeRule.onNodeWithText("fun main() = Unit").assertIsDisplayed()
        composeRule.onNodeWithText("Back to files").performClick()
        composeRule.runOnIdle { assertEquals(true, closed) }
    }
}
