/*
 * SPDX-FileCopyrightText: 2025 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.osfans.trime.editor.ui

import com.osfans.trime.editor.core.Severity
import com.osfans.trime.editor.core.ThemeLuaParser
import com.osfans.trime.editor.core.ThemeValue
import com.osfans.trime.editor.project.ThemeProject

/** Static key-style reference analysis and source patching; never evaluates table.clone or user Lua. */
object ThemeKeyStyleBatch {
    data class Reference(val keyboardId: String, val count: Int)
    data class Report(val styleIds: List<String>, val references: List<Reference>, val totalReferences: Int, val uncertainKeyboardIds: List<String>)
    data class Change(val background: String?, val textColor: String?)
    data class PreviewColors(val backgrounds: Map<String, Int>, val textColors: Map<String, Int>)

    private val safeId = Regex("^[A-Za-z_][A-Za-z0-9_]{0,63}$")
    private val color = Regex("^(?:#|0[xX])?([0-9A-Fa-f]{1,8})$")

    @JvmStatic fun references(project: ThemeProject, styleIds: Collection<String>): Report = references(project, styleIds, null)

    @JvmStatic fun references(project: ThemeProject, styleIds: Collection<String>, targetStyleId: String?): Report {
        val targets = styleIds.map { normalizedId(it) }.toSortedSet()
        val uncertain = mutableListOf<String>()
        val mainStyle = ThemeLuaParser().parse(project.mainFile.readText(Charsets.UTF_8)).document.get("style")
        val defaultStyle = (mainStyle as? ThemeValue.LuaString)?.value ?: "light"
        val dynamicDefaultStyle = mainStyle != null && mainStyle !is ThemeValue.LuaString
        val targetEntities = targetStyleId?.let { id -> project.style(id)?.file?.readText(Charsets.UTF_8)?.let(ThemeStyleEntities::list)?.map { it.id }?.toSet() } ?: emptySet()
        val references = project.keyboards.mapNotNull { keyboard ->
            val parsed = ThemeLuaParser().parse(keyboard.file.readText(Charsets.UTF_8))
            if (parsed.diagnostics.any { it.severity == Severity.ERROR }) { uncertain += keyboard.name; return@mapNotNull null }
            val declaredStyle = parsed.document.get("style")
            val keyboardStyle = (declaredStyle as? ThemeValue.LuaString)?.value ?: defaultStyle
            if (targetStyleId != null && ((declaredStyle != null && declaredStyle !is ThemeValue.LuaString) || (declaredStyle == null && dynamicDefaultStyle))) { uncertain += keyboard.name; return@mapNotNull null }
            if (targetStyleId != null && keyboardStyle != targetStyleId) return@mapNotNull null
            if (listOf("rows", "flex_box", "keys", "key_maps").any { parsed.document.get(it)?.containsRawLua() == true }) uncertain += keyboard.name
            val model = ThemeLayoutCodec.fromDocument(parsed.document)
            if (model.layoutMode == ThemeEditorModel.LayoutMode.NONE) { uncertain += keyboard.name; return@mapNotNull null }
            val keys = if (model.layoutMode == ThemeEditorModel.LayoutMode.KEY_MAPS) model.keyMapPages.flatMap { it.keys } else model.keys
            val count = keys.count { effectiveStyleId(it, targetEntities) in targets } + model.flexContainers.count { it.style in targets }
            count.takeIf { it > 0 }?.let { Reference(keyboard.name, it) }
        }
        return Report(targets.toList(), references, references.sumOf { it.count }, uncertain.distinct().sorted())
    }

    @JvmStatic fun update(source: String, styleIds: Collection<String>, change: Change): String {
        require(change.background != null || change.textColor != null) { "No style fields selected" }
        val ids = styleIds.map { normalizedId(it) }.toSortedSet()
        require(ids.isNotEmpty()) { "No referenced style entities selected" }
        val parsed = ThemeLuaParser().parse(source)
        require(parsed.diagnostics.none { it.severity == Severity.ERROR }) { "Style source contains Lua errors" }
        val entries = ThemeStyleEntities.list(source).associateBy { it.id }
        ids.forEach { id ->
            require(styleExists(parsed.document.get(id), parsed.document.sourceStatements.mapNotNull { it.path }, id)) { "Style entity not found: $id" }
            require(entries[id]?.dynamic != true) { "Dynamic style entity requires the Lua source page: $id" }
        }
        val replacements = linkedMapOf<String, String>()
        ids.forEach { id ->
            change.background?.let { replacements["$id.background"] = backgroundLiteral(it) }
            change.textColor?.let { replacements["$id.text_color"] = colorLiteral(it) }
        }
        val duplicates = parsed.document.sourceStatements.mapNotNull { it.path }.groupingBy { it }.eachCount().filter { (path, count) -> count > 1 && path in replacements }.keys
        require(duplicates.isEmpty()) { "Duplicate style assignments require the Lua source page: ${duplicates.joinToString()}" }
        val statements = parsed.document.sourceStatements
        replacements.keys.forEach { path ->
            val exact = statements.indexOfLast { it.path == path }
            val laterRoot = statements.indexOfLast { it.path == path.substringBefore('.') }
            require(exact < 0 || laterRoot < exact) { "A later '${path.substringBefore('.')}' assignment overrides '$path'; use the Lua source page" }
        }
        val found = hashSetOf<String>()
        val result = buildString {
            parsed.document.sourceStatements.forEach { statement ->
                val literal = statement.path?.let { replacements[it] }
                if (literal == null) append(statement.text) else { found += statement.path!!; append(rewriteAssignment(statement.text, literal)) }
                append(statement.separator)
            }
            replacements.filterKeys { it !in found }.forEach { (path, literal) ->
                if (isNotEmpty() && last() != '\n') append('\n')
                append(path).append(" = ").append(literal).append('\n')
            }
        }
        val verified = ThemeLuaParser().parse(result)
        require(verified.diagnostics.none { it.severity == Severity.ERROR }) { "Updated style source failed verification parse" }
        return result
    }

