/*
 * SPDX-FileCopyrightText: 2025 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.editor.ui

import com.osfans.trime.editor.core.ThemeDocument
import com.osfans.trime.editor.core.ThemeValue
import kotlin.math.abs

/** Static layout conversion. No Lua callback or event is executed. */
object ThemeLayoutMigration {
    private val layoutRoots = listOf("rows", "flex_box", "keys", "key_maps")

    data class Preview(
        val source: ThemeEditorModel.LayoutMode,
        val target: ThemeEditorModel.LayoutMode,
        val keyCount: Int,
        val sourceContainerCount: Int,
        val targetContainerCount: Int,
        val omittedKeyMapPages: Int,
        val notes: List<String>,
    )

    data class Result(
        val document: ThemeDocument,
        val model: ThemeEditorModel,
        val preview: Preview,
    )

    @JvmStatic
    fun preview(source: ThemeEditorModel, target: ThemeEditorModel.LayoutMode): Preview {
        require(target != ThemeEditorModel.LayoutMode.NONE) { "A concrete target layout is required" }
        val groups = visualGroups(source.keys)
        val sourceContainers = when (source.layoutMode) {
            ThemeEditorModel.LayoutMode.ROWS -> source.rows.size
            ThemeEditorModel.LayoutMode.FLEX_BOX -> source.flexContainers.size
            ThemeEditorModel.LayoutMode.KEY_MAPS -> source.keyMapPages.size
            ThemeEditorModel.LayoutMode.ABSOLUTE_KEYS -> source.keys.size
            ThemeEditorModel.LayoutMode.NONE -> 0
        }
        val targetContainers = when (target) {
            ThemeEditorModel.LayoutMode.ROWS -> groups.size
            ThemeEditorModel.LayoutMode.FLEX_BOX -> groups.size + 1
            ThemeEditorModel.LayoutMode.KEY_MAPS -> 1
            ThemeEditorModel.LayoutMode.ABSOLUTE_KEYS -> source.keys.size
            ThemeEditorModel.LayoutMode.NONE -> 0
        }
        val omitted = if (source.layoutMode == ThemeEditorModel.LayoutMode.KEY_MAPS) (source.keyMapPages.size - 1).coerceAtLeast(0) else 0
        val notes = buildList {
            add("Only literal key fields are converted; Lua callbacks remain static source data.")
            add("The target root becomes the only active layout root.")
            if (omitted > 0) add("$omitted non-active key_maps pages are not converted; choose backup or hidden original data to retain them.")
            if (target != ThemeEditorModel.LayoutMode.ABSOLUTE_KEYS) add("Absolute coordinates become ordering hints; the target layout recalculates positions.")
            if (source.layoutMode == ThemeEditorModel.LayoutMode.FLEX_BOX) add("Nested Flex sizing is flattened into visual rows when the target is not Flex.")
        }
        return Preview(source.layoutMode, target, source.keys.size, sourceContainers, targetContainers, omitted, notes)
    }

    @JvmStatic
    @JvmOverloads
    fun migrate(document: ThemeDocument, source: ThemeEditorModel, target: ThemeEditorModel.LayoutMode, hideOriginal: Boolean = false): Result {
        require(source.layoutMode != ThemeEditorModel.LayoutMode.NONE) { "The source has no literal layout" }
        require(target != ThemeEditorModel.LayoutMode.NONE) { "A concrete target layout is required" }
        require(source.layoutMode != target) { "Source and target layouts are identical" }
        val dynamicRoots = layoutRoots.filter { document.get(it)?.containsRawLua() == true }
        require(dynamicRoots.isEmpty()) { "Dynamic layout roots require the Lua source page: ${dynamicRoots.joinToString()}" }
        val duplicateRoots = document.sourceStatements.mapNotNull { it.path }.groupingBy { it }.eachCount()
            .filter { (path, count) -> count > 1 && path.substringBefore('.') in layoutRoots }.keys
        require(duplicateRoots.isEmpty()) { "Duplicate layout assignments require the Lua source page: ${duplicateRoots.joinToString()}" }
        val migrationPreview = preview(source, target)
        var candidate = document
        if (hideOriginal) {
            val existing = document.get("_editor_hidden_layouts")
            require(existing == null || existing is ThemeValue.LuaTable) { "_editor_hidden_layouts is dynamic; rename or edit it in the Lua source page" }
            val hidden = linkedMapOf<String, ThemeValue>()
            if (existing is ThemeValue.LuaTable) hidden.putAll(existing.fields)
            layoutRoots.forEach { root -> document.get(root)?.let { value ->
                var archiveKey = root
                var suffix = 2
                while (hidden.containsKey(archiveKey)) archiveKey = "${root}_${suffix++}"
                hidden[archiveKey] = value
            } }
            if (hidden.isNotEmpty()) candidate = candidate.set("_editor_hidden_layouts", ThemeValue.LuaTable(hidden))
        }
        layoutRoots.forEach { candidate = candidate.remove(it) }
        val converted = convertModel(source, target)
        candidate = ThemeLayoutCodec.writeWithTemplate(candidate, document, converted)
        return Result(candidate, ThemeLayoutCodec.fromDocument(candidate), migrationPreview)
    }

