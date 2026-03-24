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

import android.content.ComponentName
import android.content.ContentProviderClient
import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.content.pm.ProviderInfo
import android.content.pm.ResolveInfo
import android.content.res.Resources
import android.database.MatrixCursor
import android.net.Uri
import android.os.Bundle
import android.os.DeadObjectException
import android.os.RemoteException
import android.os.UserHandle
import android.util.Size
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.android.car.appcard.AppCardContentProvider
import com.android.car.appcard.AppCardContext
import com.android.car.appcard.AppCardMessageConstants
import com.android.car.appcard.host.ContentProviderBrokerFactory.Companion.APP_CARD_INTENT_ACTION
import com.android.car.appcard.internal.AppCardTransport
import com.android.car.appcard.util.ParcelableUtils
import com.google.common.truth.Truth.assertThat
import java.util.concurrent.Executor
import java.util.concurrent.Phaser
import java.util.concurrent.ScheduledExecutorService
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.eq
import org.mockito.kotlin.isNull
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.reset
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@RunWith(AndroidJUnit4::class)
class AppCardHostTest {
    private var actualAppCardContainers: ArrayList<AppCardContainer> = ArrayList()
    private var actualAppCardComponentContainer: AppCardComponentContainer? = null
    private var actualAddedPackageName: String? = null
    private var actualAddedAuthority: String? = null
    private var actualRemovedPackageName: String? = null
    private var actualRemovedAuthority: String? = null
    private var actualAppCardIdentifier: ApplicationIdentifier? = null
    private var actualThrowable: Throwable? = null
    private val updateReadyListener =
        object : AppCardListener {
            override fun onAppCardReceived(appCard: AppCardContainer) {
                actualAppCardContainers.add(appCard)
            }

            override fun onComponentReceived(component: AppCardComponentContainer) {
                actualAppCardComponentContainer = component
            }

            override fun onProviderRemoved(packageName: String, authority: String?) {
                actualRemovedPackageName = packageName
                actualRemovedAuthority = authority
            }

            override fun onProviderAdded(packageName: String, authority: String?) {
                actualAddedPackageName = packageName
                actualAddedAuthority = authority
            }

            override fun onPackageCommunicationError(
                identifier: ApplicationIdentifier,
                throwable: Throwable,
            ) {
                actualAppCardIdentifier = identifier
                actualThrowable = throwable
            }
        }
    private val applicationIdentifier = ApplicationIdentifier(TEST_PACKAGE, TEST_AUTHORITY)
    private val appCardContext =
        AppCardContext(1, 5000, 500, false, Size(3, 2), Size(2, 3), Size(2, 2), 3)
    private lateinit var phaser: Phaser
    private val componentName = ComponentName(TEST_PACKAGE, TEST_CLASS)
    private var responseExecutor = Executor { command ->
        command.run()
        phaser.arrive()
    }
    private val providerInfo =
        ProviderInfo().apply {
            name = TEST_CLASS
            authority = TEST_AUTHORITY
            packageName = TEST_PACKAGE
            readPermission = TEST_PERMISSION
            writePermission = TEST_PERMISSION
            grantUriPermissions = false
            forceUriPermissions = false
            uriPermissionPatterns = null
            pathPermissions = null
        }
    private val resolveInfo = ResolveInfo().also { it.providerInfo = providerInfo }
    private val packageManager =
        mock<PackageManager> {
            on(
                    it.queryIntentContentProviders(
                        any<Intent>(),
                        any<PackageManager.ResolveInfoFlags>(),
                    )
                )
                .thenReturn(listOf(resolveInfo))

            val packageInfo = PackageInfo()
            packageInfo.providers = arrayOf(providerInfo)
            val flags = PackageManager.GET_PROVIDERS or PackageManager.MATCH_DISABLED_COMPONENTS
            on(it.getPackageInfo(eq(TEST_PACKAGE), eq(flags))).thenReturn(packageInfo)
        }
    private val contentProviderClient = mock<ContentProviderClient>()
    private val contentResolver =
        mock<ContentResolver> {
            on(it.acquireUnstableContentProviderClient(eq(TEST_AUTHORITY)))
                .thenReturn(contentProviderClient)
        }
    private val resources =
        mock<Resources> { on(it.getString(any<Int>())).thenReturn(TEST_PERMISSION) }
    private val context =
        mock<Context> {
            on(it.createContextAsUser(any<UserHandle>(), any<Int>())).thenReturn(it)
            on(it.packageManager).thenReturn(packageManager)
            on(it.contentResolver).thenReturn(contentResolver)
            on(it.resources).thenReturn(resources)
        }
    private val appCardTimer = mock<AppCardTimer>()
    private var actualUpdateReadyListener: AppCardTimer.UpdateReadyListener? = null
    private var actualUpdateRateMs: Int? = null
    private var actualFastUpdateRateMs: Int? = null
    private val appCardTimerProvider =
        object : AppCardHost.AppCardTimerProvider {
            override fun getNewTimer(
                listener: AppCardTimer.UpdateReadyListener,
                updateRateMs: Int,
                fastUpdateRateMs: Int,
                scheduledExecutorService: ScheduledExecutorService,
            ): AppCardTimer {
                actualUpdateReadyListener = listener
                actualUpdateRateMs = updateRateMs
                actualFastUpdateRateMs = fastUpdateRateMs
                return appCardTimer
            }
        }
    private val userProvider =
        object : AppCardHost.UserProvider {
            override fun getCurrentUser() = 10
        }
    private lateinit var appCardHost: AppCardHost

    @Before
    fun setup() {
        phaser = Phaser(2)
        val intentCaptor = argumentCaptor<Intent>()
        val resolveInfoFlagCaptor = argumentCaptor<PackageManager.ResolveInfoFlags>()

        appCardHost =
            AppCardHost(
                context,
                TEST_UPDATE_RATE_MS,
                TEST_FAST_UPDATE_RATE_MS,
                responseExecutor,
                appCardTimerProvider,
                userProvider,
            )

        verify(packageManager)
            .queryIntentContentProviders(intentCaptor.capture(), resolveInfoFlagCaptor.capture())
        assertThat(intentCaptor.firstValue.action).isEqualTo(APP_CARD_INTENT_ACTION)
        assertThat(resolveInfoFlagCaptor.firstValue.value)
            .isEqualTo(PackageManager.MATCH_ALL.toLong())

        val uri =
            Uri.Builder().scheme(ContentResolver.SCHEME_CONTENT).authority(TEST_AUTHORITY).build()
        verify(contentResolver).registerContentObserver(eq(uri), eq(true), any<AppCardObserver>())

        appCardHost.registerListener(updateReadyListener)
    }

    @Test
    fun testInit_appChangeReceiver_registeredCorrectly() {
        val captor = argumentCaptor<IntentFilter>()
        verify(context)
            .registerReceiverForAllUsers(
                any<AppCardHost.AppChangeReceiver>(),
                captor.capture(),
                isNull(),
                isNull(),
            )
        var added = false
        var removed = false
        var changed = false
        captor.firstValue.actionsIterator().forEach { action ->
            run {
                when (action) {
                    Intent.ACTION_PACKAGE_ADDED -> added = true

                    Intent.ACTION_PACKAGE_REMOVED -> removed = true

                    Intent.ACTION_PACKAGE_CHANGED -> changed = true
                }
            }
        }

        assertThat(added && removed && changed).isTrue()
    }

    @Test
    fun testInit_userChangeReceiver_registeredCorrectly() {
        val captor = argumentCaptor<IntentFilter>()
        verify(context)
            .registerReceiverForAllUsers(
                any<AppCardHost.UserChangeReceiver>(),
                captor.capture(),
                isNull(),
                isNull(),
            )
        var foreGround = false
        captor.firstValue.actionsIterator().forEach { action ->
            run {
                when (action) {
                    Intent.ACTION_USER_FOREGROUND -> foreGround = true
                }
            }
        }

        assertThat(foreGround).isTrue()
    }

    @Test
    fun testHandlePackageAddedOrEnabled_packageAddedReceived() {
        appCardHost.handlePackageAddedOrEnabled(TEST_PACKAGE, authority = null)

        assertThat(actualAddedPackageName).isEqualTo(TEST_PACKAGE)
        assertThat(actualAddedAuthority).isNull()
    }

    @Test
    fun testHandlePackageAddedOrEnabled_contentProviderClientAcquired() {
        whenever(contentProviderClient.query(any<Uri>(), isNull(), any<Bundle>(), isNull()))
            .thenThrow(DeadObjectException())
        appCardHost.getAllAppCards(appCardContext)
        phaser.arriveAndAwaitAdvance()
        reset(contentResolver)

        appCardHost.handlePackageAddedOrEnabled(TEST_PACKAGE, authority = null)

        verify(contentResolver).acquireUnstableContentProviderClient(eq(TEST_AUTHORITY))
    }

    @Test
    fun testHandlePackageRemovedOrDisabled_existingPackage_packageRemovedReceived() {
        appCardHost.handlePackageRemovedOrDisabled(TEST_PACKAGE, authority = null)

        assertThat(actualRemovedPackageName).isEqualTo(TEST_PACKAGE)
        assertThat(actualRemovedAuthority).isNull()
    }

    @Test
    fun testHandlePackageRemovedOrDisabled_existingPackage_contentProviderClosed() {
        appCardHost.handlePackageRemovedOrDisabled(TEST_PACKAGE, authority = null)

        verify(contentProviderClient).close()
    }

    @Test
    fun testHandlePackageRemovedOrDisabled_missingPackage_contentProviderNotClosed() {
        appCardHost.handlePackageRemovedOrDisabled(TEST_AUTHORITY, authority = null)

        verify(contentProviderClient, never()).close()
    }

    @Test
    fun testHandlePackageRemovedOrDisabled_missingPackage_packageRemovedNotReceived() {
        appCardHost.handlePackageRemovedOrDisabled(TEST_AUTHORITY, authority = null)

        assertThat(actualRemovedPackageName).isNull()
    }

