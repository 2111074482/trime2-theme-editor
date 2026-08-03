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

/** Literal preset-event and action-label model. It never constructs or executes Trime Event objects. */
object ThemePresetEvents {
    data class Event(
        val id: String,
        val send: String = "", val text: String = "", val commit: String = "", val command: String = "",
        val option: String = "", val select: String = "", val toggle: String = "", val label: String = "",
        val preview: String = "", val description: String = "", val states: List<String> = emptyList(),
        val shiftLock: String = "", val repeatable: Boolean = false, val sticky: Boolean = false,
        val functional: Boolean = true, val index: Double? = null,
    ) { val risky: Boolean get() = command.isNotBlank() || command.endsWith(".lua", true) || send.endsWith(".lua", true) }
    data class ReferenceUpdate(val source: String, val count: Int)

    private val safeId = Regex("^[A-Za-z_][A-Za-z0-9_]{0,63}$")
    private val eventSlots = setOf("click", "long_click", "swipe_left", "swipe_right", "swipe_up", "swipe_down", "combo", "composing", "has_menu", "paging", "ascii")
    private val labels = listOf("none", "send", "go", "done", "search", "previous", "next")
    private val stringFields = setOf("send", "text", "commit", "command", "option", "select", "toggle", "label", "preview", "description", "shift_lock")
    private val booleanFields = setOf("repeatable", "sticky", "functional")

    @JvmStatic fun list(source: String): List<Event> {
        val table = literalRoot(parse(source), "preset_keys") ?: return emptyList()
        require(table.fields.values.all { it is ThemeValue.LuaTable && it.isLiteralEvent() }) { "preset_keys contains dynamic or invalid entries and requires the Lua source page" }
        return table.fields.map { (id, value) -> read(id, value as ThemeValue.LuaTable) }
    }

    @JvmStatic fun actionLabels(source: String): Map<String, String> {
        val table = literalRoot(parse(source), "action_labels") ?: return emptyMap()
        require(table.fields.values.all { it is ThemeValue.LuaString }) { "action_labels contains dynamic or non-string entries and requires the Lua source page" }
        return table.fields.mapNotNull { (id, value) -> (value as? ThemeValue.LuaString)?.value?.let { id to it } }.toMap(LinkedHashMap())
    }

    @JvmStatic fun updateActionLabels(source: String, values: Map<String, String?>): String {
        require(values.keys.all { it in labels }) { "Unsupported action label key" }
        val parsed = parse(source); val old = literalRoot(parsed, "action_labels") ?: ThemeValue.LuaTable()
        require(old.fields.values.all { it is ThemeValue.LuaString }) { "action_labels contains dynamic or non-string entries and requires the Lua source page" }
        val fields = LinkedHashMap(old.fields)
        labels.forEach { id ->
            if (id in values) values[id]?.let { fields[id] = ThemeValue.LuaString(it) } ?: fields.remove(id)
        }
        return verifiedWrite(parsed.set("action_labels", ThemeValue.LuaTable(fields)))
    }

    @JvmStatic fun put(source: String, event: Event, replace: Boolean): String {
        validateEvent(event)
        val parsed = parse(source); val old = literalRoot(parsed, "preset_keys") ?: ThemeValue.LuaTable()
        require(replace || event.id !in old.fields) { "Preset event already exists: ${event.id}" }
        require(event.id !in old.fields || old.fields[event.id] is ThemeValue.LuaTable) { "Dynamic preset event requires the Lua source page" }
        val previous = old.fields[event.id] as? ThemeValue.LuaTable
        require(previous == null || previous.isLiteralEvent()) { "Dynamic or invalid preset event requires the Lua source page" }
        val root = LinkedHashMap(old.fields); root[event.id] = write(event, previous)
        return verifiedWrite(parsed.set("preset_keys", ThemeValue.LuaTable(root)))
    }

    @JvmStatic fun copy(source: String, sourceId: String, targetId: String): String {
        val event = list(source).firstOrNull { it.id == sourceId } ?: error("Preset event not found: $sourceId")
        return put(source, event.copy(id = targetId), false)
    }

    @JvmStatic fun renameDefinition(source: String, oldId: String, newId: String): String {
        validateId(oldId); validateId(newId); val parsed = parse(source); val table = literalRoot(parsed, "preset_keys") ?: error("preset_keys is missing")
        require(oldId in table.fields) { "Preset event not found: $oldId" }; require(newId !in table.fields) { "Preset event already exists: $newId" }
        require(table.fields[oldId] is ThemeValue.LuaTable && (table.fields[oldId] as ThemeValue.LuaTable).isLiteralEvent()) { "Dynamic or invalid preset event requires the Lua source page" }
        val fields = linkedMapOf<String, ThemeValue>(); table.fields.forEach { (id, value) -> fields[if (id == oldId) newId else id] = value }
        return verifiedWrite(parsed.set("preset_keys", ThemeValue.LuaTable(fields)))
    }

