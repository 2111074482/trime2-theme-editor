/*
 * SPDX-FileCopyrightText: 2025 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.editor.ui

import com.osfans.trime.editor.core.ThemeDocument
import com.osfans.trime.editor.core.ThemeValue
import kotlin.math.max

/** Converts literal keyboard layouts without evaluating Lua callbacks. */
object ThemeLayoutCodec {
    @JvmStatic
    fun fromDocument(document: ThemeDocument): ThemeEditorModel {
        val model = ThemeEditorModel()
        when {
            document.get("rows") is ThemeValue.LuaTable -> parseRows(document, document.get("rows") as ThemeValue.LuaTable, model)
            document.get("flex_box") is ThemeValue.LuaTable -> parseFlex(document.get("flex_box") as ThemeValue.LuaTable, model)
            document.get("keys") is ThemeValue.LuaTable -> parseAbsolute(document.get("keys") as ThemeValue.LuaTable, model)
            document.get("key_maps") is ThemeValue.LuaTable -> parseKeyMaps(document.get("key_maps") as ThemeValue.LuaTable, model)
        }
        return model
    }

    @JvmStatic
    fun write(document: ThemeDocument, model: ThemeEditorModel): ThemeDocument = writeTo(document, document, model, false)

    @JvmStatic
    fun writeWithTemplate(result: ThemeDocument, template: ThemeDocument, model: ThemeEditorModel): ThemeDocument = writeTo(result, template, model, true)

    /**
     * Rebuilds the active layout from the source snapshot captured by the parser.
     * Workspace Undo snapshots keep their original field baselines, so using the previously edited
     * document as a template would retain fields that an Undo intends to remove.
     */
    @JvmStatic
    fun writeAgainstOriginal(document: ThemeDocument, model: ThemeEditorModel): ThemeDocument {
        val original = if (document.originalNodes.isEmpty()) document else document.copy(nodes = document.originalNodes)
        return writeTo(document, original, model, false)
    }

    private fun writeTo(result: ThemeDocument, template: ThemeDocument, model: ThemeEditorModel, migrating: Boolean): ThemeDocument = when (model.layoutMode) {
        ThemeEditorModel.LayoutMode.ROWS -> result.set("rows", writeRows(template, model, migrating))
        ThemeEditorModel.LayoutMode.FLEX_BOX -> result.set("flex_box", writeFlex(template, model, migrating))
        ThemeEditorModel.LayoutMode.ABSOLUTE_KEYS -> result.set("keys", writeAbsolute(template, model))
        ThemeEditorModel.LayoutMode.KEY_MAPS -> result.set("key_maps", writeKeyMaps(template, model, migrating))
        ThemeEditorModel.LayoutMode.NONE -> result
    }

    private fun parseRows(document: ThemeDocument, table: ThemeValue.LuaTable, model: ThemeEditorModel) {
        model.layoutMode = ThemeEditorModel.LayoutMode.ROWS
        val rowValues = arrayValues(table)
        val inheritedHeight = (document.get("key_height") as? ThemeValue.LuaNumber)?.value?.toFloat()
            ?: if (rowValues.isEmpty()) 18f else 100f / rowValues.size
        val inheritedWidth = (document.get("key_width") as? ThemeValue.LuaNumber)?.value?.toFloat()
        var y = 8f
        rowValues.forEachIndexed { rowIndex, rowValue ->
            val row = rowValue as? ThemeValue.LuaTable ?: return@forEachIndexed
            val rowHeight = number(row, "height", inheritedHeight)
            val rowModel = ThemeEditorModel.Row("row_$rowIndex", rowHeight)
            rowModel.width = number(row, "width", -1f)
            rowModel.sourceWidth = rowModel.width
            rowModel.sourcePath = "rows.#${rowIndex + 1}"
            model.rows += rowModel
            val keys = row.fields["keys"] as? ThemeValue.LuaTable ?: return@forEachIndexed
            val keyValues = arrayValues(keys)
            val defaultWidth = if (rowModel.width > 0) rowModel.width else inheritedWidth ?: if (keyValues.isEmpty()) 10f else 100f / keyValues.size
            var x = 0f
            keyValues.forEachIndexed { keyIndex, value ->
                val key = parseKey(value, "row_${rowIndex}_key_$keyIndex", "rows.#${rowIndex + 1}.keys.#${keyIndex + 1}", x, y, defaultWidth, rowHeight)
                key.ownerId = rowModel.id
                model.keys += key
                x += key.width
            }
            y += rowHeight
        }
    }

