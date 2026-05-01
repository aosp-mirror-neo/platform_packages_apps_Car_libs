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

package com.android.car.media.common.source;

import static android.car.media.CarMediaIntents.EXTRA_MEDIA_COMPONENT;

import android.app.ActivityOptions;
import android.app.PendingIntent;
import android.car.Car;
import android.car.media.CarMediaIntents;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.pm.ResolveInfo;
import android.content.pm.ServiceInfo;
import android.graphics.Bitmap;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.Bundle;
import android.service.media.MediaBrowserService;
import android.support.v4.media.session.MediaControllerCompat;
import android.text.TextUtils;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;

import com.android.car.apps.common.BitmapUtils;
import com.android.car.apps.common.IconCropper;
import com.android.car.apps.common.imaging.ImageBinder;
import com.android.car.media.common.R;

import java.net.URISyntaxException;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/**
 * This represents a source of media content. It provides convenient methods to access media source
 * metadata, such as application name and icon. Media content can either be derived from a
 * {@link MediaBrowserService} or a {@link MediaControllerCompat} or a PackageName.
 */
public class MediaSource {
    private static final String TAG = "MediaSource";
    @VisibleForTesting
    static final String ANDROIDX_CAR_APP_LAUNCHABLE = "androidx.car.app.launchable";
    // String Action used in CAL to signify to media apps that the PBV Template should be opened
    static final String SHOW_MEDIA_PLAYBACK = "androidx.car.app.media.action.SHOW_MEDIA_PLAYBACK";

    private static List<String> sCustomMediaComponents;
    @Nullable
    private final ComponentName mBrowseService;
    @Nullable
    private final ComponentName mIgnoredBrowseService;
    @Nullable
    private final MediaControllerCompat mMediaController;
    @NonNull
    private final String mPackageName;
    @NonNull
    private final CharSequence mDisplayName;
    @NonNull
    private final Drawable mIcon;
    @NonNull
    private final IconCropper mIconCropper;
    @NonNull
    private final PackageManager mPackageManager;

    /**
     * Creates a {@link MediaSource} for the given {@link ComponentName}
     */
    @Nullable
    public static MediaSource create(@NonNull Context ctx, @NonNull ComponentName componentName) {
        return create(ctx, componentName, /* ignoreBrowser= */ false);
    }

    /**
     * Creates a {@link MediaSource} for the given {@link ComponentName}
     */
    @Nullable
    public static MediaSource create(@NonNull Context ctx, @NonNull ComponentName componentName,
            boolean ignoreBrowser) {
        ServiceInfo serviceInfo = getBrowseServiceInfo(ctx, componentName);

        String className = serviceInfo != null ? serviceInfo.name : null;
        if (TextUtils.isEmpty(className)) {
            Log.w(TAG, "No MediaBrowserService for component " + componentName.flattenToString());
            return null;
        }

        try {
            String packageName = componentName.getPackageName();
            CharSequence displayName = extractDisplayName(ctx, serviceInfo, packageName);
            Drawable icon = extractIcon(ctx, serviceInfo, packageName);
            ComponentName browseService = ignoreBrowser ? null
                    : new ComponentName(packageName, className);
            ComponentName ignoredBrowseService = ignoreBrowser
                    ? new ComponentName(packageName, className) : null;
            return new MediaSource(browseService, ignoredBrowseService,
                    /* mediaController= */ null, packageName, displayName, icon,
                    new IconCropper(ctx), ctx.getPackageManager());
        } catch (PackageManager.NameNotFoundException e) {
            Log.w(TAG, "Component not found " + componentName.flattenToString());
            return null;
        }
    }

    /**
     * Creates a {@link MediaSource} for the given {@link MediaControllerCompat}
     */
    @Nullable
    public static MediaSource create(@NonNull Context context,
            @NonNull MediaControllerCompat mediaController) {
        return create(context, mediaController, /* ignoreBrowser= */ false);
    }

    /**
     * Creates a {@link MediaSource} for the given {@link MediaControllerCompat}
     */
    @Nullable
    public static MediaSource create(@NonNull Context context,
            @NonNull MediaControllerCompat mediaController, boolean ignoreBrowser) {
        return create(context, mediaController, null, ignoreBrowser);
    }

