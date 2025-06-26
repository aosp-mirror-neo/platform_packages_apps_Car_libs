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

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.android.car.appcard.BitmapUtility.convertToBytes
import com.android.car.appcard.BitmapUtility.getSampleBitmap
import com.android.car.appcard.ImageAppCard.Companion.fromMessage
import com.android.car.appcard.ImageAppCard.Companion.newBuilder
import com.android.car.appcard.component.Button
import com.android.car.appcard.component.Header
import com.android.car.appcard.component.Image
import com.android.car.appcard.component.ProgressBar
import com.android.car.appcard.component.interaction.OnClickListener
import com.android.car.appcard.internal.proto.AppCard
import com.android.car.appcard.internal.proto.Button.ButtonMessage
import com.android.car.appcard.internal.proto.Button.ButtonType
import com.android.car.appcard.internal.proto.Header.HeaderMessage
import com.android.car.appcard.internal.proto.Image.ColorFilter
import com.android.car.appcard.internal.proto.Image.ContentScale
import com.android.car.appcard.internal.proto.Image.ImageMessage
import com.android.car.appcard.internal.proto.ImageAppCard
import com.android.car.appcard.internal.proto.ProgressBar.ProgressBarMessage
import com.google.common.truth.Truth.assertThat
import com.google.protobuf.ByteString
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@RunWith(AndroidJUnit4::class)
class ImageAppCardTest {
    private val bitmap = getSampleBitmap(strokeWidth = 3)
    private val headerMessage =
        HeaderMessage.newBuilder().setComponentId(TEST_COMPONENT_ID).setTitle(TEST_TITLE).build()
    private val headerBuilder = HeaderMessage.newBuilder().setTitle(TEST_TITLE)
    private val conflictingHeaderMessage = headerBuilder.setComponentId(TEST_COMPONENT_ID).build()
    private val uniqueHeaderMessage =
        headerBuilder.setComponentId(TEST_COMPONENT_ID + Header::class.java.getSimpleName()).build()
    private val imageMessage =
        ImageMessage.newBuilder()
            .setComponentId(TEST_COMPONENT_ID)
            .setImage(ByteString.copyFrom(convertToBytes(bitmap)))
            .setColorFilter(ColorFilter.NO_TINT)
            .setContentScale(ContentScale.FILL_BOUNDS)
            .build()
    private val imageBuilder =
        ImageMessage.newBuilder()
            .setComponentId(TEST_COMPONENT_ID)
            .setImage(ByteString.copyFrom(convertToBytes(bitmap)))
            .setColorFilter(ColorFilter.NO_TINT)
            .setContentScale(ContentScale.FILL_BOUNDS)
    private val conflictingImageMessage = imageBuilder.setComponentId(TEST_COMPONENT_ID).build()
    private val uniqueImageMessage =
        imageBuilder.setComponentId(TEST_COMPONENT_ID + Image::class.java.getSimpleName()).build()
    private val uniqueInnerButtonMessage =
        imageBuilder
            .setComponentId(
                TEST_COMPONENT_ID +
                    Image::class.java.getSimpleName() +
                    Button::class.java.getSimpleName()
            )
            .build()
    private val buttonMessage =
        ButtonMessage.newBuilder()
            .setComponentId(TEST_COMPONENT_ID)
            .setImage(imageMessage)
            .setText(TEST_TITLE)
            .setType(ButtonType.PRIMARY)
            .build()
    private val buttonBuilder =
        ButtonMessage.newBuilder().setText(TEST_TITLE).setType(ButtonType.PRIMARY)
    private val conflictingButtonMessage =
        buttonBuilder.setImage(uniqueInnerButtonMessage).setComponentId(TEST_COMPONENT_ID).build()
    private val conflictingImageInButtonMessage =
        buttonBuilder
            .setImage(uniqueImageMessage)
            .setComponentId(TEST_COMPONENT_ID + Button::class.java.getSimpleName())
            .build()
    private val uniqueButtonMessage =
        buttonBuilder
            .setImage(uniqueInnerButtonMessage)
            .setComponentId(TEST_COMPONENT_ID + Button::class.java.getSimpleName())
            .build()
    private val progressBarMessage =
        ProgressBarMessage.newBuilder()
            .setComponentId(TEST_COMPONENT_ID)
            .setMax(2)
            .setMin(0)
            .setProgress(1)
            .build()
    private val progressBarBuilder =
        ProgressBarMessage.newBuilder().setMax(2).setMin(0).setProgress(1)
    private val conflictingProgressBarMessage =
        progressBarBuilder.setComponentId(TEST_COMPONENT_ID).build()
    private val uniqueProgressBarMessage =
        progressBarBuilder
            .setComponentId(TEST_COMPONENT_ID + ProgressBar::class.java.getSimpleName())
            .build()

