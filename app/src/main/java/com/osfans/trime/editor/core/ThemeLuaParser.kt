/*
 * SPDX-FileCopyrightText: 2025 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.editor.core

class ThemeLuaParser {
    fun parse(source: String): ParseResult {
        val nodes = ArrayList<ThemeNode>()
        val diagnostics = ArrayList<ThemeDiagnostic>()
        val statements = splitStatements(source)
        for ((text, line) in statements) {
            val trimmed = text.trim()
            if (trimmed.isEmpty() || trimmed.startsWith("--")) {
                nodes.add(ThemeNode(trimmed, line, ThemeValue.RawLuaNode(text, line)))
                continue
            }
            val match = ASSIGNMENT.matchEntire(trimmed)
            if (match == null) {
                nodes.add(ThemeNode(trimmed, line, ThemeValue.RawLuaNode(text, line)))
                diagnostics.add(ThemeDiagnostic(line, 1, Severity.INFO, "Preserved unsupported Lua statement"))
                continue
            }
            val name = match.groupValues[1]
            val parsed = parseValue(match.groupValues[2].trim(), line, diagnostics)
            nodes.add(ThemeNode(name, line, parsed))
        }
        return ParseResult(ThemeDocument(nodes, source.endsWith("\n")), diagnostics)
    }

    private fun parseValue(text: String, line: Int, diagnostics: MutableList<ThemeDiagnostic>): ThemeValue {
        if (text == "true") return ThemeValue.LuaBoolean(true)
        if (text == "false") return ThemeValue.LuaBoolean(false)
        if (text == "nil") return ThemeValue.LuaNil
        if (text.startsWith("{") && text.endsWith("}")) return parseTable(text.substring(1, text.length - 1), line, diagnostics)
        if (text.length >= 2 && text.first() == '"' && text.last() == '"') {
            return ThemeValue.LuaString(unescape(text.substring(1, text.length - 1)))
        }
        text.toDoubleOrNull()?.let { return ThemeValue.LuaNumber(it) }
        diagnostics.add(ThemeDiagnostic(line, 1, Severity.WARNING, "Unsupported Lua expression preserved as raw text"))
        return ThemeValue.RawLuaNode(text, line)
    }

    private fun parseTable(body: String, line: Int, diagnostics: MutableList<ThemeDiagnostic>): ThemeValue.LuaTable {
        val fields = LinkedHashMap<String, ThemeValue>()
        for (entry in splitTopLevel(body, ',')) {
            val item = entry.trim()
            if (item.isEmpty() || item.startsWith("--")) continue
            val equals = findTopLevelEquals(item)
            if (equals < 1) {
                val key = "#" + (fields.keys.count { it.startsWith("#") } + 1)
                fields[key] = parseValue(item, line, diagnostics)
                continue
            }
            val key = item.substring(0, equals).trim().removeSurrounding("[", "]").removeSurrounding("\"", "\"")
            if (!IDENTIFIER.matches(key)) {
                diagnostics.add(ThemeDiagnostic(line, 1, Severity.WARNING, "Unsupported table key: $key"))
                continue
            }
            fields[key] = parseValue(item.substring(equals + 1).trim(), line, diagnostics)
        }
        return ThemeValue.LuaTable(fields)
    }

    private fun splitStatements(source: String): List<Pair<String, Int>> {
        val result = ArrayList<Pair<String, Int>>()
        var start = 0; var line = 1; var statementLine = 1; var depth = 0; var quote = '\u0000'; var i = 0
        while (i < source.length) {
            val c = source[i]
            if (quote != '\u0000') { if (c == '\\') i++; else if (c == quote) quote = '\u0000' }
            else when (c) {
                '\'', '"' -> quote = c
                '{', '(', '[' -> depth++
                '}', ')', ']' -> depth--
                '\n' -> { line++; if (depth == 0) { result.add(source.substring(start, i) to statementLine); start = i + 1; statementLine = line } }
                ';' -> if (depth == 0) { result.add(source.substring(start, i) to statementLine); start = i + 1; statementLine = line }
            }
            i++
        }
        if (start < source.length) result.add(source.substring(start) to statementLine)
        return result
    }

    private fun splitTopLevel(source: String, delimiter: Char): List<String> {
        val result = ArrayList<String>(); var start = 0; var depth = 0; var quote = '\u0000'
        source.forEachIndexed { index, c ->
            if (quote != '\u0000') { if (c == quote && (index == 0 || source[index - 1] != '\\')) quote = '\u0000' }
            else when (c) { '\'', '"' -> quote = c; '{', '(', '[' -> depth++; '}', ')', ']' -> depth--; delimiter -> if (depth == 0) { result.add(source.substring(start, index)); start = index + 1 } }
        }
        result.add(source.substring(start)); return result
    }

    private fun findTopLevelEquals(source: String): Int = splitTopLevel(source, '=').let { if (it.size == 2) it[0].length else -1 }
    private fun unescape(value: String) = value.replace("\\\"", "\"").replace("\\\\", "\\")
    companion object { private val ASSIGNMENT = Regex("^([A-Za-z_][A-Za-z0-9_.]*)\\s*=\\s*(.+)$"); private val IDENTIFIER = Regex("^[A-Za-z_][A-Za-z0-9_]*$") }
}

data class ParseResult(val document: ThemeDocument, val diagnostics: List<ThemeDiagnostic>)
