/*
 * Copyright (C) 2023 The Android Open Source Project
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

package com.chassis.car.ui.plugin;


import android.content.Context;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.android.car.ui.plugin.PluginUiContextFactory;
import com.android.car.ui.plugin.oemapis.Consumer;
import com.android.car.ui.plugin.oemapis.FocusAreaOEMV1;
import com.android.car.ui.plugin.oemapis.FocusParkingViewOEMV1;
import com.android.car.ui.plugin.oemapis.Function;
import com.android.car.ui.plugin.oemapis.InsetsOEMV1;
import com.android.car.ui.plugin.oemapis.PluginFactoryOEMV8;
import com.android.car.ui.plugin.oemapis.preference.PreferenceOEMV1;
import com.android.car.ui.plugin.oemapis.recyclerview.AdapterOEMV2;
import com.android.car.ui.plugin.oemapis.recyclerview.ListItemOEMV1;
import com.android.car.ui.plugin.oemapis.recyclerview.RecyclerViewAttributesOEMV1;
import com.android.car.ui.plugin.oemapis.recyclerview.RecyclerViewOEMV3;
import com.android.car.ui.plugin.oemapis.recyclerview.ViewHolderOEMV1;
import com.android.car.ui.plugin.oemapis.toolbar.ToolbarControllerOEMV3;
import com.android.car.ui.recyclerview.CarUiRecyclerViewImpl;


import com.chassis.car.ui.plugin.preference.PreferenceAdapterProxy;
import com.chassis.car.ui.plugin.recyclerview.CarUiListItemAdapterAdapterProxyV2;
import com.chassis.car.ui.plugin.recyclerview.RecyclerViewAdapterProxyV3;
import com.chassis.car.ui.plugin.toolbar.BaseLayoutInstallerProxy;

import java.util.List;

/**
 * An implementation of the plugin factory that delegates back to the car-ui-lib implementation.
 * The main benefit of this is so that customizations can be applied to the car-ui-lib via a RRO
 * without the need to target each app specifically. Note: it only applies to the components that
 * come through the plugin system.
 */
public class PluginFactoryImplV8 implements PluginFactoryOEMV8 {
    private final PluginUiContextFactory mPluginUiContextFactory;

    public PluginFactoryImplV8(Context pluginContext) {
        mPluginUiContextFactory = new PluginUiContextFactory(pluginContext);
    }

    @Override
    public void setRotaryFactories(
            Function<Context, FocusParkingViewOEMV1> focusParkingViewFactory,
            Function<Context, FocusAreaOEMV1> focusAreaFactory) {
    }

    @Nullable
    @Override
    public ToolbarControllerOEMV3 installBaseLayoutAround(
            @NonNull Context sourceContext,
            @NonNull View contentView,
            @Nullable Consumer<InsetsOEMV1> insetsChangedListener,
            boolean toolbarEnabled,
            boolean fullscreen) {
        Context pluginContext = mPluginUiContextFactory.getPluginUiContext(sourceContext);
        return BaseLayoutInstallerProxy.installBaseLayoutAroundV3(
                pluginContext, contentView, insetsChangedListener, toolbarEnabled, fullscreen);
    }

    @Override
    public boolean customizesBaseLayout() {
        return true;
    }

    @Override
    public PreferenceOEMV1 createCarUiPreference(@NonNull Context sourceContext) {
        Context pluginContext = mPluginUiContextFactory.getPluginUiContext(sourceContext);
        return new PreferenceAdapterProxy(pluginContext, sourceContext);
    }


    @Nullable
    @Override
    public RecyclerViewOEMV3 createRecyclerView(@NonNull Context sourceContext,
            @Nullable RecyclerViewAttributesOEMV1 recyclerViewAttributesOEMV1) {
        Context pluginContext = mPluginUiContextFactory.getPluginUiContext(sourceContext);
        CarUiRecyclerViewImpl recyclerView =
                new CarUiRecyclerViewImpl(pluginContext, recyclerViewAttributesOEMV1);
        return new RecyclerViewAdapterProxyV3(pluginContext, recyclerView,
                recyclerViewAttributesOEMV1);
    }

    @Override
    public AdapterOEMV2<? extends ViewHolderOEMV1> createListItemAdapter(
            List<ListItemOEMV1> items) {
        return new CarUiListItemAdapterAdapterProxyV2(
                items, mPluginUiContextFactory.getRecentPluginUiContext());
    }
}
