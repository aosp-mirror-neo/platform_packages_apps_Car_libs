/*
 * Copyright 2018 The Android Open Source Project
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

import static android.support.v4.media.MediaMetadataCompat.METADATA_KEY_DISPLAY_DESCRIPTION;
import static android.support.v4.media.MediaMetadataCompat.METADATA_KEY_DISPLAY_SUBTITLE;

import static androidx.car.app.mediaextensions.MetadataExtras.KEY_EXCLUDE_MEDIA_ITEM_FROM_MIXED_APP_LIST;
import static androidx.car.app.mediaextensions.MetadataExtras.KEY_TINTABLE_INDICATOR_ICON_URI_LIST;

import static com.android.car.media.common.MediaConstants.KEY_DESCRIPTION_LINK_MEDIA_ID;
import static com.android.car.media.common.MediaConstants.KEY_IMMERSIVE_AUDIO;
import static com.android.car.media.common.MediaConstants.KEY_SUBTITLE_LINK_MEDIA_ID;

import android.content.Context;
import android.content.res.Resources;
import android.content.res.TypedArray;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Bundle;
import android.support.v4.media.MediaBrowserCompat;
import android.support.v4.media.MediaDescriptionCompat;
import android.support.v4.media.MediaMetadataCompat;
import android.support.v4.media.session.MediaSessionCompat;
import android.text.TextUtils;
import android.util.Log;

import androidx.annotation.ArrayRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import androidx.car.app.mediaextensions.MetadataExtras;
import androidx.media.utils.MediaConstants;

import com.android.car.apps.common.BitmapUtils;
import com.android.car.apps.common.CommonFlags;
import com.android.car.apps.common.imaging.ImageBinder;
import com.android.car.apps.common.imaging.ImageBinder.PlaceholderType;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Abstract representation of a media item metadata.
 *
 * For media art, only local uris are supported so downloads can be attributed to the media app.
 * Bitmaps are not supported because they slow down the binder.
 */
public class MediaItemMetadata {
    private static final String TAG = "MediaItemMetadata";

    static final int INVALID_MEDIA_ART_TINT_COLOR = Color.argb(200, 255, 0, 0);

    public static final int     NO_PLAYBACK_STATUS = -1;
    public static final double  NO_PROGRESS = -1.0;

    @NonNull
    private final MediaDescriptionCompat mMediaDescription;
    /**
     * Stores the bundle from {@link MediaMetadataCompat#getBundle} to access its extras since they
     * are not included in the {@link MediaMetadataCompat#getDescription}.
     */
    @Nullable
    private final Bundle mMetadataCompatBundle;
    @Nullable
    private final Long mQueueId;
    private final boolean mIsBrowsable;
    private final boolean mIsPlayable;
    private final ArtworkRef mArtworkKey = new ArtworkRef();


    /** Creates an instance based on a {@link MediaMetadataCompat} */
    public MediaItemMetadata(@NonNull MediaMetadataCompat metadata) {
        this(metadata.getDescription(), null, false, false,
                metadata.getBundle());
    }

    /** Creates an instance based on a {@link MediaSessionCompat.QueueItem} */
    public MediaItemMetadata(@NonNull MediaSessionCompat.QueueItem queueItem) {
        this(queueItem.getDescription(), queueItem.getQueueId(), false, true, null);
    }

    /** Creates an instance based on a {@link MediaBrowserCompat.MediaItem} */
    public MediaItemMetadata(@NonNull MediaBrowserCompat.MediaItem item) {
        this(item.getDescription(), null, item.isBrowsable(), item.isPlayable(), null);
    }

    /** Creates a MediaItemMetadata where only the media id is set. */
    public static MediaItemMetadata createEmptyRootData(String rootId) {
        MediaDescriptionCompat.Builder bb = new MediaDescriptionCompat.Builder();
        bb.setMediaId(rootId);
        bb.setTitle("Root");
        return new MediaItemMetadata(bb.build(), null, true, false, null);
    }

    @VisibleForTesting
    public MediaItemMetadata(@NonNull MediaDescriptionCompat description, @Nullable Long queueId,
                             boolean isBrowsable, boolean isPlayable,
                             @Nullable Bundle metadataCompatBundle) {
        mMediaDescription = description;
        mQueueId = queueId;
        mIsPlayable = isPlayable;
        mIsBrowsable = isBrowsable;
        mMetadataCompatBundle = metadataCompatBundle;
    }

