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

package com.android.car.media.common.ui;

import static androidx.media3.session.CommandButton.ICON_REWIND;
import static androidx.media3.session.CommandButton.ICON_SKIP_BACK_15;
import static androidx.media3.session.CommandButton.ICON_UNDEFINED;
import static androidx.media3.session.MediaConstants.EXTRAS_KEY_COMMAND_BUTTON_ICON_COMPAT;

import static com.android.car.media.common.ui.PlaybackCardControllerUtilities.getFirstCustomActionInSet;
import static com.android.car.media.common.ui.PlaybackCardControllerUtilities.skipBackStandardActions;
import static com.android.car.media.common.ui.PlaybackCardControllerUtilities.skipForwardStandardActions;
import static com.android.car.media.common.ui.PlaybackCardControllerUtilities.updateActionsWithPlaybackState;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.Mockito.when;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.ShapeDrawable;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageButton;

import androidx.annotation.ColorInt;
import androidx.core.graphics.drawable.IconCompat;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.android.car.media.common.CustomPlaybackAction;
import com.android.car.media.common.R;
import com.android.car.media.common.playback.PlaybackViewModel.PlaybackController;
import com.android.car.media.common.playback.PlaybackViewModel.PlaybackStateWrapper;
import com.android.car.media.common.playback.PlaybackViewModel.RawCustomPlaybackAction;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

import java.util.Arrays;
import java.util.List;

@RunWith(AndroidJUnit4.class)
public class PlaybackCardControllerUtilitiesTest {

    @Rule
    public final MockitoRule mMockitoRule = MockitoJUnit.rule();

    public ImageButton mButton1;
    public ImageButton mButton2;
    public ImageButton mButton3;
    public ImageButton mButton4;
    public ImageButton mButton5;
    public ImageButton mButton6;

    @Mock
    public RawCustomPlaybackAction mRawCustomAction1;
    @Mock
    public RawCustomPlaybackAction mRawCustomAction2;
    @Mock
    public RawCustomPlaybackAction mRawCustomAction3;

    @Mock
    public PlaybackStateWrapper mStateWrapper;

    @Mock
    public Drawable mSkipPrevDrawable;
    @Mock
    public Drawable mSkipNextDrawable;
    @Mock
    public Drawable mSkipPrevBackgroundDrawable;
    @Mock
    public Drawable mSkipNextBackgroundDrawable;
    @Mock
    public Drawable mCustomDrawable1;
    @Mock
    public Drawable mCustomDrawable2;
    @Mock
    public Drawable mCustomDrawable3;

    @Mock
    public IconCompat mCustomIconCompat1;
    @Mock
    public IconCompat mCustomIconCompat2;
    @Mock
    public IconCompat mCustomIconCompat3;

    @Mock
    public PlaybackController mPlaybackController;

    private Context mContext;
    private List<ImageButton> mAllButtons;
    private List<RawCustomPlaybackAction> mCustomActionList;
    private static final String CUSTOM_ACTION_STRING = "action";

    @Before
    public void setUp() {
        mContext = ApplicationProvider.getApplicationContext();

        mButton1 = new ImageButton(mContext);
        mButton2 = new ImageButton(mContext);
        mButton3 = new ImageButton(mContext);
        mButton4 = new ImageButton(mContext);
        mButton5 = new ImageButton(mContext);
        mButton6 = new ImageButton(mContext);
        mAllButtons = Arrays.asList(mButton1, mButton2, mButton3, mButton4, mButton5,
                mButton6);

        mCustomActionList = Arrays.asList(mRawCustomAction1, mRawCustomAction2, mRawCustomAction3);
        when(mCustomIconCompat1.loadDrawable(mContext)).thenReturn(mCustomDrawable1);
        when(mCustomIconCompat2.loadDrawable(mContext)).thenReturn(mCustomDrawable2);
        when(mCustomIconCompat3.loadDrawable(mContext)).thenReturn(mCustomDrawable3);
        CustomPlaybackAction action1 = CustomPlaybackAction.create(
                mContext, CUSTOM_ACTION_STRING, /* extras */ null,
                /* iconCompat */ mCustomIconCompat1);
        CustomPlaybackAction action2 = CustomPlaybackAction.create(
                mContext, CUSTOM_ACTION_STRING, /* extras */ null,
                /* iconCompat */ mCustomIconCompat2);
        CustomPlaybackAction action3 = CustomPlaybackAction.create(
                mContext, CUSTOM_ACTION_STRING, /* extras */ null,
                /* iconCompat */ mCustomIconCompat3);
        when(mRawCustomAction1.fetchDrawable(mContext)).thenReturn(action1);
        when(mRawCustomAction2.fetchDrawable(mContext)).thenReturn(action2);
        when(mRawCustomAction3.fetchDrawable(mContext)).thenReturn(action3);

        when(mStateWrapper.getCustomActions()).thenReturn(mCustomActionList);
    }

