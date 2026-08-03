/*
 * SPDX-FileCopyrightText: 2025 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.editor.project

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import java.nio.file.Files

class ThemeInstallerTest : StringSpec({
    "installs a valid project and can roll it back" {
        val root = Files.createTempDirectory("theme-install").toFile()
        val source = root.resolve("source").apply { mkdirs() }
        source.resolve("main.lua").writeText("style = 'light'\nkeyboard = 'qwerty'\n")
        source.resolve("styles/light/main.lua").apply { parentFile.mkdirs(); writeText("background = 0xff000000\n") }
        source.resolve("keyboards/qwerty.lua").apply { parentFile.mkdirs(); writeText("rows = {}\n") }
        val target = root.resolve("target")
        target.mkdirs(); target.resolve("old.txt").writeText("old")
        val result = ThemeInstaller().install(source, target) as InstallResult.Success
        target.resolve("main.lua").isFile shouldBe true
        ThemeInstaller().rollback(result, target) shouldBe true
        target.resolve("old.txt").readText() shouldBe "old"
        root.deleteRecursively()
    }
})
