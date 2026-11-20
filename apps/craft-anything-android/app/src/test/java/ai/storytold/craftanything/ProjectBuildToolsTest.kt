package ai.storytold.craftanything

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ProjectBuildToolsTest {
    private fun listing(vararg paths: String): String {
        val files = JSONArray()
        paths.forEach { files.put(JSONObject().put("path", it).put("name", it.substringAfterLast('/')).put("kind", "text")) }
        return JSONObject()
            .put("ok", true)
            .put("output", JSONObject().put("result", JSONObject().put("files", files)))
            .toString()
    }

    @Test
    fun prefersTheExistingNpmLockfileAndUsesCi() {
        val setup = detectDependencySetups(listing("package.json", "package-lock.json")).single()
        assertEquals("npm", setup.manager)
        assertEquals("npm ci", setup.command)
        assertEquals("package-lock.json", setup.lockfile)
    }

    @Test
    fun respectsPnpmAndWarnsWhenMultipleJsLockfilesExist() {
        val setup = detectDependencySetups(listing("package.json", "pnpm-lock.yaml", "package-lock.json")).single()
        assertEquals("pnpm", setup.manager)
        assertEquals("pnpm install --frozen-lockfile", setup.command)
        assertTrue(setup.caution?.contains("Multiple JavaScript lockfiles") == true)
        assertTrue(dependencyInstallPrompt(setup).contains("Do not guess or delete lockfiles"))
    }

    @Test
    fun detectsOtherProjectToolchainsWithoutInventingNode() {
        val setups = detectDependencySetups(listing("Cargo.toml", "Cargo.lock", "requirements.txt", "go.mod"))
        assertEquals(3, setups.size)
        assertTrue(setups.any { it.ecosystem == "Rust" && it.command == "cargo build --locked" })
        assertTrue(setups.any { it.ecosystem == "Python" && it.command == "python -m pip install -r requirements.txt" })
        assertTrue(setups.any { it.ecosystem == "Go" && it.command == "go mod download" })
    }

    @Test
    fun returnsNoSetupForPlainStaticSource() {
        assertTrue(detectDependencySetups(listing("index.html", "style.css", "app.js")).isEmpty())
    }
}
