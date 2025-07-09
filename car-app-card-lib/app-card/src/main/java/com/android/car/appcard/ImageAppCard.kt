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

import android.util.Log
import com.android.car.appcard.component.Button
import com.android.car.appcard.component.Component
import com.android.car.appcard.component.Header
import com.android.car.appcard.component.Image
import com.android.car.appcard.component.ProgressBar
import com.android.car.appcard.internal.proto.AppCard.AppCardMessage
import com.android.car.appcard.internal.proto.ImageAppCard.ButtonList
import com.android.car.appcard.internal.proto.ImageAppCard.ImageAppCardMessage

/**
 * An [ImageAppCard] is a [AppCard] with a stacked structure containing:
 * - An application specific ID
 * - A optional [Image]
 * - An optional [Header]
 * - An optional primary text
 * - An optional secondary text
 * - An optional [ProgressBar]
 * - An optional list of [Button]s
 */
class ImageAppCard private constructor(builder: Builder) : AppCard(builder.id) {
    /** @return optional [Image] */
    var image: Image?
        private set

    /** @return optional [Header] */
    var header: Header?
        private set

    /** @return optional primary text */
    var primaryText: String?
        private set

    /** @return optional secondary text */
    var secondaryText: String?
        private set

    /** @return optional [ProgressBar] */
    var progressBar: ProgressBar?
        private set

    val buttons: MutableList<Button>
        /** @return list of [Button]s */
        get() = field.toMutableList()

    init {
        image = builder.image
        header = builder.header
        primaryText = builder.primaryText
        secondaryText = builder.secondaryText
        progressBar = builder.progressBar
        buttons = builder.buttons
    }

    /** @return protobuf message */
    fun toMessage(): ImageAppCardMessage {
        val buttonList = ButtonList.newBuilder()
        for (button in buttons) {
            buttonList.addButtons(button.toMessage())
        }

        val builder = ImageAppCardMessage.newBuilder()

        image?.let { builder.setImage(it.toMessage()) }

        header?.let { builder.setHeader(it.toMessage()) }

        progressBar?.let { builder.setProgressBar(it.toMessage()) }

        primaryText?.let { builder.setPrimaryText(it) }

        secondaryText?.let { builder.setSecondaryText(it) }

        if (buttons.isNotEmpty()) builder.setButtonList(buttonList.build())

        return builder.setAppCard(AppCardMessage.newBuilder().setId(id).build()).build()
    }

    override fun updateComponent(component: Component) {
        header?.let { if (it.updateComponent(component)) return }

        progressBar?.let { if (it.updateComponent(component)) return }

        for (button in buttons) {
            if (button.updateComponent(component)) return
        }

        if (component is Image) {
            try {
                imageCheck(component)
            } catch (e: IllegalStateException) {
                Log.d(TAG, "Failed to update image: ${e.message}")
                return
            }
            image?.let { if (it.updateComponent(component)) return }
        }
    }

    override fun equals(other: Any?): Boolean {
        if (other === this) return true

        if (other !is ImageAppCard) return false

        return super.equals(other) &&
            image == other.image &&
            buttons == other.buttons &&
            header == other.header &&
            progressBar == other.progressBar &&
            primaryText == other.primaryText &&
            secondaryText == other.secondaryText
    }

    override fun verifyUniquenessOfComponentIds(): Boolean {
        val componentIds: MutableSet<String> = HashSet()

        image?.let { componentIds.add(it.componentId) }

        header?.let {
            if (!componentIds.contains(it.componentId)) {
                componentIds.add(it.componentId)
            } else {
                Log.d(TAG, "Header has a conflicting ID: " + it.componentId)
                return false
            }

            it.logo?.let { logo ->
                if (!componentIds.contains(logo.componentId)) {
                    componentIds.add(logo.componentId)
                } else {
                    Log.d(TAG, "Header's Image has a conflicting ID: " + logo.componentId)
                    return false
                }
            }
        }

        progressBar?.let {
            if (!componentIds.contains(it.componentId)) {
                componentIds.add(it.componentId)
            } else {
                Log.d(TAG, "Progress Bar has a conflicting ID: " + it.componentId)
                return false
            }
        }

        for (button in buttons) {
            if (!componentIds.contains(button.componentId)) {
                componentIds.add(button.componentId)
            } else {
                Log.d(TAG, "Button has a conflicting ID: " + button.componentId)
                return false
            }

            button.image?.let {
                if (!componentIds.contains(it.componentId)) {
                    componentIds.add(it.componentId)
                } else {
                    Log.d(TAG, "Button's Image has a conflicting ID: " + it.componentId)
                    return false
                }
            }
        }

        return true
    }

