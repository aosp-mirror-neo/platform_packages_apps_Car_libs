/*
 * Copyright 2024 The Android Open Source Project
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

import static android.car.media.CarMediaManager.MEDIA_SOURCE_MODE_PLAYBACK;

import android.app.Notification;
import android.car.Car;
import android.car.media.CarMediaManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.media.session.MediaController;
import android.media.session.MediaSessionManager;
import android.media.session.PlaybackState;
import android.service.notification.StatusBarNotification;
import android.support.v4.media.session.MediaControllerCompat;
import android.support.v4.media.session.MediaSessionCompat;
import android.text.TextUtils;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.android.car.apps.common.util.CarPackageManagerUtils;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.Executor;

/**
 * Source of MediaSources that listens to {@link MediaSessionManager} media session changes.
 * <p>
 * This class keeps track of two different types of media sessions:
 * 1. The most important media session with an active playback state, tracked by getMediaSource().
 *    In scenarios with multiple media sessions, it prioritizes the most important returned from
 *    MediaSessionManager.
 * 2. All media sessions with an active or paused playback state, tracked by
 *    getActiveOrPausedMediaSources()
 * <p>
 *
 * Apps may not want their media sessions to be externally controlled, which may include legal
 * reasons, and instead rely on the creation of a media notification to give users the ability to
 * control the session when the app is backgrounded. For this reason callers must provide access to
 * notifications for MediaSessionHelper to filter the media sessions. This requirement may be
 * relaxed once a wider range of automotive targeted apps are available.
 *
 * For non-active MediaSessions, listeners are created to be notified if one of the others become
 * active, since playback changes don't always trigger a session change.
 */
public class MediaSessionHelper extends MediaController.Callback {

    private static final String TAG = "MediaSessionHelper";
    private static final String SHARED_PREF = MediaSessionHelper.class.getCanonicalName();
    private static final String LAST_ACTIVE_MEDIA_SOURCE = "last_active_media_source";
    private final WeakReference<Context> mContext;
    private final MutableLiveData<MediaSource> mPrimaryMediaSource = new MutableLiveData<>(null);
    private final MutableLiveData<List<MediaSource>> mActiveOrPausedMediaSources =
            new MutableLiveData<>(Collections.emptyList());
    private final MediaSessionManager mMediaSessionManager;
    private final InputFactory mInputFactory;
    private final List<MediaController> mMediaControllersList = new ArrayList<>();
    private final SharedPreferences mSharedPrefs;
    @Nullable
    private final NotificationProvider mNotificationProvider;

    @VisibleForTesting
    final MediaSessionManager.OnActiveSessionsChangedListener mActiveSessionsListener =
            this::onMediaControllersChange;
    private static MediaSessionHelper sInstance;

    private PackageManager mPackageManager;
    @Nullable
    private final SessionProvider mSessionProvider;
    private final boolean mIgnoreBrowser;

    /**
     *  Returns the singleton.
     *  @deprecated Use {@link #getInstance(Context, NotificationProvider)} instead
     */
    @Deprecated
    public static MediaSessionHelper getInstance(@NonNull Context context) {
        if (sInstance == null) {
            sInstance = new MediaSessionHelper(context.getApplicationContext());
        }
        return sInstance;
    }

    /** Returns the singleton. */
    public static MediaSessionHelper getInstance(@NonNull Context context,
            @Nullable NotificationProvider notificationProvider) {
        if (sInstance == null) {
            sInstance =
                    new MediaSessionHelper(context.getApplicationContext(), notificationProvider);
        }
        return sInstance;
    }

    /** Class that provides a list of active notifications. */
    public interface NotificationProvider {

        /** Returns a list of currently active notifications */
        StatusBarNotification[] getActiveNotifications();

        /** Returns whether the notification is a media notification or not */
        boolean isMediaNotification(Notification notification);
    }

    /**
     * Factory for creating dependencies. Can be swapped out for testing.
     */
    @VisibleForTesting
    interface InputFactory {