    @JvmStatic fun previewColors(source: String, styleIds: Collection<String>): PreviewColors {
        val parsed = ThemeLuaParser().parse(source)
        val backgrounds = linkedMapOf<String, Int>(); val texts = linkedMapOf<String, Int>()
        (styleIds.map { normalizedId(it) } + "key").toSet().forEach { id ->
            resolveColor(parsed.document, id, "background", hashSetOf())?.let { backgrounds[id] = it }
            resolveColor(parsed.document, id, "text_color", hashSetOf())?.let { texts[id] = it }
        }
        return PreviewColors(backgrounds, texts)
    }

    private fun resolveColor(document: com.osfans.trime.editor.core.ThemeDocument, id: String, field: String, seen: MutableSet<String>): Int? {
        if (!seen.add(id)) return null
        val exact = document.sourceStatements.lastOrNull { it.path == "$id.$field" }?.text?.let(::assignmentRhs)?.let(::parseColorValue)
        if (exact != null) return exact
        (document.get("$id.$field") as? ThemeValue.LuaNumber)?.value?.toLong()?.let { return it.toInt() }
        val root = document.get(id)
        val parent = (root as? ThemeValue.RawLuaNode)?.source?.let { Regex("^table\\.clone\\(\\s*([A-Za-z_][A-Za-z0-9_]*)\\s*\\)$").matchEntire(it.trim())?.groupValues?.get(1) }
        return if (parent != null) resolveColor(document, parent, field, seen) else if (root is ThemeValue.RawLuaNode) null else if (id != "key") resolveColor(document, "key", field, seen) else null
    }

    private fun assignmentRhs(text: String): String {
        val equals = text.indexOf('='); if (equals < 0) return ""
        val raw = text.substring(equals + 1); val comment = raw.indexOf("--")
        return (if (comment < 0) raw else raw.substring(0, comment)).trim()
    }

    private fun parseColorValue(value: String): Int? {
        if (value == "nil") return null
        return runCatching { parseUnsignedColor(value).toInt() }.getOrNull()
    }

    @JvmStatic fun effectiveStyleId(key: ThemeEditorModel.Key, entityIds: Collection<String>): String = when {
        key.keyStyle.isNotBlank() -> normalizedId(key.keyStyle)
        key.click in entityIds -> normalizedId(key.click)
        else -> "key"
    }

    private fun styleExists(value: ThemeValue?, paths: List<String>, id: String): Boolean = value != null || paths.any { it == id || it.startsWith("$id.") }
    private fun normalizedId(value: String): String = value.ifBlank { "key" }.also { require(safeId.matches(it)) { "Unsafe style entity ID: $it" } }

    private fun colorLiteral(value: String): String {
        if (value.isBlank()) return "nil"
        val parsed = parseUnsignedColor(value)
        return "0x" + parsed.toString(16).padStart(8, '0')
    }

    private fun backgroundLiteral(value: String): String {
        if (value.isBlank()) return "nil"
        return runCatching { colorLiteral(value) }.getOrElse { colorError ->
            val trimmed = value.trim()
            if (trimmed.all { it.isDigit() } || trimmed.startsWith('#') || trimmed.startsWith("0x", true)) throw colorError
            require(!value.startsWith('/') && !value.startsWith('\\') && value.split('/', '\\').none { it == ".." } && value.none { it.code < 32 }) { "Background resource must be a safe project-relative path" }
            "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\""
        }
    }

    private fun parseUnsignedColor(value: String): Long {
        val trimmed = value.trim(); val match = color.matchEntire(trimmed)
        val hexadecimal = trimmed.startsWith('#') || trimmed.startsWith("0x", true) || trimmed.any { it in 'a'..'f' || it in 'A'..'F' }
        val parsed = if (match != null && hexadecimal) match.groupValues[1].toLong(16) else trimmed.toLong()
        require(parsed in 0..0xffffffffL) { "Color must fit #AARRGGBB" }
        return parsed
    }

    private fun ThemeValue.containsRawLua(): Boolean = when (this) {
        is ThemeValue.RawLuaNode -> true
        is ThemeValue.LuaTable -> fields.values.any { it.containsRawLua() }
        else -> false
    }

    private fun rewriteAssignment(text: String, literal: String): String {
        val equals = text.indexOf('='); require(equals >= 0) { "Malformed style assignment" }
        val comment = trailingComment(text, equals + 1)
        val prefix = text.substring(0, equals + 1).trimEnd()
        return if (comment < 0) "$prefix $literal" else "$prefix $literal " + text.substring(comment).trimStart()
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
}
