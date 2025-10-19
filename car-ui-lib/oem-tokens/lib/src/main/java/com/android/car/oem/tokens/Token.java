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
package com.android.car.oem.tokens;

import static android.util.TypedValue.TYPE_ATTRIBUTE;
import static android.util.TypedValue.TYPE_NULL;
import static android.util.TypedValue.TYPE_REFERENCE;

import android.content.Context;
import android.content.ContextWrapper;
import android.content.pm.PackageManager;
import android.content.pm.SharedLibraryInfo;
import android.content.res.Resources;
import android.content.res.TypedArray;
import android.util.Log;
import android.util.TypedValue;
import android.view.ContextThemeWrapper;

import androidx.annotation.AttrRes;
import androidx.annotation.ColorInt;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.Px;
import androidx.annotation.StyleRes;
import androidx.annotation.VisibleForTesting;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;

/**
 * Public interface for general CarUi static functions.
 */
public class Token {
    private static final String TOKEN_SHARED_LIBRARY_NAME = "com.android.oem.tokens";
    private static final String TEST_TOKEN_SHARED_LIBRARY_NAME = "com.android.car.oem.tokens.test";
    private static boolean sTestingOverrideEnabled = false;
    private static final String TAG = "Token";

    /**
     * Return the library name for the OEM design token shared library installed on device.
     */
    static String getTokenSharedLibraryName() {
        if (sTestingOverrideEnabled) {
            return TEST_TOKEN_SHARED_LIBRARY_NAME;
        }

        return TOKEN_SHARED_LIBRARY_NAME;
    }

    @VisibleForTesting
    static void setTestingOverride(boolean enabled) {
        sTestingOverrideEnabled = enabled;
    }

    /**
     * Return the package name for the OEM design token shared library installed on device.
     */
    @Nullable
    public static String getTokenSharedLibPackageName(@NonNull PackageManager packageManager) {
        if (sTestingOverrideEnabled) {
            return TEST_TOKEN_SHARED_LIBRARY_NAME;
        }

        List<SharedLibraryInfo> sharedLibs = packageManager.getSharedLibraries(0);
        for (SharedLibraryInfo info : sharedLibs) {
            if (info.getName().equals(getTokenSharedLibraryName())) {
                return info.getDeclaringPackage().getPackageName();
            }
        }

        return null;
    }

    /**
     * Return {@code true} if there is an OEM design token shared library installed on device.
     */
    public static boolean isTokenSharedLibInstalled(@NonNull PackageManager packageManager) {
        String packageName = getTokenSharedLibPackageName(packageManager);
        if (packageName == null) {
            return false;
        }

        try {
            return packageManager.getApplicationInfo(packageName, 0).enabled;
        } catch (PackageManager.NameNotFoundException e) {
            return false;
        }
    }

    /**
     * Return a {@link ContextThemeWrapper} that includes OEM provided values for design tokens.
     * <p>
     * If OEM customized token values are unavailable on the system , the {@code Context} object is
     * returned with library default token values.
     */
    @NonNull
    public static Context createOemStyledContext(@NonNull Context context) {
        if (checkContextIsOemStyled(context)) {
            return context;
        }

        Context tokenContext = new ContextWrapper(context);
        applyOemTokenStyle(tokenContext);

        return tokenContext;
    }

    /**
     * Apply OEM token theme attributes to the provided {@link Context}.
     * <p>
     * If OEM customized token values are unavailable on the system , the {@code Context} object is
     * will have library default token values.
     */
    public static void applyOemTokenStyle(@NonNull Context context) {
        boolean isLightTheme = isLightTheme(context);

        // Apply token default values that are compiled into static library
        if (isLightTheme) {
            context.getTheme().applyStyle(R.style.OemTokensBase_Light, true);
        } else {
            context.getTheme().applyStyle(R.style.OemTokensBase_Dark, true);
        }

        String sharedLibName = getTokenSharedLibraryName();

        int useOemTokenId = context.getResources().getIdentifier("enable_oem_tokens", "bool",
                sharedLibName);
        boolean useOemToken = useOemTokenId != 0 && context.getResources().getBoolean(
                useOemTokenId);

        if (useOemToken) {
            int oemStyleOverride = context.getResources().getIdentifier("OemStyle",
                    "style", sharedLibName);
            if (oemStyleOverride == 0) {
                Log.e(TAG,
                        "Unable to apply OEM design token overrides. Style with "
                                + "name OemStyle not found.");
                return;
            }

            Log.i(TAG, "Overriding OEM tokens with OEM values");
            if (isLightTheme) {
                context.getTheme().applyStyle(R.style.OemTokenSharedLibraryOverlay_Base_Light,
                        true);
            } else {
                context.getTheme().applyStyle(R.style.OemTokenSharedLibraryOverlay_Base_Dark, true);
            }

            context.getTheme().applyStyle(oemStyleOverride, true);

            // Apply framework-res theme overlay
            int themeOverlayNameId = context.getResources().getIdentifier("theme_overlay",
                    "string", sharedLibName);
            if (themeOverlayNameId == 0) {
                return;
            }
            String overlayName = context.getResources().getString(themeOverlayNameId);
            int themeOverlayId = context.getResources().getIdentifier(overlayName, null, null);

            if (themeOverlayId == 0) {
                return;
            }
            context.getTheme().applyStyle(themeOverlayId, true);
        }
    }

    /**
     * Returns true if the system {@code Theme.DeviceDefault.NoActionBar} theme
     * {@code android:isLightTheme} attribute resolve to true
     */
    static boolean isLightTheme(@NonNull Context context) {
        Resources.Theme deviceDefaultTheme = context.getResources().newTheme();
        deviceDefaultTheme.applyStyle(android.R.style.Theme_DeviceDefault_NoActionBar, true);

        TypedValue value = new TypedValue();
        return deviceDefaultTheme.resolveAttribute(android.R.attr.isLightTheme,
                value, true)
                && value.data != 0;
    }

