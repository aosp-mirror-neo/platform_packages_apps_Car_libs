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

import static com.android.car.media.common.ui.PlaybackCardControllerUtilities.updateActionsWithPlaybackState;
import static com.android.car.media.common.ui.PlaybackCardControllerUtilities.updateImageViewDrawableAndVisibility;
import static com.android.car.media.common.ui.PlaybackCardControllerUtilities.updateMediaLink;
import static com.android.car.media.common.ui.PlaybackCardControllerUtilities.updatePlayButtonWithPlaybackState;
import static com.android.car.media.common.ui.PlaybackCardControllerUtilities.updateProgressTimesAndSeparator;
import static com.android.car.media.common.ui.PlaybackCardControllerUtilities.updateSeekbarWithPlaybackState;
import static com.android.car.media.common.ui.PlaybackCardControllerUtilities.updateTextViewAndVisibility;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.util.Size;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.SeekBar;
import android.widget.TextView;

import androidx.annotation.CallSuper;
import androidx.lifecycle.LifecycleOwner;
import androidx.lifecycle.ViewTreeLifecycleOwner;

import com.android.car.apps.common.imaging.ImageBinder;
import com.android.car.apps.common.imaging.UriArtRef;
import com.android.car.apps.common.util.ViewUtils;
import com.android.car.media.common.ContentFormatView;
import com.android.car.media.common.MediaItemMetadata;
import com.android.car.media.common.MediaItemMetadata.ArtworkRef;
import com.android.car.media.common.MediaLinkHandler;
import com.android.car.media.common.R;
import com.android.car.media.common.browse.MediaItemsRepository;
import com.android.car.media.common.playback.PlaybackProgress;
import com.android.car.media.common.playback.PlaybackViewModel;
import com.android.car.media.common.playback.PlaybackViewModel.PlaybackController;
import com.android.car.media.common.playback.PlaybackViewModel.PlaybackStateWrapper;
import com.android.car.media.common.source.MediaSource;
import com.android.car.media.common.source.MediaSourceColors;

import java.util.List;

/**
 * A controller that manages views related to media playback, such as metadata, playback controls,
 * queue, and history. It observes a {@link PlaybackViewModel} and updates its information depending
 * on the currently playing media source through the {@link android.media.session.MediaSession} API.
 */
public class PlaybackCardController {

    protected final ViewGroup mView;
    protected final PlaybackViewModel mDataModel;
    protected final PlaybackCardViewModel mViewModel;
    protected final MediaItemsRepository mItemsRepository;
    protected final Context mContext;

    protected View mMetaDataLoadingIndicator = null;
    protected TextView mTitle = null;
    protected TextView mSubtitle = null;
    protected ImageView mAlbumCover = null;
    protected ImageBinder<ArtworkRef> mAlbumArtBinder;
    protected ContentFormatView mLogo = null;
    protected ImageBinder<ImageBinder.ImageRef> mLogoBinder;
    protected TextView mDescription = null;
    protected ImageView mAppIcon = null;
    protected TextView mAppName = null;
    protected TextView mCurrentTime = null;
    protected View mTimeSeparator = null;
    protected TextView mMaxTime = null;
    protected SeekBar mSeekBar = null;
    protected boolean mTrackingTouch;
    protected ImageButton mPlayPauseButton = null;
    protected List<ImageButton> mActions = null;
    protected ImageButton mQueueButton = null;
    private boolean mHasQueue = false;
    protected ImageButton mHistoryButton = null;
    protected ImageButton mActionOverflowButton = null;
    protected MediaLinkHandler mSubtitleLinker = null;
    protected MediaLinkHandler mDescriptionLinker = null;
    private LifecycleOwner mViewLifecycle;

    protected PlaybackCardController(Builder builder) {
        mView = builder.mView;
        mDataModel = builder.mDataModel;
        mViewModel = builder.mViewModel;
        mItemsRepository = builder.mItemsRepository;
        mContext = builder.mContext != null ? builder.mContext : mView.getContext();
        mViewLifecycle = ViewTreeLifecycleOwner.get(mView);
    }

    /** Method used to set up the views within the Controller's ViewGroup mView */
    @CallSuper
    protected void setupController() {
        getViewsFromWidget();
        setUpDataModelObservers();
        setUpSeekBar();
        setUpQueueButton();
        setUpHistoryButton();
        setUpActionsOverflowButton();
    }

    /**
     * Class used to construct a {@link PlaybackCardController} using {@link PlaybackViewModel},
     * {@link PlaybackCardViewModel}, {@link MediaItemsRepository}, and {@link ViewGroup}.
     */
    public static class Builder {
        private ViewGroup mView;
        private PlaybackViewModel mDataModel;
        private PlaybackCardViewModel mViewModel;
        private MediaItemsRepository mItemsRepository;
        private Context mContext;