    private fun convertModel(source: ThemeEditorModel, target: ThemeEditorModel.LayoutMode): ThemeEditorModel {
        val result = source.copy()
        result.layoutMode = target
        result.rows.clear(); result.flexContainers.clear(); result.keyMapPages.clear(); result.keys.clear(); result.selectedIds.clear()
        result.selectedKeyMapPage = 0; result.selectedFlexContainerId = null
        val keys = source.keys.mapIndexed { index, key -> detachedKey(key, "migrated_key_$index") }
        when (target) {
            ThemeEditorModel.LayoutMode.ROWS -> toRows(result, keys)
            ThemeEditorModel.LayoutMode.FLEX_BOX -> toFlex(result, keys)
            ThemeEditorModel.LayoutMode.ABSOLUTE_KEYS -> result.keys.addAll(keys)
            ThemeEditorModel.LayoutMode.KEY_MAPS -> toKeyMaps(result, keys)
            ThemeEditorModel.LayoutMode.NONE -> error("unreachable")
        }
        return result
    }

    private fun toRows(result: ThemeEditorModel, keys: List<ThemeEditorModel.Key>) {
        visualGroups(keys).forEachIndexed { rowIndex, group ->
            val row = ThemeEditorModel.Row("migrated_row_$rowIndex", group.maxOfOrNull { it.height } ?: 16f)
            row.sourcePath = ""; row.sourceHeight = Float.NaN; row.sourceWidth = Float.NaN
            result.rows += row
            var x = 0f
            group.forEach { key -> key.ownerId = row.id; key.x = x; key.y = 8f + result.rows.dropLast(1).sumOf { it.height.toDouble() }.toFloat(); x += key.width; result.keys += key }
        }
        if (result.rows.isEmpty()) result.rows += ThemeEditorModel.Row("migrated_row_0", 18f).also { it.sourceHeight = Float.NaN }
    }

    private fun toFlex(result: ThemeEditorModel, keys: List<ThemeEditorModel.Key>) {
        val root = ThemeEditorModel.FlexContainer("migrated_flex_root", null).also { it.direction = "column"; it.sourcePath = "" }
        result.flexContainers += root; result.selectedFlexContainerId = root.id
        visualGroups(keys).forEachIndexed { rowIndex, group ->
            val row = ThemeEditorModel.FlexContainer("migrated_flex_row_$rowIndex", root.id).also { it.direction = "row"; it.sourcePath = "" }
            result.flexContainers += row
            group.forEach { key -> key.ownerId = row.id; row.keyIds += key.id; result.keys += key }
        }
    }

    private fun toKeyMaps(result: ThemeEditorModel, keys: List<ThemeEditorModel.Key>) {
        val page = ThemeEditorModel.KeyMapPage("migrated_page_0", "Migrated").also { it.sourcePath = "" }
        keys.forEachIndexed { index, key ->
            key.ownerId = page.id; key.x = (index % 8) * 12.3f; key.y = 10f + (index / 8) * 11f
            page.keys += key.copy(); result.keys += key
        }
        result.keyMapPages += page
    }

    private fun visualGroups(keys: List<ThemeEditorModel.Key>): List<List<ThemeEditorModel.Key>> {
        if (keys.isEmpty()) return emptyList()
        val sorted = keys.sortedWith(compareBy<ThemeEditorModel.Key> { it.y }.thenBy { it.x })
        val groups = mutableListOf<MutableList<ThemeEditorModel.Key>>()
        sorted.forEach { key ->
            val group = groups.lastOrNull()
            if (group == null || abs(group.first().y - key.y) > 2f) groups += mutableListOf(key) else group += key
        }
        return groups
    }

    private fun ThemeValue.containsRawLua(): Boolean = when (this) {
        is ThemeValue.RawLuaNode -> true
        is ThemeValue.LuaTable -> fields.values.any { it.containsRawLua() }
        else -> false
    }

    private fun detachedKey(source: ThemeEditorModel.Key, id: String): ThemeEditorModel.Key = source.copy().also { key ->
        key.id = id; key.ownerId = ""; key.sourceClick = key.click; key.sourceLongClick = key.longClick
        key.sourceSwipeLeft = key.swipeLeft; key.sourceSwipeRight = key.swipeRight; key.sourceSwipeUp = key.swipeUp; key.sourceSwipeDown = key.swipeDown
        key.sourceKeyStyle = key.keyStyle; key.sourcePopup = key.popup; key.sourceX = Float.NaN; key.sourceY = Float.NaN
        key.sourceWidth = Float.NaN; key.sourceHeight = Float.NaN; key.editorLocked = false
    }
}
