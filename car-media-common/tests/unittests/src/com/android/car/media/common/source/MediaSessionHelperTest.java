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

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.app.Notification;
import android.content.Context;
import android.content.pm.PackageManager;
import android.media.session.MediaController;
import android.media.session.MediaController.TransportControls;
import android.media.session.MediaSession;
import android.media.session.MediaSessionManager;
import android.media.session.PlaybackState;
import android.os.Bundle;
import android.service.notification.StatusBarNotification;
import android.support.v4.media.session.MediaControllerCompat;

import androidx.annotation.NonNull;
import androidx.arch.core.executor.testing.InstantTaskExecutorRule;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.android.car.media.common.MediaTestUtils;
import com.android.car.testing.common.TestLifecycleOwner;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

@RunWith(AndroidJUnit4.class)
public class MediaSessionHelperTest {

    private static final String INITIAL_SOURCE_PACKAGE_NAME = "initial.source.package.name";
    private static final String ACTIVE_PACKAGE_NAME = "active.package.name";
    private static final String PAUSED_PACKAGE_NAME = "paused.package.name";
    private static final String STOPPED_PACKAGE_NAME = "stopped.package.name";

    @Rule
    public final MockitoRule mMockitoRule = MockitoJUnit.rule();
    @Rule
    public final InstantTaskExecutorRule mTaskExecutorRule = new InstantTaskExecutorRule();
    @Rule
    public final TestLifecycleOwner mLifecycleOwner = new TestLifecycleOwner();

    @Mock
    private MediaSessionManager mMediaSessionManager;
    @Mock
    private PackageManager mPackageManager;
    @Mock
    private StatusBarNotification mActiveStatusBarNotification;
    @Mock
    private StatusBarNotification mPausedStatusBarNotification;
    @Mock
    private StatusBarNotification mStoppedStatusBarNotification;
    @Mock
    private MediaController mInitialMediaController;
    @Mock
    private MediaController mActiveMediaController;
    @Mock
    private MediaController mPausedMediaController;
    @Mock
    private MediaController mStoppedMediaController;
    @Mock
    private MediaControllerCompat mInitialMediaControllerCompat;
    @Mock
    private MediaControllerCompat mActiveMediaControllerCompat;
    @Mock
    private MediaControllerCompat mPausedMediaControllerCompat;
    @Mock
    private MediaControllerCompat mStoppedMediaControllerCompat;
    @Mock
    private PlaybackState mActivePlaybackState;
    @Mock
    private PlaybackState mPausedPlaybackState;
    @Mock
    private PlaybackState mStoppedPlaybackState;

    private MediaSource mInitialMediaSource;
    private MediaSource mActiveMediaSource;
    private MediaSource mPausedMediaSource;
    private MediaSource mStoppedMediaSource;

    private Context mContext;
    private MediaSessionHelper mMediaSessionHelper;

