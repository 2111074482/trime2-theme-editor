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

/** Static key event-source editing. No Event, command, callback, or script is executed. */
object ThemeKeyEvents {
    enum class Source { MISSING, STRING, INLINE_EVENT, FULL_KEY_REPLACEMENT, RAW_LUA }
    data class Slot(val name: String, val source: Source, val literal: String?, val event: ThemePresetEvents.Event?, val risky: Boolean)
    data class Options(
        val swipeRepeatable: Boolean?,
        val sendBindings: Boolean?,
        val effectiveSendBindings: Boolean = sendBindings ?: false,
        val sendBindingsSource: String = if (sendBindings == null) "runtime default: no effective conditional events" else "explicit",
    )
    data class Hints(val values: Map<String, String?>)

    @JvmField val HINTS = arrayOf("hint_long", "hint_left", "hint_right", "hint_up", "hint_down")
    @JvmField val SLOTS = arrayOf("click", "long_click", "swipe_left", "swipe_right", "swipe_up", "swipe_down", "combo", "composing", "has_menu", "paging", "ascii")
    @JvmField val STRING_ONLY_SLOTS = arrayOf("composing", "has_menu", "paging")

    @JvmStatic fun parseDocument(source: String): ThemeDocument = ThemeLuaParser().parse(source).also { result ->
        require(result.diagnostics.none { it.severity == Severity.ERROR }) { "Keyboard Lua source contains errors" }
        require(result.diagnostics.none { it.message.startsWith("Unsupported table key") }) { "Unsupported table keys require the Lua source page" }
    }.document

    @JvmStatic fun read(document: ThemeDocument, keyPath: String): List<Slot> {
        val value = document.get(keyPath)
        val key = when (value) {
            is ThemeValue.LuaTable -> value
            is ThemeValue.LuaString -> ThemeValue.LuaTable(linkedMapOf("click" to value))
            null -> return emptyList()
            else -> throw IllegalArgumentException("Key source is dynamic and requires the Lua source page")
        }
        return SLOTS.map { name -> slot(name, key.fields[name]) }
    }

    @JvmStatic fun options(document: ThemeDocument, keyPath: String): Options {
        val value = document.get(keyPath); if (value is ThemeValue.RawLuaNode) error("Key source is dynamic and requires the Lua source page")
        val key = value as? ThemeValue.LuaTable ?: return Options(null, null)
        require(key.fields["swipe_repeatable"] == null || key.fields["swipe_repeatable"] is ThemeValue.LuaBoolean) { "swipe_repeatable has an invalid type and requires the source page" }
        require(key.fields["send_bindings"] == null || key.fields["send_bindings"] is ThemeValue.LuaBoolean) { "send_bindings has an invalid type and requires the source page" }
        val swipeRepeatable = (key.fields["swipe_repeatable"] as? ThemeValue.LuaBoolean)?.value
        val sendBindings = (key.fields["send_bindings"] as? ThemeValue.LuaBoolean)?.value
        val conditional = listOf("composing", "has_menu", "paging").any { name ->
            val event = key.fields[name]
            event is ThemeValue.LuaString && event.value.isNotEmpty()
        }
        val uncertainConditional = listOf("composing", "has_menu", "paging").any { name ->
            key.fields[name]?.let { it !is ThemeValue.LuaString } == true
        }
        return Options(
            swipeRepeatable,
            sendBindings,
            sendBindings ?: conditional,
            if (sendBindings != null) "explicit" else if (uncertainConditional) "uncertain: conditional source is not a runtime string" else if (conditional) "runtime default: conditional event present" else "runtime default: no effective conditional events",
        )
    }

    @JvmStatic fun hints(document: ThemeDocument, keyPath: String): Hints {
        val value = document.get(keyPath); if (value is ThemeValue.RawLuaNode) throw IllegalArgumentException("Key source is dynamic and requires the Lua source page")
        val key = value as? ThemeValue.LuaTable ?: return Hints(HINTS.associateWith { null })
        HINTS.forEach { name -> require(key.fields[name] == null || key.fields[name] is ThemeValue.LuaString) { "$name has an invalid type and requires the source page" } }
        return Hints(HINTS.associateWith { name -> (key.fields[name] as? ThemeValue.LuaString)?.value })
    }

    @JvmStatic fun updateHints(document: ThemeDocument, keyPath: String, values: Map<String, String?>): ThemeDocument {
        require(values.keys.all { it in HINTS }) { "Unsupported event hint field" }; hints(document, keyPath); var next = normalizeKey(document, keyPath)
        values.forEach { (name, value) -> next = if (value == null) next.remove("$keyPath.$name") else next.set("$keyPath.$name", ThemeValue.LuaString(value)) }
        return next
    }

