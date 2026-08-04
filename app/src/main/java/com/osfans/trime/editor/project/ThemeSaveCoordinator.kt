/*
 * SPDX-FileCopyrightText: 2025 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.editor.project

import com.osfans.trime.editor.core.Severity
import com.osfans.trime.editor.core.ThemeDocument
import com.osfans.trime.editor.core.ThemeLuaParser
import com.osfans.trime.editor.core.ThemeLuaWriter
import java.io.IOException
import java.security.MessageDigest

/** Serializes project saves and rejects stale external content before commit. */
class ThemeSaveCoordinator {
    private val locks = HashMap<String, Any>()

    @Synchronized
    private fun lockFor(projectId: String): Any = locks.getOrPut(projectId) { Any() }

    fun save(
        projectId: String,
        repository: ThemeProjectRepository,
        document: ThemeDocument,
        expectedFingerprint: String?,
    ): SaveResult = synchronized(lockFor(projectId)) {
        val current = fingerprint(repository.read())
        if (expectedFingerprint != null && current != expectedFingerprint) {
            return SaveResult.ExternalConflict(current)
        }
        val source = ThemeLuaWriter.write(document)
        val candidateError = ThemeLuaParser().parse(source).diagnostics.firstOrNull { it.severity == Severity.ERROR }
        if (candidateError != null) throw IOException("候选 Lua 未通过静态解析:第${candidateError.line}行:${candidateError.message}")
        repository.write(source)
        val committed = repository.read()
        if (committed != source) throw IOException("保存后的源代码回读校验不一致")
        val committedError = ThemeLuaParser().parse(committed).diagnostics.firstOrNull { it.severity == Severity.ERROR }
        if (committedError != null) throw IOException("保存后的 Lua 未通过静态解析:第${committedError.line}行:${committedError.message}")
        SaveResult.Succeeded(fingerprint(committed))
    }

    companion object {
        fun fingerprint(source: String): String = MessageDigest.getInstance("SHA-256")
            .digest(source.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
    }
}

sealed class SaveResult {
    data class Succeeded(val fingerprint: String) : SaveResult()
    data class ExternalConflict(val actualFingerprint: String) : SaveResult()
}