    @Before
    public void setUp() throws PackageManager.NameNotFoundException {
        mContext = spy(ApplicationProvider.getApplicationContext());
        when(mContext.getPackageManager()).thenReturn(mPackageManager);

        Notification notification = new Notification();
        notification.extras = new Bundle();
        when(mActiveStatusBarNotification.getNotification()).thenReturn(notification);
        when(mActiveStatusBarNotification.getPackageName()).thenReturn(ACTIVE_PACKAGE_NAME);
        when(mPausedStatusBarNotification.getNotification()).thenReturn(notification);
        when(mPausedStatusBarNotification.getPackageName()).thenReturn(PAUSED_PACKAGE_NAME);
        when(mStoppedStatusBarNotification.getNotification()).thenReturn(notification);
        when(mStoppedStatusBarNotification.getPackageName()).thenReturn(STOPPED_PACKAGE_NAME);

        when(mInitialMediaController.getPackageName()).thenReturn(INITIAL_SOURCE_PACKAGE_NAME);
        when(mInitialMediaController.getPlaybackState()).thenReturn(mActivePlaybackState);
        when(mActiveMediaController.getPackageName()).thenReturn(ACTIVE_PACKAGE_NAME);
        when(mActiveMediaController.getPlaybackState()).thenReturn(mActivePlaybackState);
        when(mActivePlaybackState.getState()).thenReturn(PlaybackState.STATE_PLAYING);
        when(mPausedMediaController.getPackageName()).thenReturn(PAUSED_PACKAGE_NAME);
        when(mPausedMediaController.getPlaybackState()).thenReturn(mPausedPlaybackState);
        when(mPausedPlaybackState.getState()).thenReturn(PlaybackState.STATE_PAUSED);
        when(mStoppedMediaController.getPackageName()).thenReturn(STOPPED_PACKAGE_NAME);
        when(mStoppedMediaController.getPlaybackState()).thenReturn(mStoppedPlaybackState);
        when(mStoppedPlaybackState.getState()).thenReturn(PlaybackState.STATE_STOPPED);

        when(mMediaSessionManager.getActiveSessions(isNull()))
                .thenReturn(Collections.singletonList(mInitialMediaController));

        when(mPackageManager.isPackageSuspended(anyString())).thenReturn(false);

        mInitialMediaSource =
                MediaTestUtils.newFakeMediaSource(mPackageManager, mInitialMediaControllerCompat);
        mActiveMediaSource =
                MediaTestUtils.newFakeMediaSource(mPackageManager, mActiveMediaControllerCompat);
        mPausedMediaSource =
                MediaTestUtils.newFakeMediaSource(mPackageManager, mPausedMediaControllerCompat);
        mStoppedMediaSource =
                MediaTestUtils.newFakeMediaSource(mPackageManager, mStoppedMediaControllerCompat);
    }

    @Test
    public void noValidSources_emitsNothing() {
        initializeMediaSessionHelper(/* withValidSources= */ false);

        mMediaSessionHelper.mActiveSessionsListener
                .onActiveSessionsChanged(Collections.singletonList(mActiveMediaController));

        assertThat(mMediaSessionHelper.getMediaSource().getValue())
                .isEqualTo(null);
        assertThat(mMediaSessionHelper.getActiveOrPausedMediaSources().getValue()).hasSize(0);
    }

    @Test
    public void hasValidSources_emitsNewSources() {
        initializeMediaSessionHelper(/* withValidSources= */ true);

        mMediaSessionHelper.mActiveSessionsListener
                .onActiveSessionsChanged(Arrays.asList(mActiveMediaController,
                        mPausedMediaController, mStoppedMediaController));

        assertThat(mMediaSessionHelper.getMediaSource().getValue())
                .isEqualTo(mActiveMediaSource);
        assertThat(mMediaSessionHelper.getActiveOrPausedMediaSources().getValue()).hasSize(2);
        assertThat(mMediaSessionHelper.getActiveOrPausedMediaSources().getValue().get(0))
                .isEqualTo(mActiveMediaSource);
        assertThat(mMediaSessionHelper.getActiveOrPausedMediaSources().getValue().get(1))
                .isEqualTo(mPausedMediaSource);
    }

    @Test
    public void onActiveSessionsChanged_whenPrimarySourceIsSuspended_replacesMediaSource()
            throws PackageManager.NameNotFoundException {
        MediaSession mediaSession = new MediaSession(mContext, "tag");
        MediaController mediaController =
                new MediaController(mContext, mediaSession.getSessionToken());
        TransportControls transportControls = mediaController.getTransportControls();
        when(mMediaSessionManager.getActiveSessions(isNull()))
                .thenReturn(Arrays.asList(mActiveMediaController,
                        mPausedMediaController, mStoppedMediaController));
        when(mActiveMediaController.getTransportControls()).thenReturn(transportControls);
        initializeMediaSessionHelper(/* withValidSources= */ true);

        // Ensures that the activeMediaController is suspended
        when(mPackageManager.isPackageSuspended(ACTIVE_PACKAGE_NAME)).thenReturn(true);
        mMediaSessionHelper.mActiveSessionsListener
                .onActiveSessionsChanged(Arrays.asList(mActiveMediaController,
                        mPausedMediaController, mStoppedMediaController));

        assertThat(mMediaSessionHelper.getMediaSource().getValue())
                .isEqualTo(mPausedMediaSource);
        assertThat(mMediaSessionHelper.getActiveOrPausedMediaSources().getValue()).hasSize(1);
    }

