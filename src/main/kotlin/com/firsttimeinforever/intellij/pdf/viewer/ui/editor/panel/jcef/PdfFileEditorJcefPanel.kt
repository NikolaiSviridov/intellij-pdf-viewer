package com.firsttimeinforever.intellij.pdf.viewer.ui.editor.panel.jcef

import com.firsttimeinforever.intellij.pdf.viewer.settings.PdfViewerSettings
import com.firsttimeinforever.intellij.pdf.viewer.ui.editor.StaticServer
import com.firsttimeinforever.intellij.pdf.viewer.ui.editor.panel.PdfFileEditorPanel
import com.firsttimeinforever.intellij.pdf.viewer.ui.editor.panel.jcef.events.*
import com.firsttimeinforever.intellij.pdf.viewer.ui.editor.panel.jcef.events.objects.*
import com.intellij.notification.Notification
import com.intellij.notification.NotificationType
import com.intellij.notification.Notifications
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.editor.colors.EditorColorsListener
import com.intellij.openapi.editor.colors.EditorColorsScheme
import com.intellij.openapi.ui.DialogBuilder
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileEvent
import com.intellij.openapi.vfs.VirtualFileListener
import com.intellij.ui.jcef.JCEFHtmlPanel
import com.intellij.util.ui.UIUtil
import kotlinx.serialization.json.JsonDecodingException
import org.cef.browser.CefBrowser
import org.cef.browser.CefFrame
import org.cef.handler.CefLoadHandlerAdapter
import java.awt.Color
import java.awt.event.KeyEvent
import java.awt.event.KeyListener
import javax.swing.BoxLayout

