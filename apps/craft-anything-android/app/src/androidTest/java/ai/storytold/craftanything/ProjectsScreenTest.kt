package ai.storytold.craftanything

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
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
    fun showsLiveBuildStatusActivityAndStopAction() {
        val folder = "/private/projects/pocket-notes"
        val detail = """{"ok":true,"output":{"result":{"name":"Pocket Notes","folder":"$folder","status":"building","status_label":"Building","questions":[],"conversation":{"total":3}}}}"""
        val status = """{"ok":true,"output":{"result":{"running":true,"queued":null,"status":"building","status_label":"Building","note":"Checking the generated pages"}}}"""
        val activity = """{"ok":true,"output":{"result":{"lines":["22:00:01 stage Studying the reference","22:00:02 start Reading src/main.kt"]}}}"""
        val stopped = AtomicReference<String?>(null)
        val direction = AtomicReference<Pair<String, String>?>(null)
        composeRule.setContent {
            ProjectsScreen(
                projectsJson = projectResponse,
                loading = false,
                error = "",
                detailsJson = detail,
                onRefresh = {},
                onOpenProject = {},
                onCloseDetails = {},
                buildStatusJson = status,
                activityJson = activity,
                onStopBuild = { stopped.set(it) },
                onAddBuildNote = { project, note -> direction.set(project to note) }
            )
        }

        composeRule.onNodeWithText("Live build activity").assertIsDisplayed()
        composeRule.onNodeWithText("Building now").assertIsDisplayed()
        composeRule.onNodeWithText("22:00:02 start Reading src/main.kt").assertIsDisplayed()
        composeRule.onNodeWithText("Tell the builder what to change").performTextInput("Add a settings page")
        composeRule.onNodeWithText("Send direction").performClick()
        composeRule.runOnIdle {
            assertEquals(folder to "Add a settings page", direction.get())
        }
        composeRule.onNodeWithText("Stop build").performClick()
        composeRule.runOnIdle { assertEquals(folder, stopped.get()) }
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
    @Test
    fun showsTheEngineInboxTitleDetailAndChoiceButtons() {
        val detail = """
            {
              "ok": true,
              "output": {
                "result": {
                  "name": "Pocket Notes",
                  "folder": "/private/projects/pocket-notes",
                  "request": "A small private notes app",
                  "status": "building",
                  "questions": [
                    {
                      "id": "ask-123",
                      "kind": "question",
                      "title": "Which storage should I use?",
                      "detail": "This changes how notes are synced.",
                      "choices": ["Local only", "Cloud sync"],
                      "allow_text": true
                    }
                  ],
                  "conversation": {"total": 2}
                }
              }
            }
        """.trimIndent()
        val answer = AtomicReference<List<String>?>(null)
        composeRule.setContent {
            ProjectsScreen(
                projectsJson = projectResponse,
                loading = false,
                error = "",
                detailsJson = detail,
                onRefresh = {},
                onOpenProject = {},
                onCloseDetails = {},
                onAnswer = { _, id, choice, text, allow ->
                    answer.set(listOf(id, choice.orEmpty(), text.orEmpty(), allow?.toString().orEmpty()))
                }
            )
        }

        composeRule.onNodeWithText("Which storage should I use?").assertIsDisplayed()
        composeRule.onNodeWithText("This changes how notes are synced.").assertIsDisplayed()
        composeRule.onNodeWithText("Local only").performClick()
        composeRule.runOnIdle {
            assertEquals(listOf("ask-123", "Local only", "", ""), answer.get())
        }
    }

    @Test
    fun approvalInboxRequestsOfferAllowAndDenyActions() {
        val detail = """
            {
              "ok": true,
              "output": {
                "result": {
                  "name": "Pocket Notes",
                  "folder": "/private/projects/pocket-notes",
                  "questions": [
                    {
                      "id": "approval-123",
                      "kind": "approval",
                      "title": "Run the test command?",
                      "detail": "The builder wants to run ./gradlew test.",
                      "choices": [],
                      "allow_text": false
                    }
                  ],
                  "conversation": {"total": 2}
                }
              }
            }
        """.trimIndent()
        val answer = AtomicReference<List<String>?>(null)
        composeRule.setContent {
            ProjectsScreen(
                projectsJson = projectResponse,
                loading = false,
                error = "",
                detailsJson = detail,
                onRefresh = {},
                onOpenProject = {},
                onCloseDetails = {},
                onAnswer = { _, id, choice, text, allow ->
                    answer.set(listOf(id, choice.orEmpty(), text.orEmpty(), allow?.toString().orEmpty()))
                }
            )
        }

        composeRule.onNodeWithText("Run the test command?").assertIsDisplayed()
        composeRule.onNodeWithText("Allow").performClick()
        composeRule.runOnIdle {
            assertEquals(listOf("approval-123", "", "", "true"), answer.get())
        }
    }

}