    private fun parseAbsolute(table: ThemeValue.LuaTable, model: ThemeEditorModel) {
        model.layoutMode = ThemeEditorModel.LayoutMode.ABSOLUTE_KEYS
        arrayValues(table).forEachIndexed { index, value ->
            val fields = (value as? ThemeValue.LuaTable)?.fields
            model.keys += parseKey(
                value, "absolute_key_$index", "keys.#${index + 1}",
                number(fields, "x", 0f), number(fields, "y", 0f),
                number(fields, "width", 10f), number(fields, "height", 16f),
            )
        }
    }

    private fun parseFlex(root: ThemeValue.LuaTable, model: ThemeEditorModel) {
        model.layoutMode = ThemeEditorModel.LayoutMode.FLEX_BOX
        val rootModel = ThemeEditorModel.FlexContainer("flex_root", null)
        rootModel.sourcePath = "flex_box"
        readContainerFields(root, rootModel)
        model.flexContainers += rootModel
        model.selectedFlexContainerId = rootModel.id
        parseFlexKeys(root, rootModel, model, 0f, 8f, 100f, 70f)
        parseFlexChildren(root, rootModel, model, 0f, 8f, 100f, 70f)
    }

    private fun parseFlexChildren(
        table: ThemeValue.LuaTable,
        parent: ThemeEditorModel.FlexContainer,
        model: ThemeEditorModel,
        x: Float,
        y: Float,
        width: Float,
        height: Float,
    ) {
        val children = arrayEntries(table).mapNotNull { (key, value) -> (value as? ThemeValue.LuaTable)?.let { key to it } }
        val vertical = parent.direction == "column"
        val fixed = children.sumOf { (_, child) ->
            val size = if (vertical) number(child, "height", -1f) else number(child, "width", -1f)
            max(0.0, size.toDouble())
        }.toFloat()
        val available = max(0f, (if (vertical) height else width) - fixed)
        val totalGrow = children.sumOf { (_, child) ->
            val fixedSize = if (vertical) number(child, "height", -1f) else number(child, "width", -1f)
            if (fixedSize > 0) 0.0 else max(0.0, number(child, "grow", 1f).toDouble())
        }.toFloat().takeIf { it > 0 } ?: 1f
        var cursor = if (vertical) y else x
        children.forEachIndexed { index, (arrayKey, child) ->
            val id = parent.id + "_container_" + index
            val container = ThemeEditorModel.FlexContainer(id, parent.id)
            container.sourcePath = parent.sourcePath + ".$arrayKey"
            readContainerFields(child, container)
            model.flexContainers += container
            val fixedSize = if (vertical) container.height else container.width
            val flexibleSize = available * max(0f, container.grow) / totalGrow
            val childWidth = if (vertical) width else (if (fixedSize > 0) fixedSize else flexibleSize).coerceAtMost(width)
            val childHeight = if (vertical) (if (fixedSize > 0) fixedSize else flexibleSize).coerceAtMost(height) else height
            val childX = if (vertical) x else cursor
            val childY = if (vertical) cursor else y
            parseFlexKeys(child, container, model, childX, childY, childWidth, childHeight)
            parseFlexChildren(child, container, model, childX, childY, childWidth, childHeight)
            cursor += if (vertical) childHeight else childWidth
        }
    }

