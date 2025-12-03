/*
 * Copyright (C) 2021 The Android Open Source Project
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

package com.android.car.ui.appstyledview;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.Rect;
import android.graphics.drawable.ColorDrawable;
import android.os.Build;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowInsets;
import android.view.WindowManager;
import android.view.WindowMetrics;
import android.widget.FrameLayout;

import androidx.activity.OnBackPressedDispatcher;
import androidx.activity.OnBackPressedDispatcherOwner;
import androidx.activity.ViewTreeOnBackPressedDispatcherOwner;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.graphics.Insets;
import androidx.core.view.OnApplyWindowInsetsListener;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;
import androidx.lifecycle.Lifecycle;
import androidx.lifecycle.LifecycleOwner;
import androidx.lifecycle.LifecycleRegistry;
import androidx.lifecycle.ViewTreeLifecycleOwner;
import androidx.savedstate.SavedStateRegistry;
import androidx.savedstate.SavedStateRegistryController;
import androidx.savedstate.SavedStateRegistryOwner;
import androidx.savedstate.ViewTreeSavedStateRegistryOwner;

import com.android.car.ui.R;
import com.android.car.ui.utils.CarUiUtils;

/**
 * App styled dialog used to display a view that cannot be customized via OEM. Dialog will inflate a
 * layout and add the view provided by the application into the layout. Everything other than the
 * view within the layout can be customized by OEM.
 * <p>
 * Apps should not use this directly. Apps should use {@link AppStyledDialogController}.
 */

