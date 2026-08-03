/*
 * SPDX-FileCopyrightText: 2025 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.osfans.trime.editor.ui

import com.osfans.trime.editor.core.Severity
import com.osfans.trime.editor.core.ThemeDocument
import com.osfans.trime.editor.core.ThemeLuaParser
import com.osfans.trime.editor.core.ThemeLuaWriter
import com.osfans.trime.editor.core.ThemeNode
import com.osfans.trime.editor.core.ThemeSourceStatement
import com.osfans.trime.editor.core.ThemeValue
import com.osfans.trime.editor.core.ThemeWriteMode

/**
 * Conservative source-only resolver for component style leaves.
 *
 * This class deliberately understands only literal assignments and `table.clone(identifier)`.
 * It never evaluates Lua. A dynamic result is information for the source editor, not a guessed
 * preview value; mutations of such a result are rejected.
 */
object ThemeComponentStyles {
    enum class FieldType { STRING, NUMBER, BOOLEAN, COLOR_OR_RESOURCE }

    /** Java-friendly resolved value. [literal] remains the lossless editor representation. */
    data class Value(
        val path: String,
        val type: FieldType,
        val literal: ThemeValue?,
        val stringValue: String? = (literal as? ThemeValue.LuaString)?.value,
        val numberValue: Double? = (literal as? ThemeValue.LuaNumber)?.value,
        val booleanValue: Boolean? = (literal as? ThemeValue.LuaBoolean)?.value,
        val colorValue: Long? = null,
        val resourceValue: String? = null,
        val explicit: Boolean = false,
        val sourcePath: String? = null,
        val inheritedFrom: String? = null,
        val trace: List<String> = emptyList(),
        val dynamic: Boolean = false,
        val diagnostic: String? = null,
        val compatibilityDiagnostic: String? = null,
    )

    private data class Field(val type: FieldType, val nonnegative: Boolean = false, val integer: Boolean = false)
    private data class Hit(
        val literal: ThemeValue? = null,
        val explicit: Boolean = false,
        val sourcePath: String? = null,
        val inheritedFrom: String? = null,
        val trace: List<String> = emptyList(),
        val dynamic: Boolean = false,
        val diagnostic: String? = null,
        val statementIndex: Int = -1,
        val assignmentPath: String? = null,
    )

    private data class Source(val text: String, val document: ThemeDocument)
    private enum class TableState { TABLE, MISSING, SCALAR, DYNAMIC }

    private val simpleIdentifier = Regex("^[A-Za-z_][A-Za-z0-9_]*$")
    private val cloneCall = Regex("^table\\.clone\\(\\s*([A-Za-z_][A-Za-z0-9_.]*)\\s*\\)$")
    private val hexadecimal = Regex("^(?:#|0[xX])([0-9A-Fa-f]{1,8})$")
    private val gravities = setOf("left", "top", "right", "bottom")
    private val tabGravities = setOf("top", "bottom")
    private val inlineValues = setOf("none", "input", "composition", "preedit", "preview", "true")
    private val positions = setOf(
        "left", "right", "left_up", "right_up", "drag", "fixed",
        "bottom_left", "bottom_right", "top_left", "top_right",
    )
    private val movableValues = setOf("true", "false", "once")

    private const val MISSING = "__static_missing__"
    private val fields: Map<String, Field> = buildRegistry()

    @JvmStatic
    fun read(source: String, path: String): Value {
        val field = requireField(path)
        val parsed = parse(source)
        rejectDuplicateExact(parsed.document, path)
        val hit = resolve(parsed, path, parsed.document.sourceStatements.size, linkedSetOf(), emptyList(), null)
        val opaque = ambiguousAccess(parsed.document, path.substringBefore('.'), hit.statementIndex)
        if (opaque != null) return dynamicValue(path, field, opaque, hit.trace, hit.inheritedFrom)
        if (hit.dynamic) return dynamicValue(path, field, hit.diagnostic ?: "Dynamic component style", hit.trace, hit.inheritedFrom)
        val publicHit = if (hit.diagnostic == MISSING) hit.copy(diagnostic = null) else hit
        val literal = publicHit.literal
        if (literal != null && literal !is ThemeValue.LuaNil) validateLiteral(path, field, literal, strictEnums = false)
        val color = if (field.type == FieldType.COLOR_OR_RESOURCE) (literal as? ThemeValue.LuaNumber)?.value?.toLong() else null
        val resource = if (field.type == FieldType.COLOR_OR_RESOURCE) (literal as? ThemeValue.LuaString)?.value else null
        return Value(
            path = path,
            type = field.type,
            literal = literal,
            colorValue = color,
            resourceValue = resource,
            explicit = publicHit.explicit,
            sourcePath = publicHit.sourcePath,
            inheritedFrom = publicHit.inheritedFrom,
            trace = publicHit.trace,
            compatibilityDiagnostic = compatibility(path, literal),
        )
    }

