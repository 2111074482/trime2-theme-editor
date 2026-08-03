/*
 * SPDX-FileCopyrightText: 2025 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.osfans.trime.editor.project

import com.osfans.trime.editor.core.ThemeLuaParser
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import java.nio.file.Files

class ThemeProjectCreatorTest : StringSpec({
    "creates every literal keyboard template as a discoverable project" {
        ThemeProjectCreator.KeyboardTemplate.values().forEach { template ->
            val root = Files.createTempDirectory("theme-create").resolve(template.name).toFile()
            val spec = ThemeProjectCreator.Spec("demo", "Demo", "Author", "light", "default", ThemeProjectCreator.Palette.LIGHT, template)
            val project = ThemeProjectCreator.create(root, spec)
            project.styles.single().name shouldBe "light"
            project.keyboards.single().name shouldBe "default"
            ThemeLuaParser().parse(project.keyboards.single().file.readText()).diagnostics.none { it.severity.name == "ERROR" } shouldBe true
            root.parentFile.deleteRecursively()
        }
    }
})