    private val image = mock<Image> { on { toMessage() }.thenReturn(imageMessage) }
    private val header = mock<Header> { on { toMessage() }.thenReturn(headerMessage) }
    private val progressBar =
        mock<ProgressBar> { on { toMessage() }.thenReturn(progressBarMessage) }
    private val button = mock<Button> { on { toMessage() }.thenReturn(buttonMessage) }

    @Test(expected = IllegalStateException::class)
    fun testNewBuilder_setImage_imageTooLarge_exceptionOccurred() {
        val tooLargeBitmap = getSampleBitmap(strokeWidth = 3, width = 1001, height = 1000)

        newBuilder(TEST_ID)
            .setImage(Image.newBuilder(TEST_COMPONENT_ID).setImageData(tooLargeBitmap).build())
            .build()
    }

    @Test(expected = IllegalStateException::class)
    fun testNewBuilder_onlySecondaryText_exceptionOccurred() {
        newBuilder(TEST_ID).setSecondaryText(TEST_SECONDARY_TEXT).build()
    }

    @Test(expected = IllegalStateException::class)
    fun testNewBuilder_noImageNoPrimaryText_exceptionOccurred() {
        newBuilder(TEST_ID).addButton(button).setProgressBar(progressBar).build()
    }

    @Test(expected = IllegalStateException::class)
    fun testNewBuilder_noImageNoButtonsNoProgressBar_exceptionOccurred() {
        newBuilder(TEST_ID).setPrimaryText(TEST_PRIMARY_TEXT).build()
    }

    @Test(expected = IllegalStateException::class)
    fun testFromMessage_mageTooLarge_exceptionOccurred() {
        val tooLargeBitmap = getSampleBitmap(strokeWidth = 3, width = 1001, height = 1000)

        fromMessage(
            ImageAppCard.ImageAppCardMessage.newBuilder()
                .setAppCard(AppCard.AppCardMessage.newBuilder().setId(TEST_ID).build())
                .setImage(
                    ImageMessage.newBuilder()
                        .setComponentId(TEST_COMPONENT_ID)
                        .setImage(ByteString.copyFrom(convertToBytes(tooLargeBitmap)))
                        .setColorFilter(ColorFilter.NO_TINT)
                        .setContentScale(ContentScale.FILL_BOUNDS)
                        .build()
                )
                .setHeader(uniqueHeaderMessage)
                .setPrimaryText(TEST_PRIMARY_TEXT)
                .setSecondaryText(TEST_SECONDARY_TEXT)
                .build()
        )
    }

    @Test
    fun testGetHeader_nonNull() {
        val template = newBuilder(TEST_ID).setHeader(header).setImage(image).build()

        assertThat(template.header).isEqualTo(header)
    }

    @Test
    fun testGetHeader_defaultValue_isNull() {
        val template = newBuilder(TEST_ID).setImage(image).build()

        assertThat(template.header).isNull()
    }

    @Test
    fun testGetPrimaryText_nonNull() {
        val template =
            newBuilder(TEST_ID)
                .setImage(image)
                .setPrimaryText(TEST_PRIMARY_TEXT)
                .setSecondaryText(TEST_SECONDARY_TEXT)
                .build()

        assertThat(template.primaryText).isEqualTo(TEST_PRIMARY_TEXT)
    }