    /**
     * The key to access the image to display for this media item.
     * Implemented as a class so that later we can support showing different images for the same
     * item (eg: cover and author) by adding other keys.
     */
    public class ArtworkRef implements ImageBinder.ImageRef {

        private @Nullable Bitmap getBitmapToFlag(Context context) {
            CommonFlags flags = CommonFlags.getInstance(context);
            return (flags.shouldFlagImproperImageRefs() && (mMediaDescription != null))
                    ? mMediaDescription.getIconBitmap() : null;
        }

        private int getPlaceholderHash() {
            // Only the title is reliably populated in metadata, since the album/artist fields
            // aren't set in the items retrieved from the browse service (only Title/Subtitle).
            return (getTitle() != null) ? getTitle().hashCode() : 0;
        }

        @Override
        public String toString() {
            return "title: " + getTitle() + " uri: " + getNonEmptyArtworkUri();
        }

        @Override
        public @Nullable Uri getImageURI() {
            return getNonEmptyArtworkUri();
        }

        @Override
        public boolean equals(Context context, Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            ArtworkRef other = (ArtworkRef) o;

            Bitmap myBitmap = getBitmapToFlag(context);
            Bitmap otherBitmap = other.getBitmapToFlag(context);
            if ((myBitmap != null) || (otherBitmap != null)) {
                return Objects.equals(myBitmap, otherBitmap);
            }

            Uri myUri = getImageURI();
            Uri otherUri = other.getImageURI();
            if ((myUri != null) || (otherUri != null)) {
                return Objects.equals(myUri, otherUri);
            }

            return getPlaceholderHash() == other.getPlaceholderHash();
        }

        @Override
        public @Nullable Drawable getImage(Context context) {
            Bitmap bitmap = getBitmapToFlag(context);
            if (bitmap != null) {
                Resources res = context.getResources();
                return new BitmapDrawable(res, BitmapUtils.createTintedBitmap(bitmap,
                        context.getColor(
                                com.android.car.apps.common.R.color.improper_image_refs_tint_color
                        )));
            }
            return null;
        }

        @Override
        public Drawable getPlaceholder(Context context, @NonNull PlaceholderType type) {
            if (type == PlaceholderType.NONE) return null;

            List<Drawable> placeholders = getPlaceHolders(type, context);
            int random = Math.floorMod(getPlaceholderHash(), placeholders.size());
            return placeholders.get(random);
        }
    }

    /** @return media item id */
    @Nullable
    public String getId() {
        return mMediaDescription.getMediaId();
    }

    /** @return media item title */
    @Nullable
    public CharSequence getTitle() {
        return mMediaDescription.getTitle();
    }

    /** @return media item subtitle */
    @Nullable
    public CharSequence getSubtitle() {
        return mMediaDescription.getSubtitle();
    }

    /**
     * Returns the media ID of the item associated with the subtitle.
     * Note: apps must explicitly set the subtitle string and the link, otherwise null is returned.
     */
    @Nullable
    public String getSubtitleLinkMediaId() {
        if (mMetadataCompatBundle == null) {
            return null;
        }

        String subtitle = mMetadataCompatBundle.getString(METADATA_KEY_DISPLAY_SUBTITLE);
        if (TextUtils.isEmpty(subtitle)) {
            return null;
        }

        return mMetadataCompatBundle.getString(KEY_SUBTITLE_LINK_MEDIA_ID);
    }

    /**
     * Returns the media ID of the item associated with the description.
     * Note: apps must explicitly set the description string and the link, or null is returned.
     */
    @Nullable
    public String getDescriptionLinkMediaId() {
        if (mMetadataCompatBundle == null) {
            return null;
        }

        String description = mMetadataCompatBundle.getString(METADATA_KEY_DISPLAY_DESCRIPTION);
        if (TextUtils.isEmpty(description)) {
            return null;
        }

        return mMetadataCompatBundle.getString(KEY_DESCRIPTION_LINK_MEDIA_ID);
    }

    /** Returns whether the IMMERSIVE_AUDIO extra is set. */
    public boolean isImmersiveAudio() {
        return (mMetadataCompatBundle != null) && mMetadataCompatBundle.getLong(KEY_IMMERSIVE_AUDIO)
                == MediaConstants.METADATA_VALUE_ATTRIBUTE_PRESENT;
    }

    /** Returns whether the EXCLUDE_MEDIA_ITEM extra is set. */
    public boolean shouldExcludeItemFromMixedAppList() {
        return (mMetadataCompatBundle != null) && mMetadataCompatBundle.getLong(
                KEY_EXCLUDE_MEDIA_ITEM_FROM_MIXED_APP_LIST)
                == MediaConstants.METADATA_VALUE_ATTRIBUTE_PRESENT;
    }