        @Nullable
        MediaSessionManager getMediaSessionManager(Context appContext);

        @Nullable
        MediaSource getMediaSource(MediaController mediaController);

        @NonNull
        List<MediaSource> getMediaSources(List<MediaController> mediaControllers);
    }

    /**
     * Interface to provide the active media sessions and listeners.
     */
    public interface SessionProvider {
        /** Returns the active media sessions. */
        List<MediaController> getActiveSessions(MediaSessionManager manager);


        /** Registers a listener for active session changes. */
        void registerActiveSessionsListener(MediaSessionManager manager, Executor executor,
                MediaSessionManager.OnActiveSessionsChangedListener listener);

        /** Returns the shared preference file name to use for saving state. */
        default String getSharedPrefName() {
            return SHARED_PREF;
        }
    }

    private static InputFactory createInputFactory(@NonNull Context appContext,
            boolean ignoreBrowser) {
        return new InputFactory() {

            @Override
            @Nullable
            public MediaSessionManager getMediaSessionManager(Context appContext) {
                return appContext.getSystemService(MediaSessionManager.class);
            }

            @Override
            @Nullable
            public MediaSource getMediaSource(MediaController mediaController) {
                if (mediaController == null) {
                    return null;
                }
                MediaSessionCompat.Token token =
                        MediaSessionCompat.Token.fromToken(mediaController.getSessionToken());
                MediaControllerCompat newMediaController =
                        new MediaControllerCompat(appContext, token);

                return MediaSource.create(appContext, newMediaController, ignoreBrowser);
            }

            @Override
            @NonNull
            public List<MediaSource> getMediaSources(List<MediaController> mediaControllers) {
                if (mediaControllers == null) {
                    return Collections.emptyList();
                }
                List<MediaSource> mediaSources = new ArrayList<>();
                for (MediaController mediaController : mediaControllers) {
                    mediaSources.add(getMediaSource(mediaController));
                }

                return mediaSources;
            }
        };
    }

    @Deprecated
    private MediaSessionHelper(@NonNull Context appContext) {
        this(appContext, null, createInputFactory(appContext, /* ignoreBrowser= */ false));
    }

    /**
     *  Creates a new class instance.
     */
    public MediaSessionHelper(@NonNull Context context,
            @Nullable NotificationProvider notificationProvider) {
        this(context, notificationProvider,
                createInputFactory(context, /* ignoreBrowser= */ false));
    }

    @VisibleForTesting
    MediaSessionHelper(Context context, @Nullable NotificationProvider notificationProvider,
            InputFactory inputFactory) {
        this(context, notificationProvider, inputFactory, /* sessionProvider= */ null,
             /* ignoreBrowser= */ false);
    }


    /**
     * Creates a new class instance with a session provider.
     */
    public MediaSessionHelper(@NonNull Context context,
            @Nullable NotificationProvider notificationProvider,
            @Nullable SessionProvider sessionProvider) {
        this(context, notificationProvider, sessionProvider, /* ignoreBrowser= */ false);
    }

    /**
     * Creates a new class instance with a session provider and ignoreBrowser flag.
     */
    public MediaSessionHelper(@NonNull Context context,
            @Nullable NotificationProvider notificationProvider,
            @Nullable SessionProvider sessionProvider,
            boolean ignoreBrowser) {
        this(context, notificationProvider, createInputFactory(context, ignoreBrowser),
                sessionProvider, ignoreBrowser);
    }