    @Test
    fun testGetPrimaryText_defaultValue_isNull() {
        val template = newBuilder(TEST_ID).setImage(image).build()

        assertThat(template.primaryText).isNull()
    }

    @Test
    fun testGetSecondaryText_nonNull() {
        val template =
            newBuilder(TEST_ID)
                .setImage(image)
                .setPrimaryText(TEST_PRIMARY_TEXT)
                .setSecondaryText(TEST_SECONDARY_TEXT)
                .build()

        assertThat(template.secondaryText).isEqualTo(TEST_SECONDARY_TEXT)
    }

    @Test
    fun testGetSecondaryText_defaultValue_isNull() {
        val template = newBuilder(TEST_ID).setImage(image).build()

        assertThat(template.secondaryText).isNull()
    }

    @Test
    fun testGetProgressBar_nonNull() {
        val template =
            newBuilder(TEST_ID)
                .setPrimaryText(TEST_PRIMARY_TEXT)
                .setSecondaryText(TEST_SECONDARY_TEXT)
                .setProgressBar(progressBar)
                .build()

        assertThat(template.progressBar).isEqualTo(progressBar)
    }

    @Test
    fun testGetProgressBar_defaultValue_isNull() {
        val template =
            newBuilder(TEST_ID)
                .setImage(image)
                .setPrimaryText(TEST_PRIMARY_TEXT)
                .setSecondaryText(TEST_SECONDARY_TEXT)
                .build()

        assertThat(template.progressBar).isNull()
    }

    @Test
    fun testGetButtons_nonNull_setButtons() {
        val buttons = listOf(button)
        val template =
            newBuilder(TEST_ID)
                .setPrimaryText(TEST_PRIMARY_TEXT)
                .setSecondaryText(TEST_SECONDARY_TEXT)
                .setButtons(buttons)
                .build()

        assertThat(template.buttons).isEqualTo(buttons)
    }

    @Test
    fun testGetButtons_nonNull_addButton() {
        val buttons = listOf(button)
        val template =
            newBuilder(TEST_ID)
                .setPrimaryText(TEST_PRIMARY_TEXT)
                .setSecondaryText(TEST_SECONDARY_TEXT)
                .addButton(button)
                .build()

        assertThat(template.buttons).isEqualTo(buttons)
    }

    @Test
    fun testGetButtons_nonNull_addButtonAfterSetButtons() {
        val buttons = listOf(button)
        val template =
            newBuilder(TEST_ID)
                .setPrimaryText(TEST_PRIMARY_TEXT)
                .setSecondaryText(TEST_SECONDARY_TEXT)
                .setButtons(buttons)
                .addButton(button)
                .build()

        assertThat(template.buttons).isEqualTo(listOf(button, button))
    }

    @Test
    fun testGetButtons_nonNull_setButtonsAfterAddButton() {
        val buttons = listOf(button)
        val template =
            newBuilder(TEST_ID)
                .setPrimaryText(TEST_PRIMARY_TEXT)
                .setSecondaryText(TEST_SECONDARY_TEXT)
                .addButton(button)
                .setButtons(buttons)
                .build()

        assertThat(template.buttons).isEqualTo(buttons)
    }

    @Test
    fun testGetButtons_defaultValue() {
        val template =
            newBuilder(TEST_ID)
                .setImage(image)
                .setPrimaryText(TEST_PRIMARY_TEXT)
                .setSecondaryText(TEST_SECONDARY_TEXT)
                .build()

        assertThat(template.buttons.size).isEqualTo(0)
    }

    @Test
    fun testGetImage_nonNull() {
        val template = newBuilder(TEST_ID).setImage(image).build()

        assertThat(template.image).isEqualTo(image)
    }

    @Test
    fun testGetImage_defaultValue_isNull() {
        val template =
            newBuilder(TEST_ID)
                .setProgressBar(progressBar)
                .setPrimaryText(TEST_PRIMARY_TEXT)
                .setSecondaryText(TEST_SECONDARY_TEXT)
                .build()

        assertThat(template.image).isNull()
    }

