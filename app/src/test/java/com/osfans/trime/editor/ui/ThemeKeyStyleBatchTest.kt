/*
 * SPDX-FileCopyrightText: 2025 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.osfans.trime.editor.ui

import com.osfans.trime.editor.project.ThemeProject
import com.osfans.trime.editor.project.ThemeProjectCreator
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import java.nio.file.Files

class ThemeKeyStyleBatchTest : StringSpec({
    "adds literal overrides after clone styles without executing clone" {
        val source = "key = { background = 0xffffffff, text_color = 0xff000000 }\nfunctional = table.clone(key)\nfunctional.preview = nil\n"
        val updated = ThemeKeyStyleBatch.update(source, listOf("functional"), ThemeKeyStyleBatch.Change("#ff112233", "4294967295"))
        updated shouldContain "functional = table.clone(key)"
        updated shouldContain "functional.preview = nil"
        updated shouldContain "functional.background = 0xff112233"
        updated shouldContain "functional.text_color = 0xffffffff"
    }

    "replaces an existing assignment while retaining its line comment" {
        val source = "key = {}\nkey.background = 0xffffffff -- existing\n"
        val updated = ThemeKeyStyleBatch.update(source, listOf("key"), ThemeKeyStyleBatch.Change("#ff010203", null))
        updated shouldContain "key.background = 0xff010203 -- existing"
    }

    "writes a safe background resource as a quoted literal" {
        val source = "key = {}\n"
        ThemeKeyStyleBatch.update(source, listOf("key"), ThemeKeyStyleBatch.Change("images/key.png", null)) shouldContain "key.background = \"images/key.png\""
        shouldThrow<IllegalArgumentException> { ThemeKeyStyleBatch.update(source, listOf("key"), ThemeKeyStyleBatch.Change("../secret.png", null)) }
    }

    "resolves clone preview colors from literal overrides and fallback" {
        val source = "key = { background = 0xffeeeeee, text_color = 0xff111111 }\nfunctional = table.clone(key)\nfunctional.background = 0xff222222\n"
        val colors = ThemeKeyStyleBatch.previewColors(source, listOf("functional"))
        colors.backgrounds["functional"] shouldBe 0xff222222.toInt()
        colors.textColors["functional"] shouldBe 0xff111111.toInt()
    }

    "rejects a field assignment shadowed by a later entity replacement" {
        val source = "key = {}\nfunctional.background = 0xff000000\nfunctional = table.clone(key)\n"
        shouldThrow<IllegalArgumentException> { ThemeKeyStyleBatch.update(source, listOf("functional"), ThemeKeyStyleBatch.Change("#ffffffff", null)) }
    }

    "clears a clone override and previews the inherited key color" {
        val source = "key = { background = 0xffeeeeee }\nfunctional = table.clone(key)\nfunctional.background = 0xff222222\n"
        val updated = ThemeKeyStyleBatch.update(source, listOf("functional"), ThemeKeyStyleBatch.Change("", null))
        updated shouldContain "functional.background = nil"
        ThemeKeyStyleBatch.previewColors(updated, listOf("functional")).backgrounds["functional"] shouldBe 0xffeeeeee.toInt()
    }

    "limits reference counts to keyboards using the edited style asset" {
        val root = Files.createTempDirectory("theme-style-scope").toFile()
        val spec = ThemeProjectCreator.Spec("demo", "Demo", "Author", "light", "default", ThemeProjectCreator.Palette.LIGHT, ThemeProjectCreator.KeyboardTemplate.ABSOLUTE_KEYS)
        ThemeProjectCreator.create(root, spec)
        root.resolve("styles/dark").mkdirs(); root.resolve("styles/dark/main.lua").writeText("key = {}\n")
        root.resolve("keyboards/dark.lua").writeText("style = \"dark\"\nkeys = { { click = \"a\" } }\n")
        val project = ThemeProject.discover(root)
        ThemeKeyStyleBatch.references(project, listOf("key"), "light").totalReferences shouldBe 4
        ThemeKeyStyleBatch.references(project, listOf("key"), "dark").totalReferences shouldBe 1
        root.deleteRecursively()
    }

    "does not reinterpret an invalid numeric background as a resource" {
        val source = "key = {}\n"
        shouldThrow<IllegalArgumentException> { ThemeKeyStyleBatch.update(source, listOf("key"), ThemeKeyStyleBatch.Change("99999999999", null)) }
    }

    "reports references across project keyboards and dynamic uncertainty" {
        val root = Files.createTempDirectory("theme-style-references").toFile()
        val spec = ThemeProjectCreator.Spec("demo", "Demo", "Author", "light", "default", ThemeProjectCreator.Palette.LIGHT, ThemeProjectCreator.KeyboardTemplate.ROWS)
        ThemeProjectCreator.create(root, spec)
        val keyboards = root.resolve("keyboards")
        keyboards.resolve("extra.lua").writeText("keys = make_keys()\n")
        val project = ThemeProject.discover(root)
        val report = ThemeKeyStyleBatch.references(project, listOf("key"))
        report.totalReferences shouldBe 32
        report.uncertainKeyboardIds.shouldContain("extra")
        root.deleteRecursively()
    }
})