    @Test
    fun testHandlePackageChanged_packageDisabled_packageRemovedReceived() {
        val appInfo = ApplicationInfo()
        appInfo.enabled = false
        whenever(packageManager.getApplicationInfo(eq(TEST_PACKAGE), any<Int>()))
            .thenReturn(appInfo)

        appCardHost.handlePackageChanged(TEST_PACKAGE, arrayOf(TEST_PACKAGE))

        assertThat(actualRemovedPackageName).isEqualTo(TEST_PACKAGE)
    }

    @Test
    fun testHandlePackageChanged_packageEnabled_packageAddedReceived() {
        val appInfo = ApplicationInfo()
        appInfo.enabled = true
        whenever(packageManager.getApplicationInfo(eq(TEST_PACKAGE), any<Int>()))
            .thenReturn(appInfo)

        appCardHost.handlePackageChanged(TEST_PACKAGE, arrayOf(TEST_PACKAGE))

        assertThat(actualAddedPackageName).isEqualTo(TEST_PACKAGE)
    }

    @Test
    fun testHandlePackageChanged_providerDisabled_packageRemovedReceived() {
        whenever(packageManager.getComponentEnabledSetting(eq(componentName)))
            .thenReturn(PackageManager.COMPONENT_ENABLED_STATE_DISABLED)

        appCardHost.handlePackageChanged(TEST_PACKAGE, arrayOf(TEST_CLASS))

        assertThat(actualRemovedPackageName).isEqualTo(TEST_PACKAGE)
        assertThat(actualRemovedAuthority).isEqualTo(TEST_AUTHORITY)
    }

    @Test
    fun testHandlePackageChanged_providerEnabled_packageRemovedReceived() {
        whenever(packageManager.getComponentEnabledSetting(eq(componentName)))
            .thenReturn(PackageManager.COMPONENT_ENABLED_STATE_ENABLED)

        appCardHost.handlePackageChanged(TEST_PACKAGE, arrayOf(TEST_CLASS))

        assertThat(actualAddedPackageName).isEqualTo(TEST_PACKAGE)
        assertThat(actualAddedAuthority).isEqualTo(TEST_AUTHORITY)
    }

    @Test
    fun testHandlePackageChanged_providerDefaultDisabled_packageRemovedReceived() {
        providerInfo.enabled = false
        val appInfo = ApplicationInfo()
        appInfo.enabled = false
        providerInfo.applicationInfo = appInfo
        whenever(packageManager.getComponentEnabledSetting(eq(componentName)))
            .thenReturn(PackageManager.COMPONENT_ENABLED_STATE_DEFAULT)

        appCardHost.handlePackageChanged(TEST_PACKAGE, arrayOf(TEST_CLASS))

        assertThat(actualRemovedPackageName).isEqualTo(TEST_PACKAGE)
        assertThat(actualRemovedAuthority).isEqualTo(TEST_AUTHORITY)
    }

    @Test
    fun testHandlePackageChanged_providerDefaultEnabled_packageRemovedReceived() {
        providerInfo.enabled = true
        val appInfo = ApplicationInfo()
        appInfo.enabled = true
        providerInfo.applicationInfo = appInfo
        whenever(packageManager.getComponentEnabledSetting(eq(componentName)))
            .thenReturn(PackageManager.COMPONENT_ENABLED_STATE_DEFAULT)

        appCardHost.handlePackageChanged(TEST_PACKAGE, arrayOf(TEST_CLASS))

        assertThat(actualAddedPackageName).isEqualTo(TEST_PACKAGE)
        assertThat(actualAddedAuthority).isEqualTo(TEST_AUTHORITY)
    }

    @Test
    fun testHandleUserChange_unregisterContentObserverCalled() {
        appCardHost.handleUserChange()

        verify(contentResolver).unregisterContentObserver(any<AppCardObserver>())
    }

    @Test
    fun testHandleUserChange_noActiveAppCard_unregisterContentObserverCalled() {
        appCardHost.handleUserChange()

        verify(contentProviderClient, never()).call(any<String>(), isNull(), any<Bundle>())
    }

    @Test
    fun testHandleUserChange_activeAppCard_unregisterContentObserverCalled() {
        setupActiveAppCard()

        appCardHost.handleUserChange()

        verify(contentResolver).unregisterContentObserver(any<AppCardObserver>())
    }

    @Test
    fun testHandleUserChange_activeAppCard_timerDestroyed() {
        setupActiveAppCard()

        appCardHost.handleUserChange()

        verify(appCardTimer).destroy()
    }

    @Test
    fun testHandleUserChange_activeAppCard_contentProviderClosed() {
        setupActiveAppCard()

        appCardHost.handleUserChange()

        verify(contentProviderClient).close()
    }

    @Test
    fun testHandleUserChange_activeAppCard_contentProviderClientAcquired() {
        setupActiveAppCard()
        reset(contentResolver)

        appCardHost.handleUserChange()

        verify(contentResolver).acquireUnstableContentProviderClient(eq(TEST_AUTHORITY))
    }

    @Test
    fun testDestroy_appChangeReceiverUnregistered() {
        appCardHost.destroy()

        verify(context).unregisterReceiver(any<AppCardHost.AppChangeReceiver>())
    }

    @Test
    fun testDestroy_userChangeReceiverUnregistered() {
        appCardHost.destroy()

        verify(context).unregisterReceiver(any<AppCardHost.UserChangeReceiver>())
    }

    @Test
    fun testDestroy_unregisterContentObserverCalled() {
        appCardHost.destroy()

        verify(contentResolver).unregisterContentObserver(any<AppCardObserver>())
    }

    @Test
    fun testDestroy_noActiveAppCard_unregisterContentObserverCalled() {
        appCardHost.destroy()

        verify(contentProviderClient, never()).call(any<String>(), isNull(), any<Bundle>())
    }

    @Test
    fun testDestroy_activeAppCard_unregisterContentObserverCalled() {
        setupActiveAppCard()

        appCardHost.destroy()

        verify(contentResolver).unregisterContentObserver(any<AppCardObserver>())
    }

    @Test
    fun testDestroy_activeAppCard_timerDestroyed() {
        setupActiveAppCard()

        appCardHost.destroy()

        verify(appCardTimer).destroy()
    }

    @Test
    fun testDestroy_activeAppCard_contentProviderClosed() {
        setupActiveAppCard()

        appCardHost.destroy()

        verify(contentProviderClient).close()
    }

    @Test
    fun testRefreshPreviews_contentProviderClientQueried() {
        val transport = AppCardTransport(ImageAppCardUtility.imageAppCard)
        val cursor = MatrixCursor(arrayOf(AppCardContentProvider.CURSOR_COLUMN_APP_CARD_TRANSPORT))
        cursor.addRow(arrayOf(ParcelableUtils.parcelableToBytes(transport)))
        val uri =
            Uri.Builder()
                .authority(TEST_AUTHORITY)
                .appendPath(AppCardMessageConstants.MSG_SEND_ALL_APP_CARDS)
                .build()
        whenever(contentProviderClient.query(eq(uri), isNull(), any<Bundle>(), isNull()))
            .thenReturn(cursor)
        val captor = argumentCaptor<Bundle>()

        appCardHost.getAllAppCards(appCardContext)

        phaser.arriveAndAwaitAdvance()
        verify(contentProviderClient).query(eq(uri), isNull(), captor.capture(), isNull())
        assertThat(
                appCardContext.equals(
                    AppCardContext.fromBundle(
                        captor.firstValue.getBundle(
                            AppCardContentProvider.BUNDLE_KEY_APP_CARD_CONTEXT
                        )
                    )
                )
            )
            .isTrue()
    }

    @Test
    fun testRefreshPreviews_listenerResultCorrect() {
        val transport = AppCardTransport(ImageAppCardUtility.imageAppCard)
        val cursor = MatrixCursor(arrayOf(AppCardContentProvider.CURSOR_COLUMN_APP_CARD_TRANSPORT))
        cursor.apply {
            addRow(arrayOf(ParcelableUtils.parcelableToBytes(transport)))
            addRow(arrayOf(ParcelableUtils.parcelableToBytes(transport)))
        }
        val uri =
            Uri.Builder()
                .authority(TEST_AUTHORITY)
                .appendPath(AppCardMessageConstants.MSG_SEND_ALL_APP_CARDS)
                .build()
        whenever(contentProviderClient.query(eq(uri), isNull(), any<Bundle>(), isNull()))
            .thenReturn(cursor)

        appCardHost.getAllAppCards(appCardContext)

        phaser.arriveAndAwaitAdvance()
        assertThat(actualAppCardContainers.size).isEqualTo(2)
        assertThat(actualAppCardContainers[0].appCard.toByteArray())
            .isEqualTo(ImageAppCardUtility.imageAppCard.toByteArray())
        assertThat(actualAppCardContainers[0].appId).isEqualTo(applicationIdentifier)
        assertThat(actualAppCardContainers[1].appCard.toByteArray())
            .isEqualTo(ImageAppCardUtility.imageAppCard.toByteArray())
        assertThat(actualAppCardContainers[1].appId).isEqualTo(applicationIdentifier)
    }

    @Test
    fun testRefreshPreviews_remoteException_listenerResultCorrect() {
        whenever(contentProviderClient.query(any<Uri>(), isNull(), any<Bundle>(), isNull()))
            .thenThrow(RemoteException())
        val expectedMsg =
            "Exception occurred when calling (with MSG_SEND_ALL_APP_CARDS)" +
                " app card content provider"
        val expectedCauseString = "android.os.RemoteException"

        appCardHost.getAllAppCards(appCardContext)

        phaser.arriveAndAwaitAdvance()
        assertThat(actualAppCardIdentifier).isEqualTo(applicationIdentifier)
        assertThat(actualThrowable?.message).isEqualTo(expectedMsg)
        assertThat(actualThrowable?.cause?.toString()).isEqualTo(expectedCauseString)
    }