    @Test
    fun testToMessage_imageAndText() {
        val imageAppCardMessage =
            ImageAppCard.ImageAppCardMessage.newBuilder()
                .setAppCard(AppCard.AppCardMessage.newBuilder().setId(TEST_ID).build())
                .setImage(imageMessage)
                .setHeader(headerMessage)
                .setPrimaryText(TEST_PRIMARY_TEXT)
                .setSecondaryText(TEST_SECONDARY_TEXT)
                .build()
        val imageAppCard =
            newBuilder(TEST_ID)
                .setImage(image)
                .setHeader(header)
                .setPrimaryText(TEST_PRIMARY_TEXT)
                .setSecondaryText(TEST_SECONDARY_TEXT)
                .build()

        assertThat(imageAppCard.toMessage()).isEqualTo(imageAppCardMessage)
    }

    @Test
    fun testToMessage_textProgressBarAndButtons() {
        val imageAppCardMessage =
            ImageAppCard.ImageAppCardMessage.newBuilder()
                .setAppCard(AppCard.AppCardMessage.newBuilder().setId(TEST_ID).build())
                .setHeader(headerMessage)
                .setPrimaryText(TEST_PRIMARY_TEXT)
                .setSecondaryText(TEST_SECONDARY_TEXT)
                .setProgressBar(progressBarMessage)
                .setButtonList(
                    ImageAppCard.ButtonList.newBuilder()
                        .addAllButtons(listOf(buttonMessage))
                        .build()
                )
                .build()
        val imageAppCard =
            newBuilder(TEST_ID)
                .setHeader(header)
                .setPrimaryText(TEST_PRIMARY_TEXT)
                .setSecondaryText(TEST_SECONDARY_TEXT)
                .setProgressBar(progressBar)
                .setButtons(listOf(button))
                .build()

        assertThat(imageAppCard.toMessage()).isEqualTo(imageAppCardMessage)
    }

    @Test
    fun testToByteArray() {
        val imageAppCardMessage =
            ImageAppCard.ImageAppCardMessage.newBuilder()
                .setAppCard(AppCard.AppCardMessage.newBuilder().setId(TEST_ID).build())
                .setImage(imageMessage)
                .setHeader(headerMessage)
                .setPrimaryText(TEST_PRIMARY_TEXT)
                .setSecondaryText(TEST_SECONDARY_TEXT)
                .build()
        val imageAppCard =
            newBuilder(TEST_ID)
                .setImage(image)
                .setHeader(header)
                .setPrimaryText(TEST_PRIMARY_TEXT)
                .setSecondaryText(TEST_SECONDARY_TEXT)
                .build()

        assertThat(imageAppCard.toByteArray()).isEqualTo(imageAppCardMessage.toByteArray())
    }

    @Test
    fun testEquals_sameObject_returnTrue() {
        val imageAppCard =
            newBuilder(TEST_ID)
                .setImage(image)
                .setHeader(header)
                .setPrimaryText(TEST_PRIMARY_TEXT)
                .setSecondaryText(TEST_SECONDARY_TEXT)
                .build()

        assertThat(imageAppCard.equals(imageAppCard)).isTrue()
    }

    @Test
    fun testEquals_imageAndText_allFieldsEqual_returnTrue() {
        val imageAppCard =
            newBuilder(TEST_ID)
                .setImage(Image.fromMessage(imageMessage))
                .setHeader(Header.fromMessage(headerMessage))
                .setPrimaryText(TEST_PRIMARY_TEXT)
                .setSecondaryText(TEST_SECONDARY_TEXT)
                .build()
        val other =
            newBuilder(TEST_ID)
                .setImage(Image.fromMessage(imageMessage))
                .setHeader(Header.fromMessage(headerMessage))
                .setPrimaryText(TEST_PRIMARY_TEXT)
                .setSecondaryText(TEST_SECONDARY_TEXT)
                .build()

        assertThat(imageAppCard == other).isTrue()
    }

