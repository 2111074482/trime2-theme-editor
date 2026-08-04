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
    "filters resources by image and font kind" {
        val resources = listOf(
            ThemeResource("images/key.png", ThemeResource.Kind.IMAGE, 1, false),
            ThemeResource("fonts/key.ttf", ThemeResource.Kind.FONT, 2, false),
            ThemeResource("sounds/key.wav", ThemeResource.Kind.SOUND, 3, false),
        )

        ThemeResourceIndex.filterByKind(resources, ThemeResource.Kind.IMAGE)
            .map { it.relativePath } shouldBe listOf("images/key.png")
        ThemeResourceIndex.filterByKind(resources, ThemeResource.Kind.FONT)
            .map { it.relativePath } shouldBe listOf("fonts/key.ttf")
    }
    "sorts resources by size in descending order" {
        val resources = listOf(
            ThemeResource("images/small.png", ThemeResource.Kind.IMAGE, 2, false),
            ThemeResource("fonts/large.ttf", ThemeResource.Kind.FONT, 8, false),
            ThemeResource("images/medium.png", ThemeResource.Kind.IMAGE, 5, false),
        )

        ThemeResourceIndex.sortBy(resources, ThemeResourceIndex.Sort.SIZE, ascending = false)
            .map { it.relativePath } shouldBe listOf(
            "fonts/large.ttf",
            "images/medium.png",
            "images/small.png",
        )
    }
    "counts referenced dynamic uncertain and unused resources" {
        val resources = listOf(
            ThemeResource("images/used.png", ThemeResource.Kind.IMAGE, 1, referenced = true),
            ThemeResource(
                "images/dynamic.png",
                ThemeResource.Kind.IMAGE,
                2,
                referenced = false,
                referenceUncertain = true,
            ),
            ThemeResource("fonts/unused.ttf", ThemeResource.Kind.FONT, 3, referenced = false),
        )

        ThemeResourceIndex.statistics(resources) shouldBe ThemeResourceStats(
            total = 3,
            referenced = 1,
            dynamicUncertain = 1,
            unused = 1,
        )
    }
    "counts only Lua string literal references" {
        val source = """
            -- images/key.png in a comment is not a reference
            local ignored = resolve(images/key.png)
            background = "images/key.png"
            icon = 'key.png'
        """.trimIndent()

        ThemeResourceIndex.literalReferenceCount(source, "images/key.png") shouldBe 2
        ThemeResourceIndex.literalReferenceCount(source, "images/key.png", allowBasename = false) shouldBe 1
    }

    "does not index a resource symlink that escapes the project" {
        val root = Files.createTempDirectory("theme-resource-root").toFile()
        val outside = Files.createTempFile("theme-resource-outside", ".png").toFile().apply { writeBytes(byteArrayOf(1)) }
        val link = root.resolve("images/outside.png").apply { parentFile.mkdirs() }
        val linked = try { Files.createSymbolicLink(link.toPath(), outside.toPath()); true } catch (_: Exception) { false }

        if (linked) ThemeResourceIndex.scan(root).none { it.relativePath == "images/outside.png" } shouldBe true
        root.deleteRecursively(); outside.delete()
    }


    "does not index a resource symlink alias inside the project" {
        val root = Files.createTempDirectory("theme-resource-alias").toFile()
        val real = root.resolve("images/real.png").apply { parentFile.mkdirs(); writeBytes(byteArrayOf(1)) }
        val alias = root.resolve("images/alias.png")
        val linked = try { Files.createSymbolicLink(alias.toPath(), real.toPath()); true } catch (_: Exception) { false }
        if (linked) ThemeResourceIndex.scan(root).map { it.relativePath } shouldBe listOf("images/real.png")
        root.deleteRecursively()
    }

    "reports only plausible missing static resource literals" {
        val root = Files.createTempDirectory("resource-missing").toFile()
        root.resolve("images/exists.png").apply { parentFile.mkdirs(); writeBytes(byteArrayOf(1)) }
        root.resolve("fonts/theme.ttf").apply { parentFile.mkdirs(); writeBytes(byteArrayOf(2)) }
        val source = """
background = 'images/exists.png'
font = 'theme.ttf'
pressed = 'images/missing.webp'
sound = 'tap.ogg'
label = 'not-a-resource'
color = '#ffffff'
script = 'scripts/action.lua'
"""
        ThemeResourceIndex.missingStaticReferences(root, source) shouldBe listOf(
            "images/missing.webp",
            "scripts/action.lua",
            "tap.ogg",
        )
        root.deleteRecursively()
    }

    "indexes style-local image font and sound resources without indexing style main Lua" {
        val root = Files.createTempDirectory("theme-style-resources").toFile()
        root.resolve("styles/blue/main.lua").apply { parentFile.mkdirs(); writeText("sound = 'click.ogg'\n") }
        root.resolve("styles/blue/click.ogg").writeBytes(byteArrayOf(1))
        root.resolve("styles/blue/key.webp").writeBytes(byteArrayOf(2))
        root.resolve("styles/blue/theme.ttf").writeBytes(byteArrayOf(3))
        val resources = ThemeResourceIndex.scan(root, root.resolve("styles/blue/main.lua").readText())
        resources.map { it.relativePath } shouldBe listOf(
            "styles/blue/click.ogg",
            "styles/blue/key.webp",
            "styles/blue/theme.ttf",
        )
        resources.map { it.kind } shouldBe listOf(
            ThemeResource.Kind.SOUND,
            ThemeResource.Kind.IMAGE,
            ThemeResource.Kind.FONT,
        )
        resources.first().referenced shouldBe true
        root.deleteRecursively()
    }

})