    @Test
    fun testRefreshPreviews_deadObjectException_listenerResultCorrect() {
        whenever(contentProviderClient.query(any<Uri>(), isNull(), any<Bundle>(), isNull()))
            .thenThrow(DeadObjectException())
        val expectedMsg =
            "Exception occurred when calling (with MSG_SEND_ALL_APP_CARDS)" +
                " app card content provider"
        val expectedCauseString = "android.os.DeadObjectException"

        appCardHost.getAllAppCards(appCardContext)

        phaser.arriveAndAwaitAdvance()
        assertThat(actualAppCardIdentifier).isEqualTo(applicationIdentifier)
        assertThat(actualThrowable?.message).isEqualTo(expectedMsg)
        assertThat(actualThrowable?.cause?.toString()).isEqualTo(expectedCauseString)
    }

    @Test
    fun testRefreshPreviews_deadObjectException_contentProviderClientClosed() {
        whenever(contentProviderClient.query(any<Uri>(), isNull(), any<Bundle>(), isNull()))
            .thenThrow(DeadObjectException())

        appCardHost.getAllAppCards(appCardContext)

        phaser.arriveAndAwaitAdvance()
        verify(contentProviderClient).close()
    }

    @Test
    fun testRefreshPreviews_nullQueryReturn_listenerResultCorrect() {
        whenever(contentProviderClient.query(any<Uri>(), isNull(), any<Bundle>(), isNull()))
            .thenReturn(null)
        val expectedMsg =
            "Exception occurred when calling (with MSG_SEND_ALL_APP_CARDS)" +
                " app card content provider"
        val expectedCauseString = "java.lang.IllegalStateException: Result from query is null"

        appCardHost.getAllAppCards(appCardContext)

        phaser.arriveAndAwaitAdvance()
        assertThat(actualAppCardIdentifier).isEqualTo(applicationIdentifier)
        assertThat(actualThrowable?.message).isEqualTo(expectedMsg)
        assertThat(actualThrowable?.cause?.toString()).isEqualTo(expectedCauseString)
    }

    @Test
    fun testRefreshPreviews_incorrectColumn_listenerResultCorrect() {
        val transport = AppCardTransport(ImageAppCardUtility.imageAppCard)
        val cursor = MatrixCursor(arrayOf(TEST_AUTHORITY))
        cursor.addRow(arrayOf(ParcelableUtils.parcelableToBytes(transport)))
        whenever(contentProviderClient.query(any<Uri>(), isNull(), any<Bundle>(), isNull()))
            .thenReturn(cursor)
        val expectedMsg =
            "Exception occurred when calling (with MSG_SEND_ALL_APP_CARDS)" +
                " app card content provider"
        val expectedCauseString =
            "java.lang.IllegalArgumentException: " +
                "column 'appCardTransport' does not exist. Available columns: [TEST_AUTHORITY]"

        appCardHost.getAllAppCards(appCardContext)

        phaser.arriveAndAwaitAdvance()
        assertThat(actualAppCardIdentifier).isEqualTo(applicationIdentifier)
        assertThat(actualThrowable?.message).isEqualTo(expectedMsg)
        assertThat(actualThrowable?.cause?.toString()).isEqualTo(expectedCauseString)
    }

    @Test
    fun testRequestAppCard_contentProviderQueriedWithCorrectInputBundle() {
        val captor = argumentCaptor<Bundle>()
        val uri =
            Uri.Builder()
                .authority(TEST_AUTHORITY)
                .appendPath(AppCardMessageConstants.MSG_APP_CARD_ADDED)
                .build()

        appCardHost.requestAppCard(
            appCardContext,
            applicationIdentifier,
            ImageAppCardUtility.TEST_ID,
        )

        phaser.arriveAndAwaitAdvance()
        verify(contentProviderClient).query(eq(uri), isNull(), captor.capture(), isNull())
        assertThat(captor.firstValue.getString(AppCardContentProvider.BUNDLE_KEY_APP_CARD_ID))
            .isEqualTo(ImageAppCardUtility.TEST_ID)
        assertThat(
                AppCardContext.fromBundle(
                    captor.firstValue.getBundle(AppCardContentProvider.BUNDLE_KEY_APP_CARD_CONTEXT)
                )
            )
            .isEqualTo(appCardContext)
    }

    @Test
    fun testRequestAppCard_remoteException_listenerResultCorrect() {
        val uri =
            Uri.Builder()
                .authority(TEST_AUTHORITY)
                .appendPath(AppCardMessageConstants.MSG_APP_CARD_ADDED)
                .build()
        whenever(contentProviderClient.query(eq(uri), isNull(), any<Bundle>(), isNull()))
            .thenThrow(RemoteException())
        val expectedMsg =
            "Exception occurred when calling (with MSG_APP_CARD_ADDED)" +
                " app card content provider (ID: ID)"
        val expectedCauseString = "android.os.RemoteException"

        appCardHost.requestAppCard(
            appCardContext,
            applicationIdentifier,
            ImageAppCardUtility.TEST_ID,
        )

        phaser.arriveAndAwaitAdvance()
        assertThat(actualAppCardIdentifier).isEqualTo(applicationIdentifier)
        assertThat(actualThrowable?.message).isEqualTo(expectedMsg)
        assertThat(actualThrowable?.cause?.toString()).isEqualTo(expectedCauseString)
    }

    @Test
    fun testRequestAppCard_deadObjectException_listenerResultCorrect() {
        val uri =
            Uri.Builder()
                .authority(TEST_AUTHORITY)
                .appendPath(AppCardMessageConstants.MSG_APP_CARD_ADDED)
                .build()
        whenever(contentProviderClient.query(eq(uri), isNull(), any<Bundle>(), isNull()))
            .thenThrow(DeadObjectException())
        val expectedMsg =
            "Exception occurred when calling (with MSG_APP_CARD_ADDED)" +
                " app card content provider (ID: ID)"
        val expectedCauseString = "android.os.DeadObjectException"

        appCardHost.requestAppCard(
            appCardContext,
            applicationIdentifier,
            ImageAppCardUtility.TEST_ID,
        )

        phaser.arriveAndAwaitAdvance()
        assertThat(actualAppCardIdentifier).isEqualTo(applicationIdentifier)
        assertThat(actualThrowable?.message).isEqualTo(expectedMsg)
        assertThat(actualThrowable?.cause?.toString()).isEqualTo(expectedCauseString)
    }

    @Test
    fun testRequestAppCard_deadObjectException_contentProviderClientClosed() {
        val uri =
            Uri.Builder()
                .authority(TEST_AUTHORITY)
                .appendPath(AppCardMessageConstants.MSG_APP_CARD_ADDED)
                .build()
        whenever(contentProviderClient.query(eq(uri), isNull(), any<Bundle>(), isNull()))
            .thenThrow(DeadObjectException())

        appCardHost.requestAppCard(
            appCardContext,
            applicationIdentifier,
            ImageAppCardUtility.TEST_ID,
        )

        phaser.arriveAndAwaitAdvance()
        verify(contentProviderClient).close()
    }

    @Test
    fun testRequestAppCard_nullQueryReturn_listenerResultCorrect() {
        val uri =
            Uri.Builder()
                .authority(TEST_AUTHORITY)
                .appendPath(AppCardMessageConstants.MSG_APP_CARD_ADDED)
                .build()
        whenever(contentProviderClient.query(eq(uri), isNull(), any<Bundle>(), isNull()))
            .thenReturn(null)
        val expectedMsg =
            "Exception occurred when calling (with MSG_APP_CARD_ADDED)" +
                " app card content provider (ID: ID)"
        val expectedCauseString = "java.lang.IllegalStateException: Result from query is null"

        appCardHost.requestAppCard(
            appCardContext,
            applicationIdentifier,
            ImageAppCardUtility.TEST_ID,
        )

        phaser.arriveAndAwaitAdvance()
        assertThat(actualAppCardIdentifier).isEqualTo(applicationIdentifier)
        assertThat(actualThrowable?.message).isEqualTo(expectedMsg)
        assertThat(actualThrowable?.cause?.toString()).isEqualTo(expectedCauseString)
    }

    @Test
    fun testRequestAppCard_incorrectColumn_listenerResultCorrect() {
        val transport = AppCardTransport(ImageAppCardUtility.imageAppCard)
        val cursor = MatrixCursor(arrayOf(TEST_AUTHORITY))
        cursor.addRow(arrayOf(ParcelableUtils.parcelableToBytes(transport)))
        whenever(contentProviderClient.query(any<Uri>(), isNull(), any<Bundle>(), isNull()))
            .thenReturn(cursor)
        val expectedMsg =
            "Exception occurred when calling (with MSG_APP_CARD_ADDED)" +
                " app card content provider (ID: ID)"
        val expectedCauseString =
            "java.lang.IllegalArgumentException: " +
                "column 'appCardTransport' does not exist. Available columns: [TEST_AUTHORITY]"

        appCardHost.requestAppCard(
            appCardContext,
            applicationIdentifier,
            ImageAppCardUtility.TEST_ID,
        )

        phaser.arriveAndAwaitAdvance()
        assertThat(actualAppCardIdentifier).isEqualTo(applicationIdentifier)
        assertThat(actualThrowable?.message).isEqualTo(expectedMsg)
        assertThat(actualThrowable?.cause?.toString()).isEqualTo(expectedCauseString)
    }