    @VisibleForTesting
    MediaSessionHelper(Context context,
            @Nullable NotificationProvider notificationProvider,
            InputFactory inputFactory,
            @Nullable SessionProvider sessionProvider,
            boolean ignoreBrowser) {
        mContext = new WeakReference<>(context);
        mPackageManager = context.getPackageManager();
        mNotificationProvider = notificationProvider;
        mInputFactory = inputFactory;
        mSessionProvider = sessionProvider;
        mIgnoreBrowser = ignoreBrowser;
        // Get application shared preferences if available
        String sharedPrefFileName = SHARED_PREF;
        if (mSessionProvider != null) {
            sharedPrefFileName = mSessionProvider.getSharedPrefName();
        }
        mSharedPrefs = context.getApplicationContext()
                .getSharedPreferences(sharedPrefFileName, Context.MODE_PRIVATE);
        // Register our listener to be notified of changes in the active media sessions.
        mMediaSessionManager = mInputFactory.getMediaSessionManager(mContext.get());
        if (mMediaSessionManager == null) {
            Log.e(TAG, "MediaSessionManager is null");
            return;
        }

        registerActiveSessionsListener();
        // Set initial value
        setInitialMediaSource();
    }

    /**
     * Returns a filtered live data of the most important active media session {@link MediaSource}.
     */
    public LiveData<MediaSource> getMediaSource() {
        return mPrimaryMediaSource;
    }

    /**
     *  Returns a filtered live data of {@link MediaSource} with active or paused playback states.
     */
    public LiveData<List<MediaSource>> getActiveOrPausedMediaSources() {
        return mActiveOrPausedMediaSources;
    }

    /** Indicate that this class should no longer be used */
    public void onCleared() {
        mMediaSessionManager.removeOnActiveSessionsChangedListener(mActiveSessionsListener);
    }

    private void onMediaControllersChange(List<MediaController> controllers) {
        unregisterSessionCallbacks();

        List<MediaController> filteredControllers =
                getMediaControllersWithMediaNotifications(controllers);

        List<MediaController> activeControllers = new ArrayList<>();
        List<MediaController> activeOrPausedControllers = new ArrayList<>();
        parseMediaControllers(filteredControllers, activeControllers, activeOrPausedControllers);
        updatePrimaryMediaSource(activeControllers);
        updateActiveOrPausedMediaSources(activeOrPausedControllers);
    }

    private List<MediaController> getMediaControllersWithMediaNotifications(
            List<MediaController> controllers) {
        List<MediaController> mediaNotificationMediaControllers = new ArrayList<>();

        // TODO: cleanup once non-notificationprovider constructor is removed
        if (mNotificationProvider == null) {
            return controllers;
        }

        List<String> mediaNotificationPackageNames =
                getMediaNotificationPackages(mNotificationProvider.getActiveNotifications());

        for (MediaController mediaController : controllers) {
            MediaSource mediaSource = mInputFactory.getMediaSource(mediaController);
            // We only want media sources with MBS or have a media notification indicating they want
            // to allow background playback.
            if (mediaSource != null && (mediaSource.getBrowseServiceComponentName() != null
                    || mediaNotificationPackageNames.contains(mediaController.getPackageName()))) {
                mediaNotificationMediaControllers.add(mediaController);
            }
        }

        return mediaNotificationMediaControllers;
    }

    /**
     * Parses MediaControllers and returns the active ones. Registers playback state listeners for
     * the non active ones to be notified if they become active.
     *
     * @param controllers the list of MediaControllers to parse through
     * @param activeControllers the list to which active MediaControllers will be added to
     * @param activeOrPausedControllers the list to which active or paused MediaControllers will be
     * added to
     */
    private void parseMediaControllers(List<MediaController> controllers,
            List<MediaController> activeControllers,
            List<MediaController> activeOrPausedControllers) {
        if (controllers == null || controllers.isEmpty()) {
            return;
        }

        for (MediaController mediaController : controllers) {
            PlaybackState playbackState = mediaController.getPlaybackState();
            if (playbackState == null) {
                continue;
            }

            // Since playback state changes don't trigger an active media session change, we
            // need to listen to the other media sessions in case another one becomes active.
            // This includes the active media session, which may be stopped and then resumed later.
            registerForPlaybackChanges(mediaController);

            if (CarPackageManagerUtils.isPackageSuspended(
                    mPackageManager, mediaController.getPackageName())) {
                maybePauseSuspendedMediaController(mediaController);
                continue;
            }

            if (isActive(playbackState.getState())) {
                activeControllers.add(mediaController);
                activeOrPausedControllers.add(mediaController);
            } else if (isPaused(playbackState.getState())) {
                activeOrPausedControllers.add(mediaController);
            }
        }
    }