    /**
     * Creates a {@link MediaSource} for the given {@link MediaControllerCompat}.
     *
     * @param componentName The component name to use for the MediaSource. If null, it will be
     *                      inferred from the media controller.
     */
    @Nullable
    public static MediaSource create(@NonNull Context context,
            @NonNull MediaControllerCompat mediaController, @Nullable ComponentName componentName,
            boolean ignoreBrowser) {
        String packageName = mediaController.getPackageName();

        if (componentName == null) {
            componentName = getMediaControllerMBS(context, mediaController);
        }

        ServiceInfo serviceInfo = null;
        if (componentName != null) {
            serviceInfo = getBrowseServiceInfo(context, componentName);
            String className = serviceInfo != null ? serviceInfo.name : null;
            if (TextUtils.isEmpty(className)) {
                serviceInfo = null;
            }
        }

        try {
            CharSequence displayName = extractDisplayName(context, serviceInfo, packageName);
            Drawable icon = extractIcon(context, serviceInfo, packageName);

            ComponentName browseService = ignoreBrowser ? null : componentName;
            ComponentName ignoredBrowseService = ignoreBrowser ? componentName : null;
            return new MediaSource(browseService, ignoredBrowseService, mediaController,
                    packageName, displayName, icon, new IconCropper(context),
                    context.getPackageManager());
        } catch (NameNotFoundException e) {
            Log.w(TAG, "App not found " + packageName);
            return null;
        }
    }

    /**
     * Creates a {@link MediaSource} for the given package name. This constructor should be avoided
     * when possible as it may not map to any mbs or media session and doesn't handle multiple
     * sources per package.
     */
    @Nullable
    public static MediaSource create(@NonNull Context context, @NonNull String packageName) {
        return create(context, packageName, /* ignoreBrowser= */ false);
    }

    /**
     * Creates a {@link MediaSource} for the given package name. This constructor should be avoided
     * when possible as it may not map to any mbs or media session and doesn't handle multiple
     * sources per package.
     */
    @Nullable
    public static MediaSource create(@NonNull Context context, @NonNull String packageName,
            boolean ignoreBrowser) {
        try {
            ServiceInfo serviceInfo = null;
            ComponentName componentName = getPackageNameMBS(context, packageName);
            if (componentName != null) {
                serviceInfo = getBrowseServiceInfo(context, componentName);
                String className = serviceInfo != null ? serviceInfo.name : null;
                if (TextUtils.isEmpty(className)) {
                    serviceInfo = null;
                }
            }

            CharSequence displayName = extractDisplayName(context, serviceInfo, packageName);
            Drawable icon = extractIcon(context, serviceInfo, packageName);

            return new MediaSource(componentName, /* ignoredBrowseService= */ null,
                    /* mediaController= */ null, packageName, displayName, icon,
                    new IconCropper(context), context.getPackageManager());
        } catch (NameNotFoundException e) {
            Log.w(TAG, "App not found " + packageName);
            return null;
        }
    }

    /**
     * Create a {@link MediaSource} with the given parameters and a null {@link
     * MediaControllerCompat}
     */
    public MediaSource(@Nullable ComponentName browseService, @NonNull String packageName) {
        this(browseService, null, null, packageName, null, null, null, null);
    }

    /**
     * Create a {@link MediaSource} with the given parameters, for backward compatibility
     */
    public MediaSource(@Nullable ComponentName browseService,
            @Nullable MediaControllerCompat mediaController, @NonNull String packageName,
            @Nullable CharSequence displayName, @Nullable Drawable icon,
            @Nullable IconCropper iconCropper, @Nullable PackageManager packageManager) {
        this(browseService, null, mediaController, packageName, displayName, icon, iconCropper,
                packageManager);
    }

    /**
     * Create a {@link MediaSource} with the given parameters
     */
    @VisibleForTesting
    public MediaSource(@Nullable ComponentName browseService,
            @Nullable ComponentName ignoredBrowseService,
            @Nullable MediaControllerCompat mediaController, @NonNull String packageName,
            @Nullable CharSequence displayName, @Nullable Drawable icon,
            @Nullable IconCropper iconCropper, @Nullable PackageManager packageManager) {
        mBrowseService = browseService;
        mIgnoredBrowseService = ignoredBrowseService;
        mMediaController = mediaController;
        mPackageName = packageName;
        mDisplayName = displayName;
        mIcon = icon;
        mIconCropper = iconCropper;
        mPackageManager = packageManager;
    }

