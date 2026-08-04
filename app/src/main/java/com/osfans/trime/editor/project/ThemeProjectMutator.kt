/*
 * SPDX-FileCopyrightText: 2025 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.osfans.trime.editor.project

import com.osfans.trime.editor.core.ThemeLuaParser
import com.osfans.trime.editor.core.ThemeLuaWriter
import com.osfans.trime.editor.core.ThemeValue
import java.io.File

/** Safe local project asset mutations. Callers mirror changes to SAF when applicable. */
object ThemeProjectMutator {
    private val SAFE_ID = Regex("^[A-Za-z_][A-Za-z0-9_]{0,63}$")

    @JvmStatic fun createKeyboard(project: ThemeProject, id: String, source: String): ThemeProjectFile {
        require(SAFE_ID.matches(id)) { "键盘标识必须是 Lua 安全标识符" }
        val file = File(project.root, "keyboards/$id.lua")
        require(!file.exists()) { "键盘已存在:$id" }
        val parsed = ThemeLuaParser().parse(source)
        require(parsed.diagnostics.none { it.severity.name == "ERROR" }) { "键盘模板包含 Lua 错误" }
        file.parentFile.mkdirs(); file.writeText(source, Charsets.UTF_8)
        return ThemeProjectFile(id, file, ThemeProjectFile.Kind.KEYBOARD)
    }

    @JvmStatic fun copyKeyboard(project: ThemeProject, source: ThemeProjectFile, id: String): ThemeProjectFile = createKeyboard(project, id, source.file.readText(Charsets.UTF_8))

    data class KeyboardMetadata(
        val name: String,
        val author: String,
        val style: String?,
        val lock: Boolean,
        val asciiMode: Boolean,
        val keyWidth: Double?,
        val keyHeight: Double?,
    )

    @JvmStatic fun readKeyboardMetadata(source: ThemeProjectFile): KeyboardMetadata {
        require(source.kind == ThemeProjectFile.Kind.KEYBOARD) { "所选文件不是键盘资源" }
        val parsed = ThemeLuaParser().parse(source.file.readText(Charsets.UTF_8))
        require(parsed.diagnostics.none { it.severity.name == "ERROR" }) { "键盘源代码包含 Lua 错误" }
        fun string(path: String) = (parsed.document.get(path) as? ThemeValue.LuaString)?.value
        fun boolean(path: String, fallback: Boolean) = (parsed.document.get(path) as? ThemeValue.LuaBoolean)?.value ?: fallback
        fun number(path: String) = (parsed.document.get(path) as? ThemeValue.LuaNumber)?.value
        return KeyboardMetadata(string("name") ?: source.name, string("author") ?: "", string("style"), boolean("lock", false), boolean("ascii_mode", false), number("key_width"), number("key_height"))
    }

    @JvmStatic fun updateKeyboardMetadata(source: ThemeProjectFile, metadata: KeyboardMetadata) {
        require(source.kind == ThemeProjectFile.Kind.KEYBOARD) { "所选文件不是键盘资源" }
        require(metadata.name.isNotBlank()) { "键盘名称不能为空" }
        require(metadata.author.isNotBlank()) { "键盘作者不能为空" }
        require(metadata.keyWidth == null || metadata.keyWidth > 0) { "按键宽度(key_width)必须为正数或未设置" }
        require(metadata.keyHeight == null || metadata.keyHeight > 0) { "按键高度(key_height)必须为正数或未设置" }
        val parsed = ThemeLuaParser().parse(source.file.readText(Charsets.UTF_8))
        require(parsed.diagnostics.none { it.severity.name == "ERROR" }) { "键盘源代码包含 Lua 错误" }
        val paths = listOf("name", "author", "style", "lock", "ascii_mode", "key_width", "key_height")
        paths.forEach { path -> require(parsed.document.get(path) !is ThemeValue.RawLuaNode) { "$path 使用动态 Lua,必须在代码页编辑" } }
        var document = parsed.document.set("name", ThemeValue.LuaString(metadata.name)).set("author", ThemeValue.LuaString(metadata.author))
            .set("lock", ThemeValue.LuaBoolean(metadata.lock)).set("ascii_mode", ThemeValue.LuaBoolean(metadata.asciiMode))
        document = if (metadata.style.isNullOrBlank()) document.remove("style") else document.set("style", ThemeValue.LuaString(metadata.style))
        document = if (metadata.keyWidth == null) document.remove("key_width") else document.set("key_width", ThemeValue.LuaNumber(metadata.keyWidth))
        document = if (metadata.keyHeight == null) document.remove("key_height") else document.set("key_height", ThemeValue.LuaNumber(metadata.keyHeight))
        val written = ThemeLuaWriter.write(document)
        require(ThemeLuaParser().parse(written).diagnostics.none { it.severity.name == "ERROR" }) { "更新后的键盘未通过静态解析校验" }
        writeTextTransaction(source.file, written)
    }

    @JvmStatic fun renameKeyboard(project: ThemeProject, source: ThemeProjectFile, id: String): ThemeProjectFile {
        require(SAFE_ID.matches(id)) { "键盘标识必须是 Lua 安全标识符" }
        val target = File(project.root, "keyboards/$id.lua")
        require(!target.exists()) { "键盘已存在:$id" }
        require(source.file.renameTo(target)) { "无法重命名键盘" }
        return ThemeProjectFile(id, target, ThemeProjectFile.Kind.KEYBOARD)
    }