    /** A null value removes the effective explicit source field instead of writing Lua `nil`. */
    @JvmStatic
    fun update(source: String, path: String, valueOrNull: ThemeValue?): String {
        val field = requireField(path)
        if (valueOrNull != null) {
            require(valueOrNull !is ThemeValue.LuaNil && valueOrNull !is ThemeValue.RawLuaNode && valueOrNull !is ThemeValue.LuaTable) {
                "Component fields require a literal scalar; use remove() to clear one"
            }
            validateLiteral(path, field, valueOrNull, strictEnums = true)
        }
        val parsed = parse(source)
        rejectDuplicateExact(parsed.document, path)
        val hit = resolve(parsed, path, parsed.document.sourceStatements.size, linkedSetOf(), emptyList(), null)
        ambiguousAccess(parsed.document, path.substringBefore('.'), hit.statementIndex)
            ?.let { throw IllegalArgumentException(it) }
        require(!hit.dynamic) { hit.diagnostic ?: "Dynamic component style requires the Lua source page" }

        val output = if (valueOrNull == null) {
            removeEffective(parsed, path, hit)
        } else {
            writeEffective(parsed, path, hit, valueOrNull)
        }
        parse(output) // Parse-after-write is part of this API's contract.
        if (valueOrNull != null) {
            val verified = read(output, path)
            require(!verified.dynamic && verified.literal == valueOrNull && verified.explicit) {
                "Updated component field could not be resolved safely"
            }
        }
        return output
    }

    @JvmStatic fun updateString(source: String, path: String, value: String?): String =
        update(source, path, value?.let { ThemeValue.LuaString(it) })

    /** Preserves boolean `true` as a distinct source spelling; current getString runtime treats it as none. */
    @JvmStatic
    fun updatePreeditInline(source: String, value: String?, booleanTrue: Boolean = false): String {
        require(!booleanTrue || value == null) { "Choose either a string inline mode or boolean true" }
        return update(
            source,
            "preedit.inline",
            if (booleanTrue) ThemeValue.LuaBoolean(true) else value?.let { ThemeValue.LuaString(it) },
        )
    }

    @JvmStatic fun updateNumber(source: String, path: String, value: Double?): String =
        update(source, path, value?.let { ThemeValue.LuaNumber(it) })

    @JvmStatic fun updateBoolean(source: String, path: String, value: Boolean?): String =
        update(source, path, value?.let { ThemeValue.LuaBoolean(it) })

    /** Accepts #AARRGGBB, 0xAARRGGBB, unsigned decimal, or a safe project-relative resource. */
    @JvmStatic
    fun updateColorOrResource(source: String, path: String, value: String?): String {
        if (value == null) return remove(source, path)
        val trimmed = value.trim()
        val literal = parseColor(trimmed)?.let { ThemeValue.LuaNumber(it.toDouble()) }
            ?: ThemeValue.LuaString(value.also(::validateResource))
        return update(source, path, literal)
    }

    @JvmStatic
    fun updateColorOrResource(source: String, path: String, value: Long): String {
        require(value in 0..0xffffffffL) { "Color must be an unsigned 32-bit integer" }
        return update(source, path, ThemeValue.LuaNumber(value.toDouble()))
    }

    @JvmStatic fun remove(source: String, path: String): String = update(source, path, null)

    @JvmStatic fun fieldType(path: String): FieldType = requireField(path).type
    @JvmStatic fun supportedPaths(): Set<String> = fields.keys

