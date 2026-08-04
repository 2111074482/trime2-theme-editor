/*
 * SPDX-FileCopyrightText: 2025 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.editor.project

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import java.nio.file.Files

class ThemeResourceManagerTest : StringSpec({
    "refuses referenced resources and deletes unused resources" {
        val root = Files.createTempDirectory("theme-resources").toFile()
        root.resolve("images/used.png").apply { parentFile.mkdirs(); writeBytes(byteArrayOf(1)) }
        root.resolve("images/free.png").writeBytes(byteArrayOf(1))
        val manager = ThemeResourceManager(root, "background = 'used.png'")
        (manager.delete("images/used.png") is ResourceDeleteResult.Referenced) shouldBe true
        (manager.delete("images/free.png") is ResourceDeleteResult.Deleted) shouldBe true
        root.deleteRecursively()
    }
    "matches exact Lua resource literals and protects dynamic references" {
        val root = Files.createTempDirectory("theme-resource-literals").toFile()
        root.resolve("images/icon.png").apply { parentFile.mkdirs(); writeBytes(byteArrayOf(1)) }
        root.resolve("images/icon.png.bak").writeBytes(byteArrayOf(1))
        val exact = ThemeResourceManager(root, "background = 'images/icon.png'")
        (exact.delete("images/icon.png") is ResourceDeleteResult.Referenced) shouldBe true
        (exact.delete("images/icon.png.bak") is ResourceDeleteResult.Deleted) shouldBe true
        root.resolve("images/dynamic.png").writeBytes(byteArrayOf(1))
        val dynamic = ThemeResourceManager(root, "background = resolve_image(name)")
        (dynamic.delete("images/dynamic.png") is ResourceDeleteResult.Referenced) shouldBe true
        root.deleteRecursively()
    }

})
