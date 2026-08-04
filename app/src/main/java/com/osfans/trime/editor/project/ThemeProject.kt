/*
 * SPDX-FileCopyrightText: 2025 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.editor.project

import com.osfans.trime.editor.core.ParseResult
import com.osfans.trime.editor.core.Severity
import com.osfans.trime.editor.core.ThemeDiagnostic
import com.osfans.trime.editor.core.ThemeDocument
import com.osfans.trime.editor.core.ThemeLuaParser
import java.io.File

/** A discovered theme project. User Lua is inspected as data; callbacks are never executed. */
data class ThemeProject(
    val root: File,
    val mainFile: File,
    val styles: List<ThemeProjectFile>,
    val keyboards: List<ThemeProjectFile>,
    val resources: List<ThemeResource>,
) {
    val id: String get() = root.canonicalPath

    fun style(name: String): ThemeProjectFile? = styles.firstOrNull { it.name == name }
    fun keyboard(name: String): ThemeProjectFile? = keyboards.firstOrNull { it.name == name }

    fun open(name: String, kind: ThemeProjectFile.Kind, parser: ThemeLuaParser = ThemeLuaParser()): ParseResult {
        val file = when (kind) {
            ThemeProjectFile.Kind.MAIN -> mainFile
            ThemeProjectFile.Kind.STYLE -> style(name)?.file
            ThemeProjectFile.Kind.KEYBOARD -> keyboard(name)?.file
        } ?: return ParseResult(
            ThemeDocument(emptyList()),
            listOf(ThemeDiagnostic(0, 0, Severity.ERROR, "未找到主题文件:$name")),
        )
        return parser.parse(file.readText(Charsets.UTF_8))
    }

    companion object {
        fun discover(root: File): ThemeProject {
            val canonicalRoot = root.canonicalFile
            require(root.absolutePath == canonicalRoot.absolutePath) { "不支持以符号链接作为主题根目录" }
            require(canonicalRoot.isDirectory) { "主题根路径必须是目录" }
            val prefix = canonicalRoot.canonicalPath.trimEnd(File.separatorChar) + File.separator
            val mainEntry = File(canonicalRoot, "main.lua")
            val main = mainEntry.canonicalFile
            require(mainEntry.absolutePath == main.absolutePath && main.isFile && main.canonicalPath.startsWith(prefix)) { "主题根目录必须包含内部且非符号链接的 main.lua" }
            return ThemeProject(
                root = canonicalRoot,
                mainFile = main,
                styles = discoverFiles(canonicalRoot, File(canonicalRoot, "styles"), ThemeProjectFile.Kind.STYLE),
                keyboards = discoverFiles(canonicalRoot, File(canonicalRoot, "keyboards"), ThemeProjectFile.Kind.KEYBOARD),
                resources = ThemeResourceIndex.scan(canonicalRoot),
            )
        }

        private fun discoverFiles(root: File, directory: File, kind: ThemeProjectFile.Kind): List<ThemeProjectFile> {
            if (!directory.isDirectory) return emptyList()
            val prefix = root.canonicalPath.trimEnd(File.separatorChar) + File.separator
            if (directory.absolutePath != directory.canonicalPath || !directory.canonicalPath.startsWith(prefix)) return emptyList()
            val files = when (kind) {
                ThemeProjectFile.Kind.STYLE -> directory.listFiles()?.asSequence()
                    ?.filter { it.isDirectory }
                    ?.map { File(it, "main.lua") }
                    ?.filter { it.isFile }
                    ?: emptySequence()
                ThemeProjectFile.Kind.KEYBOARD -> directory.listFiles()?.asSequence()
                    ?.filter { it.isFile && it.extension.equals("lua", true) }
                    ?: emptySequence()
                ThemeProjectFile.Kind.MAIN -> emptySequence()
            }
            return files.filter { it.absolutePath == it.canonicalPath }
                .map { it.canonicalFile }
                .filter { it.canonicalPath.startsWith(prefix) }
                .distinctBy { it.canonicalPath }
                .map { file ->
                    val name = if (kind == ThemeProjectFile.Kind.STYLE) file.parentFile.name else file.nameWithoutExtension
                    ThemeProjectFile(name, file, kind)
                }.sortedBy { it.name }.toList()
        }
    }
}

data class ThemeProjectFile(
    val name: String,
    val file: File,
    val kind: Kind,
) {
    enum class Kind { MAIN, STYLE, KEYBOARD }
}

data class ThemeProjectSelection(
    val style: String,
    val keyboard: String,
    val styleSource: ThemeProjectFile?,
    val keyboardSource: ThemeProjectFile?,
    val diagnostics: List<ThemeDiagnostic>,
)

object ThemeProjectSelector {
    fun select(project: ThemeProject, main: ParseResult): ThemeProjectSelection {
        val style = main.document.string("style") ?: "light"
        val keyboard = main.document.string("keyboard") ?: "qwerty26"
        val diagnostics = ArrayList<ThemeDiagnostic>()
        val styleFile = project.style(style)
        val keyboardFile = project.keyboard(keyboard)
        if (styleFile == null) diagnostics += ThemeDiagnostic(0, 0, Severity.WARNING, "未找到样式文件:$style", "style")
        if (keyboardFile == null) diagnostics += ThemeDiagnostic(0, 0, Severity.WARNING, "未找到键盘文件:$keyboard", "keyboard")
        if (main.document.hasRawExpression("get_keyboard")) {
            diagnostics += ThemeDiagnostic(0, 0, Severity.INFO, "动态取键盘(get_keyboard)已保留且不会执行", "keyboard")
        }
        return ThemeProjectSelection(style, keyboard, styleFile, keyboardFile, diagnostics)
    }
}

private fun ThemeDocument.string(path: String): String? =
    (get(path) as? com.osfans.trime.editor.core.ThemeValue.LuaString)?.value

private fun ThemeDocument.hasRawExpression(name: String): Boolean =
    nodes.any { it.value is com.osfans.trime.editor.core.ThemeValue.RawLuaNode && it.value.source.contains(name) }
