/*
 * SPDX-FileCopyrightText: 2025 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.osfans.trime.editor.ui

import androidx.lifecycle.SavedStateHandle
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

class ThemeEditorViewModelTest : StringSpec({
    "preview changes do not dirty the project" {
        val model = ThemeEditorViewModel(SavedStateHandle())
        model.recordPreviewChange()

        model.previewRevision shouldBe 1
        model.modelRevision shouldBe 0
        model.dirty shouldBe false
    }

    "model revisions and saved revisions drive dirty state" {
        val model = ThemeEditorViewModel(SavedStateHandle())
        model.recordEdit()
        model.dirty shouldBe true
        model.markSaved("fingerprint")

        model.modelRevision shouldBe 1
        model.savedRevision shouldBe 1
        model.sourceFingerprint shouldBe "fingerprint"
        model.dirty shouldBe false
    }

    "configuration recreation preserves dirty revision without adding an edit" {
        val handle = SavedStateHandle()
        val first = ThemeEditorViewModel(handle)
        first.markLoaded("project-a", "base")
        first.recordEdit()
        val revision = first.modelRevision

        val restored = ThemeEditorViewModel(handle)
        restored.modelRevision shouldBe revision
        restored.dirty shouldBe true
    }

    "loading another project clears project scoped selection and revisions" {
        val model = ThemeEditorViewModel(SavedStateHandle())
        model.markLoaded("project-a", "a")
        model.selectedKeyId = "key-a"
        model.inspectorTab = "events"
        model.recordEdit()
        model.recordPreviewChange()

        model.markLoaded("project-b", "b")

        model.projectId shouldBe "project-b"
        model.modelRevision shouldBe 0
        model.savedRevision shouldBe 0
        model.previewRevision shouldBe 0
        model.selectedKeyId shouldBe null
        model.inspectorTab shouldBe "basic"
        model.dirty shouldBe false
    }
    "persists pending SAF operation metadata without storing user bytes in saved state" {
        val handle = SavedStateHandle()
        val model = ThemeEditorViewModel(handle)
        model.pendingResourceFolder = "images"
        model.pendingExportPath = "/cache/export.zip"
        model.pendingDirectoryExportPath = "/cache/export"
        model.pendingDirectoryExportName = "demo"
        model.pendingCreateSpec = "demo\nDemo\nAuthor\nlight\ndefault\nLIGHT\nROWS"
        model.pendingBuiltInTemplatePath = "/cache/template"
        model.pendingBuiltInTemplateName = "default_copy"
        model.pendingTextExport = true
        model.pendingSaveSource = true

        ThemeEditorViewModel(handle).let { restored ->
            restored.pendingResourceFolder shouldBe "images"
            restored.pendingExportPath shouldBe "/cache/export.zip"
            restored.pendingDirectoryExportName shouldBe "demo"
            restored.pendingCreateSpec?.startsWith("demo") shouldBe true
            restored.pendingBuiltInTemplatePath shouldBe "/cache/template"
            restored.pendingBuiltInTemplateName shouldBe "default_copy"
            restored.pendingTextExport shouldBe true
            restored.pendingSaveSource shouldBe true
        }
    }

})
