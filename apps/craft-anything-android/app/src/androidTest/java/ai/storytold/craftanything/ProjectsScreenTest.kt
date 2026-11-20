package ai.storytold.craftanything

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import java.util.concurrent.atomic.AtomicReference
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import androidx.test.ext.junit.runners.AndroidJUnit4

@RunWith(AndroidJUnit4::class)
class ProjectsScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val projectResponse = """
        {
          "ok": true,
          "output": {
            "result": {
              "count": 1,
              "projects": [
                {
                  "name": "Pocket Notes",
                  "folder": "/data/user/0/ai.storytold.craftanything/files/projects/pocket-notes",
                  "request": "A small private notes app",
                  "status": "building",
                  "status_label": "Building",
                  "waiting": 2,
                  "stack": ["Kotlin", "Compose"]
                }
              ]
            }
          }
        }
    """.trimIndent()

    @Test
    fun parsesTheRealProjectListEnvelope() {
        val projects = parseProjectList(projectResponse)

        assertEquals(1, projects.size)
        assertEquals("Pocket Notes", projects.single().name)
        assertEquals("Building", projects.single().statusLabel)
        assertEquals(2, projects.single().waiting)
    }

    @Test
    fun rendersProjectsAndOpensTheSelectedProject() {
        val openedFolder = AtomicReference<String?>(null)
        composeRule.setContent {
            ProjectsScreen(
                projectsJson = projectResponse,
                loading = false,
                error = "",
                detailsJson = "",
                onRefresh = {},
                onOpenProject = { openedFolder.set(it) },
                onCloseDetails = {}
            )
        }

        composeRule.onNodeWithText("Pocket Notes").assertIsDisplayed()
        composeRule.onNodeWithText("2 questions waiting").assertIsDisplayed()
        composeRule.onNodeWithText("Pocket Notes").performClick()
        composeRule.runOnIdle {
            assertTrue(openedFolder.get().orEmpty().endsWith("/pocket-notes"))
        }
    }

    @Test
    fun emptyProjectListExplainsWhereProjectsWillAppear() {
        composeRule.setContent {
            ProjectsScreen(
                projectsJson = """{"ok":true,"output":{"result":{"projects":[]}}}""",
                loading = false,
                error = "",
                detailsJson = "",
                onRefresh = {},
                onOpenProject = {},
                onCloseDetails = {}
            )
        }

        composeRule.onNodeWithText("Nothing here yet").assertIsDisplayed()
        composeRule.onNodeWithText("Check again").assertIsDisplayed()
    }
}
