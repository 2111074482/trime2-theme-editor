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
 * Conservative, source-only access to the small panel bars used by the candidate, symbol, and
 * clipboard views. Only literal assignments are inspected; in particular, this object never runs
 * Lua or attempts to resolve `table.clone`.
 *
 * Values exposed by [Toolbar] and [FilterBar] include the runtime fallback when a field is absent.
 * The corresponding `*Explicit` flag distinguishes that fallback from a literal source value.
 * A mutation is a complete snapshot: a nullable argument removes that field so the runtime fallback
 * applies. Unknown fields in a literal component table are retained. Dynamic components and unsafe
 * assignment precedence are rejected instead of being replaced.
 */
object ThemePanelComponents {
    enum class Panel { CANDIDATE_EXPANDED, SYMBOL, CLIPBOARD }

    data class Toolbar(
        val gravity: String?,
        val height: Double?,
        val keys: List<String>,
        val gravityExplicit: Boolean,
        val heightExplicit: Boolean,
        val keysExplicit: Boolean,
        val inherited: Boolean,
        val sourcePath: String?,
    )

    data class TabBar(
        val gravity: String?,
        val height: Double?,
        val gravityExplicit: Boolean,
        val heightExplicit: Boolean,
        val inherited: Boolean,
        val sourcePath: String?,
    )

    data class FilterBar(
        val show: Boolean,
        val gravity: String,
        val showExplicit: Boolean,
        val gravityExplicit: Boolean,
        val inherited: Boolean,
        val sourcePath: String?,
    )

    private enum class State { MISSING, LITERAL, DYNAMIC }

    private data class Location(
        val state: State,
        val statementIndex: Int = -1,
        val assignmentPath: String? = null,
        val value: ThemeValue? = null,
        val relativePath: List<String> = emptyList(),
        val inherited: Boolean = false,
    )

    private data class Snapshot(
        val source: String,
        val document: ThemeDocument,
        val componentPath: String,
        val location: Location,
        val table: ThemeValue.LuaTable?,
    )

    private val toolbarGravities = setOf("left", "top", "right", "bottom")
    private val tabGravities = setOf("top", "bottom")
    private val symbolDefaults = listOf("hide", "page_up", "page_down", "BackSpace")
    private val clipboardDefaults = listOf("hide", "page_up", "page_down", "undo")
    private val candidateDefaults = listOf("hide", "page_up", "page_down", "char_filter")

    @JvmStatic
    fun readToolbar(source: String, panel: Panel): Toolbar {
        val state = snapshot(source, toolbarPath(panel))
        val table = requireLiteralTable(state, "toolbar")
        val gravity = literalString(table, "gravity", "工具栏重力方向(gravity)")
        if (gravity != null) require(gravity in toolbarGravities) {
            "工具栏重力方向(gravity)必须是 left、top、right 或 bottom"
        }
        // ExpandedCandidateView has no component height setting; preserve any such unknown field
        // but do not expose or mutate it. Symbol and clipboard toolbars do consume literal height.
        val height = if (panel == Panel.CANDIDATE_EXPANDED) null
        else literalHeight(table, "height", "工具栏高度(height)")
        val keys = table?.fields?.get("keys")?.let { stringArray(it, "工具栏按键(keys)") }
        return Toolbar(
            gravity = gravity ?: "right",
            height = height,
            keys = keys ?: toolbarDefaults(panel),
            gravityExplicit = table?.fields?.containsKey("gravity") == true,
            heightExplicit = panel != Panel.CANDIDATE_EXPANDED && table?.fields?.containsKey("height") == true,
            keysExplicit = table?.fields?.containsKey("keys") == true,
            inherited = state.location.inherited,
            sourcePath = state.location.assignmentPath,
        )
    }