    override fun toByteArray(): ByteArray = toMessage().toByteArray()

    override fun hashCode(): Int {
        var result = super.hashCode()
        result = 31 * result + (image?.hashCode() ?: 0)
        result = 31 * result + (header?.hashCode() ?: 0)
        result = 31 * result + (primaryText?.hashCode() ?: 0)
        result = 31 * result + (secondaryText?.hashCode() ?: 0)
        result = 31 * result + (progressBar?.hashCode() ?: 0)

        buttons.forEach { result = 31 * result + it.hashCode() }

        return result
    }

    /** A builder of [ImageAppCard] */
    class Builder {
        internal var id: String
        internal var image: Image?
        internal var header: Header?
        internal var primaryText: String?
        internal var secondaryText: String?
        internal var progressBar: ProgressBar?
        internal var buttons: MutableList<Button>

        internal constructor(imageAppCard: ImageAppCard) {
            this.id = imageAppCard.id
            image = imageAppCard.image
            header = imageAppCard.header
            primaryText = imageAppCard.primaryText
            secondaryText = imageAppCard.secondaryText
            progressBar = imageAppCard.progressBar
            buttons = imageAppCard.buttons
        }

        internal constructor(message: ImageAppCardMessage) : this(message.appCard.id) {
            if (message.hasImage()) {
                image = Image.fromMessage(message.image)
                image?.let { imageCheck(it) }
            }

            if (message.hasHeader()) header = Header.fromMessage(message.header)

            if (message.hasPrimaryText()) primaryText = message.primaryText

            if (message.hasSecondaryText()) secondaryText = message.secondaryText

            if (message.hasProgressBar()) {
                progressBar = ProgressBar.fromMessage(message.progressBar)
            }

            for (buttonMessage in message.buttonList.buttonsList) {
                buttons.add(Button.fromMessage(buttonMessage))
            }
        }

        internal constructor(id: String) {
            this.id = id
            image = null
            header = null
            primaryText = null
            secondaryText = null
            progressBar = null
            buttons = ArrayList()
        }

        /** Set optional [Image] */
        fun setImage(image: Image): Builder {
            imageCheck(image)

            this.image = image
            return this
        }

        /** Set optional [Header] */
        fun setHeader(header: Header): Builder {
            this.header = header
            return this
        }

        /** Set optional primary text */
        fun setPrimaryText(primaryText: String): Builder {
            this.primaryText = primaryText
            return this
        }

        /** Set optional secondary text */
        fun setSecondaryText(secondaryText: String): Builder {
            this.secondaryText = secondaryText
            return this
        }

        /** Set optional [ProgressBar] */
        fun setProgressBar(progressBar: ProgressBar): Builder {
            this.progressBar = progressBar
            return this
        }

        /** Set optional list of [Button]s */
        fun setButtons(buttons: List<Button>): Builder {
            this.buttons = ArrayList()
            this.buttons.addAll(buttons)
            return this
        }

        /** Add a [Button] to the optional list of buttons */
        fun addButton(button: Button): Builder {
            buttons.add(button)
            return this
        }

        /** @return [ImageAppCard] built using this builder */
        fun build(): ImageAppCard {
            check(primaryText != null || secondaryText == null) {
                "Secondary Text cannot be present without Primary Text"
            }

            image
                ?: run {
                    check(primaryText != null) { "Primary Text must be present when image is null" }

                    check(buttons.isNotEmpty() || progressBar != null) {
                        "Buttons or progress bar must be present when image is null"
                    }
                }

            return ImageAppCard(builder = this)
        }
    }

    companion object {
        private const val TAG = "ImageAppCard"
        private const val IMAGE_SIZE_LIMIT = 1000000

        /** @return an instance of [Builder] */
        @JvmStatic fun newBuilder(id: String) = Builder(id)

        /** @return an instance of [Builder] from an existing [ImageAppCard] */
        @JvmStatic fun newBuilder(imageAppCard: ImageAppCard) = Builder(imageAppCard)

        /** @return an instance of [ImageAppCard] from [ImageAppCardMessage] */
        @JvmStatic
        fun fromMessage(imageAppCardMessage: ImageAppCardMessage) =
            Builder(imageAppCardMessage).build()

        private fun imageCheck(image: Image) {
            image.imageData?.let {
                val size = it.height * it.width
                check(size <= IMAGE_SIZE_LIMIT) { "Image size must be <= $IMAGE_SIZE_LIMIT" }
            }
        }
    }
}
