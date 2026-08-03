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
 * A deliberately static view of `toolbar.keys`.
 *
 * This class parses literals only. In particular, it never evaluates a Lua expression. Mutations replace
 * the effective source assignment (a `toolbar.keys` assignment when present, otherwise its literal
 * `toolbar` root), rather than using [ThemeDocument.get], because a raw clone root may precede a safe
 * dotted override.
 */
object ThemeToolbarKeys {
    enum class Source { STRING, INLINE_EVENT, FULL_KEY, SCHEMA_SWITCH, RAW_LUA }

    data class SchemaSwitch(
        val name: String,
        val options: List<String>,
        val states: List<String>,
        val reset: Int,
        val style: String?,
    ) {
        /** ToolbarView reads this style but currently constructs schema switches with toolbar.key. */
        val compatibilityWarning: Boolean get() = style != null
    }

    data class Item internal constructor(
        val source: Source,
        val literal: String?,
        val event: ThemePresetEvents.Event?,
        val schemaSwitch: SchemaSwitch?,
        val risky: Boolean,
        val compatibilityWarning: Boolean,
        internal val literalValue: ThemeValue? = null,
    )

    private enum class Owner { ROOT, DOTTED, NONE }
    private data class Effective(
        val owner: Owner,
        val statementIndex: Int,
        val value: ThemeValue?,
        val root: ThemeValue.LuaTable? = null,
    )
    private data class Snapshot(
        val document: ThemeDocument,
        val effective: Effective,
        val values: List<ThemeValue>?,
        val items: List<Item>,
    )

    private val identifier = Regex("^[A-Za-z_][A-Za-z0-9_]{0,63}$")
    private val schemaFields = setOf("name", "options", "states", "reset", "style")

    @JvmStatic
    fun string(literal: String): Item = Item(Source.STRING, literal, null, null, false, false, ThemeValue.LuaString(literal))

    @JvmStatic
    fun inlineEvent(event: ThemePresetEvents.Event): Item =
        Item(Source.INLINE_EVENT, null, event, null, event.risky, false)

    @JvmStatic
    fun schemaSwitch(name: String, options: List<String>, states: List<String>, reset: Int): Item =
        schemaSwitch(name, options, states, reset, null)

    @JvmStatic
    fun schemaSwitch(name: String, options: List<String>, states: List<String>, reset: Int, style: String?): Item {
        val value = SchemaSwitch(name, options.toList(), states.toList(), reset, style)
        validateSchema(value)
        return Item(Source.SCHEMA_SWITCH, null, null, value, false, value.compatibilityWarning)
    }

    @JvmStatic
    fun list(source: String): List<Item> = snapshot(source).items

    /**
     * Appends when [append] is true, otherwise edits the zero-based [index]. A generic edit deliberately
     * cannot overwrite FULL_KEY or RAW_LUA. Use [replace] when replacing a full key is an explicit user
     * decision; raw Lua is never structurally rewritten.
     */
    @JvmStatic
    fun put(source: String, index: Int, item: Item, append: Boolean): String {
        require(item.source != Source.FULL_KEY && item.source != Source.RAW_LUA) {
            "FULL_KEY and RAW_LUA require an explicit safe replacement or the Lua source page"
        }
        val state = mutableSnapshot(source)
        val values = state.values!!.toMutableList()
        val target = if (append) values.size else index
        if (!append) {
            require(index in values.indices) { "Toolbar key index is out of range" }
            val existing = state.items[index]
            require(existing.source != Source.FULL_KEY && existing.source != Source.RAW_LUA) {
                "FULL_KEY and RAW_LUA cannot be overwritten by generic item edit; use explicit replacement or the Lua source page"
            }
        }
        val previous = if (append) null else values[target]
        val encoded = encode(item, if (!append && state.items[target].source == Source.INLINE_EVENT) previous as? ThemeValue.LuaTable else null)
        if (append) values.add(encoded) else values[target] = encoded
        return writeAndVerify(source, state, values)
    }

