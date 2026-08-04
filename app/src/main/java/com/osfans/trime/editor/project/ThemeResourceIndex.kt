/*
 * SPDX-FileCopyrightText: 2025 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.editor.project

import java.io.File

/** Resource inventory built only from static Lua string literals; Lua code is never evaluated. */
data class ThemeResource(
    val relativePath: String,
    val kind: Kind,
    val size: Long,
    val referenced: Boolean,
    val referenceUncertain: Boolean = false,
) {
    enum class Kind { IMAGE, FONT, SOUND, SCRIPT, OTHER }
}

data class ThemeResourceStats(
    val total: Int,
    val referenced: Int,
    val dynamicUncertain: Int,
    val unused: Int,
)

object ThemeResourceIndex {
    private val resourceFolders = setOf("images", "fonts", "sounds", "scripts")

    enum class Sort { PATH, SIZE }

    fun scan(root: File, source: String = ""): List<ThemeResource> {
        if (!root.isDirectory) return emptyList()
        val files = safeResourceFiles(root)
        val literals = luaStringLiterals(source).map { normalize(it) }.toSet()
        val names = files.groupingBy { it.name.lowercase() }.eachCount()
        val hasDynamicResourceExpression = DYNAMIC_RESOURCE.containsMatchIn(source)
        return files.map { file ->
            val relative = file.relativeTo(root).invariantSeparatorsPath
            val normalized = normalize(relative)
            val name = file.name.lowercase()
            val referenced = normalized in literals || (names[name] == 1 && name in literals)
            ThemeResource(
                relativePath = relative,
                kind = kind(relative),
                size = file.length(),
                referenced = referenced,
                referenceUncertain = !referenced && hasDynamicResourceExpression,
            )
        }.sortedBy { it.relativePath.lowercase() }
    }

    fun filterByKind(resources: Iterable<ThemeResource>, kind: ThemeResource.Kind?): List<ThemeResource> =
        resources.filter { kind == null || it.kind == kind }

    fun sortBy(resources: Iterable<ThemeResource>, sort: Sort, ascending: Boolean = true): List<ThemeResource> {
        val comparator = when (sort) {
            Sort.PATH -> compareBy<ThemeResource> { it.relativePath.lowercase() }
            Sort.SIZE -> compareBy<ThemeResource> { it.size }.thenBy { it.relativePath.lowercase() }
        }
        return if (ascending) resources.sortedWith(comparator) else resources.sortedWith(comparator.reversed())
    }

    fun statistics(resources: Iterable<ThemeResource>): ThemeResourceStats {
        val items = resources.toList()
        val referenced = items.count { it.referenced }
        val dynamicUncertain = items.count { !it.referenced && it.referenceUncertain }
        return ThemeResourceStats(
            total = items.size,
            referenced = referenced,
            dynamicUncertain = dynamicUncertain,
            unused = items.size - referenced - dynamicUncertain,
        )
    }

    fun missingStaticReferences(root: File, source: String): List<String> {
        if (!root.isDirectory) return emptyList()
        val existing = scan(root).map { normalize(it.relativePath) }.toSet()
        val existingNames = existing.map { it.substringAfterLast('/') }.toSet()
        return luaStringLiterals(source).asSequence()
            .map { normalize(it).trim('/') }
            .filter { it.isNotBlank() && !it.startsWith('/') && it.split('/').none { part -> part == ".." } }
            .filter { looksLikeResourceReference(it) }
            .filter { reference ->
                if ('/' in reference) reference !in existing
                else reference.substringAfterLast('/') !in existingNames
            }
            .distinct()
            .sorted()
            .toList()
    }

    fun literalReferenceCount(source: String, relativePath: String, allowBasename: Boolean = true): Int {
        val normalizedPath = normalize(relativePath)
        val basename = normalizedPath.substringAfterLast('/')
        return luaStringLiterals(source).count { literal ->
            val normalized = normalize(literal)
            normalized == normalizedPath || allowBasename && normalized == basename
        }
    }

    fun canDelete(resource: ThemeResource): Boolean = !resource.referenced && !resource.referenceUncertain

