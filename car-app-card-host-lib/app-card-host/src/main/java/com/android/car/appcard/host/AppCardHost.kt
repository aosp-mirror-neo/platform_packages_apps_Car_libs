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

import android.app.ActivityManager
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.content.pm.ProviderInfo
import android.os.Build
import android.os.Bundle
import android.os.DeadObjectException
import android.os.UserHandle
import android.util.Log
import androidx.annotation.VisibleForTesting
import com.android.car.appcard.AppCard
import com.android.car.appcard.AppCardContentProvider
import com.android.car.appcard.AppCardContext
import com.android.car.appcard.AppCardMessageConstants
import com.android.car.appcard.host.AppCardObserver.AppCardObserverCallback
import com.android.car.appcard.host.AppCardTimer.UpdateReadyListener
import com.android.car.appcard.internal.AppCardTransport
import com.google.common.util.concurrent.FutureCallback
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.MoreExecutors
import com.google.protobuf.InvalidProtocolBufferException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentMap
import java.util.concurrent.Executor
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.function.Consumer

class AppCardHost
internal constructor(
    c: Context,
    private val updateRateMs: Int,
    private val fastUpdateRateMs: Int,
    private val responseExecutor: Executor,
    private val timerProvider: AppCardTimerProvider,
    private val userProvider: UserProvider,
) : UpdateReadyListener, AppCardObserverCallback {

    constructor(
        c: Context,
        updateRate: Int,
        fastUpdateRate: Int,
        responseExecutor: Executor,
    ) : this(
        c,
        updateRate,
        fastUpdateRate,
        responseExecutor,
        object : AppCardTimerProvider {
            override fun getNewTimer(
                listener: UpdateReadyListener,
                updateRateMs: Int,
                fastUpdateRateMs: Int,
                scheduledExecutorService: ScheduledExecutorService,
            ) = AppCardTimer(listener, updateRateMs, fastUpdateRateMs, scheduledExecutorService)
        },
        object : UserProvider {
            override fun getCurrentUser() = ActivityManager.getCurrentUser()
        },
    )

    private val listeners: MutableSet<AppCardListener>
    private val idBrokerMap: ConcurrentMap<ApplicationIdentifier, BrokerWrapper>
    private val executorService = MoreExecutors.listeningDecorator(Executors.newWorkStealingPool())
    private val scheduledExecutorService = Executors.newSingleThreadScheduledExecutor()
    private val packageManager: PackageManager
    private val contentResolver: ContentResolver
    private val brokerFactory: BrokerFactory
    private var context: Context
    private var currentUser = userProvider.getCurrentUser()

    @VisibleForTesting internal var appChangeReceiver: AppChangeReceiver? = null

    @VisibleForTesting internal var userChangeReceiver: UserChangeReceiver? = null

    init {
        context = createUserContext(c, currentUser)
        packageManager = context.packageManager
        contentResolver = context.contentResolver

        listeners = HashSet()
        idBrokerMap = ConcurrentHashMap()

        val expectedBrokerPermission =
            context.resources.getString(com.android.car.appcard.R.string.host_permission)
        brokerFactory =
            ContentProviderBrokerFactory(
                context,
                contentResolver,
                packageManager,
                expectedBrokerPermission,
                callback = this,
            )
        brokerFactory.connectToAllCompatibleApplications().forEach {
            idBrokerMap[it.key] = BrokerWrapper(it.value, it.key)
        }

        setupAppChangeReceiver()
        setupUserChangeReceiver()
    }

    private fun setupAppChangeReceiver() {
        val filter = IntentFilter()
        filter.apply {
            addAction(Intent.ACTION_PACKAGE_ADDED)
            addAction(Intent.ACTION_PACKAGE_REMOVED)
            addAction(Intent.ACTION_PACKAGE_CHANGED)
            addDataScheme(DATA_SCHEME_PACKAGE)
        }

        appChangeReceiver = AppChangeReceiver(currentUser)

        val broadcastPermission = null
        val scheduler = null
        context.registerReceiverForAllUsers(
            appChangeReceiver,
            filter,
            broadcastPermission,
            scheduler,
        )
    }

    private fun setupUserChangeReceiver() {
        val filter = IntentFilter()
        filter.addAction(Intent.ACTION_USER_FOREGROUND)

        userChangeReceiver = UserChangeReceiver()

        val broadcastPermission = null
        val scheduler = null
        context.registerReceiverForAllUsers(
            userChangeReceiver,
            filter,
            broadcastPermission,
            scheduler,
        )
    }

    /** Add any new added [AppCardBroker] */
    fun refreshCompatibleApplication() {
        synchronized(idBrokerMap) {
            brokerFactory.refreshCompatibleApplication(idBrokerMap.keys).forEach {
                idBrokerMap[it.key] = BrokerWrapper(it.value, it.key)
            }
        }
    }

    private fun closeAllApplicationConnections() {
        synchronized(idBrokerMap) {
            idBrokerMap.values.forEach(
                Consumer { brokerWrapper: BrokerWrapper ->
                    val appCardIds = brokerWrapper.activeAppCardMap.values.map { it.appCard.id }

                    appCardIds.forEach {
                        notifyAppCardRemovedInternal(it, brokerWrapper.identifier, brokerWrapper)
                    }

                    brokerWrapper.broker.close()
                }
            )

            idBrokerMap.clear()
        }
    }

    /** Cleanup all connections and internal members */
    fun destroy() {
        synchronized(idBrokerMap) {
            context.unregisterReceiver(appChangeReceiver)
            context.unregisterReceiver(userChangeReceiver)

            synchronized(listeners) { listeners.clear() }

            closeAllApplicationConnections()
            scheduledExecutorService.shutdownNow()
        }
    }

    /** Register an app card listener */
    fun registerListener(listener: AppCardListener) {
        synchronized(listeners) { listeners.add(listener) }
    }

    /** Unregister an app card listener */
    fun unregisterListener(listener: AppCardListener) {
        synchronized(listeners) { listeners.remove(listener) }
    }

    /**
     * Request all available app cards from all bound
     * [com.android.car.appcard.AppCardContentProvider]s
     */
    fun getAllAppCards(context: AppCardContext) {
        synchronized(idBrokerMap) {
            idBrokerMap.values.forEach(
                Consumer { brokerWrapper: BrokerWrapper ->
                    val broker = brokerWrapper.broker
                    val identifier = brokerWrapper.identifier
                    val activeAppCardMap = brokerWrapper.activeAppCardMap

                    val listenableFuture =
                        executorService.submit<List<AppCardContainer>> {
                            val bundle = Bundle()
                            bundle.putBundle(
                                AppCardContentProvider.BUNDLE_KEY_APP_CARD_CONTEXT,
                                context.toBundle(),
                            )

                            val appCardTransports =
                                try {
                                    broker.getAppCardTransports(
                                        identifier,
                                        bundle,
                                        AppCardMessageConstants.MSG_SEND_ALL_APP_CARDS,
                                    )
                                } catch (exception: Exception) {
                                    if (
                                        exception
                                            is ContentProviderBroker.ContentProviderBrokerException
                                    ) {
                                        if (exception.cause is DeadObjectException) {
                                            handleInvalidBroker(identifier)
                                        }
                                    }

                                    throw exception
                                }

                            getHostAppCardsFromTransport(appCardTransports, identifier)
                        }

                    Futures.addCallback(
                        listenableFuture,
                        getAllAppCardsFutureCallBack(activeAppCardMap, identifier),
                        responseExecutor,
                    )
                }
            )
        }
    }

    private fun getAllAppCardsFutureCallBack(
        activeAppCardMap: ConcurrentMap<String, ActiveAppCard>,
        identifier: ApplicationIdentifier,
    ): FutureCallback<List<AppCardContainer>> {
        return object : FutureCallback<List<AppCardContainer>> {
            override fun onSuccess(appCards: List<AppCardContainer>) {
                synchronized(listeners) {
                    listeners.forEach(
                        Consumer { appCardListener: AppCardListener ->
                            appCards.forEach { appCardContainer ->
                                appCardListener.onAppCardReceived(appCardContainer)

                                val appCard = appCardContainer.appCard
                                val appCardId = appCard.id
                                val activeCard = activeAppCardMap[appCardId]

                                activeCard
                                    ?.appCardTimer
                                    ?.updateAppCard(AppCardContainer(identifier, appCard))
                                    ?: setupCacheTimer(activeAppCardMap, identifier, appCard)
                            }
                        }
                    )
                }
            }

            override fun onFailure(throwable: Throwable) {
                Log.e(TAG, throwable.message, throwable)

                synchronized(listeners) {
                    listeners.forEach(
                        Consumer { appCardListener ->
                            appCardListener.onPackageCommunicationError(identifier, throwable)
                        }
                    )
                }
            }
        }
    }

    private fun handleInvalidBroker(identifier: ApplicationIdentifier) {
        synchronized(idBrokerMap) {
            val brokerWrapper =
                idBrokerMap[identifier]
                    ?: run {
                        Log.d(TAG, "Inactive content provider is invalid")
                        return
                    }

            val activeAppCards = mutableListOf<String>()
            brokerWrapper.broker.close()

            brokerWrapper.activeAppCardMap.values.forEach {
                activeAppCards.add(it.appCard.id)
                it.appCardTimer.destroy()
            }

            if (activeAppCards.isEmpty()) {
                Log.d(TAG, "Content provider with no active app cards has become invalid")
            } else {
                Log.d(
                    TAG,
                    "Content provider has become invalid, affected app card IDs: $activeAppCards",
                )
            }

            brokerWrapper.activeAppCardMap.clear()
            idBrokerMap.remove(identifier)
        }
    }

    /** Request an app card */
    fun requestAppCard(
        context: AppCardContext,
        identifier: ApplicationIdentifier,
        appCardId: String,
    ) = retrieveAppCard(AppCardMessageConstants.MSG_APP_CARD_ADDED, context, identifier, appCardId)

    /**
     * Notify [com.android.car.appcard.AppCardContentProvider] that its app card has been removed
     */
    fun notifyAppCardRemoved(identifier: ApplicationIdentifier, appCardId: String) {
        synchronized(idBrokerMap) {
            val brokerWrapper = idBrokerMap[identifier] ?: return

            notifyAppCardRemovedInternal(appCardId, identifier, brokerWrapper)
        }
    }

    private fun notifyAppCardRemovedInternal(
        appCardId: String,
        identifier: ApplicationIdentifier,
        brokerWrapper: BrokerWrapper,
    ) {
        synchronized(idBrokerMap) {
            val broker = brokerWrapper.broker

            val listenableFuture =
                executorService.submit<Boolean> {
                    val bundle = Bundle()
                    bundle.putString(AppCardContentProvider.BUNDLE_KEY_APP_CARD_ID, appCardId)

                    try {
                        broker.sendMessage(
                            AppCardMessageConstants.MSG_APP_CARD_REMOVED,
                            bundle,
                            appCardId,
                        )
                    } catch (exception: Exception) {
                        if (exception is ContentProviderBroker.ContentProviderBrokerException) {
                            if (exception.cause is DeadObjectException) {
                                handleInvalidBroker(identifier)
                            }
                        }

                        throw exception
                    }

                    true
                }

            Futures.addCallback(
                listenableFuture,
                object : FutureCallback<Boolean> {
                    override fun onSuccess(aBoolean: Boolean) {
                        // no-op
                    }

                    override fun onFailure(throwable: Throwable) {
                        Log.e(TAG, throwable.message, throwable)

                        synchronized(listeners) {
                            listeners.forEach(
                                Consumer { appCardListener: AppCardListener ->
                                    appCardListener.onPackageCommunicationError(
                                        identifier,
                                        throwable,
                                    )
                                }
                            )
                        }
                    }
                },
                responseExecutor,
            )

            val cache = brokerWrapper.activeAppCardMap[appCardId]
            cache?.appCardTimer?.destroy()
            brokerWrapper.activeAppCardMap.remove(appCardId)
        }
    }

    /**
     * Notify [com.android.car.appcard.AppCardContentProvider] that a component inside an app card
     * has been interacted with
     */
    fun notifyAppCardInteraction(
        identifier: ApplicationIdentifier,
        appCardId: String,
        componentId: String,
        interactionType: String,
    ) {
        synchronized(idBrokerMap) {
            val brokerWrapper = idBrokerMap[identifier] ?: return
            val broker = brokerWrapper.broker
            val timer = brokerWrapper.activeAppCardMap[appCardId]?.appCardTimer

            val listenableFuture =
                executorService.submit<Boolean> {
                    val bundle = Bundle()
                    bundle.apply {
                        putString(AppCardContentProvider.BUNDLE_KEY_APP_CARD_ID, appCardId)
                        putString(
                            AppCardContentProvider.BUNDLE_KEY_APP_CARD_COMPONENT_ID,
                            componentId,
                        )
                        putString(
                            AppCardContentProvider.BUNDLE_KEY_APP_CARD_INTERACTION_ID,
                            interactionType,
                        )
                    }

                    try {
                        broker.sendMessage(
                            AppCardMessageConstants.MSG_APP_CARD_INTERACTION,
                            bundle,
                            errorId = "$appCardId#$componentId#$interactionType",
                        )
                    } catch (exception: Exception) {
                        if (exception is ContentProviderBroker.ContentProviderBrokerException) {
                            if (exception.cause is DeadObjectException) {
                                handleInvalidBroker(identifier)
                            }
                        }

                        throw exception
                    }

                    true
                }
            Futures.addCallback(
                listenableFuture,
                object : FutureCallback<Boolean> {
                    override fun onSuccess(result: Boolean) {
                        timer?.resetAppCardTimerAndRequestUpdate()
                    }

                    override fun onFailure(throwable: Throwable) {
                        Log.e(TAG, throwable.message, throwable)

                        synchronized(listeners) {
                            listeners.forEach(
                                Consumer { appCardListener: AppCardListener ->
                                    appCardListener.onPackageCommunicationError(
                                        identifier,
                                        throwable,
                                    )
                                }
                            )
                        }
                    }
                },
                responseExecutor,
            )
        }
    }

    /** Send an [AppCardContext] update for a specific app card */
    fun sendAppCardContextUpdate(
        appCardContext: AppCardContext,
        identifier: ApplicationIdentifier,
        appCardId: String,
    ) {
        synchronized(idBrokerMap) {
            val brokerWrapper = idBrokerMap[identifier] ?: return
            val broker = brokerWrapper.broker
            val timer = brokerWrapper.activeAppCardMap[appCardId]?.appCardTimer

            val listenableFuture =
                executorService.submit<Boolean> {
                    val bundle = Bundle()
                    bundle.apply {
                        putString(AppCardContentProvider.BUNDLE_KEY_APP_CARD_ID, appCardId)
                        putBundle(
                            AppCardContentProvider.BUNDLE_KEY_APP_CARD_CONTEXT,
                            appCardContext.toBundle(),
                        )
                    }

                    try {
                        broker.sendMessage(
                            AppCardMessageConstants.MSG_APP_CARD_CONTEXT_UPDATE,
                            bundle,
                            appCardId,
                        )
                    } catch (exception: Exception) {
                        if (exception is ContentProviderBroker.ContentProviderBrokerException) {
                            if (exception.cause is DeadObjectException) {
                                handleInvalidBroker(identifier)
                            }
                        }

                        throw exception
                    }

                    true
                }
            Futures.addCallback(
                listenableFuture,
                object : FutureCallback<Boolean> {
                    override fun onSuccess(result: Boolean) {
                        timer?.resetAppCardTimerAndRequestUpdate()
                    }

                    override fun onFailure(throwable: Throwable) {
                        Log.e(TAG, throwable.message, throwable)

                        synchronized(listeners) {
                            listeners.forEach(
                                Consumer { appCardListener: AppCardListener ->
                                    appCardListener.onPackageCommunicationError(
                                        identifier,
                                        throwable,
                                    )
                                }
                            )
                        }
                    }
                },
                responseExecutor,
            )
        }
    }

    private fun retrieveAppCard(
        eventName: String,
        context: AppCardContext?,
        identifier: ApplicationIdentifier,
        appCardId: String,
    ) {
        synchronized(idBrokerMap) {
            val brokerWrapper = idBrokerMap[identifier] ?: return
            val broker = brokerWrapper.broker
            val activeAppCardMap = brokerWrapper.activeAppCardMap

            val listenableFuture =
                executorService.submit<AppCardContainer> {
                    val bundle = Bundle()
                    bundle.putString(AppCardContentProvider.BUNDLE_KEY_APP_CARD_ID, appCardId)

                    if (AppCardMessageConstants.MSG_APP_CARD_ADDED == eventName) {
                        context?.also {
                            bundle.putBundle(
                                AppCardContentProvider.BUNDLE_KEY_APP_CARD_CONTEXT,
                                context.toBundle(),
                            )
                        }
                            ?: throw AppCardHostException(
                                eventName,
                                appCardId,
                                IllegalStateException(
                                    "Null AppCardContext for " +
                                        AppCardMessageConstants.MSG_APP_CARD_ADDED
                                ),
                            )
                    }

                    val appCardTransport =
                        try {
                            broker.getAppCardTransport(identifier, appCardId, bundle, eventName)
                        } catch (exception: Exception) {
                            if (exception is ContentProviderBroker.ContentProviderBrokerException) {
                                if (exception.cause is DeadObjectException) {
                                    handleInvalidBroker(identifier)
                                }
                            }

                            throw exception
                        }

                    val appCard =
                        appCardTransport.appCard
                            ?: throw AppCardHostException(
                                eventName,
                                appCardId,
                                IllegalStateException("App card missing from transport"),
                            )

                    AppCardContainer(identifier, appCard)
                }

            Futures.addCallback(
                listenableFuture,
                getRetrieveAppCardFutureCallback(
                    eventName,
                    activeAppCardMap,
                    identifier,
                    appCardId,
                ),
                responseExecutor,
            )
        }
    }

    private fun getRetrieveAppCardFutureCallback(
        eventName: String,
        activeAppCardMap: ConcurrentMap<String, ActiveAppCard>,
        identifier: ApplicationIdentifier,
        appCardId: String,
    ): FutureCallback<AppCardContainer> {
        return object : FutureCallback<AppCardContainer> {
            override fun onSuccess(appCardContainer: AppCardContainer) {
                val appCard = appCardContainer.appCard
                val validAppCard = appCard.verifyUniquenessOfComponentIds()
                if (!validAppCard) {
                    if (AppCardMessageConstants.MSG_APP_CARD_ADDED == eventName) {
                        notifyAppCardRemoved(identifier, appCardId)
                    }
                    return
                }

                synchronized(idBrokerMap) {
                    activeAppCardMap[appCardId]
                        ?.appCardTimer
                        ?.updateAppCard(AppCardContainer(identifier, appCard))
                        ?: setupCacheTimer(activeAppCardMap, identifier, appCard)
                }

                synchronized(listeners) {
                    listeners.forEach(
                        Consumer { appCardListener: AppCardListener ->
                            appCardListener.onAppCardReceived(appCardContainer)
                        }
                    )
                }
            }

            override fun onFailure(throwable: Throwable) {
                Log.e(TAG, throwable.message, throwable)

                synchronized(listeners) {
                    listeners.forEach(
                        Consumer { appCardListener: AppCardListener ->
                            appCardListener.onPackageCommunicationError(identifier, throwable)
                        }
                    )
                }
            }
        }
    }

    private fun setupCacheTimer(
        activeAppCardMap: ConcurrentMap<String, ActiveAppCard>,
        identifier: ApplicationIdentifier,
        appCard: AppCard,
    ) {
        synchronized(idBrokerMap) {
            val timer =
                timerProvider.getNewTimer(
                    listener = this,
                    updateRateMs = updateRateMs,
                    fastUpdateRateMs = fastUpdateRateMs,
                    scheduledExecutorService = scheduledExecutorService,
                )
            timer.updateAppCard(AppCardContainer(identifier, appCard))
            activeAppCardMap.put(appCard.id, ActiveAppCard(appCard, timer))
        }
    }

    private fun retrieveAppCardComponentUpdate(
        brokerWrapper: BrokerWrapper,
        activeAppCard: ActiveAppCard,
        identifier: ApplicationIdentifier,
        appCardId: String,
        componentId: String,
    ) {
        synchronized(idBrokerMap) {
            val broker = brokerWrapper.broker
            val timer = activeAppCard.appCardTimer
            val listenableFuture =
                executorService.submit<AppCardComponentContainer> {
                    val bundle = Bundle()
                    bundle.apply {
                        putString(AppCardContentProvider.BUNDLE_KEY_APP_CARD_ID, appCardId)
                        putString(
                            AppCardContentProvider.BUNDLE_KEY_APP_CARD_COMPONENT_ID,
                            componentId,
                        )
                    }

                    val appCardTransport =
                        try {
                            broker.getAppCardTransport(
                                identifier,
                                errorId = "$appCardId#$componentId",
                                bundle,
                                AppCardMessageConstants.MSG_APP_CARD_COMPONENT_UPDATE,
                            )
                        } catch (exception: Exception) {
                            if (exception is ContentProviderBroker.ContentProviderBrokerException) {
                                if (exception.cause is DeadObjectException) {
                                    handleInvalidBroker(identifier)
                                }
                            }

                            throw exception
                        }

                    val component =
                        appCardTransport.component
                            ?: throw AppCardHostException(
                                AppCardMessageConstants.MSG_APP_CARD_COMPONENT_UPDATE,
                                id = "$appCardId#$componentId",
                                IllegalStateException("App card component missing from transport"),
                            )
                    timer.componentUpdate(component.componentId)

                    AppCardComponentContainer(identifier, appCardId, component)
                }

            Futures.addCallback(
                listenableFuture,
                object : FutureCallback<AppCardComponentContainer> {
                    override fun onSuccess(appCardComponentContainer: AppCardComponentContainer) {
                        synchronized(listeners) {
                            listeners.forEach(
                                Consumer { appCardListener: AppCardListener ->
                                    appCardListener.onComponentReceived(appCardComponentContainer)
                                }
                            )
                        }
                    }

                    override fun onFailure(throwable: Throwable) {
                        Log.e(TAG, throwable.message, throwable)

                        synchronized(listeners) {
                            listeners.forEach(
                                Consumer { appCardListener: AppCardListener ->
                                    appCardListener.onPackageCommunicationError(
                                        identifier,
                                        throwable,
                                    )
                                }
                            )
                        }
                    }
                },
                responseExecutor,
            )
        }
    }

    private fun createUserContext(context: Context, userId: Int): Context {
        val flags = 0
        return context.createContextAsUser(UserHandle.of(userId), flags)
    }

    @VisibleForTesting
    internal fun handlePackageAddedOrEnabled(packageName: String, authority: String?) {
        synchronized(idBrokerMap) {
            brokerFactory.refreshCompatibleApplication(idBrokerMap.keys).forEach {
                idBrokerMap[it.key] = BrokerWrapper(it.value, it.key)
            }

            synchronized(listeners) {
                listeners.forEach(
                    Consumer { appCardListener: AppCardListener ->
                        appCardListener.onProviderAdded(packageName, authority)
                    }
                )
            }
        }
    }

    @VisibleForTesting
    internal fun handlePackageRemovedOrDisabled(packageName: String, authority: String?) {
        synchronized(idBrokerMap) {
            var isRemoved = false
            idBrokerMap.entries.removeIf { (key, value) ->
                authority?.let { if (!key.containsAuthority(it)) return@removeIf false }

                if (!key.containsPackage(packageName)) return@removeIf false

                value.broker.close()
                value.activeAppCardMap.clear()
                isRemoved = true

                true
            }

            if (!isRemoved) return

            synchronized(listeners) {
                listeners.forEach(
                    Consumer { appCardListener: AppCardListener ->
                        appCardListener.onProviderRemoved(packageName, authority)
                    }
                )
            }
        }
    }

    @VisibleForTesting
    internal fun handlePackageChanged(changedPackageName: String, componentNames: Array<String>) {
        for (name in componentNames) {
            if (name == changedPackageName) {
                val enabled = isPackageEnabled(name)
                handlePackageStateChange(enabled, changedPackageName)
            } else {
                getApplicationIdentifier(changedPackageName, name)?.let {
                    val enabled = isComponentEnabled(changedPackageName, name)
                    handlePackageStateChange(enabled, changedPackageName, it.authority)
                }
            }
        }
    }

    private fun handlePackageStateChange(
        isEnabled: Boolean,
        packageName: String,
        authority: String? = null,
    ) {
        if (isEnabled) {
            handlePackageAddedOrEnabled(packageName, authority)
        } else {
            handlePackageRemovedOrDisabled(packageName, authority)
        }
    }

    private fun getApplicationIdentifier(pkgName: String, clsName: String): ApplicationIdentifier? {
        val providers = getProviderInfos(pkgName)

        for (componentInfo in providers) {
            if (componentInfo.name == clsName) {
                return ApplicationIdentifier(componentInfo.packageName, componentInfo.authority)
            }
        }

        // The component is not declared in the app's manifest
        return null
    }

    private fun isPackageEnabled(pkgName: String): Boolean {
        return try {
            val flags = 0
            val appInfo = packageManager.getApplicationInfo(pkgName, flags)
            appInfo.enabled
        } catch (e: PackageManager.NameNotFoundException) {
            false
        }
    }

    private fun isComponentEnabled(pkgName: String, clsName: String): Boolean {
        val componentName = ComponentName(pkgName, clsName)

        return when (packageManager.getComponentEnabledSetting(componentName)) {
            PackageManager.COMPONENT_ENABLED_STATE_DISABLED -> false

            PackageManager.COMPONENT_ENABLED_STATE_ENABLED -> true

            PackageManager.COMPONENT_ENABLED_STATE_DEFAULT ->
                getComponentDefaultState(pkgName, clsName)

            else -> getComponentDefaultState(pkgName, clsName)
        }
    }

    private fun getComponentDefaultState(pkgName: String, clsName: String): Boolean {
        val providers = getProviderInfos(pkgName)

        for (componentInfo in providers) {
            if (componentInfo.name == clsName) return componentInfo.isEnabled
        }

        // The component is not declared in the app's manifest
        return false
    }

    private fun getProviderInfos(pkgName: String): Array<ProviderInfo> {
        return try {
            val flags = PackageManager.GET_PROVIDERS or PackageManager.MATCH_DISABLED_COMPONENTS
            val packageInfo = packageManager.getPackageInfo(pkgName, flags)
            packageInfo.providers ?: arrayOf()
        } catch (e: PackageManager.NameNotFoundException) {
            arrayOf()
        }
    }

    @VisibleForTesting
    internal fun handleUserChange() {
        synchronized(idBrokerMap) {
            closeAllApplicationConnections()
            currentUser = userProvider.getCurrentUser()
            idBrokerMap.clear()
            brokerFactory.connectToAllCompatibleApplications().forEach {
                idBrokerMap[it.key] = BrokerWrapper(it.value, it.key)
            }
        }
    }

    override fun appCardIsReadyForUpdate(identifier: ApplicationIdentifier?, appCardId: String?) {
        appCardId ?: return
        identifier ?: return

        retrieveAppCard(
            eventName = AppCardMessageConstants.MSG_APP_CARD_UPDATE,
            context = null,
            identifier = identifier,
            appCardId = appCardId,
        )
    }

    private class BrokerWrapper(var broker: AppCardBroker, var identifier: ApplicationIdentifier) {
        var activeAppCardMap: ConcurrentMap<String, ActiveAppCard> = ConcurrentHashMap()
    }

    private class ActiveAppCard(var appCard: AppCard, var appCardTimer: AppCardTimer)

    internal inner class AppChangeReceiver(private val userId: Int) : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            intent.data ?: return
            intent.action ?: return
            intent.extras ?: return

            val data = intent.data
            val changedPackageName = data?.schemeSpecificPart
            changedPackageName
                ?: run {
                    Log.d(TAG, "App change broadcast received without package name")
                    return
                }

            val extras = intent.extras
            val defaultValue = -1
            val uid = extras?.getInt(Intent.EXTRA_UID, defaultValue)
            if (uid == -1 || uid == null) {
                Log.d(TAG, "App change broadcast received without UID")
                return
            }

            val packageUserHandle = UserHandle.getUserHandleForUid(uid)

            if (packageUserHandle != UserHandle.of(this.userId)) return

            when (intent.action) {
                Intent.ACTION_PACKAGE_ADDED ->
                    handlePackageAddedOrEnabled(changedPackageName, authority = null)

                Intent.ACTION_PACKAGE_CHANGED -> {
                    val componentNames =
                        extras.getStringArray(Intent.EXTRA_CHANGED_COMPONENT_NAME_LIST)
                    if (componentNames.isNullOrEmpty()) {
                        Log.d(TAG, "App change broadcast received without component name list")
                        return
                    }

                    handlePackageChanged(changedPackageName, componentNames)
                }

                Intent.ACTION_PACKAGE_REMOVED ->
                    handlePackageRemovedOrDisabled(changedPackageName, authority = null)
            }
        }
    }

    internal inner class UserChangeReceiver : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == null || Intent.ACTION_USER_FOREGROUND != intent.action) return

            handleUserChange()
        }
    }

    override fun handleAppCardAppComponentRequest(
        authority: String,
        id: String,
        componentId: String,
    ) {
        synchronized(idBrokerMap) {
            val identifier =
                idBrokerMap.keys
                    .stream()
                    .filter { i: ApplicationIdentifier -> i.containsAuthority(authority) }
                    .findFirst()

            val isEmpty =
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    identifier.isEmpty
                } else {
                    !identifier.isPresent
                }
            if (isEmpty) return

            val brokerWrapper = idBrokerMap[identifier.get()] ?: return
            val cacheTimer = brokerWrapper.activeAppCardMap[id] ?: return

            if (!cacheTimer.appCardTimer.isComponentReadyForUpdate(componentId)) return

            retrieveAppCardComponentUpdate(
                brokerWrapper,
                cacheTimer,
                identifier.get(),
                id,
                componentId,
            )
        }
    }

    /** Internal interface to abstract out timers from tests */
    internal interface AppCardTimerProvider {
        fun getNewTimer(
            listener: UpdateReadyListener,
            updateRateMs: Int,
            fastUpdateRateMs: Int,
            scheduledExecutorService: ScheduledExecutorService,
        ): AppCardTimer
    }

    /** Internal interface to abstract out [ActivityManager.getCurrentUser] */
    internal interface UserProvider {
        fun getCurrentUser(): Int
    }

    class AppCardHostException(val method: String, val id: String?, cause: Throwable) :
        Exception(
            id?.let {
                "Exception occurred when calling (with $method) app card content provider (ID: $id)"
            } ?: "Exception occurred when calling (with $method) app card content provider",
            cause,
        )

    companion object {
        private const val TAG = "AppCardHost"
        private const val DATA_SCHEME_PACKAGE = "package"

        @Throws(AppCardHostException::class)
        private fun getHostAppCardsFromTransport(
            appCardTransports: List<AppCardTransport>,
            identifier: ApplicationIdentifier,
        ): List<AppCardContainer> {
            val appCards: MutableList<AppCardContainer> = ArrayList()
            for (appCardTransport in appCardTransports) {
                val appCard: AppCard? =
                    try {
                        appCardTransport.appCard
                    } catch (e: InvalidProtocolBufferException) {
                        throw AppCardHostException(
                            AppCardMessageConstants.MSG_SEND_ALL_APP_CARDS,
                            id = null,
                            e,
                        )
                    }

                appCard?.let { appCards.add(AppCardContainer(identifier, it)) }
            }
            return appCards
        }
    }
}
