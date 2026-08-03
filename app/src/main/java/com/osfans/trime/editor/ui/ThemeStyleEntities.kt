/*
 * SPDX-FileCopyrightText: 2025 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.osfans.trime.editor.ui

import com.osfans.trime.editor.core.Severity
import com.osfans.trime.editor.core.ThemeDocument
import com.osfans.trime.editor.core.ThemeLuaParser
import com.osfans.trime.editor.core.ThemeLuaWriter
import com.osfans.trime.editor.core.ThemeValue

/** Static style-entity operations. User Lua and table.clone are inspected but never evaluated. */
object ThemeStyleEntities {
    data class Snapshot(val id: String, val fragment: String, val cloneParent: String?, val referencedResources: List<String>)
    data class ReferenceUpdate(val source: String, val changedKeys: Int)
    data class Entry(val id: String, val cloneParent: String?, val dynamic: Boolean)

    private val safeId = Regex("^[A-Za-z_][A-Za-z0-9_]{0,63}$")
    private val clone = Regex("^table\\.clone\\(\\s*([A-Za-z_][A-Za-z0-9_]*)\\s*\\)$")
    private val resourceExtension = Regex("(?i).+\\.(?:png|webp|jpg|jpeg|gif|svg|ttf|otf|wav|mp3|ogg)$")
    private val reserved = setOf("keyboard", "key", "candidate", "toolbar", "symbol", "clipboard", "preedit", "composition", "popup")

    @JvmStatic fun isReserved(id: String): Boolean = id in reserved

    @JvmStatic fun list(source: String): List<Entry> {
        val parsed = parseValid(source, "Style source")
        val roots = linkedSetOf<String>(); parsed.document.sourceStatements.mapNotNullTo(roots) { it.root }
        return roots.mapNotNull { id ->
            if (!safeId.matches(id)) return@mapNotNull null
            val root = parsed.document.get(id) ?: return@mapNotNull null
            val parent = (root as? ThemeValue.RawLuaNode)?.source?.trim()?.let { clone.matchEntire(it)?.groupValues?.get(1) }
            val dynamicOverride = parsed.document.sourceStatements.filter { it.path?.startsWith("$id.") == true }.any { parseAssignmentValue(it.text).containsRawLua() }
            when (root) {
                is ThemeValue.LuaTable -> Entry(id, null, root.containsRawLua() || dynamicOverride)
                is ThemeValue.RawLuaNode -> Entry(id, parent, parent == null || dynamicOverride)
                else -> null
            }
        }
    }

    @JvmStatic fun extract(source: String, id: String): Snapshot {
        validateId(id)
        val parsed = parseValid(source, "Style source")
        val paths = parsed.document.sourceStatements.mapNotNull { it.path }
        require(paths.any { it == id || it.startsWith("$id.") }) { "Style entity not found: $id" }
        require(paths.groupingBy { it }.eachCount().none { (path, count) -> count > 1 && (path == id || path.startsWith("$id.")) }) { "Duplicate entity assignments require the Lua source page" }
        val root = parsed.document.get(id)
        val parent = (root as? ThemeValue.RawLuaNode)?.source?.trim()?.let { clone.matchEntire(it)?.groupValues?.get(1) }
        require(root !is ThemeValue.RawLuaNode || parent != null) { "Dynamic style entity requires the Lua source page: $id" }
        require(root == null || root is ThemeValue.RawLuaNode || !root.containsRawLua()) { "Dynamic field in style entity requires the Lua source page: $id" }
        val statements = parsed.document.sourceStatements.filter { it.path == id || it.path?.startsWith("$id.") == true }
        statements.forEach { statement ->
            if (statement.path == id && parent != null) return@forEach
            val value = parseAssignmentValue(statement.text)
            require(!value.containsRawLua()) { "Dynamic field requires the Lua source page: ${statement.path}" }
        }
        val fragment = buildString { statements.forEach { append(it.text.trim()).append(it.separator.ifEmpty { "\n" }) } }.trimEnd() + "\n"
        val strings = linkedSetOf<String>()
        statements.forEach { statement -> collectStrings(parseAssignmentValue(statement.text), strings) }
        val sensitive = strings.firstOrNull { isSensitivePath(it) }
        require(sensitive == null) { "Style entity contains a private URI or absolute path and cannot enter the private clipboard" }
        return Snapshot(id, fragment, parent, strings.filter { resourceExtension.matches(it) }.sorted())
    }