    private fun parseFlexKeys(
        table: ThemeValue.LuaTable,
        container: ThemeEditorModel.FlexContainer,
        model: ThemeEditorModel,
        x: Float,
        y: Float,
        width: Float,
        height: Float,
    ) {
        val keys = table.fields["keys"] as? ThemeValue.LuaTable ?: return
        val values = arrayValues(keys)
        val vertical = container.direction == "column"
        var cursor = if (vertical) y else x
        values.forEachIndexed { keyIndex, value ->
            val defaultWidth = if (vertical) width else width / max(1, values.size)
            val defaultHeight = if (vertical) height / max(1, values.size) else height
            val key = parseKey(
                value,
                "${container.id}_key_$keyIndex",
                "${container.sourcePath}.keys.#${keyIndex + 1}",
                if (vertical) x else cursor,
                if (vertical) cursor else y,
                defaultWidth,
                defaultHeight,
            )
            key.ownerId = container.id
            model.keys += key
            container.keyIds += key.id
            cursor += if (vertical) key.height else key.width
        }
    }

    private fun parseKeyMaps(table: ThemeValue.LuaTable, model: ThemeEditorModel) {
        model.layoutMode = ThemeEditorModel.LayoutMode.KEY_MAPS
        arrayValues(table).forEachIndexed { pageIndex, pageValue ->
            val pageTable = pageValue as? ThemeValue.LuaTable ?: return@forEachIndexed
            val page = ThemeEditorModel.KeyMapPage("key_map_$pageIndex", string(pageTable.fields["name"]).ifEmpty { (pageIndex + 1).toString() })
            page.sourcePath = "key_maps.#${pageIndex + 1}"
            val keys = pageTable.fields["keys"] as? ThemeValue.LuaTable
            arrayValues(keys).forEachIndexed { keyIndex, value ->
                val column = keyIndex % 8
                val row = keyIndex / 8
                page.keys += parseKey(value, "page_${pageIndex}_key_$keyIndex", "${page.sourcePath}.keys.#${keyIndex + 1}", column * 12.3f, 10f + row * 11f, 11.5f, 9.5f).also { it.ownerId = page.id }
            }
            model.keyMapPages += page
        }
        if (model.keyMapPages.isNotEmpty()) model.keys += model.keyMapPages[0].keys.map { it.copy() }
    }

    private fun parseKey(value: ThemeValue, id: String, path: String, x: Float, y: Float, width: Float, height: Float): ThemeEditorModel.Key {
        val fields = (value as? ThemeValue.LuaTable)?.fields
        val literal = (value as? ThemeValue.LuaString)?.value.orEmpty()
        val click = string(fields?.get("click")).ifEmpty { literal }
        val label = string(fields?.get("label")).ifEmpty { click.ifEmpty { "?" } }
        return ThemeEditorModel.Key(id, label, number(fields, "x", x), number(fields, "y", y), number(fields, "width", width), number(fields, "height", height)).also { key ->
            key.sourcePath = path
            key.click = click; key.sourceClick = click
            key.longClick = string(fields?.get("long_click")); key.sourceLongClick = key.longClick
            key.swipeLeft = string(fields?.get("swipe_left")); key.sourceSwipeLeft = key.swipeLeft
            key.swipeRight = string(fields?.get("swipe_right")); key.sourceSwipeRight = key.swipeRight
            key.swipeUp = string(fields?.get("swipe_up")); key.sourceSwipeUp = key.swipeUp
            key.swipeDown = string(fields?.get("swipe_down")); key.sourceSwipeDown = key.swipeDown
            key.combo = string(fields?.get("combo")); key.sourceCombo = key.combo
            key.composing = string(fields?.get("composing")); key.sourceComposing = key.composing
            key.hasMenu = string(fields?.get("has_menu")); key.sourceHasMenu = key.hasMenu
            key.paging = string(fields?.get("paging")); key.sourcePaging = key.paging
            key.ascii = string(fields?.get("ascii")); key.sourceAscii = key.ascii
            key.hasNonLiteralEventSource = listOf("click", "long_click", "swipe_left", "swipe_right", "swipe_up", "swipe_down", "combo", "composing", "has_menu", "paging", "ascii").any { name -> fields?.get(name)?.let { it !is ThemeValue.LuaString } == true }
            key.keyStyle = string(fields?.get("style")); key.sourceKeyStyle = key.keyStyle
            val popup = fields?.get("popup")
            key.popupArray = popup is ThemeValue.LuaTable
            key.popup = when (popup) {
                is ThemeValue.LuaString -> popup.value
                is ThemeValue.LuaTable -> arrayValues(popup).mapNotNull { (it as? ThemeValue.LuaString)?.value }.joinToString(", ")
                else -> ""
            }
            key.sourcePopup = key.popup
        }
    }

