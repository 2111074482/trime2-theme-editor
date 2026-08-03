/*
 * SPDX-FileCopyrightText: 2025 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.editor.project

import com.osfans.trime.editor.core.ThemeDocument
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
        repository.save(document)
        SaveResult.Succeeded(fingerprint(repository.read()))
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