    /** Explicit safe replacement contract for a static full key. RAW_LUA remains blocked. */
    @JvmStatic
    fun replace(source: String, index: Int, item: Item): String {
        require(item.source != Source.RAW_LUA) { "RAW_LUA requires the Lua source page" }
        val state = mutableSnapshot(source)
        val values = state.values!!.toMutableList()
        require(index in values.indices) { "Toolbar key index is out of range" }
        val previous = if (item.source == Source.INLINE_EVENT && state.items[index].source == Source.INLINE_EVENT) {
            values[index] as? ThemeValue.LuaTable
        } else null
        values[index] = encode(item, previous)
        return writeAndVerify(source, state, values)
    }

    @JvmStatic
    fun delete(source: String, index: Int): String {
        val state = mutableSnapshot(source)
        val values = state.values!!.toMutableList()
        require(index in values.indices) { "Toolbar key index is out of range" }
        values.removeAt(index)
        return writeAndVerify(source, state, values)
    }

    @JvmStatic
    fun move(source: String, from: Int, to: Int): String {
        val state = mutableSnapshot(source)
        val values = state.values!!.toMutableList()
        require(from in values.indices && to in values.indices) { "Toolbar key index is out of range" }
        if (from == to) return source
        values.add(to, values.removeAt(from))
        return writeAndVerify(source, state, values)
    }

    private fun mutableSnapshot(source: String): Snapshot {
        val state = snapshot(source)
        require(state.values != null) { "Dynamic toolbar.keys requires the Lua source page" }
        require(state.items.none { it.source == Source.RAW_LUA }) {
            "toolbar.keys contains Raw Lua and cannot be structurally overwritten; use the Lua source page"
        }
        return state
    }

    private fun snapshot(source: String): Snapshot {
        val document = parse(source)
        val effective = effective(document)
        require(effective.owner != Owner.NONE || !containsUnclassifiedToolbar(source)) {
            "Toolbar assignment precedence cannot be proven safe; use the Lua source page"
        }
        val value = effective.value
        if (value == null) return Snapshot(document, effective, emptyList(), emptyList())
        if (value !is ThemeValue.LuaTable) {
            return Snapshot(document, effective, null, listOf(raw(value)))
        }
        val values = array(value)
        val items = values.mapIndexed { index, child -> classify(index, child) }
        return Snapshot(document, effective, values, items)
    }

    /** Determine Lua assignment precedence from source statements, including raw clone roots. */
    private fun effective(document: ThemeDocument): Effective {
        require(document.sourceStatements.count { it.path == "toolbar.keys" } <= 1) {
            "Duplicate toolbar.keys assignments are ambiguous; use the Lua source page"
        }
        var result = Effective(Owner.NONE, -1, null)
        document.sourceStatements.forEachIndexed { index, statement ->
            when (statement.path) {
                "toolbar" -> {
                    val rootValue = statementValue(statement, "toolbar")
                    result = if (rootValue is ThemeValue.LuaTable) {
                        Effective(Owner.ROOT, index, rootValue.fields["keys"], rootValue)
                    } else {
                        Effective(Owner.ROOT, index, rootValue)
                    }
                }
                "toolbar.keys" -> result = Effective(Owner.DOTTED, index, statementValue(statement, "toolbar.keys"))
                else -> if (statement.path?.startsWith("toolbar.keys.") == true) {
                    throw IllegalArgumentException("Nested or ambiguous toolbar.keys assignment requires the Lua source page")
                }
            }
            if (statement.root == null && statement.text.contains(Regex("\\btoolbar\\b")) &&
                !statement.text.trimStart().startsWith("--")) {
                throw IllegalArgumentException("Toolbar mutation precedence cannot be proven safe; use the Lua source page")
            }
        }
        return result
    }


