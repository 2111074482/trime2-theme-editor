/*
 * SPDX-FileCopyrightText: 2025 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.editor.project

import com.osfans.trime.editor.core.Severity
import com.osfans.trime.editor.core.ThemeLuaParser
import com.osfans.trime.editor.core.ThemeValue
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import java.nio.file.Files

class ThemeProjectTest : StringSpec({
    "discovers main styles keyboards and resources without executing Lua" {
        val root = Files.createTempDirectory("theme-project").toFile()
        root.resolve("main.lua").writeText("style = 'light'\nkeyboard = 'qwerty'\nfunction get_keyboard() return 'qwerty' end\n")
        root.resolve("styles/light/main.lua").apply { parentFile.mkdirs(); writeText("background = 0xff000000\n") }
        root.resolve("styles/Night/main.lua").apply { parentFile.mkdirs(); writeText("background = 0xff000001\n") }
        root.resolve("keyboards/qwerty.lua").apply { parentFile.mkdirs(); writeText("rows = { { keys = { { click = 'a' } } } }\n") }
        root.resolve("images/key.png").apply { parentFile.mkdirs(); writeBytes(byteArrayOf(1)) }

        val project = ThemeProject.discover(root)
        project.styles.map { it.name }.contains("light") shouldBe true
        project.styles.map { it.name }.contains("Night") shouldBe true
        project.keyboards.single().name shouldBe "qwerty"
        val main = project.open("main", ThemeProjectFile.Kind.MAIN)
        val selection = ThemeProjectSelector.select(project, main)
        selection.keyboard shouldBe "qwerty"
        selection.diagnostics.map { it.message }.contains("动态取键盘(get_keyboard)已保留且不会执行") shouldBe true
        root.deleteRecursively()
    }

    "explicit style and keyboard take priority over main static selection" {
        val root = Files.createTempDirectory("theme-project-explicit").toFile()
        root.resolve("main.lua").writeText("style = 'light'\nkeyboard = 'qwerty'\n")
        root.resolve("styles/light/main.lua").apply { parentFile.mkdirs(); writeText("marker = 'main-style'\n") }
        root.resolve("styles/Night/main.lua").apply { parentFile.mkdirs(); writeText("marker = 'explicit-style'\n") }
        root.resolve("keyboards/qwerty.lua").apply { parentFile.mkdirs(); writeText("marker = 'main-keyboard'\n") }
        root.resolve("keyboards/numeric.lua").writeText("marker = 'explicit-keyboard'\n")

        val snapshot = ThemeProjectSnapshot.loadSelected(
            ThemeProject.discover(root),
            "Night",
            "numeric",
            ThemeLuaParser(),
        )

        snapshot.styleSource?.name shouldBe "Night"
        snapshot.keyboardSource?.name shouldBe "numeric"
        (snapshot.style?.document?.get("marker") as ThemeValue.LuaString).value shouldBe "explicit-style"
        (snapshot.keyboard?.document?.get("marker") as ThemeValue.LuaString).value shouldBe "explicit-keyboard"
        snapshot.diagnostics.firstOrNull { it.message == "已使用显式样式:Night" }?.severity shouldBe Severity.INFO
        snapshot.diagnostics.firstOrNull { it.message == "已使用显式键盘:numeric" }?.severity shouldBe Severity.INFO
        root.deleteRecursively()
    }

    "missing explicit names warn and fall back to main static selection" {
        val root = Files.createTempDirectory("theme-project-explicit-missing").toFile()
        root.resolve("main.lua").writeText("style = 'Night'\nkeyboard = 'numeric'\n")
        root.resolve("styles/light/main.lua").apply { parentFile.mkdirs(); writeText("marker = 'light-fallback'\n") }
        root.resolve("styles/Night/main.lua").apply { parentFile.mkdirs(); writeText("marker = 'main-style'\n") }
        root.resolve("keyboards/numeric.lua").apply { parentFile.mkdirs(); writeText("marker = 'main-keyboard'\n") }

        val snapshot = ThemeProjectSnapshot.loadSelected(
            ThemeProject.discover(root),
            "missing-style",
            "missing-keyboard",
            ThemeLuaParser(),
        )

        snapshot.styleSource?.name shouldBe "Night"
        snapshot.keyboardSource?.name shouldBe "numeric"
        snapshot.diagnostics.firstOrNull { it.message == "未找到显式样式文件:missing-style" }?.severity shouldBe Severity.WARNING
        snapshot.diagnostics.firstOrNull {
            it.message == "显式样式不可用;已回退到 main.lua 静态样式:Night"
        }?.severity shouldBe Severity.INFO
        snapshot.diagnostics.firstOrNull { it.message == "未找到显式键盘文件:missing-keyboard" }?.severity shouldBe Severity.WARNING
        snapshot.diagnostics.firstOrNull {
            it.message == "显式键盘不可用;已回退到 main.lua 静态键盘:numeric"
        }?.severity shouldBe Severity.INFO
        root.deleteRecursively()
    }

    "dynamic Lua is not executed and cannot decide static selection" {
        val root = Files.createTempDirectory("theme-project-dynamic-selection").toFile()
        root.resolve("main.lua").writeText(
            """
            style = choose_style()
            keyboard = get_keyboard()
            function choose_style() error('must not execute') return 'Dynamic' end
            function get_keyboard() error('must not execute') return 'dynamic' end
            """.trimIndent() + "\n",
        )
        root.resolve("styles/light/main.lua").apply { parentFile.mkdirs(); writeText("marker = 'safe-light'\n") }
        root.resolve("styles/Dynamic/main.lua").apply { parentFile.mkdirs(); writeText("marker = 'dynamic-style'\n") }
        root.resolve("keyboards/dynamic.lua").apply { parentFile.mkdirs(); writeText("marker = 'dynamic-keyboard'\n") }

        val snapshot = ThemeProjectSnapshot.loadSelected(
            ThemeProject.discover(root),
            null,
            null,
            ThemeLuaParser(),
        )

        snapshot.styleSource?.name shouldBe "light"
        snapshot.keyboardSource shouldBe null
        snapshot.keyboard shouldBe null
        snapshot.diagnostics.firstOrNull {
            it.message == "动态取键盘(get_keyboard)已保留且不会执行"
        }?.severity shouldBe Severity.INFO
        snapshot.diagnostics.firstOrNull { it.message == "未找到键盘文件:qwerty26" }?.severity shouldBe Severity.WARNING
        root.deleteRecursively()
    }

    "rejects a symbolic main file" {
        val root = Files.createTempDirectory("theme-project-link").toFile()
        val outside = Files.createTempFile("theme-main-outside", ".lua").toFile().apply { writeText("style = 'light'\n") }
        val linked = try { Files.createSymbolicLink(root.resolve("main.lua").toPath(), outside.toPath()); true } catch (_: Exception) { false }
        if (linked) {
            var rejected = false
            try { ThemeProject.discover(root) } catch (_: IllegalArgumentException) { rejected = true }
            rejected shouldBe true
        }
        root.deleteRecursively(); outside.delete()
    }

})
