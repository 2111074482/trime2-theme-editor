/*
 * SPDX-FileCopyrightText: 2025 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.osfans.trime.editor.ui

import com.osfans.trime.editor.core.ThemeLuaParser
import com.osfans.trime.editor.core.ThemeLuaWriter
import io.kotest.core.spec.style.StringSpec
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain

class ThemeLayoutMigrationTest : StringSpec({
    "converts rows to absolute keys and removes the higher-priority root" {
        val source = ThemeLuaParser().parse("name = 'Keyboard'\nrows = { { keys = { { click = 'a' }, { click = 'b', custom = 7 } } } }\n").document
        val model = ThemeLayoutCodec.fromDocument(source)
        val result = ThemeLayoutMigration.migrate(source, model, ThemeEditorModel.LayoutMode.ABSOLUTE_KEYS)
        result.document.get("rows") shouldBe null
        result.model.layoutMode shouldBe ThemeEditorModel.LayoutMode.ABSOLUTE_KEYS
        result.model.keys.size shouldBe 2
        ThemeLuaWriter.write(result.document) shouldContain "custom = 7"
    }

    "hides all old roots below a non-active editor key" {
        val source = ThemeLuaParser().parse("rows = { { keys = { 'a' } } }\nkeys = { { click = 'b', x = 1, y = 2 } }\n").document
        val model = ThemeLayoutCodec.fromDocument(source)
        val result = ThemeLayoutMigration.migrate(source, model, ThemeEditorModel.LayoutMode.KEY_MAPS, true)
        result.document.get("rows") shouldBe null
        result.document.get("keys") shouldBe null
        result.document.get("_editor_hidden_layouts.rows") shouldBe source.get("rows")
        result.document.get("_editor_hidden_layouts.keys") shouldBe source.get("keys")
        result.model.layoutMode shouldBe ThemeEditorModel.LayoutMode.KEY_MAPS
    }

    "reports omitted non-active key map pages before conversion" {
        val source = ThemeLuaParser().parse("key_maps = { { name = 'A', keys = { 'a' } }, { name = 'B', keys = { 'b' } } }\n").document
        val model = ThemeLayoutCodec.fromDocument(source)
        ThemeLayoutMigration.preview(model, ThemeEditorModel.LayoutMode.ROWS).omittedKeyMapPages shouldBe 1
    }
    "rejects a dynamic inactive layout root instead of changing runtime priority" {
        val source = ThemeLuaParser().parse("rows = make_rows()\nkeys = { { click = 'a', x = 1, y = 2 } }\n").document
        val model = ThemeLayoutCodec.fromDocument(source)
        shouldThrow<IllegalArgumentException> {
            ThemeLayoutMigration.migrate(source, model, ThemeEditorModel.LayoutMode.ROWS)
        }
    }

    "rejects duplicate layout assignments before hybrid write" {
        val source = ThemeLuaParser().parse("keys = { { click = 'a', x = 1, y = 2 } }\nkeys = { { click = 'b', x = 3, y = 4 } }\n").document
        val model = ThemeLayoutCodec.fromDocument(source)
        shouldThrow<IllegalArgumentException> {
            ThemeLayoutMigration.migrate(source, model, ThemeEditorModel.LayoutMode.ROWS)
        }
    }

    "removes absolute coordinates when converting to rows" {
        val source = ThemeLuaParser().parse("keys = { { click = 'a', x = 7, y = 8, custom = 9 } }\n").document
        val model = ThemeLayoutCodec.fromDocument(source)
        val result = ThemeLayoutMigration.migrate(source, model, ThemeEditorModel.LayoutMode.ROWS)
        result.document.get("rows.#1.keys.#1.x") shouldBe null
        result.document.get("rows.#1.keys.#1.y") shouldBe null
        result.document.get("rows.#1.keys.#1.custom") shouldBe source.get("keys.#1.custom")
    }

    "rejects raw values nested inside an otherwise literal layout" {
        val source = ThemeLuaParser().parse("rows = { { keys = { { click = make_event() } } } }\n").document
        val model = ThemeLayoutCodec.fromDocument(source)
        shouldThrow<IllegalArgumentException> {
            ThemeLayoutMigration.migrate(source, model, ThemeEditorModel.LayoutMode.ABSOLUTE_KEYS)
        }
    }

    "retains earlier hidden layouts under a unique archive key" {
        val source = ThemeLuaParser().parse("_editor_hidden_layouts = { rows = { old = true } }\nrows = { { keys = { 'a' } } }\n").document
        val model = ThemeLayoutCodec.fromDocument(source)
        val result = ThemeLayoutMigration.migrate(source, model, ThemeEditorModel.LayoutMode.ABSOLUTE_KEYS, true)
        result.document.get("_editor_hidden_layouts.rows.old") shouldBe source.get("_editor_hidden_layouts.rows.old")
        result.document.get("_editor_hidden_layouts.rows_2") shouldBe source.get("rows")
    }

})