    @Test
    fun testRequestAppCard_appCardTransportMissing_listenerResultCorrect() {
        val uri =
            Uri.Builder()
                .authority(TEST_AUTHORITY)
                .appendPath(AppCardMessageConstants.MSG_APP_CARD_ADDED)
                .build()
        whenever(contentProviderClient.query(eq(uri), isNull(), any<Bundle>(), isNull()))
            .thenReturn(MatrixCursor(arrayOf()))
        val expectedMsg =
            "Exception occurred when calling (with MSG_APP_CARD_ADDED)" +
                " app card content provider (ID: ID)"
        val expectedCauseString = "java.lang.IllegalStateException: App card transport missing"

        appCardHost.requestAppCard(
            appCardContext,
            applicationIdentifier,
            ImageAppCardUtility.TEST_ID,
        )

        phaser.arriveAndAwaitAdvance()
        assertThat(actualAppCardIdentifier).isEqualTo(applicationIdentifier)
        assertThat(actualThrowable?.message).isEqualTo(expectedMsg)
        assertThat(actualThrowable?.cause?.toString()).isEqualTo(expectedCauseString)
    }

    @Test
    fun testRequestAppCard_noAppCardInAppCardTransport_listenerResultCorrect() {
        val transport = AppCardTransport(ImageAppCardUtility.header)
        val cursor = MatrixCursor(arrayOf(AppCardContentProvider.CURSOR_COLUMN_APP_CARD_TRANSPORT))
        cursor.addRow(arrayOf(ParcelableUtils.parcelableToBytes(transport)))
        val uri =
            Uri.Builder()
                .authority(TEST_AUTHORITY)
                .appendPath(AppCardMessageConstants.MSG_APP_CARD_ADDED)
                .build()
        whenever(contentProviderClient.query(eq(uri), isNull(), any<Bundle>(), isNull()))
            .thenReturn(cursor)
        val expectedMsg =
            "Exception occurred when calling (with MSG_APP_CARD_ADDED)" +
                " app card content provider (ID: ID)"
        val expectedCauseString = "java.lang.IllegalStateException: App card missing from transport"

        appCardHost.requestAppCard(
            appCardContext,
            applicationIdentifier,
            ImageAppCardUtility.TEST_ID,
        )

        phaser.arriveAndAwaitAdvance()
        assertThat(actualAppCardIdentifier).isEqualTo(applicationIdentifier)
        assertThat(actualThrowable?.message).isEqualTo(expectedMsg)
        assertThat(actualThrowable?.cause?.toString()).isEqualTo(expectedCauseString)
    }

    @Test
    fun testRequestAppCard_timerUpdateAppCardCalled() {
        val captor = argumentCaptor<AppCardContainer>()
        val transport = AppCardTransport(ImageAppCardUtility.imageAppCard)
        val cursor = MatrixCursor(arrayOf(AppCardContentProvider.CURSOR_COLUMN_APP_CARD_TRANSPORT))
        cursor.addRow(arrayOf(ParcelableUtils.parcelableToBytes(transport)))
        val uri =
            Uri.Builder()
                .authority(TEST_AUTHORITY)
                .appendPath(AppCardMessageConstants.MSG_APP_CARD_ADDED)
                .build()
        whenever(contentProviderClient.query(eq(uri), isNull(), any<Bundle>(), isNull()))
            .thenReturn(cursor)

        appCardHost.requestAppCard(
            appCardContext,
            applicationIdentifier,
            ImageAppCardUtility.TEST_ID,
        )

        phaser.arriveAndAwaitAdvance()
        verify(appCardTimer).updateAppCard(captor.capture())
        assertThat(captor.firstValue.appId).isEqualTo(applicationIdentifier)
        assertThat(captor.firstValue.appCard.toByteArray())
            .isEqualTo(ImageAppCardUtility.imageAppCard.toByteArray())
    }

    @Test
    fun testRequestAppCard_listenerResultCorrect() {
        val transport = AppCardTransport(ImageAppCardUtility.imageAppCard)
        val cursor = MatrixCursor(arrayOf(AppCardContentProvider.CURSOR_COLUMN_APP_CARD_TRANSPORT))
        cursor.addRow(arrayOf(ParcelableUtils.parcelableToBytes(transport)))
        val uri =
            Uri.Builder()
                .authority(TEST_AUTHORITY)
                .appendPath(AppCardMessageConstants.MSG_APP_CARD_ADDED)
                .build()
        whenever(contentProviderClient.query(eq(uri), isNull(), any<Bundle>(), isNull()))
            .thenReturn(cursor)

        appCardHost.requestAppCard(
            appCardContext,
            applicationIdentifier,
            ImageAppCardUtility.TEST_ID,
        )

        phaser.arriveAndAwaitAdvance()
        assertThat(actualAppCardContainers.size).isEqualTo(1)
        assertThat(actualAppCardContainers[0].appCard.toByteArray())
            .isEqualTo(ImageAppCardUtility.imageAppCard.toByteArray())
        assertThat(actualAppCardContainers[0].appId).isEqualTo(applicationIdentifier)
    }

    @Test
    fun testAppCardIsReadyForUpdate_contentProviderQueriedWithCorrectInputBundle() {
        val captor = argumentCaptor<Bundle>()

        appCardHost.appCardIsReadyForUpdate(applicationIdentifier, ImageAppCardUtility.TEST_ID)

        phaser.arriveAndAwaitAdvance()
        val uri =
            Uri.Builder()
                .authority(TEST_AUTHORITY)
                .appendPath(AppCardMessageConstants.MSG_APP_CARD_UPDATE)
                .build()
        verify(contentProviderClient).query(eq(uri), isNull(), captor.capture(), isNull())
        assertThat(captor.firstValue.getString(AppCardContentProvider.BUNDLE_KEY_APP_CARD_ID))
            .isEqualTo(ImageAppCardUtility.TEST_ID)
    }

    @Test
    fun testAppCardIsReadyForUpdate_remoteException_listenerResultCorrect() {
        val uri =
            Uri.Builder()
                .authority(TEST_AUTHORITY)
                .appendPath(AppCardMessageConstants.MSG_APP_CARD_UPDATE)
                .build()
        whenever(contentProviderClient.query(eq(uri), isNull(), any<Bundle>(), isNull()))
            .thenThrow(RemoteException())
        val expectedMsg =
            "Exception occurred when calling (with MSG_APP_CARD_UPDATE)" +
                " app card content provider (ID: ID)"
        val expectedCauseString = "android.os.RemoteException"

        appCardHost.appCardIsReadyForUpdate(applicationIdentifier, ImageAppCardUtility.TEST_ID)

        phaser.arriveAndAwaitAdvance()
        assertThat(actualAppCardIdentifier).isEqualTo(applicationIdentifier)
        assertThat(actualThrowable?.message).isEqualTo(expectedMsg)
        assertThat(actualThrowable?.cause?.toString()).isEqualTo(expectedCauseString)
    }

    @Test
    fun testAppCardIsReadyForUpdate_deadObjectException_listenerResultCorrect() {
        val uri =
            Uri.Builder()
                .authority(TEST_AUTHORITY)
                .appendPath(AppCardMessageConstants.MSG_APP_CARD_UPDATE)
                .build()
        whenever(contentProviderClient.query(eq(uri), isNull(), any<Bundle>(), isNull()))
            .thenThrow(DeadObjectException())
        val expectedMsg =
            "Exception occurred when calling (with MSG_APP_CARD_UPDATE)" +
                " app card content provider (ID: ID)"
        val expectedCauseString = "android.os.DeadObjectException"

        appCardHost.appCardIsReadyForUpdate(applicationIdentifier, ImageAppCardUtility.TEST_ID)

        phaser.arriveAndAwaitAdvance()
        assertThat(actualAppCardIdentifier).isEqualTo(applicationIdentifier)
        assertThat(actualThrowable?.message).isEqualTo(expectedMsg)
        assertThat(actualThrowable?.cause?.toString()).isEqualTo(expectedCauseString)
    }

    @Test
    fun testAppCardIsReadyForUpdate_deadObjectException_contentProviderClientClosed() {
        val uri =
            Uri.Builder()
                .authority(TEST_AUTHORITY)
                .appendPath(AppCardMessageConstants.MSG_APP_CARD_UPDATE)
                .build()
        whenever(contentProviderClient.query(eq(uri), isNull(), any<Bundle>(), isNull()))
            .thenThrow(DeadObjectException())

        appCardHost.appCardIsReadyForUpdate(applicationIdentifier, ImageAppCardUtility.TEST_ID)

        phaser.arriveAndAwaitAdvance()
        verify(contentProviderClient).close()
    }

    @Test
    fun testAppCardIsReadyForUpdate_nullQueryReturn_listenerResultCorrect() {
        val uri =
            Uri.Builder()
                .authority(TEST_AUTHORITY)
                .appendPath(AppCardMessageConstants.MSG_APP_CARD_UPDATE)
                .build()
        whenever(contentProviderClient.query(eq(uri), isNull(), any<Bundle>(), isNull()))
            .thenReturn(null)
        val expectedMsg =
            "Exception occurred when calling (with MSG_APP_CARD_UPDATE)" +
                " app card content provider (ID: ID)"
        val expectedCauseString = "java.lang.IllegalStateException: Result from query is null"

        appCardHost.appCardIsReadyForUpdate(applicationIdentifier, ImageAppCardUtility.TEST_ID)

        phaser.arriveAndAwaitAdvance()
        assertThat(actualAppCardIdentifier).isEqualTo(applicationIdentifier)
        assertThat(actualThrowable?.message).isEqualTo(expectedMsg)
        assertThat(actualThrowable?.cause?.toString()).isEqualTo(expectedCauseString)
    }

    @Test
    fun testAppCardIsReadyForUpdate_incorrectColumn_listenerResultCorrect() {
        val transport = AppCardTransport(ImageAppCardUtility.imageAppCard)
        val cursor = MatrixCursor(arrayOf(TEST_AUTHORITY))
        cursor.addRow(arrayOf(ParcelableUtils.parcelableToBytes(transport)))
        whenever(contentProviderClient.query(any<Uri>(), isNull(), any<Bundle>(), isNull()))
            .thenReturn(cursor)
        val expectedMsg =
            "Exception occurred when calling (with MSG_APP_CARD_UPDATE)" +
                " app card content provider (ID: ID)"
        val expectedCauseString =
            "java.lang.IllegalArgumentException: " +
                "column 'appCardTransport' does not exist. Available columns: [TEST_AUTHORITY]"

        appCardHost.appCardIsReadyForUpdate(applicationIdentifier, ImageAppCardUtility.TEST_ID)

        phaser.arriveAndAwaitAdvance()
        assertThat(actualAppCardIdentifier).isEqualTo(applicationIdentifier)
        assertThat(actualThrowable?.message).isEqualTo(expectedMsg)
        assertThat(actualThrowable?.cause?.toString()).isEqualTo(expectedCauseString)
    }