    @Test
    fun testEquals_textProgressBarAndButtons_allFieldsEqual_returnTrue() {
        val imageAppCard =
            newBuilder(TEST_ID)
                .setHeader(Header.fromMessage(headerMessage))
                .setPrimaryText(TEST_PRIMARY_TEXT)
                .setSecondaryText(TEST_SECONDARY_TEXT)
                .setProgressBar(ProgressBar.fromMessage(progressBarMessage))
                .setButtons(listOf(Button.fromMessage(buttonMessage)))
                .build()
        val other =
            newBuilder(TEST_ID)
                .setHeader(Header.fromMessage(headerMessage))
                .setPrimaryText(TEST_PRIMARY_TEXT)
                .setSecondaryText(TEST_SECONDARY_TEXT)
                .setProgressBar(ProgressBar.fromMessage(progressBarMessage))
                .setButtons(listOf(Button.fromMessage(buttonMessage)))
                .build()

        assertThat(imageAppCard == other).isTrue()
    }

    @Test
    fun testEquals_differentClass_returnFalse() {
        val imageAppCard =
            newBuilder(TEST_ID)
                .setImage(image)
                .setHeader(header)
                .setPrimaryText(TEST_PRIMARY_TEXT)
                .setSecondaryText(TEST_SECONDARY_TEXT)
                .build()

        assertThat(imageAppCard.equals(header)).isFalse()
    }

    @Test
    fun testEquals_differentId_returnFalse() {
        val imageAppCard =
            newBuilder(TEST_ID)
                .setImage(Image.fromMessage(imageMessage))
                .setHeader(Header.fromMessage(headerMessage))
                .setPrimaryText(TEST_PRIMARY_TEXT)
                .setSecondaryText(TEST_SECONDARY_TEXT)
                .build()
        val other =
            newBuilder(TEST_PRIMARY_TEXT)
                .setImage(Image.fromMessage(imageMessage))
                .setHeader(Header.fromMessage(headerMessage))
                .setPrimaryText(TEST_PRIMARY_TEXT)
                .setSecondaryText(TEST_SECONDARY_TEXT)
                .build()

        assertThat(imageAppCard == other).isFalse()
    }

    @Test
    fun testEquals_differentImage_returnFalse() {
        val imageAppCard =
            newBuilder(TEST_ID)
                .setImage(Image.fromMessage(imageMessage))
                .setHeader(Header.fromMessage(headerMessage))
                .setPrimaryText(TEST_PRIMARY_TEXT)
                .setSecondaryText(TEST_SECONDARY_TEXT)
                .build()
        val other =
            newBuilder(TEST_ID)
                .setImage(
                    Image.fromMessage(
                        ImageMessage.newBuilder()
                            .setComponentId(TEST_COMPONENT_ID)
                            .setImage(ByteString.copyFrom(convertToBytes(bitmap)))
                            .setColorFilter(ColorFilter.TINT)
                            .setContentScale(ContentScale.FIT)
                            .build()
                    )
                )
                .setHeader(Header.fromMessage(headerMessage))
                .setPrimaryText(TEST_PRIMARY_TEXT)
                .setSecondaryText(TEST_SECONDARY_TEXT)
                .build()

        assertThat(imageAppCard == other).isFalse()
    }

    @Test
    fun testEquals_differentHeader_returnFalse() {
        val imageAppCard =
            newBuilder(TEST_ID)
                .setImage(Image.fromMessage(imageMessage))
                .setHeader(Header.fromMessage(headerMessage))
                .setPrimaryText(TEST_PRIMARY_TEXT)
                .setSecondaryText(TEST_SECONDARY_TEXT)
                .build()
        val other =
            newBuilder(TEST_ID)
                .setImage(Image.fromMessage(imageMessage))
                .setHeader(Header.newBuilder(TEST_COMPONENT_ID).setTitle(TEST_ID).build())
                .setPrimaryText(TEST_PRIMARY_TEXT)
                .setSecondaryText(TEST_SECONDARY_TEXT)
                .build()

        assertThat(imageAppCard == other).isFalse()
    }

