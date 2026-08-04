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
        diagnostics += lexicalDiagnostics(source)
        var document = ThemeDocument(emptyList(), source.endsWith("\n"))
        for ((statement, line) in splitStatements(source)) {
            val text = stripComments(statement).trim()
            if (text.isEmpty()) continue
            val assignment = ASSIGNMENT.matchEntire(text)
            if (assignment == null) {
                nodes += ThemeNode(text, line, ThemeValue.RawLuaNode(statement, line), assignment = false)
                document = document.copy(nodes = nodes.toList())
                diagnostics += ThemeDiagnostic(line, 1, Severity.INFO, "不支持的 Lua 语句已按原文保留")
                continue
            }
            val path = assignment.groupValues[1]
            val value = parseValue(assignment.groupValues[2].trim(), line, diagnostics)
            if (path.contains('.')) {
                document = document.set(path, value)
                nodes.clear()
                nodes.addAll(document.nodes)
            } else {
                val previous = nodes.indexOfFirst { it.source == path }
                val node = ThemeNode(path, line, value)
                if (previous >= 0) nodes[previous] = node else nodes += node
                document = document.copy(nodes = nodes.toList())
            }
        }
        val finalNodes = document.nodes
        document = document.copy(
            originalSource = source,
            originalNodes = finalNodes,
            sourceStatements = sourceStatements(source),
        )
        return ParseResult(document, diagnostics)
    }

    private data class LongBracketRange(val start: Int, val endExclusive: Int, val closed: Boolean)

    private fun longBracketAt(source: String, start: Int): LongBracketRange? {
        if (start !in source.indices || source[start] != '[') return null
        var cursor = start + 1
        while (cursor < source.length && source[cursor] == '=') cursor++
        if (cursor >= source.length || source[cursor] != '[') return null
        val closing = "]" + "=".repeat(cursor - start - 1) + "]"
        val close = source.indexOf(closing, cursor + 1)
        return if (close < 0) LongBracketRange(start, source.length, false) else LongBracketRange(start, close + closing.length, true)
    }

    private fun longBracketRanges(source: String): List<LongBracketRange> {
        val ranges = ArrayList<LongBracketRange>()
        var quote = '\u0000'
        var i = 0
        while (i < source.length) {
            val c = source[i]
            if (quote != '\u0000') {
                if (c == '\\') i++ else if (c == quote) quote = '\u0000'
            } else if (c == '\'' || c == '"') {
                quote = c
            } else if (c == '-' && i + 1 < source.length && source[i + 1] == '-') {
                val range = longBracketAt(source, i + 2)
                if (range != null) { ranges += range; i = range.endExclusive - 1 }
                else while (i < source.length && source[i] != '\n') i++
            } else if (c == '[') {
                val range = longBracketAt(source, i)
                if (range != null) { ranges += range; i = range.endExclusive - 1 }
            }
            i++
        }
        return ranges
    }

    private fun maskLongBrackets(source: String, preserveNewlines: Boolean): String {
        val masked = source.toCharArray()
        longBracketRanges(source).forEach { range ->
            for (index in range.start until range.endExclusive) if (!preserveNewlines || masked[index] != '\n') masked[index] = ' '
        }
        return String(masked)
    }

    private fun sourceStatements(source: String): List<ThemeSourceStatement> {
        val result = ArrayList<ThemeSourceStatement>()
        var start = 0
        var lineStart = 0
        var depth = 0
        var blockDepth = 0
        var quote = '\u0000'
        var i = 0
        fun add(end: Int, separator: String) {
            val text = source.substring(start, end)
            val parsed = ASSIGNMENT.matchEntire(stripComments(text).trim())
            val path = parsed?.groupValues?.get(1)
            result += ThemeSourceStatement(path?.substringBefore('.'), path, text, separator)
        }
        val scan = maskLongBrackets(source, false)
        while (i < scan.length) {
            val c = scan[i]
            if (quote != '\u0000') {
                if (c == '\\') i++ else if (c == quote) quote = '\u0000'
            } else if (c == '-' && i + 1 < scan.length && scan[i + 1] == '-') {
                while (i < scan.length && scan[i] != '\n') i++
                if (i >= scan.length) break
                if (depth == 0) blockDepth = (blockDepth + luaBlockDelta(scan.substring(lineStart, i))).coerceAtLeast(0)
                if (depth == 0 && blockDepth == 0) { add(i, "\n"); start = i + 1 }
                lineStart = i + 1
            } else when (c) {
                '\'', '"' -> quote = c
                '{', '(', '[' -> depth++
                '}', ')', ']' -> depth = (depth - 1).coerceAtLeast(0)
                '\n' -> {
                    if (depth == 0) blockDepth = (blockDepth + luaBlockDelta(scan.substring(lineStart, i))).coerceAtLeast(0)
                    if (depth == 0 && blockDepth == 0) { add(i, "\n"); start = i + 1 }
                    lineStart = i + 1
                }
                ';' -> if (depth == 0 && blockDepth == 0) { add(i, ";"); start = i + 1; lineStart = i + 1 }
            }
            i++
        }
        if (start < source.length) add(source.length, "")
        return result
    }

    private fun lexicalDiagnostics(source: String): List<ThemeDiagnostic> {
        val diagnostics = ArrayList<ThemeDiagnostic>()
        val stack = ArrayDeque<Pair<Char, Int>>()
        var quote = '\u0000'
        var quoteLine = 0
        var line = 1
        var i = 0
        longBracketRanges(source).filter { !it.closed }.forEach { range ->
            diagnostics += ThemeDiagnostic(source.substring(0, range.start).count { it == '\n' } + 1, 1, Severity.ERROR, "Lua 长括号未闭合")
        }
        val scan = maskLongBrackets(source, true)
        while (i < scan.length) {
            val c = scan[i]
            if (quote != '\u0000') {
                if (c == '\\') i++
                else if (c == quote) quote = '\u0000'
                else if (c == '\n') line++
            } else if (c == '-' && i + 1 < scan.length && scan[i + 1] == '-') {
                while (i < scan.length && scan[i] != '\n') i++
                if (i < scan.length) line++
            } else when (c) {
                '\'', '"' -> { quote = c; quoteLine = line }
                '{', '(', '[' -> stack.addLast(c to line)
                '}', ')', ']' -> {
                    val expected = when (c) { '}' -> '{'; ')' -> '('; else -> '[' }
                    val open = stack.removeLastOrNull()
                    if (open == null || open.first != expected) diagnostics += ThemeDiagnostic(line, 1, Severity.ERROR, "括号 '$c' 不匹配")
                }
                '\n' -> line++
            }
            i++
        }
        if (quote != '\u0000') diagnostics += ThemeDiagnostic(quoteLine, 1, Severity.ERROR, "Lua 字符串未闭合")
        stack.forEach { (open, openLine) -> diagnostics += ThemeDiagnostic(openLine, 1, Severity.ERROR, "括号 '$open' 未闭合") }
        return diagnostics
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
        diagnostics += ThemeDiagnostic(line, 1, Severity.WARNING, "不支持的 Lua 表达式已按原文保留")
        return ThemeValue.RawLuaNode(text, line)
    }

    private fun parseTable(body: String, line: Int, diagnostics: MutableList<ThemeDiagnostic>): ThemeValue.LuaTable {
        val fields = LinkedHashMap<String, ThemeValue>()
        var arrayIndex = 1
        for (entry in splitTopLevel(stripComments(body), ',')) {
            val item = entry.trim()
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
                diagnostics += ThemeDiagnostic(line, 1, Severity.WARNING, "不支持的表键:$key")
                continue
            }
            fields[key] = parseValue(item.substring(equals + 1).trim(), line, diagnostics)
        }
        return ThemeValue.LuaTable(fields)
    }

    private fun luaBlockDelta(line: String): Int {
        val tokens = LUA_WORD.findAll(stripComments(line)).map { it.value }.toList()
        var delta = 0
        tokens.forEachIndexed { index, token ->
            when (token) {
                "function", "repeat" -> delta++
                "if" -> if (tokens.drop(index + 1).contains("then")) delta++
                "for", "while" -> if (tokens.drop(index + 1).contains("do")) delta++
                "do" -> if (index == 0) delta++
                "end", "until" -> delta--
            }
        }
        return delta
    }

    private fun splitStatements(source: String): List<Pair<String, Int>> {
        val result = ArrayList<Pair<String, Int>>()
        var start = 0
        var lineStart = 0
        var line = 1
        var statementLine = 1
        var depth = 0
        var blockDepth = 0
        var quote = '\u0000'
        var i = 0
        val scan = maskLongBrackets(source, false)
        while (i < scan.length) {
            val c = scan[i]
            if (quote != '\u0000') {
                if (c == '\\') i++ else if (c == quote) quote = '\u0000'
            } else if (c == '-' && i + 1 < scan.length && scan[i + 1] == '-') {
                while (i < scan.length && scan[i] != '\n') i++
                if (i >= scan.length) break
                if (depth == 0) blockDepth = (blockDepth + luaBlockDelta(scan.substring(lineStart, i))).coerceAtLeast(0)
                line++
                if (depth == 0 && blockDepth == 0) {
                    result += source.substring(start, i) to statementLine
                    start = i + 1
                    statementLine = line
                }
                lineStart = i + 1
            } else when (c) {
                '\'', '"' -> quote = c
                '{', '(', '[' -> depth++
                '}', ')', ']' -> depth = (depth - 1).coerceAtLeast(0)
                '\n' -> {
                    if (depth == 0) blockDepth = (blockDepth + luaBlockDelta(scan.substring(lineStart, i))).coerceAtLeast(0)
                    line++
                    if (depth == 0 && blockDepth == 0) {
                        result += source.substring(start, i) to statementLine
                        start = i + 1
                        statementLine = line
                    }
                    lineStart = i + 1
                }
                ';' -> if (depth == 0 && blockDepth == 0) {
                    result += source.substring(start, i) to statementLine
                    start = i + 1
                    statementLine = line
                    lineStart = i + 1
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
        val scan = maskLongBrackets(source, false)
        while (i < scan.length) {
            val c = scan[i]
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
        val scan = maskLongBrackets(source, false)
        while (i < scan.length) {
            val c = scan[i]
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

    /** Removes comments outside strings while preserving newlines and source line numbers. */
    private fun stripComments(source: String): String = buildString(source.length) {
        var quote = '\u0000'
        var i = 0
        while (i < source.length) {
            val c = source[i]
            if (quote != '\u0000') {
                append(c)
                if (c == '\\' && i + 1 < source.length) append(source[++i])
                else if (c == quote) quote = '\u0000'
            } else {
                val longString = longBracketAt(source, i)
                if (longString != null) {
                    append(source, i, longString.endExclusive)
                    i = longString.endExclusive - 1
                } else if (c == '\'' || c == '"') {
                    quote = c
                    append(c)
                } else if (c == '-' && i + 1 < source.length && source[i + 1] == '-') {
                    val longComment = longBracketAt(source, i + 2)
                    val end = longComment?.endExclusive ?: source.indexOf('\n', i).let { if (it < 0) source.length else it }
                    repeat(source.substring(i, end).count { it == '\n' }) { append('\n') }
                    i = end - 1
                } else append(c)
            }
            i++
        }
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
        private val LUA_WORD = Regex("[A-Za-z_][A-Za-z0-9_]*")
    }
}

data class ParseResult(val document: ThemeDocument, val diagnostics: List<ThemeDiagnostic>)