    @JvmStatic
    fun updateToolbar(
        source: String,
        panel: Panel,
        gravity: String?,
        height: Double?,
        keys: List<String>?,
    ): String {
        validateGravity(gravity, toolbarGravities, "Toolbar")
        validateHeight(height, "工具栏高度(height)")
        require(panel != Panel.CANDIDATE_EXPANDED || height == null) {
            "展开候选工具栏(candidate.expanded.tool_bar)不提供高度字段"
        }
        val safeKeys: List<String>? = keys?.let { input ->
            require(input.none { it == null }) { "工具栏按键(keys)必须是字面字符串" }
            input.toList()
        }
        val state = mutableSnapshot(source, toolbarPath(panel), "toolbar")
        val fields = LinkedHashMap(state.table?.fields ?: emptyMap())
        setOrRemove(fields, "gravity", gravity?.let { ThemeValue.LuaString(it) })
        if (panel != Panel.CANDIDATE_EXPANDED) {
            setOrRemove(fields, "height", height?.let { ThemeValue.LuaNumber(it) })
        }
        setOrRemove(fields, "keys", safeKeys?.let(::strings))
        val output = writeComponent(state, ThemeValue.LuaTable(fields))
        val verified = readToolbar(output, panel)
        require(
            verified.gravityExplicit == (gravity != null) &&
                verified.heightExplicit == (height != null) &&
                verified.keysExplicit == (safeKeys != null) &&
                verified.gravity == (gravity ?: "right") &&
                verified.height == height &&
                verified.keys == (safeKeys ?: toolbarDefaults(panel)),
        ) { "写出的工具栏无法安全解析;请使用 Lua 源代码页" }
        return output
    }

    @JvmStatic
    fun readTabBar(source: String, panel: Panel): TabBar {
        requireTabPanel(panel)
        val state = snapshot(source, tabBarPath(panel))
        val table = requireLiteralTable(state, "tab bar")
        val gravity = literalString(table, "gravity", "标签栏重力方向(gravity)")
        if (gravity != null) require(gravity in tabGravities) {
            "标签栏重力方向(gravity)必须是 top 或 bottom"
        }
        val height = literalHeight(table, "height", "标签栏高度(height)")
        return TabBar(
            gravity = gravity,
            height = height,
            gravityExplicit = table?.fields?.containsKey("gravity") == true,
            heightExplicit = table?.fields?.containsKey("height") == true,
            inherited = state.location.inherited,
            sourcePath = state.location.assignmentPath,
        )
    }

    @JvmStatic
    fun updateTabBar(
        source: String,
        panel: Panel,
        gravity: String?,
        height: Double?,
    ): String {
        requireTabPanel(panel)
        validateGravity(gravity, tabGravities, "Tab bar")
        validateHeight(height, "标签栏高度(height)")
        val state = mutableSnapshot(source, tabBarPath(panel), "tab bar")
        val fields = LinkedHashMap(state.table?.fields ?: emptyMap())
        setOrRemove(fields, "gravity", gravity?.let { ThemeValue.LuaString(it) })
        setOrRemove(fields, "height", height?.let { ThemeValue.LuaNumber(it) })
        val output = writeComponent(state, ThemeValue.LuaTable(fields))
        val verified = readTabBar(output, panel)
        require(
            verified.gravityExplicit == (gravity != null) &&
                verified.heightExplicit == (height != null) &&
                verified.gravity == gravity && verified.height == height,
        ) { "写出的标签栏无法安全解析;请使用 Lua 源代码页" }
        return output
    }