    @Test
    fun testEquals_differentPrimaryText_returnFalse() {
        val imageAppCard =
            newBuilder(TEST_ID)
                .setImage(Image.fromMessage(imageMessage))
                .setHeader(Header.fromMessage(headerMessage))
                .setPrimaryText(TEST_PRIMARY_TEXT)
                .setSecondaryText(TEST_SECONDARY_TEXT)
                .build()
        val other =
            newBuilder(TEST_ID)
                .setImage(Image.fromMessage(imageMessage))
                .setHeader(Header.fromMessage(headerMessage))
                .setPrimaryText(TEST_ID)
                .setSecondaryText(TEST_SECONDARY_TEXT)
                .build()

        assertThat(imageAppCard == other).isFalse()
    }

    @Test
    fun testEquals_differentSecondaryText_returnFalse() {
        val imageAppCard =
            newBuilder(TEST_ID)
                .setImage(Image.fromMessage(imageMessage))
                .setHeader(Header.fromMessage(headerMessage))
                .setPrimaryText(TEST_PRIMARY_TEXT)
                .setSecondaryText(TEST_SECONDARY_TEXT)
                .build()
        val other =
            newBuilder(TEST_ID)
                .setImage(Image.fromMessage(imageMessage))
                .setHeader(Header.fromMessage(headerMessage))
                .setPrimaryText(TEST_PRIMARY_TEXT)
                .setSecondaryText(TEST_ID)
                .build()

        assertThat(imageAppCard == other).isFalse()
    }

    @Test
    fun testEquals_differentProgressBar_returnFalse() {
        val imageAppCard =
            newBuilder(TEST_ID)
                .setHeader(Header.fromMessage(headerMessage))
                .setPrimaryText(TEST_PRIMARY_TEXT)
                .setSecondaryText(TEST_SECONDARY_TEXT)
                .setProgressBar(ProgressBar.fromMessage(progressBarMessage))
                .setButtons(listOf(Button.fromMessage(buttonMessage)))
                .build()
        val other =
            newBuilder(TEST_ID)
                .setHeader(Header.fromMessage(headerMessage))
                .setPrimaryText(TEST_PRIMARY_TEXT)
                .setSecondaryText(TEST_SECONDARY_TEXT)
                .setProgressBar(ProgressBar.newBuilder(TEST_COMPONENT_ID, min = 0, max = 2).build())
                .setButtons(listOf(Button.fromMessage(buttonMessage)))
                .build()

        assertThat(imageAppCard == other).isFalse()
    }

    @Test
    fun testEquals_differentButtonList_returnFalse() {
        val imageAppCard =
            newBuilder(TEST_ID)
                .setHeader(Header.fromMessage(headerMessage))
                .setPrimaryText(TEST_PRIMARY_TEXT)
                .setSecondaryText(TEST_SECONDARY_TEXT)
                .setProgressBar(ProgressBar.fromMessage(progressBarMessage))
                .setButtons(listOf(Button.fromMessage(buttonMessage)))
                .build()
        val otherButton =
            Button.newBuilder(
                    TEST_COMPONENT_ID,
                    Button.ButtonType.SECONDARY,
                    object : OnClickListener {
                        override fun onClick() {}
                    },
                )
                .setText(TEST_PRIMARY_TEXT)
                .build()
        val other =
            newBuilder(TEST_ID)
                .setHeader(Header.fromMessage(headerMessage))
                .setPrimaryText(TEST_PRIMARY_TEXT)
                .setSecondaryText(TEST_SECONDARY_TEXT)
                .setProgressBar(ProgressBar.fromMessage(progressBarMessage))
                .setButtons(listOf(otherButton))
                .build()

        assertThat(imageAppCard == other).isFalse()
    }

    @Test
    fun testUpdateComponent_imageUpdate_verifyImageUpdated() {
        val imageAppCard =
            newBuilder(TEST_ID)
                .setImage(image)
                .setHeader(header)
                .setPrimaryText(TEST_PRIMARY_TEXT)
                .setSecondaryText(TEST_SECONDARY_TEXT)
                .build()
        whenever(image.updateComponent(eq(image))).thenReturn(true)
        whenever(image.imageData).thenReturn(getSampleBitmap(strokeWidth = 3))

        imageAppCard.updateComponent(image)

        verify(image).updateComponent(eq(image))
    }