    /** Returns null when table existence cannot be proven without evaluating Lua. */
    @JvmStatic
    fun staticTablePresence(source: String, path: String): Boolean? {
        require(path.split('.').all(simpleIdentifier::matches)) { "Invalid static table path" }
        val parsed = parse(source)
        if (ambiguousAccess(parsed.document, path.substringBefore('.'), -1) != null) return null
        return when (tableState(parsed, path, parsed.document.sourceStatements.size, linkedSetOf())) {
            TableState.TABLE -> true
            TableState.MISSING -> false
            TableState.SCALAR, TableState.DYNAMIC -> null
        }
    }

    private fun resolve(
        source: Source,
        path: String,
        limit: Int,
        visiting: MutableSet<String>,
        trace: List<String>,
        inheritedFrom: String?,
    ): Hit {
        val visitKey = path
        if (!visiting.add(visitKey)) return Hit(
            dynamic = true,
            diagnostic = "Cycle while resolving table.clone fallback for $path",
            trace = trace + path,
            inheritedFrom = inheritedFrom,
        )
        try {
            val statements = source.document.sourceStatements
            for (index in minOf(limit, statements.size) - 1 downTo 0) {
                val assignment = statements[index].path ?: continue
                if (assignment != path && !path.startsWith("$assignment.")) continue
                val rootValue = statementValue(statements[index], assignment)
                val relative = path.split('.').drop(assignment.split('.').size)
                return resolveValue(
                    source, rootValue, relative, index, assignment,
                    trace + assignment, inheritedFrom, visiting,
                )
            }
            return Hit(trace = trace, inheritedFrom = inheritedFrom)
        } finally {
            visiting.remove(visitKey)
        }
    }

    private fun resolveValue(
        source: Source,
        initial: ThemeValue,
        relative: List<String>,
        statementIndex: Int,
        assignmentPath: String,
        trace: List<String>,
        inheritedFrom: String?,
        visiting: MutableSet<String>,
    ): Hit {
        var value = initial
        var consumed = 0
        while (consumed < relative.size && value is ThemeValue.LuaTable) {
            value = value.fields[relative[consumed]] ?: return Hit(
                trace = trace + "${assignmentPath}.${relative.take(consumed + 1).joinToString(".")} (missing)",
                inheritedFrom = inheritedFrom,
                diagnostic = MISSING,
                statementIndex = statementIndex,
                assignmentPath = assignmentPath,
            )
            consumed++
        }
        if (value is ThemeValue.RawLuaNode) {
            val match = cloneCall.matchEntire(value.source.trim())
            if (match == null) return Hit(
                dynamic = true,
                diagnostic = "Dynamic assignment at $assignmentPath requires the Lua source page",
                trace = trace,
                inheritedFrom = inheritedFrom,
                statementIndex = statementIndex,
                assignmentPath = assignmentPath,
            )
            val target = match.groupValues[1]
            if (!simpleIdentifier.matches(target)) return Hit(
                dynamic = true,
                diagnostic = "Nested table.clone target '$target' is unsupported",
                trace = trace + "$assignmentPath -> $target",
                inheritedFrom = inheritedFrom ?: target,
                statementIndex = statementIndex,
                assignmentPath = assignmentPath,
            )
            val suffix = relative.drop(consumed)
            if (suffix.isEmpty()) return Hit(
                dynamic = true,
                diagnostic = "table.clone result at $assignmentPath is a table, not a literal field",
                trace = trace + "$assignmentPath -> $target",
                inheritedFrom = inheritedFrom ?: target,
                statementIndex = statementIndex,
                assignmentPath = assignmentPath,
            )
            val targetPath = (listOf(target) + suffix).joinToString(".")
            val fallback = resolve(
                source, targetPath, statementIndex, visiting,
                trace + "$assignmentPath -> $target", inheritedFrom ?: target,
            )
            // The source owner of an inherited literal is its parent assignment, while mutation
            // safety must continue to be based on the clone statement that exists at runtime.
            return fallback.copy(
                explicit = false,
                statementIndex = statementIndex,
                assignmentPath = assignmentPath,
            )
        }
        if (consumed < relative.size) return Hit(
            dynamic = value !is ThemeValue.LuaTable,
            diagnostic = if (value !is ThemeValue.LuaTable) "Non-table ancestor at $assignmentPath" else null,
            trace = trace,
            inheritedFrom = inheritedFrom,
            statementIndex = statementIndex,
            assignmentPath = assignmentPath,
        )
        return Hit(
            literal = value,
            explicit = inheritedFrom == null,
            sourcePath = assignmentPath,
            inheritedFrom = inheritedFrom,
            trace = trace,
            statementIndex = statementIndex,
            assignmentPath = assignmentPath,
        )
    }