    @Test
    fun testAppCardIsReadyForUpdate_appCardTransportMissing_listenerResultCorrect() {
        val uri =
            Uri.Builder()
                .authority(TEST_AUTHORITY)
                .appendPath(AppCardMessageConstants.MSG_APP_CARD_UPDATE)
                .build()
        whenever(contentProviderClient.query(eq(uri), isNull(), any<Bundle>(), isNull()))
            .thenReturn(MatrixCursor(arrayOf()))
        val expectedMsg =
            "Exception occurred when calling (with MSG_APP_CARD_UPDATE)" +
                " app card content provider (ID: ID)"
        val expectedCauseString = "java.lang.IllegalStateException: App card transport missing"

        appCardHost.appCardIsReadyForUpdate(applicationIdentifier, ImageAppCardUtility.TEST_ID)

        phaser.arriveAndAwaitAdvance()
        assertThat(actualAppCardIdentifier).isEqualTo(applicationIdentifier)
        assertThat(actualThrowable?.message).isEqualTo(expectedMsg)
        assertThat(actualThrowable?.cause?.toString()).isEqualTo(expectedCauseString)
    }

    @Test
    fun testAppCardIsReadyForUpdate_noAppCardInAppCardTransport_listenerResultCorrect() {
        val transport = AppCardTransport(ImageAppCardUtility.header)
        val cursor = MatrixCursor(arrayOf(AppCardContentProvider.CURSOR_COLUMN_APP_CARD_TRANSPORT))
        cursor.addRow(arrayOf(ParcelableUtils.parcelableToBytes(transport)))
        val uri =
            Uri.Builder()
                .authority(TEST_AUTHORITY)
                .appendPath(AppCardMessageConstants.MSG_APP_CARD_UPDATE)
                .build()
        whenever(contentProviderClient.query(eq(uri), isNull(), any<Bundle>(), isNull()))
            .thenReturn(cursor)
        val expectedMsg =
            "Exception occurred when calling (with MSG_APP_CARD_UPDATE)" +
                " app card content provider (ID: ID)"
        val expectedCauseString = "java.lang.IllegalStateException: App card missing from transport"

        appCardHost.appCardIsReadyForUpdate(applicationIdentifier, ImageAppCardUtility.TEST_ID)

        phaser.arriveAndAwaitAdvance()
        assertThat(actualAppCardIdentifier).isEqualTo(applicationIdentifier)
        assertThat(actualThrowable?.message).isEqualTo(expectedMsg)
        assertThat(actualThrowable?.cause?.toString()).isEqualTo(expectedCauseString)
    }

    @Test
    fun testAppCardIsReadyForUpdate_timerUpdateAppCardCalled() {
        val transport = AppCardTransport(ImageAppCardUtility.imageAppCard)
        var cursor = MatrixCursor(arrayOf(AppCardContentProvider.CURSOR_COLUMN_APP_CARD_TRANSPORT))
        cursor.addRow(arrayOf(ParcelableUtils.parcelableToBytes(transport)))
        var uri =
            Uri.Builder()
                .authority(TEST_AUTHORITY)
                .appendPath(AppCardMessageConstants.MSG_APP_CARD_ADDED)
                .build()
        whenever(contentProviderClient.query(eq(uri), isNull(), any<Bundle>(), isNull()))
            .thenReturn(cursor)
        appCardHost.requestAppCard(
            appCardContext,
            applicationIdentifier,
            ImageAppCardUtility.TEST_ID,
        )
        phaser.arriveAndAwaitAdvance()
        reset(contentProviderClient, appCardTimer)
        val captor = argumentCaptor<AppCardContainer>()
        cursor = MatrixCursor(arrayOf(AppCardContentProvider.CURSOR_COLUMN_APP_CARD_TRANSPORT))
        cursor.addRow(arrayOf(ParcelableUtils.parcelableToBytes(transport)))
        uri =
            Uri.Builder()
                .authority(TEST_AUTHORITY)
                .appendPath(AppCardMessageConstants.MSG_APP_CARD_UPDATE)
                .build()
        whenever(contentProviderClient.query(eq(uri), isNull(), any<Bundle>(), isNull()))
            .thenReturn(cursor)

        appCardHost.appCardIsReadyForUpdate(applicationIdentifier, ImageAppCardUtility.TEST_ID)

        phaser.arriveAndAwaitAdvance()
        verify(appCardTimer).updateAppCard(captor.capture())
        assertThat(captor.firstValue.appId).isEqualTo(applicationIdentifier)
        assertThat(captor.firstValue.appCard.toByteArray())
            .isEqualTo(ImageAppCardUtility.imageAppCard.toByteArray())
    }

    @Test
    fun testAppCardIsReadyForUpdate_listenerResultCorrect() {
        val transport = AppCardTransport(ImageAppCardUtility.imageAppCard)
        val cursor = MatrixCursor(arrayOf(AppCardContentProvider.CURSOR_COLUMN_APP_CARD_TRANSPORT))
        cursor.addRow(arrayOf(ParcelableUtils.parcelableToBytes(transport)))
        val uri =
            Uri.Builder()
                .authority(TEST_AUTHORITY)
                .appendPath(AppCardMessageConstants.MSG_APP_CARD_UPDATE)
                .build()
        whenever(contentProviderClient.query(eq(uri), isNull(), any<Bundle>(), isNull()))
            .thenReturn(cursor)

        appCardHost.appCardIsReadyForUpdate(applicationIdentifier, ImageAppCardUtility.TEST_ID)

        phaser.arriveAndAwaitAdvance()
        assertThat(actualAppCardContainers.size).isEqualTo(1)
        assertThat(actualAppCardContainers[0].appCard.toByteArray())
            .isEqualTo(ImageAppCardUtility.imageAppCard.toByteArray())
        assertThat(actualAppCardContainers[0].appId).isEqualTo(applicationIdentifier)
    }

    @Test
    fun testNotifyAppCardRemoved_timerDestroyCalled() {
        setupActiveAppCard()

        appCardHost.notifyAppCardRemoved(applicationIdentifier, ImageAppCardUtility.TEST_ID)

        verify(appCardTimer).destroy()
    }

    @Test
    fun testNotifyAppCardRemoved_contentProviderCalledCorrectly() {
        val captor = argumentCaptor<Bundle>()
        setupActiveAppCard()

        appCardHost.notifyAppCardRemoved(applicationIdentifier, ImageAppCardUtility.TEST_ID)

        phaser.arriveAndAwaitAdvance()
        verify(contentProviderClient)
            .call(eq(AppCardMessageConstants.MSG_APP_CARD_REMOVED), isNull(), captor.capture())
        assertThat(captor.firstValue.getString(AppCardContentProvider.BUNDLE_KEY_APP_CARD_ID))
            .isEqualTo(ImageAppCardUtility.TEST_ID)
    }

    @Test
    fun testNotifyAppCardRemoved_remoteException_listenerCalledCorrectly() {
        setupActiveAppCard()
        whenever(
                contentProviderClient.call(
                    eq(AppCardMessageConstants.MSG_APP_CARD_REMOVED),
                    isNull(),
                    any<Bundle>(),
                )
            )
            .thenThrow(RemoteException())
        val expectedMsg =
            "Exception occurred when calling (with MSG_APP_CARD_REMOVED)" +
                " app card content provider (ID: ID)"
        val expectedCauseString = "android.os.RemoteException"

        appCardHost.notifyAppCardRemoved(applicationIdentifier, ImageAppCardUtility.TEST_ID)

        phaser.arriveAndAwaitAdvance()
        assertThat(actualAppCardIdentifier).isEqualTo(applicationIdentifier)
        assertThat(actualThrowable?.message).isEqualTo(expectedMsg)
        assertThat(actualThrowable?.cause?.toString()).isEqualTo(expectedCauseString)
    }

    @Test
    fun testNotifyAppCardRemoved_deadObjectException_listenerResultCorrect() {
        setupActiveAppCard()
        whenever(
                contentProviderClient.call(
                    eq(AppCardMessageConstants.MSG_APP_CARD_REMOVED),
                    isNull(),
                    any<Bundle>(),
                )
            )
            .thenThrow(DeadObjectException())
        val expectedMsg =
            "Exception occurred when calling (with MSG_APP_CARD_REMOVED)" +
                " app card content provider (ID: ID)"
        val expectedCauseString = "android.os.DeadObjectException"

        appCardHost.notifyAppCardRemoved(applicationIdentifier, ImageAppCardUtility.TEST_ID)

        phaser.arriveAndAwaitAdvance()
        assertThat(actualAppCardIdentifier).isEqualTo(applicationIdentifier)
        assertThat(actualThrowable?.message).isEqualTo(expectedMsg)
        assertThat(actualThrowable?.cause?.toString()).isEqualTo(expectedCauseString)
    }

    @Test
    fun testNotifyAppCardRemoved_deadObjectException_contentProviderClientClosed() {
        setupActiveAppCard()
        whenever(
                contentProviderClient.call(
                    eq(AppCardMessageConstants.MSG_APP_CARD_REMOVED),
                    isNull(),
                    any<Bundle>(),
                )
            )
            .thenThrow(DeadObjectException())

        appCardHost.notifyAppCardRemoved(applicationIdentifier, ImageAppCardUtility.TEST_ID)

        phaser.arriveAndAwaitAdvance()
        verify(contentProviderClient).close()
    }