    /** Returns the string value associated with the given key. */
    @Nullable
    public String getStringProperty(String key) {
        if (mMetadataCompatBundle == null) {
            return null;
        }
        return mMetadataCompatBundle.getString(key);
    }

    /**
     * Returns the list of non empty uris extracted from the metadata extra:
     * {@link MetadataExtras#KEY_TINTABLE_INDICATOR_ICON_URI_LIST}.
     */
    @NonNull
    public List<Uri> getSmallIconsUriList() {
        Bundle extras = mMediaDescription.getExtras();
        if (extras == null) {
            return Collections.emptyList();
        }
        ArrayList<Uri> extra = extras.getParcelableArrayList(KEY_TINTABLE_INDICATOR_ICON_URI_LIST);
        return (extra != null) ? extra : Collections.emptyList();
    }

    /** @return media item description */
    @Nullable
    public CharSequence getDescription() {
        return mMediaDescription.getDescription();
    }

    /**
     * @return the id of this item in the session queue, or NULL if this is not a session queue
     * item.
     */
    @Nullable
    public Long getQueueId() {
        return mQueueId;
    }


    public ArtworkRef getArtworkKey() {
        return mArtworkKey;
    }

    /**
     * @return a {@link Uri} referencing the artwork's bitmap.
     */
    private @Nullable Uri getNonEmptyArtworkUri() {
        Uri uri = mMediaDescription.getIconUri();
        return (uri != null && !TextUtils.isEmpty(uri.toString())) ? uri : null;
    }

    /**
     * @return optional extras that can include extra information about the media item to be played.
     */
    public Bundle getExtras() {
        return mMediaDescription.getExtras();
    }

    /**
     * @return boolean that indicate if media is explicit.
     */
    public boolean isExplicit() {
        Bundle extras = mMediaDescription.getExtras();
        return extras != null && extras.getLong(MediaConstants.METADATA_KEY_IS_EXPLICIT)
                == MediaConstants.METADATA_VALUE_ATTRIBUTE_PRESENT;
    }

    /**
     * @return boolean that indicate if media is downloaded.
     */
    public boolean isDownloaded() {
        Bundle extras = mMediaDescription.getExtras();
        return extras != null
                && extras.getLong(MediaDescriptionCompat.EXTRA_DOWNLOAD_STATUS)
                == MediaDescriptionCompat.STATUS_DOWNLOADED;
    }

    /**
     * Checks {@link MediaConstants#DESCRIPTION_EXTRAS_KEY_COMPLETION_STATUS}
     * @return
     */
    public boolean hasPlaybackStatus() {
        if (mMediaDescription.getExtras() != null) {
            return mMediaDescription.getExtras().getInt(
                    MediaConstants.DESCRIPTION_EXTRAS_KEY_COMPLETION_STATUS,
                    NO_PLAYBACK_STATUS)
                    != NO_PLAYBACK_STATUS;
        }
        return false;
    }

    /**
     * <p></p>
     * Returns the playback status
     * {@link MediaConstants#DESCRIPTION_EXTRAS_KEY_COMPLETION_STATUS}
     * optionally stored in the {@link MediaItemMetadata#getExtras()}.
     * </p>
     * <p>
     * Can return:
     *
     * {@link MediaConstants#DESCRIPTION_EXTRAS_VALUE_COMPLETION_STATUS_NOT_PLAYED}
     * {@link MediaConstants#DESCRIPTION_EXTRAS_VALUE_COMPLETION_STATUS_PARTIALLY_PLAYED}
     * {@link MediaConstants#DESCRIPTION_EXTRAS_VALUE_COMPLETION_STATUS_FULLY_PLAYED}
     * </p>
     * <p>
     * Defaults to DESCRIPTION_EXTRAS_VALUE_COMPLETION_STATUS_NO_VALUE
     * if the optional value is not in the bundle.
     * </p>
     * <p>
     * If status {@link MediaConstants#DESCRIPTION_EXTRAS_VALUE_COMPLETION_STATUS_PARTIALLY_PLAYED}
     * call {@link MediaItemMetadata#getProgress} to get the progress percentage
     * </p>
     *
     * @return playback status
     * @see MediaConstants#DESCRIPTION_EXTRAS_KEY_COMPLETION_STATUS
     */
    public int getPlaybackStatus() {
        if (mMediaDescription.getExtras() != null) {
            return mMediaDescription.getExtras().getInt(
                    MediaConstants.DESCRIPTION_EXTRAS_KEY_COMPLETION_STATUS,
                    NO_PLAYBACK_STATUS);
        }
        return NO_PLAYBACK_STATUS;
    }

