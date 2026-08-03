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

object ThemeResourceIndex {
    private val resourceFolders = setOf("images", "fonts", "sounds", "scripts")

    fun scan(root: File, source: String = ""): List<ThemeResource> {
        if (!root.isDirectory) return emptyList()
        val files = root.walkTopDown().filter { file ->
            file.isFile && file.relativeTo(root).invariantSeparatorsPath.substringBefore('/') in resourceFolders
        }.toList()
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
        }
    }

    fun canDelete(resource: ThemeResource): Boolean = !resource.referenced && !resource.referenceUncertain

    private fun kind(path: String): ThemeResource.Kind = when {
        path.startsWith("images/") -> ThemeResource.Kind.IMAGE
        path.startsWith("fonts/") -> ThemeResource.Kind.FONT
        path.startsWith("sounds/") -> ThemeResource.Kind.SOUND
        path.startsWith("scripts/") -> ThemeResource.Kind.SCRIPT
        else -> ThemeResource.Kind.OTHER
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

    private val DYNAMIC_RESOURCE = Regex(
        "(?i)(background|image|icon|font|sound|command|script)\\s*=\\s*(?!['\"])[A-Za-z_(]",
    )
}