    @JvmStatic fun validateKeyboardDeletion(project: ThemeProject, source: ThemeProjectFile) {
        val main = ThemeLuaParser().parse(project.mainFile.readText(Charsets.UTF_8)).document
        val selected = (main.get("keyboard") as? ThemeValue.LuaString)?.value
        require(selected != source.name) { "不能删除默认键盘" }
        require(project.keyboards.size > 1) { "主题至少需要一个键盘" }
        val references = project.root.walkTopDown().filter { it.isFile && it.extension.equals("lua", true) && it != source.file }
            .any { file -> runCatching { file.readText(Charsets.UTF_8) }.getOrDefault("").contains(Regex("['\"]${Regex.escape(source.name)}['\"]")) }
        require(!references) { "键盘标识仍被其他 Lua 文件引用" }
    }

    @JvmStatic fun deleteKeyboard(project: ThemeProject, source: ThemeProjectFile) {
        validateKeyboardDeletion(project, source)
        require(source.file.delete()) { "无法删除键盘" }
    }

    @JvmStatic fun setDefaultKeyboard(project: ThemeProject, id: String) {
        require(project.keyboard(id) != null) { "未找到键盘:$id" }
        val parsed = ThemeLuaParser().parse(project.mainFile.readText(Charsets.UTF_8))
        val updated = parsed.document.set("keyboard", ThemeValue.LuaString(id))
        writeTextTransaction(project.mainFile, ThemeLuaWriter.write(updated))
    }
    @JvmStatic fun copyStyle(project: ThemeProject, source: ThemeProjectFile, id: String): ThemeProjectFile {
        require(SAFE_ID.matches(id)) { "样式标识必须是 Lua 安全标识符" }
        val target = File(project.root, "styles/$id")
        require(!target.exists()) { "样式已存在:$id" }
        copyDirectory(source.file.parentFile, target)
        return ThemeProjectFile(id, File(target, "main.lua"), ThemeProjectFile.Kind.STYLE)
    }

    @JvmStatic fun renameStyle(project: ThemeProject, source: ThemeProjectFile, id: String): ThemeProjectFile {
        require(SAFE_ID.matches(id)) { "样式标识必须是 Lua 安全标识符" }
        val target = File(project.root, "styles/$id")
        require(!target.exists()) { "样式已存在:$id" }
        require(source.file.parentFile.renameTo(target)) { "无法重命名样式" }
        return ThemeProjectFile(id, File(target, "main.lua"), ThemeProjectFile.Kind.STYLE)
    }

    @JvmStatic fun validateStyleDeletion(project: ThemeProject, source: ThemeProjectFile) {
        val main = ThemeLuaParser().parse(project.mainFile.readText(Charsets.UTF_8)).document
        val selected = (main.get("style") as? ThemeValue.LuaString)?.value
        require(selected != source.name) { "不能删除默认样式" }
        require(project.styles.size > 1) { "主题至少需要一个样式" }
        val references = project.root.walkTopDown().filter { it.isFile && it.extension.equals("lua", true) && !it.startsWith(source.file.parentFile) }
            .any { file -> runCatching { file.readText(Charsets.UTF_8) }.getOrDefault("").contains(Regex("['\"]${Regex.escape(source.name)}['\"]")) }
        require(!references) { "样式标识仍被其他 Lua 文件引用" }
    }

    @JvmStatic fun deleteStyle(project: ThemeProject, source: ThemeProjectFile) {
        validateStyleDeletion(project, source)
        require(source.file.parentFile.deleteRecursively()) { "无法删除样式" }
    }

    @JvmStatic fun setDefaultStyle(project: ThemeProject, id: String) {
        require(project.style(id) != null) { "未找到样式:$id" }
        val parsed = ThemeLuaParser().parse(project.mainFile.readText(Charsets.UTF_8))
        writeTextTransaction(project.mainFile, ThemeLuaWriter.write(parsed.document.set("style", ThemeValue.LuaString(id))))
    }

    @JvmStatic fun updateMetadata(project: ThemeProject, name: String, author: String, style: String, keyboard: String) {
        require(name.isNotBlank()) { "主题名称不能为空" }
        require(author.isNotBlank()) { "作者不能为空" }
        require(project.style(style) != null) { "未找到样式:$style" }
        require(project.keyboard(keyboard) != null) { "未找到键盘:$keyboard" }
        val parsed = ThemeLuaParser().parse(project.mainFile.readText(Charsets.UTF_8))
        var document = parsed.document.set("name", ThemeValue.LuaString(name)).set("author", ThemeValue.LuaString(author))
        document = document.set("style", ThemeValue.LuaString(style)).set("keyboard", ThemeValue.LuaString(keyboard))
        writeTextTransaction(project.mainFile, ThemeLuaWriter.write(document))
    }

    private fun copyDirectory(source: File, target: File) {
        require(target.mkdirs()) { "无法创建样式目录" }
        source.listFiles()?.forEach { child -> val out = File(target, child.name); if (child.isDirectory) copyDirectory(child, out) else child.copyTo(out) }
    }

}
