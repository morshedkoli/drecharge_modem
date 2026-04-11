package com.dRecharge.modem.helper;

import android.Manifest;
import android.app.AppOpsManager;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;

import androidx.core.content.ContextCompat;

import java.util.ArrayList;
import java.util.List;

public final class AppPermissionSupport {
    private AppPermissionSupport() {
    }

    private static final String[] CORE_RUNTIME_PERMISSIONS = {
            Manifest.permission.CALL_PHONE,
            Manifest.permission.READ_PHONE_STATE,
            Manifest.permission.RECEIVE_SMS
    };

    public static String[] getRequiredRuntimePermissions() {
        return CORE_RUNTIME_PERMISSIONS.clone();
    }

    public static List<String> getMissingRuntimePermissions(Context context) {
        List<String> missing = new ArrayList<>();
        for (String permission : CORE_RUNTIME_PERMISSIONS) {
            if (ContextCompat.checkSelfPermission(context, permission) != PackageManager.PERMISSION_GRANTED) {
                missing.add(permission);
            }
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                && ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
            missing.add(Manifest.permission.POST_NOTIFICATIONS);
        }
        return missing;
    }

    public static boolean hasAllRuntimePermissions(Context context) {
        return getMissingRuntimePermissions(context).isEmpty();
    }

    public static boolean isRestrictedSettingsUnlocked(Context context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            return true;
        }

        try {
            AppOpsManager appOps = (AppOpsManager) context.getSystemService(Context.APP_OPS_SERVICE);
            if (appOps == null) {
                // Cannot check — treat as locked so the user is guided through the step.
                return false;
            }
            int mode = appOps.checkOpNoThrow(
                    "android:access_restricted_settings",
                    android.os.Process.myUid(),
                    context.getPackageName());
            // Only MODE_ALLOWED means the user explicitly granted restricted settings access.
            // MODE_DEFAULT = system default policy = DENY for sideloaded apps.
            return mode == AppOpsManager.MODE_ALLOWED;
        } catch (IllegalArgumentException ignored) {
            // The op string is not recognised on this device/firmware — the restriction
            // does not apply here, so treat as unlocked.
            return true;
        } catch (Exception ignored) {
            // Any other failure: be strict and show the step as locked so the user
            // can attempt to unlock it rather than silently skipping.
            return false;
        }
    }
}