    /**
     * @return the {@link ServiceInfo} corresponding to a {@link MediaBrowserService} in the media
     * source, or null if the media source doesn't implement {@link MediaBrowserService}. A non-null
     * result doesn't imply that this service is accessible. The consumer code should attempt to
     * connect and handle rejections gracefully.
     */
    @Nullable
    private static ServiceInfo getBrowseServiceInfo(@NonNull Context context,
            @NonNull ComponentName componentName) {
        Intent intent = new Intent();
        intent.setAction(MediaBrowserService.SERVICE_INTERFACE);
        intent.setPackage(componentName.getPackageName());
        List<ResolveInfo> resolveInfos =
                queryIntentServices(context, intent, PackageManager.GET_RESOLVED_FILTER);
        if (resolveInfos == null || resolveInfos.isEmpty()) {
            return null;
        }
        String className = componentName.getClassName();
        if (TextUtils.isEmpty(className)) {
            return resolveInfos.get(0).serviceInfo;
        }
        for (ResolveInfo resolveInfo : resolveInfos) {
            ServiceInfo result = resolveInfo.serviceInfo;
            if (result.name.equals(className)) {
                return result;
            }
        }
        return null;
    }

    /**
     * @return a proper app name. Checks service label first. If failed, uses application label
     * as fallback.
     */
    @NonNull
    private static CharSequence extractDisplayName(@NonNull Context context,
            @Nullable ServiceInfo serviceInfo, @NonNull String packageName)
            throws PackageManager.NameNotFoundException {
        if (serviceInfo != null && serviceInfo.labelRes != 0) {
            return serviceInfo.loadLabel(context.getPackageManager());
        }
        ApplicationInfo applicationInfo =
                context.getPackageManager().getApplicationInfo(packageName,
                        PackageManager.GET_META_DATA);
        return applicationInfo.loadLabel(context.getPackageManager());
    }

    /**
     * @return a proper icon. Checks service icon first. If failed, uses application icon as
     * fallback.
     */
    @NonNull
    private static Drawable extractIcon(@NonNull Context context, @Nullable ServiceInfo serviceInfo,
            @NonNull String packageName) throws PackageManager.NameNotFoundException {
        Drawable appIcon = serviceInfo != null ? serviceInfo.loadIcon(context.getPackageManager())
                : context.getPackageManager().getApplicationIcon(packageName);

        return BitmapUtils.maybeFlagDrawable(context, appIcon);
    }

    /**
     * @return the browse service defined in the controller's extras if provided, null otherwise.
     */
    @Nullable
    private static ComponentName getServiceFromExtras(MediaControllerCompat controller) {
        if (controller == null) {
            Log.e(TAG, "getServiceFromExtras null controller");
            return null;
        }
        Bundle extras = controller.getExtras();
        if (extras == null || extras.getString(Car.CAR_EXTRA_BROWSE_SERVICE_FOR_SESSION) == null) {
            return null;
        }
        String serviceNameString =
                controller.getExtras().getString(Car.CAR_EXTRA_BROWSE_SERVICE_FOR_SESSION);

        return new ComponentName(controller.getPackageName(), serviceNameString);
    }

    /**
     *  @return the browse service defined in the controller's manifest if provided, null otherwise
     */
    @Nullable
    private static ComponentName getServiceFromManifest(Context context, String packageName) {
        Intent mediaIntent = new Intent();
        mediaIntent.setPackage(packageName);
        mediaIntent.setAction(MediaBrowserService.SERVICE_INTERFACE);

        List<ResolveInfo> mediaServices =
                queryIntentServices(context, mediaIntent, PackageManager.GET_RESOLVED_FILTER);

        for (ResolveInfo resolveInfo : mediaServices) {
            if (!TextUtils.isEmpty(resolveInfo.serviceInfo.name)) {
                return new ComponentName(packageName, resolveInfo.serviceInfo.name);
            }
        }

        return null;
    }