    private fun writeEffective(source: Source, path: String, hit: Hit, value: ThemeValue): String {
        val statements = source.document.sourceStatements
        val exactIndices = statements.indices.filter { statements[it].path == path }
        val winner = hit.statementIndex
        if (winner >= 0 && statements[winner].path == path && hit.inheritedFrom == null) {
            return replaceStatement(source, winner, render(value))
        }
        if (winner >= 0 && statements[winner].path != path) {
            val owner = statementValue(statements[winner], statements[winner].path!!)
            require(!owner.containsRaw()) { "Containing table has dynamic fields; add a safe dotted override or use the Lua source page" }
        }

        // If a later literal ancestor shadows an old dotted assignment, update that winner. This
        // avoids manufacturing duplicate exact assignments with ambiguous editor ownership.
        if (exactIndices.isNotEmpty() && winner > exactIndices.single()) {
            // Drop the stale, shadowed exact statement first, then use the normal safe insertion
            // rules after the winning ancestor. This preserves comments inside the ancestor table.
            val cleaned = parse(deleteStatement(source, exactIndices.single()))
            val cleanedHit = resolve(
                cleaned, path, cleaned.document.sourceStatements.size,
                linkedSetOf(), emptyList(), null,
            )
            require(!cleanedHit.dynamic) { cleanedHit.diagnostic ?: "Later dynamic ancestor of $path" }
            return writeEffective(cleaned, path, cleanedHit, value)
        }

        val parts = path.split('.')
        val rootState = tableState(source, parts.first(), statements.size, linkedSetOf())
        if (rootState == TableState.MISSING) {
            var root: ThemeValue = value
            parts.drop(1).asReversed().forEach { key -> root = ThemeValue.LuaTable(linkedMapOf(key to root)) }
            return appendAssignment(source.text, "${parts.first()} = ${render(root)}")
        }
        require(rootState == TableState.TABLE) { "Component root '${parts.first()}' is not provably a runtime table" }

        var firstMissing = -1
        for (size in 2 until parts.size) {
            when (tableState(source, parts.take(size).joinToString("."), statements.size, linkedSetOf())) {
                TableState.TABLE -> Unit
                TableState.MISSING -> { firstMissing = size; break }
                TableState.SCALAR -> throw IllegalArgumentException("Component ancestor '${parts.take(size).joinToString(".")}' is not a table")
                TableState.DYNAMIC -> throw IllegalArgumentException("Component ancestor '${parts.take(size).joinToString(".")}' is dynamic")
            }
        }
        if (firstMissing < 0) return appendAssignment(source.text, "$path = ${render(value)}")

        val assignmentPath = parts.take(firstMissing).joinToString(".")
        var nested: ThemeValue = value
        parts.drop(firstMissing).asReversed().forEach { key -> nested = ThemeValue.LuaTable(linkedMapOf(key to nested)) }
        return appendAssignment(source.text, "$assignmentPath = ${render(nested)}")
    }

    private fun removeEffective(source: Source, path: String, hit: Hit): String {
        if (!hit.explicit || hit.statementIndex < 0) return source.text
        val statement = source.document.sourceStatements[hit.statementIndex]
        if (statement.path == path) return deleteStatement(source, hit.statementIndex)
        val assignment = statement.path ?: return source.text
        val owner = statementValue(statement, assignment) as? ThemeValue.LuaTable
            ?: throw IllegalArgumentException("Explicit $path is not in a literal table")
        require(!owner.containsRaw()) { "Containing table has dynamic fields; removing an inline field requires the Lua source page" }
        val relative = path.split('.').drop(assignment.split('.').size)
        val updated = removeNested(owner, relative)
        return replaceStatement(source, hit.statementIndex, render(updated))
    }

