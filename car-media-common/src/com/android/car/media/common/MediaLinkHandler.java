/*
 * Copyright 2023 The Android Open Source Project
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

package com.android.car.media.common;

import android.support.v4.media.MediaBrowserCompat;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.android.car.media.common.browse.MediaItemsRepository;

import java.util.Objects;

/** Handles views that may contain links to media items. */
public class MediaLinkHandler {

    private static final String TAG = "MediaLinkHandler";

    /** Delegate interface to handle actions like links. */
    public interface MediaLinkDelegate {
        /** Invoked when the user clicks on a browse link. */
        void goToMediaItem(MediaItemMetadata mediaItem);
    }

    private final MediaItemsRepository mMediaItemsRepository;
    private final MediaLinkDelegate mControllerDelegate;
    private final View mDisplayView;
    private String mLinkedMediaId;

    public MediaLinkHandler(@Nullable MediaItemsRepository repository,
            @Nullable MediaLinkDelegate delegate, @Nullable View displayView) {
        mMediaItemsRepository = repository;
        mControllerDelegate = delegate;
        mDisplayView = displayView;
    }

    /** Set the {@link MediaBrowserCompat.MediaItem} to navigate to when the view is clicked */
    public void setLinkedMediaId(String mediaId) {
        if (mDisplayView == null) {
            return;
        }

        mLinkedMediaId = mediaId;
        if (TextUtils.isEmpty(mLinkedMediaId) || (mControllerDelegate == null)
                || (mMediaItemsRepository == null)) {
            mDisplayView.setOnClickListener(null);
            mDisplayView.setClickable(false); // setOnClickListener makes the view clickable...
        } else {
            mDisplayView.setOnClickListener(view -> mMediaItemsRepository.getItem(mLinkedMediaId,
                    new MediaBrowserCompat.ItemCallback() {
                        @Override
                        public void onItemLoaded(MediaBrowserCompat.MediaItem item) {
                            String itemId = (item != null) ? item.getMediaId() : null;
                            if (Objects.equals(itemId, mLinkedMediaId)) {
                                MediaItemMetadata mim = new MediaItemMetadata(item);
                                mControllerDelegate.goToMediaItem(mim);
                            } else {
                                Log.e(TAG, "ID mismatch. requested: [" + mLinkedMediaId
                                        + "], received: [" + itemId + "]");
                            }
                        }

                        @Override
                        public void onError(@NonNull String itemId) {
                            Log.e(TAG, "Failed to fetch item: " + itemId);
                        }
                    }));
        }
    }
}
