/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.android.car.ui.pluginsupport;

import android.content.Context;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.RecyclerView;

import com.android.car.ui.baselayout.InsetsChangedListener;
import com.android.car.ui.recyclerview.CarUiListItem;
import com.android.car.ui.recyclerview.CarUiRecyclerView;
import com.android.car.ui.toolbar.ToolbarController;
import com.android.car.ui.widget.CarUiTextView;

import java.util.List;

/**
 * This interface contains methods to create customizable carui components.
 *
 * Oftentimes, a static carui component (e.g., ToolbarControllerImpl) implements a corresponding
 * interface (e.g., ToolbarController). A plugin implementation of a similar component (e.g.,
 * ToolbarAdapterProxy), also implements a corresponding interface (e.g., ToolbarControllerOEMV2).
 * The oem versions (postfixed with OEMV#) of these interfaces almost always contain a subset of
 * methods of the static interface equivalent (e.g., plugin ToolbarControllerOEMV2 interface
 * contains a subset of static ToolbarController interface methods). This is for two main reasons:
 *
 * 1. To simplify implementation of these components for oems. For example, an oem only implements
 * one version of setTitle in ToolbarControllerOEMV2, while the carui static library implements
 * three versions of setTitle in ToolbarController to accommodate different string types.
 *
 * 2. To separate the methods that apps can use to interface with carui components from the methods
 * that are implemented (therefore customized) by oems. For example, there is a method in the
 * ToolbarController interface called setShowMenuItemsWhileSearching which allows an app to choose
 * whether or not they want menu items to be shown while in search mode. This is not present in
 * ToolbarControllerOEMV2.
 *
 * When an application interfaces with a carui component (e.g., ToolbarController) without a plugin
 * implementation on the system, the carui library delegates its calls to the corresponding static
 * implementation of that component (e.g., ToolbarControllerImpl). However, if there is an existing
 * plugin implementation on the system, the carui library keeps track of all state not relevant for
 * oems (either because it's only to be used by applications or for simplicity for oems) in an
 * adapter class (e.g., ToolbarControllerAdapterV2). This adapter forwards all changes relevant to
 * oems to the plugin implementation (e.g., ToolbarAdapterProxy) in a way canonical with the
 * corresponding interface (e.g., ToolbarControllerOEMV2).
 *
 * For example, the ToolbarControllerAdapterV2 keeps track of whether or not an app wants to
 * "showMenuItemsWhileSearching", and will forward a list of menu items to be set by the oem
 * implementation depending on the overall state of the toolbar from the app perspective. So if
 * search mode is enabled, and an app calls setShowMenuItemsWhileSearching with true,
 * ToolbarControllerAdapterV2 will call the plugin toolbar's implementation of setMenuItems with
 * menu items that should be shown while searching. If search mode is enabled and an app calls it
 * with false, the adapter will call setMenuItems with an empty list. This achieves the same effect
 * from the app perspective, while maintaining the separation of app-specific and oem-specific
 * customization.
 */
public interface PluginFactory {

    /**
     * Creates the base layout, and optionally the toolbar.
     *
     * @param context               The visual context to create views with.
     * @param contentView           The view to install the base layout around.
     * @param insetsChangedListener A method to call when the insets change.
     * @param toolbarEnabled        Whether or not to add a toolbar to the base layout.
     * @param fullscreen            A hint specifying whether this view we're installing around
     *                              takes up the whole screen or not. Used to know if putting
     *                              decorations around the edges is appropriate.
     * @return A {@link ToolbarController} or null if {@code toolbarEnabled} was false.
     */
    @Nullable
    ToolbarController installBaseLayoutAround(
            @NonNull Context context,
            @NonNull View contentView,
            @Nullable InsetsChangedListener insetsChangedListener,
            boolean toolbarEnabled,
            boolean fullscreen);

    /**
     * Creates a {@link CarUiTextView}.
     *
     * @param context The visual context to create views with.
     * @return A {@link CarUiTextView}
     */
    @NonNull
    CarUiTextView createTextView(@NonNull Context context, @Nullable AttributeSet attrs);

    /**
     * Creates a preference view
     *
     * @param context the visual context to create views with.
     * @param attrs An object containing initial attributes for the preference.
     */
    @NonNull
    View createCarUiPreferenceView(@NonNull Context context, @NonNull AttributeSet attrs);



    /**
     * Creates an instance of CarUiRecyclerView
     *
     * @param context The visual context to create views with.
     * @param attrs   An object containing initial attributes for the button.
     */
    @NonNull
    CarUiRecyclerView createRecyclerView(@NonNull Context context, @Nullable AttributeSet attrs);

    /**
     * Creates an instance of list item adapter
     */
    @NonNull
    RecyclerView.Adapter<? extends RecyclerView.ViewHolder> createListItemAdapter(
            @NonNull List<? extends CarUiListItem> items);
}
