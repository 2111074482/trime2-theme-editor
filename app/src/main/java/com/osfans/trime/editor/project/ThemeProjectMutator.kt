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
        require(SAFE_ID.matches(id)) { "Keyboard ID must be a Lua-safe identifier" }
        val file = File(project.root, "keyboards/$id.lua")
        require(!file.exists()) { "Keyboard already exists: $id" }
        val parsed = ThemeLuaParser().parse(source)
        require(parsed.diagnostics.none { it.severity.name == "ERROR" }) { "Keyboard template contains Lua errors" }
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
        require(source.kind == ThemeProjectFile.Kind.KEYBOARD) { "Not a keyboard asset" }
        val parsed = ThemeLuaParser().parse(source.file.readText(Charsets.UTF_8))
        require(parsed.diagnostics.none { it.severity.name == "ERROR" }) { "Keyboard source contains Lua errors" }
        fun string(path: String) = (parsed.document.get(path) as? ThemeValue.LuaString)?.value
        fun boolean(path: String, fallback: Boolean) = (parsed.document.get(path) as? ThemeValue.LuaBoolean)?.value ?: fallback
        fun number(path: String) = (parsed.document.get(path) as? ThemeValue.LuaNumber)?.value
        return KeyboardMetadata(string("name") ?: source.name, string("author") ?: "", string("style"), boolean("lock", false), boolean("ascii_mode", false), number("key_width"), number("key_height"))
    }

    @JvmStatic fun updateKeyboardMetadata(source: ThemeProjectFile, metadata: KeyboardMetadata) {
        require(source.kind == ThemeProjectFile.Kind.KEYBOARD) { "Not a keyboard asset" }
        require(metadata.name.isNotBlank()) { "Keyboard name is required" }
        require(metadata.author.isNotBlank()) { "Keyboard author is required" }
        require(metadata.keyWidth == null || metadata.keyWidth > 0) { "key_width must be positive or unset" }
        require(metadata.keyHeight == null || metadata.keyHeight > 0) { "key_height must be positive or unset" }
        val parsed = ThemeLuaParser().parse(source.file.readText(Charsets.UTF_8))
        require(parsed.diagnostics.none { it.severity.name == "ERROR" }) { "Keyboard source contains Lua errors" }
        val paths = listOf("name", "author", "style", "lock", "ascii_mode", "key_width", "key_height")
        paths.forEach { path -> require(parsed.document.get(path) !is ThemeValue.RawLuaNode) { "$path uses dynamic Lua and must be edited in the code page" } }
        var document = parsed.document.set("name", ThemeValue.LuaString(metadata.name)).set("author", ThemeValue.LuaString(metadata.author))
            .set("lock", ThemeValue.LuaBoolean(metadata.lock)).set("ascii_mode", ThemeValue.LuaBoolean(metadata.asciiMode))
        document = if (metadata.style.isNullOrBlank()) document.remove("style") else document.set("style", ThemeValue.LuaString(metadata.style))
        document = if (metadata.keyWidth == null) document.remove("key_width") else document.set("key_width", ThemeValue.LuaNumber(metadata.keyWidth))
        document = if (metadata.keyHeight == null) document.remove("key_height") else document.set("key_height", ThemeValue.LuaNumber(metadata.keyHeight))
        val written = ThemeLuaWriter.write(document)
        require(ThemeLuaParser().parse(written).diagnostics.none { it.severity.name == "ERROR" }) { "Updated keyboard failed verification parse" }
        writeTextTransaction(source.file, written)
    }

    @JvmStatic fun renameKeyboard(project: ThemeProject, source: ThemeProjectFile, id: String): ThemeProjectFile {
        require(SAFE_ID.matches(id)) { "Keyboard ID must be a Lua-safe identifier" }
        val target = File(project.root, "keyboards/$id.lua")
        require(!target.exists()) { "Keyboard already exists: $id" }
        require(source.file.renameTo(target)) { "Cannot rename keyboard" }
        return ThemeProjectFile(id, target, ThemeProjectFile.Kind.KEYBOARD)
    }

    @JvmStatic fun validateKeyboardDeletion(project: ThemeProject, source: ThemeProjectFile) {
        val main = ThemeLuaParser().parse(project.mainFile.readText(Charsets.UTF_8)).document
        val selected = (main.get("keyboard") as? ThemeValue.LuaString)?.value
        require(selected != source.name) { "Cannot delete the default keyboard" }
        require(project.keyboards.size > 1) { "At least one keyboard is required" }
        val references = project.root.walkTopDown().filter { it.isFile && it.extension.equals("lua", true) && it != source.file }
            .any { file -> runCatching { file.readText(Charsets.UTF_8) }.getOrDefault("").contains(Regex("['\"]${Regex.escape(source.name)}['\"]")) }
        require(!references) { "Keyboard ID is referenced by another Lua file" }
    }

    @JvmStatic fun deleteKeyboard(project: ThemeProject, source: ThemeProjectFile) {
        validateKeyboardDeletion(project, source)
        require(source.file.delete()) { "Cannot delete keyboard" }
    }

    @JvmStatic fun setDefaultKeyboard(project: ThemeProject, id: String) {
        require(project.keyboard(id) != null) { "Keyboard not found: $id" }
        val parsed = ThemeLuaParser().parse(project.mainFile.readText(Charsets.UTF_8))
        val updated = parsed.document.set("keyboard", ThemeValue.LuaString(id))
        writeTextTransaction(project.mainFile, ThemeLuaWriter.write(updated))
    }
    @JvmStatic fun copyStyle(project: ThemeProject, source: ThemeProjectFile, id: String): ThemeProjectFile {
        require(SAFE_ID.matches(id)) { "Style ID must be a Lua-safe identifier" }
        val target = File(project.root, "styles/$id")
        require(!target.exists()) { "Style already exists: $id" }
        copyDirectory(source.file.parentFile, target)
        return ThemeProjectFile(id, File(target, "main.lua"), ThemeProjectFile.Kind.STYLE)
    }

    @JvmStatic fun renameStyle(project: ThemeProject, source: ThemeProjectFile, id: String): ThemeProjectFile {
        require(SAFE_ID.matches(id)) { "Style ID must be a Lua-safe identifier" }
        val target = File(project.root, "styles/$id")
        require(!target.exists()) { "Style already exists: $id" }
        require(source.file.parentFile.renameTo(target)) { "Cannot rename style" }
        return ThemeProjectFile(id, File(target, "main.lua"), ThemeProjectFile.Kind.STYLE)
    }

    @JvmStatic fun validateStyleDeletion(project: ThemeProject, source: ThemeProjectFile) {
        val main = ThemeLuaParser().parse(project.mainFile.readText(Charsets.UTF_8)).document
        val selected = (main.get("style") as? ThemeValue.LuaString)?.value
        require(selected != source.name) { "Cannot delete the default style" }
        require(project.styles.size > 1) { "At least one style is required" }
        val references = project.root.walkTopDown().filter { it.isFile && it.extension.equals("lua", true) && !it.startsWith(source.file.parentFile) }
            .any { file -> runCatching { file.readText(Charsets.UTF_8) }.getOrDefault("").contains(Regex("['\"]${Regex.escape(source.name)}['\"]")) }
        require(!references) { "Style ID is referenced by another Lua file" }
    }

    @JvmStatic fun deleteStyle(project: ThemeProject, source: ThemeProjectFile) {
        validateStyleDeletion(project, source)
        require(source.file.parentFile.deleteRecursively()) { "Cannot delete style" }
    }

    @JvmStatic fun setDefaultStyle(project: ThemeProject, id: String) {
        require(project.style(id) != null) { "Style not found: $id" }
        val parsed = ThemeLuaParser().parse(project.mainFile.readText(Charsets.UTF_8))
        writeTextTransaction(project.mainFile, ThemeLuaWriter.write(parsed.document.set("style", ThemeValue.LuaString(id))))
    }

    @JvmStatic fun updateMetadata(project: ThemeProject, name: String, author: String, style: String, keyboard: String) {
        require(name.isNotBlank()) { "Theme name is required" }
        require(author.isNotBlank()) { "Author is required" }
        require(project.style(style) != null) { "Style not found: $style" }
        require(project.keyboard(keyboard) != null) { "Keyboard not found: $keyboard" }
        val parsed = ThemeLuaParser().parse(project.mainFile.readText(Charsets.UTF_8))
        var document = parsed.document.set("name", ThemeValue.LuaString(name)).set("author", ThemeValue.LuaString(author))
        document = document.set("style", ThemeValue.LuaString(style)).set("keyboard", ThemeValue.LuaString(keyboard))
        writeTextTransaction(project.mainFile, ThemeLuaWriter.write(document))
    }

    private fun copyDirectory(source: File, target: File) {
        require(target.mkdirs()) { "Cannot create style directory" }
        source.listFiles()?.forEach { child -> val out = File(target, child.name); if (child.isDirectory) copyDirectory(child, out) else child.copyTo(out) }
    }

}