    @Test
    fun testNotifyAppCardRemoved_deadObjectException_unregisterContentObserverCalled() {
        setupActiveAppCard()
        whenever(
                contentProviderClient.call(
                    eq(AppCardMessageConstants.MSG_APP_CARD_REMOVED),
                    isNull(),
                    any<Bundle>(),
                )
            )
            .thenThrow(DeadObjectException())

        appCardHost.notifyAppCardRemoved(applicationIdentifier, ImageAppCardUtility.TEST_ID)

        phaser.arriveAndAwaitAdvance()
        verify(contentResolver).unregisterContentObserver(any<AppCardObserver>())
    }

    @Test
    fun testNotifyAppCardRemoved_deadObjectException_timerDestroyed() {
        setupActiveAppCard()
        whenever(
                contentProviderClient.call(
                    eq(AppCardMessageConstants.MSG_APP_CARD_REMOVED),
                    isNull(),
                    any<Bundle>(),
                )
            )
            .thenThrow(DeadObjectException())

        appCardHost.notifyAppCardRemoved(applicationIdentifier, ImageAppCardUtility.TEST_ID)

        phaser.arriveAndAwaitAdvance()
        verify(appCardTimer).destroy()
    }

    @Test
    fun testNotifyAppCardInteraction_contentProviderCalledWithCorrectInputBundle() {
        val captor = argumentCaptor<Bundle>()
        setupActiveAppCard()

        appCardHost.notifyAppCardInteraction(
            applicationIdentifier,
            ImageAppCardUtility.TEST_ID,
            ImageAppCardUtility.TEST_BUTTON_COMPONENT_ID,
            AppCardMessageConstants.InteractionMessageConstants.MSG_INTERACTION_ON_CLICK,
        )

        phaser.arriveAndAwaitAdvance()
        verify(contentProviderClient)
            .call(eq(AppCardMessageConstants.MSG_APP_CARD_INTERACTION), isNull(), captor.capture())
        assertThat(captor.firstValue.getString(AppCardContentProvider.BUNDLE_KEY_APP_CARD_ID))
            .isEqualTo(ImageAppCardUtility.TEST_ID)
        assertThat(
                captor.firstValue.getString(AppCardContentProvider.BUNDLE_KEY_APP_CARD_COMPONENT_ID)
            )
            .isEqualTo(ImageAppCardUtility.TEST_BUTTON_COMPONENT_ID)
        assertThat(
                captor.firstValue.getString(
                    AppCardContentProvider.BUNDLE_KEY_APP_CARD_INTERACTION_ID
                )
            )
            .isEqualTo(AppCardMessageConstants.InteractionMessageConstants.MSG_INTERACTION_ON_CLICK)
    }

    @Test
    fun testNotifyAppCardInteraction_timerResetAppCardTimerAndRequestUpdateCalled() {
        setupActiveAppCard()

        appCardHost.notifyAppCardInteraction(
            applicationIdentifier,
            ImageAppCardUtility.TEST_ID,
            ImageAppCardUtility.TEST_BUTTON_COMPONENT_ID,
            AppCardMessageConstants.InteractionMessageConstants.MSG_INTERACTION_ON_CLICK,
        )

        phaser.arriveAndAwaitAdvance()
        verify(appCardTimer).resetAppCardTimerAndRequestUpdate()
    }

    @Test
    fun testNotifyAppCardInteraction_remoteException_listenerCalledCorrectly() {
        setupActiveAppCard()
        whenever(
                contentProviderClient.call(
                    eq(AppCardMessageConstants.MSG_APP_CARD_INTERACTION),
                    isNull(),
                    any<Bundle>(),
                )
            )
            .thenThrow(RemoteException())
        val expectedMsg =
            "Exception occurred when calling (with MSG_APP_CARD_INTERACTION) app " +
                "card content provider (ID: ID#TEST_BUTTON_COMPONENT_ID#MSG_INTERACTION_ON_CLICK)"
        val expectedCauseString = "android.os.RemoteException"

        appCardHost.notifyAppCardInteraction(
            applicationIdentifier,
            ImageAppCardUtility.TEST_ID,
            ImageAppCardUtility.TEST_BUTTON_COMPONENT_ID,
            AppCardMessageConstants.InteractionMessageConstants.MSG_INTERACTION_ON_CLICK,
        )

        phaser.arriveAndAwaitAdvance()
        assertThat(actualAppCardIdentifier).isEqualTo(applicationIdentifier)
        assertThat(actualThrowable?.message).isEqualTo(expectedMsg)
        assertThat(actualThrowable?.cause?.toString()).isEqualTo(expectedCauseString)
    }

    @Test
    fun testNotifyAppCardInteraction_deadObjectException_listenerResultCorrect() {
        setupActiveAppCard()
        whenever(
                contentProviderClient.call(
                    eq(AppCardMessageConstants.MSG_APP_CARD_INTERACTION),
                    isNull(),
                    any<Bundle>(),
                )
            )
            .thenThrow(DeadObjectException())
        val expectedMsg =
            "Exception occurred when calling (with MSG_APP_CARD_INTERACTION) app " +
                "card content provider (ID: ID#TEST_BUTTON_COMPONENT_ID#MSG_INTERACTION_ON_CLICK)"
        val expectedCauseString = "android.os.DeadObjectException"

        appCardHost.notifyAppCardInteraction(
            applicationIdentifier,
            ImageAppCardUtility.TEST_ID,
            ImageAppCardUtility.TEST_BUTTON_COMPONENT_ID,
            AppCardMessageConstants.InteractionMessageConstants.MSG_INTERACTION_ON_CLICK,
        )

        phaser.arriveAndAwaitAdvance()
        assertThat(actualAppCardIdentifier).isEqualTo(applicationIdentifier)
        assertThat(actualThrowable?.message).isEqualTo(expectedMsg)
        assertThat(actualThrowable?.cause?.toString()).isEqualTo(expectedCauseString)
    }

    @Test
    fun testNotifyAppCardInteraction_deadObjectException_contentProviderClientClosed() {
        setupActiveAppCard()
        whenever(
                contentProviderClient.call(
                    eq(AppCardMessageConstants.MSG_APP_CARD_INTERACTION),
                    isNull(),
                    any<Bundle>(),
                )
            )
            .thenThrow(DeadObjectException())

        appCardHost.notifyAppCardInteraction(
            applicationIdentifier,
            ImageAppCardUtility.TEST_ID,
            ImageAppCardUtility.TEST_BUTTON_COMPONENT_ID,
            AppCardMessageConstants.InteractionMessageConstants.MSG_INTERACTION_ON_CLICK,
        )

        phaser.arriveAndAwaitAdvance()
        verify(contentProviderClient).close()
    }

    @Test
    fun testNotifyAppCardInteraction_deadObjectException_unregisterContentObserverCalled() {
        setupActiveAppCard()
        whenever(
                contentProviderClient.call(
                    eq(AppCardMessageConstants.MSG_APP_CARD_INTERACTION),
                    isNull(),
                    any<Bundle>(),
                )
            )
            .thenThrow(DeadObjectException())

        appCardHost.notifyAppCardInteraction(
            applicationIdentifier,
            ImageAppCardUtility.TEST_ID,
            ImageAppCardUtility.TEST_BUTTON_COMPONENT_ID,
            AppCardMessageConstants.InteractionMessageConstants.MSG_INTERACTION_ON_CLICK,
        )

        phaser.arriveAndAwaitAdvance()
        verify(contentResolver).unregisterContentObserver(any<AppCardObserver>())
    }

    @Test
    fun testNotifyAppCardInteraction_deadObjectException_timerDestroyed() {
        setupActiveAppCard()
        whenever(
                contentProviderClient.call(
                    eq(AppCardMessageConstants.MSG_APP_CARD_INTERACTION),
                    isNull(),
                    any<Bundle>(),
                )
            )
            .thenThrow(DeadObjectException())

        appCardHost.notifyAppCardInteraction(
            applicationIdentifier,
            ImageAppCardUtility.TEST_ID,
            ImageAppCardUtility.TEST_BUTTON_COMPONENT_ID,
            AppCardMessageConstants.InteractionMessageConstants.MSG_INTERACTION_ON_CLICK,
        )

        phaser.arriveAndAwaitAdvance()
        verify(appCardTimer).destroy()
    }

    @Test
    fun testSendAppCardContextUpdate_contentProviderCalledWithCorrectInputBundle() {
        val captor = argumentCaptor<Bundle>()
        setupActiveAppCard()

        appCardHost.sendAppCardContextUpdate(
            appCardContext,
            applicationIdentifier,
            ImageAppCardUtility.TEST_ID,
        )

        phaser.arriveAndAwaitAdvance()
        verify(contentProviderClient)
            .call(
                eq(AppCardMessageConstants.MSG_APP_CARD_CONTEXT_UPDATE),
                isNull(),
                captor.capture(),
            )
        assertThat(captor.firstValue.getString(AppCardContentProvider.BUNDLE_KEY_APP_CARD_ID))
            .isEqualTo(ImageAppCardUtility.TEST_ID)
        assertThat(
                AppCardContext.fromBundle(
                    captor.firstValue.getBundle(AppCardContentProvider.BUNDLE_KEY_APP_CARD_CONTEXT)
                )
            )
            .isEqualTo(appCardContext)
    }

    @Test
    fun testSendAppCardContextUpdate_timerResetAppCardTimerAndRequestUpdateCalled() {
        setupActiveAppCard()

        appCardHost.sendAppCardContextUpdate(
            appCardContext,
            applicationIdentifier,
            ImageAppCardUtility.TEST_ID,
        )

        phaser.arriveAndAwaitAdvance()
        verify(appCardTimer).resetAppCardTimerAndRequestUpdate()
    }

