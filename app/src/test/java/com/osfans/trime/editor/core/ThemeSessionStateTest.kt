/*
 * SPDX-FileCopyrightText: 2025 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.editor.core

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

class ThemeSessionStateTest : StringSpec({
    "preview changes do not dirty the model" {
        val initial = ThemeSessionState("project")
        val preview = initial.previewChanged()
        preview.previewRevision shouldBe 1
        preview.modelRevision shouldBe 0
        preview.dirty shouldBe false
    }

    "saving the current revision clears dirty state" {
        val edited = ThemeSessionState("project").edited()
        edited.saved("fingerprint").dirty shouldBe false
        edited.saved("fingerprint").savedRevision shouldBe edited.modelRevision
    }
})