    @Test
    fun testUpdateComponent_imageUpdate_imageTooLarge_verifyImageNotUpdated() {
        val tooLargeBitmap = getSampleBitmap(strokeWidth = 3, width = 1001, height = 1000)
        val imageAppCard =
            newBuilder(TEST_ID)
                .setImage(image)
                .setHeader(header)
                .setPrimaryText(TEST_PRIMARY_TEXT)
                .setSecondaryText(TEST_SECONDARY_TEXT)
                .build()
        whenever(image.updateComponent(eq(image))).thenReturn(true)
        whenever(image.imageData).thenReturn(tooLargeBitmap)

        imageAppCard.updateComponent(image)

        verify(image, never()).updateComponent(eq(image))
    }

    @Test
    fun testUpdateComponent_headerUpdate_verifyHeaderUpdated() {
        val imageAppCard =
            newBuilder(TEST_ID)
                .setImage(image)
                .setHeader(header)
                .setPrimaryText(TEST_PRIMARY_TEXT)
                .setSecondaryText(TEST_SECONDARY_TEXT)
                .build()
        whenever(image.updateComponent(eq(progressBar))).thenReturn(false)
        whenever(header.updateComponent(eq(progressBar))).thenReturn(true)

        imageAppCard.updateComponent(progressBar)

        verify(header).updateComponent(eq(progressBar))
    }

    @Test
    fun testUpdateComponent_progressBarUpdate_verifyProgressBarUpdated() {
        val imageAppCard =
            newBuilder(TEST_ID)
                .setHeader(header)
                .setPrimaryText(TEST_PRIMARY_TEXT)
                .setSecondaryText(TEST_SECONDARY_TEXT)
                .setProgressBar(progressBar)
                .setButtons(listOf(button))
                .build()
        whenever(header.updateComponent(eq(progressBar))).thenReturn(false)
        whenever(progressBar.updateComponent(eq(progressBar))).thenReturn(true)

        imageAppCard.updateComponent(progressBar)

        verify(progressBar).updateComponent(eq(progressBar))
    }

    @Test
    fun testUpdateComponent_buttonUpdate_verifyButtonUpdated() {
        val imageAppCard =
            newBuilder(TEST_ID)
                .setHeader(header)
                .setPrimaryText(TEST_PRIMARY_TEXT)
                .setSecondaryText(TEST_SECONDARY_TEXT)
                .setProgressBar(progressBar)
                .setButtons(listOf(button))
                .build()
        whenever(header.updateComponent(eq(progressBar))).thenReturn(false)
        whenever(progressBar.updateComponent(eq(progressBar))).thenReturn(false)
        whenever(button.updateComponent(eq(progressBar))).thenReturn(true)

        imageAppCard.updateComponent(progressBar)

        verify(button).updateComponent(eq(progressBar))
    }

    @Test
    fun testVerifyUniquenessOfComponentIds_textProgressBarAndButtons_allUnique_returnTrue() {
        val imageAppCardMessage =
            ImageAppCard.ImageAppCardMessage.newBuilder()
                .setAppCard(AppCard.AppCardMessage.newBuilder().setId(TEST_ID).build())
                .setHeader(uniqueHeaderMessage)
                .setPrimaryText(TEST_PRIMARY_TEXT)
                .setSecondaryText(TEST_SECONDARY_TEXT)
                .setProgressBar(uniqueProgressBarMessage)
                .setButtonList(
                    ImageAppCard.ButtonList.newBuilder()
                        .addAllButtons(listOf(uniqueButtonMessage))
                        .build()
                )
                .build()

        assertThat(fromMessage(imageAppCardMessage).verifyUniquenessOfComponentIds()).isTrue()
    }

