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

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.android.car.appcard.ImageAppCard
import com.google.common.truth.Truth.assertThat
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.reset
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@RunWith(AndroidJUnit4::class)
class AppCardTimerTest {
    private var actualIdentifier: ApplicationIdentifier? = null
    private var actualAppCardId: String? = null
    private val updateReadyListener =
        object : AppCardTimer.UpdateReadyListener {
            override fun appCardIsReadyForUpdate(
                identifier: ApplicationIdentifier?,
                appCardId: String?,
            ) {
                actualIdentifier = identifier
                actualAppCardId = appCardId
            }
        }
    private lateinit var appCardTimer: AppCardTimer
    private val future: ScheduledFuture<*> = mock<ScheduledFuture<Any>>()
    private val scheduledExecutorService: ScheduledExecutorService =
        mock<ScheduledExecutorService> {
            on { schedule(any(), any(), any()) } doReturn future
            on { scheduleAtFixedRate(any(), any(), any(), any()) } doReturn future
        }
    private val identifier = ApplicationIdentifier(TEST_AUTHORITY, TEST_PACKAGE)

    @Before
    fun setup() {
        appCardTimer =
            AppCardTimer(
                updateReadyListener,
                TEST_UPDATE_RATE_MS,
                TEST_FAST_UPDATE_RATE_MS,
                scheduledExecutorService,
            )
    }

    @Test
    fun testUpdateAppCard_imageAppCard_timerCancelled() {
        appCardTimer.updateAppCard(AppCardContainer(identifier, ImageAppCardUtility.imageAppCard))
        reset(future)

        appCardTimer.updateAppCard(AppCardContainer(identifier, ImageAppCardUtility.imageAppCard))

        verify(future).cancel(eq(false))
    }

    @Test
    fun testUpdateAppCard_imageAppCard_withProgressBar_componentTimerScheduled() {
        appCardTimer.updateAppCard(
            AppCardContainer(identifier, ImageAppCardUtility.progressBarButtonCard)
        )

        val captor = argumentCaptor<Runnable>()
        verify(scheduledExecutorService)
            .schedule(
                captor.capture(),
                eq(TEST_FAST_UPDATE_RATE_MS.toLong()),
                eq(TimeUnit.MILLISECONDS),
            )
        captor.firstValue.run()
        assertThat(
                appCardTimer.isComponentReadyForUpdate(
                    ImageAppCardUtility.TEST_PROGRESS_BAR_COMPONENT_ID
                )
            )
            .isTrue()
    }

    @Test
    fun testUpdateAppCard_imageAppCard_withProgressBar_appCardTimerScheduled() {
        appCardTimer.updateAppCard(AppCardContainer(identifier, ImageAppCardUtility.imageAppCard))

        val captor = argumentCaptor<Runnable>()
        verify(scheduledExecutorService)
            .scheduleAtFixedRate(
                captor.capture(),
                eq(TEST_UPDATE_RATE_MS.toLong()),
                eq(TEST_UPDATE_RATE_MS.toLong()),
                eq(TimeUnit.MILLISECONDS),
            )
        captor.firstValue.run()
        assertThat(actualIdentifier).isEqualTo(identifier)
        assertThat(actualAppCardId).isEqualTo(ImageAppCardUtility.TEST_ID)
    }

    @Test
    fun testUpdateAppCard_imageAppCard_withoutProgressBar_componentTimerNotScheduled() {
        val imageAppCard =
            ImageAppCard.newBuilder(ImageAppCardUtility.TEST_ID)
                .setImage(ImageAppCardUtility.image)
                .setHeader(ImageAppCardUtility.header)
                .setPrimaryText(ImageAppCardUtility.TEST_PRIMARY_TEXT)
                .setSecondaryText(ImageAppCardUtility.TEST_SECONDARY_TEXT)
                .build()

        appCardTimer.updateAppCard(AppCardContainer(identifier, imageAppCard))

        verify(scheduledExecutorService, never())
            .schedule(any<Runnable>(), eq(TEST_FAST_UPDATE_RATE_MS.toLong()), any())
    }

    @Test
    fun testIsComponentReadyForUpdate_nonExistingComponent_returnFalse() {
        assertThat(appCardTimer.isComponentReadyForUpdate(TEST_COMPONENT_ID)).isFalse()
    }

    @Test
    fun testIsComponentReadyForUpdate_existingComponent_returnTrue() {
        setupProgressBarComponentReady()

        assertThat(
                appCardTimer.isComponentReadyForUpdate(
                    ImageAppCardUtility.TEST_PROGRESS_BAR_COMPONENT_ID
                )
            )
            .isTrue()
    }

    @Test
    fun testDestroy_timerCancelled() {
        appCardTimer.updateAppCard(AppCardContainer(identifier, ImageAppCardUtility.imageAppCard))

        appCardTimer.destroy()

        verify(future).cancel(eq(false))
    }

    @Test
    fun testResetAppCardTimerAndRequestUpdate_noAppCard_timerCancelNotCalled() {
        appCardTimer.resetAppCardTimerAndRequestUpdate()

        verify(future, never()).cancel(any())
    }

    @Test
    fun testResetAppCardTimerAndRequestUpdate_noAppCard_listenerNotCalled() {
        appCardTimer.resetAppCardTimerAndRequestUpdate()

        assertThat(actualIdentifier).isNull()
    }

    @Test
    fun testResetAppCardTimerAndRequestUpdate_noAppCard_timerScheduleNotCalled() {
        appCardTimer.resetAppCardTimerAndRequestUpdate()

        verify(scheduledExecutorService, never()).schedule(any<Runnable>(), any(), any())
    }

