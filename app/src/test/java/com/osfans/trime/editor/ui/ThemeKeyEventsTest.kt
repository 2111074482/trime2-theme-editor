/*
 * SPDX-FileCopyrightText: 2025 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.osfans.trime.editor.ui

import com.osfans.trime.editor.core.ThemeLuaParser
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain

class ThemeKeyEventsTest : StringSpec({
    "classifies string inline full-key and raw event sources" {
        val source = "keys = { { click = \"Preset\", long_click = { send = \"a\" }, ascii = { click = \"b\", label = \"B\" }, paging = make_event() } }\n"
        val document = ThemeLuaParser().parse(source).document
        val slots = ThemeKeyEvents.read(document, "keys.#1").associateBy { it.name }
        slots["click"]!!.source shouldBe ThemeKeyEvents.Source.STRING
        slots["long_click"]!!.source shouldBe ThemeKeyEvents.Source.INLINE_EVENT
        slots["ascii"]!!.source shouldBe ThemeKeyEvents.Source.FULL_KEY_REPLACEMENT
        slots["paging"]!!.source shouldBe ThemeKeyEvents.Source.RAW_LUA
        slots["combo"]!!.source shouldBe ThemeKeyEvents.Source.MISSING
    }

    "updates string inline events and preserves verification" {
        var document = ThemeLuaParser().parse("keys = { { click = \"a\" } }\n").document
        document = ThemeKeyEvents.updateString(document, "keys.#1", "composing", "CommitRawInput")
        document = ThemeKeyEvents.updateInline(document, "keys.#1", "long_click", ThemePresetEvents.Event("long_click", label = "L", command = "tool.lua"))
        val source = ThemeKeyEvents.verifiedSource(document)
        source shouldContain "composing = \"CommitRawInput\""
        source shouldContain "command = \"tool.lua\""
    }

    "preserves missing event flags with nullable updates" {
        var document = ThemeLuaParser().parse("keys = { { click = \"a\" } }\n").document
        ThemeKeyEvents.options(document, "keys.#1") shouldBe ThemeKeyEvents.Options(null, null)
        document = ThemeKeyEvents.updateOptions(document, "keys.#1", true, false)
        ThemeKeyEvents.options(document, "keys.#1") shouldBe ThemeKeyEvents.Options(true, false)
        document = ThemeKeyEvents.updateOptions(document, "keys.#1", null, null)
        ThemeKeyEvents.options(document, "keys.#1") shouldBe ThemeKeyEvents.Options(null, null)
    }

    "blocks invalid option field types" {
        val document = ThemeLuaParser().parse("keys = { { click = \"a\", send_bindings = \"yes\" } }\n").document
        shouldThrow<IllegalArgumentException> { ThemeKeyEvents.options(document, "keys.#1") }
        shouldThrow<IllegalArgumentException> { ThemeKeyEvents.updateOptions(document, "keys.#1", null, true) }
    }

    "expands shorthand keys without losing their click event" {
        var document = ThemeLuaParser().parse("keys = { \"a\" }\n").document
        document = ThemeKeyEvents.updateString(document, "keys.#1", "long_click", "b")
        val source = ThemeKeyEvents.verifiedSource(document)
        source shouldContain "click = \"a\""
        source shouldContain "long_click = \"b\""
    }

    "preserves missing versus explicit-empty event hints" {
        var document = ThemeLuaParser().parse("keys = { { click = \"a\", hint_long = \"\" } }\n").document
        val hints = ThemeKeyEvents.hints(document, "keys.#1")
        hints.values["hint_long"] shouldBe ""
        hints.values["hint_left"] shouldBe null
        document = ThemeKeyEvents.updateHints(document, "keys.#1", mapOf("hint_long" to null, "hint_left" to "L"))
        val updated = ThemeKeyEvents.hints(document, "keys.#1")
        updated.values["hint_long"] shouldBe null
        updated.values["hint_left"] shouldBe "L"
    }

    "blocks structural replacement of full ascii keys and Raw Lua" {
        val full = ThemeLuaParser().parse("keys = { { ascii = { click = \"a\" } } }\n").document
        shouldThrow<IllegalArgumentException> { ThemeKeyEvents.updateString(full, "keys.#1", "ascii", "Preset") }
        val raw = ThemeLuaParser().parse("keys = { { paging = make_event() } }\n").document
        shouldThrow<IllegalArgumentException> { ThemeKeyEvents.updateString(raw, "keys.#1", "paging", "Preset") }
    }

    "allows only string state replacements used by the current runtime" {
        val document = ThemeLuaParser().parse("keys = { { composing = \"CommitRawInput\" } }\n").document
        shouldThrow<IllegalArgumentException> {
            ThemeKeyEvents.updateInline(document, "keys.#1", "composing", ThemePresetEvents.Event("composing", send = "a"))
        }
        val ignoredTable = ThemeLuaParser().parse("keys = { { paging = { send = \"Page_Down\" } } }\n").document
        ThemeKeyEvents.read(ignoredTable, "keys.#1").associateBy { it.name }["paging"]!!.source shouldBe ThemeKeyEvents.Source.RAW_LUA
        shouldThrow<IllegalArgumentException> {
            ThemeKeyEvents.updateString(ignoredTable, "keys.#1", "paging", "Page_Down")
        }
    }


    "reports the effective send bindings runtime default" {
        val plain = ThemeLuaParser().parse("keys = { { click = \"a\" } }\n").document
        ThemeKeyEvents.options(plain, "keys.#1").effectiveSendBindings shouldBe false
        val conditional = ThemeLuaParser().parse("keys = { { click = \"a\", composing = \"CommitRawInput\" } }\n").document
        val options = ThemeKeyEvents.options(conditional, "keys.#1")
        options.effectiveSendBindings shouldBe true
        options.sendBindingsSource shouldBe "runtime default: conditional event present"
        val empty = ThemeLuaParser().parse("keys = { { composing = \"\" } }\n").document
        ThemeKeyEvents.options(empty, "keys.#1").effectiveSendBindings shouldBe false
        val dynamic = ThemeLuaParser().parse("keys = { { composing = make_event() } }\n").document
        ThemeKeyEvents.options(dynamic, "keys.#1").sendBindingsSource shouldBe "uncertain: conditional source is not a runtime string"
    }

})