    /**
     * Checks {@link MediaConstants#DESCRIPTION_EXTRAS_KEY_COMPLETION_PERCENTAGE}
     * @return
     */
    public boolean hasProgress() {
        if (mMediaDescription.getExtras() != null) {
            return mMediaDescription.getExtras().getDouble(
                    MediaConstants.DESCRIPTION_EXTRAS_KEY_COMPLETION_PERCENTAGE,
                    NO_PROGRESS)
                    > NO_PROGRESS;
        }
        return false;
    }

    /**
     * Returns the playback percentage stored in {@link MediaItemMetadata#getExtras()}
     * Value is stored with key {@link MediaConstants#DESCRIPTION_EXTRAS_KEY_COMPLETION_PERCENTAGE}
     *
     * @return progress 0.0 - 1.0 inclusive , default to -1.0
     * {@link MediaItemMetadata#NO_PROGRESS} if optional key in not
     * present @see MediaConstants#DESCRIPTION_EXTRAS_KEY_COMPLETION_PERCENTAGE
     */
    public double getProgress() {
        if (mMediaDescription.getExtras() != null) {
            return mMediaDescription.getExtras().getDouble(
                    MediaConstants.DESCRIPTION_EXTRAS_KEY_COMPLETION_PERCENTAGE,
                    NO_PROGRESS);
        }
        return NO_PROGRESS;
    }

    /**
     * Update progress
     */
    public void setProgress(double progress) {
        if (mMediaDescription.getExtras() != null) {
            mMediaDescription.getExtras().putDouble(
                    MediaConstants.DESCRIPTION_EXTRAS_KEY_COMPLETION_PERCENTAGE, progress);
        }
    }

    private static Map<PlaceholderType, List<Drawable>> sPlaceHolders = new HashMap<>();

    private static @ArrayRes int getPlaceHolderArray(PlaceholderType type) {
        switch (type) {
            case FOREGROUND_ICON:
                return R.array.placeholder_icons;
            case BACKGROUND:
                return R.array.placeholder_backgrounds;
            case FOREGROUND:
            default:
                return R.array.placeholder_images;
        }
    }

    /**
     * Clears the static cache of placeholder drawables. This should be called
     * when the application's theme or configuration changes.
     */
    public static void clearPlaceholderCache() {
        sPlaceHolders.clear();
    }

    private static List<Drawable> getPlaceHolders(PlaceholderType type, Context context) {
        List<Drawable> placeHolders = sPlaceHolders.get(type);
        if (placeHolders == null) {
            TypedArray placeholderImages = context.getResources().obtainTypedArray(
                    getPlaceHolderArray(type));

            if (placeholderImages == null) {
                throw new NullPointerException("No placeholders for " + type);
            }

            placeHolders = new ArrayList<>(placeholderImages.length());
            for (int i = 0; i < placeholderImages.length(); i++) {
                int resId = placeholderImages.getResourceId(i, 0);
                if (resId != 0) {
                    // Use the context to get proper theming.
                    placeHolders.add(context.getDrawable(resId));
                }
            }
            placeholderImages.recycle();
            sPlaceHolders.put(type, placeHolders);

            if (sPlaceHolders.size() <= 0) {
                throw new Resources.NotFoundException("Placeholders should not be empty " + type);
            }
        }
        return placeHolders;
    }

    public boolean isBrowsable() {
        return mIsBrowsable;
    }

    /**
     * @return Content style hint for browsable items, if provided as an extra, or
     * 0 as default value if not provided.
     */
    public int getBrowsableContentStyleHint() {
        Bundle extras = mMediaDescription.getExtras();
        if (extras != null) {
            if (extras.containsKey(MediaConstants.DESCRIPTION_EXTRAS_KEY_CONTENT_STYLE_BROWSABLE)) {
                return extras.getInt(MediaConstants.DESCRIPTION_EXTRAS_KEY_CONTENT_STYLE_BROWSABLE,
                        0);
            }
        }
        return 0;
    }

    public boolean isPlayable() {
        return mIsPlayable;
    }

