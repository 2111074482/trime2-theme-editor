/*
 * SPDX-FileCopyrightText: 2025 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.editor.ui

import com.osfans.trime.editor.core.ThemeLuaParser
import com.osfans.trime.editor.core.ThemeLuaWriter
import com.osfans.trime.editor.core.ThemeValue
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain

class ThemeLayoutCodecTest : StringSpec({
    "uses Trime layout priority and preserves inactive roots" {
        val document = ThemeLuaParser().parse("rows = { { keys = { { click = 'a' } } } }\nkeys = { { click = 'b', x = 4 } }\n").document
        val model = ThemeLayoutCodec.fromDocument(document)
        model.layoutMode shouldBe ThemeEditorModel.LayoutMode.ROWS
        model.keys.first().label shouldBe "a"
        ThemeLayoutCodec.write(document, model).get("keys") shouldBe document.get("keys")
    }

    "updates flex fields while preserving unknown container data" {
        val source = "flex_box = { direction = 'row', custom = 7, { direction = 'column', keys = { { click = 'a', swipe_up = 'b' } } } }\n"
        val document = ThemeLuaParser().parse(source).document
        val model = ThemeLayoutCodec.fromDocument(document)
        model.flexContainers.first().direction = "column"
        val written = ThemeLayoutCodec.write(document, model)
        written.get("flex_box.custom") shouldBe ThemeValue.LuaNumber(7.0)
        ThemeLuaWriter.write(written) shouldContain "swipe_up = \"b\""
    }

    "writes absolute coordinates" {
        val document = ThemeLuaParser().parse("keys = { { click = 'a', x = 1, y = 2, width = 10, height = 12 } }\n").document
        val model = ThemeLayoutCodec.fromDocument(document)
        model.keys.first().x = 25f
        val written = ThemeLayoutCodec.write(document, model)
        written.get("keys.#1.x") shouldBe ThemeValue.LuaNumber(25.0)
    }

    "keeps string symbol keys as strings" {
        val document = ThemeLuaParser().parse("key_maps = { { name = 'Common', keys = { ',', '.', '?' } } }\n").document
        val model = ThemeLayoutCodec.fromDocument(document)
        model.keys.first().label = ";"
        val written = ThemeLayoutCodec.write(document, model)
        written.get("key_maps.#1.keys.#1") shouldBe ThemeValue.LuaString(";")
    }
    "reorders symbol pages using stable source templates" {
        val document = ThemeLuaParser().parse("key_maps = { { name = 'A', custom = 1, keys = { 'a' } }, { name = 'B', custom = 2, keys = { 'b' } } }\n").document
        val model = ThemeLayoutCodec.fromDocument(document)
        java.util.Collections.swap(model.keyMapPages, 0, 1)
        val written = ThemeLayoutCodec.write(document, model)
        written.get("key_maps.#1.custom") shouldBe ThemeValue.LuaNumber(2.0)
        written.get("key_maps.#2.custom") shouldBe ThemeValue.LuaNumber(1.0)
    }

    "does not materialize inherited key fields during unchanged write" {
        val document = ThemeLuaParser().parse("rows = { { keys = { { click = 'a' }, { width = 5 } } } }\n").document
        val model = ThemeLayoutCodec.fromDocument(document)
        val written = ThemeLayoutCodec.write(document, model)
        written.get("rows.#1.keys.#1.width") shouldBe null
        written.get("rows.#1.keys.#1.height") shouldBe null
        written.get("rows.#1.keys.#2.click") shouldBe null
    }

    "writes a click value for a newly added key" {
        val document = ThemeLuaParser().parse("rows = { { keys = { { click = 'a' } } } }\n").document
        val model = ThemeLayoutCodec.fromDocument(document)
        val key = ThemeEditorModel.Key("new", "b", 10f, 10f, 10f, 16f)
        key.ownerId = model.rows.first().id
        model.keys += key
        val written = ThemeLayoutCodec.write(document, model)
        written.get("rows.#1.keys.#2.click") shouldBe ThemeValue.LuaString("b")
    }

    "parses keys directly on a flex root" {
        val document = ThemeLuaParser().parse("flex_box = { direction = 'row', keys = { { click = 'a' }, { click = 'b' } } }\n").document
        val model = ThemeLayoutCodec.fromDocument(document)
        model.keys.size shouldBe 2
        model.flexContainers.first().keyIds.size shouldBe 2
    }

    "edits literal key events without losing unsupported fields" {
        val document = ThemeLuaParser().parse("rows = { { keys = { { click = 'a', long_click = 'A', custom_event = build_event() } } } }\n").document
        val model = ThemeLayoutCodec.fromDocument(document)
        model.keys.first().longClick = "B"
        model.keys.first().swipeUp = "Up"
        val written = ThemeLayoutCodec.write(document, model)
        written.get("rows.#1.keys.#1.long_click") shouldBe ThemeValue.LuaString("B")
        written.get("rows.#1.keys.#1.swipe_up") shouldBe ThemeValue.LuaString("Up")
        written.get("rows.#1.keys.#1.custom_event") shouldBe ThemeValue.RawLuaNode("build_event()", 1)
    }

    "converts a string key to a table when adding an independent label" {
        val document = ThemeLuaParser().parse("key_maps = { { name = 'A', keys = { 'action' } } }\n").document
        val model = ThemeLayoutCodec.fromDocument(document)
        model.keys.first().label = "Label"
        val written = ThemeLayoutCodec.write(document, model)
        written.get("key_maps.#1.keys.#1.click") shouldBe ThemeValue.LuaString("action")
        written.get("key_maps.#1.keys.#1.label") shouldBe ThemeValue.LuaString("Label")
    }

    "inherits top level row dimensions without materializing them" {
        val document = ThemeLuaParser().parse("key_width = 9\nkey_height = 21\nrows = { { keys = { { click = 'a' } } } }\n").document
        val model = ThemeLayoutCodec.fromDocument(document)
        model.rows.first().height shouldBe 21f
        model.keys.first().width shouldBe 9f
        val written = ThemeLayoutCodec.write(document, model)
        written.get("rows.#1.height") shouldBe null
        written.get("rows.#1.keys.#1.width") shouldBe null
    }

    "reverts repeated edits against the original source snapshot" {
        val document = ThemeLuaParser().parse("rows = { { keys = { { click = 'a' } } } }\n").document
        val changedModel = ThemeLayoutCodec.fromDocument(document).also { it.keys.first().click = "b" }
        val changed = ThemeLayoutCodec.writeAgainstOriginal(document, changedModel)
        changed.get("rows.#1.keys.#1.click") shouldBe ThemeValue.LuaString("b")

        val originalModel = ThemeLayoutCodec.fromDocument(document)
        val withOtherRoot = changed.set("name", ThemeValue.LuaString("edited"))
        val reverted = ThemeLayoutCodec.writeAgainstOriginal(withOtherRoot, originalModel)
        reverted.get("rows.#1.keys.#1.click") shouldBe ThemeValue.LuaString("a")
        reverted.get("name") shouldBe ThemeValue.LuaString("edited")
    }

})