    private void registerForPlaybackChanges(MediaController controller) {
        if (mMediaControllersList.contains(controller)) {
            return;
        }

        controller.registerCallback(this);
        mMediaControllersList.add(controller);
    }

    private void unregisterSessionCallbacks() {
        for (MediaController mediaController : mMediaControllersList) {
            if (mediaController != null) {
                mediaController.unregisterCallback(this);
            }
        }
        mMediaControllersList.clear();
    }

    @Override
    public void onPlaybackStateChanged(@Nullable PlaybackState state) {
        if (state != null && isPausedOrActive(state.getState())) {
            onMediaControllersChange(getActiveSessions());
        }
    }

    private void updatePrimaryMediaSource(List<MediaController> activeMediaControllers) {
        MediaSource mediaSource = mPrimaryMediaSource.getValue();
        // Only update when there are active media sources
        if (activeMediaControllers != null && !activeMediaControllers.isEmpty()) {
            MediaController primaryMediaController = activeMediaControllers.get(0);
            mediaSource = mInputFactory.getMediaSource(primaryMediaController);
        }
        mediaSource = maybeReplacePrimaryMediaSource(mediaSource);
        if (Objects.equals(mediaSource, mPrimaryMediaSource.getValue())) {
            // Prevent updating with the same media source
            return;
        }
        saveLastActiveMediaSource(mediaSource);
        mPrimaryMediaSource.setValue(mediaSource);
    }

    private void updateActiveOrPausedMediaSources(List<MediaController> activeMediaControllers) {
        // Only update when there are active or paused media sources
        if (activeMediaControllers != null && !activeMediaControllers.isEmpty()) {
            List<MediaSource> mediaSources = mInputFactory.getMediaSources(activeMediaControllers);
            if (mediaSources.equals(mActiveOrPausedMediaSources.getValue())) {
                // Prevent updating with the same media sources
                return;
            }
            mActiveOrPausedMediaSources
                    .setValue(mInputFactory.getMediaSources(activeMediaControllers));
        }
    }

    private void setInitialMediaSource() {
        List<MediaController> activeMediaControllers = new ArrayList<>();
        List<MediaController> activeOrPausedMediaControllers = new ArrayList<>();
        List<MediaController> filteredControllers = getMediaControllersWithMediaNotifications(
                getActiveSessions());

        parseMediaControllers(filteredControllers,
                activeMediaControllers, activeOrPausedMediaControllers);

        // Setup initial activeMediaController
        if (activeMediaControllers.isEmpty()) {
            // Get the last saved media source
            String savedMediaSourceName = mSharedPrefs.getString(LAST_ACTIVE_MEDIA_SOURCE, "");
            if (!TextUtils.isEmpty(savedMediaSourceName)) {
                setSavedMediaSource(savedMediaSourceName, activeOrPausedMediaControllers);
            } else {
                getCarMediaServiceSessionAsync(activeOrPausedMediaControllers);
            }
        } else {
            updatePrimaryMediaSource(activeMediaControllers);
        }

        // Setup initial activeOrPausedMediaSources
        if (!activeOrPausedMediaControllers.isEmpty()) {
            updateActiveOrPausedMediaSources(activeOrPausedMediaControllers);
        }
    }

    private void setSavedMediaSource(String savedMediaSourceName,
            List<MediaController> activeOrPausedMediaControllers) {
        MediaSource savedMediaSource = createSavedMediaSource(savedMediaSourceName,
                activeOrPausedMediaControllers);
        savedMediaSource = maybeReplacePrimaryMediaSource(savedMediaSource);
        mPrimaryMediaSource.setValue(savedMediaSource);

        if (activeOrPausedMediaControllers.isEmpty()) {
            if (savedMediaSource != null) {
                mActiveOrPausedMediaSources.setValue(Collections.singletonList(savedMediaSource));
            }
        }
    }

