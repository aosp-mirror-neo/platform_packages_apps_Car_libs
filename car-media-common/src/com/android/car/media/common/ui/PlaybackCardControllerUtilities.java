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

import static androidx.media3.session.CommandButton.ICON_FAST_FORWARD;
import static androidx.media3.session.CommandButton.ICON_NEXT;
import static androidx.media3.session.CommandButton.ICON_PREVIOUS;
import static androidx.media3.session.CommandButton.ICON_REWIND;
import static androidx.media3.session.CommandButton.ICON_SKIP_BACK;
import static androidx.media3.session.CommandButton.ICON_SKIP_BACK_10;
import static androidx.media3.session.CommandButton.ICON_SKIP_BACK_15;
import static androidx.media3.session.CommandButton.ICON_SKIP_BACK_30;
import static androidx.media3.session.CommandButton.ICON_SKIP_BACK_5;
import static androidx.media3.session.CommandButton.ICON_SKIP_FORWARD;
import static androidx.media3.session.CommandButton.ICON_SKIP_FORWARD_10;
import static androidx.media3.session.CommandButton.ICON_SKIP_FORWARD_15;
import static androidx.media3.session.CommandButton.ICON_SKIP_FORWARD_30;
import static androidx.media3.session.CommandButton.ICON_SKIP_FORWARD_5;
import static androidx.media3.session.CommandButton.ICON_UNDEFINED;
import static androidx.media3.session.MediaConstants.EXTRAS_KEY_COMMAND_BUTTON_ICON_COMPAT;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.text.TextUtils;
import android.util.Size;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.SeekBar;
import android.widget.TextView;

import androidx.annotation.LayoutRes;
import androidx.annotation.Nullable;

import com.android.car.apps.common.imaging.ImageBinder;
import com.android.car.apps.common.imaging.ImageViewBinder;
import com.android.car.apps.common.imaging.UriArtRef;
import com.android.car.apps.common.util.ViewUtils;
import com.android.car.media.common.CustomPlaybackAction;
import com.android.car.media.common.MediaLinkHandler;
import com.android.car.media.common.playback.PlaybackProgress;
import com.android.car.media.common.playback.PlaybackViewModel;
import com.android.car.media.common.playback.PlaybackViewModel.PlaybackController;
import com.android.car.media.common.playback.PlaybackViewModel.PlaybackStateWrapper;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Static utility functions used to give base logic to views in {@link PlaybackCardController} */
public final class PlaybackCardControllerUtilities {

    public static Set<Integer> skipForwardStandardActions = Collections.unmodifiableSet(
            new HashSet<>(Arrays.asList(ICON_NEXT, ICON_SKIP_FORWARD, ICON_SKIP_FORWARD_5,
                    ICON_SKIP_FORWARD_10, ICON_SKIP_FORWARD_15, ICON_SKIP_FORWARD_30,
                    ICON_FAST_FORWARD)));
    public static Set<Integer> skipBackStandardActions = Collections.unmodifiableSet(
            new HashSet<>(Arrays.asList(ICON_PREVIOUS, ICON_SKIP_BACK, ICON_SKIP_BACK_5,
                    ICON_SKIP_BACK_10, ICON_SKIP_BACK_15, ICON_SKIP_BACK_30, ICON_REWIND)));

    /**
     * Set text on the non-null TextView if the text is non-empty, or sets the
     * defaultText otherwise. Set the TextView VISIBLE if its contents are non-empty
     */
    public static void updateTextViewAndVisibility(@Nullable TextView view, CharSequence text,
            CharSequence defaultText) {
        updateTextViewAndVisibility(view, !TextUtils.isEmpty(text) ? text : defaultText);
    }

    /** Set text on the non-null TextView. Sets the TextView VISIBLE if the text is non-empty */
    public static void updateTextViewAndVisibility(@Nullable TextView view, CharSequence text) {
        if (view == null) {
            return;
        }
        view.setText(text);
        ViewUtils.setVisible(view, !TextUtils.isEmpty(text));
    }

    /** Set the mediaId associated with the {@link MediaLinkHandler} */
    public static void updateMediaLink(@Nullable MediaLinkHandler linkHandler,
            @Nullable String mediaId) {
        if (linkHandler != null) {
            linkHandler.setLinkedMediaId(mediaId);
        }
    }

