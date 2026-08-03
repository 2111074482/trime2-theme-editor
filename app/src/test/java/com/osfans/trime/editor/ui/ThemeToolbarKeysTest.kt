/*
 * SPDX-FileCopyrightText: 2025 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.osfans.trime.editor.ui

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain

class ThemeToolbarKeysTest : StringSpec({
    "classifies strings direct events full keys and schema switches without running Lua" {
        val source = """
            toolbar = { keys = {
              "BackSpace",
              { label = "Paste", send = "paste" },
              { click = "Return", long_click = "Menu", label = "Enter" },
              { name = "ascii_mode", options = { "off", "on" }, states = { "中", "A" }, reset = 0 }
            } }
        """.trimIndent() + "\n"

        val items = ThemeToolbarKeys.list(source)
        items.map { it.source } shouldBe listOf(
            ThemeToolbarKeys.Source.STRING,
            ThemeToolbarKeys.Source.INLINE_EVENT,
            ThemeToolbarKeys.Source.FULL_KEY,
            ThemeToolbarKeys.Source.SCHEMA_SWITCH,
        )
        items[0].literal shouldBe "BackSpace"
        items[1].event?.send shouldBe "paste"
        items[2].event shouldBe null
        items[3].schemaSwitch?.states shouldBe listOf("中", "A")
    }

    "reads clone root followed by effective dotted keys and patches only the dotted statement" {
        val source = """
            toolbar = table.clone(candidate)
            -- keep this explanation
            toolbar.keys = { "Menu" } -- keep keys comment
            unrelated = "same"
        """.trimIndent() + "\n"

        ThemeToolbarKeys.list(source).single().literal shouldBe "Menu"
        val updated = ThemeToolbarKeys.put(source, 0, ThemeToolbarKeys.string("BackSpace"), false)
        updated shouldContain "toolbar = table.clone(candidate)"
        updated shouldContain "-- keep this explanation"
        updated shouldContain "-- keep keys comment"
        updated shouldContain "unrelated = \"same\""
        updated.lines().count { it.trimStart().startsWith("toolbar.keys =") } shouldBe 1
        ThemeToolbarKeys.list(updated).single().literal shouldBe "BackSpace"
    }

    "round trips all schema switch fields and exposes runtime style compatibility warning" {
        val source = "toolbar.keys = { { name = \"schema_group\", options = { \"a\", \"b\" }, states = { \"A\", \"B\" }, reset = -1, style = \"round_key\" } }\n"
        val original = ThemeToolbarKeys.list(source).single()
        original.schemaSwitch shouldBe ThemeToolbarKeys.SchemaSwitch(
            "schema_group", listOf("a", "b"), listOf("A", "B"), -1, "round_key",
        )
        original.compatibilityWarning shouldBe true
        original.schemaSwitch?.compatibilityWarning shouldBe true

        val replacement = ThemeToolbarKeys.schemaSwitch(
            "schema_id", listOf("luna", "terra"), listOf("L", "T"), 1, "compact",
        )
        val updated = ThemeToolbarKeys.put(source, 0, replacement, false)
        val result = ThemeToolbarKeys.list(updated).single().schemaSwitch!!
        result.name shouldBe "schema_id"
        result.options shouldBe listOf("luna", "terra")
        result.states shouldBe listOf("L", "T")
        result.reset shouldBe 1
        result.style shouldBe "compact"
    }

    "preserves unrelated literal toolbar fields and existing inline event fields" {
        val source = """
            -- toolbar heading stays
            toolbar = {
              height = 48,
              custom = "untouched",
              keys = { { label = "Old", send = "a", vendor_hint = "keep" } }
            }
            after = true
        """.trimIndent() + "\n"
        val old = ThemeToolbarKeys.list(source).single().event!!
        val updated = ThemeToolbarKeys.put(
            source, 0, ThemeToolbarKeys.inlineEvent(old.copy(label = "New")), false,
        )
        updated shouldContain "-- toolbar heading stays"
        updated shouldContain "height = 48"
        updated shouldContain "custom = \"untouched\""
        updated shouldContain "vendor_hint = \"keep\""
        updated shouldContain "after = true"
        ThemeToolbarKeys.list(updated).single().event?.label shouldBe "New"
    }

    "supports append edit delete and move for a fully static serializable array" {
        var source = "toolbar.keys = { \"A\", { label = \"B\", send = \"b\" }, { click = \"C\" } }\n"
        source = ThemeToolbarKeys.put(source, 0, ThemeToolbarKeys.string("D"), true)
        ThemeToolbarKeys.list(source).map { it.literal ?: it.event?.label ?: it.source.name } shouldBe
            listOf("A", "B", "FULL_KEY", "D")

        source = ThemeToolbarKeys.put(source, 0, ThemeToolbarKeys.string("A2"), false)
        source = ThemeToolbarKeys.move(source, 3, 1)
        ThemeToolbarKeys.list(source).map { it.literal ?: it.event?.label ?: it.source.name } shouldBe
            listOf("A2", "D", "B", "FULL_KEY")

        source = ThemeToolbarKeys.delete(source, 2)
        ThemeToolbarKeys.list(source).map { it.literal ?: it.source.name } shouldBe
            listOf("A2", "D", "FULL_KEY")
    }

    "requires explicit replacement before overwriting a full key" {
        val source = "toolbar.keys = { { click = \"Return\", label = \"Enter\" } }\n"
        shouldThrow<IllegalArgumentException> {
            ThemeToolbarKeys.put(source, 0, ThemeToolbarKeys.string("Space"), false)
        }
        val updated = ThemeToolbarKeys.replace(source, 0, ThemeToolbarKeys.string("Space"))
        ThemeToolbarKeys.list(updated).single().literal shouldBe "Space"
    }

    "preserves an explicitly transferred full key literal including unknown fields" {
        val donor = ThemeToolbarKeys.list(
            "toolbar.keys = { { click = \"Return\", width = 2, vendor_hint = \"keep\" } }\n",
        ).single()
        val updated = ThemeToolbarKeys.replace("toolbar.keys = { \"Old\" }\n", 0, donor)
        updated shouldContain "click = \"Return\""
        updated shouldContain "width = 2"
        updated shouldContain "vendor_hint = \"keep\""
        ThemeToolbarKeys.list(updated).single().source shouldBe ThemeToolbarKeys.Source.FULL_KEY
    }

    "classifies raw Lua and blocks every structural overwrite" {
        val source = "toolbar.keys = { \"Safe\", make_key(), { send = dynamic_send() } }\n"
        ThemeToolbarKeys.list(source).map { it.source } shouldBe listOf(
            ThemeToolbarKeys.Source.STRING,
            ThemeToolbarKeys.Source.RAW_LUA,
            ThemeToolbarKeys.Source.RAW_LUA,
        )
        shouldThrow<IllegalArgumentException> {
            ThemeToolbarKeys.put(source, 0, ThemeToolbarKeys.string("Changed"), false)
        }
        shouldThrow<IllegalArgumentException> { ThemeToolbarKeys.delete(source, 0) }
        shouldThrow<IllegalArgumentException> { ThemeToolbarKeys.move(source, 0, 1) }
        shouldThrow<IllegalArgumentException> {
            ThemeToolbarKeys.replace(source, 1, ThemeToolbarKeys.string("Changed"))
        }
    }

    "blocks a wholly dynamic array and never calls it" {
        val source = "toolbar.keys = build_toolbar_keys(dangerous_argument)\n"
        ThemeToolbarKeys.list(source).single().source shouldBe ThemeToolbarKeys.Source.RAW_LUA
        shouldThrow<IllegalArgumentException> {
            ThemeToolbarKeys.put(source, 0, ThemeToolbarKeys.string("New"), true)
        }
    }

    "rejects unsupported schema fields identifiers and nonliteral string arrays" {
        shouldThrow<IllegalArgumentException> {
            ThemeToolbarKeys.list("toolbar.keys = { { name = \"ascii_mode\", options = { \"x\" }, states = { \"X\" }, reset = 0, mystery = true } }\n")
        }
        shouldThrow<IllegalArgumentException> {
            ThemeToolbarKeys.list("toolbar.keys = { { [\"not-supported\"] = true } }\n")
        }
        shouldThrow<IllegalArgumentException> {
            ThemeToolbarKeys.list("toolbar.keys = { { name = \"bad-name\", options = { \"x\" }, states = { \"X\" }, reset = 0 } }\n")
        }
        shouldThrow<IllegalArgumentException> {
            ThemeToolbarKeys.schemaSwitch("ascii_mode", listOf("x"), listOf("X"), 0, "bad-style")
        }
        ThemeToolbarKeys.list(
            "toolbar.keys = { { name = \"ascii_mode\", options = { option() }, states = { \"X\" }, reset = 0 } }\n",
        ).single().source shouldBe ThemeToolbarKeys.Source.RAW_LUA
        shouldThrow<IllegalArgumentException> {
            ThemeToolbarKeys.list("toolbar.keys = { { name = \"ascii_mode\", options = { 1 }, states = { \"X\" }, reset = 0 } }\n")
        }
    }

    "honors a later toolbar root over an earlier dotted assignment" {
        val source = "toolbar.keys = { \"old\" }\ntoolbar = { height = 40, keys = { \"effective\" } }\n"
        ThemeToolbarKeys.list(source).single().literal shouldBe "effective"
        val updated = ThemeToolbarKeys.put(source, 0, ThemeToolbarKeys.string("new"), false)
        updated shouldContain "toolbar.keys = { \"old\" }"
        updated shouldContain "height = 40"
        ThemeToolbarKeys.list(updated).single().literal shouldBe "new"
    }

    "blocks an unprovable later dynamic root and duplicate dotted assignments" {
        val laterRoot = "toolbar.keys = { \"safe-looking\" }\ntoolbar = make_toolbar()\n"
        ThemeToolbarKeys.list(laterRoot).single().source shouldBe ThemeToolbarKeys.Source.RAW_LUA
        shouldThrow<IllegalArgumentException> {
            ThemeToolbarKeys.put(laterRoot, 0, ThemeToolbarKeys.string("New"), false)
        }
        shouldThrow<IllegalArgumentException> {
            ThemeToolbarKeys.list("toolbar.keys = { \"A\" }\ntoolbar.keys = { \"B\" }\n")
        }
        shouldThrow<IllegalArgumentException> {
            ThemeToolbarKeys.list("toolbar[\"keys\"] = { \"A\" }\n")
        }
    }

    "validates reset as a signed 32 bit integer" {
        listOf("1.5", "2147483648", "-2147483649").forEach { reset ->
            shouldThrow<IllegalArgumentException> {
                ThemeToolbarKeys.list("toolbar.keys = { { name = \"ascii_mode\", options = { \"x\" }, states = { \"X\" }, reset = $reset } }\n")
            }
        }
        ThemeToolbarKeys.list(
            "toolbar.keys = { { name = \"ascii_mode\", options = { \"x\" }, states = { \"X\" }, reset = -2147483648 } }\n",
        ).single().schemaSwitch?.reset shouldBe Int.MIN_VALUE
    }
})