    @Test
    public void updateActions_allButtonsNull_noDrawablesSet() {
        setSkipPreviousReserved(true);
        setSkipPreviousEnabled(true);
        setSkipNextReserved(true);
        setSkipNextEnabled(true);
        List<ImageButton> actions = Arrays.asList(null, null, null, null, null, null);

        // function runs successfully with all null ImageButtons in actions list
        updateActionsWithPlaybackState(mContext, actions, mStateWrapper, mPlaybackController,
                mSkipPrevDrawable, mSkipNextDrawable, mSkipPrevBackgroundDrawable,
                mSkipNextBackgroundDrawable, /* reserveSkipSlots */ false,
                /* defaultButtonDrawable */ null);
    }

    @Test
    public void updateActions_usesSkipPrevAndNext_noExtraButtons_doesNotSetNullDefaultDrawable() {
        setSkipPreviousReserved(true);
        setSkipPreviousEnabled(true);
        setSkipNextReserved(true);
        setSkipNextEnabled(true);
        List<ImageButton> actions = Arrays.asList(mButton1, mButton2, mButton3, mButton4, mButton5);
        Drawable defaultButtonDrawable = null;

        updateActionsWithPlaybackState(mContext, actions, mStateWrapper, mPlaybackController,
                mSkipPrevDrawable, mSkipNextDrawable, mSkipPrevBackgroundDrawable,
                mSkipNextBackgroundDrawable, /* reserveSkipSlots */ false,
                /* defaultButtonDrawable */ defaultButtonDrawable);

        verify(mButton1, View.VISIBLE, mSkipPrevDrawable, mSkipPrevBackgroundDrawable);
        verify(mButton2, View.VISIBLE, mSkipNextDrawable, mSkipNextBackgroundDrawable);
        verify(mButton3, View.VISIBLE, mCustomDrawable1, Color.TRANSPARENT);
        verify(mButton4, View.VISIBLE, mCustomDrawable2, Color.TRANSPARENT);
        verify(mButton5, View.VISIBLE, mCustomDrawable3, Color.TRANSPARENT);
    }

    @Test
    public void updateActions_usesSkipPrevAndNext_extraButtons_doesNotSetDefaultDrawable() {
        setSkipPreviousReserved(true);
        setSkipPreviousEnabled(true);
        setSkipNextReserved(true);
        setSkipNextEnabled(true);
        Drawable defaultButtonDrawable = null;

        updateActionsWithPlaybackState(mContext, mAllButtons, mStateWrapper, mPlaybackController,
                mSkipPrevDrawable, mSkipNextDrawable, mSkipPrevBackgroundDrawable,
                mSkipNextBackgroundDrawable, /* reserveSkipSlots */ false,
                /* defaultButtonDrawable */ defaultButtonDrawable);

        verify(mButton1, View.VISIBLE, mSkipPrevDrawable, mSkipPrevBackgroundDrawable);
        verify(mButton2, View.VISIBLE, mSkipNextDrawable, mSkipNextBackgroundDrawable);
        verify(mButton3, View.VISIBLE, mCustomDrawable1, Color.TRANSPARENT);
        verify(mButton4, View.VISIBLE, mCustomDrawable2, Color.TRANSPARENT);
        verify(mButton5, View.VISIBLE, mCustomDrawable3, Color.TRANSPARENT);
        verify(mButton6, View.GONE, null, null);
    }

