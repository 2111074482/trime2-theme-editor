/*
 * SPDX-FileCopyrightText: 2025 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.editor.ui

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

class ThemeEditorClipboardTest : StringSpec({
    "stores process-local deep copies instead of mutable editor objects" {
        val key = ThemeEditorModel.Key("key", "A", 1f, 2f, 3f, 4f).also {
            it.click = "a"
            it.keyStyle = "functional"
        }
        ThemeEditorClipboard.put(
            ThemeEditorClipboard.Payload(
                ThemeEditorClipboard.Type.KEYS,
                "project-hash",
                listOf(key),
                null,
                null,
                null,
                null,
            ),
        )
        key.click = "mutated"

        val first = ThemeEditorClipboard.get()!!
        first.keys.first().click shouldBe "a"
        first.keys.first().click = "changed-copy"
        ThemeEditorClipboard.get()!!.keys.first().click shouldBe "a"
        first.projectIdentity shouldBe "project-hash"
    }
})
