/*
 * SPDX-FileCopyrightText: 2025 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.editor.core

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

class ThemeSessionStateTest : StringSpec({
    "model revisions increase monotonically for edits" {
        val initial = ThemeSessionState("project")
        val firstEdit = initial.edited()
        val secondEdit = firstEdit.edited()

        firstEdit.modelRevision shouldBe 1
        secondEdit.modelRevision shouldBe 2
        (secondEdit.modelRevision > firstEdit.modelRevision) shouldBe true
    }

    "preview changes do not dirty the model" {
        val initial = ThemeSessionState("project")
        val preview = initial.previewChanged()
        preview.previewRevision shouldBe 1
        preview.modelRevision shouldBe 0
        preview.dirty shouldBe false
    }

    "preview revisions increase independently of model edits" {
        val edited = ThemeSessionState("project").edited()
        val preview = edited.previewChanged().previewChanged()

        preview.previewRevision shouldBe 2
        preview.modelRevision shouldBe edited.modelRevision
        preview.dirty shouldBe edited.dirty
    }

    "saving the current revision clears dirty state" {
        val edited = ThemeSessionState("project").edited()
        edited.saved("fingerprint").dirty shouldBe false
        edited.saved("fingerprint").savedRevision shouldBe edited.modelRevision
    }

    "saving does not clear dirty state from later edits" {
        val saved = ThemeSessionState("project").edited().saved("fingerprint")
        val editedAgain = saved.edited()

        editedAgain.dirty shouldBe true
        editedAgain.savedRevision shouldBe saved.savedRevision
        editedAgain.modelRevision shouldBe saved.modelRevision + 1
    }


    "completing an older save keeps later revisions dirty" {
        val current = ThemeSessionState("project").edited().edited()
        val afterOldSave = current.saved(targetRevision = 1, fingerprint = "old-fingerprint")

        afterOldSave.modelRevision shouldBe 2
        afterOldSave.savedRevision shouldBe 1
        afterOldSave.dirty shouldBe true
    }

    "a stale save completion cannot replace a newer fingerprint" {
        val current = ThemeSessionState("project").edited().edited().saved(targetRevision = 2, fingerprint = "new")
        val afterStaleSave = current.saved(targetRevision = 1, fingerprint = "old")

        afterStaleSave shouldBe current
        afterStaleSave.sourceFingerprint shouldBe "new"
    }

})
