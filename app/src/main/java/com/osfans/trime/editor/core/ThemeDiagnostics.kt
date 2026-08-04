/*
 * SPDX-FileCopyrightText: 2025 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.editor.core

import com.osfans.trime.editor.project.ThemeResource

enum class Severity { INFO, WARNING, ERROR }

data class ThemeDiagnostic(
    val line: Int,
    val column: Int,
    val severity: Severity,
    val message: String,
    val path: String? = null,
    val code: String = stableDiagnosticCode(message, path),
)

private fun stableDiagnosticCode(message: String, path: String?): String {
    val category = when {
        message.contains("未闭合") || message.contains("不匹配") -> "lua.syntax"
        message.contains("不支持") || message.contains("原始 Lua") || message.contains("动态") -> "lua.dynamic"
        message.contains("资源") -> "resource.integrity"
        message.contains("布局") || path?.startsWith("rows") == true || path?.startsWith("flex_box") == true || path?.startsWith("keys") == true || path?.startsWith("key_maps") == true -> "layout.structure"
        message.contains("样式") -> "style.structure"
        message.contains("键盘") -> "keyboard.structure"
        else -> "theme.validation"
    }
    val suffix = path?.substringBefore(".#")?.replace(Regex("[^A-Za-z0-9_.-]"), "_")?.takeIf { it.isNotBlank() }
    return if (suffix == null) category else "$category.$suffix"
}

object ThemeDiagnostics {
    fun validate(document: ThemeDocument, registry: ThemeFieldRegistry = ThemeFieldRegistry()): List<ThemeDiagnostic> = buildList {
        document.nodes.forEach { node ->
            validateValue(node.source, node.value, node.line, registry, this)
        }
        addAll(layout(document))
    }

    fun resources(resources: Iterable<ThemeResource>): List<ThemeDiagnostic> = buildList {
        resources.filter { it.referenced && it.size == 0L }.forEach {
            add(ThemeDiagnostic(0, 0, Severity.ERROR, "引用的资源为空:${it.relativePath}", it.relativePath))
        }
        resources.filter { it.referenceUncertain }.forEach {
            add(ThemeDiagnostic(0, 0, Severity.WARNING, "动态 Lua 可能引用此资源,已禁止安全删除", it.relativePath))
        }
    }

    fun layout(document: ThemeDocument): List<ThemeDiagnostic> = buildList {
        when {
            document.get("rows") is ThemeValue.LuaTable -> validateRows(document.get("rows") as ThemeValue.LuaTable, this)
            document.get("flex_box") is ThemeValue.LuaTable -> validateFlex(document.get("flex_box") as ThemeValue.LuaTable, "flex_box", this)
            document.get("keys") is ThemeValue.LuaTable -> validateAbsolute(document.get("keys") as ThemeValue.LuaTable, this)
            document.get("key_maps") is ThemeValue.LuaTable -> validateKeyMaps(document.get("key_maps") as ThemeValue.LuaTable, this)
        }
    }

    private fun validateRows(rows: ThemeValue.LuaTable, out: MutableList<ThemeDiagnostic>) {
        arrayEntries(rows).forEachIndexed { rowIndex, row ->
            val table = row as? ThemeValue.LuaTable ?: return@forEachIndexed
            val keys = table.fields["keys"] as? ThemeValue.LuaTable ?: run { out += ThemeDiagnostic(0, 0, Severity.WARNING, "行缺少字面量按键表(keys)", "rows.#${rowIndex + 1}.keys"); return@forEachIndexed }
            val defaultWidth = number(table.fields["width"])
            var total = 0.0
            val values = arrayEntries(keys)
            values.forEach { value -> total += number((value as? ThemeValue.LuaTable)?.fields?.get("width")) ?: defaultWidth ?: if (values.isEmpty()) 0.0 else 100.0 / values.size }
            if (total > 100.01) out += ThemeDiagnostic(0, 0, Severity.WARNING, "行内按键宽度总和为 ${format(total)}%,超过 100%", "rows.#${rowIndex + 1}")
        }
    }

    private fun validateFlex(table: ThemeValue.LuaTable, path: String, out: MutableList<ThemeDiagnostic>) {
        val direction = (table.fields["direction"] as? ThemeValue.LuaString)?.value ?: "row"
        val fixed = number(table.fields[if (direction == "column") "height" else "width"])
        val grow = number(table.fields["grow"])
        if (fixed != null && fixed > 0 && grow != null && grow > 0) out += ThemeDiagnostic(0, 0, Severity.WARNING, "主轴固定尺寸会覆盖增长值(grow);预览按 grow=0 处理", path)
        arrayEntries(table).forEachIndexed { index, child -> (child as? ThemeValue.LuaTable)?.let { validateFlex(it, "$path.#${index + 1}", out) } }
    }

    private data class Box(val path: String, val x: Double, val y: Double, val width: Double, val height: Double)
    private fun validateAbsolute(keys: ThemeValue.LuaTable, out: MutableList<ThemeDiagnostic>) {
        val boxes = arrayEntries(keys).mapIndexedNotNull { index, value ->
            val fields = (value as? ThemeValue.LuaTable)?.fields ?: return@mapIndexedNotNull null
            val box = Box("keys.#${index + 1}", number(fields["x"]) ?: 0.0, number(fields["y"]) ?: 0.0, number(fields["width"]) ?: 0.0, number(fields["height"]) ?: 0.0)
            if (box.width <= 0 || box.height <= 0) out += ThemeDiagnostic(0, 0, Severity.ERROR, "绝对定位按键宽高必须为正数", box.path)
            if (box.x < 0 || box.y < 0 || box.x + box.width > 100.01 || box.y + box.height > 100.01) out += ThemeDiagnostic(0, 0, Severity.WARNING, "绝对定位按键超出 0..100 布局边界", box.path)
            box
        }
        if (boxes.size <= 200) for (i in boxes.indices) for (j in i + 1 until boxes.size) if (overlaps(boxes[i], boxes[j])) out += ThemeDiagnostic(0, 0, Severity.WARNING, "绝对定位按键重叠:${boxes[i].path} 与 ${boxes[j].path}", boxes[j].path)
        else out += ThemeDiagnostic(0, 0, Severity.INFO, "绝对定位按键超过 200 个,已跳过重叠诊断", "keys")
    }

    private fun validateKeyMaps(pages: ThemeValue.LuaTable, out: MutableList<ThemeDiagnostic>) {
        val names = HashSet<String>()
        arrayEntries(pages).forEachIndexed { index, value ->
            val page = value as? ThemeValue.LuaTable ?: return@forEachIndexed
            val path = "key_maps.#${index + 1}"
            val name = (page.fields["name"] as? ThemeValue.LuaString)?.value.orEmpty()
            if (name.isBlank()) out += ThemeDiagnostic(0, 0, Severity.WARNING, "符号页没有名称(name)", "$path.name") else if (!names.add(name)) out += ThemeDiagnostic(0, 0, Severity.WARNING, "符号页名称重复:$name", "$path.name")
            val keys = page.fields["keys"] as? ThemeValue.LuaTable
            if (keys == null || arrayEntries(keys).isEmpty()) out += ThemeDiagnostic(0, 0, Severity.WARNING, "符号页没有按键(keys)", "$path.keys")
        }
    }

    private fun arrayEntries(table: ThemeValue.LuaTable): List<ThemeValue> = table.fields.entries.filter { it.key.startsWith("#") }.sortedBy { it.key.drop(1).toIntOrNull() ?: Int.MAX_VALUE }.map { it.value }
    private fun number(value: ThemeValue?): Double? = (value as? ThemeValue.LuaNumber)?.value
    private fun overlaps(a: Box, b: Box): Boolean = a.x < b.x + b.width && a.x + a.width > b.x && a.y < b.y + b.height && a.y + a.height > b.y
    private fun format(value: Double): String = if (value % 1.0 == 0.0) value.toLong().toString() else "%.2f".format(java.util.Locale.ROOT, value)

    fun coverage(registry: ThemeFieldRegistry = ThemeFieldRegistry()): List<ThemeDiagnostic> = buildList {
        registry.all().forEach { field ->
            if (field.consumption != ConsumptionStatus.CONSUMED) {
                val severity = if (field.consumption == ConsumptionStatus.UNRELIABLE) Severity.WARNING else Severity.INFO
                add(ThemeDiagnostic(0, 0, severity, "${field.path}:${consumptionText(field.consumption)}", field.path))
            }
        }
    }

    private fun consumptionText(status: ConsumptionStatus): String = when (status) {
        ConsumptionStatus.CONSUMED -> "已实现"
        ConsumptionStatus.PARSED_NOT_TRIGGERED -> "已解析但不会自动触发"
        ConsumptionStatus.UNRELIABLE -> "兼容性不可靠"
        ConsumptionStatus.NOT_PARSED -> "未结构化解析"
        ConsumptionStatus.RAW_ONLY -> "仅保留原始源码"
    }

    private fun validateValue(path: String, value: ThemeValue, line: Int, registry: ThemeFieldRegistry, out: MutableList<ThemeDiagnostic>) {
        registry.validate(path, value)?.let { out.add(ThemeDiagnostic(line, 1, Severity.ERROR, it, path)) }
        registry.find(path)?.let { field ->
            if (field.consumption != ConsumptionStatus.CONSUMED) {
                val severity = if (field.consumption == ConsumptionStatus.UNRELIABLE) Severity.WARNING else Severity.INFO
                out.add(ThemeDiagnostic(line, 1, severity, "${field.path}:${consumptionText(field.consumption)}", path))
            }
        }
        when (value) {
            is ThemeValue.LuaTable -> value.fields.forEach { (key, child) -> validateValue("$path.$key", child, line, registry, out) }
            is ThemeValue.RawLuaNode -> out.add(ThemeDiagnostic(line, 1, Severity.INFO, "原始 Lua 已保留且不会执行", path))
            else -> Unit
        }
    }
}
