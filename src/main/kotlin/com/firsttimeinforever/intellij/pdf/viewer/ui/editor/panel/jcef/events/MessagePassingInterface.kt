package com.firsttimeinforever.intellij.pdf.viewer.ui.editor.panel.jcef.events

import com.intellij.openapi.Disposable
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.util.Disposer
import com.intellij.ui.jcef.JBCefBrowser
import kotlinx.serialization.ImplicitReflectionSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonConfiguration
import kotlinx.serialization.serializer

class MessagePassingInterface(browser: JBCefBrowser): Disposable {
    val eventSender = MessageEventSender(browser, jsonSerializer)
    val eventReceiver =
        MessageEventReceiver.fromList(browser, SubscribableEventType.values().asList())

    init {
        Disposer.register(this, eventReceiver)
    }

    @ImplicitReflectionSerializer
    inline fun <reified MessageType: Any> triggerEvent(event: TriggerableEventType, message: MessageType) {
        eventSender.triggerWith(event, message, MessageType::class.serializer())
    }

    fun triggerEvent(event: TriggerableEventType) = eventSender.trigger(event)

    @ImplicitReflectionSerializer
    inline fun <reified MessageType: Any> subscribe(event: SubscribableEventType, crossinline listener: (MessageType) -> Unit) {
        eventReceiver.addHandler(event) {
            try {
                val result = jsonSerializer.parse(MessageType::class.serializer(), it)
                listener(result)
            }
            catch (exception: Exception) {
                logger<MessagePassingInterface>().warn("Failed to parse event message!", exception)
            }
        }
    }

    fun subscribe(event: SubscribableEventType, listener: () -> Unit) {
        eventReceiver.addHandler(event) { listener() }
    }

    fun subscribePlain(event: SubscribableEventType, listener: (String) -> Unit) {
        eventReceiver.addHandler(event, listener)
    }

    override fun dispose() = Unit

    companion object {
        val jsonSerializer = Json(JsonConfiguration.Stable.copy(ignoreUnknownKeys = true))
    }
}
