package com.firsttimeinforever.intellij.pdf.viewer.actions.common

import com.firsttimeinforever.intellij.pdf.viewer.actions.PdfEditorAction
import com.firsttimeinforever.intellij.pdf.viewer.actions.findPdfFileEditor
import com.intellij.openapi.actionSystem.AnActionEvent

class PreviousPageAction: PdfEditorAction(
    disableInIdePresentationMode = false
) {
    override fun actionPerformed(event: AnActionEvent) {
        findPdfFileEditor(event)?.previousPage()
    }

    override fun update(event: AnActionEvent) {
        super.update(event)
        findPdfFileEditor(event)?.also {
            if (it.viewPanel.pagesCount == 0) {
                event.presentation.isEnabled = false
            }
            else {
                event.presentation.isEnabled = (it.viewPanel.currentPageNumber != 1)
            }
        }
    }
}
