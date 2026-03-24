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
package com.android.car.appcard.component

import android.util.Log
import com.android.car.appcard.component.interaction.OnClickListener
import com.android.car.appcard.component.interaction.RoutingActivityIntent
import com.android.car.appcard.internal.proto.Button.ButtonMessage

/**
 * A button is a component that contains:
 * - an [OnClickListener] to handle clicks
 * - some text or an [Image]
 * - can be defined as primary or secondary to tell the presenter how it should be styled
 * - For example, in a media action row, the play/pause button will be primary while the
 *   skip/previous buttons will be secondary
 */
class Button private constructor(builder: Builder) : Component(builder) {
    /** @return an optional string representing the text inside a button */
    var text: String?
        private set

    /** @return an optional [Image] */
    var image: Image?
        private set

    /** @return [ButtonType] */
    var buttonType: ButtonType
        private set

    /** @return an optional [RoutingActivityIntent] */
    var intent: RoutingActivityIntent?
        private set

    /** @return this button's [OnClickListener] */
    var onClickListener: OnClickListener?
        private set

    init {
        text = builder.text
        image = builder.image
        buttonType = builder.buttonType
        onClickListener = builder.onClickListener
        intent = builder.intent
    }

    /** @return protobuf message */
    fun toMessage(): ButtonMessage {
        val builder = ButtonMessage.newBuilder()

        text?.let { if (it.isNotEmpty()) builder.setText(it) }

        image?.let { builder.setImage(it.toMessage()) }

        intent?.let { builder.setIntent(it.toMessage()) }

        return builder.setComponentId(componentId).setType(toButtonType(buttonType)).build()
    }

    /** @return protobuf byte array */
    override fun toByteArray(): ByteArray = toMessage().toByteArray()

    override fun updateComponent(component: Component): Boolean {
        if (component is Image) {
            try {
                imageCheck(component)
            } catch (e: IllegalStateException) {
                Log.e(TAG, "Failed to update image: ${e.message}")
                return false
            }
            if (
                buttonType == ButtonType.NO_BACKGROUND &&
                    component.colorFilter != Image.ColorFilter.TINT
            ) {
                Log.e(
                    TAG,
                    "Failed to update image: ButtonType.NO_BACKGROUND must contain an " +
                        "image with Image.ColorFilter.TINT",
                )
                return false
            }
            return image?.updateComponent(component) ?: false
        }
        if (component !is Button) {
            return false
        }

        if (componentId != component.componentId) return false

        image = component.image
        text = component.text
        buttonType = component.buttonType
        onClickListener = component.onClickListener
        intent = component.intent

        return true
    }

    override fun equals(other: Any?): Boolean {
        if (other === this) return true

        if (other !is Button) return false

        return super.equals(other) &&
            image == other.image &&
            text == other.text &&
            buttonType == other.buttonType &&
            onClickListener == other.onClickListener &&
            intent == other.intent
    }

    override fun hashCode(): Int {
        var result = super.hashCode()
        result = 31 * result + (text?.hashCode() ?: 0)
        result = 31 * result + (image?.hashCode() ?: 0)
        result = 31 * result + buttonType.hashCode()
        result = 31 * result + (onClickListener?.hashCode() ?: 0)
        result = 31 * result + (intent?.hashCode() ?: 0)
        return result
    }

    /**
     * This enum will advise the system on how to style this button.
     * - PRIMARY: The button should be styled as primary
     * - SECONDARY: The button should be styled as secondary
     * - NO_BACKGROUND: The button will not have a background but must contain only a tintable image
     */
    enum class ButtonType {
        PRIMARY,
        SECONDARY,
        NO_BACKGROUND,
    }

    /** A builder of [Button] */
    class Builder : Component.Builder {
        internal var text: String? = null
        internal var image: Image? = null
        internal var buttonType: ButtonType
        internal var onClickListener: OnClickListener?
        internal var intent: RoutingActivityIntent? = null