        /** Default constructor */
        public Builder() {}

        /** Used to set the {@link PlaybackViewModel}, {@link PlaybackCardViewModel}, and
         * {@link MediaItemsRepository}.
         */
        public Builder setModels(PlaybackViewModel dataModel, PlaybackCardViewModel viewModel,
                MediaItemsRepository itemsRepository) {
            mDataModel = dataModel;
            mViewModel = viewModel;
            mItemsRepository = itemsRepository;
            return this;
        }

        /** Used to set the {@link ViewGroup}.*/
        public Builder setViewGroup(ViewGroup view) {
            mView = view;
            return this;
        }

        /** Used to set the {@link Context}. */
        public Builder setContext(Context context) {
            mContext = context;
            return this;
        }

        /** Creates {@link PlaybackCardController}. Throws a {@link RuntimeException} if
         *  {@link #setViewGroup(ViewGroup)} has not been called or called with null ViewGroup.
         *  Throws a {@link RuntimeException} if
         *  {@link #setModels(PlaybackViewModel, PlaybackCardViewModel, MediaItemsRepository)} has
         *  not been called or was called with a null {@link PlaybackViewModel} or null
         *  {@link PlaybackCardViewModel}.
         */
        public PlaybackCardController build() {
            if (mView == null) {
                throw new IllegalStateException("Must call setViewGroup on Builder with non-null "
                        + "ViewGroup before build");
            }
            if (mDataModel == null || mViewModel == null) {
                throw new IllegalStateException("Must call setModels on Builder before with "
                        + "non-null PlaybackViewModel and PlaybackCardViewModel before build");
            }
            PlaybackCardController mwc = new PlaybackCardController(this);
            mwc.setupController();
            return mwc;
        }

    }

    /** Find views by id and assign to class fields */
    private void getViewsFromWidget() {
        mMetaDataLoadingIndicator = mView.findViewById(R.id.metadata_loading_indicator);
        mTitle = mView.findViewById(R.id.title);
        mAlbumCover = mView.findViewById(R.id.album_art);
        TextView subtitle = mView.findViewById(R.id.subtitle);
        mSubtitle = subtitle != null ? subtitle : mView.findViewById(R.id.artist);
        mDescription = mView.findViewById(R.id.album_title);
        mLogo = mView.findViewById(R.id.content_format);

        mAppIcon = mView.findViewById(R.id.media_widget_app_icon);
        mAppName = mView.findViewById(R.id.media_widget_app_name);

        mCurrentTime = mView.findViewById(R.id.current_time);
        mTimeSeparator = mView.findViewById(R.id.inner_separator);
        mMaxTime = mView.findViewById(R.id.max_time);
        mSeekBar = mView.findViewById(R.id.playback_seek_bar);

        mPlayPauseButton = mView.findViewById(R.id.play_pause_button);
        mActions = ViewUtils.getViewsById(mView, mContext.getResources(),
                R.array.playback_action_slot_ids, null);
        mQueueButton = mView.findViewById(R.id.queue_button);
        mHistoryButton = mView.findViewById(R.id.history_button);
        mActionOverflowButton = mView.findViewById(R.id.overflow_button);
    }

    /**
     * Instantiate and set observers on the {@link androidx.lifecycle.LiveData} of
     * {@link PlaybackViewModel}
     */
    private void setUpDataModelObservers() {
        int max = mContext.getResources().getInteger(
                R.integer.media_items_bitmap_max_size_px);
        mAlbumArtBinder = new ImageBinder<>(ImageBinder.PlaceholderType.FOREGROUND,
                new Size(max, max), this::updateAlbumCoverWithDrawable);
        mLogoBinder = new ImageBinder<>(ImageBinder.PlaceholderType.NONE, new Size(max, max),
                this::updateLogoWithDrawable);
        mDataModel.getMetadata().observe(mViewLifecycle, this::updateMetadata);
        mDataModel.getMediaSource().observe(mViewLifecycle, this::updateMediaSource);
        mDataModel.getProgress().observe(mViewLifecycle, this::updateProgress);
        mDataModel.getMediaSourceColors().observe(mViewLifecycle,
                this::updateViewsWithMediaSourceColors);
        mDataModel.getPlaybackStateWrapper().observe(mViewLifecycle, this::updatePlaybackState);
    }

    /** Get the {@link LifecycleOwner} of the ViewGroup of this Controller */
    protected LifecycleOwner getViewLifecycleOwner() {
        return mViewLifecycle;
    }

