/*
 * SPDX-FileCopyrightText: 2025 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.osfans.trime.editor.project

import com.osfans.trime.editor.core.ThemeLuaParser
import com.osfans.trime.editor.core.ThemeValue
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import java.nio.file.Files

class ThemeProjectMutatorTest : StringSpec({
    "copies renames selects and safely deletes keyboards" {
        val root = Files.createTempDirectory("theme-mutate").toFile()
        val spec = ThemeProjectCreator.Spec("demo", "Demo", "Author", "light", "default", ThemeProjectCreator.Palette.LIGHT, ThemeProjectCreator.KeyboardTemplate.ROWS)
        var project = ThemeProjectCreator.create(root, spec)
        val copy = ThemeProjectMutator.copyKeyboard(project, project.keyboards.single(), "copy")
        project = ThemeProject.discover(root)
        val renamed = ThemeProjectMutator.renameKeyboard(project, copy, "renamed")
        project = ThemeProject.discover(root)
        ThemeProjectMutator.setDefaultKeyboard(project, renamed.name)
        (ThemeLuaParser().parse(project.mainFile.readText()).document.get("keyboard") as ThemeValue.LuaString).value shouldBe "renamed"
        project = ThemeProject.discover(root)
        ThemeProjectMutator.deleteKeyboard(project, project.keyboard("default")!!)
        ThemeProject.discover(root).keyboards.single().name shouldBe "renamed"
        root.deleteRecursively()
    }
    "updates and removes literal keyboard top-level fields transactionally" {
        val root = Files.createTempDirectory("theme-keyboard-metadata").toFile()
        val spec = ThemeProjectCreator.Spec("demo", "Demo", "Author", "light", "default", ThemeProjectCreator.Palette.LIGHT, ThemeProjectCreator.KeyboardTemplate.ROWS)
        val project = ThemeProjectCreator.create(root, spec)
        val keyboard = project.keyboards.single()
        ThemeProjectMutator.updateKeyboardMetadata(
            keyboard,
            ThemeProjectMutator.KeyboardMetadata("Named", "Tester", "functional", true, true, 11.0, 22.0),
        )
        ThemeProjectMutator.readKeyboardMetadata(keyboard) shouldBe ThemeProjectMutator.KeyboardMetadata("Named", "Tester", "functional", true, true, 11.0, 22.0)

        ThemeProjectMutator.updateKeyboardMetadata(
            keyboard,
            ThemeProjectMutator.KeyboardMetadata("Named", "Tester", null, false, false, null, null),
        )
        val document = ThemeLuaParser().parse(keyboard.file.readText()).document
        document.get("style") shouldBe null
        document.get("key_width") shouldBe null
        document.get("key_height") shouldBe null
        root.deleteRecursively()
    }

})
