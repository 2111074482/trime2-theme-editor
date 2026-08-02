/*
 * SPDX-FileCopyrightText: 2025 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.editor.core

/** Values understood by the theme editor. RawLuaNode keeps syntax the editor does not model. */
sealed class ThemeValue {
    data class LuaString(val value: String) : ThemeValue()
    data class LuaNumber(val value: Double) : ThemeValue()
    data class LuaBoolean(val value: Boolean) : ThemeValue()
    data class LuaTable(val fields: LinkedHashMap<String, ThemeValue> = LinkedHashMap()) : ThemeValue()
    data object LuaNil : ThemeValue()
    data class RawLuaNode(val source: String, val line: Int = 0) : ThemeValue()
}

data class ThemeNode(val source: String, val line: Int, val value: ThemeValue)

data class ThemeDocument(
    val nodes: List<ThemeNode>,
    val trailingNewline: Boolean = true,
) {
    fun get(path: String): ThemeValue? {
        val parts = path.split('.').filter { it.isNotBlank() }
        if (parts.isEmpty()) return null
        var current: ThemeValue = nodes.firstOrNull { it.source == parts.first() }?.value ?: return null
        for (part in parts.drop(1)) current = (current as? ThemeValue.LuaTable)?.fields?.get(part) ?: return null
        return current
    }

    fun set(path: String, value: ThemeValue): ThemeDocument {
        val parts = path.split('.').filter { it.isNotBlank() }
        require(parts.isNotEmpty()) { "path must not be empty" }
        val roots = nodes.toMutableList()
        val rootIndex = roots.indexOfFirst { it.source == parts.first() }
        val root = if (rootIndex >= 0) roots[rootIndex].value else ThemeValue.LuaTable()
        val updated = setNested(root, parts.drop(1), value)
        if (rootIndex >= 0) roots[rootIndex] = roots[rootIndex].copy(value = updated)
        else roots.add(ThemeNode(parts.first(), 0, updated))
        return copy(nodes = roots)
    }

    private fun setNested(current: ThemeValue, path: List<String>, value: ThemeValue): ThemeValue {
        if (path.isEmpty()) return value
        val table = (current as? ThemeValue.LuaTable)?.fields?.let { LinkedHashMap(it) } ?: LinkedHashMap()
        table[path.first()] = setNested(table[path.first()] ?: ThemeValue.LuaTable(), path.drop(1), value)
        return ThemeValue.LuaTable(table)
    }
}

object ThemeLuaWriter {
    fun write(document: ThemeDocument): String = buildString {
        document.nodes.forEachIndexed { index, node ->
            if (index > 0) append('\n')
            if (node.value is ThemeValue.RawLuaNode) append(node.value.source)
            else append(node.source).append(" = ").append(value(node.value, 0))
        }
        if (document.trailingNewline) append('\n')
    }

    private fun value(value: ThemeValue, depth: Int): String = when (value) {
        is ThemeValue.LuaString -> '"' + value.value.replace("\\", "\\\\").replace("\"", "\\\"") + '"'
        is ThemeValue.LuaNumber -> if (value.value % 1.0 == 0.0) value.value.toLong().toString() else value.value.toString()
        is ThemeValue.LuaBoolean -> value.value.toString()
        ThemeValue.LuaNil -> "nil"
        is ThemeValue.RawLuaNode -> value.source
        is ThemeValue.LuaTable -> {
            if (value.fields.isEmpty()) return "{}"
            val pad = "  ".repeat(depth + 1)
            val close = "  ".repeat(depth)
            "{\n" + value.fields.entries.joinToString(",\n") { (key, child) ->
                if (key.startsWith("#")) "$pad${value(child, depth + 1)}" else "$pad$key = ${value(child, depth + 1)}"
            } + "\n$close}"
        }
    }
}