    /**
     * @return media source human readable name for display.
     */
    @NonNull
    public CharSequence getDisplayName(Context context) {
        ServiceInfo serviceInfo = null;
        if (mBrowseService != null) {
            serviceInfo = getBrowseServiceInfo(context, mBrowseService);
        } else if (mIgnoredBrowseService != null) {
            serviceInfo = getBrowseServiceInfo(context, mIgnoredBrowseService);
        }
        try {
            return extractDisplayName(context, serviceInfo, getPackageName());
        } catch (PackageManager.NameNotFoundException e) {
            Log.e(TAG, "getDisplayName: " + e);
            return mDisplayName;
        }
    }

    /**
     * @return the package name of this media source.
     */
    @NonNull
    public String getPackageName() {
        return mPackageName;
    }

    /**
     * @return a {@link ComponentName} referencing this media source's {@link MediaBrowserService}.
     */
    @Nullable
    public ComponentName getBrowseServiceComponentName() {
        return mBrowseService;
    }

    /**
     * @return the {@link ComponentName} of the ignored browse service, if it was created with
     * ignoreBrowser=true.
     */
    @Nullable
    public ComponentName getIgnoredBrowseServiceComponentName() {
        return mIgnoredBrowseService;
    }

    /**
     * @return a {@link Drawable} as the media source's icon.
     */
    @NonNull
    public Drawable getIcon() {
        return mIcon;
    }

    /**
     * Returns this media source's icon cropped to a predefined shape (see
     * {@link #IconCropper(Context)} on where and how the shape is defined).
     */
    public Bitmap getCroppedPackageIcon() {
        return mIconCropper.crop(mIcon);
    }

    /**
     * @return {@link MediaControllerCompat} of this media source
     */
    @Nullable
    public MediaControllerCompat getMediaController() {
        return mMediaController;
    }

    /**
     *  Returns the intent to open a provided MediaSource, or null if not available
     * @deprecated Prefer {@link #launchActivity } so that a PendingIntent or
     * the SHOW_PLAYBACK_VIEW Intent can be passed to the application, if supported
     */
    @Nullable
    public Intent getIntent() {
        // Only intent to a templated app with mbs
        if (mBrowseService == null) {
            return createMediaSessionIntent();
        }

        Intent intent = new Intent(CarMediaIntents.ACTION_MEDIA_TEMPLATE);
        intent.putExtra(EXTRA_MEDIA_COMPONENT, mBrowseService.flattenToString());

        return intent;
    }

    /**
     *  Returns the intent to open a provided MediaSource, or null if not available.
     *  Optionally passes the SHOW_PLAYBACK_VIEW action if the app supports it
     */
    @Nullable
    private Intent createMediaAppIntent(Context context) {
        if (mBrowseService == null) {
            // Fallback to ignored browse service if available. This is the most reliable fallback
            // as it was resolved during creation.
            if (mIgnoredBrowseService != null) {
                Intent intent = new Intent(CarMediaIntents.ACTION_MEDIA_TEMPLATE);
                intent.putExtra(EXTRA_MEDIA_COMPONENT, mIgnoredBrowseService.flattenToString());
                intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                return intent;
            }


            Intent mediaSessionIntent = createMediaSessionIntent();
            if (mediaSessionIntent == null) {
                return null;
            }
            Intent intent = new Intent(SHOW_MEDIA_PLAYBACK);
            intent.setComponent(mediaSessionIntent.getComponent());
            ResolveInfo resolveInfo = context.getPackageManager().resolveActivity(intent,
                    /* flags */ 0);
            if (resolveInfo != null) {
                return intent;
            } else {
                return mediaSessionIntent;
            }
        }

        Intent intent = new Intent(CarMediaIntents.ACTION_MEDIA_TEMPLATE);
        intent.putExtra(EXTRA_MEDIA_COMPONENT, mBrowseService.flattenToString());

        return intent;
    }

    @Nullable
    private Intent createMediaSessionIntent() {
        Intent intent = mPackageManager.getLaunchIntentForPackage(mPackageName);
        if (intent == null) {
            return null;
        }
        // FLAG_ACTIVITY_NEW_TASK brings any existing task to foreground
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

        return intent;
    }

