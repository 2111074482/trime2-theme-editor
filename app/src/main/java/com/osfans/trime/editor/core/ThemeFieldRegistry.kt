/*
 * SPDX-FileCopyrightText: 2025 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.editor.core

enum class ThemeFieldType { COLOR, DIMENSION, NUMBER, BOOLEAN, TEXT, TABLE, LUA }

data class ThemeField(
    val path: String,
    val type: ThemeFieldType,
    val label: String = path,
    val description: String = "",
    val defaultValue: ThemeValue? = null,
)

class ThemeFieldRegistry(fields: Iterable<ThemeField> = defaultFields()) {
    private val fields = fields.associateBy { it.path }
    fun find(path: String): ThemeField? = fields[path]
    fun all(): List<ThemeField> = fields.values.toList()
    fun validate(path: String, value: ThemeValue): String? {
        val field = find(path) ?: return null
        val compatible = when (field.type) {
            ThemeFieldType.COLOR, ThemeFieldType.DIMENSION, ThemeFieldType.NUMBER -> value is ThemeValue.LuaNumber
            ThemeFieldType.BOOLEAN -> value is ThemeValue.LuaBoolean
            ThemeFieldType.TEXT -> value is ThemeValue.LuaString
            ThemeFieldType.TABLE -> value is ThemeValue.LuaTable
            ThemeFieldType.LUA -> true
        }
        return if (compatible) null else "Expected ${field.type.name.lowercase()} for $path"
    }

    companion object {
        fun defaultFields() = listOf(
            ThemeField("style", ThemeFieldType.TEXT, defaultValue = ThemeValue.LuaString("light")),
            ThemeField("background", ThemeFieldType.COLOR),
            ThemeField("text_color", ThemeFieldType.COLOR),
            ThemeField("candidate", ThemeFieldType.TABLE),
            ThemeField("keyboard", ThemeFieldType.TABLE),
            ThemeField("key", ThemeFieldType.TABLE),
            ThemeField("height", ThemeFieldType.DIMENSION),
            ThemeField("text_size", ThemeFieldType.DIMENSION),
            ThemeField("corner_radius", ThemeFieldType.DIMENSION),
            ThemeField("stroke_width", ThemeFieldType.DIMENSION),
            ThemeField("keyboard.height", ThemeFieldType.DIMENSION),
            ThemeField("candidate.height", ThemeFieldType.DIMENSION),
            ThemeField("key.text_size", ThemeFieldType.DIMENSION),
            ThemeField("key.text_color", ThemeFieldType.COLOR),
            ThemeField("key.background", ThemeFieldType.COLOR),
        )
    }
}

object ThemeDefaults {
    fun document(): ThemeDocument = ThemeDocument(listOf(
        ThemeNode("style", 0, ThemeValue.LuaString("light")),
        ThemeNode("keyboard", 0, ThemeValue.LuaTable(linkedMapOf("height" to ThemeValue.LuaNumber(240.0)))),
        ThemeNode("candidate", 0, ThemeValue.LuaTable(linkedMapOf("height" to ThemeValue.LuaNumber(48.0)))),
    ))
}
