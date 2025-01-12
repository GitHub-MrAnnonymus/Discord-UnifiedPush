package com.mrannonymus.discordwithnormalnotificationsunifiedpush

import android.content.Context
import org.unifiedpush.android.connector.MessagingReceiver

class UnifiedPushReceiver : MessagingReceiver() {
    companion object {
        private var helper: UnifiedPushHelper? = null
    }

    override fun onMessage(context: Context, message: ByteArray, instance: String) {
        getHelper(context).handleMessage(message)
    }

    override fun onNewEndpoint(context: Context, endpoint: String, instance: String) {
        getHelper(context).onNewEndpoint(endpoint)
    }

    override fun onRegistrationFailed(context: Context, instance: String) {
        getHelper(context).onRegistrationFailed()
    }

    override fun onUnregistered(context: Context, instance: String) {
        getHelper(context).onRegistrationFailed()
    }

    private fun getHelper(context: Context): UnifiedPushHelper {
        return helper ?: UnifiedPushHelper.getInstance(context).also { helper = it }
    }
}