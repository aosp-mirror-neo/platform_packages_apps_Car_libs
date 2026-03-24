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

import com.android.car.appcard.ImageAppCard
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentMap
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit
import java.util.function.Consumer

internal class AppCardTimer(
    private val listener: UpdateReadyListener,
    private val updateRateMs: Int,
    private val fastUpdateRateMs: Int,
    private val scheduledExecutorService: ScheduledExecutorService,
) {
    private val componentUpdateStatusMap: ConcurrentMap<String, Boolean>
    private val componentUpdateFutureMap: ConcurrentMap<String, ScheduledFuture<*>>
    private var identifier: ApplicationIdentifier? = null
    private var appCardId: String? = null
    private var refreshFuture: ScheduledFuture<*>? = null

    init {
        componentUpdateStatusMap = ConcurrentHashMap()
        componentUpdateFutureMap = ConcurrentHashMap()
    }

    fun updateAppCard(appCardContainer: AppCardContainer) {
        if (appCardContainer.appCard !is ImageAppCard) return

        handleImageAppCardUpdate(appCardContainer.appCard as ImageAppCard, appCardContainer.appId)
    }

    private fun handleImageAppCardUpdate(imageAppCard: ImageAppCard, id: ApplicationIdentifier) {
        synchronized(lock = this) {
            cancelAllFutures()

            componentUpdateStatusMap.clear()

            identifier = id
            appCardId = imageAppCard.id

            imageAppCard.progressBar?.let {
                componentUpdateStatusMap[it.componentId] = false
                val future =
                    scheduledExecutorService.schedule(
                        { componentUpdateStatusMap[it.componentId] = true },
                        fastUpdateRateMs.toLong(),
                        TimeUnit.MILLISECONDS,
                    )
                componentUpdateFutureMap[it.componentId] = future
            }

            refreshFuture =
                scheduledExecutorService.scheduleAtFixedRate(
                    { listener.appCardIsReadyForUpdate(identifier, appCardId) },
                    updateRateMs.toLong(),
                    updateRateMs.toLong(),
                    TimeUnit.MILLISECONDS,
                )
        }
    }

    fun resetAppCardTimerAndRequestUpdate() {
        synchronized(lock = this) {
            appCardId ?: return
            identifier ?: return

            cancelAllFutures()

            listener.appCardIsReadyForUpdate(identifier, appCardId)

            componentUpdateStatusMap.replaceAll { _, _ -> false }

            componentUpdateStatusMap.keys.forEach(
                Consumer { componentId: String ->
                    val future =
                        scheduledExecutorService.schedule(
                            { componentUpdateStatusMap[componentId] = true },
                            fastUpdateRateMs.toLong(),
                            TimeUnit.MILLISECONDS,
                        )
                    componentUpdateFutureMap[componentId] = future
                }
            )

            refreshFuture =
                scheduledExecutorService.scheduleAtFixedRate(
                    { listener.appCardIsReadyForUpdate(identifier, appCardId) },
                    updateRateMs.toLong(),
                    updateRateMs.toLong(),
                    TimeUnit.MILLISECONDS,
                )
        }
    }

    fun isComponentReadyForUpdate(componentId: String): Boolean {
        synchronized(lock = this) {
            val defaultValue = false
            return componentUpdateStatusMap.getOrDefault(componentId, defaultValue)
        }
    }

    fun destroy() {
        synchronized(lock = this) {
            cancelAllFutures()
            componentUpdateStatusMap.clear()
        }
    }

    fun componentUpdate(componentId: String) {
        synchronized(lock = this) {
            if (!componentUpdateStatusMap.containsKey(componentId)) return

            componentUpdateStatusMap[componentId] = false
            componentUpdateFutureMap[componentId]?.cancel(false)

            val future =
                scheduledExecutorService.schedule(
                    { componentUpdateStatusMap[componentId] = true },
                    fastUpdateRateMs.toLong(),
                    TimeUnit.MILLISECONDS,
                )
            componentUpdateFutureMap[componentId] = future
        }
    }

    private fun cancelAllFutures() {
        refreshFuture?.cancel(false)
        refreshFuture = null
        componentUpdateFutureMap.values.forEach { it.cancel(false) }
        componentUpdateFutureMap.clear()
    }

    internal interface UpdateReadyListener {
        fun appCardIsReadyForUpdate(identifier: ApplicationIdentifier?, appCardId: String?)
    }
}
