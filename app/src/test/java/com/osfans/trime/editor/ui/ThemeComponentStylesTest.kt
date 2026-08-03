/*
 * SPDX-FileCopyrightText: 2025 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.osfans.trime.editor.ui

import com.osfans.trime.editor.core.ThemeValue
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain

class ThemeComponentStylesTest : StringSpec({
    "reads and updates literal nested and dotted fields" {
        val nested = "candidate = { height = 48, pressed = { background = 0xff000001 } }\n"
        ThemeComponentStyles.read(nested, "candidate.height").let {
            it.numberValue shouldBe 48.0
            it.explicit shouldBe true
            it.sourcePath shouldBe "candidate"
        }
        val first = ThemeComponentStyles.updateNumber(nested, "candidate.height", 52.0)
        ThemeComponentStyles.read(first, "candidate.height").numberValue shouldBe 52.0

        val dotted = first + "candidate.pressed.text_color = 0xff010203 -- keep me\n"
        val updated = ThemeComponentStyles.updateColorOrResource(
            dotted,
            "candidate.pressed.text_color",
            "#ff112233",
        )
        updated shouldContain "candidate.pressed.text_color = 4279312947 -- keep me"
        ThemeComponentStyles.read(updated, "candidate.pressed.text_color").colorValue shouldBe 0xff112233L
    }

    "follows a simple clone only as static fallback and appends a safe override" {
        val source = """
            base = { text_color = 0xff010203, pressed = { background = 0xff111111 } }
            candidate = table.clone(base)
            candidate.height = 48
        """.trimIndent() + "\n"
        ThemeComponentStyles.read(source, "candidate.text_color").let {
            it.colorValue shouldBe 0xff010203L
            it.explicit shouldBe false
            it.inheritedFrom shouldBe "base"
            it.trace shouldContain "candidate -> base"
        }
        val updated = ThemeComponentStyles.updateColorOrResource(source, "candidate.text_color", 0xffaabbccL)
        updated shouldContain "candidate = table.clone(base)"
        updated shouldContain "candidate.text_color = 4289379276"
        ThemeComponentStyles.read(updated, "candidate.text_color").explicit shouldBe true
    }

    "a later literal root wins and is patched rather than reviving an old override" {
        val source = """
            candidate.text_color = 0xff000001
            candidate = { text_color = 0xff000002, height = 41 }
        """.trimIndent() + "\n"
        ThemeComponentStyles.read(source, "candidate.text_color").colorValue shouldBe 0xff000002L
        val updated = ThemeComponentStyles.updateColorOrResource(source, "candidate.text_color", 0xff000003L)
        updated.lines().count { it.startsWith("candidate.text_color") } shouldBe 1
        updated shouldContain "text_color = 4278190083"
        ThemeComponentStyles.read(updated, "candidate.height").numberValue shouldBe 41.0
        ThemeComponentStyles.read(updated, "candidate.text_color").colorValue shouldBe 0xff000003L
    }

    "creates a literal table when the component root is entirely missing" {
        val updated = ThemeComponentStyles.updateNumber("unrelated = true\n", "composition.min_height", 0.0)
        updated shouldContain "unrelated = true"
        updated shouldContain "composition = {"
        updated shouldContain "min_height = 0"
        updated shouldNotContain "composition.min_height ="
        ThemeComponentStyles.read(updated, "composition.min_height").numberValue shouldBe 0.0
    }

    "preserves comments and unrelated fields while rewriting a containing literal table" {
        val source = """
            -- component heading
            preedit = { text_size = 18, unknown = "retain" } -- table note
            other = callback()
        """.trimIndent() + "\n"
        val updated = ThemeComponentStyles.updateNumber(source, "preedit.text_size", 20.0)
        updated shouldContain "-- component heading"
        updated shouldContain "unknown = \"retain\""
        updated shouldContain "-- table note"
        updated shouldContain "other = callback()"
    }

    "blocks duplicate exact assignments and later dynamic ancestors" {
        shouldThrow<IllegalArgumentException> {
            ThemeComponentStyles.read(
                "candidate.height = 40\ncandidate.height = 41\n",
                "candidate.height",
            )
        }
        val dynamic = "candidate.height = 40\ncandidate = make_candidate()\n"
        ThemeComponentStyles.read(dynamic, "candidate.height").let {
            it.dynamic shouldBe true
            it.diagnostic shouldNotBe null
        }
        shouldThrow<IllegalArgumentException> {
            ThemeComponentStyles.updateNumber(dynamic, "candidate.height", 42.0)
        }
        ThemeComponentStyles.read(
            "base = {}\ncandidate = table.clone(base.pressed)\n",
            "candidate.height",
        ).dynamic shouldBe true
    }

    "accepts unsigned colors and safe resources and rejects unsafe paths" {
        var source = "symbol = {}\n"
        source = ThemeComponentStyles.updateColorOrResource(source, "symbol.background", "4294967295")
        ThemeComponentStyles.read(source, "symbol.background").colorValue shouldBe 0xffffffffL
        source = ThemeComponentStyles.updateColorOrResource(source, "symbol.background", "images/panel.webp")
        ThemeComponentStyles.read(source, "symbol.background").resourceValue shouldBe "images/panel.webp"

        listOf("../secret.png", "/sdcard/a.png", "file:///tmp/a", "https://example/a", "images/\u0001a")
            .forEach { unsafe ->
                shouldThrow<IllegalArgumentException> {
                    ThemeComponentStyles.updateColorOrResource(source, "symbol.background", unsafe)
                }
            }
        shouldThrow<IllegalArgumentException> {
            ThemeComponentStyles.updateColorOrResource(source, "symbol.background", 0x1_0000_0000L)
        }
    }

    "validates enums booleans and numeric constraints" {
        val source = """
            symbol = { tab_bar = {} }
            preedit = {}
            composition = {}
        """.trimIndent() + "\n"
        ThemeComponentStyles.updateString(source, "symbol.tab_bar.gravity", "top") shouldContain "gravity = \"top\""
        shouldThrow<IllegalArgumentException> { ThemeComponentStyles.updateString(source, "symbol.tab_bar.gravity", "left") }
        shouldThrow<IllegalArgumentException> { ThemeComponentStyles.updateString(source, "preedit.inline", "callback") }
        ThemeComponentStyles.updateString(source, "preedit.inline", "preview") shouldContain "inline = \"preview\""
        ThemeComponentStyles.updatePreeditInline(source, null, true).let { updated ->
            updated shouldContain "inline = true"
            ThemeComponentStyles.read(updated, "preedit.inline").compatibilityDiagnostic shouldContain "runtime previews it as none"
        }
        shouldThrow<IllegalArgumentException> { ThemeComponentStyles.updateString(source, "composition.position", "middle") }
        shouldThrow<IllegalArgumentException> { ThemeComponentStyles.updateBoolean(source, "composition.movable", true) }
        ThemeComponentStyles.updateString(source, "composition.movable", "once") shouldContain "movable = \"once\""
        shouldThrow<IllegalArgumentException> { ThemeComponentStyles.updateNumber(source, "composition.max_width", -1.0) }
        shouldThrow<IllegalArgumentException> { ThemeComponentStyles.updateNumber(source, "composition.text_size", Double.NaN) }
        shouldThrow<IllegalArgumentException> { ThemeComponentStyles.updateNumber(source, "composition.text_size", 18.5) }
        ThemeComponentStyles.read("composition.text_size = 18.5\n", "composition.text_size").let {
            it.numberValue shouldBe 18.5
            it.compatibilityDiagnostic shouldContain "runtime fallback"
        }
        ThemeComponentStyles.updateNumber(source, "composition.max_entries", -1.0) shouldContain "max_entries = -1"
        ThemeComponentStyles.updateNumber(source, "composition.min_height", 0.0) shouldContain "min_height = 0"
        ThemeComponentStyles.updateNumber(source, "composition.cloud_max_entries", 0.0) shouldContain "cloud_max_entries = 0"
        ThemeComponentStyles.updateBoolean(source, "composition.use_cursor", true) shouldContain "use_cursor = true"
        ThemeComponentStyles.updateNumber(source, "composition.padding.left", 4.0) shouldContain "left = 4"
        shouldThrow<IllegalArgumentException> { ThemeComponentStyles.read("composition.show = \"true\"\n", "composition.show") }
    }

    "removes only the explicit override and restores clone inheritance" {
        val source = """
            base = { text_color = 0xff010203 }
            candidate = table.clone(base)
            candidate.height = 48
            candidate.text_color = 0xffaabbcc -- remove this only
        """.trimIndent() + "\n"
        val updated = ThemeComponentStyles.remove(source, "candidate.text_color")
        updated shouldContain "candidate.height = 48"
        updated shouldNotContain "remove this only"
        ThemeComponentStyles.read(updated, "candidate.text_color").let {
            it.colorValue shouldBe 0xff010203L
            it.explicit shouldBe false
            it.inheritedFrom shouldBe "base"
        }
    }

    "preserves zero line spacing multiplier and reports preview normalization" {
        val source = "composition = { line_spacing_multiplier = 0 }\n"
        ThemeComponentStyles.read(source, "composition.line_spacing_multiplier").let {
            it.numberValue shouldBe 0.0
            it.compatibilityDiagnostic shouldContain "normalizes"
            it.compatibilityDiagnostic shouldContain "1"
        }
        ThemeComponentStyles.updateNumber(source, "composition.line_spacing_multiplier", 0.0)
            .let { ThemeComponentStyles.read(it, "composition.line_spacing_multiplier").numberValue shouldBe 0.0 }
    }

    "preserves unknown composition enums while normalizing preview semantics" {
        ThemeComponentStyles.read("composition.position = \"future\"\n", "composition.position").let {
            it.stringValue shouldBe "future"
            it.compatibilityDiagnostic shouldBe "Unknown composition.position is preserved and previewed as fixed"
        }
        ThemeComponentStyles.read("composition.position = \"LEFT_UP\"\n", "composition.position").compatibilityDiagnostic shouldBe null
        ThemeComponentStyles.read("composition.movable = \"future\"\n", "composition.movable").let {
            it.stringValue shouldBe "future"
            it.compatibilityDiagnostic shouldContain "runtime treats every string except false as movable true"
        }
    }

    "reports static nested table presence without executing Lua" {
        val source = """
            key = { pressed = { background = 0xff000001 } }
            composition = { key = { hint = {} } }
        """.trimIndent() + "\n"
        ThemeComponentStyles.staticTablePresence(source, "composition.key") shouldBe true
        ThemeComponentStyles.staticTablePresence(source, "composition.key.pressed") shouldBe false
        ThemeComponentStyles.staticTablePresence(source, "composition.key.hint") shouldBe true
        ThemeComponentStyles.staticTablePresence("composition = make_style()\n", "composition.key") shouldBe null
    }

    "does not warn for missing composition enum defaults" {
        ThemeComponentStyles.read("composition = {}\n", "composition.position").compatibilityDiagnostic shouldBe null
        ThemeComponentStyles.read("composition = {}\n", "composition.movable").compatibilityDiagnostic shouldBe null
    }

    "keeps composition window source-only" {
        val source = "composition = { window = {} }\n"
        shouldThrow<IllegalArgumentException> { ThemeComponentStyles.read(source, "composition.window") }
        shouldThrow<IllegalArgumentException> {
            ThemeComponentStyles.update(source, "composition.window", ThemeValue.LuaString("unsafe"))
        }
        shouldThrow<IllegalArgumentException> { ThemeComponentStyles.updateNumber(source, "candidate.unlisted", 1.0) }
    }

    "registry includes representative visual leaves from every component" {
        val paths = ThemeComponentStyles.supportedPaths()
        listOf(
            "candidate.key.pressed.translation_z",
            "candidate.expanded.comment.pressed.text_size",
            "toolbar.hide.pressed.shadow_color",
            "symbol.text.pressed.scale_x",
            "symbol.tool_bar.pressed.background",
            "clipboard.key.pressed.translation_y",
            "clipboard.item.pressed.translation_y",
            "clipboard.tool_bar.height",
            "preedit.inline",
            "composition.all_phrases",
            "composition.pressed.text_color",
            "composition.key.hint.text_color",
            "composition.key.pressed.hint.text_color",
            "key.pressed.hint.text_size",
        ).forEach { paths shouldContain it }
    }

    "blocks inline-table normalization when a sibling is Raw Lua" {
        val source = "composition = { line_spacing_multiplier = 1.2, window = make_window() }\n"
        shouldThrow<IllegalArgumentException> {
            ThemeComponentStyles.updateNumber(source, "composition.line_spacing_multiplier", 1.0)
        }
        shouldThrow<IllegalArgumentException> {
            ThemeComponentStyles.remove(source, "composition.line_spacing_multiplier")
        }
        source shouldContain "window = make_window()"
    }

})