    @JvmStatic fun paste(source: String, snapshot: Snapshot, targetId: String): String {
        validateId(targetId); validateId(snapshot.id)
        val parsed = parseValid(source, "Target style source")
        require(parsed.document.get(targetId) == null && parsed.document.sourceStatements.none { it.path == targetId || it.path?.startsWith("$targetId.") == true }) { "Style entity already exists: $targetId" }
        snapshot.cloneParent?.let { parent ->
            validateId(parent)
            val dependency = list(source).firstOrNull { it.id == parent }
            require(dependency != null) { "Clone dependency is missing in target style: $parent" }
            require(!dependency.dynamic) { "Clone dependency is dynamic in target style: $parent" }
        }
        // Re-extract to verify clipboard text is still a static, self-contained entity.
        val verified = extract(snapshot.fragment, snapshot.id)
        require(verified.cloneParent == snapshot.cloneParent) { "Clipboard style dependency changed" }
        require(verified.referencedResources == snapshot.referencedResources) { "Clipboard style resource dependencies changed" }
        val rewritten = rewriteEntityPaths(verified.fragment, snapshot.id, targetId)
        val result = source + (if (source.isNotEmpty() && !source.endsWith('\n')) "\n" else "") + rewritten
        parseValid(result, "Pasted style source")
        return result
    }

    @JvmStatic fun create(source: String, targetId: String, cloneParent: String?): String {
        validateId(targetId)
        cloneParent?.let(::validateId)
        val fragment = if (cloneParent == null) "$targetId = {}\n" else "$targetId = table.clone($cloneParent)\n"
        return paste(source, Snapshot(targetId, fragment, cloneParent, emptyList()), targetId)
    }

    @JvmStatic fun rename(source: String, oldId: String, newId: String): String {
        validateId(oldId); validateId(newId)
        require(!isReserved(oldId)) { "Reserved component style cannot be renamed: $oldId" }
        require(!isReserved(newId)) { "Cannot rename an entity to a reserved component ID: $newId" }
        if (oldId == newId) return source
        val parsed = parseValid(source, "Style source")
        val duplicatePaths = parsed.document.sourceStatements.mapNotNull { it.path }.groupingBy { it }.eachCount().filter { (path, count) -> count > 1 && (path == oldId || path.startsWith("$oldId.")) }.keys
        require(duplicatePaths.isEmpty()) { "Duplicate entity assignments require the Lua source page: ${duplicatePaths.joinToString()}" }
        require(list(source).firstOrNull { it.id == oldId }?.dynamic == false) { "Dynamic style entity requires the Lua source page: $oldId" }
        require(parsed.document.sourceStatements.none { uncertainEntityReference(it.path, it.text, oldId) }) { "Inline or dynamic style references require the Lua source page before rename: $oldId" }
        require(parsed.document.get(oldId) != null || parsed.document.sourceStatements.any { it.path == oldId || it.path?.startsWith("$oldId.") == true }) { "Style entity not found: $oldId" }
        require(parsed.document.get(newId) == null && parsed.document.sourceStatements.none { it.path == newId || it.path?.startsWith("$newId.") == true }) { "Style entity already exists: $newId" }
        val result = buildString {
            parsed.document.sourceStatements.forEach { statement ->
                var text = if (statement.path == oldId || statement.path?.startsWith("$oldId.") == true) rewriteStatementPath(statement.text, statement.path!!, newId + statement.path!!.removePrefix(oldId)) else statement.text
                val value = statement.path?.let { parseAssignmentValue(statement.text) }
                if (value is ThemeValue.RawLuaNode && clone.matchEntire(value.source.trim())?.groupValues?.get(1) == oldId) text = rewriteAssignmentValue(text, statement.path!!, "table.clone($newId)")
                else if (statement.path?.substringAfterLast('.') == "style" && value is ThemeValue.LuaString && value.value == oldId) text = rewriteAssignmentValue(text, statement.path!!, "\"$newId\"")
                append(text).append(statement.separator)
            }
        }
        parseValid(result, "Renamed style source")
        return result
    }

