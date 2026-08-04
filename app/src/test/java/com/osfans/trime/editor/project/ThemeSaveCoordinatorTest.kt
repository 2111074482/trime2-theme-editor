/*
 * SPDX-FileCopyrightText: 2025 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.editor.project

import com.osfans.trime.editor.core.ThemeDocument
import com.osfans.trime.editor.core.ThemeNode
import com.osfans.trime.editor.core.ThemeValue
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

class ThemeSaveCoordinatorTest : StringSpec({
    "rejects an externally changed repository" {
        var source = "style = \"light\"\n"
        val repository = object : ThemeProjectRepository {
            override fun read(): String = source
            override fun write(value: String) { source = value }
        }
        val document = ThemeDocument(listOf(ThemeNode("style", 1, ThemeValue.LuaString("dark"))))
        val coordinator = ThemeSaveCoordinator()
        val expected = ThemeSaveCoordinator.fingerprint(source)
        source = "style = \"external\"\n"
        (coordinator.save("project", repository, document, expected) is SaveResult.ExternalConflict) shouldBe true
        source shouldBe "style = \"external\"\n"
    }

    "validates generated Lua before writing" {
        var writes = 0
        val repository = object : ThemeProjectRepository {
            override fun read(): String = "style = 'light'\n"
            override fun write(source: String) { writes++ }
        }
        val document = ThemeDocument(listOf(ThemeNode("broken", 1, ThemeValue.RawLuaNode("{"))))
        var rejected = false
        try { ThemeSaveCoordinator().save("project-invalid", repository, document, ThemeSaveCoordinator.fingerprint(repository.read())) }
        catch (_: java.io.IOException) { rejected = true }
        rejected shouldBe true
        writes shouldBe 0
    }

    "repository save validates raw documents before write" {
        var writes = 0
        val repository = object : ThemeProjectRepository {
            override fun read(): String = "style = 'light'\n"
            override fun write(source: String) { writes++ }
        }
        val document = ThemeDocument(listOf(ThemeNode("broken", 1, ThemeValue.RawLuaNode("{"))))
        var rejected = false
        try { repository.save(document) } catch (_: java.io.IOException) { rejected = true }
        rejected shouldBe true
        writes shouldBe 0
    }
})