    @Test
    fun testSendAppCardContextUpdate_remoteException_listenerCalledCorrectly() {
        setupActiveAppCard()
        whenever(
                contentProviderClient.call(
                    eq(AppCardMessageConstants.MSG_APP_CARD_CONTEXT_UPDATE),
                    isNull(),
                    any<Bundle>(),
                )
            )
            .thenThrow(RemoteException())
        val expectedMsg =
            "Exception occurred when calling (with MSG_APP_CARD_CONTEXT_UPDATE)" +
                " app card content provider (ID: ID)"
        val expectedCauseString = "android.os.RemoteException"

        appCardHost.sendAppCardContextUpdate(
            appCardContext,
            applicationIdentifier,
            ImageAppCardUtility.TEST_ID,
        )

        phaser.arriveAndAwaitAdvance()
        assertThat(actualAppCardIdentifier).isEqualTo(applicationIdentifier)
        assertThat(actualThrowable?.message).isEqualTo(expectedMsg)
        assertThat(actualThrowable?.cause?.toString()).isEqualTo(expectedCauseString)
    }

    @Test
    fun testSendAppCardContextUpdate_deadObjectException_listenerResultCorrect() {
        setupActiveAppCard()
        whenever(
                contentProviderClient.call(
                    eq(AppCardMessageConstants.MSG_APP_CARD_CONTEXT_UPDATE),
                    isNull(),
                    any<Bundle>(),
                )
            )
            .thenThrow(DeadObjectException())
        val expectedMsg =
            "Exception occurred when calling (with MSG_APP_CARD_CONTEXT_UPDATE)" +
                " app card content provider (ID: ID)"
        val expectedCauseString = "android.os.DeadObjectException"

        appCardHost.sendAppCardContextUpdate(
            appCardContext,
            applicationIdentifier,
            ImageAppCardUtility.TEST_ID,
        )

        phaser.arriveAndAwaitAdvance()
        assertThat(actualAppCardIdentifier).isEqualTo(applicationIdentifier)
        assertThat(actualThrowable?.message).isEqualTo(expectedMsg)
        assertThat(actualThrowable?.cause?.toString()).isEqualTo(expectedCauseString)
    }

    @Test
    fun testSendAppCardContextUpdate_deadObjectException_contentProviderClientClosed() {
        setupActiveAppCard()
        whenever(
                contentProviderClient.call(
                    eq(AppCardMessageConstants.MSG_APP_CARD_CONTEXT_UPDATE),
                    isNull(),
                    any<Bundle>(),
                )
            )
            .thenThrow(DeadObjectException())

        appCardHost.sendAppCardContextUpdate(
            appCardContext,
            applicationIdentifier,
            ImageAppCardUtility.TEST_ID,
        )

        phaser.arriveAndAwaitAdvance()
        verify(contentProviderClient).close()
    }

    @Test
    fun testSendAppCardContextUpdate_deadObjectException_unregisterContentObserverCalled() {
        setupActiveAppCard()
        whenever(
                contentProviderClient.call(
                    eq(AppCardMessageConstants.MSG_APP_CARD_CONTEXT_UPDATE),
                    isNull(),
                    any<Bundle>(),
                )
            )
            .thenThrow(DeadObjectException())

        appCardHost.sendAppCardContextUpdate(
            appCardContext,
            applicationIdentifier,
            ImageAppCardUtility.TEST_ID,
        )

        phaser.arriveAndAwaitAdvance()
        verify(contentResolver).unregisterContentObserver(any<AppCardObserver>())
    }

    @Test
    fun testSendAppCardContextUpdate_deadObjectException_timerDestroyed() {
        setupActiveAppCard()
        whenever(
                contentProviderClient.call(
                    eq(AppCardMessageConstants.MSG_APP_CARD_CONTEXT_UPDATE),
                    isNull(),
                    any<Bundle>(),
                )
            )
            .thenThrow(DeadObjectException())

        appCardHost.sendAppCardContextUpdate(
            appCardContext,
            applicationIdentifier,
            ImageAppCardUtility.TEST_ID,
        )

        phaser.arriveAndAwaitAdvance()
        verify(appCardTimer).destroy()
    }

    @Test
    fun testHandleAppCardAppComponentRequest_absentIdentifier_isComponentReadyForUpdateNotCalled() {
        appCardHost.handleAppCardAppComponentRequest(
            TEST_PACKAGE,
            ImageAppCardUtility.TEST_ID,
            ImageAppCardUtility.TEST_PROGRESS_BAR_COMPONENT_ID,
        )

        verify(appCardTimer, never()).isComponentReadyForUpdate(any<String>())
    }

    @Test
    fun testHandleAppCardAppComponentRequest_inactiveAppCard_isComponentReadyForUpdateNotCalled() {
        appCardHost.handleAppCardAppComponentRequest(
            TEST_AUTHORITY,
            ImageAppCardUtility.TEST_ID,
            ImageAppCardUtility.TEST_PROGRESS_BAR_COMPONENT_ID,
        )

        verify(appCardTimer, never()).isComponentReadyForUpdate(any<String>())
    }

    @Test
    fun testHandleAppCardAppComponentRequest_isComponentReadyForUpdateCalled() {
        setupActiveAppCard()

        appCardHost.handleAppCardAppComponentRequest(
            TEST_AUTHORITY,
            ImageAppCardUtility.TEST_ID,
            ImageAppCardUtility.TEST_PROGRESS_BAR_COMPONENT_ID,
        )

        verify(appCardTimer)
            .isComponentReadyForUpdate(eq(ImageAppCardUtility.TEST_PROGRESS_BAR_COMPONENT_ID))
    }

    @Test
    fun testHandleAppCardAppComponentRequest_componentNotReady_contentProviderNotCalled() {
        setupActiveAppCard()

        appCardHost.handleAppCardAppComponentRequest(
            TEST_AUTHORITY,
            ImageAppCardUtility.TEST_ID,
            ImageAppCardUtility.TEST_PROGRESS_BAR_COMPONENT_ID,
        )

        verify(contentProviderClient, never()).call(any<String>(), isNull(), any<Bundle>())
    }

    @Test
    fun testHandleAppCardAppComponentRequest_componentReady_contentProviderCalled() {
        val captor = argumentCaptor<Bundle>()
        setupActiveAppCard()
        whenever(
                appCardTimer.isComponentReadyForUpdate(
                    eq(ImageAppCardUtility.TEST_PROGRESS_BAR_COMPONENT_ID)
                )
            )
            .thenReturn(true)

        appCardHost.handleAppCardAppComponentRequest(
            TEST_AUTHORITY,
            ImageAppCardUtility.TEST_ID,
            ImageAppCardUtility.TEST_PROGRESS_BAR_COMPONENT_ID,
        )

        phaser.arriveAndAwaitAdvance()
        verify(contentProviderClient)
            .call(
                eq(AppCardMessageConstants.MSG_APP_CARD_COMPONENT_UPDATE),
                isNull(),
                captor.capture(),
            )
        assertThat(captor.firstValue.getString(AppCardContentProvider.BUNDLE_KEY_APP_CARD_ID))
            .isEqualTo(ImageAppCardUtility.TEST_ID)
        assertThat(
                captor.firstValue.getString(AppCardContentProvider.BUNDLE_KEY_APP_CARD_COMPONENT_ID)
            )
            .isEqualTo(ImageAppCardUtility.TEST_PROGRESS_BAR_COMPONENT_ID)
    }

    @Test
    fun testHandleAppCardAppComponentRequest_nullBundleFromProvider_listenerCorrectResult() {
        setupActiveAppCard()
        whenever(
                appCardTimer.isComponentReadyForUpdate(
                    eq(ImageAppCardUtility.TEST_PROGRESS_BAR_COMPONENT_ID)
                )
            )
            .thenReturn(true)
        whenever(
                contentProviderClient.call(
                    eq(AppCardMessageConstants.MSG_APP_CARD_COMPONENT_UPDATE),
                    isNull(),
                    any<Bundle>(),
                )
            )
            .thenReturn(null)
        val expectedMsg =
            "Exception occurred when calling (with MSG_APP_CARD_COMPONENT_UPDATE)" +
                " app card content provider (ID: ID#TEST_PROGRESS_BAR_COMPONENT_ID)"
        val expectedCauseString = "java.lang.IllegalStateException: Result from call is null"

        appCardHost.handleAppCardAppComponentRequest(
            TEST_AUTHORITY,
            ImageAppCardUtility.TEST_ID,
            ImageAppCardUtility.TEST_PROGRESS_BAR_COMPONENT_ID,
        )

        phaser.arriveAndAwaitAdvance()
        assertThat(actualAppCardIdentifier).isEqualTo(applicationIdentifier)
        assertThat(actualThrowable?.message).isEqualTo(expectedMsg)
        assertThat(actualThrowable?.cause?.toString()).isEqualTo(expectedCauseString)
    }

    @Test
    fun testHandleAppCardAppComponentRequest_remoteException_listenerCorrectResult() {
        setupActiveAppCard()
        whenever(
                appCardTimer.isComponentReadyForUpdate(
                    eq(ImageAppCardUtility.TEST_PROGRESS_BAR_COMPONENT_ID)
                )
            )
            .thenReturn(true)
        whenever(
                contentProviderClient.call(
                    eq(AppCardMessageConstants.MSG_APP_CARD_COMPONENT_UPDATE),
                    isNull(),
                    any<Bundle>(),
                )
            )
            .thenThrow(RemoteException())
        val expectedMsg =
            "Exception occurred when calling (with MSG_APP_CARD_COMPONENT_UPDATE)" +
                " app card content provider (ID: ID#TEST_PROGRESS_BAR_COMPONENT_ID)"
        val expectedCauseString = "android.os.RemoteException"

        appCardHost.handleAppCardAppComponentRequest(
            TEST_AUTHORITY,
            ImageAppCardUtility.TEST_ID,
            ImageAppCardUtility.TEST_PROGRESS_BAR_COMPONENT_ID,
        )

        phaser.arriveAndAwaitAdvance()
        assertThat(actualAppCardIdentifier).isEqualTo(applicationIdentifier)
        assertThat(actualThrowable?.message).isEqualTo(expectedMsg)
        assertThat(actualThrowable?.cause?.toString()).isEqualTo(expectedCauseString)
    }