    /** Do any necessary set up for the mSeekBar like setting OnSeekBarChangeListener */
    protected void setUpSeekBar() {
        if (mSeekBar != null) {
            mSeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                @Override
                public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                    if (fromUser && seekBar.isFocused()) {
                        PlaybackController playbackController =
                                mDataModel.getPlaybackController().getValue();
                        if (playbackController != null) {
                            playbackController.seekTo(seekBar.getProgress());
                        }
                    }
                }

                @Override
                public void onStartTrackingTouch(SeekBar seekBar) {
                    mTrackingTouch = true;
                }

                @Override
                public void onStopTrackingTouch(SeekBar seekBar) {
                    PlaybackController controller = mDataModel.getPlaybackController().getValue();
                    if (mTrackingTouch && controller != null) {
                        controller.seekTo(seekBar.getProgress());
                    }
                    mTrackingTouch = false;
                }
            });
        }
    }

    /** Do any necessary set up for the mQueueButton like setting its state and OnClickListener */
    protected void setUpQueueButton() {
        if (mQueueButton != null) {
            mQueueButton.setOnClickListener(this::handleQueueButtonClicked);
        }
        mDataModel.hasQueue().observe(mViewLifecycle, hasQueue -> {
            mHasQueue = (hasQueue != null) && hasQueue;
            updateQueueState(mHasQueue, mHasQueue && mViewModel.getQueueVisible());
        });
    }

    /**
     * Handles the onClick logic for the mQueueButton by toggling
     * {@link PlaybackCardViewModel#setQueueVisible(boolean)}. Should also handle showing or hiding
     * the queue, based on {@link PlaybackCardViewModel#getQueueVisible()}. It is also recommended
     * to query {@link #getMediaHasQueue()} before deciding to show the queue.
     */
    @CallSuper
    protected void handleQueueButtonClicked(View queue) {
        boolean isQueueVisible = mViewModel.getQueueVisible();
        mViewModel.setQueueVisible(!isQueueVisible);
    }

    /** Updates the mQueueButton UI state and handles showing or hiding the queue */
    protected void updateQueueState(boolean hasQueue, boolean isQueueVisible) {
        if (mQueueButton != null) {
            mQueueButton.setEnabled(hasQueue);
            mQueueButton.setSelected(isQueueVisible);
        }
    }

    /** Source of truth for whether the currently playing source has a queue */
    protected boolean getMediaHasQueue() {
        return mHasQueue;
    }

    /** Do any necessary set up for the mHistoryButton like setting its state and OnClickListener */
    protected void setUpHistoryButton() {
        if (mHistoryButton != null) {
            mHistoryButton.setOnClickListener(this::handleHistoryButtonClicked);
            mHistoryButton.setSelected(mViewModel.getHistoryVisible());
        }
    }

    /**
     * Handles the onClick logic for the mHistoryButton by toggling
     * {@link PlaybackCardViewModel#setHistoryVisible(boolean)}. Should also handle showing or hiding
     * the history, based on {@link PlaybackCardViewModel#getHistoryVisible()}.
     */
    @CallSuper
    protected void handleHistoryButtonClicked(View history) {
        boolean isHistoryVisible = mViewModel.getHistoryVisible();
        mViewModel.setHistoryVisible(!isHistoryVisible);
    }

    /**
     * Do any necessary set up for the mActionOverflowButton like setting its state and
     * OnClickListener
     */
    protected void setUpActionsOverflowButton() {
        if (mActionOverflowButton != null) {
            mActionOverflowButton.setOnClickListener(
                    this::handleCustomActionsOverflowButtonClicked);
            mActionOverflowButton.setSelected(mViewModel.getOverflowExpanded());
        }
    }

    /**
     * Handles the onClick logic for the mActionOverflowButton by toggling
     * {@link PlaybackCardViewModel#setOverflowExpanded(boolean)}. Should also handle showing or
     * hiding the overflow actions, based on {@link PlaybackCardViewModel#getOverflowExpanded()}.
     */
    @CallSuper
    protected void handleCustomActionsOverflowButtonClicked(View overflow) {
        boolean isOverflowExpanded = mViewModel.getOverflowExpanded();
        mViewModel.setOverflowExpanded(!isOverflowExpanded);
    }

    /** Update views with {@link MediaItemMetadata} */
    protected void updateMetadata(MediaItemMetadata metadata) {
        if (metadata != null) {
            ViewUtils.setVisible(mMetaDataLoadingIndicator, false);
            String defaultTitle = mContext.getString(
                    R.string.metadata_default_title);
            updateTextViewAndVisibility(mTitle, metadata.getTitle(), defaultTitle);
            updateTextViewAndVisibility(mSubtitle, metadata.getSubtitle());
            updateMediaLink(mSubtitleLinker, metadata.getSubtitleLinkMediaId());
            updateTextViewAndVisibility(mDescription, metadata.getDescription());
            updateMediaLink(mDescriptionLinker, metadata.getDescriptionLinkMediaId());
            updateMetadataAlbumCoverArtworkRef(metadata.getArtworkKey());
            updateMetadataLogoWithUri(metadata);
        } else {
            ViewUtils.setVisible(mMetaDataLoadingIndicator, true);
            ViewUtils.setVisible(mTitle, false);
            ViewUtils.setVisible(mSubtitle, false);
            ViewUtils.setVisible(mDescription, false);
            // Set current images to null
            mAlbumArtBinder.setImage(mContext, /* newRef= */ null);
            mLogoBinder.setImage(mContext, /* newRef= */ null);
        }
    }

    /** Update mAlbumArtBinder {@link ArtworkRef} with {@link MediaItemMetadata#getArtworkKey()}  */
    private void updateMetadataAlbumCoverArtworkRef(ArtworkRef artworkRef) {
        MediaSource mediaSource =
                mViewModel.getMediaSourceViewModel().getPrimaryMediaSource().getValue();
        if (mAlbumCover != null && artworkRef != null && mediaSource != null) {
            mediaSource.loadImage(mAlbumArtBinder, mContext, artworkRef);
        }
    }

    /** Update mLogoBinder {@link UriArtRef} with {@link MediaItemMetadata}  */
    private void updateMetadataLogoWithUri(MediaItemMetadata metadata) {
        MediaSource mediaSource =
                mViewModel.getMediaSourceViewModel().getPrimaryMediaSource().getValue();
        if (mLogo != null && mediaSource != null) {
            Uri logoUri = mLogo.prepareToDisplay(metadata);
            mediaSource.loadImage(mLogoBinder, mContext, new UriArtRef(logoUri));
        }
    }

    /** Update mAlbumCover {@link Drawable} with {@link ArtworkRef#getImage(Context)} */
    protected void updateAlbumCoverWithDrawable(Drawable drawable) {
        updateImageViewDrawableAndVisibility(mAlbumCover, drawable);
    }

    /** Update mLogo {@link Drawable} with {@link ImageBinder.ImageRef#getImage(Context)} */
    protected void updateLogoWithDrawable(Drawable drawable) {
        updateImageViewDrawableAndVisibility(mLogo, drawable);
    }

    /** Update views with {@link MediaSource} */
    protected void updateMediaSource(MediaSource mediaSource) {
        if (mediaSource != null) {
            updateImageViewDrawableAndVisibility(mAppIcon, mediaSource.getIcon());
            updateTextViewAndVisibility(mAppName, mediaSource.getDisplayName(mContext));
        } else {
            ViewUtils.setVisible(mAppIcon, false);
            ViewUtils.setVisible(mAppName, false);
        }
    }

    /** Update views with {@link PlaybackProgress} */
    protected void updateProgress(PlaybackProgress progress) {
        if (progress != null && progress.hasTime()) {
            updateProgressTimesAndSeparator(mCurrentTime, mMaxTime, mTimeSeparator,
                    progress.getCurrentTimeText(), progress.getMaxTimeText());
            updateProgressBarProgressAndMax(progress);
        } else {
            ViewUtils.setVisible(mCurrentTime, false);
            ViewUtils.setVisible(mMaxTime, false);
            ViewUtils.setVisible(mTimeSeparator, false);
            if (mSeekBar != null) {
                mSeekBar.setProgress(0);
            }
        }
    }

    /** Update mSeekBar {@link SeekBar} with {@link PlaybackProgress#getProgressFraction()} */
    private void updateProgressBarProgressAndMax(PlaybackProgress progress) {
        if (mSeekBar != null) {
            mSeekBar.setMax((int) progress.getMaxProgress());
            if (!mTrackingTouch) {
                mSeekBar.setProgress((int) progress.getProgress());
            }
        }
        ViewUtils.setVisible(mSeekBar, true);
    }

    /** Update views with colors from {@link MediaSourceColors}
     * The colors are {@link MediaSourceColors#getPrimaryColor(int)},
     * {@link MediaSourceColors#getPrimaryColorDark(int)},
     * {@link MediaSourceColors#getAccentColor(int)}
     */
    protected void updateViewsWithMediaSourceColors(MediaSourceColors colors) {}

    /** Update views with data from the {@link PlaybackStateWrapper} */
    protected void updatePlaybackState(PlaybackStateWrapper playbackState) {
        if (playbackState != null) {
            PlaybackController playbackController =
                    mDataModel.getPlaybackController().getValue();
            updatePlayButtonWithPlaybackState(mPlayPauseButton, playbackState, playbackController);
            updateActionsWithPlaybackState(mContext, mActions, playbackState,
                    mDataModel.getPlaybackController().getValue(),
                    mContext.getDrawable(R.drawable.ic_skip_previous),
                    mContext.getDrawable(R.drawable.ic_skip_next), null, null,
                    false, null);
            updateSeekbarWithPlaybackState(mSeekBar, playbackState);
        }
    }
}
