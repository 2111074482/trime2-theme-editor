/*
 * SPDX-FileCopyrightText: 2025 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.editor.project

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import java.nio.file.Files

class DirectoryThemeProjectRepositoryTest : StringSpec({
    "writes selected keyboard without changing main file" {
        val root = Files.createTempDirectory("theme-repository").toFile()
        root.resolve("main.lua").writeText("keyboard = 'qwerty'\n")
        root.resolve("keyboards/qwerty.lua").apply { parentFile.mkdirs(); writeText("rows = {}\n") }
        val project = ThemeProject.discover(root)
        val selected = project.keyboard("qwerty")!!
        DirectoryThemeProjectRepository(project, selected).write("rows = { { keys = {} } }\n")
        root.resolve("main.lua").readText() shouldBe "keyboard = 'qwerty'\n"
        root.resolve("keyboards/qwerty.lua").readText() shouldBe "rows = { { keys = {} } }\n"
        root.deleteRecursively()
    }
})