    private void getCarMediaServiceSessionAsync(
            List<MediaController> activeOrPausedMediaControllers) {
        Context context = mContext.get();
        if (context == null) {
            return;
        }

        Car.createCar(context, null, Car.CAR_WAIT_TIMEOUT_DO_NOT_WAIT, (car, ready) -> {
            if (!ready) {
                return;
            }
            try {
                CarMediaManager carMediaManager =
                        (CarMediaManager) car.getCarManager(Car.CAR_MEDIA_SERVICE);
                if (carMediaManager != null) {
                    ComponentName componentName = carMediaManager.getMediaSource(
                            MEDIA_SOURCE_MODE_PLAYBACK);
                    if (componentName != null) {
                        setSavedMediaSource(componentName.flattenToString(),
                                activeOrPausedMediaControllers);
                    }
                }
            } catch (SecurityException e) {
                Log.e(TAG, "Unable to read CarMediaManager media source. Requires "
                        + "android.permission.MEDIA_CONTENT_CONTROL " + e);
            } finally {
                if (car != null) {
                    car.disconnect();
                }
            }
        });
    }

    private MediaSource createSavedMediaSource(String savedMediaSourceName,
            List<MediaController> activeOrPausedControllers) {
        String packageName = savedMediaSourceName;
        ComponentName componentName = ComponentName.unflattenFromString(savedMediaSourceName);
        if (componentName != null) {
            packageName = componentName.getPackageName();
        }

        if (!mIgnoreBrowser && componentName != null) {
            // If we are not ignoring the browser service and we have a component name, we should
            // use it to create the media source.
            // This is preferred over using an existing MediaController because we want to make sure
            // we are connecting to the browser service if possible.
            return MediaSource.create(mContext.get(), componentName);
        }

        MediaControllerCompat controller = findMatchingMediaController(packageName,
                activeOrPausedControllers);

        if (controller == null) {
            // If the media controller is not in the active or paused list, we should check the
            // active sessions.
            List<MediaController> activeSessions = getActiveSessions();
            controller = findMatchingMediaController(packageName, activeSessions);

        }

        if (controller != null) {
            // Initialize using any existing media controller
            // If we have a component name, pass it down so it can be used for icon/label lookup
            // even if ignoreBrowser is true.
            return MediaSource.create(mContext.get(), controller, componentName,
                    mIgnoreBrowser);
        }

        if (componentName != null) {
            // Initialize using MBS component name
            // This is reached if ignoreBrowser is true and no controller was found.
            return MediaSource.create(mContext.get(), componentName, mIgnoreBrowser);
        }

        // Initialize using package name
        return MediaSource.create(mContext.get(), packageName);
    }

    private MediaControllerCompat findMatchingMediaController(String packageName,
            List<MediaController> activeOrPausedControllers) {
        for (MediaController controller : activeOrPausedControllers) {
            if (TextUtils.equals(packageName, controller.getPackageName())) {
                MediaSessionCompat.Token token = MediaSessionCompat.Token.fromToken(
                        controller.getSessionToken());
                return new MediaControllerCompat(mContext.get(), token);
            }
        }
        return null;
    }

    private void saveLastActiveMediaSource(MediaSource mediaSource) {
        if (mediaSource == null) {
            Log.w(TAG, "Null media source detected, not saving as last active");
            return;
        }
        ComponentName componentName = mediaSource.getBrowseServiceComponentName();
        if (componentName != null) {
            Log.i(TAG, "Saving last active media source " + componentName);
            mSharedPrefs.edit()
                    .putString(LAST_ACTIVE_MEDIA_SOURCE, componentName.flattenToString())
                    .apply();
        } else {
            String packageName = mediaSource.getPackageName();
            Log.i(TAG, "Saving last active media source " + packageName);
            mSharedPrefs.edit().putString(LAST_ACTIVE_MEDIA_SOURCE, packageName).apply();
        }
    }



