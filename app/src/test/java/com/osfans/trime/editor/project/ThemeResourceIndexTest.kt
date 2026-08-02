/*
 * SPDX-FileCopyrightText: 2025 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.editor.project

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import java.nio.file.Files

class ThemeResourceIndexTest : StringSpec({
    "indexes theme resource folders and references" {
        val root = Files.createTempDirectory("theme-editor").toFile()
        root.resolve("images/key.png").apply { parentFile.mkdirs(); writeBytes(byteArrayOf(1)) }
        val resources = ThemeResourceIndex.scan(root, "background = 'key.png'")
        resources.single().kind shouldBe ThemeResource.Kind.IMAGE
        resources.single().referenced shouldBe true
        root.deleteRecursively()
    }
})
