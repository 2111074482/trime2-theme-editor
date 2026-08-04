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
        if (hit.dynamic) return dynamicValue(path, field, hit.diagnostic ?: "动态组件样式", hit.trace, hit.inheritedFrom)
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
                "组件字段必须是字面标量;如需清除请使用移除操作"
            }
            validateLiteral(path, field, valueOrNull, strictEnums = true)
        }
        val parsed = parse(source)
        rejectDuplicateExact(parsed.document, path)
        val hit = resolve(parsed, path, parsed.document.sourceStatements.size, linkedSetOf(), emptyList(), null)
        ambiguousAccess(parsed.document, path.substringBefore('.'), hit.statementIndex)
            ?.let { throw IllegalArgumentException(it) }
        require(!hit.dynamic) { hit.diagnostic ?: "动态组件样式必须在 Lua 源代码页编辑" }

        val output = if (valueOrNull == null) {
            removeEffective(parsed, path, hit)
        } else {
            writeEffective(parsed, path, hit, valueOrNull)
        }
        parse(output) // Parse-after-write is part of this API's contract.
        if (valueOrNull != null) {
            val verified = read(output, path)
            require(!verified.dynamic && verified.literal == valueOrNull && verified.explicit) {
                "更新后的组件字段无法安全解析"
            }
        }
        return output
    }

    @JvmStatic fun updateString(source: String, path: String, value: String?): String =
        update(source, path, value?.let { ThemeValue.LuaString(it) })

    /** Preserves boolean `true` as a distinct source spelling; current getString runtime treats it as none. */
    @JvmStatic
    fun updatePreeditInline(source: String, value: String?, booleanTrue: Boolean = false): String {
        require(!booleanTrue || value == null) { "请选择字符串内联模式或布尔值 true" }
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
        require(value in 0..0xffffffffL) { "颜色必须是无符号 32 位整数" }
        return update(source, path, ThemeValue.LuaNumber(value.toDouble()))
    }

    @JvmStatic fun remove(source: String, path: String): String = update(source, path, null)

    @JvmStatic fun fieldType(path: String): FieldType = requireField(path).type
    @JvmStatic fun supportedPaths(): Set<String> = fields.keys

    /** Returns null when table existence cannot be proven without evaluating Lua. */
    @JvmStatic
    fun staticTablePresence(source: String, path: String): Boolean? {
        require(path.split('.').all(simpleIdentifier::matches)) { "静态表路径无效" }
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
            diagnostic = "解析 $path 的表克隆(table.clone)回退时发现循环",
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
                diagnostic = "$assignmentPath 的动态赋值必须在 Lua 源代码页编辑",
                trace = trace,
                inheritedFrom = inheritedFrom,
                statementIndex = statementIndex,
                assignmentPath = assignmentPath,
            )
            val target = match.groupValues[1]
            if (!simpleIdentifier.matches(target)) return Hit(
                dynamic = true,
                diagnostic = "不支持嵌套表克隆(table.clone)目标 '$target'",
                trace = trace + "$assignmentPath -> $target",
                inheritedFrom = inheritedFrom ?: target,
                statementIndex = statementIndex,
                assignmentPath = assignmentPath,
            )
            val suffix = relative.drop(consumed)
            if (suffix.isEmpty()) return Hit(
                dynamic = true,
                diagnostic = "$assignmentPath 的表克隆(table.clone)结果是表,不是字面字段",
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
            diagnostic = if (value !is ThemeValue.LuaTable) "$assignmentPath 的上级不是表" else null,
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
            require(!owner.containsRaw()) { "所属表包含动态字段;请添加安全的点路径覆盖或使用 Lua 源代码页" }
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
            require(!cleanedHit.dynamic) { cleanedHit.diagnostic ?: "$path 之后存在动态上级赋值" }
            return writeEffective(cleaned, path, cleanedHit, value)
        }

        val parts = path.split('.')
        val rootState = tableState(source, parts.first(), statements.size, linkedSetOf())
        if (rootState == TableState.MISSING) {
            var root: ThemeValue = value
            parts.drop(1).asReversed().forEach { key -> root = ThemeValue.LuaTable(linkedMapOf(key to root)) }
            return appendAssignment(source.text, "${parts.first()} = ${render(root)}")
        }
        require(rootState == TableState.TABLE) { "无法证明组件根 '${parts.first()}' 是运行时表" }

        var firstMissing = -1
        for (size in 2 until parts.size) {
            when (tableState(source, parts.take(size).joinToString("."), statements.size, linkedSetOf())) {
                TableState.TABLE -> Unit
                TableState.MISSING -> { firstMissing = size; break }
                TableState.SCALAR -> throw IllegalArgumentException("组件上级 '${parts.take(size).joinToString(".")}' 不是表")
                TableState.DYNAMIC -> throw IllegalArgumentException("组件上级 '${parts.take(size).joinToString(".")}' 是动态内容")
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
            ?: throw IllegalArgumentException("显式字段 $path 不在字面表中")
        require(!owner.containsRaw()) { "所属表包含动态字段;移除内联字段必须使用 Lua 源代码页" }
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
                    require(!strictEnums || value.value) { "预编辑内联(preedit.inline)只写出布尔值 true;false 仍仅支持源码编辑" }
                    return
                }
                val text = (value as? ThemeValue.LuaString)?.value
                    ?: throw IllegalArgumentException("$path 必须是字面字符串")
                when {
                    path.endsWith(".gravity") -> require(!strictEnums || text in if (path.contains(".tab_bar.")) tabGravities else gravities) { "$path 的重力方向(gravity)无效" }
                    path == "preedit.inline" -> require(!strictEnums || text in inlineValues) { "预编辑内联(preedit.inline)必须是以下值之一:${inlineValues.joinToString()}" }
                    path == "composition.position" -> require(!strictEnums || text in positions) { "编码窗口位置(composition.position)无效" }
                    path == "composition.movable" -> require(!strictEnums || text in movableValues) { "编码窗口可移动(composition.movable)必须是字符串 true、false 或 once" }
                }
            }
            FieldType.NUMBER -> {
                val number = (value as? ThemeValue.LuaNumber)?.value
                    ?: throw IllegalArgumentException("$path 必须是字面数值")
                require(number.isFinite()) { "$path 必须是有限数值" }
                require(!strictEnums || !field.integer || number % 1.0 == 0.0) { "$path 在 Trime2 运行时中必须为整数" }
                val sentinel = path == "composition.max_entries" && number == -1.0
                require(!field.nonnegative || number >= 0.0 || sentinel) { "$path 必须非负${if (path == "composition.max_entries") "或为 -1" else ""}" }
            }
            FieldType.BOOLEAN -> require(value is ThemeValue.LuaBoolean) { "$path 必须是字面布尔值" }
            FieldType.COLOR_OR_RESOURCE -> when (value) {
                is ThemeValue.LuaNumber -> {
                    require(value.value.isFinite() && value.value % 1.0 == 0.0 && value.value >= 0.0 && value.value <= 0xffffffffL.toDouble()) {
                        "$path 颜色必须是有限的无符号 32 位整数"
                    }
                }
                is ThemeValue.LuaString -> validateResource(value.value)
                else -> throw IllegalArgumentException("$path 必须是无符号颜色或安全的项目相对资源")
            }
        }
    }

    private fun validateResource(value: String) {
        require(value.isNotBlank()) { "资源路径不能为空" }
        require(!value.startsWith('/') && !value.startsWith('\\')) { "资源路径必须相对于项目" }
        require(!Regex("^[A-Za-z][A-Za-z0-9+.-]*:").containsMatchIn(value)) { "资源路径不允许 URI 协议" }
        require(value.split('/', '\\').none { it == ".." }) { "资源路径不允许目录穿越" }
        require(value.none { it.code < 32 || it.code == 127 }) { "资源路径包含控制字符" }
    }

    private fun parseColor(value: String): Long? {
        hexadecimal.matchEntire(value)?.let { return it.groupValues[1].toLong(16) }
        if (value.all(Char::isDigit) && value.isNotEmpty()) return value.toLongOrNull()?.takeIf { it in 0..0xffffffffL }
        return null
    }

    private fun compatibility(path: String, literal: ThemeValue?): String? = when {
        fields[path]?.integer == true && literal is ThemeValue.LuaNumber && literal.value % 1.0 != 0.0 ->
            "$path 已保留,但 Trime2 整数读取器会使用运行时回退值"
        path == "composition.line_spacing_multiplier" && (literal as? ThemeValue.LuaNumber)?.value == 0.0 ->
            "预览会将行距倍数(line_spacing_multiplier)=0 归一为 1"
        path == "composition.position" && literal is ThemeValue.LuaString && literal.value.lowercase(java.util.Locale.ROOT) !in positions ->
            "未知编码窗口位置(composition.position)已保留,预览按 fixed 显示"
        path == "composition.movable" && literal is ThemeValue.LuaString && literal.value !in movableValues ->
            "未知编码窗口可移动值(composition.movable)已保留;当前运行时会把除 false 外的字符串视为可移动"
        path == "preedit.inline" && literal is ThemeValue.LuaString && literal.value !in inlineValues ->
            "未知预编辑内联值(preedit.inline)已保留,预览按 none 显示"
        path == "preedit.inline" && literal is ThemeValue.LuaBoolean && literal.value ->
            "布尔值 true 已保留,但当前 Style.getString 运行时会按 none 预览"
        path == "preedit.inline" && literal is ThemeValue.LuaBoolean ->
            "布尔值 false 已保留,预览按 none 显示"
        else -> null
    }

    private fun dynamicValue(path: String, field: Field, diagnostic: String, trace: List<String> = emptyList(), inheritedFrom: String? = null) =
        Value(path, field.type, null, inheritedFrom = inheritedFrom, trace = trace, dynamic = true, diagnostic = diagnostic)

    private fun requireField(path: String): Field {
        require(path != "composition.window" && !path.startsWith("composition.window.")) {
            "编码窗口(composition.window)仅支持源码编辑,不能通用修改"
        }
        return fields[path] ?: throw IllegalArgumentException("不支持的组件样式字段:$path")
    }

    private fun rejectDuplicateExact(document: ThemeDocument, path: String) {
        require(document.sourceStatements.count { it.path == path } <= 1) {
            "$path 的重复精确赋值必须在 Lua 源代码页编辑"
        }
    }

    private fun ambiguousAccess(document: ThemeDocument, root: String, afterIndex: Int): String? {
        val access = Regex("\\b${Regex.escape(root)}\\b")
        return document.sourceStatements.withIndex().firstOrNull { (index, statement) ->
            index > afterIndex && statement.root == null && access.containsMatchIn(visibleLua(statement.text))
        }?.let { "由于后续存在不支持的 Lua,无法证明 '$root' 的赋值优先级" }
    }

    private fun statementValue(statement: ThemeSourceStatement, path: String): ThemeValue {
        val parsed = ThemeLuaParser().parse(statement.text)
        require(parsed.diagnostics.none { it.severity == Severity.ERROR }) { "Lua 源代码包含错误" }
        return parsed.document.get(path)
            ?: throw IllegalArgumentException("无法证明 $path 的赋值优先级")
    }

    private fun parse(source: String): Source {
        val result = ThemeLuaParser().parse(source)
        require(result.diagnostics.none { it.severity == Severity.ERROR }) { "Lua 源代码包含错误" }
        require(result.diagnostics.none { it.message.startsWith("不支持的表键") || it.message.startsWith("Unsupported table key") }) {
            "不支持的表键必须在 Lua 源代码页编辑"
        }
        return Source(source, result.document)
    }

    private fun setNested(current: ThemeValue, path: List<String>, value: ThemeValue): ThemeValue {
        if (path.isEmpty()) return value
        val table = current as? ThemeValue.LuaTable ?: throw IllegalArgumentException("不能覆盖非表类型的上级")
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
        require(equals >= 0) { "赋值不能安全重写" }
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
