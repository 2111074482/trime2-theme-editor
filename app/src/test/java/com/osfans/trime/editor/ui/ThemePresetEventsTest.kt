/*
 * SPDX-FileCopyrightText: 2025 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.osfans.trime.editor.ui

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain

class ThemePresetEventsTest : StringSpec({
    "reads and updates complete literal preset fields without executing command" {
        val source = "preset_keys = { Run = { label = \"Run\", send = \"function\", command = \"tool.lua\", option = \"x\", states = { \"off\", \"on\" }, repeatable = true, sticky = true, functional = false, index = 2 } }\n"
        val event = ThemePresetEvents.list(source).single()
        event.command shouldBe "tool.lua"
        event.risky shouldBe true
        event.states shouldBe listOf("off", "on")
        val updated = ThemePresetEvents.put(source, event.copy(label = "Static only", command = "tool.lua"), true)
        updated shouldContain "label = \"Static only\""
        ThemePresetEvents.list(updated).single().command shouldBe "tool.lua"
    }

    "does not inject absent default boolean fields" {
        val source = "preset_keys = { Plain = { label = \"P\", send = \"p\" } }\n"
        val event = ThemePresetEvents.list(source).single()
        val updated = ThemePresetEvents.put(source, event.copy(label = "P2"), true)
        updated.contains("repeatable") shouldBe false
        updated.contains("sticky") shouldBe false
        updated.contains("functional") shouldBe false
    }

    "preserves explicit empty states and rejects non-finite index" {
        val source = "preset_keys = { Toggle = { states = {} } }\n"
        val event = ThemePresetEvents.list(source).single()
        val updated = ThemePresetEvents.put(source, event.copy(label = "T"), true)
        updated shouldContain "states = {}"
        shouldThrow<IllegalArgumentException> { ThemePresetEvents.put(source, event.copy(index = Double.NaN), true) }
        shouldThrow<IllegalArgumentException> { ThemePresetEvents.put(source, event.copy(index = 1.5), true) }
    }

    "reads and updates seven action labels" {
        val source = "action_labels = { none = \"Enter\", send = \"Send\", custom = \"keep\" }\n"
        ThemePresetEvents.actionLabels(source)["none"] shouldBe "Enter"
        val updated = ThemePresetEvents.updateActionLabels(source, mapOf("none" to "Return", "done" to "Done"))
        updated shouldContain "none = \"Return\""
        updated shouldContain "done = \"Done\""
        updated shouldContain "custom = \"keep\""
    }

    "creates copies renames and deletes preset definitions" {
        var source = "preset_keys = { Base = { label = \"B\", send = \"b\" } }\n"
        source = ThemePresetEvents.copy(source, "Base", "Copy")
        ThemePresetEvents.list(source).map { it.id } shouldBe listOf("Base", "Copy")
        source = ThemePresetEvents.renameDefinition(source, "Copy", "Renamed")
        ThemePresetEvents.list(source).map { it.id } shouldBe listOf("Base", "Renamed")
        source = ThemePresetEvents.deleteDefinition(source, "Renamed")
        ThemePresetEvents.list(source).map { it.id } shouldBe listOf("Base")
    }

    "replaces only known event reference positions" {
        val source = "preset_keys = { Old = { send = \"a\" } }\nrows = { { keys = { { click = \"Old\", label = \"Old\", text = \"Old\", composing = \"Old\", popup = { \"Old\" } } } } }\ntoolbar = { keys = { \"Old\" } }\n"
        val update = ThemePresetEvents.replaceReferences(source, "Old", "New")
        update.count shouldBe 4
        update.source shouldContain "click = \"New\""
        update.source shouldContain "composing = \"New\""
        update.source shouldContain "label = \"Old\""
        update.source shouldContain "text = \"Old\""
    }

    "counts and renames shorthand string keys" {
        val source = "rows = { { keys = { \"Old\", { click = \"x\" } } } }\n"
        ThemePresetEvents.references(source, "Old") shouldBe 1
        val updated = ThemePresetEvents.replaceReferences(source, "Old", "New")
        updated.count shouldBe 1
        updated.source shouldContain "\"New\""
    }

    "does not rename unrelated keys arrays" {
        val source = "candidate = { keys = { \"Old\" } }\n"
        ThemePresetEvents.references(source, "Old") shouldBe 0
        ThemePresetEvents.replaceReferences(source, "Old", "New").source shouldBe source
    }

    "does not count preset definition IDs as references" {
        val source = "preset_keys = { Old = { label = \"Old\", send = \"Old\" } }\nrows = { { keys = { { click = \"Old\" } } } }\n"
        ThemePresetEvents.references(source, "Old") shouldBe 1
    }

    "reports Raw Lua references without executing them" {
        val source = "rows = make_rows(Old)\n"
        ThemePresetEvents.hasUncertainReference(source, "Old") shouldBe true
    }

    "blocks dynamic action label roots" {
        shouldThrow<IllegalArgumentException> { ThemePresetEvents.actionLabels("action_labels = make_labels()\n") }
        shouldThrow<IllegalArgumentException> { ThemePresetEvents.updateActionLabels("action_labels = make_labels()\n", mapOf("none" to "Enter")) }
    }

    "blocks unsupported table keys before structural rewrite" {
        val source = "preset_keys = { [\"unsafe-id\"] = { send = \"a\" }, Safe = { send = \"b\" } }\n"
        shouldThrow<IllegalArgumentException> { ThemePresetEvents.put(source, ThemePresetEvents.Event("Safe", send = "c"), true) }
    }

    "blocks dynamic preset roots and dynamic event entries" {
        shouldThrow<IllegalArgumentException> { ThemePresetEvents.put("preset_keys = make_events()\n", ThemePresetEvents.Event("New"), false) }
        val source = "preset_keys = { Dynamic = make_event() }\n"
        shouldThrow<IllegalArgumentException> { ThemePresetEvents.put(source, ThemePresetEvents.Event("Dynamic", label = "x"), true) }
    }

    "removes a managed action label only when the caller supplies null" {
        val source = "action_labels = { none = \"Enter\", send = \"Send\", custom = \"keep\" }\n"
        val updated = ThemePresetEvents.updateActionLabels(source, mapOf("none" to null))
        ThemePresetEvents.actionLabels(updated).containsKey("none") shouldBe false
        ThemePresetEvents.actionLabels(updated)["send"] shouldBe "Send"
        ThemePresetEvents.actionLabels(updated)["custom"] shouldBe "keep"
    }

    "does not treat schema switch arrays as preset references" {
        val source = "toolbar.keys = { { name = \"schema_id\", options = { \"Old\" }, states = { \"Old\" }, reset = 0 }, \"Old\" }\n"
        ThemePresetEvents.references(source, "Old") shouldBe 1
        val update = ThemePresetEvents.replaceReferences(source, "Old", "New")
        update.count shouldBe 1
        update.source.split("\"Old\"").size - 1 shouldBe 2
    }


    "counts preset references in string-only panel toolbars" {
        val source = "candidate = { expanded = { tool_bar = { keys = { \"Old\" } } } }\nsymbol = { tool_bar = { keys = { \"Old\" } } }\nclipboard = { toolbar = { keys = { \"Old\" } } }\n"
        ThemePresetEvents.references(source, "Old") shouldBe 3
        ThemePresetEvents.replaceReferences(source, "Old", "New").count shouldBe 3
    }


    "counts literal values in key swipe tap maps" {
        val source = "rows = { { keys = { { click = \"a\", swipe = { tap_left = \"Old\", tap_right = \"Other\" } } } } }\n"
        ThemePresetEvents.references(source, "Old") shouldBe 1
        val update = ThemePresetEvents.replaceReferences(source, "Old", "New")
        update.count shouldBe 1
        update.source shouldContain "tap_left = \"New\""
    }

})
