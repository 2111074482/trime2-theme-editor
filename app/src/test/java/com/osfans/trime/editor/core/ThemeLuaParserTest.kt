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
    "parses array entries after comments without stripping comment markers in strings" {
        val source = """flex_box = {
  direction = "row",
  -- first column
  { direction = "column", keys = { { click = "--" }, { click = "a" } } },
}
"""
        val flex = ThemeLuaParser().parse(source).document.get("flex_box") as ThemeValue.LuaTable
        val child = flex.fields["#1"] as ThemeValue.LuaTable
        val keys = child.fields["keys"] as ThemeValue.LuaTable
        (keys.fields["#1"] as ThemeValue.LuaTable).fields["click"] shouldBe ThemeValue.LuaString("--")
        keys.fields.size shouldBe 2
    }

    "keeps dotted assignments across later roots and trailing raw statements" {
        val source = "key = { text_color = 1 }\nkey.hint = { text_size = 12 }\ncandidate = { height = 48 }\nreturn custom_view\n"
        val document = ThemeLuaParser().parse(source).document
        document.get("key.hint.text_size") shouldBe ThemeValue.LuaNumber(12.0)
        document.get("candidate.height") shouldBe ThemeValue.LuaNumber(48.0)
        (document.nodes.last().value as ThemeValue.RawLuaNode).source.trim() shouldBe "return custom_view"
    }

    "reports unclosed strings and tables as errors" {
        val result = ThemeLuaParser().parse("rows = { { click = 'a }\n")
        result.diagnostics.any { it.severity == Severity.ERROR } shouldBe true
    }

    "hybrid writer preserves unchanged source byte for byte" {
        val source = "-- header\nstyle = 'light' -- keep quote\nkeyboard = get_keyboard(id)\n"
        val document = ThemeLuaParser().parse(source).document
        ThemeLuaWriter.write(document) shouldBe source
        ThemeLuaWriter.write(document, ThemeWriteMode.PRESERVE) shouldBe source
    }

    "hybrid writer rewrites only changed root" {
        val source = "-- header\nstyle = 'light' -- untouched\nkey = { text_size = 18, custom = call() }\n-- tail\ncandidate = { height = 40 }\n"
        val document = ThemeLuaParser().parse(source).document
        val updated = document.set("candidate.height", ThemeValue.LuaNumber(48.0))
        val written = ThemeLuaWriter.write(updated)
        written shouldContain "style = 'light' -- untouched"
        written shouldContain "custom = call()"
        written shouldContain "candidate = {"
        written shouldContain "height = 48"
    }

    "structured writer keeps assignment for raw expression" {
        val document = ThemeLuaParser().parse("keyboard = get_keyboard(id)\n").document
        ThemeLuaWriter.write(document, ThemeWriteMode.STRUCTURED) shouldBe "keyboard = get_keyboard(id)\n"
    }

    "preserve mode rejects structural changes" {
        val document = ThemeLuaParser().parse("candidate = { height = 40 }\n").document
            .set("candidate.height", ThemeValue.LuaNumber(48.0))
        var failed = false
        try { ThemeLuaWriter.write(document, ThemeWriteMode.PRESERVE) } catch (_: IllegalArgumentException) { failed = true }
        failed shouldBe true
    }

    "preserves complete Lua function blocks without parsing their internals" {
        val source = "name = 'demo'\nfunction get_keyboard(id, alphabet)\n  if id == '' then\n    return keyboard\n  end\n  return 'default'\nend\nkeyboard = 'qwerty'\n"
        val result = ThemeLuaParser().parse(source)
        result.document.get("name") shouldBe ThemeValue.LuaString("demo")
        result.document.get("keyboard") shouldBe ThemeValue.LuaString("qwerty")
        result.document.get("id") shouldBe null
        result.document.nodes.any { !it.assignment && (it.value as? ThemeValue.RawLuaNode)?.source?.contains("function get_keyboard") == true } shouldBe true
        ThemeLuaWriter.write(result.document) shouldBe source
    }

    "editor refuses to overwrite a nested raw inheritance expression" {
        val editor = ThemeEditor(ThemeLuaParser().parse("popup = { key = table.clone(base) }\n").document)
        val diagnostics = editor.set("popup.key.text_size", ThemeValue.LuaNumber(18.0))
        diagnostics.any { it.severity == Severity.ERROR } shouldBe true
        editor.document.get("popup.key") shouldBe ThemeValue.RawLuaNode("table.clone(base)", 1)
    }

    "supports Lua long strings and comments without false delimiter errors" {
        val source = "text = [=[function fake() { -- not code\nend]=]\n--[=[ comment with 'quotes' and { brackets } ]=]\nstyle = 'light'\n"
        val result = ThemeLuaParser().parse(source)
        result.diagnostics.none { it.severity == Severity.ERROR } shouldBe true
        result.document.get("style") shouldBe ThemeValue.LuaString("light")
        ThemeLuaWriter.write(result.document) shouldBe source
    }

    "reports an unclosed Lua long bracket" {
        val result = ThemeLuaParser().parse("text = [=[not closed\n")
        result.diagnostics.any { it.severity == Severity.ERROR } shouldBe true
    }

    "refuses structured edits when a root has duplicate assignments" {
        val editor = ThemeEditor(ThemeLuaParser().parse("candidate = { height = 40 }\ncandidate = { height = 42 }\n").document)
        editor.set("candidate.height", ThemeValue.LuaNumber(48.0)).any { it.severity == Severity.ERROR } shouldBe true
    }

    "keeps a multiline long string assignment as one raw value" {
        val source = "text = [=[first line\nfunction fake()\nend\nlast line]=]\nstyle = 'light'\n"
        val result = ThemeLuaParser().parse(source)
        result.document.get("text") shouldBe ThemeValue.RawLuaNode("[=[first line\nfunction fake()\nend\nlast line]=]", 1)
        result.document.get("style") shouldBe ThemeValue.LuaString("light")
        ThemeLuaWriter.write(result.document) shouldBe source
    }

    "allows structured edits across distinct dotted assignments" {
        val editor = ThemeEditor(ThemeLuaParser().parse("key = { text_color = 1 }\nkey.hint = { text_size = 12 }\n").document)
        editor.set("key.text_color", ThemeValue.LuaNumber(2.0)).none { it.severity == Severity.ERROR } shouldBe true
        ThemeLuaWriter.write(editor.document) shouldContain "text_size = 12"
    }

    "does not treat long bracket markers inside quoted strings as Lua long strings" {
        val source = "text = 'literal [[ marker without close'\nstyle = 'light'\n"
        val result = ThemeLuaParser().parse(source)
        result.diagnostics.none { it.severity == Severity.ERROR } shouldBe true
        result.document.get("style") shouldBe ThemeValue.LuaString("light")
        ThemeLuaWriter.write(result.document) shouldBe source
    }

})
