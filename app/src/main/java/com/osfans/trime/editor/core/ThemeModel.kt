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

data class ThemeNode(val source: String, val line: Int, val value: ThemeValue, val assignment: Boolean = true)

data class ThemeSourceStatement(
    val root: String?,
    val path: String?,
    val text: String,
    val separator: String,
)

enum class ThemeWriteMode { PRESERVE, HYBRID, STRUCTURED }

data class ThemeDocument(
    val nodes: List<ThemeNode>,
    val trailingNewline: Boolean = true,
    val originalSource: String? = null,
    val originalNodes: List<ThemeNode> = emptyList(),
    val sourceStatements: List<ThemeSourceStatement> = emptyList(),
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
        require(parts.isNotEmpty()) { "字段路径不能为空" }
        val roots = nodes.toMutableList()
        val rootIndex = roots.indexOfFirst { it.source == parts.first() }
        if (rootIndex >= 0 && roots[rootIndex].value is ThemeValue.RawLuaNode) {
            return this
        }
        val root = if (rootIndex >= 0) roots[rootIndex].value else ThemeValue.LuaTable()
        val updated = setNested(root, parts.drop(1), value)
        if (rootIndex >= 0) roots[rootIndex] = roots[rootIndex].copy(value = updated)
        else roots.add(ThemeNode(parts.first(), 0, updated))
        return copy(nodes = roots)
    }

    fun remove(path: String): ThemeDocument {
        val parts = path.split('.').filter { it.isNotBlank() }
        if (parts.isEmpty()) return this
        val roots = nodes.toMutableList()
        val rootIndex = roots.indexOfFirst { it.source == parts.first() }
        if (rootIndex < 0 || roots[rootIndex].value is ThemeValue.RawLuaNode) return this
        if (parts.size == 1) {
            roots.removeAt(rootIndex)
            return copy(nodes = roots)
        }
        val updated = removeNested(roots[rootIndex].value, parts.drop(1))
        roots[rootIndex] = roots[rootIndex].copy(value = updated)
        return copy(nodes = roots)
    }

    private fun removeNested(current: ThemeValue, path: List<String>): ThemeValue {
        if (path.isEmpty()) return current
        val currentTable = current as? ThemeValue.LuaTable ?: return current
        val table = LinkedHashMap(currentTable.fields)
        if (path.size == 1) table.remove(path.first())
        else table[path.first()]?.let { table[path.first()] = removeNested(it, path.drop(1)) }
        return ThemeValue.LuaTable(table)
    }

    private fun setNested(current: ThemeValue, path: List<String>, value: ThemeValue): ThemeValue {
        if (path.isEmpty()) return value
        val table = (current as? ThemeValue.LuaTable)?.fields?.let { LinkedHashMap(it) } ?: LinkedHashMap()
        table[path.first()] = setNested(table[path.first()] ?: ThemeValue.LuaTable(), path.drop(1), value)
        return ThemeValue.LuaTable(table)
    }
}

object ThemeLuaWriter {
    fun write(document: ThemeDocument, mode: ThemeWriteMode = ThemeWriteMode.HYBRID): String = when (mode) {
        ThemeWriteMode.PRESERVE -> preserve(document)
        ThemeWriteMode.HYBRID -> hybrid(document)
        ThemeWriteMode.STRUCTURED -> structured(document)
    }

    private fun preserve(document: ThemeDocument): String {
        val source = document.originalSource
        require(source != null && document.nodes == document.originalNodes) {
            "保留模式(Preserve)不能写出已发生结构修改的文档"
        }
        return source
    }

    private fun hybrid(document: ThemeDocument): String {
        val source = document.originalSource ?: return structured(document)
        if (document.nodes == document.originalNodes) return source
        if (document.sourceStatements.isEmpty()) return structured(document)
        val changed = changedRoots(document)
        val duplicateChanged = document.sourceStatements.mapNotNull { it.path }.groupingBy { it }.eachCount()
            .filter { (path, count) -> count > 1 && path.substringBefore('.') in changed }.keys
        require(duplicateChanged.isEmpty()) { "混合模式(Hybrid)无法安全重写重复赋值:${duplicateChanged.joinToString()}" }
        val current = document.nodes.associateBy { it.source }
        val emitted = HashSet<String>()
        val result = buildString {
            document.sourceStatements.forEach { statement ->
                val root = statement.root
                if (root == null || root !in changed) {
                    append(statement.text).append(statement.separator)
                } else {
                    if (emitted.add(root)) current[root]?.let { appendNode(it) }
                    append(statement.separator.ifEmpty { if (document.trailingNewline) "\n" else "" })
                }
            }
            document.nodes.filter { node ->
                node.source !in document.originalNodes.map { it.source }.toSet() && emitted.add(node.source)
            }.forEachIndexed { index, node ->
                if (isNotEmpty() && last() != '\n') append('\n')
                appendNode(node)
                if (document.trailingNewline || index < document.nodes.lastIndex) append('\n')
            }
        }
        return result
    }

    private fun changedRoots(document: ThemeDocument): Set<String> {
        val original = document.originalNodes.associateBy { it.source }
        val current = document.nodes.associateBy { it.source }
        return (original.keys + current.keys).filterTo(LinkedHashSet()) { original[it]?.value != current[it]?.value }
    }

    private fun structured(document: ThemeDocument): String = buildString {
        document.nodes.forEachIndexed { index, node ->
            if (index > 0) append('\n')
            appendNode(node)
        }
        if (document.trailingNewline) append('\n')
    }

    private fun StringBuilder.appendNode(node: ThemeNode) {
        if (!node.assignment && node.value is ThemeValue.RawLuaNode) append(node.value.source)
        else append(node.source).append(" = ").append(value(node.value, 0))
    }

    private fun value(value: ThemeValue, depth: Int): String = when (value) {
        is ThemeValue.LuaString -> "\"" + value.value.replace("\\", "\\\\").replace("\"", "\\\"") + "\""
        is ThemeValue.LuaNumber -> if (value.value % 1.0 == 0.0) value.value.toLong().toString() else value.value.toString()
        is ThemeValue.LuaBoolean -> value.value.toString()
        ThemeValue.LuaNil -> "nil"
        is ThemeValue.RawLuaNode -> value.source
        is ThemeValue.LuaTable -> if (value.fields.isEmpty()) {
            "{}"
        } else {
            val pad = "  ".repeat(depth + 1)
            val close = "  ".repeat(depth)
            "{\n" + value.fields.entries.joinToString(",\n") { (key, child) ->
                if (key.startsWith("#")) "$pad${value(child, depth + 1)}"
                else "$pad${writeKey(key)} = ${value(child, depth + 1)}"
            } + "\n$close}"
        }
    }

    private fun writeKey(key: String): String = if (key.matches(Regex("^[A-Za-z_][A-Za-z0-9_]*$"))) key else "[\"${key.replace("\"", "\\\"")}\"]"
}