    @Test
    fun testHandleAppCardAppComponentRequest_deadObjectException_listenerResultCorrect() {
        setupActiveAppCard()
        whenever(
                appCardTimer.isComponentReadyForUpdate(
                    eq(ImageAppCardUtility.TEST_PROGRESS_BAR_COMPONENT_ID)
                )
            )
            .thenReturn(true)
        whenever(
                contentProviderClient.call(
                    eq(AppCardMessageConstants.MSG_APP_CARD_COMPONENT_UPDATE),
                    isNull(),
                    any<Bundle>(),
                )
            )
            .thenThrow(DeadObjectException())
        val expectedMsg =
            "Exception occurred when calling (with MSG_APP_CARD_COMPONENT_UPDATE)" +
                " app card content provider (ID: ID#TEST_PROGRESS_BAR_COMPONENT_ID)"
        val expectedCauseString = "android.os.DeadObjectException"

        appCardHost.handleAppCardAppComponentRequest(
            TEST_AUTHORITY,
            ImageAppCardUtility.TEST_ID,
            ImageAppCardUtility.TEST_PROGRESS_BAR_COMPONENT_ID,
        )

        phaser.arriveAndAwaitAdvance()
        assertThat(actualAppCardIdentifier).isEqualTo(applicationIdentifier)
        assertThat(actualThrowable?.message).isEqualTo(expectedMsg)
        assertThat(actualThrowable?.cause?.toString()).isEqualTo(expectedCauseString)
    }

    @Test
    fun testHandleAppCardAppComponentRequest_deadObjectException_contentProviderClientClosed() {
        setupActiveAppCard()
        whenever(
                appCardTimer.isComponentReadyForUpdate(
                    eq(ImageAppCardUtility.TEST_PROGRESS_BAR_COMPONENT_ID)
                )
            )
            .thenReturn(true)
        whenever(
                contentProviderClient.call(
                    eq(AppCardMessageConstants.MSG_APP_CARD_COMPONENT_UPDATE),
                    isNull(),
                    any<Bundle>(),
                )
            )
            .thenThrow(DeadObjectException())

        appCardHost.handleAppCardAppComponentRequest(
            TEST_AUTHORITY,
            ImageAppCardUtility.TEST_ID,
            ImageAppCardUtility.TEST_PROGRESS_BAR_COMPONENT_ID,
        )

        phaser.arriveAndAwaitAdvance()
        verify(contentProviderClient).close()
    }

    @Test
    fun testHandleAppCardAppComponentRequest_deadObjectException_unregisterContentObserverCalled() {
        setupActiveAppCard()
        whenever(
                appCardTimer.isComponentReadyForUpdate(
                    eq(ImageAppCardUtility.TEST_PROGRESS_BAR_COMPONENT_ID)
                )
            )
            .thenReturn(true)
        whenever(
                contentProviderClient.call(
                    eq(AppCardMessageConstants.MSG_APP_CARD_COMPONENT_UPDATE),
                    isNull(),
                    any<Bundle>(),
                )
            )
            .thenThrow(DeadObjectException())

        appCardHost.handleAppCardAppComponentRequest(
            TEST_AUTHORITY,
            ImageAppCardUtility.TEST_ID,
            ImageAppCardUtility.TEST_PROGRESS_BAR_COMPONENT_ID,
        )

        phaser.arriveAndAwaitAdvance()
        verify(contentResolver).unregisterContentObserver(any<AppCardObserver>())
    }

    @Test
    fun testHandleAppCardAppComponentRequest_deadObjectException_timerDestroyed() {
        setupActiveAppCard()
        whenever(
                appCardTimer.isComponentReadyForUpdate(
                    eq(ImageAppCardUtility.TEST_PROGRESS_BAR_COMPONENT_ID)
                )
            )
            .thenReturn(true)
        whenever(
                contentProviderClient.call(
                    eq(AppCardMessageConstants.MSG_APP_CARD_COMPONENT_UPDATE),
                    isNull(),
                    any<Bundle>(),
                )
            )
            .thenThrow(DeadObjectException())

        appCardHost.handleAppCardAppComponentRequest(
            TEST_AUTHORITY,
            ImageAppCardUtility.TEST_ID,
            ImageAppCardUtility.TEST_PROGRESS_BAR_COMPONENT_ID,
        )

        phaser.arriveAndAwaitAdvance()
        verify(appCardTimer).destroy()
    }

    @Test
    fun testHandleAppCardAppComponentRequest_noAppCardTransportReturned_listenerCorrectResult() {
        setupActiveAppCard()
        whenever(
                appCardTimer.isComponentReadyForUpdate(
                    eq(ImageAppCardUtility.TEST_PROGRESS_BAR_COMPONENT_ID)
                )
            )
            .thenReturn(true)
        whenever(
                contentProviderClient.call(
                    eq(AppCardMessageConstants.MSG_APP_CARD_COMPONENT_UPDATE),
                    isNull(),
                    any<Bundle>(),
                )
            )
            .thenReturn(Bundle())
        val expectedMsg =
            "Exception occurred when calling (with MSG_APP_CARD_COMPONENT_UPDATE)" +
                " app card content provider (ID: ID#TEST_PROGRESS_BAR_COMPONENT_ID)"
        val expectedCauseString =
            "java.lang.IllegalStateException: " + "App card transport missing from bundle"

        appCardHost.handleAppCardAppComponentRequest(
            TEST_AUTHORITY,
            ImageAppCardUtility.TEST_ID,
            ImageAppCardUtility.TEST_PROGRESS_BAR_COMPONENT_ID,
        )

        phaser.arriveAndAwaitAdvance()
        assertThat(actualAppCardIdentifier).isEqualTo(applicationIdentifier)
        assertThat(actualThrowable?.message).isEqualTo(expectedMsg)
        assertThat(actualThrowable?.cause?.toString()).isEqualTo(expectedCauseString)
    }

    @Test
    fun testHandleAppCardAppComponentRequest_noComponentInAppCardTransport_listenerCorrectResult() {
        val transport = AppCardTransport(ImageAppCardUtility.imageAppCard)
        val cursor = MatrixCursor(arrayOf(AppCardContentProvider.CURSOR_COLUMN_APP_CARD_TRANSPORT))
        cursor.addRow(arrayOf(ParcelableUtils.parcelableToBytes(transport)))
        val uri =
            Uri.Builder()
                .authority(TEST_AUTHORITY)
                .appendPath(AppCardMessageConstants.MSG_APP_CARD_ADDED)
                .build()
        whenever(contentProviderClient.query(eq(uri), isNull(), any<Bundle>(), isNull()))
            .thenReturn(cursor)
        appCardHost.requestAppCard(
            appCardContext,
            applicationIdentifier,
            ImageAppCardUtility.TEST_ID,
        )
        phaser.arriveAndAwaitAdvance()
        reset(contentProviderClient, appCardTimer)
        whenever(
                appCardTimer.isComponentReadyForUpdate(
                    eq(ImageAppCardUtility.TEST_PROGRESS_BAR_COMPONENT_ID)
                )
            )
            .thenReturn(true)
        val bundle = Bundle()
        bundle.putParcelable(
            AppCardContentProvider.BUNDLE_KEY_APP_CARD_COMPONENT,
            AppCardTransport(ImageAppCardUtility.imageAppCard),
        )
        whenever(
                contentProviderClient.call(
                    eq(AppCardMessageConstants.MSG_APP_CARD_COMPONENT_UPDATE),
                    isNull(),
                    any<Bundle>(),
                )
            )
            .thenReturn(bundle)
        val expectedMsg =
            "Exception occurred when calling (with MSG_APP_CARD_COMPONENT_UPDATE)" +
                " app card content provider (ID: ID#TEST_PROGRESS_BAR_COMPONENT_ID)"
        val expectedCauseString =
            "java.lang.IllegalStateException: " + "App card component missing from transport"

        appCardHost.handleAppCardAppComponentRequest(
            TEST_AUTHORITY,
            ImageAppCardUtility.TEST_ID,
            ImageAppCardUtility.TEST_PROGRESS_BAR_COMPONENT_ID,
        )

        phaser.arriveAndAwaitAdvance()
        assertThat(actualAppCardIdentifier).isEqualTo(applicationIdentifier)
        assertThat(actualThrowable?.message).isEqualTo(expectedMsg)
        assertThat(actualThrowable?.cause?.toString()).isEqualTo(expectedCauseString)
    }

    private fun setupActiveAppCard() {
        val transport = AppCardTransport(ImageAppCardUtility.imageAppCard)
        val cursor = MatrixCursor(arrayOf(AppCardContentProvider.CURSOR_COLUMN_APP_CARD_TRANSPORT))
        cursor.addRow(arrayOf(ParcelableUtils.parcelableToBytes(transport)))
        val uri =
            Uri.Builder()
                .authority(TEST_AUTHORITY)
                .appendPath(AppCardMessageConstants.MSG_APP_CARD_ADDED)
                .build()
        whenever(contentProviderClient.query(eq(uri), isNull(), any<Bundle>(), isNull()))
            .thenReturn(cursor)

        appCardHost.requestAppCard(
            appCardContext,
            applicationIdentifier,
            ImageAppCardUtility.TEST_ID,
        )

        phaser.arriveAndAwaitAdvance()
        reset(contentProviderClient, appCardTimer)
    }

    companion object {
        private const val TEST_UPDATE_RATE_MS = 5000
        private const val TEST_FAST_UPDATE_RATE_MS = 500
        private const val TEST_AUTHORITY = "TEST_AUTHORITY"
        private const val TEST_PACKAGE = "TEST_PACKAGE"
        private const val TEST_CLASS = "TEST_CLASS"
        private const val TEST_PERMISSION = "TEST_PERMISSION"
    }
}