class PdfFileEditorJcefPanel(virtualFile: VirtualFile):
    PdfFileEditorPanel(virtualFile), EditorColorsListener
{
    private val browserPanel = JCEFHtmlPanel("about:blank")
    private val logger = logger<PdfFileEditorJcefPanel>()
    private val messagePassingInterface = MessagePassingInterface(browserPanel)
    val presentationModeController =
        PresentationModeController(this, browserPanel.component, messagePassingInterface)
    private var currentPageNumberHolder: Int = 1
    private val controlPanel = ControlPanel()
    private var currentScrollDirectionHorizontal = true
    private var pagesCountHolder = 0
    private var pageSpreadStateHolder = PageSpreadState.NONE
    private val documentLoadErrorPanel = DocumentLoadErrorPanel()
    private var sidebarViewStateHolder = SidebarViewState()
    private var sidebarAvailableViewModesHolder = SidebarAvailableViewModes()

    private fun showDocumentLoadErrorNotification() {
        val reloadAction =  ActionManager.getInstance().getAction(RELOAD_ACTION_ID)?:
            error("Could not get document reload action")
        val notification = Notification(
            "PDF Viewer",
            "Could not open document!",
            "Failed to open selected document!",
            NotificationType.ERROR
        ).addAction(reloadAction.templatePresentation.run {
            object: AnAction(text, description, icon) {
                override fun actionPerformed(event: AnActionEvent) {
                    if (browserPanel.isDisposed) {
                        return
                    }
                    reloadDocument()
                }
            }
        })
        Notifications.Bus.notify(notification)
    }

    private val pageNavigationKeyListener = object: KeyListener {
        override fun keyPressed(event: KeyEvent?) {
            when (event?.keyCode) {
                KeyEvent.VK_LEFT -> previousPage()
                KeyEvent.VK_RIGHT -> nextPage()
            }
        }
        override fun keyTyped(event: KeyEvent?) = Unit
        override fun keyReleased(event: KeyEvent?) = Unit
    }

    private val settingsChangeListener = { settings: PdfViewerSettings ->
        setThemeColors()
        if (!settings.enableDocumentAutoReload) {
            removeFileUpdateHandler()
        }
        else if (watchRequest == null) {
            addFileUpdateHandler()
        }
    }

    private var watchRequest: LocalFileSystem.WatchRequest? = null

    private val fileListener = object: VirtualFileListener {
        override fun contentsChanged(event: VirtualFileEvent) {
            logger.debug("Got some events batch")
            if (event.file != virtualFile) {
                logger.debug("Seems like target file (${virtualFile.path}) is not changed")
                return
            }
            logger.debug("Target file (${virtualFile.path}) changed. Reloading page!")
            val targetUrl = StaticServer.instance.getFilePreviewUrl(virtualFile.path)
            browserPanel.loadURL(targetUrl.toExternalForm())
        }
    }

    init {
        Disposer.register(this, browserPanel)
        Disposer.register(this, messagePassingInterface)
        layout = BoxLayout(this, BoxLayout.Y_AXIS)
        add(controlPanel)
        add(browserPanel.component)
        messagePassingInterface.run {
            subscribe<PageChangeDataObject>(SubscribableEvent.PAGE_CHANGED) {
                currentPageNumberHolder = it.pageNumber
                pageStateChanged()
            }
            subscribe<DocumentInfoDataObject>(SubscribableEvent.DOCUMENT_INFO) {
                ApplicationManager.getApplication().invokeLater {
                    showDocumentInfoDialog(it)
                }
            }
            subscribe(SubscribableEvent.FRAME_FOCUSED) {
                grabFocus()
            }
            subscribe<PagesCountDataObject>(SubscribableEvent.PAGES_COUNT) {
                try {
                    pagesCountHolder = it.count
                    pageStateChanged()
                }
                catch (exception: JsonDecodingException) {
                    logger.warn(
                        "Failed to parse PagesCount data object! (This should be fixed at message passing level)",
                        exception
                    )
                }
            }
            subscribePlain(SubscribableEvent.DOCUMENT_LOAD_ERROR) {
                // For some reason this event triggers with no data
                // This should be impossible, due to passing event data
                // to triggerEvent() in unhandledrejection event handler
                if (it.isNotEmpty()) {
                    browserPanel.component.isVisible = false
                    add(documentLoadErrorPanel)
                    showDocumentLoadErrorNotification()
                }
            }
            subscribe<SidebarViewStateChangeDataObject>(SubscribableEvent.SIDEBAR_VIEW_STATE_CHANGED) {
                sidebarViewStateHolder = it.state
            }
            subscribe<SidebarAvailableViewModesChangedDataObject>(SubscribableEvent.SIDEBAR_AVAILABLE_VIEWS_CHANGED) {
                sidebarAvailableViewModesHolder = it
            }
        }
        addKeyListener(pageNavigationKeyListener)
        presentationModeController.run {
            addEnterListener {
                controlPanel.presentationModeEnabled = true
                false
            }
            addExitListener {
                controlPanel.presentationModeEnabled = false
                false
            }
        }
        PdfViewerSettings.instance.addChangeListener(settingsChangeListener)
        openDocument()
    }

    val sidebarAvailableViewModes
        get() = sidebarAvailableViewModesHolder

    val isCurrentScrollDirectionHorizontal
        get() = currentScrollDirectionHorizontal

    override val pagesCount
        get() = pagesCountHolder

    val sidebarViewState: SidebarViewState
        get() = sidebarViewStateHolder

    fun setSidebarViewMode(mode: SidebarViewMode) {
        sidebarViewStateHolder = SidebarViewState(mode, sidebarViewStateHolder.hidden)
        messagePassingInterface.triggerEvent(
            TriggerableEvent.SET_SIDEBAR_VIEW_MODE,
            SidebarViewModeChangeDataObject.from(mode)
        )
    }

    override var currentPageNumber: Int
        get() = currentPageNumberHolder
        set(value) {
            currentPageNumberHolder = value
            updatePageNumber(value)
        }

    private fun updatePageNumber(value: Int) {
        messagePassingInterface.triggerEvent(
            TriggerableEvent.SET_PAGE,
            PageChangeDataObject(value)
        )
    }

    override fun increaseScale() = messagePassingInterface.triggerEvent(TriggerableEvent.INCREASE_SCALE)
    override fun decreaseScale() = messagePassingInterface.triggerEvent(TriggerableEvent.DECREASE_SCALE)
    override fun nextPage() = messagePassingInterface.triggerEvent(TriggerableEvent.GOTO_NEXT_PAGE)
    override fun previousPage() = messagePassingInterface.triggerEvent(TriggerableEvent.GOTO_PREVIOUS_PAGE)

    fun getDocumentInfo() = messagePassingInterface.triggerEvent(TriggerableEvent.GET_DOCUMENT_INFO)
    fun toggleSidebar() = messagePassingInterface.triggerEvent(TriggerableEvent.TOGGLE_SIDEBAR)
    fun printDocument() = messagePassingInterface.triggerEvent(TriggerableEvent.PRINT_DOCUMENT)
    fun rotateClockwise() = messagePassingInterface.triggerEvent(TriggerableEvent.ROTATE_CLOCKWISE)
    fun rotateCounterclockwise() = messagePassingInterface.triggerEvent(TriggerableEvent.ROTATE_COUNTERCLOCKWISE)

    var pageSpreadState
        get() = pageSpreadStateHolder
        set(state) {
            if (pageSpreadStateHolder == state) {
                return
            }
            pageSpreadStateHolder = state
            messagePassingInterface.triggerEvent(when (state) {
                PageSpreadState.NONE -> TriggerableEvent.SPREAD_NONE
                PageSpreadState.EVEN -> TriggerableEvent.SPREAD_EVEN_PAGES
                PageSpreadState.ODD -> TriggerableEvent.SPREAD_ODD_PAGES
            })
        }

    fun toggleScrollDirection(): Boolean {
        messagePassingInterface.triggerEvent(TriggerableEvent.TOGGLE_SCROLL_DIRECTION)
        currentScrollDirectionHorizontal = !currentScrollDirectionHorizontal
        return currentScrollDirectionHorizontal
    }

    fun openDevtools() = browserPanel.openDevtools()

    override fun findNext() {
        if (!controlPanel.searchTextField.isFocusOwner) {
            controlPanel.searchTextField.grabFocus()
        }
        val searchTarget = controlPanel.searchTextField.text ?: return
        messagePassingInterface.triggerEvent(
            TriggerableEvent.FIND_NEXT,
            SearchDataObject(searchTarget)
        )
    }

    override fun findPrevious() {
        if (!controlPanel.searchTextField.isFocusOwner) {
            controlPanel.searchTextField.grabFocus()
        }
        val searchTarget = controlPanel.searchTextField.text ?: return
        messagePassingInterface.triggerEvent(
            TriggerableEvent.FIND_PREVIOUS,
            SearchDataObject(searchTarget)
        )
    }

    private fun addFileUpdateHandler() {
        LocalFileSystem.getInstance().run {
            watchRequest = addRootToWatch(virtualFile.path, false)
            addVirtualFileListener(fileListener)
        }
    }

    private fun removeFileUpdateHandler() {
        LocalFileSystem.getInstance().run {
            watchRequest?.also {
                removeWatchedRoot(it)
                watchRequest = null
            }
            removeVirtualFileListener(fileListener)
        }
    }

    private fun showDocumentInfoDialog(documentInfo: DocumentInfoDataObject) =
        DialogBuilder().centerPanel(DocumentInfoPanel(documentInfo)).showModal(true)

    private fun openDocument() {
        if (PdfViewerSettings.instance.enableDocumentAutoReload) {
            addFileUpdateHandler()
        }
        addReloadHandler()
        reloadDocument()
    }

    private fun addReloadHandler() {
        val targetUrl = StaticServer.instance.getFilePreviewUrl(virtualFile.path).toExternalForm()
        browserPanel.jbCefClient.addLoadHandler(object: CefLoadHandlerAdapter() {
            override fun onLoadEnd(browser: CefBrowser?, frame: CefFrame?, httpStatusCode: Int) {
                if (browser == null || browser.url != targetUrl) {
                    return
                }
                messagePassingInterface.eventReceiver.injectSubscriptions()
                updatePageNumber(currentPageNumber)
                setThemeColors()
            }
        }, browserPanel.cefBrowser)
    }

    override fun reloadDocument() {
        remove(documentLoadErrorPanel)
        browserPanel.component.isVisible = true
        val targetUrl = StaticServer.instance.getFilePreviewUrl(virtualFile.path).toExternalForm()
        logger.debug("Trying to load url: $targetUrl")
        browserPanel.loadURL(targetUrl)
    }

    private fun setThemeColors(
        background: Color = UIUtil.getPanelBackground(),
        foreground: Color = UIUtil.getLabelForeground()
    ) {
        messagePassingInterface.triggerEvent(
            TriggerableEvent.SET_THEME_COLORS,
            PdfViewerSettings.instance.run {
                if (useCustomColors) {
                    SetThemeColorsDataObject.from(
                        Color(customBackgroundColor),
                        Color(customForegroundColor),
                        Color(customIconColor)
                    )
                }
                else {
                    SetThemeColorsDataObject.from(
                        background,
                        foreground,
                        PdfViewerSettings.defaultIconColor
                    )
                }
            }
        )
    }

    override fun dispose() {
        PdfViewerSettings.instance.removeChangeListener(settingsChangeListener)
    }

    override fun globalSchemeChange(scheme: EditorColorsScheme?) {
        setThemeColors()
    }

    companion object {
        private const val RELOAD_ACTION_ID =
            "com.firsttimeinforever.intellij.pdf.viewer.actions.common.ReloadDocumentAction"
    }
}