    /**
     * @return Content style hint for playable items, if provided as an extra, or
     * 0 as default value if not provided.
     */
    public int getPlayableContentStyleHint() {
        Bundle extras = mMediaDescription.getExtras();
        if (extras != null) {
            if (extras.containsKey(MediaConstants.DESCRIPTION_EXTRAS_KEY_CONTENT_STYLE_PLAYABLE)) {
                return extras.getInt(MediaConstants.DESCRIPTION_EXTRAS_KEY_CONTENT_STYLE_PLAYABLE,
                        0);
            }
        }
        return 0;
    }

    /**
     * @return Content style hint for single item, if provided as an extra, or 0 as default if not
     * provided.
     */
    public int getSingleItemContentStyleHint() {
        Bundle extras = mMediaDescription.getExtras();
        if (extras != null) {
            if (extras.containsKey(
                    MediaConstants.DESCRIPTION_EXTRAS_KEY_CONTENT_STYLE_SINGLE_ITEM)) {
                return extras.getInt(
                        MediaConstants.DESCRIPTION_EXTRAS_KEY_CONTENT_STYLE_SINGLE_ITEM, 0);
            }
        }
        return 0;
    }

    /**
     * @return Content style title group this item belongs to, or null if not provided
     */
    public String getTitleGrouping() {
        Bundle extras = mMediaDescription.getExtras();
        if (extras != null) {
            if (extras.containsKey(
                    MediaConstants.DESCRIPTION_EXTRAS_KEY_CONTENT_STYLE_GROUP_TITLE)) {
                return extras.getString(
                        MediaConstants.DESCRIPTION_EXTRAS_KEY_CONTENT_STYLE_GROUP_TITLE, null);
            }
        }
        return null;
    }

    /** Returns list of browse custom actions if present, empty if not present */
    public List<String> getBrowseCustomActionIds() {
        Bundle extras = mMediaDescription.getExtras();
        if (extras != null) {
            if (extras.containsKey(
                    com.android.car.media.common.MediaConstants.BROWSE_CUSTOM_ACTIONS_ITEM_LIST)) {
                return extras.getStringArrayList(
                        com.android.car.media.common.MediaConstants
                                .BROWSE_CUSTOM_ACTIONS_ITEM_LIST);
            }
        }
        return Collections.emptyList();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        MediaItemMetadata that = (MediaItemMetadata) o;
        return mIsBrowsable == that.mIsBrowsable
                && mIsPlayable == that.mIsPlayable
                && Objects.equals(getId(), that.getId())
                && Objects.equals(getTitle(), that.getTitle())
                && Objects.equals(getSubtitle(), that.getSubtitle())
                && Objects.equals(getDescription(), that.getDescription())
                && Objects.equals(getNonEmptyArtworkUri(), that.getNonEmptyArtworkUri())
                && Objects.equals(mQueueId, that.mQueueId)
                && Objects.equals(hasPlaybackStatus(), that.hasPlaybackStatus())
                && Objects.equals(hasProgress(), that.hasProgress())
                && areDoublesClose(getProgress(), that.getProgress(),  .0001)
                && Objects.equals(getSubtitleLinkMediaId(), that.getSubtitleLinkMediaId())
                && Objects.equals(getDescriptionLinkMediaId(), that.getDescriptionLinkMediaId());
    }

    /**
     * Checks whether 2 doubles are within the given threshold.
     * Threshold is the maximum difference before it's considered unequal.
     *
     * @param threshold Usually accurate to .001 or .0001
     * @return if equal
     */
    public static boolean areDoublesClose(double first, double second, double threshold) {
        if (threshold < 0.0) {
            Log.w(TAG, "areDoublesClose threshold less than 0.0");
            threshold = 0.0;
        }
        return Math.abs(first - second) < threshold;
    }

    @Override
    public int hashCode() {
        return Objects.hash(mIsBrowsable, mIsPlayable, getId(), getTitle(), getSubtitle(),
                getDescription(), getNonEmptyArtworkUri(), mQueueId, hasPlaybackStatus(),
                hasProgress(), getProgress(), getSubtitleLinkMediaId(),
                getDescriptionLinkMediaId());
    }

    @NonNull
    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("[Queue Id: ");
        sb.append(mQueueId);
        if (mMediaDescription == null) {
            sb.append("No mMediaDescription!!]");
        } else {
            sb.append(", Media Id: ");
            sb.append(mMediaDescription.getMediaId());
            sb.append(", title: ");
            sb.append(mMediaDescription.getTitle());
            sb.append(", subtitle: ");
            sb.append(mMediaDescription.getSubtitle());
            sb.append(", album art URI: ");
            sb.append(mMediaDescription.getIconUri());
            sb.append("]");
        }

        return sb.toString();
    }
}