    /**
     * Set the ImageView Drawable if its non-null and non-current, and set the ImageView to VISIBLE
     * if the Drawable is non-null
     */
    public static void updateImageViewDrawableAndVisibility(@Nullable ImageView view,
            @Nullable Drawable drawable) {
        if (view != null && drawable != null && view.getDrawable() != drawable) {
            view.setImageDrawable(drawable);
        }
        ViewUtils.setVisible(view, drawable != null);
    }

    /**
     * Update currTimeView {@link TextView} with {@link PlaybackProgress#getCurrentTimeText()}.
     * Update maxTimeView {@link TextView} with {@link PlaybackProgress#getMaxTimeText()}.
     * Update mTimeSeparator {@link TextView} dependent on non-null
     * {@link PlaybackProgress#getCurrentTimeText()} & {@link PlaybackProgress#getMaxTimeText()}
     * with an optional String separator to use
     */
    public static void updateProgressTimesAndSeparator(@Nullable TextView currTimeView,
            @Nullable TextView maxTimeView, @Nullable View timeSeparatorView,
            @Nullable CharSequence currTime, @Nullable CharSequence maxTime) {
        // Update currTimeView text
        updateTextViewAndVisibility(currTimeView, currTime);

        // Update maxTimeView text
        updateTextViewAndVisibility(maxTimeView, maxTime);

        // Update timeSeparatorView text
        ViewUtils.setVisible(timeSeparatorView, (timeSeparatorView != null
                && currTimeView != null && maxTimeView != null
                && !TextUtils.isEmpty(currTime) && !TextUtils.isEmpty(maxTime)));
    }

    /**
     * Set enabled and selected state on playButton based on {@link PlaybackViewModel.Action}, as
     * well as the onClickListener.
     */
    public static void updatePlayButtonWithPlaybackState(@Nullable View playButton,
            PlaybackStateWrapper playbackState, @Nullable PlaybackController playbackController) {
        if (playButton != null) {
            @PlaybackViewModel.Action int action = (playbackState != null)
                    ? playbackState.getMainAction() : PlaybackViewModel.ACTION_DISABLED;
            switch (action) {
                case PlaybackViewModel.ACTION_DISABLED:
                    playButton.setEnabled(false);
                    playButton.setSelected(false);
                    playButton.setOnClickListener(null);
                    break;
                case PlaybackViewModel.ACTION_PLAY:
                    playButton.setEnabled(true);
                    playButton.setSelected(false);
                    if (playbackController != null) {
                        playButton.setOnClickListener(view -> playbackController.play());
                    }
                    break;
                case PlaybackViewModel.ACTION_PAUSE:
                    playButton.setEnabled(true);
                    playButton.setSelected(true);
                    if (playbackController != null) {
                        playButton.setOnClickListener(view -> playbackController.pause());
                    }
                    break;
                case PlaybackViewModel.ACTION_STOP:
                    playButton.setEnabled(true);
                    playButton.setSelected(true);
                    if (playbackController != null) {
                        playButton.setOnClickListener(view -> playbackController.stop());
                    }
                    break;
            }
        }
    }

