/*
 * SPDX-FileCopyrightText: 2025 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.editor.core

/** A deliberately conservative Lua reader for literal theme tables. */
class ThemeLuaParser {
    fun parse(source: String): ParseResult {
        val nodes = ArrayList<ThemeNode>()
        val diagnostics = ArrayList<ThemeDiagnostic>()
        var document = ThemeDocument(emptyList(), source.endsWith("\n"))
        for ((statement, line) in splitStatements(source)) {
            val text = statement.trim()
            if (text.isEmpty()) continue
            val assignment = ASSIGNMENT.matchEntire(text)
            if (assignment == null) {
                nodes += ThemeNode(text, line, ThemeValue.RawLuaNode(statement, line))
                diagnostics += ThemeDiagnostic(line, 1, Severity.INFO, "Preserved unsupported Lua statement")
                continue
            }
            val path = assignment.groupValues[1]
            val value = parseValue(stripComment(assignment.groupValues[2].trim()), line, diagnostics)
            if (path.contains('.')) {
                document = document.set(path, value)
            } else {
                nodes += ThemeNode(path, line, value)
                document = document.copy(nodes = nodes.toList())
            }
        }
        return ParseResult(document, diagnostics)
    }

    private fun parseValue(text: String, line: Int, diagnostics: MutableList<ThemeDiagnostic>): ThemeValue {
        when (text) {
            "true" -> return ThemeValue.LuaBoolean(true)
            "false" -> return ThemeValue.LuaBoolean(false)
            "nil" -> return ThemeValue.LuaNil
        }
        if (text.startsWith("{") && text.endsWith("}")) {
            return parseTable(text.substring(1, text.length - 1), line, diagnostics)
        }
        if (text.length >= 2 && ((text.first() == '\'' && text.last() == '\'') || (text.first() == '"' && text.last() == '"'))) {
            return ThemeValue.LuaString(unescape(text.substring(1, text.length - 1)))
        }
        parseNumber(text)?.let { return ThemeValue.LuaNumber(it) }
        diagnostics += ThemeDiagnostic(line, 1, Severity.WARNING, "Unsupported Lua expression preserved as raw text")
        return ThemeValue.RawLuaNode(text, line)
    }

    private fun parseTable(body: String, line: Int, diagnostics: MutableList<ThemeDiagnostic>): ThemeValue.LuaTable {
        val fields = LinkedHashMap<String, ThemeValue>()
        var arrayIndex = 1
        for (entry in splitTopLevel(body, ',')) {
            val item = stripComment(entry.trim())
            if (item.isEmpty()) continue
            val equals = findTopLevelEquals(item)
            if (equals < 0) {
                fields["#${arrayIndex++}"] = parseValue(item, line, diagnostics)
                continue
            }
            val keyText = item.substring(0, equals).trim()
            val key = when {
                keyText.startsWith("[") && keyText.endsWith("]") -> keyText.substring(1, keyText.length - 1).trim().removeSurrounding("\"").removeSurrounding("'")
                else -> keyText
            }
            if (!IDENTIFIER.matches(key) && !key.startsWith("#")) {
                diagnostics += ThemeDiagnostic(line, 1, Severity.WARNING, "Unsupported table key: $key")
                continue
            }
            fields[key] = parseValue(item.substring(equals + 1).trim(), line, diagnostics)
        }
        return ThemeValue.LuaTable(fields)
    }

    private fun splitStatements(source: String): List<Pair<String, Int>> {
        val result = ArrayList<Pair<String, Int>>()
        var start = 0
        var line = 1
        var statementLine = 1
        var depth = 0
        var quote = '\u0000'
        var i = 0
        while (i < source.length) {
            val c = source[i]
            if (quote != '\u0000') {
                if (c == '\\') i++ else if (c == quote) quote = '\u0000'
            } else if (c == '-' && i + 1 < source.length && source[i + 1] == '-') {
                while (i < source.length && source[i] != '\n') i++
                continue
            } else {
                when (c) {
                    '\'', '"' -> quote = c
                    '{', '(', '[' -> depth++
                    '}', ')', ']' -> depth = (depth - 1).coerceAtLeast(0)
                    '\n' -> {
                        line++
                        if (depth == 0) {
                            result += source.substring(start, i) to statementLine
                            start = i + 1
                            statementLine = line
                        }
                    }
                    ';' -> if (depth == 0) {
                        result += source.substring(start, i) to statementLine
                        start = i + 1
                        statementLine = line
                    }
                }
            }
            i++
        }
        if (start < source.length) result += source.substring(start) to statementLine
        return result
    }

    private fun splitTopLevel(source: String, delimiter: Char): List<String> {
        val result = ArrayList<String>()
        var start = 0
        var depth = 0
        var quote = '\u0000'
        var i = 0
        while (i < source.length) {
            val c = source[i]
            if (quote != '\u0000') {
                if (c == '\\') i++ else if (c == quote) quote = '\u0000'
            } else {
                when (c) {
                    '\'', '"' -> quote = c
                    '{', '(', '[' -> depth++
                    '}', ')', ']' -> depth = (depth - 1).coerceAtLeast(0)
                    delimiter -> if (depth == 0) {
                        result += source.substring(start, i)
                        start = i + 1
                    }
                }
            }
            i++
        }
        result += source.substring(start)
        return result
    }

    private fun findTopLevelEquals(source: String): Int {
        var depth = 0
        var quote = '\u0000'
        var i = 0
        while (i < source.length) {
            val c = source[i]
            if (quote != '\u0000') {
                if (c == '\\') i++ else if (c == quote) quote = '\u0000'
            } else {
                when (c) {
                    '\'', '"' -> quote = c
                    '{', '(', '[' -> depth++
                    '}', ')', ']' -> depth = (depth - 1).coerceAtLeast(0)
                    '=' -> if (depth == 0) return i
                }
            }
            i++
        }
        return -1
    }

    private fun stripComment(source: String): String {
        var quote = '\u0000'
        var i = 0
        while (i + 1 < source.length) {
            val c = source[i]
            if (quote != '\u0000') {
                if (c == '\\') i++ else if (c == quote) quote = '\u0000'
            } else if (c == '\'' || c == '"') {
                quote = c
            } else if (c == '-' && source[i + 1] == '-') {
                return source.substring(0, i).trimEnd()
            }
            i++
        }
        return source.trim()
    }

    private fun parseNumber(text: String): Double? {
        return when {
            text.startsWith("0x", true) -> text.substring(2).toLongOrNull(16)?.toDouble()
            else -> text.toDoubleOrNull()
        }
    }

    private fun unescape(value: String): String = value
        .replace("\\\"", "\"")
        .replace("\\'", "'")
        .replace("\\\\", "\\")

    companion object {
        private val ASSIGNMENT = Regex("^([A-Za-z_][A-Za-z0-9_.]*)\\s*=\\s*(.+)$", RegexOption.DOT_MATCHES_ALL)
        private val IDENTIFIER = Regex("^[A-Za-z_][A-Za-z0-9_]*$")
    }
}

data class ParseResult(val document: ThemeDocument, val diagnostics: List<ThemeDiagnostic>)
