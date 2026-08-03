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
        val gravity = literalString(table, "gravity", "Toolbar gravity")
        if (gravity != null) require(gravity in toolbarGravities) {
            "Toolbar gravity must be left, top, right, or bottom"
        }
        // ExpandedCandidateView has no component height setting; preserve any such unknown field
        // but do not expose or mutate it. Symbol and clipboard toolbars do consume literal height.
        val height = if (panel == Panel.CANDIDATE_EXPANDED) null
        else literalHeight(table, "height", "Toolbar height")
        val keys = table?.fields?.get("keys")?.let { stringArray(it, "Toolbar keys") }
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
        validateHeight(height, "Toolbar height")
        require(panel != Panel.CANDIDATE_EXPANDED || height == null) {
            "candidate.expanded.tool_bar does not expose height"
        }
        val safeKeys: List<String>? = keys?.let { input ->
            require(input.none { it == null }) { "Toolbar keys must be literal strings" }
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
        ) { "Written toolbar could not be parsed safely; use the Lua source page" }
        return output
    }

    @JvmStatic
    fun readTabBar(source: String, panel: Panel): TabBar {
        requireTabPanel(panel)
        val state = snapshot(source, tabBarPath(panel))
        val table = requireLiteralTable(state, "tab bar")
        val gravity = literalString(table, "gravity", "Tab-bar gravity")
        if (gravity != null) require(gravity in tabGravities) {
            "Tab-bar gravity must be top or bottom"
        }
        val height = literalHeight(table, "height", "Tab-bar height")
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
        validateHeight(height, "Tab-bar height")
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
        ) { "Written tab bar could not be parsed safely; use the Lua source page" }
        return output
    }

    @JvmStatic
    fun readCandidateFilter(source: String): FilterBar {
        val state = snapshot(source, "candidate.expanded.filter_bar")
        val table = requireLiteralTable(state, "candidate filter bar")
        val showValue = table?.fields?.get("show")
        require(showValue == null || showValue is ThemeValue.LuaBoolean) {
            "Candidate filter show must be a literal boolean"
        }
        val gravity = literalString(table, "gravity", "Candidate filter gravity")
        if (gravity != null) require(gravity in toolbarGravities) {
            "Candidate filter gravity must be left, top, right, or bottom"
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
        validateGravity(gravity, toolbarGravities, "Candidate filter")
        val state = mutableSnapshot(source, "candidate.expanded.filter_bar", "candidate filter bar")
        val fields = LinkedHashMap(state.table?.fields ?: emptyMap())
        setOrRemove(fields, "show", show?.let { ThemeValue.LuaBoolean(it) })
        setOrRemove(fields, "gravity", gravity?.let { ThemeValue.LuaString(it) })
        val output = writeComponent(state, ThemeValue.LuaTable(fields))
        val verified = readCandidateFilter(output)
        require(
            verified.showExplicit == (show != null) &&
                verified.gravityExplicit == (gravity != null) &&
                verified.show == (show ?: true) && verified.gravity == (gravity ?: "left"),
        ) { "Written candidate filter bar could not be parsed safely; use the Lua source page" }
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
        Panel.CANDIDATE_EXPANDED -> throw IllegalArgumentException("Candidate expanded panel has no tab bar")
    }

    private fun toolbarDefaults(panel: Panel): List<String> = when (panel) {
        Panel.CANDIDATE_EXPANDED -> candidateDefaults
        Panel.SYMBOL -> symbolDefaults
        Panel.CLIPBOARD -> clipboardDefaults
    }

    private fun requireTabPanel(panel: Panel) {
        require(panel != Panel.CANDIDATE_EXPANDED) { "Candidate expanded panel has no tab bar" }
    }

    private fun requireLiteralTable(state: Snapshot, name: String): ThemeValue.LuaTable? = when (state.location.state) {
        State.MISSING -> null
        State.LITERAL -> state.table
            ?: throw IllegalArgumentException("$name must be a literal table")
        State.DYNAMIC -> throw IllegalArgumentException("Dynamic $name requires the Lua source page")
    }

    private fun mutableSnapshot(source: String, path: String, name: String): Snapshot {
        val state = snapshot(source, path)
        when (state.location.state) {
            State.DYNAMIC -> throw IllegalArgumentException("Dynamic $name requires the Lua source page")
            State.LITERAL -> require(state.table != null) { "$name must be a literal table" }
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
        else -> throw IllegalArgumentException("Unsupported panel component path")
    }

    /** Finds the last assignment that can determine [componentPath], without evaluating its RHS. */
    private fun snapshot(source: String, componentPath: String): Snapshot {
        val document = parse(source)
        val prefixes = prefixes(componentPath)
        prefixes.forEach { path ->
            require(document.sourceStatements.count { it.path == path } <= 1) {
                "Duplicate $path assignments are ambiguous; use the Lua source page"
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
                    "Nested $componentPath assignments are unsupported; use one literal component table or the Lua source page",
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
                ?: throw IllegalArgumentException("Panel component ancestor must be a literal table")
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
                    "Panel assignment precedence cannot be proven safe; use the Lua source page",
                )
            }
        }
    }

    private fun statementValue(statement: ThemeSourceStatement, path: String): ThemeValue {
        val result = ThemeLuaParser().parse(statement.text)
        require(result.diagnostics.none { it.severity == Severity.ERROR }) { "Lua source contains errors" }
        return result.document.get(path)
            ?: throw IllegalArgumentException("Panel assignment precedence cannot be proven safe; use the Lua source page")
    }

    private fun literalString(table: ThemeValue.LuaTable?, field: String, label: String): String? {
        val value = table?.fields?.get(field) ?: return null
        return (value as? ThemeValue.LuaString)?.value
            ?: throw IllegalArgumentException("$label must be a literal string")
    }

    private fun literalHeight(table: ThemeValue.LuaTable?, field: String, label: String): Double? {
        val value = table?.fields?.get(field) ?: return null
        val number = (value as? ThemeValue.LuaNumber)?.value
            ?: throw IllegalArgumentException("$label must be a literal number")
        validateHeight(number, label)
        return number
    }

    private fun stringArray(value: ThemeValue, label: String): List<String> {
        val table = value as? ThemeValue.LuaTable
            ?: throw IllegalArgumentException("$label must be a literal string array")
        require(table.fields.keys.all { it.matches(Regex("^#[1-9][0-9]*$")) }) {
            "$label must be a literal array without named fields"
        }
        val indexed = table.fields.entries.map { entry ->
            entry.key.drop(1).toIntOrNull()?.let { it to entry.value }
                ?: throw IllegalArgumentException("$label contains an unsupported array index")
        }.sortedBy { it.first }
        require(indexed.map { it.first } == (1..indexed.size).toList()) {
            "$label must be a contiguous literal array"
        }
        return indexed.map { (_, item) ->
            (item as? ThemeValue.LuaString)?.value
                ?: throw IllegalArgumentException("$label must contain literal strings only; event tables are not supported")
        }
    }

    private fun validateGravity(value: String?, allowed: Set<String>, label: String) {
        require(value == null || value in allowed) {
            "$label gravity must be one of ${allowed.joinToString()}"
        }
    }

    private fun validateHeight(value: Double?, label: String) {
        require(value == null || value.isFinite() && value >= 0.0) {
            "$label must be finite and nonnegative"
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
            else -> throw IllegalArgumentException("Dynamic component requires the Lua source page")
        }
        parse(output) // Parse-after-write is part of the mutation contract.
        return output
    }

    private fun setNested(current: ThemeValue, path: List<String>, value: ThemeValue): ThemeValue {
        if (path.isEmpty()) return value
        val table = current as? ThemeValue.LuaTable
            ?: throw IllegalArgumentException("Panel component ancestor cannot be overwritten safely")
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
            require(rootValue is ThemeValue.LuaTable) { "Dynamic panel root requires the Lua source page" }
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
        require(equals >= 0) { "Panel assignment cannot be rewritten safely; use the Lua source page" }
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
        require(result.diagnostics.none { it.severity == Severity.ERROR }) { "Lua source contains errors" }
        require(result.diagnostics.none { it.message.startsWith("Unsupported table key") }) {
            "Unsupported table keys require the Lua source page"
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