    @JvmStatic
    fun readCandidateFilter(source: String): FilterBar {
        val state = snapshot(source, "candidate.expanded.filter_bar")
        val table = requireLiteralTable(state, "候选过滤栏")
        val showValue = table?.fields?.get("show")
        require(showValue == null || showValue is ThemeValue.LuaBoolean) {
            "候选过滤栏显示(show)必须是字面布尔值"
        }
        val gravity = literalString(table, "gravity", "候选过滤栏重力方向(gravity)")
        if (gravity != null) require(gravity in toolbarGravities) {
            "候选过滤栏重力方向(gravity)必须是 left、top、right 或 bottom"
        }
        return FilterBar(
            show = (showValue as? ThemeValue.LuaBoolean)?.value ?: true,
            gravity = gravity ?: "left",
            showExplicit = table?.fields?.containsKey("show") == true,
            gravityExplicit = table?.fields?.containsKey("gravity") == true,
            inherited = state.location.inherited,
            sourcePath = state.location.assignmentPath,
        )
    }

    @JvmStatic
    fun updateCandidateFilter(source: String, show: Boolean?, gravity: String?): String {
        validateGravity(gravity, toolbarGravities, "候选过滤栏")
        val state = mutableSnapshot(source, "candidate.expanded.filter_bar", "候选过滤栏")
        val fields = LinkedHashMap(state.table?.fields ?: emptyMap())
        setOrRemove(fields, "show", show?.let { ThemeValue.LuaBoolean(it) })
        setOrRemove(fields, "gravity", gravity?.let { ThemeValue.LuaString(it) })
        val output = writeComponent(state, ThemeValue.LuaTable(fields))
        val verified = readCandidateFilter(output)
        require(
            verified.showExplicit == (show != null) &&
                verified.gravityExplicit == (gravity != null) &&
                verified.show == (show ?: true) && verified.gravity == (gravity ?: "left"),
        ) { "写出的候选过滤栏无法安全解析;请使用 Lua 源代码页" }
        return output
    }

    private fun toolbarPath(panel: Panel): String = when (panel) {
        Panel.CANDIDATE_EXPANDED -> "candidate.expanded.tool_bar"
        Panel.SYMBOL -> "symbol.tool_bar"
        Panel.CLIPBOARD -> "clipboard.tool_bar"
    }

    private fun tabBarPath(panel: Panel): String = when (panel) {
        Panel.SYMBOL -> "symbol.tab_bar"
        Panel.CLIPBOARD -> "clipboard.tab_bar"
        Panel.CANDIDATE_EXPANDED -> throw IllegalArgumentException("展开候选面板没有标签栏(tab_bar)")
    }

    private fun toolbarDefaults(panel: Panel): List<String> = when (panel) {
        Panel.CANDIDATE_EXPANDED -> candidateDefaults
        Panel.SYMBOL -> symbolDefaults
        Panel.CLIPBOARD -> clipboardDefaults
    }

    private fun requireTabPanel(panel: Panel) {
        require(panel != Panel.CANDIDATE_EXPANDED) { "展开候选面板没有标签栏(tab_bar)" }
    }

    private fun requireLiteralTable(state: Snapshot, name: String): ThemeValue.LuaTable? = when (state.location.state) {
        State.MISSING -> null
        State.LITERAL -> state.table
            ?: throw IllegalArgumentException("$name 必须是字面表")
        State.DYNAMIC -> throw IllegalArgumentException("动态字段 $name 必须在 Lua 源代码页编辑")
    }

    private fun mutableSnapshot(source: String, path: String, name: String): Snapshot {
        val state = snapshot(source, path)
        when (state.location.state) {
            State.DYNAMIC -> throw IllegalArgumentException("动态字段 $name 必须在 Lua 源代码页编辑")
            State.LITERAL -> require(state.table != null) { "$name 必须是字面表" }
            State.MISSING -> Unit
        }
        // Validate all currently modeled fields before preserving the table around an update.
        when (path.substringAfterLast('.')) {
            "tool_bar" -> readToolbar(source, panelFor(path))
            "tab_bar" -> readTabBar(source, panelFor(path))
            "filter_bar" -> readCandidateFilter(source)
        }
        return state
    }

