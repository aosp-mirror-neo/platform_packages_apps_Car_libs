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

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import com.android.car.appcard.internal.proto.Image.ImageMessage
import com.google.protobuf.ByteString
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.security.MessageDigest
import java.util.Arrays

/**
 * Image is a component that contains an image and information on how the image should be displayed.
 */
class Image private constructor(builder: Builder) : Component(builder) {
    /** @return [ContentScale] */
    @Volatile
    var contentScale: ContentScale
        private set

    /** @return [ColorFilter] */
    @Volatile
    var colorFilter: ColorFilter
        private set

    /** @return [android.graphics.Bitmap] */
    @Volatile
    var imageData: Bitmap?
        private set

    @Volatile private var imageHash: ByteArray? = null
    @Volatile private var capturedGenerationId: Int = -1
    @Volatile private var compressedData: ByteArray? = null
    @Volatile private var cachedHashCode: Int? = null
    private val lock = Any()

    init {
        imageData = builder.imageData
        contentScale = builder.contentScale
        colorFilter = builder.colorFilter
        updateImageHashAndCache()
    }

    /** @return protobuf message */
    fun toMessage(): ImageMessage {
        synchronized(lock) {
            return ImageMessage.newBuilder()
                .setComponentId(componentId)
                .setImage(getCompressedData()?.let { ByteString.copyFrom(it) })
                .setContentScale(toContentScale(contentScale))
                .setColorFilter(toColorFilter(colorFilter))
                .build()
        }
    }

    /** @return protobuf byte array */
    override fun toByteArray(): ByteArray = toMessage().toByteArray()

    override fun updateComponent(component: Component): Boolean {
        if (component !is Image || componentId != component.componentId) return false

        withSafeMultiLock(component) { performUpdate(component) }

        return true
    }

    private fun performUpdate(other: Image) {
        contentScale = other.contentScale
        colorFilter = other.colorFilter

        if (other.imageData != null) {
            imageData = other.imageData
            imageHash = other.imageHash
            capturedGenerationId = other.capturedGenerationId
            compressedData = other.compressedData
            cachedHashCode = other.cachedHashCode
        } else {
            cachedHashCode = null
        }
    }

    /**
     * Note: This method compares the properties of the [Image] component, including a snapshot of
     * the [Bitmap] content taken at construction time. If the underlying [Bitmap] is mutated in
     * place after construction, this instance will still be considered equal to other [Image]
     * instances created from the same [Bitmap] state. To ensure UI updates after a [Bitmap]
     * mutation, a new [Image] instance should be created or the existing one should be updated via
     * [updateComponent].
     */
    override fun equals(other: Any?): Boolean {
        if (other === this) return true
        if (other !is Image) return false
        if (!super.equals(other)) return false

        return withSafeMultiLock(other) { equalsInternal(other) }
    }

    private fun equalsInternal(other: Image): Boolean {
        if (contentScale != other.contentScale || colorFilter != other.colorFilter) {
            return false
        }

        if (imageData === other.imageData && capturedGenerationId == other.capturedGenerationId) {
            return true
        }

        return Arrays.equals(imageHash, other.imageHash)
    }

    private inline fun <T> withSafeMultiLock(other: Image, block: () -> T): T {
        val h1 = System.identityHashCode(this.lock)
        val h2 = System.identityHashCode(other.lock)

        return when {
            h1 < h2 -> synchronized(this.lock) { synchronized(other.lock) { block() } }
            h1 > h2 -> synchronized(other.lock) { synchronized(this.lock) { block() } }
            else ->
                synchronized(TIE_LOCK) {
                    synchronized(this.lock) { synchronized(other.lock) { block() } }
                }
        }
    }

    override fun hashCode(): Int {
        var cached = cachedHashCode
        if (cached != null) return cached

        synchronized(lock) {
            cached = cachedHashCode
            if (cached != null) return cached

            val currentContentScale = contentScale
            val currentColorFilter = colorFilter
            val currentImageHash = imageHash

            var result = super.hashCode()
            result = 31 * result + currentContentScale.hashCode()
            result = 31 * result + currentColorFilter.hashCode()
            result = 31 * result + Arrays.hashCode(currentImageHash)
            cached = result
            cachedHashCode = cached
            return cached
        }
    }

    private fun updateImageHashAndCache() {
        synchronized(lock) {
            val bitmap = imageData ?: return
            capturedGenerationId = bitmap.generationId
            val md = MessageDigest.getInstance(HASH_ALGORITHM)
            val byteBuffer = ByteBuffer.allocate(bitmap.byteCount)
            bitmap.copyPixelsToBuffer(byteBuffer)
            imageHash = md.digest(byteBuffer.array())
            compressedData = null
            cachedHashCode = null
        }
    }

