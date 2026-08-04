/*
 * SPDX-FileCopyrightText: 2025 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.editor.project

import java.io.File
import java.io.IOException

/** Supported static export scopes. User Lua and scripts are never executed. */
enum class ThemeExportKind {
    FULL_THEME,
    LUA_ONLY,
    RESOURCES_ONLY,
    CURRENT_KEYBOARD,
    CURRENT_STYLE,
    COMPATIBILITY_REPORT,
}

data class ThemeExportOptions @JvmOverloads constructor(
    val includeImages: Boolean = true,
    val includeFonts: Boolean = true,
    val includeSounds: Boolean = true,
    val includeScripts: Boolean = true,
    val removeUnusedResources: Boolean = false,
    val includeComments: Boolean = true,
    val includeDiagnosticReport: Boolean = true,
)

data class ThemeExportEntry(
    val relativePath: String,
    val source: File,
    val size: Long,
)

data class ThemeExportPlan(
    val kind: ThemeExportKind,
    val entries: List<ThemeExportEntry>,
    val totalBytes: Long,
    val excludedCount: Int,
    val includeComments: Boolean,
    val includeDiagnosticReport: Boolean,
    val warnings: List<String>,
)

/** Builds a bounded, path-safe export manifest without evaluating theme code. */
object ThemeProjectExportPlanner {
    @JvmStatic
    @JvmOverloads
    @Throws(IOException::class)
    fun plan(
        project: ThemeProject,
        kind: ThemeExportKind,
        options: ThemeExportOptions = ThemeExportOptions(),
        currentFile: ThemeProjectFile? = null,
        luaSource: String = "",
    ): ThemeExportPlan {
        val root = project.root.canonicalFile
        require(project.root.absolutePath == root.absolutePath && root.isDirectory) {
            "主题导出根目录无效或包含符号链接"
        }
        val all = safeFiles(root)
        val resources = ThemeResourceIndex.scan(root, luaSource).associateBy { it.relativePath }
        val warnings = ArrayList<String>()
        val includeReport = options.includeDiagnosticReport || kind == ThemeExportKind.COMPATIBILITY_REPORT
        val selected = when (kind) {
            ThemeExportKind.FULL_THEME -> all.filter { includeFullEntry(it, options, resources) }
            ThemeExportKind.LUA_ONLY -> all.filter { entry ->
                val folder = entry.relativePath.substringBefore('/', "")
                entry.source.extension.equals("lua", true) &&
                    (folder != "scripts" || options.includeScripts) &&
                    includeResourceFolder(folder, options)
            }
            ThemeExportKind.RESOURCES_ONLY -> all.filter { entry ->
                val resource = resources[entry.relativePath]
                resource != null && includeResource(resource, options) &&
                    !isRemovableUnused(entry, options, resources)
            }
            ThemeExportKind.CURRENT_KEYBOARD -> listOf(currentEntry(root, currentFile, ThemeProjectFile.Kind.KEYBOARD))
            ThemeExportKind.CURRENT_STYLE -> listOf(currentEntry(root, currentFile, ThemeProjectFile.Kind.STYLE))
            ThemeExportKind.COMPATIBILITY_REPORT -> emptyList()
        }
        if (options.removeUnusedResources) {
            val removed = all.count { isRemovableUnused(it, options, resources) }
            if (removed > 0) warnings += "已按静态引用检查排除 $removed 个未使用资源;动态引用不确定资源仍保留"
        }
        if (!options.includeScripts && all.any { it.relativePath.startsWith("scripts/") }) {
            warnings += "脚本目录未包含;编辑器未执行或验证任何用户脚本"
        }
        return ThemeExportPlan(
            kind = kind,
            entries = selected.sortedBy { it.relativePath },
            totalBytes = selected.sumOf { it.size },
            excludedCount = all.size - selected.size,
            includeComments = options.includeComments,
            includeDiagnosticReport = includeReport,
            warnings = warnings,
        )
    }

    private fun includeFullEntry(
        entry: ThemeExportEntry,
        options: ThemeExportOptions,
        resources: Map<String, ThemeResource>,
    ): Boolean {
        val resource = resources[entry.relativePath]
        if (resource != null && !includeResource(resource, options)) return false
        return !isRemovableUnused(entry, options, resources)
    }

    private fun includeResource(resource: ThemeResource, options: ThemeExportOptions): Boolean = when (resource.kind) {
        ThemeResource.Kind.IMAGE -> options.includeImages
        ThemeResource.Kind.FONT -> options.includeFonts
        ThemeResource.Kind.SOUND -> options.includeSounds
        ThemeResource.Kind.SCRIPT -> options.includeScripts
        ThemeResource.Kind.OTHER -> true
    }

    private fun includeResourceFolder(folder: String, options: ThemeExportOptions): Boolean = when (folder) {
        "images" -> options.includeImages
        "fonts" -> options.includeFonts
        "sounds" -> options.includeSounds
        "scripts" -> options.includeScripts
        else -> true
    }

    private fun isRemovableUnused(
        entry: ThemeExportEntry,
        options: ThemeExportOptions,
        resources: Map<String, ThemeResource>,
    ): Boolean {
        if (!options.removeUnusedResources) return false
        val resource = resources[entry.relativePath] ?: return false
        return !resource.referenced && !resource.referenceUncertain
    }