    @Test
    public void updateActions_usesSkipPrevAndNext_extraButtons_setDefaultDrawableOnExtra() {
        setSkipPreviousReserved(true);
        setSkipPreviousEnabled(true);
        setSkipNextReserved(true);
        setSkipNextEnabled(true);
        Drawable defaultButtonDrawable = new ShapeDrawable();

        updateActionsWithPlaybackState(mContext, mAllButtons, mStateWrapper, mPlaybackController,
                mSkipPrevDrawable, mSkipNextDrawable, mSkipPrevBackgroundDrawable,
                mSkipNextBackgroundDrawable, /* reserveSkipSlots */ false,
                /* defaultButtonDrawable */ defaultButtonDrawable);

        verify(mButton1, View.VISIBLE, mSkipPrevDrawable, mSkipPrevBackgroundDrawable);
        verify(mButton2, View.VISIBLE, mSkipNextDrawable, mSkipNextBackgroundDrawable);
        verify(mButton3, View.VISIBLE, mCustomDrawable1, Color.TRANSPARENT);
        verify(mButton4, View.VISIBLE, mCustomDrawable2, Color.TRANSPARENT);
        verify(mButton5, View.VISIBLE, mCustomDrawable3, Color.TRANSPARENT);
        verify(mButton6, View.VISIBLE, defaultButtonDrawable, null);
    }

    @Test
    public void updateActions_usesSkipNextOnly_button1IsCustom_setDefaultDrawableOnExtra() {
        setSkipPreviousReserved(false);
        setSkipPreviousEnabled(false);
        setSkipNextReserved(true);
        setSkipNextEnabled(true);
        Drawable defaultButtonDrawable = new ShapeDrawable();

        updateActionsWithPlaybackState(mContext, mAllButtons, mStateWrapper, mPlaybackController,
                mSkipPrevDrawable, mSkipNextDrawable, mSkipPrevBackgroundDrawable,
                mSkipNextBackgroundDrawable, /* reserveSkipSlots */ false,
                /* defaultButtonDrawable */ defaultButtonDrawable);

        verify(mButton1, View.VISIBLE, mCustomDrawable1, Color.TRANSPARENT);
        verify(mButton2, View.VISIBLE, mSkipNextDrawable, mSkipNextBackgroundDrawable);
        verify(mButton3, View.VISIBLE, mCustomDrawable2, Color.TRANSPARENT);
        verify(mButton4, View.VISIBLE, mCustomDrawable3, Color.TRANSPARENT);
        verify(mButton5, View.VISIBLE, defaultButtonDrawable, null);
        verify(mButton6, View.VISIBLE, defaultButtonDrawable, null);
    }

    @Test
    public void updateActions_usesSkipNextOnlyAndReserves_hidesButton1_setDefaultDrawableOnExtra() {
        setSkipPreviousReserved(false);
        setSkipPreviousEnabled(false);
        setSkipNextReserved(true);
        setSkipNextEnabled(true);
        Drawable defaultButtonDrawable = new ShapeDrawable();

        updateActionsWithPlaybackState(mContext, mAllButtons, mStateWrapper, mPlaybackController,
                mSkipPrevDrawable, mSkipNextDrawable, mSkipPrevBackgroundDrawable,
                mSkipNextBackgroundDrawable, /* reserveSkipSlots */ true,
                /* defaultButtonDrawable */ defaultButtonDrawable);

        verify(mButton1, View.GONE, null, null);
        verify(mButton2, View.VISIBLE, mSkipNextDrawable, mSkipNextBackgroundDrawable);
        verify(mButton3, View.VISIBLE, mCustomDrawable1, Color.TRANSPARENT);
        verify(mButton4, View.VISIBLE, mCustomDrawable2, Color.TRANSPARENT);
        verify(mButton5, View.VISIBLE, mCustomDrawable3, Color.TRANSPARENT);
        verify(mButton6, View.VISIBLE, defaultButtonDrawable, null);
    }

    @Test
    public void updateActions_usesNoSkips_button1And2AreCustom_setDefaultDrawableOnExtra() {
        setSkipPreviousReserved(false);
        setSkipPreviousEnabled(false);
        setSkipNextReserved(false);
        setSkipNextEnabled(false);
        Drawable defaultButtonDrawable = new ShapeDrawable();

        updateActionsWithPlaybackState(mContext, mAllButtons, mStateWrapper, mPlaybackController,
                mSkipPrevDrawable, mSkipNextDrawable, mSkipPrevBackgroundDrawable,
                mSkipNextBackgroundDrawable, /* reserveSkipSlots */ false,
                /* defaultButtonDrawable */ defaultButtonDrawable);

        verify(mButton1, View.VISIBLE, mCustomDrawable1, Color.TRANSPARENT);
        verify(mButton2, View.VISIBLE, mCustomDrawable2, Color.TRANSPARENT);
        verify(mButton3, View.VISIBLE, mCustomDrawable3, Color.TRANSPARENT);
        verify(mButton4, View.VISIBLE, defaultButtonDrawable, null);
        verify(mButton5, View.VISIBLE, defaultButtonDrawable, null);
        verify(mButton6, View.VISIBLE, defaultButtonDrawable, null);
    }

