/*
 * Copyright 2024 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.car.appcard.host

import android.content.ContentProviderClient
import android.content.ContentResolver
import android.database.Cursor
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.DeadObjectException
import android.util.Log
import com.android.car.appcard.AppCardContentProvider
import com.android.car.appcard.AppCardMessageConstants.MSG_APP_CARD_COMPONENT_UPDATE
import com.android.car.appcard.AppCardMessageConstants.MSG_CLOSE_PROVIDER
import com.android.car.appcard.internal.AppCardTransport
import com.android.car.appcard.util.ParcelableUtils

/** An [AppCardBroker] that uses a [ContentProviderClient] to communicate */
internal class ContentProviderBroker(
    private val identifier: ApplicationIdentifier,
    private val contentResolver: ContentResolver,
    private val contentProviderClient: ContentProviderClient,
    private var appCardObserver: AppCardObserver,
) : AppCardBroker {
    private var isAlive = true

    init {
        val uri =
            Uri.Builder()
                .scheme(ContentResolver.SCHEME_CONTENT)
                .authority(identifier.authority)
                .build()

        val notifyForDescendants = true
        contentResolver.registerContentObserver(uri, notifyForDescendants, appCardObserver)
    }

    override fun close() {
        contentResolver.unregisterContentObserver(appCardObserver)
        if (isAlive) {
            try {
                sendMessageInternal(MSG_CLOSE_PROVIDER, bundle = null, errorId = null)
            } catch (e: ContentProviderBrokerException) {
                Log.e(TAG, e.message ?: "Exception thrown when trying to close provider")
            }
        }
        contentProviderClient.close()
    }

    override fun getAppCardTransports(
        identifier: ApplicationIdentifier,
        bundle: Bundle,
        msg: String,
    ): List<AppCardTransport> {
        val result =
            getCursorContentProviderClient(
                contentProviderClient,
                identifier.authority,
                bundle,
                msg,
                errorId = null,
            )

        val appCardTransports = mutableListOf<AppCardTransport>()

        if (result.moveToFirst()) {
            do {
                val blob =
                    try {
                        result.getBlob(
                            result.getColumnIndexOrThrow(
                                AppCardContentProvider.CURSOR_COLUMN_APP_CARD_TRANSPORT
                            )
                        )
                    } catch (e: IllegalArgumentException) {
                        result.close()
                        throw ContentProviderBrokerException(msg, id = null, e)
                    }

                val appCardTransport =
                    ParcelableUtils.bytesToParcelable(blob, AppCardTransport.CREATOR)
                appCardTransports.add(appCardTransport)
            } while (result.moveToNext())
            result.close()
        }

        return appCardTransports
    }

    override fun getAppCardTransport(
        identifier: ApplicationIdentifier,
        errorId: String,
        bundle: Bundle,
        msg: String,
    ): AppCardTransport {
        return when (msg) {
            MSG_APP_CARD_COMPONENT_UPDATE ->
                getAppCardTransportFromBundle(identifier, errorId, bundle, msg)

            else -> getAppCardTransportFromCursor(identifier, errorId, bundle, msg)
        }
    }

    private fun getAppCardTransportFromCursor(
        identifier: ApplicationIdentifier,
        errorId: String,
        bundle: Bundle,
        msg: String,
    ): AppCardTransport {
        val result =
            getCursorContentProviderClient(
                contentProviderClient,
                identifier.authority,
                bundle,
                msg,
                errorId,
            )

        var appCardTransport: AppCardTransport? = null
        if (result.moveToFirst()) {
            val blob =
                try {
                    result.getBlob(
                        result.getColumnIndexOrThrow(
                            AppCardContentProvider.CURSOR_COLUMN_APP_CARD_TRANSPORT
                        )
                    )
                } catch (e: IllegalArgumentException) {
                    result.close()
                    throw ContentProviderBrokerException(msg, errorId, e)
                }
            appCardTransport = ParcelableUtils.bytesToParcelable(blob, AppCardTransport.CREATOR)
        }
        result.close()

        appCardTransport
            ?: throw ContentProviderBrokerException(
                msg,
                errorId,
                IllegalStateException("App card transport missing"),
            )

        return appCardTransport
    }

    private fun getAppCardTransportFromBundle(
        identifier: ApplicationIdentifier,
        errorId: String,
        bundle: Bundle,
        msg: String,
    ): AppCardTransport {
        val result = getBundleContentProviderClient(contentProviderClient, bundle, msg, errorId)

        result.classLoader = AppCardTransport::class.java.classLoader
        val appCardTransport =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                result.getParcelable(
                    AppCardContentProvider.BUNDLE_KEY_APP_CARD_COMPONENT,
                    AppCardTransport::class.java,
                )
            } else {
                result.getParcelable(AppCardContentProvider.BUNDLE_KEY_APP_CARD_COMPONENT)
            }
                ?: throw ContentProviderBrokerException(
                    msg,
                    id = errorId,
                    IllegalStateException("App card transport missing from bundle"),
                )

        return appCardTransport
    }

    override fun sendMessage(msg: String, bundle: Bundle, errorId: String) {
        sendMessageInternal(msg, bundle, errorId)
    }

    private fun sendMessageInternal(msg: String, bundle: Bundle?, errorId: String?) {
        try {
            contentProviderClient.call(msg, NO_ARGS, bundle)
        } catch (e: Exception) {
            if (e is DeadObjectException) {
                isAlive = false
            }
            throw ContentProviderBrokerException(msg, errorId, e)
        }
    }

    private fun getCursorContentProviderClient(
        contentProviderClient: ContentProviderClient,
        authority: String,
        bundle: Bundle,
        eventName: String,
        errorId: String?,
    ): Cursor {
        val uri = Uri.Builder().authority(authority).appendPath(eventName).build()

        val result: Cursor =
            try {
                contentProviderClient.query(uri, NO_ARGS, bundle, NO_ARGS)
            } catch (e: Exception) {
                if (e is DeadObjectException) {
                    isAlive = false
                }
                throw ContentProviderBrokerException(eventName, errorId, e)
            }
                ?: throw ContentProviderBrokerException(
                    eventName,
                    errorId,
                    IllegalStateException("Result from query is null"),
                )

        return result
    }

    private fun getBundleContentProviderClient(
        contentProviderClient: ContentProviderClient,
        bundle: Bundle,
        eventName: String,
        errorId: String?,
    ): Bundle {
        val result: Bundle =
            try {
                contentProviderClient.call(eventName, NO_ARGS, bundle)
            } catch (e: Exception) {
                if (e is DeadObjectException) {
                    isAlive = false
                }
                throw ContentProviderBrokerException(eventName, errorId, e)
            }
                ?: throw ContentProviderBrokerException(
                    eventName,
                    errorId,
                    IllegalStateException("Result from call is null"),
                )

        return result
    }

    class ContentProviderBrokerException(val method: String, val id: String?, cause: Throwable) :
        Exception(
            id?.let {
                "Exception occurred when calling (with $method) app card content provider (ID: $id)"
            } ?: "Exception occurred when calling (with $method) app card content provider",
            cause,
        )

    companion object {
        private const val TAG = "ContentProviderAppCardBroker"
        private val NO_ARGS = null
    }
}
