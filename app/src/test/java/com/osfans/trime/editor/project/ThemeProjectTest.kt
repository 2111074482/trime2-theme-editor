/*
 * SPDX-FileCopyrightText: 2025 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.editor.project

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldContain
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
        project.styles.map { it.name } shouldContain "light"
        project.styles.map { it.name } shouldContain "Night"
        project.keyboards.single().name shouldBe "qwerty"
        val main = project.open("main", ThemeProjectFile.Kind.MAIN)
        val selection = ThemeProjectSelector.select(project, main)
        selection.keyboard shouldBe "qwerty"
        selection.diagnostics.map { it.message } shouldContain "Dynamic get_keyboard is preserved but not executed"
        root.deleteRecursively()
    }
})