    @Test
    public void updateActions_usesNoSkipsAndReserves_hidesButton1And2_setDefaultDrawableOnExtra() {
        setSkipPreviousReserved(false);
        setSkipPreviousEnabled(false);
        setSkipNextReserved(false);
        setSkipNextEnabled(false);
        Drawable defaultButtonDrawable = new ShapeDrawable();

        updateActionsWithPlaybackState(mContext, mAllButtons, mStateWrapper, mPlaybackController,
                mSkipPrevDrawable, mSkipNextDrawable, mSkipPrevBackgroundDrawable,
                mSkipNextBackgroundDrawable, /* reserveSkipSlots */ true,
                /* defaultButtonDrawable */ defaultButtonDrawable);

        verify(mButton1, View.GONE, null, null);
        verify(mButton2, View.GONE, null, null);
        verify(mButton3, View.VISIBLE, mCustomDrawable1, Color.TRANSPARENT);
        verify(mButton4, View.VISIBLE, mCustomDrawable2, Color.TRANSPARENT);
        verify(mButton5, View.VISIBLE, mCustomDrawable3, Color.TRANSPARENT);
        verify(mButton6, View.VISIBLE, defaultButtonDrawable, null);
    }

    @Test
    public void updateActions_usesSkipPrevAndNext_nullBackgrounds_setsDefaultDrawableOnExtras() {
        setSkipPreviousReserved(true);
        setSkipPreviousEnabled(true);
        setSkipNextReserved(true);
        setSkipNextEnabled(true);
        Drawable defaultButtonDrawable = new ShapeDrawable();

        updateActionsWithPlaybackState(mContext, mAllButtons, mStateWrapper, mPlaybackController,
                mSkipPrevDrawable, mSkipNextDrawable, null,
                null, /* reserveSkipSlots */ false,
                /* defaultButtonDrawable */ defaultButtonDrawable);

        verify(mButton1, View.VISIBLE, mSkipPrevDrawable, null);
        verify(mButton2, View.VISIBLE, mSkipNextDrawable, null);
        verify(mButton3, View.VISIBLE, mCustomDrawable1, Color.TRANSPARENT);
        verify(mButton4, View.VISIBLE, mCustomDrawable2, Color.TRANSPARENT);
        verify(mButton5, View.VISIBLE, mCustomDrawable3, Color.TRANSPARENT);
        verify(mButton6, View.VISIBLE, defaultButtonDrawable, null);
    }

    @Test
    public void updateActions_usesSkipPrevAndNext_skipsNullButtons() {
        setSkipPreviousReserved(true);
        setSkipPreviousEnabled(true);
        setSkipNextReserved(true);
        setSkipNextEnabled(true);
        List<ImageButton> actions = Arrays.asList(mButton1, null, mButton3, mButton4, null,
                mButton6);
        Drawable defaultButtonDrawable = null;

        updateActionsWithPlaybackState(mContext, actions, mStateWrapper, mPlaybackController,
                mSkipPrevDrawable, mSkipNextDrawable, mSkipPrevBackgroundDrawable,
                mSkipNextBackgroundDrawable, /* reserveSkipSlots */ false,
                /* defaultButtonDrawable */ defaultButtonDrawable);

        verify(mButton1, View.VISIBLE, mSkipPrevDrawable, mSkipPrevBackgroundDrawable);
        verify(mButton3, View.VISIBLE, mSkipNextDrawable, mSkipNextBackgroundDrawable);
        verify(mButton4, View.VISIBLE, mCustomDrawable1, Color.TRANSPARENT);
        verify(mButton6, View.VISIBLE, mCustomDrawable2, Color.TRANSPARENT);
        assertThat(mButton2.getDrawable()).isEqualTo(null);
        assertThat(mButton5.getDrawable()).isEqualTo(null);
    }

