/*
 * SPDX-FileCopyrightText: 2025 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.editor.project

import java.io.File
import java.security.MessageDigest

/** Content fingerprint used before committing a project file. */
data class ThemeSourceFingerprint(val size: Long, val modified: Long, val sha256: String) {
    companion object {
        fun capture(file: File): ThemeSourceFingerprint {
            val digest = MessageDigest.getInstance("SHA-256")
            file.inputStream().buffered().use { input ->
                val buffer = ByteArray(8192)
                var count: Int
                while (input.read(buffer).also { count = it } != -1) digest.update(buffer, 0, count)
            }
            return ThemeSourceFingerprint(file.length(), file.lastModified(), digest.digest().joinToString("") { "%02x".format(it) })
        }
    }
}