    private fun panelFor(path: String): Panel = when {
        path.startsWith("candidate.expanded.") -> Panel.CANDIDATE_EXPANDED
        path.startsWith("symbol.") -> Panel.SYMBOL
        path.startsWith("clipboard.") -> Panel.CLIPBOARD
        else -> throw IllegalArgumentException("不支持的面板组件路径")
    }

    /** Finds the last assignment that can determine [componentPath], without evaluating its RHS. */
    private fun snapshot(source: String, componentPath: String): Snapshot {
        val document = parse(source)
        val prefixes = prefixes(componentPath)
        prefixes.forEach { path ->
            require(document.sourceStatements.count { it.path == path } <= 1) {
                "$path 的重复赋值存在歧义;请使用 Lua 源代码页"
            }
        }
        rejectUnclassifiedAccess(document, componentPath.substringBefore('.'))

        var location = Location(State.MISSING)
        var sawDynamicAncestor = false
        document.sourceStatements.forEachIndexed { index, statement ->
            val path = statement.path ?: return@forEachIndexed
            if (path == componentPath || path in prefixes.dropLast(1)) {
                val value = statementValue(statement, path)
                val relative = componentPath.split('.').drop(path.split('.').size)
                val resolved = resolve(value, relative)
                if (resolved.state == State.DYNAMIC && relative.isNotEmpty()) sawDynamicAncestor = true
                location = Location(
                    state = resolved.state,
                    statementIndex = index,
                    assignmentPath = path,
                    value = resolved.value,
                    relativePath = relative,
                    inherited = sawDynamicAncestor && path == componentPath,
                )
            } else if (path.startsWith("$componentPath.")) {
                throw IllegalArgumentException(
                    "不支持嵌套 $componentPath 赋值;请使用单个字面组件表或 Lua 源代码页",
                )
            }
        }
        val table = location.value as? ThemeValue.LuaTable
        return Snapshot(source, document, componentPath, location, table)
    }

    private data class Resolved(val state: State, val value: ThemeValue?)

    private fun resolve(root: ThemeValue, relative: List<String>): Resolved {
        if (root is ThemeValue.RawLuaNode) return Resolved(State.DYNAMIC, root)
        var current: ThemeValue = root
        relative.forEach { part ->
            if (current is ThemeValue.RawLuaNode) return Resolved(State.DYNAMIC, current)
            val table = current as? ThemeValue.LuaTable
                ?: throw IllegalArgumentException("面板组件的上级必须是字面表")
            current = table.fields[part] ?: return Resolved(State.MISSING, null)
        }
        return if (current is ThemeValue.RawLuaNode) Resolved(State.DYNAMIC, current)
        else Resolved(State.LITERAL, current)
    }

    private fun prefixes(path: String): List<String> {
        val parts = path.split('.')
        return parts.indices.map { parts.take(it + 1).joinToString(".") }
    }

    private fun rejectUnclassifiedAccess(document: ThemeDocument, root: String) {
        val access = Regex("\\b${Regex.escape(root)}\\b")
        document.sourceStatements.forEach { statement ->
            if (statement.root == null && access.containsMatchIn(visibleLua(statement.text))) {
                throw IllegalArgumentException(
                    "无法证明面板赋值优先级安全;请使用 Lua 源代码页",
                )
            }
        }
    }

    private fun statementValue(statement: ThemeSourceStatement, path: String): ThemeValue {
        val result = ThemeLuaParser().parse(statement.text)
        require(result.diagnostics.none { it.severity == Severity.ERROR }) { "Lua 源代码包含错误" }
        return result.document.get(path)
            ?: throw IllegalArgumentException("无法证明面板赋值优先级安全;请使用 Lua 源代码页")
    }

    private fun literalString(table: ThemeValue.LuaTable?, field: String, label: String): String? {
        val value = table?.fields?.get(field) ?: return null
        return (value as? ThemeValue.LuaString)?.value
            ?: throw IllegalArgumentException("$label 必须是字面字符串")
    }

