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

import android.content.Context;
import android.content.res.Configuration;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.ImageView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.android.car.ui.R;
import com.android.car.ui.appstyledview.AppStyledDialogController.NavIcon;

/**
 * Controller to interact with the app styled view.
 */
public class AppStyledViewControllerImpl implements AppStyledViewController {
    private static final String TAG = "AppStyledViewController";
    private final Context mContext;
    @NavIcon
    private int mAppStyleViewNavIcon;
    @Nullable
    private Runnable mAppStyledVCloseClickListener;
    @Nullable
    private View mAppStyledView;
    @Nullable
    private View mContent;
    @NonNull
    private final AppStyledDialog mDialog;

    public AppStyledViewControllerImpl(@NonNull Context context) {
        mContext = context;
        mDialog = new AppStyledDialog(context);
    }

    @NonNull
    public AppStyledDialog getDialog() {
        return mDialog;
    }

    @Override
    public void setNavIcon(@NavIcon int navIcon) {
        mAppStyleViewNavIcon = navIcon;
        updateNavIcon();
    }

    /**
     * Sets the AppStyledVCloseClickListener on the close icon.
     */
    @Override
    public void setOnNavIconClickListener(@Nullable Runnable listener) {
        mAppStyledVCloseClickListener = listener;
        updateNavIconClickListener();
    }

    @Override
    public int getContentAreaWidth() {
        WindowManager.LayoutParams params =
                mDialog.getDialogWindowLayoutParam(new WindowManager.LayoutParams());

        int width = params.width;
        int orientation = mContext.getResources().getConfiguration().orientation;
        if (orientation == Configuration.ORIENTATION_LANDSCAPE) {
            return width - mContext.getResources().getDimensionPixelSize(
                    R.dimen.car_ui_toolbar_first_row_height);
        }

        return width;
    }

    @Override
    public int getContentAreaHeight() {
        WindowManager.LayoutParams params =
                mDialog.getDialogWindowLayoutParam(new WindowManager.LayoutParams());

        int height = params.height;
        int orientation = mContext.getResources().getConfiguration().orientation;
        if (orientation == Configuration.ORIENTATION_LANDSCAPE) {
            return height;
        }

        return height - mContext.getResources().getDimensionPixelSize(
                R.dimen.car_ui_toolbar_first_row_height);
    }

    @Override
    public void setSceneType(int sceneType) {
        mDialog.setSceneType(sceneType);
    }

    private void updateContent() {
        if (mContent == null) {
            return;
        }

        if (Log.isLoggable(TAG, Log.DEBUG)) {
            Log.d(TAG, "Cannot update content with null. No-op.");
        }

        setContent(mContent);
        mDialog.setContentView(mAppStyledView);
    }

    @Override
    public void show() {
        updateContent();
        mDialog.show();
    }

    @Override
    public void dismiss() {
        mDialog.dismiss();
    }

    @Override
    public void setOnDismissListener(Runnable runnable) {
        if (runnable == null) {
            mDialog.setOnDismissListener(null);
            return;
        }

        mDialog.setOnDismissListener(dialog -> runnable.run());
    }

    @Override
    public WindowManager.LayoutParams getAttributes() {
        return mDialog.getDialogWindowLayoutParam(new WindowManager.LayoutParams());
    }

    /**
     * Applies OEM scrim to app content.
     */
    @Nullable
    public View createAppStyledView(@Nullable View contentView) {
        if (mContent == null) {
            return null;
        }

        if (mContent.getParent() != null) {
            ((ViewGroup) mContent.getParent()).removeView(mContent);
        }

        LayoutInflater inflater = LayoutInflater.from(mContext);
        mAppStyledView = inflater.inflate(R.layout.car_ui_app_styled_view, null, false);
        mAppStyledView.setClipToOutline(true);
        ViewGroup contentHolder = mAppStyledView.findViewById(R.id.car_ui_app_styled_content);
        contentHolder.addView(contentView);

        updateNavIcon();
        updateNavIconClickListener();

        return mAppStyledView;
    }

    @Override
    public void setContent(@Nullable View contentView) {
        mContent = contentView;
        View unused = createAppStyledView(contentView);
    }

    private void updateNavIcon() {
        if (mAppStyledView == null) {
            return;
        }

        ImageView close = mAppStyledView.findViewById(R.id.car_ui_app_styled_view_icon_close);
        if (mAppStyleViewNavIcon == NavIcon.BACK) {
            close.setImageResource(R.drawable.car_ui_icon_arrow_back);
        } else if (mAppStyleViewNavIcon == NavIcon.CLOSE) {
            close.setImageResource(R.drawable.car_ui_icon_close);
        } else {
            close.setImageResource(R.drawable.car_ui_icon_close);
        }
    }

    private void updateNavIconClickListener() {
        if (mAppStyledView == null) {
            return;
        }

        FrameLayout navContainer = mAppStyledView.findViewById(
                R.id.car_ui_app_styled_view_nav_icon_container);
        if (navContainer != null) {
            navContainer.setOnClickListener((v) -> {
                if (mAppStyledVCloseClickListener == null) {
                    return;
                }
                mAppStyledVCloseClickListener.run();
            });
        }
    }
}