        internal constructor(buttonMessage: ButtonMessage) : super(buttonMessage.componentId) {
            if (buttonMessage.hasText()) text = buttonMessage.text

            if (buttonMessage.hasImage()) {
                image = Image.fromMessage(buttonMessage.image)
                image?.let { imageCheck(it) }
            }

            buttonType =
                when (buttonMessage.type) {
                    com.android.car.appcard.internal.proto.Button.ButtonType.PRIMARY ->
                        ButtonType.PRIMARY

                    com.android.car.appcard.internal.proto.Button.ButtonType.SECONDARY ->
                        ButtonType.SECONDARY

                    com.android.car.appcard.internal.proto.Button.ButtonType.NO_BACKGROUND ->
                        ButtonType.NO_BACKGROUND

                    else ->
                        throw IllegalStateException(
                            "ButtonType must not be unspecified nor unrecognized"
                        )
                }

            onClickListener = null

            if (buttonMessage.hasIntent()) {
                intent = RoutingActivityIntent.fromMessage(buttonMessage.intent)
            }
        }

        internal constructor(
            componentId: String,
            type: ButtonType,
            onClickListener: OnClickListener,
        ) : super(componentId) {
            this.onClickListener = onClickListener
            text = null
            image = null
            buttonType = type
            intent = null
        }

        /**
         * Set text for the button
         *
         * Text or an [Image] must exist
         */
        fun setText(text: String): Builder {
            this.text = text
            return this
        }

        /**
         * Set [Image] for the button
         *
         * Text or an [Image] must exist
         */
        fun setImage(image: Image): Builder {
            imageCheck(image)

            this.image = image
            return this
        }

        /** Set [RoutingActivityIntent] for the button */
        fun setIntent(intent: RoutingActivityIntent): Builder {
            this.intent = intent
            return this
        }

        /** @return [Button] built using this builder */
        fun build(): Button {
            check(!(image == null && text == null)) { "Image or text must be defined" }
            if (buttonType == ButtonType.NO_BACKGROUND) {
                val errMsg =
                    "ButtonType.NO_BACKGROUND must contain an image with Image.ColorFilter.TINT"
                image?.let { check(it.colorFilter == Image.ColorFilter.TINT) { errMsg } }
                    ?: throw IllegalStateException(errMsg)

                check(text == null) { "ButtonType.NO_BACKGROUND must not contain text" }
            }
            return Button(builder = this)
        }
    }

    companion object {
        private const val TAG = "Button"
        private const val IMAGE_SIZE_LIMIT = 10000

        /** @return instance of [Builder] */
        @JvmStatic
        fun newBuilder(componentId: String, type: ButtonType, onClickListener: OnClickListener) =
            Builder(componentId, type, onClickListener)

        private fun toButtonType(
            buttonType: ButtonType
        ): com.android.car.appcard.internal.proto.Button.ButtonType {
            return when (buttonType) {
                ButtonType.SECONDARY ->
                    com.android.car.appcard.internal.proto.Button.ButtonType.SECONDARY

                ButtonType.NO_BACKGROUND ->
                    com.android.car.appcard.internal.proto.Button.ButtonType.NO_BACKGROUND

                else -> com.android.car.appcard.internal.proto.Button.ButtonType.PRIMARY
            }
        }

        /** @return an instance of [Button] from [ButtonMessage] */
        @JvmStatic fun fromMessage(buttonMessage: ButtonMessage) = Builder(buttonMessage).build()

        private fun imageCheck(image: Image) {
            check(image.contentScale == Image.ContentScale.FILL_BOUNDS) {
                "Image must be ContentScale.FILL_BOUNDS"
            }

            image.imageData?.let {
                val size = it.height * it.width
                check(size <= IMAGE_SIZE_LIMIT) { "Image size must be <= $IMAGE_SIZE_LIMIT" }
            }
        }
    }
}
