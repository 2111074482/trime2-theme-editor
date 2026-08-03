/*
 * SPDX-FileCopyrightText: 2025 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.editor.core

enum class ThemeFieldType { COLOR, DIMENSION, NUMBER, BOOLEAN, TEXT, TABLE, LUA }
enum class ConsumptionStatus { CONSUMED, PARSED_NOT_TRIGGERED, UNRELIABLE, NOT_PARSED, RAW_ONLY }
enum class EditorSupport { VISUAL, CODE_ONLY, READ_ONLY, HIDDEN_INVALID }

enum class PreviewSupport { EXACT, SIMULATED, DISABLED_WITH_REASON, NONE }

enum class WriteSupport { STRUCTURED, PRESERVE_RAW, REJECT }

data class ThemeField(
    val path: String,
    val type: ThemeFieldType,
    val label: String = path,
    val description: String = "",
    val defaultValue: ThemeValue? = null,
    val consumption: ConsumptionStatus = ConsumptionStatus.CONSUMED,
    val editorSupport: EditorSupport = EditorSupport.VISUAL,
    val previewSupport: PreviewSupport = PreviewSupport.EXACT,
    val writeSupport: WriteSupport = WriteSupport.STRUCTURED,
    val fallbackPath: String? = null,
)

class ThemeFieldRegistry @JvmOverloads constructor(fields: Iterable<ThemeField> = defaultFields()) {
    private val fields = fields.associateBy { it.path }
    fun find(path: String): ThemeField? = fields[path]
    fun all(): List<ThemeField> = fields.values.toList()
    fun validate(path: String, value: ThemeValue): String? {
        val field = find(path) ?: return null
        if (value is ThemeValue.RawLuaNode) return null
        val compatible = when (field.type) {
            ThemeFieldType.COLOR, ThemeFieldType.DIMENSION, ThemeFieldType.NUMBER -> value is ThemeValue.LuaNumber
            ThemeFieldType.BOOLEAN -> value is ThemeValue.LuaBoolean
            ThemeFieldType.TEXT -> value is ThemeValue.LuaString
            ThemeFieldType.TABLE -> value is ThemeValue.LuaTable
            ThemeFieldType.LUA -> true
        }
        return if (compatible) null else "Expected ${field.type.name.lowercase()} for $path"
    }

    fun coverage(): ThemeFieldCoverage {
        val all = fields.values
        return ThemeFieldCoverage(
            total = all.size,
            visual = all.count { it.editorSupport == EditorSupport.VISUAL },
            codeOnly = all.count { it.editorSupport == EditorSupport.CODE_ONLY },
            exactPreview = all.count { it.previewSupport == PreviewSupport.EXACT },
            simulatedPreview = all.count { it.previewSupport == PreviewSupport.SIMULATED },
            disabledPreview = all.count { it.previewSupport == PreviewSupport.DISABLED_WITH_REASON },
            structuredWrite = all.count { it.writeSupport == WriteSupport.STRUCTURED },
            rawWrite = all.count { it.writeSupport == WriteSupport.PRESERVE_RAW },
            missing = 0,
        )
    }

    companion object {
        fun defaultFields(): List<ThemeField> {
            val componentFields = buildList {
                fun field(
                    path: String,
                    type: ThemeFieldType,
                    preview: PreviewSupport = PreviewSupport.EXACT,
                    consumption: ConsumptionStatus = ConsumptionStatus.CONSUMED,
                    editor: EditorSupport = EditorSupport.VISUAL,
                    write: WriteSupport = WriteSupport.STRUCTURED,
                ) = add(ThemeField(path, type, consumption = consumption, editorSupport = editor, previewSupport = preview, writeSupport = write))

                fun table(path: String) = field(path, ThemeFieldType.TABLE)
                fun background(
                    path: String,
                    preview: PreviewSupport = PreviewSupport.SIMULATED,
                    codeOnly: Boolean = false,
                ) = field(
                    path = path,
                    type = ThemeFieldType.LUA,
                    preview = if (codeOnly) PreviewSupport.DISABLED_WITH_REASON else preview,
                    consumption = if (codeOnly) ConsumptionStatus.PARSED_NOT_TRIGGERED else ConsumptionStatus.CONSUMED,
                    editor = if (codeOnly) EditorSupport.CODE_ONLY else EditorSupport.VISUAL,
                    write = WriteSupport.PRESERVE_RAW,
                )
                fun simulated(path: String, type: ThemeFieldType) = field(path, type, PreviewSupport.SIMULATED)
                fun codeOnly(path: String, type: ThemeFieldType) = field(
                    path = path,
                    type = type,
                    preview = PreviewSupport.DISABLED_WITH_REASON,
                    consumption = ConsumptionStatus.PARSED_NOT_TRIGGERED,
                    editor = EditorSupport.CODE_ONLY,
                    write = WriteSupport.PRESERVE_RAW,
                )

                fun keyStyle(
                    path: String,
                    codeOnlyVisuals: Boolean = false,
                    exactVisuals: Set<String> = emptySet(),
                ) {
                    table(path)
                    fun visual(suffix: String, type: ThemeFieldType) {
                        val leaf = "$path.$suffix"
                        when {
                            suffix.endsWith("background") -> background(leaf, codeOnly = codeOnlyVisuals)
                            codeOnlyVisuals -> codeOnly(leaf, type)
                            suffix in exactVisuals -> field(leaf, type)
                            else -> simulated(leaf, type)
                        }
                    }
                    visual("text", ThemeFieldType.TEXT)
                    visual("background", ThemeFieldType.LUA)
                    visual("text_color", ThemeFieldType.COLOR)
                    visual("text_size", ThemeFieldType.DIMENSION)
                    visual("elevation", ThemeFieldType.DIMENSION)
                    visual("corner_radius", ThemeFieldType.DIMENSION)
                    visual("shadow_color", ThemeFieldType.COLOR)
                    table("$path.pressed")
                    visual("pressed.background", ThemeFieldType.LUA)
                    visual("pressed.text_color", ThemeFieldType.COLOR)
                    visual("pressed.scale_x", ThemeFieldType.NUMBER)
                    visual("pressed.scale_y", ThemeFieldType.NUMBER)
                    visual("pressed.translation_x", ThemeFieldType.DIMENSION)
                    visual("pressed.translation_y", ThemeFieldType.DIMENSION)
                    visual("pressed.translation_z", ThemeFieldType.DIMENSION)
                    visual("pressed.shadow_color", ThemeFieldType.COLOR)
                    table("$path.hint")
                    visual("hint.background", ThemeFieldType.LUA)
                    visual("hint.text_color", ThemeFieldType.COLOR)
                    visual("hint.text_size", ThemeFieldType.DIMENSION)
                    table("$path.pressed.hint")
                    visual("pressed.hint.background", ThemeFieldType.LUA)
                    visual("pressed.hint.text_color", ThemeFieldType.COLOR)
                    visual("pressed.hint.text_size", ThemeFieldType.DIMENSION)
                }

                fun candidate(path: String, expanded: Boolean = false) {
                    table(path)
                    fun candidateVisual(suffix: String, type: ThemeFieldType, preview: PreviewSupport = PreviewSupport.EXACT) {
                        val leaf = "$path.$suffix"
                        when {
                            suffix == "background" -> background(leaf)
                            expanded -> codeOnly(leaf, type)
                            suffix.endsWith("background") -> background(leaf)
                            else -> field(leaf, type, preview)
                        }
                    }
                    candidateVisual("height", ThemeFieldType.DIMENSION)
                    candidateVisual("background", ThemeFieldType.LUA, PreviewSupport.SIMULATED)
                    candidateVisual("text_color", ThemeFieldType.COLOR)
                    candidateVisual("text_size", ThemeFieldType.DIMENSION)
                    candidateVisual("elevation", ThemeFieldType.DIMENSION, PreviewSupport.SIMULATED)
                    candidateVisual("shadow_color", ThemeFieldType.COLOR, PreviewSupport.SIMULATED)
                    table("$path.pressed")
                    candidateVisual("pressed.background", ThemeFieldType.LUA, PreviewSupport.SIMULATED)
                    candidateVisual("pressed.text_color", ThemeFieldType.COLOR)
                    table("$path.comment")
                    candidateVisual("comment.text_color", ThemeFieldType.COLOR)
                    candidateVisual("comment.text_size", ThemeFieldType.DIMENSION)
                    table("$path.comment.pressed")
                    candidateVisual("comment.pressed.text_color", ThemeFieldType.COLOR, PreviewSupport.SIMULATED)
                    candidateVisual("comment.pressed.text_size", ThemeFieldType.DIMENSION, PreviewSupport.SIMULATED)
                    keyStyle("$path.key")
                }

                candidate("candidate")
                candidate("candidate.expanded", expanded = true)

                table("toolbar")
                codeOnly("toolbar.height", ThemeFieldType.DIMENSION)
                background("toolbar.background")
                codeOnly("toolbar.text_color", ThemeFieldType.COLOR)
                field("toolbar.elevation", ThemeFieldType.DIMENSION)
                field("toolbar.shadow_color", ThemeFieldType.COLOR)
                simulated("toolbar.schema_switches", ThemeFieldType.BOOLEAN)
                keyStyle("toolbar.hide")
                keyStyle("toolbar.key")

                table("symbol")
                background("symbol.background")
                codeOnly("symbol.text_color", ThemeFieldType.COLOR)
                field("symbol.indicator_color", ThemeFieldType.COLOR)
                keyStyle("symbol.text")
                keyStyle("symbol.key", exactVisuals = setOf("text_color"))
                table("symbol.tab_bar")
                simulated("symbol.tab_bar.gravity", ThemeFieldType.TEXT)
                field("symbol.tab_bar.height", ThemeFieldType.DIMENSION)
                field("symbol.tab_bar.indicator_color", ThemeFieldType.COLOR)
                simulated("symbol.tool_bar.gravity", ThemeFieldType.TEXT)
                simulated("symbol.tool_bar.height", ThemeFieldType.DIMENSION)
                keyStyle("symbol.tool_bar", codeOnlyVisuals = true)

                table("clipboard")
                background("clipboard.background")
                simulated("clipboard.indicator_color", ThemeFieldType.COLOR)
                keyStyle("clipboard.key")
                keyStyle("clipboard.item")
                table("clipboard.tab_bar")
                simulated("clipboard.tab_bar.gravity", ThemeFieldType.TEXT)
                simulated("clipboard.tab_bar.height", ThemeFieldType.DIMENSION)
                simulated("clipboard.tab_bar.indicator_color", ThemeFieldType.COLOR)
                simulated("clipboard.tool_bar.gravity", ThemeFieldType.TEXT)
                simulated("clipboard.tool_bar.height", ThemeFieldType.DIMENSION)
                keyStyle("clipboard.tool_bar", codeOnlyVisuals = true)
            }

            return listOf(
                ThemeField("name", ThemeFieldType.TEXT),
                ThemeField("author", ThemeFieldType.TEXT),
                ThemeField("style", ThemeFieldType.TEXT, defaultValue = ThemeValue.LuaString("light")),
                ThemeField("keyboard", ThemeFieldType.LUA, defaultValue = ThemeValue.LuaString("qwerty26")),
                ThemeField("background", ThemeFieldType.COLOR),
                ThemeField("text_color", ThemeFieldType.COLOR),
                ThemeField("text_size", ThemeFieldType.DIMENSION),
                ThemeField("height", ThemeFieldType.DIMENSION),
                ThemeField("width", ThemeFieldType.DIMENSION),
                ThemeField("visible", ThemeFieldType.BOOLEAN),
                ThemeField("enabled", ThemeFieldType.BOOLEAN),
                *componentFields.toTypedArray(),
                ThemeField("popup", ThemeFieldType.TABLE),
                ThemeField("popup.background", ThemeFieldType.COLOR),
                ThemeField("popup.corner_radius", ThemeFieldType.DIMENSION),
                ThemeField("popup.column_count", ThemeFieldType.NUMBER),
                ThemeField("preedit", ThemeFieldType.TABLE),
                ThemeField("preedit.show", ThemeFieldType.BOOLEAN, defaultValue = ThemeValue.LuaBoolean(true), previewSupport = PreviewSupport.SIMULATED),
                ThemeField("preedit.background", ThemeFieldType.LUA, previewSupport = PreviewSupport.SIMULATED, writeSupport = WriteSupport.PRESERVE_RAW),
                ThemeField("preedit.text_color", ThemeFieldType.COLOR),
                ThemeField("preedit.text_size", ThemeFieldType.DIMENSION),
                ThemeField("preedit.inline", ThemeFieldType.LUA, previewSupport = PreviewSupport.SIMULATED),
                ThemeField("composition", ThemeFieldType.TABLE),
                ThemeField("composition.show", ThemeFieldType.BOOLEAN, defaultValue = ThemeValue.LuaBoolean(true), previewSupport = PreviewSupport.SIMULATED, fallbackPath = "preedit.show"),
                ThemeField("composition.background", ThemeFieldType.LUA, previewSupport = PreviewSupport.SIMULATED, writeSupport = WriteSupport.PRESERVE_RAW),
                ThemeField("composition.text_color", ThemeFieldType.COLOR),
                ThemeField("composition.text_size", ThemeFieldType.DIMENSION),
                ThemeField("composition.position", ThemeFieldType.TEXT, defaultValue = ThemeValue.LuaString("fixed"), previewSupport = PreviewSupport.SIMULATED),
                ThemeField("composition.movable", ThemeFieldType.TEXT, defaultValue = ThemeValue.LuaString("false"), previewSupport = PreviewSupport.SIMULATED),
                ThemeField("composition.min_length", ThemeFieldType.NUMBER, defaultValue = ThemeValue.LuaNumber(0.0), previewSupport = PreviewSupport.SIMULATED),
                ThemeField("composition.max_length", ThemeFieldType.NUMBER, defaultValue = ThemeValue.LuaNumber(5.0), previewSupport = PreviewSupport.SIMULATED),
                ThemeField("composition.sticky_lines", ThemeFieldType.NUMBER, defaultValue = ThemeValue.LuaNumber(0.0), previewSupport = PreviewSupport.SIMULATED),
                ThemeField("composition.max_entries", ThemeFieldType.NUMBER, defaultValue = ThemeValue.LuaNumber(5.0), previewSupport = PreviewSupport.SIMULATED),
                ThemeField("composition.cloud_max_entries", ThemeFieldType.NUMBER, defaultValue = ThemeValue.LuaNumber(0.0), previewSupport = PreviewSupport.SIMULATED),
                ThemeField("composition.all_phrases", ThemeFieldType.BOOLEAN, defaultValue = ThemeValue.LuaBoolean(false), previewSupport = PreviewSupport.SIMULATED),
                ThemeField("composition.use_cursor", ThemeFieldType.BOOLEAN, defaultValue = ThemeValue.LuaBoolean(true), previewSupport = PreviewSupport.SIMULATED),
                ThemeField("composition.min_width", ThemeFieldType.DIMENSION, defaultValue = ThemeValue.LuaNumber(10.0), previewSupport = PreviewSupport.SIMULATED),
                ThemeField("composition.min_height", ThemeFieldType.DIMENSION, defaultValue = ThemeValue.LuaNumber(10.0), previewSupport = PreviewSupport.SIMULATED),
                ThemeField("composition.max_width", ThemeFieldType.DIMENSION, defaultValue = ThemeValue.LuaNumber(10000.0), previewSupport = PreviewSupport.SIMULATED),
                ThemeField("composition.max_height", ThemeFieldType.DIMENSION, defaultValue = ThemeValue.LuaNumber(1000.0), previewSupport = PreviewSupport.SIMULATED),
                ThemeField("composition.padding", ThemeFieldType.TABLE),
                ThemeField("composition.padding.left", ThemeFieldType.DIMENSION, defaultValue = ThemeValue.LuaNumber(0.0), previewSupport = PreviewSupport.SIMULATED),
                ThemeField("composition.padding.top", ThemeFieldType.DIMENSION, defaultValue = ThemeValue.LuaNumber(0.0), previewSupport = PreviewSupport.SIMULATED),
                ThemeField("composition.padding.right", ThemeFieldType.DIMENSION, defaultValue = ThemeValue.LuaNumber(0.0), previewSupport = PreviewSupport.SIMULATED),
                ThemeField("composition.padding.bottom", ThemeFieldType.DIMENSION, defaultValue = ThemeValue.LuaNumber(0.0), previewSupport = PreviewSupport.SIMULATED),
                ThemeField("composition.line_spacing", ThemeFieldType.DIMENSION, defaultValue = ThemeValue.LuaNumber(1.0), previewSupport = PreviewSupport.SIMULATED),
                ThemeField("composition.line_spacing_multiplier", ThemeFieldType.NUMBER, defaultValue = ThemeValue.LuaNumber(1.0), previewSupport = PreviewSupport.SIMULATED),
                ThemeField("composition.pressed", ThemeFieldType.TABLE),
                ThemeField("composition.pressed.background", ThemeFieldType.COLOR),
                ThemeField("composition.pressed.text_color", ThemeFieldType.COLOR),
                ThemeField("composition.key", ThemeFieldType.TABLE),
                ThemeField("composition.key.background", ThemeFieldType.COLOR, previewSupport = PreviewSupport.SIMULATED),
                ThemeField("composition.key.text_color", ThemeFieldType.COLOR, previewSupport = PreviewSupport.SIMULATED),
                ThemeField("composition.key.text_size", ThemeFieldType.DIMENSION, previewSupport = PreviewSupport.SIMULATED),
                ThemeField("composition.key.pressed", ThemeFieldType.TABLE),
                ThemeField("composition.key.pressed.background", ThemeFieldType.COLOR, previewSupport = PreviewSupport.SIMULATED),
                ThemeField("composition.key.pressed.text_color", ThemeFieldType.COLOR, previewSupport = PreviewSupport.SIMULATED),
                ThemeField("composition.key.hint", ThemeFieldType.TABLE),
                ThemeField("composition.key.hint.text_color", ThemeFieldType.COLOR, previewSupport = PreviewSupport.SIMULATED),
                ThemeField("composition.key.hint.text_size", ThemeFieldType.DIMENSION, previewSupport = PreviewSupport.SIMULATED),
                ThemeField("composition.key.pressed.hint", ThemeFieldType.TABLE),
                ThemeField("composition.key.pressed.hint.text_color", ThemeFieldType.COLOR, previewSupport = PreviewSupport.SIMULATED),
                ThemeField("composition.key.pressed.hint.text_size", ThemeFieldType.DIMENSION, previewSupport = PreviewSupport.SIMULATED),
                ThemeField("preedit.font", ThemeFieldType.LUA, consumption = ConsumptionStatus.UNRELIABLE, editorSupport = EditorSupport.CODE_ONLY, previewSupport = PreviewSupport.DISABLED_WITH_REASON, writeSupport = WriteSupport.PRESERVE_RAW),
                ThemeField("composition.font", ThemeFieldType.LUA, editorSupport = EditorSupport.CODE_ONLY, previewSupport = PreviewSupport.DISABLED_WITH_REASON, writeSupport = WriteSupport.PRESERVE_RAW),
                ThemeField("composition.key.font", ThemeFieldType.LUA, editorSupport = EditorSupport.CODE_ONLY, previewSupport = PreviewSupport.DISABLED_WITH_REASON, writeSupport = WriteSupport.PRESERVE_RAW),
                ThemeField("composition.window", ThemeFieldType.LUA, editorSupport = EditorSupport.CODE_ONLY, previewSupport = PreviewSupport.DISABLED_WITH_REASON, writeSupport = WriteSupport.PRESERVE_RAW),
                ThemeField("keyboard.height", ThemeFieldType.DIMENSION),
                ThemeField("keyboard.background", ThemeFieldType.COLOR),
                ThemeField("keyboard.font", ThemeFieldType.TEXT),
                ThemeField("key", ThemeFieldType.TABLE),
                ThemeField("key.text_size", ThemeFieldType.DIMENSION),
                ThemeField("key.text_color", ThemeFieldType.COLOR),
                ThemeField("key.background", ThemeFieldType.COLOR),
                ThemeField("key.corner_radius", ThemeFieldType.DIMENSION),
                ThemeField("key.stroke_width", ThemeFieldType.DIMENSION),
                ThemeField("key.elevation", ThemeFieldType.DIMENSION),
                ThemeField("key.font", ThemeFieldType.TEXT),
                ThemeField("key.hint", ThemeFieldType.TABLE),
                ThemeField("key.hint.text_color", ThemeFieldType.COLOR),
                ThemeField("key.hint.text_size", ThemeFieldType.DIMENSION),
                ThemeField("key.long_click", ThemeFieldType.TABLE),
                ThemeField("key.long_click.text_color", ThemeFieldType.COLOR),
                ThemeField("key.long_click.text_size", ThemeFieldType.DIMENSION),
                ThemeField("key.pressed", ThemeFieldType.TABLE),
                ThemeField("key.pressed.background", ThemeFieldType.COLOR),
                ThemeField("key.pressed.text_color", ThemeFieldType.COLOR),
                ThemeField("key.swipe_left", ThemeFieldType.LUA, consumption = ConsumptionStatus.PARSED_NOT_TRIGGERED, editorSupport = EditorSupport.CODE_ONLY, previewSupport = PreviewSupport.SIMULATED),
                ThemeField("key.swipe_right", ThemeFieldType.LUA, consumption = ConsumptionStatus.PARSED_NOT_TRIGGERED, editorSupport = EditorSupport.CODE_ONLY, previewSupport = PreviewSupport.SIMULATED),
                ThemeField("key.swipe_up", ThemeFieldType.LUA, consumption = ConsumptionStatus.PARSED_NOT_TRIGGERED, editorSupport = EditorSupport.CODE_ONLY, previewSupport = PreviewSupport.SIMULATED),
                ThemeField("key.swipe_down", ThemeFieldType.LUA, consumption = ConsumptionStatus.PARSED_NOT_TRIGGERED, editorSupport = EditorSupport.CODE_ONLY, previewSupport = PreviewSupport.SIMULATED),
                ThemeField("key.click", ThemeFieldType.LUA),
                ThemeField("key.long_click_time", ThemeFieldType.NUMBER),
                ThemeField("key.repeat_click_time", ThemeFieldType.NUMBER),
                ThemeField("key.background_image", ThemeFieldType.TEXT),
                ThemeField("key.event", ThemeFieldType.TABLE),
                ThemeField("rows", ThemeFieldType.TABLE),
                ThemeField("flex_box", ThemeFieldType.TABLE),
                ThemeField("keys", ThemeFieldType.TABLE),
                ThemeField("key_maps", ThemeFieldType.TABLE),
                ThemeField("preset_keys", ThemeFieldType.TABLE),
                ThemeField("action_labels", ThemeFieldType.TABLE),
                ThemeField("double_click", ThemeFieldType.LUA, consumption = ConsumptionStatus.PARSED_NOT_TRIGGERED, editorSupport = EditorSupport.CODE_ONLY, previewSupport = PreviewSupport.DISABLED_WITH_REASON),
                ThemeField("triple_click", ThemeFieldType.LUA, consumption = ConsumptionStatus.PARSED_NOT_TRIGGERED, editorSupport = EditorSupport.CODE_ONLY, previewSupport = PreviewSupport.DISABLED_WITH_REASON),
                ThemeField("composition.border", ThemeFieldType.NUMBER, consumption = ConsumptionStatus.UNRELIABLE, editorSupport = EditorSupport.CODE_ONLY, previewSupport = PreviewSupport.DISABLED_WITH_REASON),
                ThemeField("composition.spacing", ThemeFieldType.NUMBER, consumption = ConsumptionStatus.UNRELIABLE, editorSupport = EditorSupport.CODE_ONLY, previewSupport = PreviewSupport.DISABLED_WITH_REASON),
                ThemeField("composition.round_corner", ThemeFieldType.NUMBER, consumption = ConsumptionStatus.UNRELIABLE, editorSupport = EditorSupport.CODE_ONLY, previewSupport = PreviewSupport.DISABLED_WITH_REASON),
                ThemeField("composition.elevation", ThemeFieldType.NUMBER, consumption = ConsumptionStatus.UNRELIABLE, editorSupport = EditorSupport.CODE_ONLY, previewSupport = PreviewSupport.DISABLED_WITH_REASON),
            )
        }
    }
}

data class ThemeFieldCoverage(
    val total: Int,
    val visual: Int,
    val codeOnly: Int,
    val exactPreview: Int,
    val simulatedPreview: Int,
    val disabledPreview: Int,
    val structuredWrite: Int,
    val rawWrite: Int,
    val missing: Int,
)

object ThemeDefaults {
    fun document(): ThemeDocument = ThemeDocument(listOf(
        ThemeNode("style", 0, ThemeValue.LuaString("light")),
        ThemeNode("keyboard", 0, ThemeValue.LuaTable(linkedMapOf("height" to ThemeValue.LuaNumber(240.0)))),
        ThemeNode("candidate", 0, ThemeValue.LuaTable(linkedMapOf("height" to ThemeValue.LuaNumber(48.0)))),
    ))
}