    @JvmStatic fun updateString(document: ThemeDocument, keyPath: String, slot: String, value: String?): ThemeDocument {
        require(slot in SLOTS) { "Unsupported key event slot" }; val normalized = normalizeKey(document, keyPath); requireEditable(normalized.get("$keyPath.$slot"), slot)
        return if (value == null) normalized.remove("$keyPath.$slot") else normalized.set("$keyPath.$slot", ThemeValue.LuaString(value))
    }

    @JvmStatic fun updateInline(document: ThemeDocument, keyPath: String, slot: String, event: ThemePresetEvents.Event): ThemeDocument {
        require(slot in SLOTS) { "Unsupported key event slot" }
        require(slot !in STRING_ONLY_SLOTS) { "$slot is string-only in the current Trime runtime" }
        val normalized = normalizeKey(document, keyPath); requireEditable(normalized.get("$keyPath.$slot"), slot)
        return normalized.set("$keyPath.$slot", ThemePresetEvents.toLiteralTable(event, normalized.get("$keyPath.$slot") as? ThemeValue.LuaTable))
    }

    @JvmStatic fun updateOptions(document: ThemeDocument, keyPath: String, swipeRepeatable: Boolean?, sendBindings: Boolean?): ThemeDocument {
        options(document, keyPath)
        var next = normalizeKey(document, keyPath)
        next = if (swipeRepeatable == null) next.remove("$keyPath.swipe_repeatable") else next.set("$keyPath.swipe_repeatable", ThemeValue.LuaBoolean(swipeRepeatable))
        next = if (sendBindings == null) next.remove("$keyPath.send_bindings") else next.set("$keyPath.send_bindings", ThemeValue.LuaBoolean(sendBindings))
        return next
    }

    @JvmStatic fun verifiedSource(document: ThemeDocument): String = ThemeLuaWriter.write(document).also { source ->
        require(ThemeLuaParser().parse(source).diagnostics.none { it.severity == Severity.ERROR }) { "Updated key event source failed verification" }
    }

    private fun normalizeKey(document: ThemeDocument, keyPath: String): ThemeDocument = when (val value = document.get(keyPath)) {
        is ThemeValue.LuaTable -> document
        is ThemeValue.LuaString -> document.set(keyPath, ThemeValue.LuaTable(linkedMapOf("click" to value)))
        is ThemeValue.RawLuaNode -> throw IllegalArgumentException("Key source is dynamic and requires the Lua source page")
        null -> throw IllegalArgumentException("Key source path not found: $keyPath")
        else -> throw IllegalArgumentException("Unsupported key source requires the Lua source page")
    }

    private fun slot(name: String, value: ThemeValue?): Slot = when (value) {
        null -> Slot(name, Source.MISSING, null, null, false)
        is ThemeValue.LuaString -> Slot(name, Source.STRING, value.value, null, value.value.endsWith(".lua", true))
        is ThemeValue.LuaTable -> if (name in STRING_ONLY_SLOTS) Slot(name, Source.RAW_LUA, null, null, true) else {
            val fullKey = name == "ascii" && value.fields["click"] != null
            if (fullKey) Slot(name, Source.FULL_KEY_REPLACEMENT, null, null, value.containsRaw())
            else if (value.containsRaw()) Slot(name, Source.RAW_LUA, null, null, true)
            else ThemePresetEvents.fromLiteralTable(name, value).let { Slot(name, Source.INLINE_EVENT, null, it, it.risky) }
        }
        is ThemeValue.RawLuaNode -> Slot(name, Source.RAW_LUA, value.source, null, true)
        else -> Slot(name, Source.RAW_LUA, null, null, true)
    }

    private fun requireEditable(value: ThemeValue?, slot: String) {
        require(value !is ThemeValue.RawLuaNode) { "$slot uses Raw Lua and requires the source page" }
        if (slot in STRING_ONLY_SLOTS) require(value !is ThemeValue.LuaTable) { "$slot uses a table that the current Trime runtime ignores; use the source page" }
        if (slot == "ascii" && value is ThemeValue.LuaTable) require(value.fields["click"] == null) { "ascii is a full key replacement and requires the source page" }
        require(value !is ThemeValue.LuaTable || !value.containsRaw()) { "$slot contains dynamic fields and requires the source page" }
    }

    private fun ThemeValue.containsRaw(): Boolean = when (this) { is ThemeValue.RawLuaNode -> true; is ThemeValue.LuaTable -> fields.values.any { it.containsRaw() }; else -> false }
}
