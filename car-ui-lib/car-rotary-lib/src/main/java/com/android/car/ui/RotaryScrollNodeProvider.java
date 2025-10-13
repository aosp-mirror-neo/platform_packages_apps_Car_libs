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

package com.android.car.ui;

import static com.android.car.ui.RotaryScrollWebView.TAG;
import static com.android.car.ui.utils.RotaryConstants.ROTARY_VERTICALLY_SCROLLABLE;

import android.annotation.TargetApi;
import android.os.Build;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.accessibility.AccessibilityNodeProvider;
import android.webkit.WebView;
import androidx.annotation.NonNull;
import java.util.List;

/**
 * An {@link AccessibilityNodeProvider} with customized behavior to enable Rotary scrolling.
 * <p>
 * This class expects the host {@link WebView} to help maintain and consume the values assigned to
 * {@link R.id#car_ui_web_view_last_focused_element}. If not using a {@link RotaryScrollWebView},
 * use with a {@link RotaryScrollManager} attached to any WebView.
 */
@TargetApi(Build.VERSION_CODES.R)
class RotaryScrollNodeProvider extends AccessibilityNodeProvider {
    /**
     * The {@link WebView} that represents the host {@link AccessibilityNodeInfo}. This is ideally
     * a {@link RotaryScrollWebView}.
     */
    @NonNull
    private final WebView mWebView;

    /**
     * The instance returned by {@link #mWebView}'s super implementation of
     * {@link WebView#getAccessibilityNodeProvider}.
     */
    @NonNull
    private final AccessibilityNodeProvider mBaseProvider;

    RotaryScrollNodeProvider(@NonNull WebView webView,
            @NonNull AccessibilityNodeProvider provider) {
        mWebView = webView;
        mBaseProvider = provider;
        if (!(webView.getAccessibilityDelegate() instanceof RotaryScrollManager)) {
            throw new IllegalStateException("The provided WebView must attach a "
                    + RotaryScrollManager.class.getName()
                    + " before constructing this class.");
        }
    }

    @Override
    public AccessibilityNodeInfo createAccessibilityNodeInfo(int virtualViewId) {
        AccessibilityNodeInfo node = mBaseProvider.createAccessibilityNodeInfo(virtualViewId);
        if (node == null) {
            return null;
        }
        if (TextUtils.equals(node.getClassName(), WebView.class.getName())) {
            enableRotaryScrolling(node, virtualViewId);
        }
        return node;
    }

    /**
     * {@inheritDoc}
     * If the given focus type is {@link AccessibilityNodeInfo#FOCUS_INPUT} and {@link #mWebView}
     * has a last focused node, returns the last focused node.
     */
    @Override
    public AccessibilityNodeInfo findFocus(int focus) {
        if (focus != AccessibilityNodeInfo.FOCUS_INPUT) {
            return mBaseProvider.findFocus(focus);
        }

        // RotaryService uses AccessibilityNodeInfo.FOCUS_INPUT to determine which node took
        // focus within a FocusArea after a successful nudge. Since WebView does not implement
        // this at all, we make the best effort to let the user nudge back to their last position
        // on the page.
        Integer id = RotaryScrollManager.getLastFocusedNodeId(mWebView);
        if (id != null) {
            AccessibilityNodeInfo node = createAccessibilityNodeInfo(id);
            if (node != null) {
                return node;
            }
        }
        return mBaseProvider.findFocus(focus);
    }

    @Override
    public boolean performAction(int virtualViewId, int action, Bundle arguments) {
        if (!mBaseProvider.performAction(virtualViewId, action, arguments)) {
            return false;
        }
        if (action == AccessibilityNodeInfo.ACTION_CLEAR_FOCUS) {
            RotaryScrollManager.setLastFocusedNodeId(mWebView, null);
        } else if (action == AccessibilityNodeInfo.ACTION_FOCUS) {
            RotaryScrollManager.setLastFocusedNodeId(mWebView, virtualViewId);
            // Always clear the color filter when a child is focused (ACTION_FOCUS is never
            // called on the WebView nodes).
            RotaryScrollManager.clearScrollbarThumbHighlight(mWebView);
        }
        return true;
    }

    @Override
    public void addExtraDataToAccessibilityNodeInfo(int virtualViewId,
            AccessibilityNodeInfo info,
            String extraDataKey,
            Bundle arguments) {
        mBaseProvider.addExtraDataToAccessibilityNodeInfo(virtualViewId, info, extraDataKey,
                arguments);
    }

    @Override
    public List<AccessibilityNodeInfo> findAccessibilityNodeInfosByText(String text,
            int virtualViewId) {
        return mBaseProvider.findAccessibilityNodeInfosByText(text, virtualViewId);
    }

    /**
     * Set parameters on the node representing a {@link WebView} to enable Rotary scrolling.
     *
     * {@link com.android.car.rotary.RotaryService} does not jump scroll inside of nodes that
     * have scrolling actions that match the requested direction from the user. Since WebViews do
     * not add these actions in their base implementation, we add them here if the WebView can
     * scroll in the specified direction. This tells RotaryService to scroll the WebView when
     * the WebView has focus and no children are on-screen.
     */
    private void enableRotaryScrolling(AccessibilityNodeInfo node, int virtualViewId) {
        if (!TextUtils.isEmpty(node.getContentDescription()) && Log.isLoggable(TAG, Log.WARN)) {
            Log.w(TAG, "Overwriting content description to enable rotary scrolling.");
        }
        node.setContentDescription(ROTARY_VERTICALLY_SCROLLABLE);
        if (virtualViewId != HOST_VIEW_ID) {
            return;
        }
        // TODO(b/202420924): The child node of the WebView (which also advertises itself as a
        //  WebView) already appropriately adds ACTION_SCROLL_BACKWARD and ACTION_SCROLL_FORWARD.
        // The topmost WebView does not set those actions. We might be able to adhere more closely
        // to the framework expectations, and additionally not have to manually add these actions
        // if we track focus from the inner WebView instead.
        if (mWebView.canScrollVertically(/* direction= */ -1)) {
            node.addAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_BACKWARD);
        }
        if (mWebView.canScrollVertically(/* direction= */ 1)) {
            node.addAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_FORWARD);
        }
    }
}