    @Test
    public void hasValidSources_registersForPlaybackStateChanges() {
        initializeMediaSessionHelper(/* withValidSources= */ true);

        mMediaSessionHelper.mActiveSessionsListener
                .onActiveSessionsChanged(Arrays.asList(mActiveMediaController,
                        mPausedMediaController, mStoppedMediaController));

        verify(mActiveMediaController).registerCallback(any());
        verify(mPausedMediaController).registerCallback(any());
        verify(mStoppedMediaController).registerCallback(any());
    }

    private void initializeMediaSessionHelper(boolean withValidSources) {
        initializeMediaSessionHelper(withValidSources, null, /* ignoreBrowser= */ false);
    }

    private void initializeMediaSessionHelper(boolean withValidSources,
            MediaSessionHelper.SessionProvider sessionProvider) {
        initializeMediaSessionHelper(withValidSources, sessionProvider, /* ignoreBrowser= */ false);
    }

    private void initializeMediaSessionHelper(boolean withValidSources,
            MediaSessionHelper.SessionProvider sessionProvider, boolean ignoreBrowser) {
        MediaSessionHelper.InputFactory inputFactory = new MediaSessionHelper.InputFactory() {
            @Override
            public MediaSessionManager getMediaSessionManager(Context appContext) {
                return mMediaSessionManager;
            }

            @Override
            public MediaSource getMediaSource(MediaController mediaController) {
                switch (mediaController.getPackageName()) {
                    case INITIAL_SOURCE_PACKAGE_NAME:
                        return mInitialMediaSource;
                    case ACTIVE_PACKAGE_NAME:
                        return mActiveMediaSource;
                    case PAUSED_PACKAGE_NAME:
                        return mPausedMediaSource;
                    case STOPPED_PACKAGE_NAME:
                    default:
                        return mStoppedMediaSource;
                }
            }

            @NonNull
            @Override
            public List<MediaSource> getMediaSources(List<MediaController> mediaControllers) {
                List<MediaSource> mediaSources = new ArrayList<>();
                for (MediaController mediaController : mediaControllers) {
                    mediaSources.add(getMediaSource(mediaController));
                }
                return mediaSources;
            }
        };

        MediaSessionHelper.NotificationProvider notificationProvider =
                new MediaSessionHelper.NotificationProvider() {
                    @Override
                    public StatusBarNotification[] getActiveNotifications() {
                        if (withValidSources) {
                            return new StatusBarNotification[]{ mActiveStatusBarNotification,
                                    mPausedStatusBarNotification, mStoppedStatusBarNotification };
                        }
                        return new StatusBarNotification[0];
                    }

                    @Override
                    public boolean isMediaNotification(Notification notification) {
                        return withValidSources;
                    }
                };

        mMediaSessionHelper = new MediaSessionHelper(mContext, notificationProvider, inputFactory,
                sessionProvider, ignoreBrowser);
    }

    @Test
    public void usesSessionProvider_whenProvided() {
        MediaSessionHelper.SessionProvider mockProvider =
                org.mockito.Mockito.mock(MediaSessionHelper.SessionProvider.class);

        initializeMediaSessionHelper(false, mockProvider);

        // Verify getActiveSessions delegates to provider
        // Triggers init -> invokeActiveSessionsListener -> getActiveSessions
        verify(mockProvider).getActiveSessions(mMediaSessionManager);

        // Verify registerActiveSessionsListener delegates to provider
        verify(mockProvider).registerActiveSessionsListener(eq(mMediaSessionManager), any(), any());
    }
}