    /**
     * Launches the activity associated with the MediaSource.
     *
     * If this is a MediaController based MediaSource with a defined activity, that PendingIntent
     * will be used. Otherwise the intent returned from getIntent() will be used.
     */
    public void launchActivity(Context context, ActivityOptions activityOptions) {
        if (context == null) {
            return;
        }

        PendingIntent pendingIntent = getPendingIntent();
        if (pendingIntent != null) {
            try {
                Log.i(TAG, "Launching PendingIntent " + pendingIntent);
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                    activityOptions.setPendingIntentBackgroundActivityStartMode(
                            ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED);
                    pendingIntent.send(activityOptions != null ? activityOptions.toBundle() : null);
                } else {
                    pendingIntent.send();
                }
            } catch (PendingIntent.CanceledException e) {
                Log.e(TAG, "Exception trying to launch PendingIntent " + e);
            }
        } else {
            Intent intent = createMediaAppIntent(context);
            if (intent != null) {
                Log.i(TAG, "Launching intent " + intent);
                try {
                    context.startActivity(intent,
                            activityOptions != null ? activityOptions.toBundle() : null);
                } catch (Exception e) {
                    Log.e(TAG, "Exception trying to launch intent", e);
                }
            } else {
                Log.w(TAG, "No intent to launch for " + mPackageName);
            }
        }
    }

    /**
     *  Returns the PendingIntent to open a media controller MediaSource, or null if not available
     */
    @Nullable
    private PendingIntent getPendingIntent() {
        // Only return PendingIntent from media controllers that define it.
        if (mBrowseService == null && mMediaController != null) {
            return mMediaController.getSessionActivity();
        }
        return null;
    }

    /**
     * Loads a given ImageRef depending on the type of MediaSource. For AAOS audio apps with an MBS,
     * prevent or flag remote uris depending on the system configuration. For other MediaSources,
     * allow the loading of remote uris.
     */
    public <T extends ImageBinder.ImageRef> void loadImage(ImageBinder<T> imageBinder,
            Context context, T imageRef) {
        imageBinder.setImage(context, imageRef, /* preventRemoteUris= */ mBrowseService != null);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        MediaSource that = (MediaSource) o;
        if (mBrowseService != null || that.mBrowseService != null) {
            // If at least one browse service is not null, compare them
            return Objects.equals(mBrowseService, that.mBrowseService);
        } else if (mMediaController != null && that.mMediaController != null
                && (mMediaController.getSessionToken() != null
                || that.mMediaController.getSessionToken() != null)) {
            // At least one session token is not null, compare them
            return Objects.equals(mMediaController.getSessionToken(),
                    that.mMediaController.getSessionToken());
        } else if (mMediaController == null ^ that.mMediaController == null) {
            // If there is only one not null media controller return false
            return false;
        } else {
            // Both MediaSources either have null browse services and media controllers, or both
            // have media controllers with null session tokens, compare package names
            return Objects.equals(mPackageName, that.mPackageName);
        }
    }

    @Override
    public int hashCode() {
        if (mBrowseService != null) {
            return Objects.hash(mBrowseService);
        } else if (mMediaController != null) {
            return Objects.hash(mMediaController);
        } else {
            return Objects.hash(mPackageName);
        }
    }

    @Override
    @NonNull
    public String toString() {
        if (mBrowseService != null) {
            return mBrowseService.flattenToString();
        } else if (mMediaController != null) {
            return mMediaController.toString();
        } else {
            return mPackageName;
        }
    }

    /**
     * @return an intent to open the media source selector, or null if no source selector is
     * configured.
     * @param popup Whether the intent should point to the regular app selector (false), which
     *              would open the selected media source in Media Center, or the "popup" version
     *              (true), which would just select the source and dismiss itself.
     */
    @Nullable
    public static Intent getSourceSelectorIntent(Context context, boolean popup) {
        String uri = context.getString(popup ? R.string.launcher_popup_intent
                : R.string.launcher_intent);
        try {
            return uri != null && !uri.isEmpty() ? Intent.parseUri(uri, Intent.URI_INTENT_SCHEME)
                    : null;
        } catch (URISyntaxException e) {
            throw new IllegalStateException("Wrong app-launcher intent: " + uri, e);
        }
    }

    /**
     * Checks if the media source is supported i.e. Audio only no video, browsers etc.
     */
    public static boolean isAudioMediaSource(Context context, ComponentName mbsComponentName) {
        if (mbsComponentName == null) {
            return false;
        }

        if (sCustomMediaComponents == null) {
            sCustomMediaComponents = Arrays.asList(
                    context.getResources().getStringArray(R.array.custom_media_packages));
        }
        if (sCustomMediaComponents.contains(mbsComponentName.flattenToString())) {
            Log.d(TAG, "Custom media component " + mbsComponentName + " is supported");
            return true;
        }
        return isMediaTemplate(context, mbsComponentName);
    }

    /**
     * Determines if the given media component is supported through media templates
     */
    public static boolean isMediaTemplate(Context context,
            @NonNull ComponentName mbsComponentName) {
        if (Log.isLoggable(TAG, Log.DEBUG)) {
            Log.d(TAG, "Checking if Component " + mbsComponentName + " is a media template");
        }

        // check the metadata for opt in info
        Bundle metaData = getMbsMetadata(context, mbsComponentName);
        if ((metaData != null) && metaData.containsKey(ANDROIDX_CAR_APP_LAUNCHABLE)) {
            boolean launchable = metaData.getBoolean(ANDROIDX_CAR_APP_LAUNCHABLE);
            Log.d(TAG, "MBS for " + mbsComponentName
                    + " is opted " + (launchable ? "in" : "out"));
            return launchable;
        }

        Log.d(TAG, "No opt-in info found for Component " + mbsComponentName);

        // No explicit declaration. For backward compatibility, keep MBS only for audio apps
        String packageName = mbsComponentName.getPackageName();
        try {
            if (isLegacyMediaApp(context, packageName)) {
                if (Log.isLoggable(TAG, Log.DEBUG)) {
                    Log.d(TAG, "Including " + mbsComponentName
                            + " for media template app " + packageName);
                }
                return true;
            }
        } catch (PackageManager.NameNotFoundException e) {
            Log.e(TAG, "Package " + packageName + " was not found");
        }
        Log.d(TAG, "Skipping MBS for " + mbsComponentName
                + " belonging to non media template app " + packageName);
        return false;
    }

    /**
     * Finds the Media Browse Service associated with the {@link MediaControllerCompat}
     */
    @Nullable
    private static ComponentName getMediaControllerMBS(Context context,
            MediaControllerCompat mediaController) {
        // Media browse service can be either provided from controller extras or defined in the
        // manifest
        ComponentName componentName = getServiceFromExtras(mediaController);
        if (componentName == null) {
            return getPackageNameMBS(context, mediaController.getPackageName());
        }

        // Only intent if the app is media templated
        return isAudioMediaSource(context, componentName) ? componentName : null;
    }

    private static ComponentName getPackageNameMBS(Context context, String packageName) {
        ComponentName componentName = getServiceFromManifest(context, packageName);

        // Only intent if the app is media templated
        return isAudioMediaSource(context, componentName) ? componentName : null;
    }

    /**
     * Determines if it's a legacy media app that doesn't have a launcher activity
     */
    private static boolean isLegacyMediaApp(Context context, String packageName)
            throws PackageManager.NameNotFoundException {
        // a media app doesn't have a launcher activity
        return context.getPackageManager().getLaunchIntentForPackage(packageName) == null;
    }

    /**
     * Finds the media browser service for the given mbs component and returns its metadata
     */
    private static Bundle getMbsMetadata(Context context, ComponentName mbsComponentName) {
        Intent mediaIntent = new Intent();
        mediaIntent.setComponent(mbsComponentName);
        mediaIntent.setAction(MediaBrowserService.SERVICE_INTERFACE);
        List<ResolveInfo> mediaServices =
                queryIntentServices(context, mediaIntent, PackageManager.GET_META_DATA);
        if (Log.isLoggable(TAG, Log.DEBUG)) {
            Log.d(TAG, "MBS info found for " + mbsComponentName + " : " + mediaServices);
        }
        if (mediaServices.isEmpty() || mediaServices.get(0) == null
                || mediaServices.get(0).serviceInfo == null) {
            return null;
        }

        return mediaServices.get(0).serviceInfo.metaData;
    }

    private static List<ResolveInfo> queryIntentServices(Context context, Intent intent, int flag) {
        return context.getPackageManager().queryIntentServices(intent, flag);
    }
}