    @Test
    fun testVerifyUniquenessOfComponentIds_imageAndText_allUnique_returnTrue() {
        val imageAppCardMessage =
            ImageAppCard.ImageAppCardMessage.newBuilder()
                .setAppCard(AppCard.AppCardMessage.newBuilder().setId(TEST_ID).build())
                .setImage(uniqueImageMessage)
                .setHeader(uniqueHeaderMessage)
                .setPrimaryText(TEST_PRIMARY_TEXT)
                .setSecondaryText(TEST_SECONDARY_TEXT)
                .build()

        assertThat(fromMessage(imageAppCardMessage).verifyUniquenessOfComponentIds()).isTrue()
    }

    @Test
    fun testVerifyUniquenessOfComponentIds_headerImageClash_returnFalse() {
        val imageAppCardMessage =
            ImageAppCard.ImageAppCardMessage.newBuilder()
                .setAppCard(AppCard.AppCardMessage.newBuilder().setId(TEST_ID).build())
                .setImage(conflictingImageMessage)
                .setHeader(conflictingHeaderMessage)
                .setPrimaryText(TEST_PRIMARY_TEXT)
                .setSecondaryText(TEST_SECONDARY_TEXT)
                .build()

        assertThat(fromMessage(imageAppCardMessage).verifyUniquenessOfComponentIds()).isFalse()
    }

    @Test
    fun testVerifyUniquenessOfComponentIds_progressBarHeaderClash_returnFalse() {
        val imageAppCardMessage =
            ImageAppCard.ImageAppCardMessage.newBuilder()
                .setAppCard(AppCard.AppCardMessage.newBuilder().setId(TEST_ID).build())
                .setHeader(conflictingHeaderMessage)
                .setPrimaryText(TEST_PRIMARY_TEXT)
                .setSecondaryText(TEST_SECONDARY_TEXT)
                .setProgressBar(conflictingProgressBarMessage)
                .setButtonList(
                    ImageAppCard.ButtonList.newBuilder()
                        .addAllButtons(listOf(uniqueButtonMessage))
                        .build()
                )
                .build()

        assertThat(fromMessage(imageAppCardMessage).verifyUniquenessOfComponentIds()).isFalse()
    }

    @Test
    fun testVerifyUniquenessOfComponentIds_buttonHeaderClash_returnFalse() {
        val imageAppCardMessage =
            ImageAppCard.ImageAppCardMessage.newBuilder()
                .setAppCard(AppCard.AppCardMessage.newBuilder().setId(TEST_ID).build())
                .setHeader(conflictingHeaderMessage)
                .setPrimaryText(TEST_PRIMARY_TEXT)
                .setSecondaryText(TEST_SECONDARY_TEXT)
                .setProgressBar(uniqueProgressBarMessage)
                .setButtonList(
                    ImageAppCard.ButtonList.newBuilder()
                        .addAllButtons(listOf(conflictingButtonMessage))
                        .build()
                )
                .build()

        assertThat(fromMessage(imageAppCardMessage).verifyUniquenessOfComponentIds()).isFalse()
    }

    @Test
    fun testVerifyUniquenessOfComponentIds_imageInButtonImageInHeaderClash_returnFalse() {
        val imageAppCardMessage =
            ImageAppCard.ImageAppCardMessage.newBuilder()
                .setAppCard(AppCard.AppCardMessage.newBuilder().setId(TEST_ID).build())
                .setHeader(headerBuilder.setImage(uniqueImageMessage).build())
                .setPrimaryText(TEST_PRIMARY_TEXT)
                .setSecondaryText(TEST_SECONDARY_TEXT)
                .setProgressBar(uniqueProgressBarMessage)
                .setButtonList(
                    ImageAppCard.ButtonList.newBuilder()
                        .addAllButtons(listOf(conflictingImageInButtonMessage))
                        .build()
                )
                .build()

        assertThat(fromMessage(imageAppCardMessage).verifyUniquenessOfComponentIds()).isFalse()
    }

    companion object {
        private const val TEST_ID = "ID"
        private const val TEST_PRIMARY_TEXT = "PRIMARY_TEXT"
        private const val TEST_SECONDARY_TEXT = "SECONDARY_TEXT"
        private const val TEST_COMPONENT_ID = "COMPONENT_ID"
        private const val TEST_TITLE = "TITLE"
    }
}