    private fun tableState(source: Source, path: String, limit: Int, visiting: MutableSet<String>): TableState {
        val key = path
        if (!visiting.add(key)) return TableState.DYNAMIC
        try {
            val statements = source.document.sourceStatements
            for (index in minOf(limit, statements.size) - 1 downTo 0) {
                val assignment = statements[index].path ?: continue
                if (assignment != path && !path.startsWith("$assignment.")) continue
                var value = statementValue(statements[index], assignment)
                val relative = path.split('.').drop(assignment.split('.').size)
                var consumed = 0
                while (consumed < relative.size && value is ThemeValue.LuaTable) {
                    val child = value.fields[relative[consumed]]
                    if (child == null) { consumed = -1; break }
                    value = child
                    consumed++
                }
                if (consumed < 0) continue
                if (value is ThemeValue.RawLuaNode) {
                    val clone = cloneCall.matchEntire(value.source.trim()) ?: return TableState.DYNAMIC
                    val target = clone.groupValues[1]
                    if (!simpleIdentifier.matches(target)) return TableState.DYNAMIC
                    val suffix = relative.drop(consumed)
                    val fallback = tableState(source, (listOf(target) + suffix).joinToString("."), index, visiting)
                    return if (fallback == TableState.MISSING && suffix.isEmpty()) TableState.DYNAMIC else fallback
                }
                if (consumed < relative.size) return TableState.SCALAR
                return if (value is ThemeValue.LuaTable) TableState.TABLE else TableState.SCALAR
            }
            return TableState.MISSING
        } finally {
            visiting.remove(key)
        }
    }

    private fun validateLiteral(path: String, field: Field, value: ThemeValue, strictEnums: Boolean) {
        when (field.type) {
            FieldType.STRING -> {
                if (path == "preedit.inline" && value is ThemeValue.LuaBoolean) {
                    require(!strictEnums || value.value) { "preedit.inline only writes boolean true; false remains source-only" }
                    return
                }
                val text = (value as? ThemeValue.LuaString)?.value
                    ?: throw IllegalArgumentException("$path must be a literal string")
                when {
                    path.endsWith(".gravity") -> require(!strictEnums || text in if (path.contains(".tab_bar.")) tabGravities else gravities) { "Invalid gravity for $path" }
                    path == "preedit.inline" -> require(!strictEnums || text in inlineValues) { "preedit.inline must be one of ${inlineValues.joinToString()}" }
                    path == "composition.position" -> require(!strictEnums || text in positions) { "Invalid composition.position" }
                    path == "composition.movable" -> require(!strictEnums || text in movableValues) { "composition.movable must be true, false, or once as a string" }
                }
            }
            FieldType.NUMBER -> {
                val number = (value as? ThemeValue.LuaNumber)?.value
                    ?: throw IllegalArgumentException("$path must be a literal number")
                require(number.isFinite()) { "$path must be finite" }
                require(!strictEnums || !field.integer || number % 1.0 == 0.0) { "$path must be an integer for the Trime2 runtime" }
                val sentinel = path == "composition.max_entries" && number == -1.0
                require(!field.nonnegative || number >= 0.0 || sentinel) { "$path must be nonnegative${if (path == "composition.max_entries") " or -1" else ""}" }
            }
            FieldType.BOOLEAN -> require(value is ThemeValue.LuaBoolean) { "$path must be a literal boolean" }
            FieldType.COLOR_OR_RESOURCE -> when (value) {
                is ThemeValue.LuaNumber -> {
                    require(value.value.isFinite() && value.value % 1.0 == 0.0 && value.value >= 0.0 && value.value <= 0xffffffffL.toDouble()) {
                        "$path color must be a finite unsigned 32-bit integer"
                    }
                }
                is ThemeValue.LuaString -> validateResource(value.value)
                else -> throw IllegalArgumentException("$path must be an unsigned color or safe project-relative resource")
            }
        }
    }

    private fun validateResource(value: String) {
        require(value.isNotBlank()) { "Resource path must not be blank" }
        require(!value.startsWith('/') && !value.startsWith('\\')) { "Resource path must be project-relative" }
        require(!Regex("^[A-Za-z][A-Za-z0-9+.-]*:").containsMatchIn(value)) { "Resource URI schemes are not allowed" }
        require(value.split('/', '\\').none { it == ".." }) { "Resource traversal is not allowed" }
        require(value.none { it.code < 32 || it.code == 127 }) { "Resource path contains control characters" }
    }