    private fun literalHeight(table: ThemeValue.LuaTable?, field: String, label: String): Double? {
        val value = table?.fields?.get(field) ?: return null
        val number = (value as? ThemeValue.LuaNumber)?.value
            ?: throw IllegalArgumentException("$label 必须是字面数值")
        validateHeight(number, label)
        return number
    }

    private fun stringArray(value: ThemeValue, label: String): List<String> {
        val table = value as? ThemeValue.LuaTable
            ?: throw IllegalArgumentException("$label 必须是字面字符串数组")
        require(table.fields.keys.all { it.matches(Regex("^#[1-9][0-9]*$")) }) {
            "$label 必须是不含命名字段的字面数组"
        }
        val indexed = table.fields.entries.map { entry ->
            entry.key.drop(1).toIntOrNull()?.let { it to entry.value }
                ?: throw IllegalArgumentException("$label 包含不支持的数组索引")
        }.sortedBy { it.first }
        require(indexed.map { it.first } == (1..indexed.size).toList()) {
            "$label 必须是连续的字面数组"
        }
        return indexed.map { (_, item) ->
            (item as? ThemeValue.LuaString)?.value
                ?: throw IllegalArgumentException("$label 只能包含字面字符串;不支持事件表")
        }
    }

    private fun validateGravity(value: String?, allowed: Set<String>, label: String) {
        require(value == null || value in allowed) {
            "$label 的重力方向(gravity)必须是以下值之一:${allowed.joinToString()}"
        }
    }

    private fun validateHeight(value: Double?, label: String) {
        require(value == null || value.isFinite() && value >= 0.0) {
            "$label 必须是有限且非负的数值"
        }
    }

    private fun strings(values: List<String>): ThemeValue.LuaTable = ThemeValue.LuaTable(
        linkedMapOf<String, ThemeValue>().apply {
            values.forEachIndexed { index, value -> put("#${index + 1}", ThemeValue.LuaString(value)) }
        },
    )

    private fun setOrRemove(fields: LinkedHashMap<String, ThemeValue>, key: String, value: ThemeValue?) {
        if (value == null) fields.remove(key) else fields[key] = value
    }

    private fun writeComponent(state: Snapshot, component: ThemeValue.LuaTable): String {
        val output = when {
            state.location.statementIndex < 0 -> appendMissingComponent(state, component)
            state.location.state == State.MISSING || state.location.state == State.LITERAL -> {
                val replacement = if (state.location.relativePath.isEmpty()) {
                    component
                } else {
                    val owner = statementValue(
                        state.document.sourceStatements[state.location.statementIndex],
                        state.location.assignmentPath!!,
                    )
                    setNested(owner, state.location.relativePath, component)
                }
                replaceStatement(state, render(replacement))
            }
            else -> throw IllegalArgumentException("动态组件必须在 Lua 源代码页编辑")
        }
        parse(output) // Parse-after-write is part of the mutation contract.
        return output
    }

    private fun setNested(current: ThemeValue, path: List<String>, value: ThemeValue): ThemeValue {
        if (path.isEmpty()) return value
        val table = current as? ThemeValue.LuaTable
            ?: throw IllegalArgumentException("面板组件上级不能安全覆盖")
        val fields = LinkedHashMap(table.fields)
        val child = fields[path.first()]
        fields[path.first()] = if (path.size == 1) value else {
            setNested(child ?: ThemeValue.LuaTable(), path.drop(1), value)
        }
        return ThemeValue.LuaTable(fields)
    }