    /**
     * Update non-null buttons in actions list with skipPrev, skipNext, and custom actions. If
     * defaultButtonDrawable is non-null, any any unfilled actions in the list are assigned this
     * Drawable and set to VISIBLE, if defaultButtonDrawable is null, any unfilled actions in the
     * list will be set to GONE. If the skipPrevDrawable, skipPrevBackgroundDrawable,
     * skipNextDrawable, or skipNextBackgroundDrawable are non-null, they will be used for the
     * skipPrev/skipNext button's setImageDrawable and setBackground respectively. If
     * reserveSkipSlots is true, the first two non-null buttons in actions list will not be filled
     * in with non-skipPrev/skipNext custom actions, regardless if skipPrev/skipNext actions are
     * used by the media app.
     */
    public static void updateActionsWithPlaybackState(Context context, List<ImageButton> actions,
            PlaybackStateWrapper playbackState, @Nullable PlaybackController playbackController,
            @Nullable Drawable skipPrevDrawable, @Nullable Drawable skipNextDrawable,
            @Nullable Drawable skipPrevBackgroundDrawable,
            @Nullable Drawable skipNextBackgroundDrawable, boolean reserveSkipSlots,
            @Nullable Drawable defaultButtonDrawable) {
        boolean isSkipPrevEnabled = playbackState.isSkipPreviousEnabled();
        boolean isSkipPrevReserved = playbackState.iSkipPreviousReserved();
        boolean isSkipNextEnabled = playbackState.isSkipNextEnabled();
        boolean isSkipNextReserved = playbackState.isSkipNextReserved();
        List<PlaybackViewModel.RawCustomPlaybackAction> customActions = playbackState
                .getCustomActions();
        List<ImageButton> actionsToFill = new ArrayList<>();
        for (int i = 0; i < actions.size(); i++) {
            ImageButton button = actions.get(i);
            if (button != null) {
                actionsToFill.add(button);
                button.setBackground(null);
                if (defaultButtonDrawable != null && i != 0 && i != 1) {
                    button.setImageDrawable(defaultButtonDrawable);
                    ViewUtils.setVisible(button, true);
                } else {
                    button.setImageDrawable(null);
                    ViewUtils.setVisible(button, false);
                }
            }
        }

        // First handle the skip next button to place it in second button
        boolean isSkipNextHandled = false;
        if (actionsToFill.size() >= 2) {
            ImageButton button = actionsToFill.get(1);
            if ((isSkipNextEnabled || isSkipNextReserved)) {
                if (skipNextDrawable != null) {
                    button.setImageDrawable(skipNextDrawable);
                }
                if (skipNextBackgroundDrawable != null) {
                    button.setBackground(skipNextBackgroundDrawable);
                }
                ViewUtils.setVisible(button, true);
                button.setEnabled(isSkipNextEnabled);
                button.setOnClickListener(v -> {
                    if (playbackController != null) {
                        playbackController.skipToNext();
                    }
                });
                isSkipNextHandled = true;
            } else {
                PlaybackViewModel.RawCustomPlaybackAction skipForwardCustomAction =
                        getFirstCustomActionInSet(customActions, skipForwardStandardActions);
                if (skipForwardCustomAction != null) {
                    if (skipNextBackgroundDrawable != null) {
                        button.setBackground(skipNextBackgroundDrawable);
                    }
                    CustomPlaybackAction customAction = skipForwardCustomAction
                            .fetchDrawable(context);
                    if (customAction != null) {
                        button.setImageDrawable(customAction.mIcon);
                        ViewUtils.setVisible(button, true);
                        button.setEnabled(true);
                        button.setOnClickListener(v -> {
                            if (playbackController != null) {
                                playbackController.doCustomAction(
                                        customAction.mAction, customAction.mExtras);
                            }
                        });
                        customActions.remove(skipForwardCustomAction);
                        isSkipNextHandled = true;
                    }
                }
            }
        }
        // Now handle the skip prev button
        int startingSlotForCustomActions = 0;
        if (actionsToFill.size() >= 1) {
            ImageButton button = actionsToFill.get(0);
            if ((isSkipPrevEnabled || isSkipPrevReserved)) {
                if (skipPrevDrawable != null) {
                    button.setImageDrawable(skipPrevDrawable);
                }
                if (skipPrevBackgroundDrawable != null) {
                    button.setBackground(skipPrevBackgroundDrawable);
                }
                ViewUtils.setVisible(button, true);
                button.setEnabled(isSkipPrevEnabled);
                button.setOnClickListener(v -> {
                    if (playbackController != null) {
                        playbackController.skipToPrevious();
                    }
                });
                startingSlotForCustomActions++;
            } else {
                PlaybackViewModel.RawCustomPlaybackAction skipBackCustomAction =
                        getFirstCustomActionInSet(customActions, skipBackStandardActions);
                if (skipBackCustomAction != null) {
                    if (skipPrevBackgroundDrawable != null) {
                        button.setBackground(skipPrevBackgroundDrawable);
                    }
                    CustomPlaybackAction customAction = skipBackCustomAction
                            .fetchDrawable(context);
                    if (customAction != null) {
                        button.setImageDrawable(customAction.mIcon);
                        ViewUtils.setVisible(button, true);
                        button.setEnabled(true);
                        button.setOnClickListener(v -> {
                            if (playbackController != null) {
                                playbackController.doCustomAction(
                                        customAction.mAction, customAction.mExtras);
                            }
                        });
                        customActions.remove(skipBackCustomAction);
                        startingSlotForCustomActions++;
                    }
                }
            }
        }

        // Fill in remaining buttons (if any) with custom actions
        if (reserveSkipSlots) {
            startingSlotForCustomActions = 2;
        }
        int i = startingSlotForCustomActions;
        for (PlaybackViewModel.RawCustomPlaybackAction a : customActions) {
            if (i == 1 && isSkipNextHandled) {
                i++;
            }
            if (i < actionsToFill.size()) {
                CustomPlaybackAction customAction = a.fetchDrawable(context);
                if (customAction != null) {
                    ImageButton actionButton = actionsToFill.get(i);
                    actionButton.setImageDrawable(customAction.mIcon);
                    actionButton.setBackgroundColor(Color.TRANSPARENT);
                    ViewUtils.setVisible(actionButton, true);
                    actionButton.setEnabled(true);
                    actionButton.setOnClickListener(v -> {
                        if (playbackController != null) {
                            playbackController.doCustomAction(
                                    customAction.mAction, customAction.mExtras);
                        }
                    });
                }
                i++;
            } else {
                break;
            }
        }
    }