    private fun parseColor(value: String): Long? {
        hexadecimal.matchEntire(value)?.let { return it.groupValues[1].toLong(16) }
        if (value.all(Char::isDigit) && value.isNotEmpty()) return value.toLongOrNull()?.takeIf { it in 0..0xffffffffL }
        return null
    }

    private fun compatibility(path: String, literal: ThemeValue?): String? = when {
        fields[path]?.integer == true && literal is ThemeValue.LuaNumber && literal.value % 1.0 != 0.0 ->
            "$path is preserved but the Trime2 integer getter uses its runtime fallback"
        path == "composition.line_spacing_multiplier" && (literal as? ThemeValue.LuaNumber)?.value == 0.0 ->
            "Preview normalizes line_spacing_multiplier=0 to 1"
        path == "composition.position" && literal is ThemeValue.LuaString && literal.value.lowercase(java.util.Locale.ROOT) !in positions ->
            "Unknown composition.position is preserved and previewed as fixed"
        path == "composition.movable" && literal is ThemeValue.LuaString && literal.value !in movableValues ->
            "Unknown composition.movable is preserved; the current runtime treats every string except false as movable true"
        path == "preedit.inline" && literal is ThemeValue.LuaString && literal.value !in inlineValues ->
            "Unknown preedit.inline is preserved and previewed as none"
        path == "preedit.inline" && literal is ThemeValue.LuaBoolean && literal.value ->
            "Boolean true is preserved, but the current Style.getString runtime previews it as none"
        path == "preedit.inline" && literal is ThemeValue.LuaBoolean ->
            "Boolean false is preserved and previewed as none"
        else -> null
    }

    private fun dynamicValue(path: String, field: Field, diagnostic: String, trace: List<String> = emptyList(), inheritedFrom: String? = null) =
        Value(path, field.type, null, inheritedFrom = inheritedFrom, trace = trace, dynamic = true, diagnostic = diagnostic)

    private fun requireField(path: String): Field {
        require(path != "composition.window" && !path.startsWith("composition.window.")) {
            "composition.window is source-only and cannot be patched generically"
        }
        return fields[path] ?: throw IllegalArgumentException("Unsupported component style field: $path")
    }

    private fun rejectDuplicateExact(document: ThemeDocument, path: String) {
        require(document.sourceStatements.count { it.path == path } <= 1) {
            "Duplicate exact assignments for $path require the Lua source page"
        }
    }

    private fun ambiguousAccess(document: ThemeDocument, root: String, afterIndex: Int): String? {
        val access = Regex("\\b${Regex.escape(root)}\\b")
        return document.sourceStatements.withIndex().firstOrNull { (index, statement) ->
            index > afterIndex && statement.root == null && access.containsMatchIn(visibleLua(statement.text))
        }?.let { "Assignment precedence for '$root' is not provable because of later unsupported Lua" }
    }

    private fun statementValue(statement: ThemeSourceStatement, path: String): ThemeValue {
        val parsed = ThemeLuaParser().parse(statement.text)
        require(parsed.diagnostics.none { it.severity == Severity.ERROR }) { "Lua source contains errors" }
        return parsed.document.get(path)
            ?: throw IllegalArgumentException("Assignment precedence for $path cannot be proven")
    }

    private fun parse(source: String): Source {
        val result = ThemeLuaParser().parse(source)
        require(result.diagnostics.none { it.severity == Severity.ERROR }) { "Lua source contains errors" }
        require(result.diagnostics.none { it.message.startsWith("Unsupported table key") }) {
            "Unsupported table keys require the Lua source page"
        }
        return Source(source, result.document)
    }

    private fun setNested(current: ThemeValue, path: List<String>, value: ThemeValue): ThemeValue {
        if (path.isEmpty()) return value
        val table = current as? ThemeValue.LuaTable ?: throw IllegalArgumentException("Cannot overwrite a non-table ancestor")
        val fields = LinkedHashMap(table.fields)
        val child = fields[path.first()]
        fields[path.first()] = if (path.size == 1) value else setNested(child ?: ThemeValue.LuaTable(), path.drop(1), value)
        return ThemeValue.LuaTable(fields)
    }