    private fun appendMissingComponent(state: Snapshot, component: ThemeValue.LuaTable): String {
        val parts = state.componentPath.split('.')
        val rootName = parts.first()
        val existingRoot = state.document.sourceStatements.lastOrNull { it.path == rootName }
        if (existingRoot != null) {
            val rootValue = statementValue(existingRoot, rootName)
            require(rootValue is ThemeValue.LuaTable) { "动态面板根必须在 Lua 源代码页编辑" }
            val nested = setNested(rootValue, parts.drop(1), component)
            val index = state.document.sourceStatements.indexOf(existingRoot)
            val nestedState = state.copy(location = Location(State.MISSING, index, rootName, rootValue, parts.drop(1)))
            return replaceStatement(nestedState, render(nested))
        }
        var value: ThemeValue = component
        parts.drop(1).asReversed().forEach { name -> value = ThemeValue.LuaTable(linkedMapOf(name to value)) }
        return appendAssignment(state.source, "$rootName = ${render(value)}")
    }

    private fun replaceStatement(state: Snapshot, rendered: String): String = buildString {
        state.document.sourceStatements.forEachIndexed { index, statement ->
            append(if (index == state.location.statementIndex) replaceRhs(statement.text, rendered) else statement.text)
            append(statement.separator)
        }
    }

    /** Keeps assignment indentation and a top-level trailing line comment where possible. */
    private fun replaceRhs(statement: String, rendered: String): String {
        val equals = topLevelEquals(statement)
        require(equals >= 0) { "面板赋值不能安全重写;请使用 Lua 源代码页" }
        var rhs = equals + 1
        while (rhs < statement.length && (statement[rhs] == ' ' || statement[rhs] == '\t')) rhs++
        val comment = topLevelComment(statement, rhs)
        val end = if (comment >= 0) comment else statement.length
        val trailing = statement.substring(rhs, end).takeLastWhile { it == ' ' || it == '\t' }
        return statement.substring(0, rhs) + rendered + trailing +
            if (comment >= 0) statement.substring(comment) else ""
    }

    private fun topLevelEquals(source: String): Int {
        var quote = '\u0000'
        var depth = 0
        var index = 0
        while (index < source.length) {
            val char = source[index]
            if (quote != '\u0000') {
                if (char == '\\') index++ else if (char == quote) quote = '\u0000'
            } else if (char == '-' && index + 1 < source.length && source[index + 1] == '-') {
                while (index < source.length && source[index] != '\n') index++
            } else when (char) {
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
        var quote = '\u0000'
        var depth = 0
        var index = start
        while (index + 1 < source.length) {
            val char = source[index]
            if (quote != '\u0000') {
                if (char == '\\') index++ else if (char == quote) quote = '\u0000'
            } else when {
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

    private fun render(value: ThemeValue): String {
        val document = ThemeDocument(listOf(ThemeNode("value", 0, value)), trailingNewline = false)
        return ThemeLuaWriter.write(document, ThemeWriteMode.STRUCTURED).substringAfter("value = ")
    }

    private fun parse(source: String): ThemeDocument = ThemeLuaParser().parse(source).also { result ->
        require(result.diagnostics.none { it.severity == Severity.ERROR }) { "Lua 源代码包含错误" }
        require(result.diagnostics.none { it.message.startsWith("不支持的表键") || it.message.startsWith("Unsupported table key") }) {
            "不支持的表键必须在 Lua 源代码页编辑"
        }
    }.document

    /** Removes strings and comments before checking for bracket notation or opaque mutations. */
    private fun visibleLua(source: String): String {
        val visible = StringBuilder(source.length)
        var quote = '\u0000'
        var index = 0
        while (index < source.length) {
            val char = source[index]
            if (quote != '\u0000') {
                visible.append(' ')
                if (char == '\\' && index + 1 < source.length) {
                    visible.append(' ')
                    index++
                } else if (char == quote) quote = '\u0000'
            } else if (char == '\'' || char == '"') {
                quote = char
                visible.append(' ')
            } else if (char == '-' && index + 1 < source.length && source[index + 1] == '-') {
                while (index < source.length && source[index] != '\n') {
                    visible.append(' ')
                    index++
                }
                if (index < source.length) visible.append('\n')
            } else visible.append(char)
            index++
        }
        return visible.toString()
    }
}
