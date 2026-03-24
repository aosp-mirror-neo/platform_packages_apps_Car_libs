/*
 * Copyright (C) 2024 The Android Open Source Project
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
package com.android.car.appcard

import android.content.ContentProvider
import android.content.ContentResolver
import android.content.ContentValues
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.Bundle
import android.os.CancellationSignal
import android.util.Log
import androidx.annotation.CallSuper
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import com.android.car.appcard.AppCardContext.Companion.fromBundle
import com.android.car.appcard.AppCardMessageConstants.InteractionMessageConstants
import com.android.car.appcard.annotations.EnforceFastUpdateRate
import com.android.car.appcard.component.Button
import com.android.car.appcard.component.Component
import com.android.car.appcard.internal.AppCardTransport
import com.android.car.appcard.util.ParcelableUtils
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentMap

/** A [ContentProvider] that provides the system access to an application's app cards. */
abstract class AppCardContentProvider : ContentProvider(), LifecycleOwner {
    private val activeAppCardCountMap: ConcurrentMap<String, Int> = ConcurrentHashMap()
    private val appCardIdComponentMap: ConcurrentMap<String, ConcurrentMap<String, Component>> =
        ConcurrentHashMap()
    private val latestAppCard: ConcurrentMap<String, AppCard?> = ConcurrentHashMap()
    private val lock = Any()
    private val dispatcher = ProviderLifecycleDispatcher(provider = this)

    override val lifecycle: Lifecycle = dispatcher.lifecycle

    /** @return authority of provider in manifest */
    abstract val authority: String

    @CallSuper
    override fun onCreate(): Boolean {
        dispatcher.queueOnCreate()
        return true
    }

    /**
     * Setup app card for given ID.
     *
     * @param id id of app card being requested
     * @param ctx context providing information such as dimensions of app card, etc.
     * @return app card being requested
     */
    protected abstract fun onAppCardAdded(id: String, ctx: AppCardContext): AppCard

    /**
     * Update app card for given ID when system is ready to receive an update
     *
     * @param appCard app card that should be updated
     */
    fun sendAppCardUpdate(appCard: AppCard) {
        synchronized(lock) {
            if (!isAppCardActive(appCard.id)) {
                Log.e(TAG, "For app card update, app card must be active")
                return
            }

            latestAppCard[appCard.id] = appCard
            appCardIdComponentMap.put(appCard.id, getComponentMapFromAppCard(appCard))
        }
    }

    /**
     * Update app card component
     * - Component's tagged with [EnforceFastUpdateRate] will be updated before system is ready for
     *   a full app card update
     * - Otherwise, the component will be updated when the system is ready for a full update
     *
     * @param id id of app card being requested
     * @param component app card component that should be updated
     */
    fun sendAppCardComponentUpdate(id: String, component: Component) {
        synchronized(lock) {
            if (!isAppCardActive(id)) {
                Log.e(TAG, "For component updates, app card must be active")
                return
            }

            val latestAppCard = latestAppCard[id]
            val componentMap = appCardIdComponentMap[id] ?: return
            if (!componentMap.containsKey(component.componentId)) {
                Log.e(TAG, "For component updates, component must already exist inside app card")
                return
            }

            var supportedAppCard = true
            var updated: AppCard? = null
            if (latestAppCard is ImageAppCard) {
                latestAppCard.updateComponent(component)
                updated = latestAppCard
            } else {
                supportedAppCard = false
            }

            updated
                ?: run {
                    if (supportedAppCard) {
                        Log.e(TAG, "No matching component found")
                    } else {
                        Log.e(TAG, "Unsupported app card")
                    }
                    return
                }

            this.latestAppCard[updated.id] = updated
            appCardIdComponentMap[updated.id] = getComponentMapFromAppCard(updated)
            if (component.javaClass.isAnnotationPresent(EnforceFastUpdateRate::class.java)) {
                requestUpdate(updated.id, component.componentId)
            }
        }
    }

    protected abstract val appCardIds: List<String>

    /**
     * Handle cleanup for when an app card is removed
     *
     * @param id id of app card being removed
     */
    protected abstract fun onAppCardRemoved(id: String)