    private fun removeNested(current: ThemeValue, path: List<String>): ThemeValue {
        if (path.isEmpty()) return current
        val table = current as? ThemeValue.LuaTable ?: return current
        val fields = LinkedHashMap(table.fields)
        if (path.size == 1) fields.remove(path.first())
        else fields[path.first()]?.let { fields[path.first()] = removeNested(it, path.drop(1)) }
        return ThemeValue.LuaTable(fields)
    }

    private fun replaceStatement(source: Source, index: Int, rhs: String): String = buildString {
        source.document.sourceStatements.forEachIndexed { current, statement ->
            append(if (current == index) replaceRhs(statement.text, rhs) else statement.text)
            append(statement.separator)
        }
    }

    private fun deleteStatement(source: Source, index: Int): String = buildString {
        source.document.sourceStatements.forEachIndexed { current, statement ->
            if (current != index) append(statement.text).append(statement.separator)
        }
    }

    private fun replaceRhs(statement: String, rendered: String): String {
        val equals = topLevelEquals(statement)
        require(equals >= 0) { "Assignment cannot be rewritten safely" }
        var rhs = equals + 1
        while (rhs < statement.length && statement[rhs] in " \t") rhs++
        val comment = topLevelComment(statement, rhs)
        val end = if (comment < 0) statement.length else comment
        val trailing = statement.substring(rhs, end).takeLastWhile { it == ' ' || it == '\t' }
        return statement.substring(0, rhs) + rendered + trailing + if (comment < 0) "" else statement.substring(comment)
    }

    private fun topLevelEquals(source: String): Int {
        var quote = '\u0000'; var depth = 0; var index = 0
        while (index < source.length) {
            val char = source[index]
            if (quote != '\u0000') { if (char == '\\') index++ else if (char == quote) quote = '\u0000' }
            else if (char == '-' && index + 1 < source.length && source[index + 1] == '-') while (index < source.length && source[index] != '\n') index++
            else when (char) {
                '\'', '"' -> quote = char
                '{', '(', '[' -> depth++
                '}', ')', ']' -> depth = (depth - 1).coerceAtLeast(0)
                '=' -> if (depth == 0) return index
            }
            index++
        }
        return -1
    }

    private fun topLevelComment(source: String, start: Int): Int {
        var quote = '\u0000'; var depth = 0; var index = start
        while (index + 1 < source.length) {
            val char = source[index]
            if (quote != '\u0000') { if (char == '\\') index++ else if (char == quote) quote = '\u0000' }
            else when {
                char == '\'' || char == '"' -> quote = char
                char == '{' || char == '(' || char == '[' -> depth++
                char == '}' || char == ')' || char == ']' -> depth = (depth - 1).coerceAtLeast(0)
                char == '-' && source[index + 1] == '-' && depth == 0 -> return index
            }
            index++
        }
        return -1
    }

    private fun appendAssignment(source: String, assignment: String): String = when {
        source.isEmpty() -> "$assignment\n"
        source.endsWith("\n") -> source + assignment + "\n"
        else -> source + "\n" + assignment
    }

    private fun render(value: ThemeValue): String = ThemeLuaWriter.write(
        ThemeDocument(listOf(ThemeNode("value", 0, value)), trailingNewline = false),
        ThemeWriteMode.STRUCTURED,
    ).substringAfter("value = ")

    /** Masks quoted strings and line comments before conservative opaque-access checks. */
    private fun visibleLua(source: String): String = buildString(source.length) {
        var quote = '\u0000'; var index = 0
        while (index < source.length) {
            val char = source[index]
            if (quote != '\u0000') {
                append(' ')
                if (char == '\\' && index + 1 < source.length) { append(' '); index++ }
                else if (char == quote) quote = '\u0000'
            } else if (char == '\'' || char == '"') { quote = char; append(' ') }
            else if (char == '-' && index + 1 < source.length && source[index + 1] == '-') {
                while (index < source.length && source[index] != '\n') { append(' '); index++ }
                if (index < source.length) append('\n')
            } else append(char)
            index++
        }
    }