    /* Copy of PlaybackState.isActive() which is only available for minsdk >=S  */
    private boolean isActive(int playbackState) {
        switch (playbackState) {
            case PlaybackState.STATE_FAST_FORWARDING:
            case PlaybackState.STATE_REWINDING:
            case PlaybackState.STATE_SKIPPING_TO_PREVIOUS:
            case PlaybackState.STATE_SKIPPING_TO_NEXT:
            case PlaybackState.STATE_SKIPPING_TO_QUEUE_ITEM:
            case PlaybackState.STATE_BUFFERING:
            case PlaybackState.STATE_CONNECTING:
            case PlaybackState.STATE_PLAYING:
                return true;
        }
        return false;
    }

    /** Returns if a playback state is paused. */
    private boolean isPaused(int playbackState) {
        return playbackState == PlaybackState.STATE_PAUSED;
    }

    /** Returns whether a playback state is active or paused. */
    private boolean isPausedOrActive(int playbackState) {
        return isActive(playbackState) || isPaused(playbackState);
    }

    /**
     *  Parses through a list of notifications for those with media style and a media session.
     *  Returns a list of associated package names.
     */
    private List<String> getMediaNotificationPackages(StatusBarNotification[] activeNotifications) {
        List<String> mediaStyleNotificationPackages = new ArrayList<>();

        for (StatusBarNotification sbn : activeNotifications) {
            Notification notification = sbn.getNotification();

            if (notification.extras == null) {
                continue;
            }

            if (mNotificationProvider != null
                    && mNotificationProvider.isMediaNotification(notification)) {
                mediaStyleNotificationPackages.add(sbn.getPackageName());
            }
        }

        return mediaStyleNotificationPackages;
    }

    private MediaSource maybeReplacePrimaryMediaSource(MediaSource mediaSource) {
        // Define primary media source replacement conditions
        if (mediaSource == null) {
            return null;
        }
        if (CarPackageManagerUtils.isPackageSuspended(
                mPackageManager, mediaSource.getPackageName())) {
            return getSuspendedMediaSourceReplacement();
        }
        return mediaSource;
    }

    private MediaSource getSuspendedMediaSourceReplacement() {
        List<MediaController> filteredControllers = getMediaControllersWithMediaNotifications(
                mMediaSessionManager.getActiveSessions(/* notificationListener = */ null));
        MediaController mc =  filteredControllers.stream().filter(ctrl -> {
            PlaybackState ps = ctrl.getPlaybackState();
            if (ps == null) {
                return false;
            }
            return !CarPackageManagerUtils.isPackageSuspended(
                    mPackageManager, ctrl.getPackageName());
        }).findFirst().orElse(null);
        return mInputFactory.getMediaSource(mc);
    }

    private void maybePauseSuspendedMediaController(MediaController mediaController) {
        PlaybackState playbackState = mediaController.getPlaybackState();
        if (playbackState == null) {
            return;
        }
        if (playbackState.getState() != PlaybackState.STATE_PAUSED) {
            mediaController.getTransportControls().pause();
        }
    }

    /**
     * Gets the active sessions.
     * <p>
     * The default implementation only supports the current user (process user).
     * A custom {@link SessionProvider} can be provided to change this behavior.
     */
    private List<MediaController> getActiveSessions() {
        if (mSessionProvider != null) {
            return mSessionProvider.getActiveSessions(mMediaSessionManager);
        }
        return mMediaSessionManager.getActiveSessions(/* notificationListener= */ null);
    }

    /**
     * Registers a listener for active sessions changes.
     * <p>
     * A custom {@link SessionProvider} can be provided to change this behavior.
     */
    private void registerActiveSessionsListener() {
        if (mSessionProvider != null) {
            mSessionProvider.registerActiveSessionsListener(
                    mMediaSessionManager, mContext.get().getMainExecutor(),
                    mActiveSessionsListener);
        } else {
            mMediaSessionManager.addOnActiveSessionsChangedListener(mActiveSessionsListener,
                    /* notificationListener= */ null);
        }
    }
}
