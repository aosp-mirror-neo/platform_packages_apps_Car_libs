/*
 * Copyright (C) 2022 The Android Open Source Project
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
package com.android.car.ui.paintbooth;

import android.app.Activity;
import android.os.Bundle;
import android.util.Log;
import android.util.Pair;
import android.widget.Switch;
import android.widget.Toast;

import com.android.car.oem.tokens.Token;
import com.android.car.ui.core.CarUi;
import com.android.car.ui.paintbooth.overlays.OverlayManager;
import com.android.car.ui.recyclerview.CarUiRecyclerView;
import com.android.car.ui.toolbar.NavButtonMode;
import com.android.car.ui.toolbar.Tab;
import com.android.car.ui.toolbar.ToolbarController;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Activity that shows displays the values of OEM design tokens.
 */
public class TokenActivity extends Activity {
    private static final String TAG = "TokenActivity";
    private OverlayManager mOverlayManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.token_activity);

        CarUiRecyclerView list = requireViewById(R.id.list);
        TokenDemoAdapter adapter = new TokenDemoAdapter(createColorList());
        list.setAdapter(adapter);

        ToolbarController toolbar = CarUi.requireToolbar(this);
        toolbar.setTitle(getTitle());
        toolbar.setNavButtonMode(NavButtonMode.BACK);

        List<Tab> tabs = new ArrayList<>();
        tabs.add(Tab.builder().setText("Color").setIcon(
                getDrawable(R.drawable.car_ui_icon_edit)).setSelectedListener(
                tab -> list.setAdapter(new TokenDemoAdapter(createColorList()))).build());
        tabs.add(Tab.builder().setText("Text").setIcon(
                getDrawable(R.drawable.car_ui_icon_edit)).setSelectedListener(
                tab -> list.setAdapter(new TokenDemoAdapter(createTextList()))).build());
        tabs.add(Tab.builder().setText("Shape").setIcon(
                getDrawable(R.drawable.car_ui_icon_edit)).setSelectedListener(
                tab -> list.setAdapter(new TokenDemoAdapter(createCornerRadiusList()))).build());
        toolbar.setTabs(tabs, 0);

        Switch oemSwitch = requireViewById(R.id.oem_switch1);

        String tokenLibPackageName = Token.getTokenSharedLibPackageName(getPackageManager());
        if (tokenLibPackageName == null) {
            Toast.makeText(this, "OEM design token shared library not found",
                    Toast.LENGTH_LONG).show();
            return;
        }

        try {
            mOverlayManager = OverlayManager.getInstance(this);
            Map<String, List<OverlayManager.OverlayInfo>> overlays =
                    OverlayManager.getInstance(this).getOverlays();

            List<OverlayManager.OverlayInfo> tokenOverlays = overlays.get(tokenLibPackageName);
            if (tokenOverlays == null) {
                Toast.makeText(this, "No token overlays found", Toast.LENGTH_LONG).show();
                return;
            }

            OverlayManager.OverlayInfo overlay = tokenOverlays.get(0);
            oemSwitch.setChecked(overlay.isEnabled());
            oemSwitch.setEnabled(true);
            oemSwitch.setOnCheckedChangeListener(
                    (buttonView, isChecked) -> applyOverlay(overlay.getPackageName(), isChecked));
        } catch (Exception e) {
            Toast.makeText(this, "Error: " + e.getMessage(), Toast.LENGTH_LONG).show();
            Log.e(TAG, "Can't load overlay manager: ", e);
        }

    }

    private void applyOverlay(String overlayPackage, boolean enableOverlay) {
        try {
            mOverlayManager.applyOverlay(overlayPackage, enableOverlay);
        } catch (Exception e) {
            Toast.makeText(this, "Error: " + e.getMessage(),
                    Toast.LENGTH_LONG).show();
            Log.e(TAG, "Can't apply overlay: ", e);
        }
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

        list.add(new Pair<>("colorBackground", R.attr.oemColorBackground));
        list.add(new Pair<>("colorOnBackground", R.attr.oemColorOnBackground));
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
}