    private fun ThemeValue.containsRaw(): Boolean = when (this) {
        is ThemeValue.RawLuaNode -> true
        is ThemeValue.LuaTable -> fields.values.any { it.containsRaw() }
        else -> false
    }

    private fun buildRegistry(): Map<String, Field> {
        val result = linkedMapOf<String, Field>()
        fun add(path: String, type: FieldType, nonnegative: Boolean = false, integer: Boolean = false) { result[path] = Field(type, nonnegative, integer) }
        fun color(path: String) = add(path, FieldType.COLOR_OR_RESOURCE)
        fun number(path: String, nonnegative: Boolean = true) = add(path, FieldType.NUMBER, nonnegative, integer = false)
        fun integer(path: String, nonnegative: Boolean = true) = add(path, FieldType.NUMBER, nonnegative, integer = true)
        fun string(path: String) = add(path, FieldType.STRING)
        fun bool(path: String) = add(path, FieldType.BOOLEAN)
        fun keyStyle(path: String, includeText: Boolean = true) {
            if (includeText) string("$path.text")
            color("$path.background"); color("$path.text_color")
            integer("$path.text_size"); integer("$path.elevation"); number("$path.corner_radius"); color("$path.shadow_color")
            color("$path.pressed.background"); color("$path.pressed.text_color")
            number("$path.pressed.scale_x"); number("$path.pressed.scale_y")
            integer("$path.pressed.translation_x", false); integer("$path.pressed.translation_y", false); integer("$path.pressed.translation_z", false)
            color("$path.pressed.shadow_color")
            color("$path.hint.background"); color("$path.hint.text_color"); integer("$path.hint.text_size")
            color("$path.pressed.hint.background"); color("$path.pressed.hint.text_color"); integer("$path.pressed.hint.text_size")
        }
        keyStyle("key")

        fun candidate(path: String) {
            integer("$path.height"); color("$path.background"); color("$path.text_color"); integer("$path.text_size")
            integer("$path.elevation"); color("$path.shadow_color")
            color("$path.pressed.background"); color("$path.pressed.text_color")
            color("$path.comment.text_color"); integer("$path.comment.text_size")
            color("$path.comment.pressed.text_color"); integer("$path.comment.pressed.text_size")
            keyStyle("$path.key")
        }
        candidate("candidate"); candidate("candidate.expanded")

        integer("toolbar.height"); color("toolbar.background"); color("toolbar.text_color")
        integer("toolbar.elevation"); color("toolbar.shadow_color"); bool("toolbar.schema_switches")
        keyStyle("toolbar.hide"); keyStyle("toolbar.key")

        color("symbol.background"); color("symbol.indicator_color")
        keyStyle("symbol.text"); keyStyle("symbol.key")
        string("symbol.tab_bar.gravity"); integer("symbol.tab_bar.height"); color("symbol.tab_bar.indicator_color")
        string("symbol.tool_bar.gravity"); integer("symbol.tool_bar.height"); keyStyle("symbol.tool_bar")

        color("clipboard.background"); color("clipboard.indicator_color")
        keyStyle("clipboard.key"); keyStyle("clipboard.item")
        string("clipboard.tab_bar.gravity"); integer("clipboard.tab_bar.height"); color("clipboard.tab_bar.indicator_color")
        string("clipboard.tool_bar.gravity"); integer("clipboard.tool_bar.height"); keyStyle("clipboard.tool_bar")

        bool("preedit.show"); color("preedit.background"); color("preedit.text_color"); integer("preedit.text_size"); string("preedit.inline")

        bool("composition.show"); color("composition.background"); color("composition.text_color"); integer("composition.text_size")
        string("composition.position")
        listOf("min_length", "max_length", "sticky_lines", "max_entries", "cloud_max_entries", "border", "max_width", "max_height", "min_width", "min_height", "spacing", "round_corner", "elevation")
            .forEach { integer("composition.$it") }
        number("composition.line_spacing"); number("composition.line_spacing_multiplier")
        listOf("left", "top", "right", "bottom").forEach { integer("composition.padding.$it") }
        bool("composition.all_phrases"); bool("composition.use_cursor"); string("composition.movable")
        color("composition.pressed.background"); color("composition.pressed.text_color")
        keyStyle("composition.key")
        return result
    }
}