    /**
     * Return the OEM provided corner radius corresponding to the attribute.
     * <p>
     * If OEM customized token corner radius values are unavailable on the system , the library
     * default corner radius token value is returned.
     */
    @Px
    public static float getCornerRadius(@NonNull Context context, @AttrRes int attr) {
        requireContextIsOemStyled(context);
        TypedValue tv = getThemeTypedValue(context, attr);
        return TypedValue.complexToDimension(tv.data,
                context.getResources().getDisplayMetrics());
    }

    /**
     * Return the OEM provided text appearance resource id corresponding to the attribute.
     * <p>
     * If OEM customized token text appearance values are unavailable on the system , the library
     * default text appearance token value is returned.
     */
    @StyleRes
    public static int getTextAppearance(@NonNull Context context, @AttrRes int attr) {
        requireContextIsOemStyled(context);
        TypedValue tv = getThemeTypedValue(context, attr);
        return tv.resourceId;
    }

    /**
     * Return the OEM provided color value corresponding to the attribute.
     * <p>
     * If OEM customized token color values are unavailable on the system, the library default color
     * token value is returned.
     */
    @ColorInt
    public static int getColor(@NonNull Context context, @AttrRes int attr) {
        requireContextIsOemStyled(context);
        TypedValue tv = getThemeTypedValue(context, attr);

        if (tv.resourceId == 0) {
            return tv.data;
        }

        return context.getColor(tv.resourceId);
    }

    private static HashMap<String, String> createTokenMap(@NonNull Context context) {
        context = Token.createOemStyledContext(context.getApplicationContext());

        HashMap<String, String> map = new HashMap<>();
        int[] attrs = Arrays.stream(R.styleable.OemTokens).toArray();

        for (int i : attrs) {
            String id = context.getResources().getResourceEntryName(i);
            TypedValue typedValue = getThemeTypedValue(context, i);
            String value = getAttributeValue(context, typedValue);

            map.put(id, value);
        }

        return map;
    }

    /**
     * Return a mapping of current values for OEM Design tokens.
     */
    public static String dump(@NonNull Context context) {
        return createTokenMap(context).toString();
    }

    /**
     * Return a hashcode for OEM styling of token values meant to be used to determine when OEM
     * styling has changed.
     */
    public static int hashCode(@NonNull Context context) {
        return createTokenMap(context).hashCode();
    }

    private static String getAttributeValue(Context context, TypedValue value) {
        int valueType = value.type;

        if (valueType == TYPE_NULL) {
            return "";
        }

        // Text tokens map to textAppearance styles
        if (valueType == TYPE_REFERENCE) {
            int resId = value.data;
            if (resId != 0) {
                try {
                    int[] attrs = new int[]{android.R.attr.textSize, android.R.attr.textColor};
                    TypedArray array = context.obtainStyledAttributes(resId, attrs);

                    float size = array.getDimension(0, 0f);
                    TypedValue typedColor = new TypedValue();
                    array.getValue(1, typedColor);
                    String color = typedColor.coerceToString().toString();

                    return String.format("Text size: %f Text color %s", size, color);
                } catch (Exception e) {
                    return String.valueOf(resId); // If not a simple resource name
                }
            } else {
                return "Invalid reference";
            }
        }

        // Color and shape tokens can be coerced directly
        return value.coerceToString().toString();
    }

    /**
     * Return {@code true} if there is an available OEM provided value for the design token that
     * corresponds to the attribute.
     */
    public static boolean isOemStyled(Context context, @AttrRes int attr) {
        context = context.getApplicationContext();
        int oemStyleOverride = context.getResources().getIdentifier("OemStyle",
                "style", Token.getTokenSharedLibraryName());
        if (oemStyleOverride == 0) {
            return false;
        }

        TypedArray libAttributes = context.getTheme().obtainStyledAttributes(
                R.style.OemTokenSharedLibraryOverlay,
                new int[]{attr});
        TypedValue tv = new TypedValue();
        if (libAttributes.getType(0) != TYPE_ATTRIBUTE) {
            libAttributes.recycle();
            return false;
        }

        libAttributes.getValue(0, tv);
        int[] attrs = new int[]{tv.data};

        TypedArray sharedLibAttributes = context.obtainStyledAttributes(oemStyleOverride, attrs);
        int type = sharedLibAttributes.getType(0);
        boolean isOemStyled = type != 0;

        libAttributes.recycle();
        sharedLibAttributes.recycle();
        return isOemStyled;
    }

    private static boolean checkContextIsOemStyled(@NonNull Context context) {
        TypedArray attributes = context.getTheme().obtainStyledAttributes(
                new int[]{R.attr.oemTokenOverrideEnabled});
        if (attributes.getType(0) == (TypedValue.TYPE_NULL)) {
            attributes.recycle();
            return false;
        }
        attributes.recycle();
        return true;
    }

    private static void requireContextIsOemStyled(@NonNull Context context) {
        if (!checkContextIsOemStyled(context)) {
            throw new IllegalArgumentException(
                    "Cannot access OEM token values in a context that has not had OEM token "
                            + "styles applied");
        }
    }

    @NonNull
    private static TypedValue getThemeTypedValue(@NonNull Context oemContext, @AttrRes int attr) {
        TypedValue typedValue = new TypedValue();
        oemContext.getTheme().resolveAttribute(attr, typedValue, true);
        return typedValue;
    }
}
