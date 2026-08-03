/*
 * SPDX-FileCopyrightText: 2025 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.osfans.trime.editor.ui

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain

class ThemeStyleEntitiesTest : StringSpec({
    "extracts and pastes a complete clone style with literal overrides" {
        val source = "key = { background = 0xffffffff }\nfunctional = table.clone(key)\nfunctional.background = 0xff222222\nfunctional.font = { \"a.ttf\", \"b.ttf\" }\n"
        val snapshot = ThemeStyleEntities.extract(source, "functional")
        snapshot.cloneParent shouldBe "key"
        snapshot.referencedResources shouldBe listOf("a.ttf", "b.ttf")
        val target = "key = { background = 0xffeeeeee }\n"
        val pasted = ThemeStyleEntities.paste(target, snapshot, "action")
        pasted shouldContain "action = table.clone(key)"
        pasted shouldContain "action.background = 0xff222222"
        pasted shouldContain "action.font = {"
        pasted shouldNotContain "functional.background"
    }

    "blocks private URIs and absolute paths from complete entity clipboard payloads" {
        shouldThrow<IllegalArgumentException> { ThemeStyleEntities.extract("key = {}\nsecret = { background = \"content://provider/private.png\" }\n", "secret") }
        shouldThrow<IllegalArgumentException> { ThemeStyleEntities.extract("key = {}\nsecret = { font = \"/sdcard/private.ttf\" }\n", "secret") }
    }

    "blocks paste when clone dependency is missing" {
        val snapshot = ThemeStyleEntities.extract("key = {}\nfunctional = table.clone(key)\n", "functional")
        shouldThrow<IllegalArgumentException> { ThemeStyleEntities.paste("candidate = {}\n", snapshot, "action") }
    }

    "renames entity assignments and clone consumers" {
        val source = "key = {}\nfunctional = table.clone(key)\nfunctional.background = 0xff111111\naction = table.clone(functional)\n"
        val renamed = ThemeStyleEntities.rename(source, "functional", "command")
        renamed shouldContain "command = table.clone(key)"
        renamed shouldContain "command.background = 0xff111111"
        renamed shouldContain "action = table.clone(command)"
        renamed shouldNotContain "functional ="
    }

    "renames dotted literal style fields inside the style document" {
        val source = "key = {}\nfunctional = table.clone(key)\nsymbol = {}\nsymbol.key = {}\nsymbol.key.style = \"functional\"\n"
        val renamed = ThemeStyleEntities.rename(source, "functional", "command")
        renamed shouldContain "symbol.key.style = \"command\""
        shouldThrow<IllegalArgumentException> { ThemeStyleEntities.delete(source, "functional") }
    }

    "blocks inline and dynamic entity references from automatic rename" {
        val inline = "key = {}\nfunctional = table.clone(key)\nsymbol = { key = { style = \"functional\" } }\n"
        shouldThrow<IllegalArgumentException> { ThemeStyleEntities.rename(inline, "functional", "command") }
        val dynamic = "key = {}\nfunctional = table.clone(key)\naction = merge_style(functional)\n"
        shouldThrow<IllegalArgumentException> { ThemeStyleEntities.rename(dynamic, "functional", "command") }
        shouldThrow<IllegalArgumentException> { ThemeStyleEntities.delete(dynamic, "functional") }
    }

    "marks clone entities with dynamic dotted overrides as code-only" {
        val entries = ThemeStyleEntities.list("key = {}\nfunctional = table.clone(key)\nfunctional.background = choose_color()\n")
        entries.first { it.id == "functional" }.dynamic shouldBe true
    }

    "renames clone consumers without rewriting equals signs in leading comments" {
        val source = "key = {}\nfunctional = table.clone(key)\n-- relation = inherited\naction = table.clone(functional) -- keep\n"
        val renamed = ThemeStyleEntities.rename(source, "functional", "command")
        renamed shouldContain "-- relation = inherited"
        renamed shouldContain "action = table.clone(command) -- keep"
    }

    "blocks dynamic entity fields" {
        val source = "key = {}\nfunctional = table.clone(key)\nfunctional.background = choose_color()\n"
        shouldThrow<IllegalArgumentException> { ThemeStyleEntities.extract(source, "functional") }
    }

    "blocks deletion of dynamic named entities" {
        val source = "key = {}\ndynamic_style = make_style()\n"
        shouldThrow<IllegalArgumentException> { ThemeStyleEntities.delete(source, "dynamic_style") }
    }

    "protects key and inherited entities from deletion" {
        shouldThrow<IllegalArgumentException> { ThemeStyleEntities.delete("key = {}\n", "key") }
        val source = "key = {}\nfunctional = table.clone(key)\naction = table.clone(functional)\n"
        shouldThrow<IllegalArgumentException> { ThemeStyleEntities.delete(source, "functional") }
    }

    "replaces references in every rows key" {
        val source = "rows = { { keys = { { click = \"a\", style = \"functional\" }, { click = \"b\" } } } }\n"
        val updated = ThemeStyleEntities.replaceKeyboardReferences(source, "functional", "command")
        updated.changedKeys shouldBe 1
        updated.source shouldContain "style = \"command\""
        ThemeStyleEntities.referenceCount(updated.source, "functional") shouldBe 0
        ThemeStyleEntities.referenceCount(updated.source, "command") shouldBe 1
    }

    "counts and preserves implicit click-name style references during rename" {
        val source = "rows = { { keys = { { click = \"functional\" }, { click = \"x\", style = \"functional\" } } } }\n"
        ThemeStyleEntities.referenceCount(source, "functional") shouldBe 2
        val updated = ThemeStyleEntities.replaceKeyboardReferences(source, "functional", "command")
        updated.changedKeys shouldBe 2
        ThemeStyleEntities.referenceCount(updated.source, "command") shouldBe 2
        updated.source shouldContain "click = \"functional\""
        updated.source shouldContain "style = \"command\""
    }

    "clearing an implicit click-name style writes explicit key fallback" {
        val source = "rows = { { keys = { { click = \"functional\" } } } }\n"
        val updated = ThemeStyleEntities.replaceKeyboardReferences(source, "functional", null)
        updated.source shouldContain "style = \"key\""
        ThemeStyleEntities.referenceCount(updated.source, "functional") shouldBe 0
    }

    "counts and renames Flex container style references" {
        val source = "flex_box = { style = \"functional\", keys = { { click = \"a\" } } }\n"
        ThemeStyleEntities.referenceCount(source, "functional") shouldBe 1
        val updated = ThemeStyleEntities.replaceKeyboardReferences(source, "functional", "command")
        updated.changedKeys shouldBe 1
        updated.source shouldContain "style = \"command\""
    }

    "replaces references on all key_maps pages" {
        val source = "key_maps = { { name = \"one\", keys = { { click = \"a\", style = \"functional\" } } }, { name = \"two\", keys = { { click = \"b\", style = \"functional\" } } } }\n"
        val updated = ThemeStyleEntities.replaceKeyboardReferences(source, "functional", null)
        updated.changedKeys shouldBe 2
        ThemeStyleEntities.referenceCount(updated.source, "functional") shouldBe 0
    }
})