    /**
     * Enable or disable seekbar seeking depending on whether the media supports it or not.
     */
    public static void updateSeekbarWithPlaybackState(SeekBar seekBar,
            PlaybackStateWrapper playbackState) {
        if (seekBar != null) {
            boolean enabled = playbackState != null && playbackState.isSeekToEnabled();
            if (seekBar.getThumb() != null) {
                seekBar.getThumb().mutate().setAlpha(enabled ? 255 : 0);
            }
            final boolean shouldHandleTouch = seekBar.getThumb() != null && enabled;
            seekBar.setOnTouchListener(
                    (v, event) -> !shouldHandleTouch /* consumeEvent */);
        }
    }

    /**
     * Returns the first custom action that matches a standard
     * {@link androidx.media3.session.CommandButton} in the Set.
     */
    @Nullable
    public static PlaybackViewModel.RawCustomPlaybackAction getFirstCustomActionInSet(
            List<PlaybackViewModel.RawCustomPlaybackAction> customActions,
            Set<Integer> standardActionsSet) {
        for (PlaybackViewModel.RawCustomPlaybackAction a : customActions) {
            int standardIconId = a.mExtras != null
                    ? a.mExtras.getInt(EXTRAS_KEY_COMMAND_BUTTON_ICON_COMPAT) : ICON_UNDEFINED;
            if (standardIconId != ICON_UNDEFINED
                    && standardActionsSet.contains(standardIconId)) {
                return a;

            }
        }
        return null;
    }

    /**
     * Removes all views from the iconsContainer, then for each uri (up to maxIcons) adds a new
     * image view (loaded from iconViewResId) and fetches the icon.
     */
    public static void bindIndicatorIcons(
            @Nullable ViewGroup iconsContainer, @LayoutRes int iconViewResId,
            List<Uri> indicatorUris, int maxIcons, Size maxArtSize) {
        if (iconsContainer == null || (maxIcons <= 0)) return;

        Context context = iconsContainer.getContext();
        iconsContainer.removeAllViews();
        for (int i = 0; i < Math.min(maxIcons, indicatorUris.size()); i++) {
            ImageView icon = (ImageView) LayoutInflater.from(context).inflate(
                    iconViewResId, iconsContainer, false);

            ImageViewBinder<ImageBinder.ImageRef> imageBinder =
                    new ImageViewBinder<>(maxArtSize, icon);
            imageBinder.setImage(context, new UriArtRef(indicatorUris.get(i)));

            iconsContainer.addView(icon);
        }
        ViewUtils.setVisible(iconsContainer, indicatorUris.size() > 0);
    }

}