    @JvmStatic fun deleteDefinition(source: String, id: String): String {
        validateId(id); val parsed = parse(source); val table = literalRoot(parsed, "preset_keys") ?: error("preset_keys is missing")
        require(id in table.fields) { "Preset event not found: $id" }; require(table.fields[id] is ThemeValue.LuaTable && (table.fields[id] as ThemeValue.LuaTable).isLiteralEvent()) { "Dynamic or invalid preset event requires the Lua source page" }; val fields = LinkedHashMap(table.fields); fields.remove(id)
        return verifiedWrite(parsed.set("preset_keys", ThemeValue.LuaTable(fields)))
    }

    @JvmStatic fun references(source: String, id: String): Int { validateId(id); return countValue(parse(source), id) }

    @JvmStatic fun hasUncertainReference(source: String, id: String): Boolean {
        validateId(id); val document = parse(source); val word = Regex("\\b${Regex.escape(id)}\\b")
        return document.nodes.any { node ->
            if (node.source == "preset_keys") false
            else node.value.hasRawReference(word)
        }
    }


    @JvmStatic fun replaceReferences(source: String, oldId: String, newId: String): ReferenceUpdate {
        validateId(oldId); validateId(newId); val parsed = parse(source); var count = 0
        fun visit(value: ThemeValue, path: List<String>): ThemeValue = when (value) {
            is ThemeValue.LuaTable -> ThemeValue.LuaTable(LinkedHashMap<String, ThemeValue>().apply { value.fields.forEach { (name, child) -> put(name, visit(child, path + name)) } })
            is ThemeValue.LuaString -> if (value.value == oldId && referencePosition(path)) { count++; ThemeValue.LuaString(newId) } else value
            else -> value
        }
        val nodes = parsed.nodes.map { node -> if (node.source == "preset_keys" || node.value is ThemeValue.RawLuaNode) node else node.copy(value = visit(node.value, listOf(node.source))) }
        if (count == 0) return ReferenceUpdate(source, 0)
        return ReferenceUpdate(verifiedWrite(parsed.copy(nodes = nodes)), count)
    }

    private fun countValue(document: ThemeDocument, id: String): Int {
        var count = 0
        fun visit(value: ThemeValue, path: List<String>) {
            when (value) {
                is ThemeValue.LuaTable -> value.fields.forEach { (name, child) -> visit(child, path + name) }
                is ThemeValue.LuaString -> if (value.value == id && referencePosition(path)) count++
                else -> Unit
            }
        }
        document.nodes.forEach { if (it.source != "preset_keys" && it.value !is ThemeValue.RawLuaNode) visit(it.value, listOf(it.source)) }
        return count
    }

    private fun referencePosition(path: List<String>): Boolean {
        val field = path.lastOrNull()
        if (field in eventSlots) return true
        val parent = path.dropLast(1).lastOrNull()
        if (parent == "swipe") return true
        if (field?.startsWith("#") != true) return false
        if (parent == "popup") return true
        val toolbarKeys = path.size == 3 && path[0] in setOf("toolbar", "tool_bar") && path[1] == "keys"
        val panelToolbarKeys = path.size == 5 && path[0] in setOf("candidate", "symbol", "clipboard") && path[1] == "expanded" && path[2] in setOf("toolbar", "tool_bar") && path[3] == "keys" ||
            path.size == 4 && path[0] in setOf("symbol", "clipboard") && path[1] in setOf("toolbar", "tool_bar") && path[2] == "keys"
        val layoutKeys = parent == "keys" && path.firstOrNull() in setOf("keys", "rows", "flex_box", "key_maps")
        return toolbarKeys || panelToolbarKeys || layoutKeys
    }

    @JvmStatic fun fromLiteralTable(id: String, table: ThemeValue.LuaTable): Event { require(table.isLiteralEvent()) { "Dynamic or invalid event table requires the Lua source page" }; return read(id, table) }
    @JvmStatic fun toLiteralTable(event: Event, previous: ThemeValue.LuaTable?): ThemeValue.LuaTable { validateEvent(event); return write(event, previous) }

    private fun read(id: String, table: ThemeValue.LuaTable): Event = Event(
        id, string(table, "send"), string(table, "text"), string(table, "commit"), string(table, "command"),
        string(table, "option"), string(table, "select"), string(table, "toggle"), string(table, "label"),
        string(table, "preview"), string(table, "description"), strings(table.fields["states"]), string(table, "shift_lock"),
        bool(table, "repeatable", false), bool(table, "sticky", false), bool(table, "functional", true), (table.fields["index"] as? ThemeValue.LuaNumber)?.value,
    )

