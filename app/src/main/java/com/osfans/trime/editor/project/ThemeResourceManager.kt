/*
 * SPDX-FileCopyrightText: 2025 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.editor.project

import java.io.File

/** Resource operations refuse to delete files that are referenced by Lua source. */
class ThemeResourceManager(private val root: File, private val source: String) {
    fun list(): List<ThemeResource> = ThemeResourceIndex.scan(root, source)

    fun list(
        kind: ThemeResource.Kind?,
        sort: ThemeResourceIndex.Sort = ThemeResourceIndex.Sort.PATH,
        ascending: Boolean = true,
    ): List<ThemeResource> = ThemeResourceIndex.sortBy(
        ThemeResourceIndex.filterByKind(list(), kind), sort, ascending
    )

    fun statistics(): ThemeResourceStats = ThemeResourceIndex.statistics(list())

    fun delete(relativePath: String): ResourceDeleteResult {
        val resource = list().firstOrNull { it.relativePath == relativePath }
            ?: return ResourceDeleteResult.NotFound
        if (resource.referenced || resource.referenceUncertain) return ResourceDeleteResult.Referenced(resource)
        val target = File(root, relativePath)
        if (!target.canonicalPath.startsWith(root.canonicalPath + File.separator)) return ResourceDeleteResult.NotFound
        return if (target.isFile && target.delete()) ResourceDeleteResult.Deleted(resource) else ResourceDeleteResult.Failed(resource)
    }
}

sealed class ResourceDeleteResult {
    data class Deleted(val resource: ThemeResource) : ResourceDeleteResult()
    data class Referenced(val resource: ThemeResource) : ResourceDeleteResult()
    data class Failed(val resource: ThemeResource) : ResourceDeleteResult()
    data object NotFound : ResourceDeleteResult()
}
