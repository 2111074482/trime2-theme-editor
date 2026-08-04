/*
 * SPDX-FileCopyrightText: 2025 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.editor.project

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