    @JvmStatic fun delete(source: String, id: String): String {
        validateId(id)
        val parsed = parseValid(source, "Style source")
        require(!isReserved(id)) { "Reserved component style cannot be deleted: $id" }
        require(list(source).firstOrNull { it.id == id }?.dynamic == false) { "Dynamic style entity requires the Lua source page: $id" }
        require(parsed.document.sourceStatements.none { uncertainEntityReference(it.path, it.text, id) }) { "Inline or dynamic style references require the Lua source page before deletion: $id" }
        require(parsed.document.sourceStatements.none { statement ->
            val value = statement.path?.let { parseAssignmentValue(statement.text) }
            (value is ThemeValue.RawLuaNode && clone.matchEntire(value.source.trim())?.groupValues?.get(1) == id) || (statement.path?.substringAfterLast('.') == "style" && value is ThemeValue.LuaString && value.value == id)
        }) { "Style entity is referenced or inherited by another style" }
        var found = false
        val result = buildString {
            parsed.document.sourceStatements.forEach { statement ->
                if (statement.path == id || statement.path?.startsWith("$id.") == true) found = true
                else append(statement.text).append(statement.separator)
            }
        }
        require(found) { "Style entity not found: $id" }
        parseValid(result, "Style source after deletion")
        return result
    }

    @JvmStatic fun replaceKeyboardReferences(source: String, oldId: String, newId: String?): ReferenceUpdate {
        validateId(oldId); newId?.let(::validateId)
        val parsed = parseValid(source, "Keyboard source")
        val roots = listOf("rows", "flex_box", "keys", "key_maps")
        roots.forEach { root -> require(parsed.document.get(root)?.containsRawLua() != true) { "Dynamic layout requires the Lua source page: $root" } }
        val model = ThemeLayoutCodec.fromDocument(parsed.document)
        require(model.layoutMode != ThemeEditorModel.LayoutMode.NONE) { "Keyboard has no statically recognized layout" }
        val allKeys = if (model.layoutMode == ThemeEditorModel.LayoutMode.KEY_MAPS) model.keyMapPages.flatMap { it.keys } else model.keys
        val affected = allKeys.filter { it.keyStyle == oldId || (it.keyStyle.isEmpty() && it.click == oldId) }
        val affectedContainers = model.flexContainers.filter { it.style == oldId }
        val changed = affected.size + affectedContainers.size
        if (changed == 0) return ReferenceUpdate(source, 0)
        affected.forEach { key -> key.keyStyle = newId ?: if (key.click == oldId) "key" else "" }
        affectedContainers.forEach { it.style = newId.orEmpty() }
        if (model.layoutMode == ThemeEditorModel.LayoutMode.KEY_MAPS && model.keyMapPages.isNotEmpty()) {
            model.keys.clear(); model.keys.addAll(model.keyMapPages[model.selectedKeyMapPage].keys.map { it.copy() })
        }
        val updated = ThemeLayoutCodec.writeAgainstOriginal(parsed.document, model)
        val written = ThemeLuaWriter.write(updated)
        parseValid(written, "Keyboard reference update")
        return ReferenceUpdate(written, changed)
    }

    @JvmStatic fun referenceCount(source: String, id: String): Int {
        validateId(id)
        val parsed = parseValid(source, "Keyboard source")
        val roots = listOf("rows", "flex_box", "keys", "key_maps")
        require(roots.none { parsed.document.get(it)?.containsRawLua() == true }) { "Dynamic layout has uncertain style references" }
        val model = ThemeLayoutCodec.fromDocument(parsed.document)
        require(model.layoutMode != ThemeEditorModel.LayoutMode.NONE) { "Keyboard has no statically recognized layout" }
        val keys = if (model.layoutMode == ThemeEditorModel.LayoutMode.KEY_MAPS) model.keyMapPages.flatMap { it.keys } else model.keys
        return keys.count { it.keyStyle == id || (it.keyStyle.isEmpty() && it.click == id) } + model.flexContainers.count { it.style == id }
    }