    private fun safeResourceFiles(root: File): List<File> {
        val canonicalRoot = try { root.canonicalFile } catch (_: Exception) { return emptyList() }
        if (!canonicalRoot.isDirectory || root.absolutePath != canonicalRoot.absolutePath) return emptyList()
        val prefix = canonicalRoot.canonicalPath.trimEnd(File.separatorChar) + File.separator
        val directories = java.util.ArrayDeque<File>()
        val visited = HashSet<String>()
        val result = ArrayList<File>()
        directories.add(canonicalRoot)
        while (directories.isNotEmpty()) {
            val directory = directories.removeLast()
            val path = try { directory.canonicalPath } catch (_: Exception) { continue }
            if ((path != canonicalRoot.canonicalPath && !path.startsWith(prefix)) || directory.absolutePath != path || !visited.add(path)) continue
            for (child in directory.listFiles().orEmpty()) {
                val canonical = try { child.canonicalFile } catch (_: Exception) { continue }
                if (child.absolutePath != canonical.absolutePath || !canonical.canonicalPath.startsWith(prefix)) continue
                if (canonical.isDirectory) directories.add(canonical)
                else if (canonical.isFile) {
                    val relative = canonical.relativeTo(canonicalRoot).invariantSeparatorsPath
                    if (isResourcePath(relative)) result += canonical
                }
            }
        }
        return result
    }

    private fun isResourcePath(path: String): Boolean {
        val first = path.substringBefore('/', "")
        if (first in resourceFolders) return true
        val extension = path.substringAfterLast('.', "").lowercase()
        return path.startsWith("styles/") && extension in RESOURCE_EXTENSIONS
    }

    private fun kind(path: String): ThemeResource.Kind {
        val first = path.substringBefore('/', "")
        val extension = path.substringAfterLast('.', "").lowercase()
        return when {
            first == "images" || extension in IMAGE_EXTENSIONS -> ThemeResource.Kind.IMAGE
            first == "fonts" || extension in FONT_EXTENSIONS -> ThemeResource.Kind.FONT
            first == "sounds" || extension in SOUND_EXTENSIONS -> ThemeResource.Kind.SOUND
            first == "scripts" -> ThemeResource.Kind.SCRIPT
            else -> ThemeResource.Kind.OTHER
        }
    }

    private fun looksLikeResourceReference(value: String): Boolean {
        val folder = value.substringBefore('/', "")
        if (folder in resourceFolders) return true
        return value.substringAfterLast('.', "").lowercase() in RESOURCE_EXTENSIONS
    }

    private fun normalize(value: String): String = value.replace('\\', '/').removePrefix("./").lowercase()

    private fun luaStringLiterals(source: String): List<String> {
        val result = ArrayList<String>()
        var quote = '\u0000'
        var start = 0
        var escaped = false
        var i = 0
        while (i < source.length) {
            val c = source[i]
            if (quote == '\u0000') {
                if (c == '-' && i + 1 < source.length && source[i + 1] == '-') {
                    while (i < source.length && source[i] != '\n') i++
                    continue
                }
                if (c == '\'' || c == '"') { quote = c; start = i + 1; escaped = false }
            } else if (escaped) {
                escaped = false
            } else if (c == '\\') {
                escaped = true
            } else if (c == quote) {
                result += unescape(source.substring(start, i))
                quote = '\u0000'
            }
            i++
        }
        return result
    }

    private fun unescape(value: String): String = value
        .replace("\\\\", "\\")
        .replace("\\\"", "\"")
        .replace("\\'", "'")

    private val IMAGE_EXTENSIONS = setOf("png", "jpg", "jpeg", "webp", "gif", "bmp")
    private val FONT_EXTENSIONS = setOf("ttf", "otf", "ttc")
    private val SOUND_EXTENSIONS = setOf("ogg", "mp3", "wav")
    private val RESOURCE_EXTENSIONS = IMAGE_EXTENSIONS + FONT_EXTENSIONS + SOUND_EXTENSIONS

    private val DYNAMIC_RESOURCE = Regex(
        "(?i)(background|image|icon|font|sound|command|script)\\s*=\\s*(?!['\"])[A-Za-z_(]",
    )
}
