/*
 * SPDX-FileCopyrightText: 2025 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.osfans.trime.editor.core

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldContain

class ThemeLuaParserTest : StringSpec({
    "preserves rows array entries and writes them back" {
        val source = """rows = {
  { keys = { { click = "a" }, { click = "b" } } },
}
"""
        val result = ThemeLuaParser().parse(source)
        result.diagnostics.filter { it.severity == Severity.ERROR } shouldBe emptyList()
        val rows = result.document.get("rows") as ThemeValue.LuaTable
        val row = rows.fields["#1"] as ThemeValue.LuaTable
        val keys = row.fields["keys"] as ThemeValue.LuaTable
        keys.fields.keys shouldContain "#1"
        ThemeLuaWriter.write(result.document) shouldContain "click = \"a\""
    }

    "keeps unsupported expressions as raw nodes" {
        val result = ThemeLuaParser().parse("keyboard = get_keyboard(id, alphabet)\n")
        result.document.get("keyboard") shouldBe ThemeValue.RawLuaNode("get_keyboard(id, alphabet)", 1)
    }

    "updates nested fields without changing unrelated roots" {
        val document = ThemeDocument(listOf(
            ThemeNode("style", 1, ThemeValue.LuaString("light")),
            ThemeNode("key", 2, ThemeValue.LuaTable(linkedMapOf("text_size" to ThemeValue.LuaNumber(18.0)))),
        ))
        val updated = document.set("key.text_size", ThemeValue.LuaNumber(20.0))
        updated.get("style") shouldBe ThemeValue.LuaString("light")
        updated.get("key.text_size") shouldBe ThemeValue.LuaNumber(20.0)
    }
})
