package com.dRecharge.modem.ussd;

import android.accessibilityservice.AccessibilityServiceInfo;
import android.content.ComponentName;
import android.content.Context;
import android.provider.Settings;
import android.text.TextUtils;
import android.view.accessibility.AccessibilityManager;

import java.util.List;

/**
 * Utility class for reliable detection of the app's Accessibility Service status.
 * <p>
 * Provides three methods:
 * <ul>
 *   <li>{@link #isAccessibilityServiceEnabled(Context, Class)} – authoritative check using the
 *       system Settings string.</li>
 *   <li>{@link #isAccessibilityEnabledFallback(Context)} – safety layer using the
 *       AccessibilityManager API.</li>
 *   <li>{@link #isAccessibilityFullyEnabled(Context, Class)} – combined check used by the app.</li>
 * </ul>
 * All methods are safe to call on Android 8 (API 26) and above.
 */
public final class AccessibilityUtils {

    private AccessibilityUtils() {
        // Prevent instantiation.
    }

    /**
     * Checks whether the specific Accessibility Service defined by {@code serviceClass}
     * is enabled in the system settings.
     */
    public static boolean isAccessibilityServiceEnabled(Context context, Class<?> serviceClass) {
        ComponentName expected = new ComponentName(context, serviceClass);

        String enabled = Settings.Secure.getString(
                context.getContentResolver(),
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
        if (enabled == null) {
            return false;
        }

        // The setting stores a colon‑separated list of flattened ComponentName strings.
        String[] services = enabled.split(":");
        for (String s : services) {
            ComponentName cn = ComponentName.unflattenFromString(s);
            if (cn != null && cn.equals(expected)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Fallback check using {@link AccessibilityManager#getEnabledAccessibilityServiceList(int)}.
     * This method is less strict but works on devices where the Settings string may be malformed.
     */
    public static boolean isAccessibilityEnabledFallback(Context context) {
        AccessibilityManager am = (AccessibilityManager) context.getSystemService(Context.ACCESSIBILITY_SERVICE);
        if (am == null) {
            return false;
        }
        List<AccessibilityServiceInfo> enabled = am.getEnabledAccessibilityServiceList(
                AccessibilityServiceInfo.FEEDBACK_ALL_MASK);
        for (AccessibilityServiceInfo info : enabled) {
            if (info != null && info.getResolveInfo() != null &&
                    info.getResolveInfo().serviceInfo != null &&
                    TextUtils.equals(info.getResolveInfo().serviceInfo.packageName, context.getPackageName())) {
                return true;
            }
        }
        return false;
    }

    /**
     * Combined check – returns {@code true} if either authoritative source confirms the service
     * is active. {@code USSDService.isServiceConnected()} is intentionally NOT included here:
     * when the user disables the service in settings, both authoritative sources update
     * immediately, but {@code serviceInstance} remains non-null until {@code onDestroy()} fires.
     * Including it would mask the disabled state and prevent the app from redirecting the user
     * back to the permission setup screen.
     */
    public static boolean isAccessibilityFullyEnabled(Context context, Class<?> serviceClass) {
        return isAccessibilityServiceEnabled(context, serviceClass)
                || isAccessibilityEnabledFallback(context);
    }
}
