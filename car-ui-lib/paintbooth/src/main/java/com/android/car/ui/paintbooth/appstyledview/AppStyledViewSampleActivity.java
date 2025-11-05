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
package com.android.car.ui.paintbooth.appstyledview;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;

import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;

import com.android.car.ui.appstyledview.AppStyledDialogController;
import com.android.car.ui.appstyledview.AppStyledDialogController.NavIcon;
import com.android.car.ui.core.CarUi;
import com.android.car.ui.paintbooth.R;
import com.android.car.ui.toolbar.NavButtonMode;
import com.android.car.ui.toolbar.ToolbarController;

/**
 * Sample activity to show app styled Dialog fragment.
 */
public class AppStyledViewSampleActivity extends Activity {
    private AppStyledDialogController mAppStyledDialogController;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.app_styled_view_sample_activity);

        ToolbarController toolbar = CarUi.requireToolbar(this);
        toolbar.setTitle(getTitle());
        toolbar.setNavButtonMode(NavButtonMode.BACK);
        toolbar.setLogo(R.drawable.ic_launcher);

        mAppStyledDialogController = new AppStyledDialogController(this);
        View appStyledView = LayoutInflater.from(
                        mAppStyledDialogController.createContentViewConfigurationContext(this,
                                R.style.AppStyledDialogThemeSample))
                .inflate(R.layout.app_styled_view_test_sample, null, false);

        mAppStyledDialogController.setOnNavIconClickListener(
                () -> mAppStyledDialogController.dismiss());

        Button btn = requireViewById(R.id.show_app_styled_fragment);
        Button btnWithBars = requireViewById(R.id.show_app_styled_fragment_with_system_bars);
        Button btnNewActivity = requireViewById(R.id.show_app_styled_fragment_new_activity);

        mAppStyledDialogController.setContentView(appStyledView);
        mAppStyledDialogController.setNavIconType(NavIcon.CLOSE);

        btn.setOnClickListener(v -> {
            mAppStyledDialogController.setOnDismissListener(this::showSystemBars);
            hideSystemBars();
            mAppStyledDialogController.show();
        });

        btnWithBars.setOnClickListener(v -> {
            mAppStyledDialogController.setOnDismissListener(null);
            mAppStyledDialogController.show();
        });

        btnNewActivity.setOnClickListener(v -> {
            Intent intent = new Intent(v.getContext(), ActivitySample.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
        });
    }

    private void hideSystemBars() {
        WindowInsetsControllerCompat windowInsetsController =
                WindowCompat.getInsetsController(getWindow(), getWindow().getDecorView());

        // Configure the behavior of the hidden system bars
        windowInsetsController.setSystemBarsBehavior(
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        );

        // Hide the system bars
        windowInsetsController.hide(WindowInsetsCompat.Type.systemBars());
    }

    private void showSystemBars() {
        WindowInsetsControllerCompat windowInsetsController =
                WindowCompat.getInsetsController(getWindow(), getWindow().getDecorView());

        // Show the system bars
        windowInsetsController.show(WindowInsetsCompat.Type.systemBars());
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (mAppStyledDialogController != null) {
            mAppStyledDialogController.dismiss();
            showSystemBars();
        }
    }

    public static class ActivitySample extends Activity {
        @Override
        protected void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            setContentView(R.layout.app_styled_view_test_sample);
        }
    }}