    @Test
    fun testResetAppCardTimerAndRequestUpdate_appCard_timerCancelled() {
        appCardTimer.updateAppCard(AppCardContainer(identifier, ImageAppCardUtility.imageAppCard))
        reset(future)

        appCardTimer.resetAppCardTimerAndRequestUpdate()

        verify(future).cancel(eq(false))
    }

    @Test
    fun testResetAppCardTimerAndRequestUpdate_appCard_componentStatusFalse() {
        setupProgressBarComponentReady()

        appCardTimer.resetAppCardTimerAndRequestUpdate()

        assertThat(
                appCardTimer.isComponentReadyForUpdate(
                    ImageAppCardUtility.TEST_PROGRESS_BAR_COMPONENT_ID
                )
            )
            .isFalse()
    }

    @Test
    fun testResetAppCardTimerAndRequestUpdate_appCard_componentTimerTaskSetStatusToTrue() {
        setupProgressBarComponentReady()

        appCardTimer.resetAppCardTimerAndRequestUpdate()

        val captor = argumentCaptor<Runnable>()
        verify(scheduledExecutorService)
            .schedule(
                captor.capture(),
                eq(TEST_FAST_UPDATE_RATE_MS.toLong()),
                eq(TimeUnit.MILLISECONDS),
            )
        captor.firstValue.run()
        assertThat(
                appCardTimer.isComponentReadyForUpdate(
                    ImageAppCardUtility.TEST_PROGRESS_BAR_COMPONENT_ID
                )
            )
            .isTrue()
    }

    @Test
    fun testResetAppCardTimerAndRequestUpdate_appCard_appCardTimerTaskCorrectlySet() {
        appCardTimer.updateAppCard(AppCardContainer(identifier, ImageAppCardUtility.imageAppCard))
        reset(scheduledExecutorService)
        reConfigureMockExecutor()

        appCardTimer.resetAppCardTimerAndRequestUpdate()

        val captor = argumentCaptor<Runnable>()
        verify(scheduledExecutorService)
            .scheduleAtFixedRate(
                captor.capture(),
                eq(TEST_UPDATE_RATE_MS.toLong()),
                eq(TEST_UPDATE_RATE_MS.toLong()),
                eq(TimeUnit.MILLISECONDS),
            )
        captor.firstValue.run()
        assertThat(actualIdentifier).isEqualTo(identifier)
        assertThat(actualAppCardId).isEqualTo(ImageAppCardUtility.TEST_ID)
    }

    @Test
    fun testComponentUpdate_nonExistingComponent_timerNotScheduled() {
        appCardTimer.componentUpdate(TEST_COMPONENT_ID)

        verify(scheduledExecutorService, never()).schedule(any<Runnable>(), any(), any())
    }

    @Test
    fun testComponentUpdate_existingComponent_timerScheduled() {
        setupProgressBarComponentReady()

        appCardTimer.componentUpdate(ImageAppCardUtility.TEST_PROGRESS_BAR_COMPONENT_ID)

        val captor = argumentCaptor<Runnable>()
        verify(scheduledExecutorService)
            .schedule(
                captor.capture(),
                eq(TEST_FAST_UPDATE_RATE_MS.toLong()),
                eq(TimeUnit.MILLISECONDS),
            )
        captor.firstValue.run()
        assertThat(
                appCardTimer.isComponentReadyForUpdate(
                    ImageAppCardUtility.TEST_PROGRESS_BAR_COMPONENT_ID
                )
            )
            .isTrue()
    }

    @Test
    fun testComponentUpdate_existingComponent_componentUpdateStatusIsFalse() {
        setupProgressBarComponentReady()

        appCardTimer.componentUpdate(ImageAppCardUtility.TEST_PROGRESS_BAR_COMPONENT_ID)

        verify(scheduledExecutorService)
            .schedule(
                any<Runnable>(),
                eq(TEST_FAST_UPDATE_RATE_MS.toLong()),
                eq(TimeUnit.MILLISECONDS),
            )
        assertThat(
                appCardTimer.isComponentReadyForUpdate(
                    ImageAppCardUtility.TEST_PROGRESS_BAR_COMPONENT_ID
                )
            )
            .isFalse()
    }

    private fun setupProgressBarComponentReady() {
        appCardTimer.updateAppCard(
            AppCardContainer(identifier, ImageAppCardUtility.progressBarButtonCard)
        )
        val captor = argumentCaptor<Runnable>()
        verify(scheduledExecutorService)
            .schedule(
                captor.capture(),
                eq(TEST_FAST_UPDATE_RATE_MS.toLong()),
                eq(TimeUnit.MILLISECONDS),
            )
        captor.firstValue.run()
        assertThat(
                appCardTimer.isComponentReadyForUpdate(
                    ImageAppCardUtility.TEST_PROGRESS_BAR_COMPONENT_ID
                )
            )
            .isTrue()
        reset(scheduledExecutorService)
        reConfigureMockExecutor()
    }

    private fun reConfigureMockExecutor() {
        doReturn(future).whenever(scheduledExecutorService).schedule(any(), any(), any())
        doReturn(future)
            .whenever(scheduledExecutorService)
            .scheduleAtFixedRate(any(), any(), any(), any())
    }

    companion object {
        private const val TEST_UPDATE_RATE_MS = 5000
        private const val TEST_FAST_UPDATE_RATE_MS = 500
        private const val TEST_COMPONENT_ID = "TEST_COMPONENT_ID"
        private const val TEST_AUTHORITY = "TEST_AUTHORITY"
        private const val TEST_PACKAGE = "TEST_PACKAGE"
    }
}