    /** Detect bracket notation and complex statements the conservative parser could not attribute. */
    private fun containsUnclassifiedToolbar(source: String): Boolean {
        var quote = '\u0000'
        var index = 0
        val visible = StringBuilder(source.length)
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
                while (index < source.length && source[index] != '\n') { visible.append(' '); index++ }
                if (index < source.length) visible.append('\n')
            } else visible.append(char)
            index++
        }
        return Regex("\\btoolbar\\s*(?:\\[|\\.)").containsMatchIn(visible)
    }

    private fun statementValue(statement: ThemeSourceStatement, path: String): ThemeValue {
        val parsed = ThemeLuaParser().parse(statement.text)
        require(parsed.diagnostics.none { it.severity == Severity.ERROR }) { "Lua source contains errors" }
        return parsed.document.get(path)
            ?: throw IllegalArgumentException("Toolbar assignment precedence cannot be proven safe; use the Lua source page")
    }

    private fun classify(index: Int, value: ThemeValue): Item = when (value) {
        is ThemeValue.LuaString -> Item(Source.STRING, value.value, null, null, false, false, value)
        is ThemeValue.LuaTable -> {
            if (value.containsRaw()) raw(value)
            else if ("options" in value.fields) {
                val schema = readSchema(value)
                Item(Source.SCHEMA_SWITCH, null, null, schema, false, schema.compatibilityWarning, value)
            } else if ("click" in value.fields) {
                Item(Source.FULL_KEY, null, null, null, value.isRisky(), false, value)
            } else {
                val event = ThemePresetEvents.fromLiteralTable("ToolbarKey${index + 1}", value)
                Item(Source.INLINE_EVENT, null, event, null, event.risky, false, value)
            }
        }
        else -> raw(value)
    }

    private fun raw(value: ThemeValue) = Item(Source.RAW_LUA, null, null, null, true, false, value)

    private fun readSchema(table: ThemeValue.LuaTable): SchemaSwitch {
        require(table.fields.keys.all { it in schemaFields }) {
            "Unsupported schema-switch table keys require the Lua source page"
        }
        val name = (table.fields["name"] as? ThemeValue.LuaString)?.value
            ?: throw IllegalArgumentException("Schema switch name must be a literal string")
        val options = stringArray(table.fields["options"], "options")
        val states = stringArray(table.fields["states"], "states")
        val resetNumber = table.fields["reset"] as? ThemeValue.LuaNumber
            ?: throw IllegalArgumentException("Schema switch reset must be a 32-bit integer")
        require(resetNumber.value.isFinite() && resetNumber.value % 1.0 == 0.0 &&
            resetNumber.value in Int.MIN_VALUE.toDouble()..Int.MAX_VALUE.toDouble()) {
            "Schema switch reset must be a 32-bit integer"
        }
        val styleValue = table.fields["style"]
        require(styleValue == null || styleValue is ThemeValue.LuaString) { "Schema switch style must be a literal string" }
        val result = SchemaSwitch(name, options, states, resetNumber.value.toInt(), (styleValue as? ThemeValue.LuaString)?.value)
        validateSchema(result)
        return result
    }

    private fun validateSchema(schema: SchemaSwitch) {
        require(identifier.matches(schema.name)) { "Schema switch name must be a Lua-safe identifier" }
        require(schema.style == null || identifier.matches(schema.style)) { "Schema switch style must be a Lua-safe identifier" }
    }

    private fun stringArray(value: ThemeValue?, name: String): List<String> {
        require(value is ThemeValue.LuaTable) { "Schema switch $name must be a literal string array" }
        return array(value).map {
            (it as? ThemeValue.LuaString)?.value
                ?: throw IllegalArgumentException("Schema switch $name must be a literal string array")
        }
    }

    private fun array(table: ThemeValue.LuaTable): List<ThemeValue> {
        require(table.fields.keys.all { it.matches(Regex("^#[1-9][0-9]*$")) }) {
            "toolbar.keys must be a literal array without named fields"
        }
        val indexed = table.fields.entries.map { entry ->
            entry.key.drop(1).toIntOrNull()?.let { it to entry.value }
                ?: throw IllegalArgumentException("toolbar.keys array index is unsupported")
        }.sortedBy { it.first }
        require(indexed.map { it.first } == (1..indexed.size).toList()) {
            "toolbar.keys must be a contiguous literal array"
        }
        return indexed.map { it.second }
    }

    private fun encode(item: Item, previous: ThemeValue.LuaTable?): ThemeValue = when (item.source) {
        Source.STRING -> ThemeValue.LuaString(item.literal ?: throw IllegalArgumentException("String toolbar key is missing its literal"))
        Source.INLINE_EVENT -> {
            val event = item.event ?: throw IllegalArgumentException("Inline toolbar event is missing")
            // The ID identifies preset definitions only; toolbar inline events have no serialized ID.
            ThemePresetEvents.toLiteralTable(event.copy(id = "ToolbarKey"), previous)
        }
        Source.SCHEMA_SWITCH -> writeSchema(item.schemaSwitch ?: throw IllegalArgumentException("Schema switch is missing"))
        Source.FULL_KEY -> item.literalValue as? ThemeValue.LuaTable
            ?: throw IllegalArgumentException("FULL_KEY is not a static literal table")
        Source.RAW_LUA -> throw IllegalArgumentException("RAW_LUA requires the Lua source page")
    }

    private fun writeSchema(schema: SchemaSwitch): ThemeValue.LuaTable {
        validateSchema(schema)
        val fields = linkedMapOf<String, ThemeValue>(
            "name" to ThemeValue.LuaString(schema.name),
            "options" to strings(schema.options),
            "states" to strings(schema.states),
            "reset" to ThemeValue.LuaNumber(schema.reset.toDouble()),
        )
        schema.style?.let { fields["style"] = ThemeValue.LuaString(it) }
        return ThemeValue.LuaTable(fields)
    }

    private fun strings(values: List<String>) = ThemeValue.LuaTable(linkedMapOf<String, ThemeValue>().apply {
        values.forEachIndexed { index, value -> put("#${index + 1}", ThemeValue.LuaString(value)) }
    })

    private fun writeAndVerify(source: String, state: Snapshot, values: List<ThemeValue>): String {
        val table = ThemeValue.LuaTable(linkedMapOf<String, ThemeValue>().apply {
            values.forEachIndexed { index, value -> put("#${index + 1}", value) }
        })
        val output = when (state.effective.owner) {
            Owner.DOTTED -> replaceStatement(source, state.document, state.effective.statementIndex, render(table))
            Owner.ROOT -> {
                val root = state.effective.root
                    ?: throw IllegalArgumentException("Dynamic toolbar root requires the Lua source page")
                val fields = LinkedHashMap(root.fields)
                fields["keys"] = table
                replaceStatement(source, state.document, state.effective.statementIndex, render(ThemeValue.LuaTable(fields)))
            }
            Owner.NONE -> appendAssignment(source, "toolbar.keys = ${render(table)}")
        }
        val verified = snapshot(output)
        require(verified.values != null && verified.items.none { it.source == Source.RAW_LUA } && verified.values.size == values.size) {
            "Written toolbar.keys could not be parsed safely; use the Lua source page"
        }
        return output
    }

    private fun replaceStatement(source: String, document: ThemeDocument, index: Int, rendered: String): String {
        require(index in document.sourceStatements.indices) { "Effective toolbar.keys source statement is missing" }
        return buildString(source.length + rendered.length) {
            document.sourceStatements.forEachIndexed { statementIndex, statement ->
                append(if (statementIndex == index) replaceRhs(statement.text, rendered) else statement.text)
                append(statement.separator)
            }
        }
    }

    /** Keeps assignment indentation and a top-level trailing line comment where possible. */
    private fun replaceRhs(statement: String, rendered: String): String {
        val equals = topLevelEquals(statement)
        require(equals >= 0) { "Effective toolbar assignment cannot be rewritten safely; use the Lua source page" }
        var rhs = equals + 1
        while (rhs < statement.length && (statement[rhs] == ' ' || statement[rhs] == '\t')) rhs++
        val comment = topLevelComment(statement, rhs)
        val end = if (comment >= 0) comment else statement.length
        val trailing = statement.substring(rhs, end).takeLastWhile { it == ' ' || it == '\t' }
        return statement.substring(0, rhs) + rendered + trailing + if (comment >= 0) statement.substring(comment) else ""
    }

    private fun topLevelEquals(source: String): Int {
        var quote = '\u0000'; var depth = 0; var index = 0
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
        var quote = '\u0000'; var depth = 0; var index = start
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

    private fun ThemeValue.containsRaw(): Boolean = when (this) {
        is ThemeValue.RawLuaNode -> true
        is ThemeValue.LuaTable -> fields.values.any { it.containsRaw() }
        else -> false
    }

    private fun ThemeValue.LuaTable.isRisky(): Boolean = fields.any { (name, value) ->
        (name == "command" && (value as? ThemeValue.LuaString)?.value?.isNotBlank() == true) ||
            ((name == "command" || name == "send") && (value as? ThemeValue.LuaString)?.value?.endsWith(".lua", true) == true) ||
            (value as? ThemeValue.LuaTable)?.isRisky() == true
    }
}
