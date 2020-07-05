package com.firsttimeinforever.intellij.pdf.viewer.ui.editor.panel.jcef

import com.firsttimeinforever.intellij.pdf.viewer.ui.editor.panel.jcef.messages.DocumentInfoMessage
import javax.swing.BoxLayout
import javax.swing.JPanel
import javax.swing.JSeparator
import javax.swing.SwingConstants

class DocumentInfoPanel(documentInfo: DocumentInfoMessage): JPanel() {
    init {
        layout = BoxLayout(this, BoxLayout.Y_AXIS)
        documentInfo.run {
            add(InfoEntryPanel("File Name", fileName))
            add(InfoEntryPanel("File Size", fileSize))
            add(JSeparator(SwingConstants.HORIZONTAL))
            add(InfoEntryPanel("Title", title))
            add(InfoEntryPanel("Subject", subject))
            add(InfoEntryPanel("Author", author))
            add(InfoEntryPanel("Creator", creator))
            add(InfoEntryPanel("Creation Date", creationDate))
            add(InfoEntryPanel("Modification Date", modificationDate))
            add(InfoEntryPanel("Producer", producer))
            add(InfoEntryPanel("Version", version))
            add(JSeparator(SwingConstants.HORIZONTAL))
            add(InfoEntryPanel("Page Size", pageSize))
            add(InfoEntryPanel("Linearized", linearized))
        }
    }
}
