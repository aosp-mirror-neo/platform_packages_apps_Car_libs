/*
 * Copyright (C) 2025 The Android Open Source Project
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
package com.android.aaos.studio;

import android.app.Activity;
import android.content.om.FabricatedOverlay;
import android.content.om.OverlayIdentifier;
import android.content.om.OverlayManager;
import android.content.om.OverlayManagerTransaction;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.util.Pair;
import android.util.TypedValue;
import android.view.View;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.Toast;

import com.android.car.oem.tokens.Token;
import com.android.car.ui.core.CarUi;
import com.android.car.ui.recyclerview.CarUiRecyclerView;
import com.android.car.ui.toolbar.MenuItem;
import com.android.car.ui.toolbar.NavButtonMode;
import com.android.car.ui.toolbar.Tab;
import com.android.car.ui.toolbar.ToolbarController;

import com.google.ux.material.libmonet.blend.Blend;
import com.google.ux.material.libmonet.hct.Hct;
import com.google.ux.material.libmonet.palettes.TonalPalette;
import com.google.ux.material.libmonet.scheme.SchemeVibrant;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Activity that shows displays the values of OEM design tokens.
 */
public class TokenActivity extends Activity {
    private static final String TAG = "TokenActivity";
    private static final String OWNING_PACKAGE = "com.android.aaos.studio";
    private static final String TARGET_PACKAGE = "oem.demo.sharedlib";
    private static final String OVERLAY_NAME = "AaosStudioFrro";
    private static final String ANDROID_OVERLAY_NAME = "AaosStudioFrameworkResFrro";