    private fun readContainerFields(table: ThemeValue.LuaTable, target: ThemeEditorModel.FlexContainer) {
        target.direction = string(table.fields["direction"]).let { if (it == "column") "column" else "row" }
        target.style = string(table.fields["style"])
        target.width = number(table, "width", -1f)
        target.height = number(table, "height", -1f)
        target.grow = number(table, "grow", 1f)
    }

    private fun writeRows(document: ThemeDocument, model: ThemeEditorModel, migrating: Boolean): ThemeValue.LuaTable {
        val result = linkedMapOf<String, ThemeValue>()
        model.rows.forEachIndexed { rowIndex, rowModel ->
            val old = (if (rowModel.sourcePath.isNotBlank()) document.get(rowModel.sourcePath) else null) as? ThemeValue.LuaTable
            val rowFields = LinkedHashMap(old?.fields ?: emptyMap())
            if (rowFields.containsKey("height") || rowModel.height != rowModel.sourceHeight) rowFields["height"] = ThemeValue.LuaNumber(rowModel.height.toDouble())
            if (rowFields.containsKey("width") || rowModel.width != rowModel.sourceWidth) {
                if (rowModel.width > 0) rowFields["width"] = ThemeValue.LuaNumber(rowModel.width.toDouble()) else rowFields.remove("width")
            }
            val rowKeys = model.keys.filter { it.ownerId == rowModel.id }
            rowFields["keys"] = ThemeValue.LuaTable(linkedMapOf<String, ThemeValue>().apply {
                rowKeys.forEachIndexed { index, key -> put("#${index + 1}", updatedKey(document.get(key.sourcePath), key, false, migrating)) }
            })
            result["#${rowIndex + 1}"] = ThemeValue.LuaTable(rowFields)
        }
        return ThemeValue.LuaTable(result)
    }

    private fun writeAbsolute(document: ThemeDocument, model: ThemeEditorModel) = ThemeValue.LuaTable(linkedMapOf<String, ThemeValue>().apply {
        model.keys.forEachIndexed { index, key -> put("#${index + 1}", updatedKey(document.get(key.sourcePath), key, true, false)) }
    })

    private fun writeFlex(document: ThemeDocument, model: ThemeEditorModel, migrating: Boolean): ThemeValue.LuaTable {
        val originalRoot = document.get("flex_box") as? ThemeValue.LuaTable ?: ThemeValue.LuaTable()
        val byId = model.flexContainers.associateBy { it.id }
        val keysById = model.keys.associateBy { it.id }
        val rootModel = model.flexContainers.firstOrNull { it.parentId == null } ?: return originalRoot

        fun original(container: ThemeEditorModel.FlexContainer): ThemeValue.LuaTable? =
            if (container.sourcePath.isBlank()) null else document.get(container.sourcePath) as? ThemeValue.LuaTable

        fun build(container: ThemeEditorModel.FlexContainer): ThemeValue.LuaTable {
            val old = original(container)
            val fields = LinkedHashMap(old?.fields ?: emptyMap())
            fields.keys.filter { it.startsWith("#") }.forEach(fields::remove)
            if (fields.containsKey("direction") || container.direction != "row") fields["direction"] = ThemeValue.LuaString(container.direction) else fields.remove("direction")
            if (container.style.isNotEmpty()) fields["style"] = ThemeValue.LuaString(container.style) else fields.remove("style")
            if (fields.containsKey("width") || container.width > 0) fields["width"] = ThemeValue.LuaNumber(container.width.toDouble()) else fields.remove("width")
            if (fields.containsKey("height") || container.height > 0) fields["height"] = ThemeValue.LuaNumber(container.height.toDouble()) else fields.remove("height")
            if (fields.containsKey("grow") || container.grow != 1f) fields["grow"] = ThemeValue.LuaNumber(container.grow.toDouble()) else fields.remove("grow")

            if (container.keyIds.isNotEmpty() || fields["keys"] is ThemeValue.LuaTable) {
                val oldKeys = fields["keys"] as? ThemeValue.LuaTable
                fields["keys"] = ThemeValue.LuaTable(linkedMapOf<String, ThemeValue>().apply {
                    container.keyIds.forEachIndexed { index, id ->
                        val key = keysById[id] ?: return@forEachIndexed
                        val oldKey = if (key.sourcePath.isBlank()) null else document.get(key.sourcePath)
                            ?: oldKeys?.fields?.get("#${index + 1}")
                        put("#${index + 1}", updatedKey(oldKey, key, false, migrating))
                    }
                })
            }
            model.flexContainers.filter { it.parentId == container.id }.forEachIndexed { index, child ->
                fields["#${index + 1}"] = build(child)
            }
            return ThemeValue.LuaTable(fields)
        }
        return build(byId[rootModel.id] ?: rootModel)
    }