    /**
     * Handle updates to App Card's [AppCardContext]
     *
     * @param id app card ID whose context is being update
     * @param appCardContext updated context
     */
    protected abstract fun onAppCardContextChanged(id: String, appCardContext: AppCardContext)

    /** Request system to ask for an update for app card or app card component with given IDs */
    private fun requestUpdate(id: String, componentId: String) {
        val context =
            context
                ?: run {
                    Log.e(TAG, "Unable to get content resolver since context is null")
                    return
                }

        val uri =
            Uri.Builder()
                .scheme(ContentResolver.SCHEME_CONTENT)
                .authority(authority)
                .appendPath(id)
                .appendPath(componentId)
                .build()

        val observer = null
        context.contentResolver.notifyChange(uri, observer)
    }

    final override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?,
    ): Cursor? = null

    final override fun getType(uri: Uri): String? = TYPE

    final override fun insert(uri: Uri, contentValues: ContentValues?): Uri? = null

    final override fun delete(uri: Uri, s: String?, strings: Array<String>?): Int = 0

    final override fun update(
        uri: Uri,
        contentValues: ContentValues?,
        s: String?,
        strings: Array<String>?,
    ): Int = 0

    final override fun query(
        uri: Uri,
        projection: Array<String>?,
        queryArgs: Bundle?,
        cancellationSignal: CancellationSignal?,
    ): Cursor? {
        synchronized(lock) {
            var id: String? = null
            var appCardContext: AppCardContext? = null

            queryArgs?.let {
                val defaultValue = null
                id = it.getString(BUNDLE_KEY_APP_CARD_ID, defaultValue)
                val appCardContextBundle = it.getBundle(BUNDLE_KEY_APP_CARD_CONTEXT)
                appCardContext = fromBundle(appCardContextBundle)
            }

            return when (val method = getMethod(uri)) {
                AppCardMessageConstants.MSG_SEND_ALL_APP_CARDS ->
                    handleSendAllAppCards(appCardContext)

                AppCardMessageConstants.MSG_APP_CARD_UPDATE -> handleAppCardUpdate(id)

                AppCardMessageConstants.MSG_APP_CARD_ADDED -> handleAppCardAdded(id, appCardContext)

                else -> {
                    Log.e(TAG, "Unrecognized method: $method")
                    null
                }
            }
        }
    }

    private fun handleSendAllAppCards(appCardContext: AppCardContext?): Cursor? {
        appCardContext
            ?: run {
                Log.e(TAG, "App Card Context must exist")
                return null
            }

        val cursor = MatrixCursor(arrayOf(CURSOR_COLUMN_APP_CARD_TRANSPORT))

        for (appCardId in appCardIds) {
            cursor.addRow(
                arrayOf(
                    ParcelableUtils.parcelableToBytes(
                        AppCardTransport(getAppCard(appCardId, appCardContext))
                    )
                )
            )
        }

        return cursor
    }

    private fun handleAppCardUpdate(id: String?): Cursor? {
        id
            ?: run {
                Log.e(TAG, "App Card ID must exist")
                return null
            }

        val appCard =
            latestAppCard[id]
                ?: run {
                    Log.e(TAG, "App Card ID must be active")
                    return null
                }

        val cursor = MatrixCursor(arrayOf(CURSOR_COLUMN_APP_CARD_TRANSPORT))

        cursor.addRow(arrayOf(ParcelableUtils.parcelableToBytes(AppCardTransport(appCard))))

        return cursor
    }

    private fun handleAppCardAdded(id: String?, appCardContext: AppCardContext?): Cursor? {
        id
            ?: run {
                Log.e(TAG, "App Card ID must exist")
                return null
            }

        appCardContext
            ?: run {
                Log.e(TAG, "App Card Context must exist")
                return null
            }

        val appCard = getAppCard(id, appCardContext)
        val cursor = MatrixCursor(arrayOf(CURSOR_COLUMN_APP_CARD_TRANSPORT))

        cursor.addRow(arrayOf(ParcelableUtils.parcelableToBytes(AppCardTransport(appCard))))

        return cursor
    }

    private fun getMethod(uri: Uri) = uri.pathSegments[0]

    final override fun call(method: String, arg: String?, extras: Bundle?): Bundle? {
        synchronized(lock) {
            var id: String? = null
            var componentId: String? = null
            var interactionId: String? = null
            var appCardContext: AppCardContext? = null

            extras?.let {
                val defaultValue = null
                id = it.getString(BUNDLE_KEY_APP_CARD_ID, defaultValue)
                componentId = it.getString(BUNDLE_KEY_APP_CARD_COMPONENT_ID, defaultValue)
                interactionId = it.getString(BUNDLE_KEY_APP_CARD_INTERACTION_ID, defaultValue)
                val appCardContextBundle = it.getBundle(BUNDLE_KEY_APP_CARD_CONTEXT)
                appCardContext = fromBundle(appCardContextBundle)
            }

            var bundle: Bundle? = null
            when (method) {
                AppCardMessageConstants.MSG_APP_CARD_COMPONENT_UPDATE ->
                    bundle = handleAppCardComponentUpdate(id, componentId)

                AppCardMessageConstants.MSG_APP_CARD_REMOVED -> removeAppCard(id)

                AppCardMessageConstants.MSG_APP_CARD_INTERACTION ->
                    handleInteraction(id, componentId, interactionId)

                AppCardMessageConstants.MSG_APP_CARD_CONTEXT_UPDATE ->
                    handleAppCardContextUpdate(id, appCardContext)

                AppCardMessageConstants.MSG_CLOSE_PROVIDER -> handleClose()

                else -> Log.e(TAG, "Unrecognized method: $method")
            }

            return bundle
        }
    }

    private fun handleAppCardComponentUpdate(id: String?, componentId: String?): Bundle? {
        id
            ?: run {
                Log.e(TAG, "App Card ID must exist")
                return null
            }

        componentId
            ?: run {
                Log.e(TAG, "App Card Component ID must exist")
                return null
            }

        val componentMap =
            appCardIdComponentMap[id]
                ?: run {
                    Log.e(TAG, "App Card ID must exist in component map")
                    return null
                }

        val component =
            componentMap[componentId]
                ?: run {
                    Log.e(TAG, "App Card Component ID must exist in component map")
                    return null
                }

        val bundle = Bundle()
        bundle.putParcelable(BUNDLE_KEY_APP_CARD_COMPONENT, AppCardTransport(component))

        return bundle
    }

    private fun handleInteraction(id: String?, componentId: String?, interactionId: String?) {
        id
            ?: run {
                Log.e(TAG, "App Card ID must exist")
                return
            }

        componentId
            ?: run {
                Log.e(TAG, "App Card Component ID must exist")
                return
            }

        interactionId
            ?: run {
                Log.e(TAG, "App Card Component's interaction ID must exist")
                return
            }

        val componentInteractionMap =
            appCardIdComponentMap[id]
                ?: run {
                    Log.e(TAG, "App Card ID is not active: $id")
                    return
                }

        val component =
            componentInteractionMap[componentId]
                ?: run {
                    Log.e(
                        TAG,
                        "Component ID ($componentId) does not exist in app card with ID: $id",
                    )
                    return
                }

        if (component !is Button) {
            Log.e(
                TAG,
                "Component ID (" +
                    componentId +
                    ") in app card with ID (" +
                    id +
                    ") is not interactable: " +
                    interactionId,
            )
            return
        }

        if (InteractionMessageConstants.MSG_INTERACTION_ON_CLICK == interactionId) {
            component.onClickListener?.onClick()
        } else {
            Log.e(
                TAG,
                "Component ID (" +
                    componentId +
                    ") in app card with ID (" +
                    id +
                    ") does not contain interaction: " +
                    interactionId,
            )
        }
    }

    private fun handleClose() {
        if (!activeAppCardCountMap.isEmpty()) return
        if (dispatcher.getDesiredState() == Lifecycle.Event.ON_CREATE) dispatcher.queueOnStart()
        if (dispatcher.getDesiredState() == Lifecycle.Event.ON_START) dispatcher.queueOnResume()
        if (dispatcher.getDesiredState() == Lifecycle.Event.ON_RESUME) dispatcher.queueOnPause()
        dispatcher.queueOnStop()
        dispatcher.queueOnDestroy()
    }

    private fun handleAppCardContextUpdate(id: String?, appCardContext: AppCardContext?) {
        id
            ?: run {
                Log.e(TAG, "App Card ID must exist")
                return
            }

        appCardContext
            ?: run {
                Log.e(TAG, "App Card Context must exist")
                return
            }

        onAppCardContextChanged(id, appCardContext)
    }

    private fun getAppCard(id: String, appCardContext: AppCardContext): AppCard {
        val appCard =
            latestAppCard[id]
                ?: run {
                    val temp = onAppCardAdded(id, appCardContext)
                    check(value = temp.id == id) {
                        "Requested app card did not contain required ID"
                    }
                    temp
                }

        if (!appCard.verifyUniquenessOfComponentIds()) {
            Log.e(TAG, "App cards must contain unique component IDs")
            // Do not throw error here since this will be verified and dropped on system side
            // and a call to {@link AppCardContentProvider#onAppCardRemoved(String)} will be called
            return appCard
        }

        latestAppCard[appCard.id] = appCard
        activeAppCardCountMap.compute(id) { _, count ->
            val newCount = (count ?: 0) + 1
            if (newCount == 1 && dispatcher.getDesiredState() != Lifecycle.Event.ON_RESUME) {
                if (dispatcher.getDesiredState() != Lifecycle.Event.ON_START) {
                    dispatcher.queueOnStart()
                }
                dispatcher.queueOnResume()
            }
            newCount
        }
        appCardIdComponentMap[id] = getComponentMapFromAppCard(appCard)

        return appCard
    }

    private fun removeAppCard(id: String?) {
        id
            ?: run {
                Log.e(TAG, "App Card ID must exist")
                return
            }

        var removed = false
        var wasInactive = false
        activeAppCardCountMap.compute(id) { _, count ->
            when (count) {
                null,
                0 -> {
                    wasInactive = true
                    null
                }
                1 -> {
                    removed = true
                    null
                }
                else -> count - 1
            }
        }

        if (wasInactive) {
            Log.e(TAG, "App card remove requested for an inactive app card")
            return
        }

        if (removed) {
            appCardIdComponentMap.remove(id)
            latestAppCard.remove(id)
            onAppCardRemoved(id)

            if (
                activeAppCardCountMap.isEmpty() &&
                    dispatcher.getDesiredState() != Lifecycle.Event.ON_STOP
            ) {
                if (dispatcher.getDesiredState() != Lifecycle.Event.ON_PAUSE) {
                    dispatcher.queueOnPause()
                }
                dispatcher.queueOnStop()
            }
        }
    }

    private fun getComponentMapFromAppCard(appCard: AppCard?): ConcurrentMap<String, Component> {
        val result: ConcurrentMap<String, Component> = ConcurrentHashMap()

        if (appCard is ImageAppCard) {
            appCard.image?.let { result[it.componentId] = it }

            for (button in appCard.buttons) {
                result[button.componentId] = button
            }

            appCard.header?.let { result[it.componentId] = it }

            appCard.progressBar?.let { result[it.componentId] = it }
        }

        return result
    }

    private fun isAppCardActive(id: String): Boolean {
        return appCardIdComponentMap.containsKey(id) && latestAppCard.containsKey(id)
    }

    companion object {
        /** Bundle key for app card ID */
        const val BUNDLE_KEY_APP_CARD_ID = "appCardId"

        /** Bundle key for app card component ID */
        const val BUNDLE_KEY_APP_CARD_COMPONENT_ID = "appCardComponentId"

        /** Bundle key for app card component's interaction ID */
        const val BUNDLE_KEY_APP_CARD_INTERACTION_ID = "appCardInteractionId"

        /** Bundle key for app card context bundle */
        const val BUNDLE_KEY_APP_CARD_CONTEXT = "appCardContext"

        /** Bundle key for app card component */
        const val BUNDLE_KEY_APP_CARD_COMPONENT = "appCardComponent"

        /** Cursor column name for app card transport */
        const val CURSOR_COLUMN_APP_CARD_TRANSPORT = "appCardTransport"

        private const val TAG = "AppCardContentProvider"
        private const val TYPE = "android.car.appcard"
    }
}
