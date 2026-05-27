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
import android.util.TypedValue;
import android.view.View;
import android.widget.EditText;
import android.widget.RadioButton;
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
import com.google.ux.material.libmonet.dynamiccolor.DynamicScheme;
import com.google.ux.material.libmonet.hct.Hct;
import com.google.ux.material.libmonet.palettes.TonalPalette;
import com.google.ux.material.libmonet.scheme.SchemeContent;
import com.google.ux.material.libmonet.scheme.SchemeExpressive;
import com.google.ux.material.libmonet.scheme.SchemeNeutral;
import com.google.ux.material.libmonet.scheme.SchemeTonalSpot;
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
    private DynamicScheme mScheme;
    private boolean mIsLightMode;
    private boolean mSquareCorners;
    private SeekBar mSeekBar;
    private EditText mHexInput;
    private String mSchemeType = "Vibrant";
    private float mSaturation = 1.0f;
    private float mValue = 1.0f;

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
        mHexInput = requireViewById(R.id.hex_input);
        mHexInput.setOnEditorActionListener((v, actionId, event) -> {
            String text = v.getText().toString();
            if (text.matches("^#?([0-9a-fA-F]{6})$")) {
                int color = Color.parseColor(text.startsWith("#") ? text : "#" + text);
                float[] hsv = new float[3];
                Color.colorToHSV(color, hsv);
                mSeekBar.setProgress((int) hsv[0]);
                mSaturation = hsv[1];
                mValue = hsv[2];
                updateColorPreview();
            } else {
                Toast.makeText(this, "Invalid hex color", Toast.LENGTH_SHORT).show();
                mSeekBar.setProgress(0);
                mSaturation = 1.0f;
                mValue = 1.0f;
                updateColorPreview();
            }
            return false;
        });

        mIsLightMode = isLightMode(Token.getColor(this, R.attr.oemColorSurface),
                Token.getColor(this, R.attr.oemColorOnSurface));
        lightSwitch.setChecked(!mIsLightMode);
        lightSwitch.setOnCheckedChangeListener(
                (buttonView, isChecked) -> mIsLightMode = !isChecked);

        mSquareCorners = Token.getCornerRadius(this, R.attr.oemShapeCornerFull) == 0;
        cornerSwitch.setChecked(mSquareCorners);
        cornerSwitch.setOnCheckedChangeListener(
                (buttonView, isChecked) -> mSquareCorners = isChecked);

        List<RadioButton> radioButtons = new ArrayList<>();
        RadioButton rbTonalSpot = requireViewById(R.id.scheme_tonal_spot);
        RadioButton rbVibrant = requireViewById(R.id.scheme_vibrant);
        RadioButton rbExpressive = requireViewById(R.id.scheme_expressive);
        RadioButton rbNeutral = requireViewById(R.id.scheme_neutral);
        RadioButton rbContent = requireViewById(R.id.scheme_content);

        radioButtons.add(rbTonalSpot);
        radioButtons.add(rbVibrant);
        radioButtons.add(rbExpressive);
        radioButtons.add(rbNeutral);
        radioButtons.add(rbContent);

        for (RadioButton rb : radioButtons) {
            rb.setOnClickListener(v -> {
                for (RadioButton other : radioButtons) {
                    other.setChecked(other == v);
                }
                if (v.getId() == R.id.scheme_tonal_spot) {
                    mSchemeType = "Tonal Spot";
                } else if (v.getId() == R.id.scheme_vibrant) {
                    mSchemeType = "Vibrant";
                } else if (v.getId() == R.id.scheme_expressive) {
                    mSchemeType = "Expressive";
                } else if (v.getId() == R.id.scheme_neutral) {
                    mSchemeType = "Neutral";
                } else if (v.getId() == R.id.scheme_content) {
                    mSchemeType = "Content";
                }
            });
        }

        List<MenuItem> menuItems = new ArrayList<>();
        menuItems.add(MenuItem.builder(this)
                .setTitle("Apply")
                .setOnClickListener(i -> {
                    int seedColor = getSeedColor();
                    Hct seed = Hct.fromInt(seedColor);
                    boolean isDark = !mIsLightMode;
                    switch (mSchemeType) {
                        case "Tonal Spot": mScheme = new SchemeTonalSpot(seed, isDark, 0.0); break;
                        case "Expressive": mScheme = new SchemeExpressive(seed, isDark, 0.0); break;
                        case "Neutral": mScheme = new SchemeNeutral(seed, isDark, 0.0); break;
                        case "Content": mScheme = new SchemeContent(seed, isDark, 0.0); break;
                        default: mScheme = new SchemeVibrant(seed, isDark, 0.0); break;
                    }

                    FabricatedOverlay appOverlay = createAppOverlay();
                    FabricatedOverlay frameworkOverlay = createFrameworkOverlay();

                    OverlayManagerTransaction.Builder transaction =
                            new OverlayManagerTransaction.Builder()
                            .unregisterFabricatedOverlay(
                                    new OverlayIdentifier(OWNING_PACKAGE, OVERLAY_NAME))
                            .unregisterFabricatedOverlay(
                                    new OverlayIdentifier(OWNING_PACKAGE, ANDROID_OVERLAY_NAME))
                            .registerFabricatedOverlay(appOverlay)
                            .setEnabled(appOverlay.getIdentifier(), true)
                            .setEnabled(appOverlay.getIdentifier(), true, 0)
                            .registerFabricatedOverlay(frameworkOverlay)
                            .setEnabled(frameworkOverlay.getIdentifier(), true)
                            .setEnabled(frameworkOverlay.getIdentifier(), true, 0);

                    mOverlayManager.commit(transaction.build());
                })
                .build());
        menuItems.add(MenuItem.builder(this)
                .setTitle("Reset")
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

        float[] hsv = {hue, mSaturation, mValue};
        return Color.HSVToColor(hsv);
    }

    private void updateColorPreview() {
        int color = getSeedColor();
        ((GradientDrawable) mColorPreview.getBackground()).setColor(color);
        if (mHexInput != null) {
            mHexInput.setText(String.format("#%06X", (0xFFFFFF & color)));
        }
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

    private FabricatedOverlay createAppOverlay() {

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
                        Blend.harmonize(Token.getColor(this, R.attr.oemColorBlue),
                                mScheme.getPrimary()), null)
                .setResourceValue("com.android.oem.tokens:color/color_on_blue",
                        TypedValue.TYPE_INT_COLOR_ARGB8,
                        Blend.harmonize(Token.getColor(this, R.attr.oemColorOnBlue),
                                mScheme.getPrimary()), null)
                .setResourceValue("com.android.oem.tokens:color/color_blue_container",
                        TypedValue.TYPE_INT_COLOR_ARGB8,
                        Blend.harmonize(Token.getColor(this, R.attr.oemColorBlueContainer),
                                mScheme.getPrimary()), null)
                .setResourceValue("com.android.oem.tokens:color/color_on_blue_container",
                        TypedValue.TYPE_INT_COLOR_ARGB8,
                        Blend.harmonize(Token.getColor(this, R.attr.oemColorOnBlueContainer),
                                mScheme.getPrimary()), null)
                .setResourceValue("com.android.oem.tokens:color/color_green",
                        TypedValue.TYPE_INT_COLOR_ARGB8,
                        Blend.harmonize(Token.getColor(this, R.attr.oemColorGreen),
                                mScheme.getPrimary()), null)
                .setResourceValue("com.android.oem.tokens:color/color_on_green",
                        TypedValue.TYPE_INT_COLOR_ARGB8,
                        Blend.harmonize(Token.getColor(this, R.attr.oemColorOnGreen),
                                mScheme.getPrimary()), null)
                .setResourceValue("com.android.oem.tokens:color/color_green_container",
                        TypedValue.TYPE_INT_COLOR_ARGB8,
                        Blend.harmonize(Token.getColor(this, R.attr.oemColorGreenContainer),
                                mScheme.getPrimary()), null)
                .setResourceValue("com.android.oem.tokens:color/color_on_green_container",
                        TypedValue.TYPE_INT_COLOR_ARGB8,
                        Blend.harmonize(Token.getColor(this, R.attr.oemColorOnGreenContainer),
                                mScheme.getPrimary()), null)
                .setResourceValue("com.android.oem.tokens:color/color_yellow",
                        TypedValue.TYPE_INT_COLOR_ARGB8,
                        Blend.harmonize(Token.getColor(this, R.attr.oemColorYellow),
                                mScheme.getPrimary()), null)
                .setResourceValue("com.android.oem.tokens:color/color_on_yellow",
                        TypedValue.TYPE_INT_COLOR_ARGB8,
                        Blend.harmonize(Token.getColor(this, R.attr.oemColorOnYellow),
                                mScheme.getPrimary()), null)
                .setResourceValue("com.android.oem.tokens:color/color_yellow_container",
                        TypedValue.TYPE_INT_COLOR_ARGB8,
                        Blend.harmonize(Token.getColor(this, R.attr.oemColorYellowContainer),
                                mScheme.getPrimary()), null)
                .setResourceValue("com.android.oem.tokens:color/color_on_yellow_container",
                        TypedValue.TYPE_INT_COLOR_ARGB8,
                        Blend.harmonize(Token.getColor(this, R.attr.oemColorOnYellowContainer),
                                mScheme.getPrimary()), null)
                .setResourceValue("com.android.oem.tokens:color/color_red",
                        TypedValue.TYPE_INT_COLOR_ARGB8,
                        Blend.harmonize(Token.getColor(this, R.attr.oemColorRed),
                                mScheme.getPrimary()), null)
                .setResourceValue("com.android.oem.tokens:color/color_on_red",
                        TypedValue.TYPE_INT_COLOR_ARGB8,
                        Blend.harmonize(Token.getColor(this, R.attr.oemColorOnRed),
                                mScheme.getPrimary()), null)
                .setResourceValue("com.android.oem.tokens:color/color_red_container",
                        TypedValue.TYPE_INT_COLOR_ARGB8,
                        Blend.harmonize(Token.getColor(this, R.attr.oemColorRedContainer),
                                mScheme.getPrimary()), null)
                .setResourceValue("com.android.oem.tokens:color/color_on_red_container",
                        TypedValue.TYPE_INT_COLOR_ARGB8,
                        Blend.harmonize(Token.getColor(this, R.attr.oemColorOnRedContainer),
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

        return overlay;
    }

    private FabricatedOverlay createFrameworkOverlay() {
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

        return overlay;
    }

    private List<TokenDemoAdapter.TokenItem> createColorList() {
        List<TokenDemoAdapter.TokenItem> list = new ArrayList<>();

        list.add(new TokenDemoAdapter.TokenItem("colorPrimary", R.attr.oemColorPrimary,
                TokenDemoAdapter.VIEW_TYPE_LIST_COLOR));
        list.add(new TokenDemoAdapter.TokenItem("colorOnPrimary", R.attr.oemColorOnPrimary,
                TokenDemoAdapter.VIEW_TYPE_LIST_COLOR));
        list.add(new TokenDemoAdapter.TokenItem("colorPrimaryContainer",
                R.attr.oemColorPrimaryContainer, TokenDemoAdapter.VIEW_TYPE_LIST_COLOR));
        list.add(new TokenDemoAdapter.TokenItem("colorPrimaryOnContainer",
                R.attr.oemColorOnPrimaryContainer, TokenDemoAdapter.VIEW_TYPE_LIST_COLOR));

        list.add(new TokenDemoAdapter.TokenItem("colorSecondary", R.attr.oemColorSecondary,
                TokenDemoAdapter.VIEW_TYPE_LIST_COLOR));
        list.add(new TokenDemoAdapter.TokenItem("colorOnSecondary", R.attr.oemColorOnSecondary,
                TokenDemoAdapter.VIEW_TYPE_LIST_COLOR));
        list.add(new TokenDemoAdapter.TokenItem("colorSecondaryContainer",
                R.attr.oemColorSecondaryContainer, TokenDemoAdapter.VIEW_TYPE_LIST_COLOR));
        list.add(new TokenDemoAdapter.TokenItem("colorSecondaryOnContainer",
                R.attr.oemColorOnSecondaryContainer, TokenDemoAdapter.VIEW_TYPE_LIST_COLOR));

        list.add(new TokenDemoAdapter.TokenItem("colorTertiary", R.attr.oemColorTertiary,
                TokenDemoAdapter.VIEW_TYPE_LIST_COLOR));
        list.add(new TokenDemoAdapter.TokenItem("colorOnTertiary", R.attr.oemColorOnTertiary,
                TokenDemoAdapter.VIEW_TYPE_LIST_COLOR));
        list.add(new TokenDemoAdapter.TokenItem("colorTertiaryContainer",
                R.attr.oemColorTertiaryContainer, TokenDemoAdapter.VIEW_TYPE_LIST_COLOR));
        list.add(new TokenDemoAdapter.TokenItem("colorTertiaryOnContainer",
                R.attr.oemColorOnTertiaryContainer, TokenDemoAdapter.VIEW_TYPE_LIST_COLOR));

        list.add(new TokenDemoAdapter.TokenItem("colorError", R.attr.oemColorError,
                TokenDemoAdapter.VIEW_TYPE_LIST_COLOR));
        list.add(new TokenDemoAdapter.TokenItem("colorOnError", R.attr.oemColorOnError,
                TokenDemoAdapter.VIEW_TYPE_LIST_COLOR));
        list.add(new TokenDemoAdapter.TokenItem("colorErrorContainer",
                R.attr.oemColorErrorContainer, TokenDemoAdapter.VIEW_TYPE_LIST_COLOR));
        list.add(new TokenDemoAdapter.TokenItem("colorErrorOnContainer",
                R.attr.oemColorOnErrorContainer, TokenDemoAdapter.VIEW_TYPE_LIST_COLOR));

        list.add(new TokenDemoAdapter.TokenItem("colorSurface", R.attr.oemColorSurface,
                TokenDemoAdapter.VIEW_TYPE_LIST_COLOR));
        list.add(new TokenDemoAdapter.TokenItem("colorOnSurface", R.attr.oemColorOnSurface,
                TokenDemoAdapter.VIEW_TYPE_LIST_COLOR));
        list.add(new TokenDemoAdapter.TokenItem("colorSurfaceVariant",
                R.attr.oemColorSurfaceVariant, TokenDemoAdapter.VIEW_TYPE_LIST_COLOR));
        list.add(new TokenDemoAdapter.TokenItem("colorOnSurfaceVariant",
                R.attr.oemColorOnSurfaceVariant, TokenDemoAdapter.VIEW_TYPE_LIST_COLOR));

        list.add(new TokenDemoAdapter.TokenItem("colorOutline", R.attr.oemColorOutline,
                TokenDemoAdapter.VIEW_TYPE_LIST_COLOR));

        list.add(new TokenDemoAdapter.TokenItem("colorSurfaceDim", R.attr.oemColorSurfaceDim,
                TokenDemoAdapter.VIEW_TYPE_LIST_COLOR));
        list.add(new TokenDemoAdapter.TokenItem("colorSurfaceBright", R.attr.oemColorSurfaceBright,
                TokenDemoAdapter.VIEW_TYPE_LIST_COLOR));
        list.add(new TokenDemoAdapter.TokenItem("colorSurfaceContainer",
                R.attr.oemColorSurfaceContainer, TokenDemoAdapter.VIEW_TYPE_LIST_COLOR));
        list.add(new TokenDemoAdapter.TokenItem("colorSurfaceContainerLow",
                R.attr.oemColorSurfaceContainerLow, TokenDemoAdapter.VIEW_TYPE_LIST_COLOR));
        list.add(new TokenDemoAdapter.TokenItem("colorSurfaceContainerLowest",
                R.attr.oemColorSurfaceContainerLowest, TokenDemoAdapter.VIEW_TYPE_LIST_COLOR));
        list.add(new TokenDemoAdapter.TokenItem("colorSurfaceContainerHigh",
                R.attr.oemColorSurfaceContainerHigh, TokenDemoAdapter.VIEW_TYPE_LIST_COLOR));
        list.add(new TokenDemoAdapter.TokenItem("colorSurfaceContainerHighest",
                R.attr.oemColorSurfaceContainerHighest, TokenDemoAdapter.VIEW_TYPE_LIST_COLOR));
        list.add(new TokenDemoAdapter.TokenItem("colorShadow",
                R.attr.oemColorShadow, TokenDemoAdapter.VIEW_TYPE_LIST_COLOR));

        list.add(new TokenDemoAdapter.TokenItem("colorBlue", R.attr.oemColorBlue,
                TokenDemoAdapter.VIEW_TYPE_LIST_COLOR));
        list.add(new TokenDemoAdapter.TokenItem("colorOnBlue",
                R.attr.oemColorOnBlue, TokenDemoAdapter.VIEW_TYPE_LIST_COLOR));
        list.add(new TokenDemoAdapter.TokenItem("colorBlueContainer", R.attr.oemColorBlueContainer,
                TokenDemoAdapter.VIEW_TYPE_LIST_COLOR));
        list.add(new TokenDemoAdapter.TokenItem("colorOnBlueContainer",
                R.attr.oemColorOnBlueContainer, TokenDemoAdapter.VIEW_TYPE_LIST_COLOR));

        list.add(new TokenDemoAdapter.TokenItem("colorGreen", R.attr.oemColorGreen,
                TokenDemoAdapter.VIEW_TYPE_LIST_COLOR));
        list.add(new TokenDemoAdapter.TokenItem("colorOnGreen",
                R.attr.oemColorOnGreen, TokenDemoAdapter.VIEW_TYPE_LIST_COLOR));
        list.add(new TokenDemoAdapter.TokenItem("colorGreenContainer",
                R.attr.oemColorGreenContainer, TokenDemoAdapter.VIEW_TYPE_LIST_COLOR));
        list.add(new TokenDemoAdapter.TokenItem("colorOnGreenContainer",
                R.attr.oemColorOnGreenContainer, TokenDemoAdapter.VIEW_TYPE_LIST_COLOR));

        list.add(new TokenDemoAdapter.TokenItem("colorYellow", R.attr.oemColorYellow,
                TokenDemoAdapter.VIEW_TYPE_LIST_COLOR));
        list.add(new TokenDemoAdapter.TokenItem("colorOnYellow",
                R.attr.oemColorOnYellow, TokenDemoAdapter.VIEW_TYPE_LIST_COLOR));
        list.add(new TokenDemoAdapter.TokenItem("colorYellowContainer",
                R.attr.oemColorYellowContainer, TokenDemoAdapter.VIEW_TYPE_LIST_COLOR));
        list.add(new TokenDemoAdapter.TokenItem("colorOnYellowContainer",
                R.attr.oemColorOnYellowContainer, TokenDemoAdapter.VIEW_TYPE_LIST_COLOR));

        list.add(new TokenDemoAdapter.TokenItem("colorRed", R.attr.oemColorRed,
                TokenDemoAdapter.VIEW_TYPE_LIST_COLOR));
        list.add(new TokenDemoAdapter.TokenItem("colorOnRed", R.attr.oemColorOnRed,
                TokenDemoAdapter.VIEW_TYPE_LIST_COLOR));
        list.add(new TokenDemoAdapter.TokenItem("colorRedContainer", R.attr.oemColorRedContainer,
                TokenDemoAdapter.VIEW_TYPE_LIST_COLOR));
        list.add(new TokenDemoAdapter.TokenItem("colorOnRedContainer",
                R.attr.oemColorOnRedContainer, TokenDemoAdapter.VIEW_TYPE_LIST_COLOR));

        return list;
    }

    private List<TokenDemoAdapter.TokenItem> createTextList() {
        List<TokenDemoAdapter.TokenItem> list = new ArrayList<>();
        list.add(new TokenDemoAdapter.TokenItem("Display Large",
                R.attr.oemTextAppearanceDisplayLarge, TokenDemoAdapter.VIEW_TYPE_LIST_TEXT));
        list.add(new TokenDemoAdapter.TokenItem("Display Medium",
                R.attr.oemTextAppearanceDisplayMedium, TokenDemoAdapter.VIEW_TYPE_LIST_TEXT));
        list.add(new TokenDemoAdapter.TokenItem("Display Small",
                R.attr.oemTextAppearanceDisplaySmall, TokenDemoAdapter.VIEW_TYPE_LIST_TEXT));

        list.add(new TokenDemoAdapter.TokenItem("Headline Large",
                R.attr.oemTextAppearanceHeadlineLarge, TokenDemoAdapter.VIEW_TYPE_LIST_TEXT));
        list.add(new TokenDemoAdapter.TokenItem("Headline Medium",
                R.attr.oemTextAppearanceHeadlineMedium, TokenDemoAdapter.VIEW_TYPE_LIST_TEXT));
        list.add(new TokenDemoAdapter.TokenItem("Headline Small",
                R.attr.oemTextAppearanceHeadlineSmall, TokenDemoAdapter.VIEW_TYPE_LIST_TEXT));

        list.add(new TokenDemoAdapter.TokenItem("Title Large",
                R.attr.oemTextAppearanceTitleLarge, TokenDemoAdapter.VIEW_TYPE_LIST_TEXT));
        list.add(new TokenDemoAdapter.TokenItem("Title Medium",
                R.attr.oemTextAppearanceTitleMedium, TokenDemoAdapter.VIEW_TYPE_LIST_TEXT));
        list.add(new TokenDemoAdapter.TokenItem("Title Small",
                R.attr.oemTextAppearanceTitleSmall, TokenDemoAdapter.VIEW_TYPE_LIST_TEXT));

        list.add(new TokenDemoAdapter.TokenItem("Label Large",
                R.attr.oemTextAppearanceLabelLarge, TokenDemoAdapter.VIEW_TYPE_LIST_TEXT));
        list.add(new TokenDemoAdapter.TokenItem("Label Medium",
                R.attr.oemTextAppearanceLabelMedium, TokenDemoAdapter.VIEW_TYPE_LIST_TEXT));
        list.add(new TokenDemoAdapter.TokenItem("Label Small",
                R.attr.oemTextAppearanceLabelSmall, TokenDemoAdapter.VIEW_TYPE_LIST_TEXT));

        list.add(new TokenDemoAdapter.TokenItem("Body Large",
                R.attr.oemTextAppearanceBodyLarge, TokenDemoAdapter.VIEW_TYPE_LIST_TEXT));
        list.add(new TokenDemoAdapter.TokenItem("Body Medium",
                R.attr.oemTextAppearanceBodyMedium, TokenDemoAdapter.VIEW_TYPE_LIST_TEXT));
        list.add(new TokenDemoAdapter.TokenItem("Body Small",
                R.attr.oemTextAppearanceBodySmall, TokenDemoAdapter.VIEW_TYPE_LIST_TEXT));

        return list;
    }

    private List<TokenDemoAdapter.TokenItem> createCornerRadiusList() {
        List<TokenDemoAdapter.TokenItem> list = new ArrayList<>();
        list.add(new TokenDemoAdapter.TokenItem("Corner None",
                R.attr.oemShapeCornerNone, TokenDemoAdapter.VIEW_TYPE_LIST_SHAPE));
        list.add(new TokenDemoAdapter.TokenItem("Corner Extra Small",
                R.attr.oemShapeCornerExtraSmall, TokenDemoAdapter.VIEW_TYPE_LIST_SHAPE));
        list.add(new TokenDemoAdapter.TokenItem("Corner Small",
                R.attr.oemShapeCornerSmall, TokenDemoAdapter.VIEW_TYPE_LIST_SHAPE));
        list.add(new TokenDemoAdapter.TokenItem("Corner Medium",
                R.attr.oemShapeCornerMedium, TokenDemoAdapter.VIEW_TYPE_LIST_SHAPE));
        list.add(new TokenDemoAdapter.TokenItem("Corner Large",
                R.attr.oemShapeCornerLarge, TokenDemoAdapter.VIEW_TYPE_LIST_SHAPE));
        list.add(new TokenDemoAdapter.TokenItem("Corner Extra Large",
                R.attr.oemShapeCornerExtraLarge, TokenDemoAdapter.VIEW_TYPE_LIST_SHAPE));
        list.add(new TokenDemoAdapter.TokenItem("Corner Full",
                R.attr.oemShapeCornerFull, TokenDemoAdapter.VIEW_TYPE_LIST_SHAPE));

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