    private fun currentEntry(
        root: File,
        currentFile: ThemeProjectFile?,
        expectedKind: ThemeProjectFile.Kind,
    ): ThemeExportEntry {
        require(currentFile != null && currentFile.kind == expectedKind) {
            if (expectedKind == ThemeProjectFile.Kind.KEYBOARD) "请先选择要导出的键盘文件" else "请先选择要导出的样式文件"
        }
        val source = currentFile.file.canonicalFile
        val prefix = root.canonicalPath.trimEnd(File.separatorChar) + File.separator
        require(currentFile.file.absolutePath == source.absolutePath && source.isFile && source.canonicalPath.startsWith(prefix)) {
            "当前导出文件无效、位于项目外或包含符号链接"
        }
        val relative = source.relativeTo(root).invariantSeparatorsPath
        require(isSafeRelativePath(relative)) { "当前导出文件路径无效" }
        return ThemeExportEntry(relative, source, source.length())
    }

    private fun safeFiles(root: File): List<ThemeExportEntry> {
        val prefix = root.canonicalPath.trimEnd(File.separatorChar) + File.separator
        val directories = java.util.ArrayDeque<File>()
        val visitedDirectories = HashSet<String>()
        val visitedFiles = HashSet<String>()
        val result = ArrayList<ThemeExportEntry>()
        directories.add(root)
        while (directories.isNotEmpty()) {
            val directory = directories.removeLast().canonicalFile
            val directoryPath = directory.canonicalPath
            require(directoryPath == root.canonicalPath || directoryPath.startsWith(prefix)) {
                "主题目录超出项目根目录"
            }
            require(directory.absolutePath == directoryPath) { "主题项目不支持符号链接目录:${directory.name}" }
            if (!visitedDirectories.add(directoryPath)) continue
            for (child in directory.listFiles().orEmpty()) {
                val canonical = child.canonicalFile
                require(child.absolutePath == canonical.absolutePath) { "主题项目不支持符号链接:${child.name}" }
                require(canonical.canonicalPath.startsWith(prefix)) { "主题文件超出项目根目录:${child.name}" }
                if (canonical.isDirectory) {
                    directories.add(canonical)
                } else if (canonical.isFile && visitedFiles.add(canonical.canonicalPath)) {
                    val relative = canonical.relativeTo(root).invariantSeparatorsPath
                    require(isSafeRelativePath(relative)) { "主题文件路径无效:$relative" }
                    result += ThemeExportEntry(relative, canonical, canonical.length())
                }
            }
        }
        return result
    }

    private fun isSafeRelativePath(path: String): Boolean =
        path.isNotBlank() && !path.startsWith('/') && path.split('/').none { it.isBlank() || it == "." || it == ".." }

    private val RESOURCE_FOLDERS = setOf("images", "fonts", "sounds", "scripts")
}

/** Removes only lexical Lua comments while preserving strings and line boundaries. */
object ThemeLuaCommentFilter {
    @JvmStatic
    fun strip(source: String): String {
        val result = StringBuilder(source.length)
        var index = 0
        while (index < source.length) {
            val quote = source[index]
            if (quote == '\'' || quote == '"') {
                index = copyQuoted(source, index, quote, result)
                continue
            }
            val longEquals = longBracketEquals(source, index)
            if (longEquals >= 0) {
                index = copyLongBracket(source, index, longEquals, result)
                continue
            }
            if (quote == '-' && index + 1 < source.length && source[index + 1] == '-') {
                val commentEquals = longBracketEquals(source, index + 2)
                if (commentEquals >= 0) {
                    val close = findLongBracketEnd(source, index + 2, commentEquals)
                    val end = if (close < 0) source.length else close
                    for (position in index until end) if (source[position] == '\n' || source[position] == '\r') result.append(source[position])
                    index = end
                } else {
                    while (index < source.length && source[index] != '\n' && source[index] != '\r') index++
                }
                continue
            }
            result.append(quote)
            index++
        }
        return result.toString()
    }

    private fun copyQuoted(source: String, start: Int, quote: Char, out: StringBuilder): Int {
        var index = start
        var escaped = false
        while (index < source.length) {
            val char = source[index++]
            out.append(char)
            if (escaped) escaped = false
            else if (char == '\\') escaped = true
            else if (index > start + 1 && char == quote) break
        }
        return index
    }

    private fun copyLongBracket(source: String, start: Int, equals: Int, out: StringBuilder): Int {
        val end = findLongBracketEnd(source, start, equals)
        val safeEnd = if (end < 0) source.length else end
        out.append(source, start, safeEnd)
        return safeEnd
    }

    private fun findLongBracketEnd(source: String, start: Int, equals: Int): Int {
        val closing = "]" + "=".repeat(equals) + "]"
        val contentStart = start + equals + 2
        val close = source.indexOf(closing, contentStart)
        return if (close < 0) -1 else close + closing.length
    }

    private fun longBracketEquals(source: String, start: Int): Int {
        if (start >= source.length || source[start] != '[') return -1
        var index = start + 1
        while (index < source.length && source[index] == '=') index++
        return if (index < source.length && source[index] == '[') index - start - 1 else -1
    }
}