    private fun getCompressedData(): ByteArray? {
        var result = compressedData
        if (result == null) {
            synchronized(lock) {
                result = compressedData
                if (result == null) {
                    val currentImageData = imageData
                    result = toByteArray(currentImageData)
                    compressedData = result
                }
            }
        }
        return result
    }

    /**
     * This enum will advise the system on how the image should be scaled.
     * - FIT: Scale the image uniformly, keeping the aspect ratio
     * - FILL_BOUNDS: Scale the content vertically & horizontally non-uniformly to fill the bounds
     */
    enum class ContentScale {
        FIT,
        FILL_BOUNDS,
    }

    /**
     * This enum will advise the system on whether this image can be tinted or not.
     * - TINT: The image can be tinted by the system
     * - NO_TINT: The image must not be tinted by the system
     */
    enum class ColorFilter {
        TINT,
        NO_TINT,
    }

    /** A builder of [Image] */
    class Builder(componentId: String) : Component.Builder(componentId) {
        internal var imageData: Bitmap? = null
        internal var contentScale: ContentScale
        internal var colorFilter: ColorFilter

        internal constructor(imageMessage: ImageMessage) : this(imageMessage.componentId) {
            if (imageMessage.hasImage() && !imageMessage.image.isEmpty) {
                imageData =
                    BitmapFactory.decodeByteArray(
                        imageMessage.image.toByteArray(),
                        BITMAP_OFFSET,
                        imageMessage.image.size(),
                    )
                imageData ?: run { Log.e(TAG, "Image was unable to be created from bundle") }
            }

            contentScale =
                if (
                    imageMessage.getContentScale() ==
                        com.android.car.appcard.internal.proto.Image.ContentScale.FILL_BOUNDS
                ) {
                    ContentScale.FILL_BOUNDS
                } else if (
                    imageMessage.getContentScale() ==
                        com.android.car.appcard.internal.proto.Image.ContentScale.FIT
                ) {
                    ContentScale.FIT
                } else {
                    throw IllegalStateException("ContentScale must not be unrecognized")
                }

            colorFilter =
                if (
                    imageMessage.getColorFilter() ==
                        com.android.car.appcard.internal.proto.Image.ColorFilter.TINT
                ) {
                    ColorFilter.TINT
                } else if (
                    imageMessage.getColorFilter() ==
                        com.android.car.appcard.internal.proto.Image.ColorFilter.NO_TINT
                ) {
                    ColorFilter.NO_TINT
                } else {
                    throw IllegalStateException("ContentScale must not be unrecognized")
                }
        }

        init {
            contentScale = ContentScale.FIT
            colorFilter = ColorFilter.TINT
        }

        /** Set image data */
        fun setImageData(imageData: Bitmap?): Builder {
            this.imageData = imageData
            return this
        }

        /** Set [ContentScale] */
        fun setContentScale(contentScale: ContentScale): Builder {
            this.contentScale = contentScale
            return this
        }

        /** Set [ColorFilter] */
        fun setColorFilter(colorFilter: ColorFilter): Builder {
            this.colorFilter = colorFilter
            return this
        }

        /** @return [Image] built using this builder */
        fun build(): Image = Image(builder = this)
    }

    companion object {
        private const val TAG = "Image"
        private const val BITMAP_QUALITY = 100
        private const val BITMAP_OFFSET = 0
        private const val HASH_ALGORITHM = "MD5"
        private val TIE_LOCK = Any()

        /** @return an instance of [Builder] */
        @JvmStatic fun newBuilder(componentId: String) = Builder(componentId)

        private fun toContentScale(
            contentScale: ContentScale
        ): com.android.car.appcard.internal.proto.Image.ContentScale {
            return if (contentScale == ContentScale.FILL_BOUNDS) {
                com.android.car.appcard.internal.proto.Image.ContentScale.FILL_BOUNDS
            } else {
                com.android.car.appcard.internal.proto.Image.ContentScale.FIT
            }
        }

        private fun toColorFilter(
            colorFilter: ColorFilter
        ): com.android.car.appcard.internal.proto.Image.ColorFilter {
            return if (colorFilter == ColorFilter.NO_TINT) {
                com.android.car.appcard.internal.proto.Image.ColorFilter.NO_TINT
            } else {
                com.android.car.appcard.internal.proto.Image.ColorFilter.TINT
            }
        }

        /** @return an instance of [Image] from [ImageMessage] */
        @JvmStatic fun fromMessage(imageMessage: ImageMessage) = Builder(imageMessage).build()

        private fun toByteArray(bmp: Bitmap?): ByteArray? {
            bmp
                ?: run {
                    return null
                }

            val stream = ByteArrayOutputStream()
            bmp.compress(Bitmap.CompressFormat.PNG, BITMAP_QUALITY, stream)

            return stream.toByteArray()
        }
    }
}