    @Test
    public void getFirstCustomActionInSet_skipBackSet_returnsSkipBackCustomAction() {
        RawCustomPlaybackAction skipBack15StandardAction = createStandardCustomAction(
                ICON_SKIP_BACK_15);
        List<RawCustomPlaybackAction> customActionList = List.of(mRawCustomAction1,
                skipBack15StandardAction);

        RawCustomPlaybackAction returnedCustomAction = getFirstCustomActionInSet(
                customActionList, skipBackStandardActions);

        assertThat(returnedCustomAction).isEqualTo(skipBack15StandardAction);
    }

    @Test
    public void getFirstCustomActionInSet_skipForwardSet_noForwardStandardAction_returnsNull() {
        RawCustomPlaybackAction skipBack15StandardAction = createStandardCustomAction(
                ICON_SKIP_BACK_15);
        List<RawCustomPlaybackAction> customActionList = List.of(mRawCustomAction1,
                skipBack15StandardAction);

        RawCustomPlaybackAction returnedCustomAction = getFirstCustomActionInSet(
                customActionList, skipForwardStandardActions);

        assertThat(returnedCustomAction).isEqualTo(null);
    }

    @Test
    public void getFirstCustomActionInSet_skipBackSet_noStandardActions_returnsNull() {
        List<RawCustomPlaybackAction> customActionList = List.of(mRawCustomAction1,
                mRawCustomAction2);

        RawCustomPlaybackAction returnedCustomAction = getFirstCustomActionInSet(
                customActionList, skipBackStandardActions);

        assertThat(returnedCustomAction).isEqualTo(null);
    }

    @Test
    public void getFirstCustomActionInSet_skipBackSet_undefinedStandardAction_returnsNull() {
        RawCustomPlaybackAction undefinedAction = createStandardCustomAction(
                ICON_UNDEFINED);
        List<RawCustomPlaybackAction> customActionList = List.of(mRawCustomAction1,
                undefinedAction);

        RawCustomPlaybackAction returnedCustomAction = getFirstCustomActionInSet(
                customActionList, skipBackStandardActions);

        assertThat(returnedCustomAction).isEqualTo(null);
    }

    @Test
    public void getFirstCustomActionInSet_skipBackSet_multipleBackStandardAction_returnsFirst() {
        RawCustomPlaybackAction skipBack15StandardAction = createStandardCustomAction(
                ICON_SKIP_BACK_15);
        RawCustomPlaybackAction rewindStandardAction = createStandardCustomAction(
                ICON_REWIND);
        List<RawCustomPlaybackAction> customActionList = List.of(mRawCustomAction1,
                skipBack15StandardAction, rewindStandardAction);

        RawCustomPlaybackAction returnedCustomAction = getFirstCustomActionInSet(
                customActionList, skipBackStandardActions);

        assertThat(returnedCustomAction).isEqualTo(skipBack15StandardAction);
    }

    private void setSkipPreviousReserved(boolean reserved) {
        when(mStateWrapper.iSkipPreviousReserved()).thenReturn(reserved);
    }

    private void setSkipPreviousEnabled(boolean enabled) {
        when(mStateWrapper.isSkipPreviousEnabled()).thenReturn(enabled);
    }

    private void setSkipNextReserved(boolean reserved) {
        when(mStateWrapper.isSkipNextReserved()).thenReturn(reserved);
    }

    private void setSkipNextEnabled(boolean enabled) {
        when(mStateWrapper.isSkipNextEnabled()).thenReturn(enabled);
    }

    private void verify(ImageButton button, int visibility, Drawable imageDrawable,
            Drawable backgroundDrawable) {
        assertThat(button.getVisibility()).isEqualTo(visibility);
        assertThat(button.getDrawable()).isEqualTo(imageDrawable);
        assertThat(button.getBackground()).isEqualTo(backgroundDrawable);
    }

    private void verify(ImageButton button, int visibility, Drawable imageDrawable,
            @ColorInt int backgroundColor) {
        assertThat(button.getVisibility()).isEqualTo(visibility);
        assertThat(button.getDrawable()).isEqualTo(imageDrawable);
        assertThat(((ColorDrawable) button.getBackground()).getColor()).isEqualTo(
                backgroundColor);
    }

    private RawCustomPlaybackAction createStandardCustomAction(int standardAction) {
        Bundle extras = new Bundle();
        extras.putInt(EXTRAS_KEY_COMMAND_BUTTON_ICON_COMPAT, standardAction);
        RawCustomPlaybackAction actionWithExtras = new RawCustomPlaybackAction(
                R.drawable.ic_star_empty, null, "action", extras);
        return actionWithExtras;
    }
}