    private fun writeKeyMaps(document: ThemeDocument, model: ThemeEditorModel, migrating: Boolean): ThemeValue.LuaTable {
        return ThemeValue.LuaTable(linkedMapOf<String, ThemeValue>().apply {
            model.keyMapPages.forEachIndexed { pageIndex, page ->
                val oldPage = (if (page.sourcePath.isNotBlank()) document.get(page.sourcePath) else null) as? ThemeValue.LuaTable
                val fields = LinkedHashMap(oldPage?.fields ?: emptyMap())
                fields["name"] = ThemeValue.LuaString(page.name)
                val visibleKeys = if (pageIndex == model.selectedKeyMapPage) model.keys else page.keys
                fields["keys"] = ThemeValue.LuaTable(linkedMapOf<String, ThemeValue>().apply {
                    visibleKeys.forEachIndexed { keyIndex, key -> put("#${keyIndex + 1}", updatedKey(document.get(key.sourcePath), key, false, migrating)) }
                })
                put("#${pageIndex + 1}", ThemeValue.LuaTable(fields))
            }
        })
    }

    private fun updatedKey(original: ThemeValue?, key: ThemeEditorModel.Key, absolute: Boolean, stripCoordinates: Boolean): ThemeValue {
        require(original !is ThemeValue.RawLuaNode) { "动态按键节点必须在 Lua 源代码页编辑" }
        val labelChanged = key.label != key.sourceLabel
        val clickChanged = key.click != key.sourceClick
        val eventChanged = clickChanged || key.longClick != key.sourceLongClick || key.swipeLeft != key.sourceSwipeLeft || key.swipeRight != key.sourceSwipeRight || key.swipeUp != key.sourceSwipeUp || key.swipeDown != key.sourceSwipeDown || key.combo != key.sourceCombo || key.composing != key.sourceComposing || key.hasMenu != key.sourceHasMenu || key.paging != key.sourcePaging || key.ascii != key.sourceAscii || key.keyStyle != key.sourceKeyStyle || key.popup != key.sourcePopup
        val widthChanged = key.width != key.sourceWidth
        val heightChanged = key.height != key.sourceHeight
        val xChanged = key.x != key.sourceX
        val yChanged = key.y != key.sourceY
        if (original is ThemeValue.LuaString && !eventChanged && !widthChanged && !heightChanged && (!absolute || (!xChanged && !yChanged))) {
            if (!labelChanged) return original
            val sourceCodePoints = original.value.codePointCount(0, original.value.length)
            val labelCodePoints = key.label.codePointCount(0, key.label.length)
            if (key.ownerId.startsWith("key_map_") && sourceCodePoints == 1 && labelCodePoints == 1) {
                return ThemeValue.LuaString(key.label)
            }
        }
        val fields = LinkedHashMap((original as? ThemeValue.LuaTable)?.fields ?: emptyMap())
        if (original is ThemeValue.LuaString) fields["click"] = ThemeValue.LuaString(key.click.ifEmpty { original.value })
        if (labelChanged) fields["label"] = ThemeValue.LuaString(key.label)
        if (clickChanged) setLiteral(fields, "click", key.click)
        else if (original == null) fields["click"] = ThemeValue.LuaString(key.click.ifEmpty { key.label })
        setChangedLiteral(fields, "long_click", key.longClick, key.sourceLongClick)
        setChangedLiteral(fields, "swipe_left", key.swipeLeft, key.sourceSwipeLeft)
        setChangedLiteral(fields, "swipe_right", key.swipeRight, key.sourceSwipeRight)
        setChangedLiteral(fields, "swipe_up", key.swipeUp, key.sourceSwipeUp)
        setChangedLiteral(fields, "swipe_down", key.swipeDown, key.sourceSwipeDown)
        setChangedLiteral(fields, "combo", key.combo, key.sourceCombo)
        setChangedLiteral(fields, "composing", key.composing, key.sourceComposing)
        setChangedLiteral(fields, "has_menu", key.hasMenu, key.sourceHasMenu)
        setChangedLiteral(fields, "paging", key.paging, key.sourcePaging)
        setChangedLiteral(fields, "ascii", key.ascii, key.sourceAscii)
        setChangedLiteral(fields, "style", key.keyStyle, key.sourceKeyStyle)
        if (key.popup != key.sourcePopup) {
            if (key.popup.isBlank()) fields.remove("popup")
            else if (key.popupArray) fields["popup"] = ThemeValue.LuaTable(linkedMapOf<String, ThemeValue>().apply {
                key.popup.split(',').map { it.trim() }.filter { it.isNotEmpty() }.forEachIndexed { index, value -> put("#${index + 1}", ThemeValue.LuaString(value)) }
            }) else fields["popup"] = ThemeValue.LuaString(key.popup)
        }
        if (fields.containsKey("width") || widthChanged) fields["width"] = ThemeValue.LuaNumber(key.width.toDouble())
        if (fields.containsKey("height") || heightChanged) fields["height"] = ThemeValue.LuaNumber(key.height.toDouble())
        if (absolute) {
            if (fields.containsKey("x") || xChanged) fields["x"] = ThemeValue.LuaNumber(key.x.toDouble())
            if (fields.containsKey("y") || yChanged) fields["y"] = ThemeValue.LuaNumber(key.y.toDouble())
        } else if (stripCoordinates) {
            fields.remove("x"); fields.remove("y")
        }
        return ThemeValue.LuaTable(fields)
    }

