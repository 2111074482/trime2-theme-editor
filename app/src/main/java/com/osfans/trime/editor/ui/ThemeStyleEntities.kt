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
        val parsed = parseValid(source, "样式源代码")
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
        val parsed = parseValid(source, "样式源代码")
        val paths = parsed.document.sourceStatements.mapNotNull { it.path }
        require(paths.any { it == id || it.startsWith("$id.") }) { "未找到样式实体:$id" }
        require(paths.groupingBy { it }.eachCount().none { (path, count) -> count > 1 && (path == id || path.startsWith("$id.")) }) { "重复实体赋值必须在 Lua 源代码页编辑" }
        val root = parsed.document.get(id)
        val parent = (root as? ThemeValue.RawLuaNode)?.source?.trim()?.let { clone.matchEntire(it)?.groupValues?.get(1) }
        require(root !is ThemeValue.RawLuaNode || parent != null) { "动态样式实体必须在 Lua 源代码页编辑:$id" }
        require(root == null || root is ThemeValue.RawLuaNode || !root.containsRawLua()) { "样式实体中的动态字段必须在 Lua 源代码页编辑:$id" }
        val statements = parsed.document.sourceStatements.filter { it.path == id || it.path?.startsWith("$id.") == true }
        statements.forEach { statement ->
            if (statement.path == id && parent != null) return@forEach
            val value = parseAssignmentValue(statement.text)
            require(!value.containsRawLua()) { "动态字段必须在 Lua 源代码页编辑:${statement.path}" }
        }
        val fragment = buildString { statements.forEach { append(it.text.trim()).append(it.separator.ifEmpty { "\n" }) } }.trimEnd() + "\n"
        val strings = linkedSetOf<String>()
        statements.forEach { statement -> collectStrings(parseAssignmentValue(statement.text), strings) }
        val sensitive = strings.firstOrNull { isSensitivePath(it) }
        require(sensitive == null) { "样式实体包含私有 URI 或绝对路径,不能进入编辑器私有剪贴板" }
        return Snapshot(id, fragment, parent, strings.filter { resourceExtension.matches(it) }.sorted())
    }

    @JvmStatic fun paste(source: String, snapshot: Snapshot, targetId: String): String {
        validateId(targetId); validateId(snapshot.id)
        val parsed = parseValid(source, "目标样式源代码")
        require(parsed.document.get(targetId) == null && parsed.document.sourceStatements.none { it.path == targetId || it.path?.startsWith("$targetId.") == true }) { "样式实体已存在:$targetId" }
        snapshot.cloneParent?.let { parent ->
            validateId(parent)
            val dependency = list(source).firstOrNull { it.id == parent }
            require(dependency != null) { "目标样式缺少克隆依赖:$parent" }
            require(!dependency.dynamic) { "目标样式的克隆依赖为动态内容:$parent" }
        }
        // Re-extract to verify clipboard text is still a static, self-contained entity.
        val verified = extract(snapshot.fragment, snapshot.id)
        require(verified.cloneParent == snapshot.cloneParent) { "剪贴板中的样式依赖已变化" }
        require(verified.referencedResources == snapshot.referencedResources) { "剪贴板中的样式资源依赖已变化" }
        val rewritten = rewriteEntityPaths(verified.fragment, snapshot.id, targetId)
        val result = source + (if (source.isNotEmpty() && !source.endsWith('\n')) "\n" else "") + rewritten
        parseValid(result, "粘贴后的样式源代码")
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
        require(!isReserved(oldId)) { "保留组件样式不能重命名:$oldId" }
        require(!isReserved(newId)) { "不能将实体重命名为保留组件标识:$newId" }
        if (oldId == newId) return source
        val parsed = parseValid(source, "样式源代码")
        val duplicatePaths = parsed.document.sourceStatements.mapNotNull { it.path }.groupingBy { it }.eachCount().filter { (path, count) -> count > 1 && (path == oldId || path.startsWith("$oldId.")) }.keys
        require(duplicatePaths.isEmpty()) { "重复实体赋值必须在 Lua 源代码页编辑:${duplicatePaths.joinToString()}" }
        require(list(source).firstOrNull { it.id == oldId }?.dynamic == false) { "动态样式实体必须在 Lua 源代码页编辑:$oldId" }
        require(parsed.document.sourceStatements.none { uncertainEntityReference(it.path, it.text, oldId) }) { "重命名前必须在 Lua 源代码页处理内联或动态样式引用:$oldId" }
        require(parsed.document.get(oldId) != null || parsed.document.sourceStatements.any { it.path == oldId || it.path?.startsWith("$oldId.") == true }) { "未找到样式实体:$oldId" }
        require(parsed.document.get(newId) == null && parsed.document.sourceStatements.none { it.path == newId || it.path?.startsWith("$newId.") == true }) { "样式实体已存在:$newId" }
        val result = buildString {
            parsed.document.sourceStatements.forEach { statement ->
                var text = if (statement.path == oldId || statement.path?.startsWith("$oldId.") == true) rewriteStatementPath(statement.text, statement.path!!, newId + statement.path!!.removePrefix(oldId)) else statement.text
                val value = statement.path?.let { parseAssignmentValue(statement.text) }
                if (value is ThemeValue.RawLuaNode && clone.matchEntire(value.source.trim())?.groupValues?.get(1) == oldId) text = rewriteAssignmentValue(text, statement.path!!, "table.clone($newId)")
                else if (statement.path?.substringAfterLast('.') == "style" && value is ThemeValue.LuaString && value.value == oldId) text = rewriteAssignmentValue(text, statement.path!!, "\"$newId\"")
                append(text).append(statement.separator)
            }
        }
        parseValid(result, "重命名后的样式源代码")
        return result
    }

    @JvmStatic fun delete(source: String, id: String): String {
        validateId(id)
        val parsed = parseValid(source, "样式源代码")
        require(!isReserved(id)) { "保留组件样式不能删除:$id" }
        require(list(source).firstOrNull { it.id == id }?.dynamic == false) { "动态样式实体必须在 Lua 源代码页编辑:$id" }
        require(parsed.document.sourceStatements.none { uncertainEntityReference(it.path, it.text, id) }) { "删除前必须在 Lua 源代码页处理内联或动态样式引用:$id" }
        require(parsed.document.sourceStatements.none { statement ->
            val value = statement.path?.let { parseAssignmentValue(statement.text) }
            (value is ThemeValue.RawLuaNode && clone.matchEntire(value.source.trim())?.groupValues?.get(1) == id) || (statement.path?.substringAfterLast('.') == "style" && value is ThemeValue.LuaString && value.value == id)
        }) { "样式实体仍被其他样式引用或继承" }
        var found = false
        val result = buildString {
            parsed.document.sourceStatements.forEach { statement ->
                if (statement.path == id || statement.path?.startsWith("$id.") == true) found = true
                else append(statement.text).append(statement.separator)
            }
        }
        require(found) { "未找到样式实体:$id" }
        parseValid(result, "删除后的样式源代码")
        return result
    }

    @JvmStatic fun replaceKeyboardReferences(source: String, oldId: String, newId: String?): ReferenceUpdate {
        validateId(oldId); newId?.let(::validateId)
        val parsed = parseValid(source, "键盘源代码")
        val roots = listOf("rows", "flex_box", "keys", "key_maps")
        roots.forEach { root -> require(parsed.document.get(root)?.containsRawLua() != true) { "动态布局必须在 Lua 源代码页编辑:$root" } }
        val model = ThemeLayoutCodec.fromDocument(parsed.document)
        require(model.layoutMode != ThemeEditorModel.LayoutMode.NONE) { "键盘没有可静态识别的布局" }
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
        parseValid(written, "键盘引用更新结果")
        return ReferenceUpdate(written, changed)
    }

    @JvmStatic fun referenceCount(source: String, id: String): Int {
        validateId(id)
        val parsed = parseValid(source, "键盘源代码")
        val roots = listOf("rows", "flex_box", "keys", "key_maps")
        require(roots.none { parsed.document.get(it)?.containsRawLua() == true }) { "动态布局包含无法确定的样式引用" }
        val model = ThemeLayoutCodec.fromDocument(parsed.document)
        require(model.layoutMode != ThemeEditorModel.LayoutMode.NONE) { "键盘没有可静态识别的布局" }
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
        val parsed = parseValid(fragment, "剪贴板样式实体")
        return buildString { parsed.document.sourceStatements.forEach { statement ->
            val path = statement.path
            append(if (path == oldId || path?.startsWith("$oldId.") == true) rewriteStatementPath(statement.text, path!!, newId + path.removePrefix(oldId)) else statement.text).append(statement.separator)
        } }
    }

    private fun rewriteStatementPath(text: String, oldPath: String, newPath: String): String {
        val assignment = Regex("(^|\\n)([ \\t]*)${Regex.escape(oldPath)}([ \\t]*=)")
        val match = assignment.find(text) ?: error("无法定位赋值路径:$oldPath")
        return text.replaceRange(match.range, match.groupValues[1] + match.groupValues[2] + newPath + match.groupValues[3])
    }

    private fun rewriteAssignmentValue(text: String, path: String, value: String): String {
        val assignment = Regex("(^|\n)([ \t]*)${Regex.escape(path)}[ \t]*=")
        val match = assignment.find(text) ?: error("无法定位赋值值:$path")
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
        require(parsed.diagnostics.none { it.severity == Severity.ERROR }) { "实体语句无效" }
        return parsed.document.nodes.firstOrNull { it.assignment }?.value ?: error("实体语句不是赋值语句")
    }

    private fun parseValid(source: String, label: String) = ThemeLuaParser().parse(source).also { parsed ->
        require(parsed.diagnostics.none { it.severity == Severity.ERROR }) { "$label 包含 Lua 错误" }
    }

    private fun validateId(id: String) { require(safeId.matches(id)) { "样式实体标识必须是 Lua 安全标识符" } }
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
