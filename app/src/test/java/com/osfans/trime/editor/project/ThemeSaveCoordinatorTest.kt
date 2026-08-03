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
        coordinator.save("project", repository, document, expected) is SaveResult.ExternalConflict shouldBe true
        source shouldBe "style = \"external\"\n"
    }
})