    private fun write(event: Event, previous: ThemeValue.LuaTable?): ThemeValue.LuaTable {
        val fields = LinkedHashMap(previous?.fields ?: emptyMap())
        fun text(name: String, value: String) { if (value.isNotEmpty() || previous?.fields?.containsKey(name) == true) fields[name] = ThemeValue.LuaString(value) else fields.remove(name) }
        text("send", event.send); text("text", event.text); text("commit", event.commit); text("command", event.command); text("option", event.option); text("select", event.select); text("toggle", event.toggle); text("label", event.label); text("preview", event.preview); text("description", event.description); text("shift_lock", event.shiftLock)
        if (event.states.isEmpty() && previous?.fields?.containsKey("states") != true) fields.remove("states") else fields["states"] = ThemeValue.LuaTable(linkedMapOf<String, ThemeValue>().apply { event.states.forEachIndexed { i, value -> put("#${i + 1}", ThemeValue.LuaString(value)) } })
        if (previous?.fields?.containsKey("repeatable") == true || event.repeatable) fields["repeatable"] = ThemeValue.LuaBoolean(event.repeatable) else fields.remove("repeatable")
        if (previous?.fields?.containsKey("sticky") == true || event.sticky) fields["sticky"] = ThemeValue.LuaBoolean(event.sticky) else fields.remove("sticky")
        if (previous?.fields?.containsKey("functional") == true || !event.functional) fields["functional"] = ThemeValue.LuaBoolean(event.functional) else fields.remove("functional")
        if (event.index == null) fields.remove("index") else fields["index"] = ThemeValue.LuaNumber(event.index)
        return ThemeValue.LuaTable(fields)
    }

    private fun validateEvent(event: Event) {
        validateId(event.id)
        require(event.shiftLock in setOf("", "click", "double", "long")) { "shift_lock must be click, double, long, or empty" }
        require(event.index == null || (event.index.isFinite() && event.index % 1.0 == 0.0 && event.index in Int.MIN_VALUE.toDouble()..Int.MAX_VALUE.toDouble())) { "index must be a 32-bit integer" }
    }

    private fun parse(source: String): ThemeDocument = ThemeLuaParser().parse(source).also { result ->
        require(result.diagnostics.none { it.severity == Severity.ERROR }) { "Lua source contains errors" }
        require(result.diagnostics.none { it.message.startsWith("Unsupported table key") }) { "Unsupported table keys require the Lua source page" }
    }.document
    private fun literalRoot(document: ThemeDocument, name: String): ThemeValue.LuaTable? { val value = document.get(name); require(value == null || value is ThemeValue.LuaTable) { "$name is dynamic and requires the Lua source page" }; return value as? ThemeValue.LuaTable }
    private fun verifiedWrite(document: ThemeDocument): String = ThemeLuaWriter.write(document).also { parse(it) }
    private fun validateId(id: String) { require(safeId.matches(id)) { "Preset ID must be a Lua-safe identifier" } }
    private fun string(table: ThemeValue.LuaTable, name: String) = (table.fields[name] as? ThemeValue.LuaString)?.value.orEmpty()
    private fun bool(table: ThemeValue.LuaTable, name: String, fallback: Boolean) = (table.fields[name] as? ThemeValue.LuaBoolean)?.value ?: fallback
    private fun strings(value: ThemeValue?): List<String> = (value as? ThemeValue.LuaTable)?.fields?.entries?.filter { it.key.startsWith("#") }?.sortedBy { it.key.drop(1).toIntOrNull() }?.mapNotNull { (it.value as? ThemeValue.LuaString)?.value } ?: emptyList()
    private fun ThemeValue.LuaTable.isLiteralEvent(): Boolean {
        if (containsRaw()) return false
        if (stringFields.any { name -> fields[name]?.let { it !is ThemeValue.LuaString } == true }) return false
        if (booleanFields.any { name -> fields[name]?.let { it !is ThemeValue.LuaBoolean } == true }) return false
        if (fields["index"]?.let { it !is ThemeValue.LuaNumber } == true) return false
        val states = fields["states"]
        if (states != null && (states !is ThemeValue.LuaTable || states.fields.any { (name, value) -> !name.startsWith("#") || value !is ThemeValue.LuaString })) return false
        return true
    }
    private fun ThemeValue.hasRawReference(word: Regex): Boolean = when (this) {
        is ThemeValue.RawLuaNode -> word.containsMatchIn(source)
        is ThemeValue.LuaTable -> fields.values.any { it.hasRawReference(word) }
        else -> false
    }
    private fun ThemeValue.containsRaw(): Boolean = when (this) { is ThemeValue.RawLuaNode -> true; is ThemeValue.LuaTable -> fields.values.any { it.containsRaw() }; else -> false }
}