    private OverlayManager mOverlayManager;
    private View mColorPreview;
    private SchemeVibrant mScheme;
    private boolean mIsLightMode;
    private boolean mSquareCorners;
    private SeekBar mSeekBar;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        Token.applyOemTokenStyle(this);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.token_activity);

        mOverlayManager = getSystemService(android.content.om.OverlayManager.class);

        CarUiRecyclerView list = requireViewById(R.id.list);
        TokenDemoAdapter adapter = new TokenDemoAdapter(createColorList());
        list.setAdapter(adapter);

        ToolbarController toolbar = CarUi.requireToolbar(this);
        toolbar.setTitle(getTitle());
        toolbar.setNavButtonMode(NavButtonMode.BACK);

        List<Tab> tabs = new ArrayList<>();
        tabs.add(Tab.builder()
                .setText("Color")
                .setIcon(getDrawable(R.drawable.car_ui_icon_edit))
                .setSelectedListener(
                        tab -> list.setAdapter(new TokenDemoAdapter(createColorList())))
                .build());
        tabs.add(Tab.builder()
                .setText("Text")
                .setIcon(getDrawable(R.drawable.car_ui_icon_edit))
                .setSelectedListener(
                        tab -> list.setAdapter(new TokenDemoAdapter(createTextList())))
                .build());
        tabs.add(Tab.builder()
                .setText("Shape")
                .setIcon(getDrawable(R.drawable.car_ui_icon_edit))
                .setSelectedListener(
                        tab -> list.setAdapter(new TokenDemoAdapter(createCornerRadiusList())))
                .build());
        toolbar.setTabs(tabs, 0);

        String tokenLibPackageName = Token.getTokenSharedLibPackageName(getPackageManager());
        if (tokenLibPackageName == null) {
            Toast.makeText(this, "OEM design token shared library not found",
                    Toast.LENGTH_LONG).show();
            return;
        }

        Switch lightSwitch = requireViewById(R.id.light_switch);
        Switch cornerSwitch = requireViewById(R.id.corner_switch);
        mColorPreview = requireViewById(R.id.color_preview);
        mSeekBar = requireViewById(R.id.color_seekbar);

        mIsLightMode = isLightMode(Token.getColor(this, R.attr.oemColorSurface),
                Token.getColor(this, R.attr.oemColorOnSurface));
        lightSwitch.setChecked(mIsLightMode);
        lightSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> mIsLightMode = isChecked);

        mSquareCorners = Token.getCornerRadius(this, R.attr.oemShapeCornerFull) == 0;
        cornerSwitch.setChecked(mSquareCorners);
        cornerSwitch.setOnCheckedChangeListener(
                (buttonView, isChecked) -> mSquareCorners = isChecked);

        List<MenuItem> menuItems = new ArrayList<>();
        menuItems.add(MenuItem.builder(this)
                .setTitle("Enable RRO")
                .setOnClickListener(i -> {
                    int seedColor = getSeedColor();
                    Hct seed = Hct.fromInt(seedColor);
                    mScheme = new SchemeVibrant(seed, !mIsLightMode, 0.0);
                    updateOverlay();
                    updateFrameworkOverlay();
                })
                .build());
        menuItems.add(MenuItem.builder(this)
                .setTitle("Disable RRO")
                .setOnClickListener(i -> {
                    disableOverlay();
                    disableFrameworkOverlay();
                })
                .build());
        toolbar.setMenuItems(menuItems);

        mSeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                updateColorPreview();
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {

            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {

            }
        });
    }

    private boolean isLightMode(int background, int foreground) {
        float[] hsvA = new float[3];
        float[] hsvB = new float[3];

        Color.colorToHSV(background, hsvA);
        Color.colorToHSV(foreground, hsvB);

        return hsvA[2] > hsvB[2];
    }

    private int getSeedColor() {
        float hue = (float) mSeekBar.getProgress();

        // Set saturation and value to max 1.0f
        float[] hsv = {hue, 1.0f, 1.0f};
        return Color.HSVToColor(hsv);
    }

    private void updateColorPreview() {
        int color = getSeedColor();
        ((GradientDrawable) mColorPreview.getBackground()).setColor(color);
    }

    private void disableOverlay() {
        OverlayManagerTransaction.Builder transaction =
                new OverlayManagerTransaction.Builder()
                        .unregisterFabricatedOverlay(
                                new OverlayIdentifier(OWNING_PACKAGE, OVERLAY_NAME));
        mOverlayManager.commit(transaction.build());
    }

    private void disableFrameworkOverlay() {
        OverlayManagerTransaction.Builder transaction =
                new OverlayManagerTransaction.Builder()
                        .unregisterFabricatedOverlay(
                                new OverlayIdentifier(OWNING_PACKAGE, ANDROID_OVERLAY_NAME));
        mOverlayManager.commit(transaction.build());
    }

    private void updateOverlay() {
        disableOverlay();

        FabricatedOverlay.Builder builder = new FabricatedOverlay.Builder(
                OWNING_PACKAGE, OVERLAY_NAME, TARGET_PACKAGE)
                .setResourceValue("com.android.oem.tokens:string/theme_overlay",
                        TypedValue.TYPE_STRING, "oem.brand.model.android.rro:style/OemThemeOverlay",
                        null)
                .setResourceValue("com.android.oem.tokens:bool/enable_oem_tokens",
                        TypedValue.TYPE_INT_BOOLEAN, 1, null)
                // Set color resources
                .setResourceValue("com.android.oem.tokens:color/color_primary",
                        TypedValue.TYPE_INT_COLOR_ARGB8, mScheme.getPrimary(), null)
                .setResourceValue("com.android.oem.tokens:color/color_on_primary",
                        TypedValue.TYPE_INT_COLOR_ARGB8, mScheme.getOnPrimary(), null)
                .setResourceValue("com.android.oem.tokens:color/color_primary_container",
                        TypedValue.TYPE_INT_COLOR_ARGB8, mScheme.getPrimaryContainer(), null)
                .setResourceValue("com.android.oem.tokens:color/color_on_primary_container",
                        TypedValue.TYPE_INT_COLOR_ARGB8, mScheme.getOnPrimaryContainer(), null)
                .setResourceValue("com.android.oem.tokens:color/color_secondary",
                        TypedValue.TYPE_INT_COLOR_ARGB8, mScheme.getSecondary(), null)
                .setResourceValue("com.android.oem.tokens:color/color_on_secondary",
                        TypedValue.TYPE_INT_COLOR_ARGB8, mScheme.getOnSecondary(), null)
                .setResourceValue("com.android.oem.tokens:color/color_secondary_container",
                        TypedValue.TYPE_INT_COLOR_ARGB8, mScheme.getSecondaryContainer(), null)
                .setResourceValue("com.android.oem.tokens:color/color_on_secondary_container",
                        TypedValue.TYPE_INT_COLOR_ARGB8, mScheme.getOnSecondaryContainer(), null)
                .setResourceValue("com.android.oem.tokens:color/color_tertiary",
                        TypedValue.TYPE_INT_COLOR_ARGB8, mScheme.getTertiary(), null)
                .setResourceValue("com.android.oem.tokens:color/color_on_tertiary",
                        TypedValue.TYPE_INT_COLOR_ARGB8, mScheme.getOnTertiary(), null)
                .setResourceValue("com.android.oem.tokens:color/color_tertiary_container",
                        TypedValue.TYPE_INT_COLOR_ARGB8, mScheme.getTertiaryContainer(), null)
                .setResourceValue("com.android.oem.tokens:color/color_on_tertiary_container",
                        TypedValue.TYPE_INT_COLOR_ARGB8, mScheme.getOnTertiaryContainer(), null)
                .setResourceValue("com.android.oem.tokens:color/color_error",
                        TypedValue.TYPE_INT_COLOR_ARGB8, mScheme.getError(), null)
                .setResourceValue("com.android.oem.tokens:color/color_on_error",
                        TypedValue.TYPE_INT_COLOR_ARGB8, mScheme.getOnError(), null)
                .setResourceValue("com.android.oem.tokens:color/color_error_container",
                        TypedValue.TYPE_INT_COLOR_ARGB8, mScheme.getErrorContainer(), null)
                .setResourceValue("com.android.oem.tokens:color/color_on_error_container",
                        TypedValue.TYPE_INT_COLOR_ARGB8, mScheme.getOnErrorContainer(), null)

                .setResourceValue("com.android.oem.tokens:color/color_surface",
                        TypedValue.TYPE_INT_COLOR_ARGB8, mScheme.getSurface(), null)
                .setResourceValue("com.android.oem.tokens:color/color_on_surface",
                        TypedValue.TYPE_INT_COLOR_ARGB8, mScheme.getOnSurface(), null)
                .setResourceValue("com.android.oem.tokens:color/color_surface_variant",
                        TypedValue.TYPE_INT_COLOR_ARGB8, mScheme.getSurfaceVariant(), null)
                .setResourceValue("com.android.oem.tokens:color/color_on_surface_variant",
                        TypedValue.TYPE_INT_COLOR_ARGB8, mScheme.getOnSurfaceVariant(), null)
                .setResourceValue("com.android.oem.tokens:color/color_surface_inverse",
                        TypedValue.TYPE_INT_COLOR_ARGB8, mScheme.getInverseSurface(), null)
                .setResourceValue("com.android.oem.tokens:color/color_on_surface_inverse",
                        TypedValue.TYPE_INT_COLOR_ARGB8, mScheme.getInverseOnSurface(), null)
                .setResourceValue("com.android.oem.tokens:color/color_outline",
                        TypedValue.TYPE_INT_COLOR_ARGB8, mScheme.getOutline(), null)
                .setResourceValue("com.android.oem.tokens:color/color_outline_variant",
                        TypedValue.TYPE_INT_COLOR_ARGB8, mScheme.getOutlineVariant(), null)
                .setResourceValue("com.android.oem.tokens:color/color_scrim",
                        TypedValue.TYPE_INT_COLOR_ARGB8, mScheme.getScrim(), null)
                .setResourceValue("com.android.oem.tokens:color/color_surface_dim",
                        TypedValue.TYPE_INT_COLOR_ARGB8, mScheme.getSurfaceDim(), null)
                .setResourceValue("com.android.oem.tokens:color/color_surface_bright",
                        TypedValue.TYPE_INT_COLOR_ARGB8, mScheme.getSurfaceBright(), null)
                .setResourceValue("com.android.oem.tokens:color/color_surface_container",
                        TypedValue.TYPE_INT_COLOR_ARGB8, mScheme.getSurfaceContainer(), null)
                .setResourceValue("com.android.oem.tokens:color/color_surface_container_low",
                        TypedValue.TYPE_INT_COLOR_ARGB8, mScheme.getSurfaceContainerLow(), null)
                .setResourceValue("com.android.oem.tokens:color/color_surface_container_lowest",
                        TypedValue.TYPE_INT_COLOR_ARGB8, mScheme.getSurfaceContainerLowest(), null)
                .setResourceValue("com.android.oem.tokens:color/color_surface_container_high",
                        TypedValue.TYPE_INT_COLOR_ARGB8, mScheme.getSurfaceContainerHigh(), null)
                .setResourceValue("com.android.oem.tokens:color/color_surface_container_highest",
                        TypedValue.TYPE_INT_COLOR_ARGB8, mScheme.getSurfaceContainerHighest(), null)
                .setResourceValue("com.android.oem.tokens:color/color_blue",
                        TypedValue.TYPE_INT_COLOR_ARGB8,
                        Blend.harmonize(getColor(R.color.oem_color_blue_dark),
                                mScheme.getPrimary()), null)
                .setResourceValue("com.android.oem.tokens:color/color_on_blue",
                        TypedValue.TYPE_INT_COLOR_ARGB8,
                        Blend.harmonize(getColor(R.color.oem_color_on_blue_dark),
                                mScheme.getPrimary()), null)
                .setResourceValue("com.android.oem.tokens:color/color_blue_container",
                        TypedValue.TYPE_INT_COLOR_ARGB8,
                        Blend.harmonize(getColor(R.color.oem_color_blue_container_dark),
                                mScheme.getPrimary()), null)
                .setResourceValue("com.android.oem.tokens:color/color_on_blue_container",
                        TypedValue.TYPE_INT_COLOR_ARGB8,
                        Blend.harmonize(getColor(R.color.oem_color_on_blue_container_dark),
                                mScheme.getPrimary()), null)
                .setResourceValue("com.android.oem.tokens:color/color_green",
                        TypedValue.TYPE_INT_COLOR_ARGB8,
                        Blend.harmonize(getColor(R.color.oem_color_green_dark),
                                mScheme.getPrimary()), null)
                .setResourceValue("com.android.oem.tokens:color/color_on_green",
                        TypedValue.TYPE_INT_COLOR_ARGB8,
                        Blend.harmonize(getColor(R.color.oem_color_on_green_dark),
                                mScheme.getPrimary()), null)
                .setResourceValue("com.android.oem.tokens:color/color_green_container",
                        TypedValue.TYPE_INT_COLOR_ARGB8,
                        Blend.harmonize(getColor(R.color.oem_color_green_container_dark),
                                mScheme.getPrimary()), null)
                .setResourceValue("com.android.oem.tokens:color/color_on_green_container",
                        TypedValue.TYPE_INT_COLOR_ARGB8,
                        Blend.harmonize(getColor(R.color.oem_color_on_green_container_dark),
                                mScheme.getPrimary()), null)
                .setResourceValue("com.android.oem.tokens:color/color_yellow",
                        TypedValue.TYPE_INT_COLOR_ARGB8,
                        Blend.harmonize(getColor(R.color.oem_color_yellow_dark),
                                mScheme.getPrimary()), null)
                .setResourceValue("com.android.oem.tokens:color/color_on_yellow",
                        TypedValue.TYPE_INT_COLOR_ARGB8,
                        Blend.harmonize(getColor(R.color.oem_color_on_yellow_dark),
                                mScheme.getPrimary()), null)
                .setResourceValue("com.android.oem.tokens:color/color_yellow_container",
                        TypedValue.TYPE_INT_COLOR_ARGB8,
                        Blend.harmonize(getColor(R.color.oem_color_yellow_container_dark),
                                mScheme.getPrimary()), null)
                .setResourceValue("com.android.oem.tokens:color/color_on_yellow_container",
                        TypedValue.TYPE_INT_COLOR_ARGB8,
                        Blend.harmonize(getColor(R.color.oem_color_on_yellow_container_dark),
                                mScheme.getPrimary()), null);

        // Palette tones
        int[] tones = {100, 99, 95, 90, 80, 70, 60, 50, 40, 30, 20, 10, 0};
        String[] palettes = {
                "primary", "secondary", "tertiary", "error", "neutral", "neutral_variant"};

        for (String palette : palettes) {
            TonalPalette tonalPalette;
            switch (palette) {
                case "primary": tonalPalette = mScheme.primaryPalette; break;
                case "secondary": tonalPalette = mScheme.secondaryPalette; break;
                case "tertiary": tonalPalette = mScheme.tertiaryPalette; break;
                case "error": tonalPalette = mScheme.errorPalette; break;
                case "neutral": tonalPalette = mScheme.neutralPalette; break;
                case "neutral_variant": tonalPalette = mScheme.neutralVariantPalette; break;
                default: continue;
            }

            for (int tone : tones) {
                String resName = "color_" + palette + "_palette_" + tone;
                builder.setResourceValue("com.android.oem.tokens:color/" + resName,
                        TypedValue.TYPE_INT_COLOR_ARGB8, tonalPalette.tone(tone), null);
            }
        }

        FabricatedOverlay overlay = builder.build();

        if (mSquareCorners) {
            overlay.setResourceValue("com.android.oem.tokens:dimen/corner_none",
                    0f, TypedValue.COMPLEX_UNIT_DIP, null);
            overlay.setResourceValue("com.android.oem.tokens:dimen/corner_extra_small",
                    0f, TypedValue.COMPLEX_UNIT_DIP, null);
            overlay.setResourceValue("com.android.oem.tokens:dimen/corner_small",
                    0f, TypedValue.COMPLEX_UNIT_DIP, null);
            overlay.setResourceValue("com.android.oem.tokens:dimen/corner_medium",
                    0f, TypedValue.COMPLEX_UNIT_DIP, null);
            overlay.setResourceValue("com.android.oem.tokens:dimen/corner_large",
                    0f, TypedValue.COMPLEX_UNIT_DIP, null);
            overlay.setResourceValue("com.android.oem.tokens:dimen/corner_extra_large",
                    0f, TypedValue.COMPLEX_UNIT_DIP, null);
            overlay.setResourceValue("com.android.oem.tokens:dimen/corner_full",
                    0f, TypedValue.COMPLEX_UNIT_DIP, null);
        }

        OverlayManagerTransaction.Builder transaction =
                new OverlayManagerTransaction.Builder()
                        .registerFabricatedOverlay(overlay)
                        .setEnabled(overlay.getIdentifier(), true)
                        .setEnabled(overlay.getIdentifier(), true, 0);

        mOverlayManager.commit(transaction.build());
    }

    private void updateFrameworkOverlay() {
        FabricatedOverlay overlay = new FabricatedOverlay.Builder(OWNING_PACKAGE,
                ANDROID_OVERLAY_NAME, "android")
                .build();

        int corner = 1000;
        int isRoundCorner = 1;
        if (mSquareCorners) {
            corner = 0;
            isRoundCorner = 0;
        }

        overlay.setResourceValue("android:string/config_icon_mask",
                TypedValue.TYPE_STRING, createRoundedRectPath(corner),
                null);
        overlay.setResourceValue("android:bool/config_useRoundIcon",
                TypedValue.TYPE_INT_BOOLEAN, isRoundCorner, null);

        Map<String, Integer> semanticColors = new HashMap<>();
        semanticColors.put("primary", mScheme.getPrimary());
        semanticColors.put("on_primary", mScheme.getOnPrimary());
        semanticColors.put("primary_container", mScheme.getPrimaryContainer());
        semanticColors.put("on_primary_container", mScheme.getOnPrimaryContainer());
        semanticColors.put("secondary", mScheme.getSecondary());
        semanticColors.put("on_secondary", mScheme.getOnSecondary());
        semanticColors.put("secondary_container", mScheme.getSecondaryContainer());
        semanticColors.put("on_secondary_container", mScheme.getOnSecondaryContainer());
        semanticColors.put("tertiary", mScheme.getTertiary());
        semanticColors.put("on_tertiary", mScheme.getOnTertiary());
        semanticColors.put("tertiary_container", mScheme.getTertiaryContainer());
        semanticColors.put("on_tertiary_container", mScheme.getOnTertiaryContainer());
        semanticColors.put("error", mScheme.getError());
        semanticColors.put("on_error", mScheme.getOnError());
        semanticColors.put("error_container", mScheme.getErrorContainer());
        semanticColors.put("on_error_container", mScheme.getOnErrorContainer());
        semanticColors.put("surface", mScheme.getSurface());
        semanticColors.put("on_surface", mScheme.getOnSurface());
        semanticColors.put("surface_variant", mScheme.getSurfaceVariant());
        semanticColors.put("on_surface_variant", mScheme.getOnSurfaceVariant());
        semanticColors.put("inverse_surface", mScheme.getInverseSurface());
        semanticColors.put("inverse_on_surface", mScheme.getInverseOnSurface());
        semanticColors.put("outline", mScheme.getOutline());
        semanticColors.put("outline_variant", mScheme.getOutlineVariant());
        semanticColors.put("scrim", mScheme.getScrim());
        semanticColors.put("surface_dim", mScheme.getSurfaceDim());
        semanticColors.put("surface_bright", mScheme.getSurfaceBright());
        semanticColors.put("surface_container", mScheme.getSurfaceContainer());
        semanticColors.put("surface_container_low", mScheme.getSurfaceContainerLow());
        semanticColors.put("surface_container_lowest", mScheme.getSurfaceContainerLowest());
        semanticColors.put("surface_container_high", mScheme.getSurfaceContainerHigh());
        semanticColors.put("surface_container_highest", mScheme.getSurfaceContainerHighest());

        for (Map.Entry<String, Integer> entry : semanticColors.entrySet()) {
            String name = entry.getKey();
            int color = entry.getValue();
            overlay.setResourceValue("android:color/system_" + name + "_dark",
                    TypedValue.TYPE_INT_COLOR_ARGB8, color, null);
            overlay.setResourceValue("android:color/system_" + name + "_light",
                    TypedValue.TYPE_INT_COLOR_ARGB8, color, null);
        }

        // Palette tones
        int[] tones = {0, 10, 50, 100, 200, 300, 400, 500, 600, 700, 800, 900, 1000};
        String[] palettes = {"accent1", "accent2", "accent3", "neutral1", "neutral2", "error"};

        for (String palette : palettes) {
            TonalPalette tonalPalette;
            switch (palette) {
                case "accent1": tonalPalette = mScheme.primaryPalette; break;
                case "accent2": tonalPalette = mScheme.secondaryPalette; break;
                case "accent3": tonalPalette = mScheme.tertiaryPalette; break;
                case "neutral1": tonalPalette = mScheme.neutralPalette; break;
                case "neutral2": tonalPalette = mScheme.neutralVariantPalette; break;
                case "error": tonalPalette = mScheme.errorPalette; break;
                default: continue;
            }

            for (int tone : tones) {
                int toneValue = 100 - tone / 10;
                overlay.setResourceValue("android:color/system_" + palette + "_" + tone,
                        TypedValue.TYPE_INT_COLOR_ARGB8, tonalPalette.tone(toneValue), null);
            }
        }

        OverlayManagerTransaction.Builder transaction =
                new OverlayManagerTransaction.Builder()
                        .registerFabricatedOverlay(overlay)
                        .setEnabled(overlay.getIdentifier(), true)
                        .setEnabled(overlay.getIdentifier(), true, 0);

        mOverlayManager.commit(transaction.build());
    }

    private List<Pair<String, Integer>> createColorList() {
        List<Pair<String, Integer>> list = new ArrayList<>();

        list.add(new Pair<>("colorPrimary", R.attr.oemColorPrimary));
        list.add(new Pair<>("colorOnPrimary", R.attr.oemColorOnPrimary));
        list.add(new Pair<>("colorPrimaryContainer",
                R.attr.oemColorPrimaryContainer));
        list.add(new Pair<>("colorPrimaryOnContainer",
                R.attr.oemColorOnPrimaryContainer));

        list.add(new Pair<>("colorSecondary", R.attr.oemColorSecondary));
        list.add(new Pair<>("colorOnSecondary", R.attr.oemColorOnSecondary));
        list.add(new Pair<>("colorSecondaryContainer",
                R.attr.oemColorSecondaryContainer));
        list.add(new Pair<>("colorSecondaryOnContainer",
                R.attr.oemColorOnSecondaryContainer));

        list.add(new Pair<>("colorTertiary", R.attr.oemColorTertiary));
        list.add(new Pair<>("colorOnTertiary", R.attr.oemColorOnTertiary));
        list.add(new Pair<>("colorTertiaryContainer",
                R.attr.oemColorTertiaryContainer));
        list.add(new Pair<>("colorTertiaryOnContainer",
                R.attr.oemColorOnTertiaryContainer));

        list.add(new Pair<>("colorError", R.attr.oemColorError));
        list.add(new Pair<>("colorOnError", R.attr.oemColorOnError));
        list.add(new Pair<>("colorErrorContainer", R.attr.oemColorErrorContainer));
        list.add(new Pair<>("colorErrorOnContainer",
                R.attr.oemColorOnErrorContainer));

        list.add(new Pair<>("colorSurface", R.attr.oemColorSurface));
        list.add(new Pair<>("colorOnSurface", R.attr.oemColorOnSurface));
        list.add(new Pair<>("colorSurfaceVariant", R.attr.oemColorSurfaceVariant));
        list.add(new Pair<>("colorOnSurfaceVariant",
                R.attr.oemColorOnSurfaceVariant));

        list.add(new Pair<>("colorOutline", R.attr.oemColorOutline));

        list.add(new Pair<>("colorSurfaceDim", R.attr.oemColorSurfaceDim));
        list.add(new Pair<>("colorSurfaceBright", R.attr.oemColorSurfaceBright));
        list.add(new Pair<>("colorSurfaceContainer",
                R.attr.oemColorSurfaceContainer));
        list.add(new Pair<>("colorSurfaceContainerLow", R.attr.oemColorSurfaceContainerLow));
        list.add(new Pair<>("colorSurfaceContainerLowest", R.attr.oemColorSurfaceContainerLowest));
        list.add(new Pair<>("colorSurfaceContainerHigh",
                R.attr.oemColorSurfaceContainerHigh));
        list.add(new Pair<>("colorSurfaceContainerHighest",
                R.attr.oemColorSurfaceContainerHighest));
        list.add(new Pair<>("colorShadow",
                R.attr.oemColorShadow));

        list.add(new Pair<>("colorBlue", R.attr.oemColorBlue));
        list.add(new Pair<>("colorOnBlue",
                R.attr.oemColorOnBlue));
        list.add(new Pair<>("colorBlueContainer", R.attr.oemColorBlueContainer));
        list.add(new Pair<>("colorOnBlueContainer",
                R.attr.oemColorOnBlueContainer));

        list.add(new Pair<>("colorGreen", R.attr.oemColorGreen));
        list.add(new Pair<>("colorOnGreen",
                R.attr.oemColorOnGreenContainer));
        list.add(new Pair<>("colorGreenContainer", R.attr.oemColorGreenContainer));
        list.add(new Pair<>("colorOnGreenContainer",
                R.attr.oemColorOnGreenContainer));

        list.add(new Pair<>("colorYellow", R.attr.oemColorYellow));
        list.add(new Pair<>("colorOnYellow",
                R.attr.oemColorOnYellow));
        list.add(new Pair<>("colorYellowContainer", R.attr.oemColorYellowContainer));
        list.add(new Pair<>("colorOnYellowContainer",
                R.attr.oemColorOnYellowContainer));

        return list;
    }

    private List<Pair<String, Integer>> createTextList() {
        List<Pair<String, Integer>> list = new ArrayList<>();
        list.add(new Pair<>("textAppearanceDisplayLarge",
                R.attr.oemTextAppearanceDisplayLarge));
        list.add(new Pair<>("textAppearanceDisplayMedium",
                R.attr.oemTextAppearanceDisplayMedium));
        list.add(new Pair<>("textAppearanceDisplaySmall",
                R.attr.oemTextAppearanceDisplaySmall));

        list.add(new Pair<>("textAppearanceHeadlineLarge",
                R.attr.oemTextAppearanceHeadlineLarge));
        list.add(new Pair<>("textAppearanceHeadlineMedium",
                R.attr.oemTextAppearanceHeadlineMedium));
        list.add(new Pair<>("textAppearanceHeadlineSmall",
                R.attr.oemTextAppearanceHeadlineSmall));

        list.add(new Pair<>("textAppearanceTitleLarge",
                R.attr.oemTextAppearanceTitleLarge));
        list.add(new Pair<>("textAppearanceTitleMedium",
                R.attr.oemTextAppearanceTitleMedium));
        list.add(new Pair<>("textAppearanceTitleSmall",
                R.attr.oemTextAppearanceTitleSmall));

        list.add(new Pair<>("textAppearanceLabelLarge",
                R.attr.oemTextAppearanceLabelLarge));
        list.add(new Pair<>("textAppearanceLabelMedium",
                R.attr.oemTextAppearanceLabelMedium));
        list.add(new Pair<>("textAppearanceLabelSmall",
                R.attr.oemTextAppearanceLabelSmall));

        list.add(new Pair<>("textAppearanceBodyLarge",
                R.attr.oemTextAppearanceBodyLarge));
        list.add(new Pair<>("textAppearanceBodyMedium",
                R.attr.oemTextAppearanceBodyMedium));
        list.add(new Pair<>("textAppearanceBodySmall",
                R.attr.oemTextAppearanceBodySmall));

        return list;
    }

    private List<Pair<String, Integer>> createCornerRadiusList() {
        List<Pair<String, Integer>> list = new ArrayList<>();
        list.add(new Pair<>("shapeCornerNone",
                R.attr.oemShapeCornerNone));
        list.add(new Pair<>("shapeCornerExtraSmall",
                R.attr.oemShapeCornerExtraSmall));
        list.add(new Pair<>("shapeCornerSmall",
                R.attr.oemShapeCornerSmall));
        list.add(new Pair<>("shapeCornerMedium",
                R.attr.oemShapeCornerMedium));
        list.add(new Pair<>("shapeCornerLarge",
                R.attr.oemShapeCornerLarge));
        list.add(new Pair<>("shapeCornerExtraLarge",
                R.attr.oemShapeCornerExtraLarge));
        list.add(new Pair<>("shapeCornerFull",
                R.attr.oemShapeCornerFull));

        return list;
    }

    private static String createRoundedRectPath(float corner) {
        // Clamp the radius: must be >= 0 and <= half the shortest side (100 / 2 = 50)
        float r = Math.max(0f, Math.min(corner, 50f));

        // Handle the non-rounded case
        if (r <= 0.001f) {
            return "M 0 0 H 100 V 100 H 0 Z"; // Simple rectangle
        }
        return String.format(
                Locale.US,
                "M %f 0 L %f 0 A %f %f 0 0 1 100 %f L 100 %f A %f %f 0 0 1 %f 100 L %f 100 A %f "
                        + "%f 0 0 1 0 %f L 0 %f A %f %f 0 0 1 %f 0 Z",
                r,       // M x=r, y=0               (Start middle of top edge)
                100f - r,       // L x=100-r, y=0           (Line to top-right corner start)
                r, r, r,        // A rx=r ry=r rot=0 lrg=0 swp=1 x=100, y=r (Top-right arc)
                100f - r,       // L x=100, y=100-r         (Line to bottom-right corner start)
                r, r, 100f - r, // A rx=r ry=r rot=0 lrg=0 swp=1 x=100-r, y=100(Bottom-right arc)
                r,              // L x=r, y=100             (Line to bottom-left corner start)
                r, r, 100f - r, // A rx=r ry=r rot=0 lrg=0 swp=1 x=0, y=100-r  (Bottom-left arc)
                r,              // L x=0, y=r               (Line to top-left corner start)
                r, r, r    // A rx=r ry=r rot=0 lrg=0 swp=1 x=r, y=0    (Top-left arc back to start)
        );
    }
}