public class AppStyledDialog extends Dialog implements LifecycleOwner, SavedStateRegistryOwner,
        OnBackPressedDispatcherOwner {

    private static final double VISIBLE_SCREEN_PERCENTAGE = 0.9;
    private static final int DIALOG_START_MARGIN_THRESHOLD = 120;
    private static final int DIALOG_MIN_PADDING = 32;
    private static final int IME_OVERLAP_DP = 32;
    private final Context mContext;
    private final LifecycleRegistry mLifecycleRegistry;
    private final SavedStateRegistryController mSavedStateRegistryController;
    private final OnBackPressedDispatcher mOnBackPressedDispatcher;
    @AppStyledDialogController.SceneType
    private int mSceneType;
    // Track content padding
    private int mOriginalContentPaddingBottom = -1;
    private final FrameLayout mContentHolder;

    public AppStyledDialog(@NonNull Context context) {
        super(context, R.style.AppStyledDialogStyle);
        mLifecycleRegistry = new LifecycleRegistry(this);
        mSavedStateRegistryController = SavedStateRegistryController.create(this);
        mOnBackPressedDispatcher = new OnBackPressedDispatcher(super::onBackPressed);
        // super.getContext() returns a ContextThemeWrapper which is not an Activity which we
        // need in order to get call getWindow()
        mContext = context;
        requestWindowFeature(Window.FEATURE_NO_TITLE);

        mContentHolder = new FrameLayout(mContext);
        super.setContentView(mContentHolder);

        Window window = getWindow();
        if (window == null) {
            return;
        }

        updateAttributes();
    }

    @NonNull
    @Override
    public Bundle onSaveInstanceState() {
        Bundle bundle = super.onSaveInstanceState();
        mSavedStateRegistryController.performSave(bundle);
        return bundle;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mSavedStateRegistryController.performRestore(savedInstanceState);
        Window window = getWindow();
        if (window == null) {
            return;
        }

        window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
        window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        window.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);

        WindowManager.LayoutParams params = window.getAttributes();
        params.dimAmount = CarUiUtils.getFloat(mContext.getResources(),
                R.dimen.car_ui_app_styled_dialog_dim_amount);
        switch (mSceneType) {
            case AppStyledDialogController.SceneType.ENTER:
                params.windowAnimations = R.style.Widget_CarUi_AppStyledView_WindowAnimations_Enter;
                break;
            case AppStyledDialogController.SceneType.EXIT:
                params.windowAnimations = R.style.Widget_CarUi_AppStyledView_WindowAnimations_Exit;
                break;
            case AppStyledDialogController.SceneType.INTERMEDIATE:
                params.windowAnimations =
                        R.style.Widget_CarUi_AppStyledView_WindowAnimations_Intermediate;
                break;
            case AppStyledDialogController.SceneType.SINGLE:
            default:
                params.windowAnimations = R.style.Widget_CarUi_AppStyledView_WindowAnimations;
                break;
        }
        window.setAttributes(params);

        copySystemUiVisibility();
        configureImeInsetFit();
        updateAttributes();

        mLifecycleRegistry.setCurrentState(Lifecycle.State.CREATED);
    }

    private void updateAttributes() {
        Window window = getWindow();
        if (window == null) {
            return;
        }

        WindowManager.LayoutParams windowParams = new WindowManager.LayoutParams();
        getDialogWindowLayoutParam(windowParams);

        FrameLayout.LayoutParams frameParams = new FrameLayout.LayoutParams(
                windowParams.width,
                windowParams.height
        );
        frameParams.gravity = windowParams.gravity;
        frameParams.leftMargin = windowParams.x;
        frameParams.topMargin = windowParams.y;

        mContentHolder.setLayoutParams(frameParams);
        if (mOriginalContentPaddingBottom != -1) {
            mContentHolder.getChildAt(0).setPadding(
                    mContentHolder.getPaddingLeft(),
                    mContentHolder.getPaddingTop(),
                    mContentHolder.getPaddingRight(),
                    mOriginalContentPaddingBottom);

        }

        WindowManager.LayoutParams params = window.getAttributes();
        switch (mSceneType) {
            case AppStyledDialogController.SceneType.ENTER:
                params.windowAnimations = R.style.Widget_CarUi_AppStyledView_WindowAnimations_Enter;
                break;
            case AppStyledDialogController.SceneType.EXIT:
                params.windowAnimations = R.style.Widget_CarUi_AppStyledView_WindowAnimations_Exit;
                break;
            case AppStyledDialogController.SceneType.INTERMEDIATE:
                params.windowAnimations =
                        R.style.Widget_CarUi_AppStyledView_WindowAnimations_Intermediate;
                break;
            case AppStyledDialogController.SceneType.SINGLE:
            default:
                params.windowAnimations = R.style.Widget_CarUi_AppStyledView_WindowAnimations;
                break;
        }
        window.setAttributes(params);
    }

    @SuppressLint("NewApi")
    private float getVerticalInset() {
        int insetType =
                WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.displayCutout();

        // Inset API not supported before Android R. Fallback to approximation
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
            Context unwrappedContext = CarUiUtils.unwrapContext(mContext);
            WindowInsets windowInsets =
                    unwrappedContext.getSystemService(
                            WindowManager.class).getCurrentWindowMetrics().getWindowInsets();
            android.graphics.Insets insets = windowInsets.getInsets(insetType);

            return insets.top + insets.bottom;
        }

        float fallbackInset =
                (float) (getWindowBounds().height() * (1 - VISIBLE_SCREEN_PERCENTAGE));
        Activity activity = CarUiUtils.getActivity(mContext);
        if (activity == null) {
            return fallbackInset;
        }

        WindowInsets windowInsets =
                activity.getWindow().getDecorView().getRootView().getRootWindowInsets();
        if (windowInsets == null) {
            return fallbackInset;
        }

        Insets insets = WindowInsetsCompat.toWindowInsetsCompat(windowInsets).getInsets(insetType);
        return insets.top + insets.bottom;
    }

    @SuppressLint("NewApi")
    private float getHorizontalInset() {
        int insetType =
                WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.displayCutout();

        // Inset API not supported before Android R. Fallback to approximation
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
            Context unwrappedContext = CarUiUtils.unwrapContext(mContext);
            WindowInsets windowInsets = unwrappedContext.getSystemService(
                    WindowManager.class).getCurrentWindowMetrics().getWindowInsets();
            android.graphics.Insets insets = windowInsets.getInsets(insetType);

            return insets.left + insets.right;
        }

        float fallbackInset =
                (float) (getWindowBounds().width() * (1 - VISIBLE_SCREEN_PERCENTAGE));
        Activity activity = CarUiUtils.getActivity(mContext);
        if (activity == null) {
            return fallbackInset;
        }

        WindowInsets windowInsets =
                activity.getWindow().getDecorView().getRootView().getRootWindowInsets();
        if (windowInsets == null) {
            return fallbackInset;
        }

        Insets insets = WindowInsetsCompat.toWindowInsetsCompat(windowInsets).getInsets(insetType);
        return insets.left + insets.right;
    }

    private Rect getWindowBounds() {
        WindowManager windowManager =
                (WindowManager) mContext.getSystemService(Context.WINDOW_SERVICE);
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            Rect bounds = new Rect();
            windowManager.getDefaultDisplay().getRectSize(bounds);
            return bounds;
        }

        WindowMetrics windowMetrics = windowManager.getCurrentWindowMetrics();
        return windowMetrics.getBounds();
    }

    /**
     * Returns the layout params for the AppStyledView dialog
     */
    public WindowManager.LayoutParams getDialogWindowLayoutParam(
            WindowManager.LayoutParams params) {
        Rect windowBounds = getWindowBounds();
        int windowWidth = windowBounds.width();
        int windowHeight = windowBounds.height();
        int horizontalInset = (int) getHorizontalInset();
        int verticalInset = (int) getVerticalInset();

        int maxWidth = mContext.getResources().getDimensionPixelSize(
                R.dimen.car_ui_app_styled_dialog_width_max);
        int maxHeight = mContext.getResources().getDimensionPixelSize(
                R.dimen.car_ui_app_styled_dialog_height_max);
        int configuredWidth = mContext.getResources().getDimensionPixelSize(
                R.dimen.car_ui_app_styled_dialog_width);
        int configuredHeight = mContext.getResources().getDimensionPixelSize(
                R.dimen.car_ui_app_styled_dialog_height);

        params.width = configuredWidth != 0 ? configuredWidth : Math.min(windowWidth, maxWidth);
        params.height = configuredHeight != 0 ? configuredHeight
                : Math.min(windowHeight, maxHeight);

        if (configuredWidth > windowWidth) {
            params.width = windowWidth;
        }

        if (configuredHeight > windowHeight) {
            params.height = windowHeight;
        }


        int posX = mContext.getResources().getDimensionPixelSize(
                R.dimen.car_ui_app_styled_dialog_position_x);
        int posY = mContext.getResources().getDimensionPixelSize(
                R.dimen.car_ui_app_styled_dialog_position_y);

        if (posX + params.width > windowWidth || posY + params.height > windowHeight) {
            posX = 0;
            posY = 0;
        }

        int minPaddingPx = (int) CarUiUtils.dpToPixel(mContext.getResources(),
                DIALOG_MIN_PADDING);

        if (params.width + horizontalInset >= windowWidth - (minPaddingPx * 2)) {
            params.width = windowWidth - horizontalInset - (minPaddingPx * 2);
        }

        if (params.height + verticalInset >= windowHeight - (minPaddingPx * 2)) {
            params.height = windowHeight - verticalInset - (minPaddingPx * 2);
        }

        params.gravity = Gravity.TOP | Gravity.START;
        if (posX != 0 || posY != 0) {
            params.x = posX;
            params.y = posY;
            return params;
        } else {
            params.x = ((windowWidth - horizontalInset) - params.width) / 2;
            params.y = ((windowHeight - verticalInset) - params.height) / 2;
        }

        int startMarginThresholdPx = (int) CarUiUtils.dpToPixel(mContext.getResources(),
                DIALOG_START_MARGIN_THRESHOLD);
        boolean isLandscape = mContext.getResources().getConfiguration().orientation
                == Configuration.ORIENTATION_LANDSCAPE;
        int startMargin = (windowWidth - horizontalInset - params.width) / 2;

        if (isLandscape && startMargin > startMarginThresholdPx) {
            params.x = startMarginThresholdPx;
        }

        return params;
    }


    private void configureImeInsetFit() {
        Window window = getWindow();
        if (window == null) {
            return;
        }

        // Required inset API is unsupported. Fallback to resize behavior.
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_PAN);
            ViewCompat.setOnApplyWindowInsetsListener(window.getDecorView().getRootView(),
                    new OnApplyWindowInsetsListener() {
                        @NonNull
                        @Override
                        public WindowInsetsCompat onApplyWindowInsets(
                                @NonNull View v, @NonNull WindowInsetsCompat insets) {
                            updateAttributes();
                            return insets;
                        }
                    });
            return;
        } else {
            window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING);
        }

        // Handle final state for IME resizing as animation callbacks are best effort and are not
        // guaranteed to be called.
        ViewCompat.setOnApplyWindowInsetsListener(window.getDecorView().getRootView(),
                (v, insets) -> {

                    Insets imeInsets = insets.getInsets(WindowInsetsCompat.Type.ime());
                    int imeHeight = imeInsets.bottom;

                    if (imeHeight <= 0) {
                        updateAttributes();
                        return insets;
                    }

                    // Fix for Android R/S system bar inclusion
                    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S_V2) {
                        imeHeight -= getSystemBarBottomHeight();
                    }

                    int currentHeight = mContentHolder.getMeasuredHeight();
                    int targetResize = calculateImeResize(currentHeight, imeHeight);

                    // Apply logic with progress as 1.0 (100% complete)
                    applyImeResize(currentHeight, targetResize, 1.0f);

                    return new WindowInsetsCompat.Builder(insets)
                            .setInsets(WindowInsetsCompat.Type.ime(), Insets.NONE)
                            .build();
                });
    }

    private int calculateImeResize(int startHeight, int imeHeight) {
        if (imeHeight <= 0) {
            return 0;
        }

        int imeOverlapPx = (int) CarUiUtils.dpToPixel(mContext.getResources(), IME_OVERLAP_DP);

        int[] contentLocation = new int[2];
        mContentHolder.getLocationOnScreen(contentLocation);

        int[] windowLocation = new int[2];
        mContentHolder.getRootView().getLocationOnScreen(windowLocation);

        // Makes assumption that ime is shown on bottom of screen
        int dialogBottom = contentLocation[1] + startHeight;
        int imeTop = windowLocation[1] + getWindowBounds().height() - imeHeight;

        if (imeTop < dialogBottom) {
            return Math.max(0, dialogBottom - imeTop - imeOverlapPx);
        }
        return 0;
    }

    private void applyImeResize(int startHeight, int targetResize, float progress) {
        int currentResize = (int) (targetResize * progress);
        ViewGroup.LayoutParams params = mContentHolder.getLayoutParams();
        params.height = startHeight - currentResize;
        mContentHolder.setLayoutParams(params);

        mOriginalContentPaddingBottom = mContentHolder.getChildAt(0).getPaddingBottom();

        // If resizing, add padding to the content to push elements up above overlap
        if (mOriginalContentPaddingBottom != -1) {
            int imeOverlapPx = (int) CarUiUtils.dpToPixel(mContext.getResources(), IME_OVERLAP_DP);
            int extraPadding = targetResize > 0 ? (int) (imeOverlapPx * progress) : 0;

            mContentHolder.getChildAt(0).setPadding(
                    mContentHolder.getPaddingLeft(),
                    mContentHolder.getPaddingTop(),
                    mContentHolder.getPaddingRight(),
                    mOriginalContentPaddingBottom + extraPadding
            );
        }
    }

    private int getSystemBarBottomHeight() {
        Activity activity = CarUiUtils.getActivity(mContext);
        if (activity != null) {
            WindowInsetsCompat activityInsets = ViewCompat.getRootWindowInsets(
                    activity.getWindow().getDecorView().getRootView());
            if (activityInsets != null) {
                return activityInsets.getInsets(WindowInsetsCompat.Type.systemBars()).bottom;
            }
        }
        return 0;
    }

    @Override
    protected void onStart() {
        mLifecycleRegistry.setCurrentState(Lifecycle.State.STARTED);
        mLifecycleRegistry.setCurrentState(Lifecycle.State.RESUMED);

        super.onStart();
    }

    @Override
    protected void onStop() {
        mLifecycleRegistry.setCurrentState(Lifecycle.State.DESTROYED);
        super.onStop();
    }

    @Override
    public void onAttachedToWindow() {
        super.onAttachedToWindow();
        copyWindowInsets();
        getWindow().getDecorView().getRootView().post(() -> updateAttributes());
    }

    /**
     * Copy the visibility of the Activity that has started the dialog {@code mContext}. If the
     * activity is in Immersive mode the dialog will be in Immersive mode too and vice versa.
     */
    private void copySystemUiVisibility() {
        if (getWindow() == null) {
            return;
        }

        Activity activity = CarUiUtils.getActivity(mContext);

        getWindow().getDecorView().setSystemUiVisibility(
                activity.getWindow().getDecorView().getSystemUiVisibility());
        getWindow().getDecorView().setOnSystemUiVisibilityChangeListener(
                i -> getWindow().getDecorView().setSystemUiVisibility(
                        activity.getWindow().getDecorView().getSystemUiVisibility()));
    }

    /**
     * Copy window inset settings from the activity that requested the dialog. Status bar insets
     * mirror activity state but nav bar requires the following workaround.
     */
    private void copyWindowInsets() {
        Window window = getWindow();
        if (window == null) {
            return;
        }

        // WindowInsetsController corresponding to the dialog
        WindowInsetsControllerCompat dialogWindowInsetsController =
                WindowCompat.getInsetsController(window, getWindow().getDecorView());

        Activity activity = CarUiUtils.getActivity(mContext);

        // WindowInsetsController corresponding to activity that requested the dialog
        WindowInsetsControllerCompat activityWindowInsetsController =
                WindowCompat.getInsetsController(activity.getWindow(),
                        activity.getWindow().getDecorView());


        int activitySystemBarBehavior = activityWindowInsetsController.getSystemBarsBehavior();
        // Only set system bar behavior when non-default settings are required. Setting default may
        // overwrite flags set by deprecated methods with different defaults.
        if (activitySystemBarBehavior != 0) {
            // Configure the behavior of the hidden system bars to match requesting activity
            dialogWindowInsetsController.setSystemBarsBehavior(activitySystemBarBehavior);
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            // Configure nav bar visibility to match requesting activity
            WindowInsets windowInsets = activity.getWindow().getDecorView().getRootWindowInsets();
            if (windowInsets == null) {
                return;
            }

            boolean isStatusBarVisible = windowInsets.isVisible(WindowInsets.Type.statusBars());
            if (!isStatusBarVisible) {
                dialogWindowInsetsController.hide(WindowInsetsCompat.Type.statusBars());
            }

            boolean isNavBarVisible = windowInsets.isVisible(WindowInsets.Type.navigationBars());
            if (!isNavBarVisible) {
                dialogWindowInsetsController.hide(WindowInsetsCompat.Type.navigationBars());
            }
        }
    }

    @Override
    public void show() {
        if (isShowing()) {
            return;
        }

        // Don't show when no content view set.
        if (mContentHolder.getChildCount() == 0) {
            return;
        }

        super.show();
        View focusedView = getCurrentFocus();
        if (focusedView != null) {
            focusedView.clearFocus();
        }
    }

    @Override
    public void setContentView(@NonNull View view) {
        initViewTreeOwners();
        mContentHolder.removeAllViews();
        mContentHolder.addView(view);

        mOriginalContentPaddingBottom = view.getPaddingBottom();
    }

    @Override
    public void setContentView(@NonNull View view, @Nullable ViewGroup.LayoutParams params) {
        initViewTreeOwners();
        mContentHolder.removeAllViews();
        mContentHolder.addView(view);

        mOriginalContentPaddingBottom = view.getPaddingBottom();
    }

    @Override
    public void addContentView(@NonNull View view, @Nullable ViewGroup.LayoutParams params) {
        initViewTreeOwners();
        mContentHolder.removeAllViews();
        mContentHolder.addView(view);

        mOriginalContentPaddingBottom = view.getPaddingBottom();
    }

    public void setSceneType(int sceneType) {
        mSceneType = sceneType;
        updateAttributes();
    }

    @Nullable
    public WindowManager.LayoutParams getWindowLayoutParams() {
        if (getWindow() == null) {
            return null;
        }

        return getWindow().getAttributes();
    }

    @NonNull
    @Override
    public Lifecycle getLifecycle() {
        return mLifecycleRegistry;
    }

    private void initViewTreeOwners() {
        Window window = getWindow();
        if (window == null) {
            return;
        }

        ViewTreeLifecycleOwner.set(window.getDecorView(), this);
        ViewTreeSavedStateRegistryOwner.set(window.getDecorView(), this);
        ViewTreeOnBackPressedDispatcherOwner.set(window.getDecorView(), this);
    }

    @NonNull
    @Override
    public SavedStateRegistry getSavedStateRegistry() {
        return mSavedStateRegistryController.getSavedStateRegistry();
    }

    @NonNull
    @Override
    public OnBackPressedDispatcher getOnBackPressedDispatcher() {
        return mOnBackPressedDispatcher;
    }
}