    private fun setChangedLiteral(fields: LinkedHashMap<String, ThemeValue>, name: String, value: String, source: String) {
        if (value == source) return
        setLiteral(fields, name, value)
    }

    private fun setLiteral(fields: LinkedHashMap<String, ThemeValue>, name: String, value: String) {
        if (value.isEmpty()) fields.remove(name) else fields[name] = ThemeValue.LuaString(value)
    }

    private fun arrayEntries(table: ThemeValue.LuaTable?): List<Pair<String, ThemeValue>> = table?.fields?.entries
        ?.filter { it.key.startsWith("#") }
        ?.sortedBy { it.key.drop(1).toIntOrNull() ?: Int.MAX_VALUE }
        ?.map { it.key to it.value }
        ?: emptyList()

    private fun arrayValues(table: ThemeValue.LuaTable?): List<ThemeValue> = arrayEntries(table).map { it.second }
    private fun string(value: ThemeValue?): String = (value as? ThemeValue.LuaString)?.value.orEmpty()
    private fun number(table: ThemeValue.LuaTable, key: String, fallback: Float) = number(table.fields, key, fallback)
    private fun number(fields: Map<String, ThemeValue>?, key: String, fallback: Float): Float = (fields?.get(key) as? ThemeValue.LuaNumber)?.value?.toFloat() ?: fallback
}