    private fun uncertainEntityReference(path: String?, text: String, id: String): Boolean {
        if (path == id || path?.startsWith("$id.") == true) return false
        if (path == null) return Regex("\\b${Regex.escape(id)}\\b").containsMatchIn(text)
        val value = parseAssignmentValue(text)
        if (value is ThemeValue.RawLuaNode) {
            val recognizedClone = clone.matchEntire(value.source.trim())?.groupValues?.get(1) == id
            return !recognizedClone && Regex("\\b${Regex.escape(id)}\\b").containsMatchIn(value.source)
        }
        return value.containsNestedStyleReference(id)
    }

    private fun ThemeValue.containsNestedStyleReference(id: String): Boolean = when (this) {
        is ThemeValue.LuaTable -> fields.any { (name, value) -> (name == "style" && value is ThemeValue.LuaString && value.value == id) || value.containsNestedStyleReference(id) }
        else -> false
    }

    private fun rewriteEntityPaths(fragment: String, oldId: String, newId: String): String {
        val parsed = parseValid(fragment, "Clipboard style entity")
        return buildString { parsed.document.sourceStatements.forEach { statement ->
            val path = statement.path
            append(if (path == oldId || path?.startsWith("$oldId.") == true) rewriteStatementPath(statement.text, path!!, newId + path.removePrefix(oldId)) else statement.text).append(statement.separator)
        } }
    }

    private fun rewriteStatementPath(text: String, oldPath: String, newPath: String): String {
        val assignment = Regex("(^|\\n)([ \\t]*)${Regex.escape(oldPath)}([ \\t]*=)")
        val match = assignment.find(text) ?: error("Cannot locate assignment path: $oldPath")
        return text.replaceRange(match.range, match.groupValues[1] + match.groupValues[2] + newPath + match.groupValues[3])
    }

    private fun rewriteAssignmentValue(text: String, path: String, value: String): String {
        val assignment = Regex("(^|\n)([ \t]*)${Regex.escape(path)}[ \t]*=")
        val match = assignment.find(text) ?: error("Cannot locate assignment value: $path")
        val valueStart = match.range.last + 1
        val comment = trailingComment(text, valueStart)
        return text.substring(0, valueStart) + " " + value + if (comment >= 0) " " + text.substring(comment) else ""
    }

    private fun trailingComment(text: String, start: Int): Int {
        var quote = '\u0000'; var index = start
        while (index + 1 < text.length) {
            val char = text[index]
            if (quote != '\u0000') { if (char == '\\') index++ else if (char == quote) quote = '\u0000' }
            else if (char == '\'' || char == '"') quote = char
            else if (char == '-' && text[index + 1] == '-') return index
            index++
        }
        return -1
    }

    private fun parseAssignmentValue(text: String): ThemeValue {
        val parsed = ThemeLuaParser().parse(text.trim())
        require(parsed.diagnostics.none { it.severity == Severity.ERROR }) { "Invalid entity statement" }
        return parsed.document.nodes.firstOrNull { it.assignment }?.value ?: error("Entity statement is not an assignment")
    }

    private fun parseValid(source: String, label: String) = ThemeLuaParser().parse(source).also { parsed ->
        require(parsed.diagnostics.none { it.severity == Severity.ERROR }) { "$label contains Lua errors" }
    }

    private fun validateId(id: String) { require(safeId.matches(id)) { "Style entity ID must be a Lua-safe identifier" } }
    private fun ThemeValue.containsRawLua(): Boolean = when (this) {
        is ThemeValue.RawLuaNode -> true
        is ThemeValue.LuaTable -> fields.values.any { it.containsRawLua() }
        else -> false
    }
    private fun isSensitivePath(value: String): Boolean {
        val lower = value.lowercase()
        return lower.startsWith("content://") || lower.startsWith("file://") || value.startsWith('/') || value.startsWith('\\') || Regex("^[A-Za-z]:[\\\\/].+").matches(value) || value.replace('\\', '/').split('/').any { it == ".." } || value.any { it.code < 32 }
    }

    private fun collectStrings(value: ThemeValue, result: MutableSet<String>) {
        when (value) {
            is ThemeValue.LuaString -> result += value.value
            is ThemeValue.LuaTable -> value.fields.values.forEach { collectStrings(it, result) }
            else -> Unit
        }
    }
}
