/*
 * SPDX-FileCopyrightText: 2025 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.editor.project

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import java.nio.file.Files

class ThemeProjectExportTest : StringSpec({
    fun project(): ThemeProject {
        val root = Files.createTempDirectory("theme-export").toFile()
        root.resolve("main.lua").writeText("style = 'light'\nkeyboard = 'qwerty'\nbackground = 'images/used.png'\n")
        root.resolve("styles/light/main.lua").apply { parentFile.mkdirs(); writeText("background = 0xff000000\n") }
        root.resolve("keyboards/qwerty.lua").apply { parentFile.mkdirs(); writeText("rows = {}\n") }
        root.resolve("images/used.png").apply { parentFile.mkdirs(); writeBytes(byteArrayOf(1)) }
        root.resolve("images/unused.png").writeBytes(byteArrayOf(2))
        root.resolve("fonts/theme.ttf").apply { parentFile.mkdirs(); writeBytes(byteArrayOf(3)) }
        root.resolve("sounds/tap.ogg").apply { parentFile.mkdirs(); writeBytes(byteArrayOf(4)) }
        root.resolve("scripts/action.lua").apply { parentFile.mkdirs(); writeText("return function() end\n") }
        return ThemeProject.discover(root)
    }

    "plans complete Lua resource keyboard style and report scopes" {
        val project = project()
        val source = project.mainFile.readText()
        ThemeProjectExportPlanner.plan(project, ThemeExportKind.FULL_THEME, luaSource = source).entries.size shouldBe 8
        ThemeProjectExportPlanner.plan(project, ThemeExportKind.LUA_ONLY, luaSource = source).entries.map { it.relativePath } shouldBe listOf(
            "keyboards/qwerty.lua",
            "main.lua",
            "scripts/action.lua",
            "styles/light/main.lua",
        )
        ThemeProjectExportPlanner.plan(project, ThemeExportKind.RESOURCES_ONLY, luaSource = source).entries.size shouldBe 5
        ThemeProjectExportPlanner.plan(project, ThemeExportKind.CURRENT_KEYBOARD, currentFile = project.keyboards.single()).entries.single().relativePath shouldBe "keyboards/qwerty.lua"
        ThemeProjectExportPlanner.plan(project, ThemeExportKind.CURRENT_STYLE, currentFile = project.styles.single()).entries.single().relativePath shouldBe "styles/light/main.lua"
        ThemeProjectExportPlanner.plan(project, ThemeExportKind.COMPATIBILITY_REPORT).let {
            it.entries.isEmpty() shouldBe true
            it.includeDiagnosticReport shouldBe true
        }
        project.root.deleteRecursively()
    }

    "applies resource switches and preserves dynamically uncertain resources" {
        val project = project()
        val options = ThemeExportOptions(
            includeImages = true,
            includeFonts = false,
            includeSounds = false,
            includeScripts = false,
            removeUnusedResources = true,
            includeComments = false,
            includeDiagnosticReport = false,
        )
        val source = project.mainFile.readText() + "\nbackground = dynamic_image()\n"
        val plan = ThemeProjectExportPlanner.plan(project, ThemeExportKind.FULL_THEME, options, luaSource = source)
        plan.entries.any { it.relativePath == "images/used.png" } shouldBe true
        plan.entries.any { it.relativePath == "images/unused.png" } shouldBe true
        plan.entries.none { it.relativePath.startsWith("fonts/") || it.relativePath.startsWith("sounds/") || it.relativePath.startsWith("scripts/") } shouldBe true
        plan.includeComments shouldBe false
        plan.includeDiagnosticReport shouldBe false
        project.root.deleteRecursively()
    }

    "removes only statically unused resources when references are certain" {
        val project = project()
        val plan = ThemeProjectExportPlanner.plan(
            project,
            ThemeExportKind.RESOURCES_ONLY,
            ThemeExportOptions(removeUnusedResources = true),
            luaSource = project.mainFile.readText(),
        )
        plan.entries.map { it.relativePath } shouldBe listOf("images/used.png")
        plan.excludedCount shouldBe 7
        project.root.deleteRecursively()
    }

    "rejects wrong current scope and symbolic entries" {
        val project = project()
        var wrongScopeRejected = false
        try {
            ThemeProjectExportPlanner.plan(project, ThemeExportKind.CURRENT_STYLE, currentFile = project.keyboards.single())
        } catch (_: IllegalArgumentException) {
            wrongScopeRejected = true
        }
        wrongScopeRejected shouldBe true

        val outside = Files.createTempFile("theme-export-outside", ".png").toFile()
        val linked = try {
            Files.createSymbolicLink(project.root.resolve("images/link.png").toPath(), outside.toPath())
            true
        } catch (_: Exception) {
            false
        }
        if (linked) {
            var linkRejected = false
            try {
                ThemeProjectExportPlanner.plan(project, ThemeExportKind.FULL_THEME)
            } catch (_: IllegalArgumentException) {
                linkRejected = true
            }
            linkRejected shouldBe true
        }
        project.root.deleteRecursively()
        outside.delete()
    }
    "strips comments without changing quoted or long bracket strings" {
        val source = """-- heading
name = "-- literal" -- tail
value = '-- second'
long = [=[-- kept
text]=]
--[=[ block
comment ]=]
last = 1
"""
        ThemeLuaCommentFilter.strip(source) shouldBe """
name = "-- literal"
value = '-- second'
long = [=[-- kept
text]=]

last = 1
"""
    }

    "includes style-local resources and applies resource kind switches" {
        val project = project()
        project.root.resolve("styles/light/click.ogg").writeBytes(byteArrayOf(9))
        val included = ThemeProjectExportPlanner.plan(project, ThemeExportKind.RESOURCES_ONLY, luaSource = "sound = 'click.ogg'")
        included.entries.any { it.relativePath == "styles/light/click.ogg" } shouldBe true
        val excluded = ThemeProjectExportPlanner.plan(
            project,
            ThemeExportKind.FULL_THEME,
            ThemeExportOptions(includeSounds = false),
            luaSource = "sound = 'click.ogg'",
        )
        excluded.entries.none { it.relativePath == "styles/light/click.ogg" } shouldBe true
        project.root.deleteRecursively()
    }

})
