package com.dRecharge.modem.helper;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.util.TypedValue;

import com.dRecharge.modem.R;

public class ThemeManager {
    private static final String PREFS_NAME = "dRechargeThemePrefs";
    private static final String KEY_THEME = "selected_theme";

    public static final int THEME_NEON_DARK = 0;      // Cyber Dark - Neon Cyan
    public static final int THEME_BANKING_GREEN = 1;   // Banking Green - bKash/Nagad style
    public static final int THEME_INDIGO_PRO = 2;      // Indigo Pro - Stripe/PayPal style

    public static final String[] THEME_NAMES = {
        "Cyber Dark",
        "Cash Green",
        "Indigo Pro"
    };

    public static final String[] THEME_DESCRIPTIONS = {
        "Modern crypto-banking dark theme with neon cyan",
        "Trusted banking style — bKash & Nagad vibe",
        "Startup fintech — Stripe & PayPal style"
    };

    public static int getSelectedTheme(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        return prefs.getInt(KEY_THEME, THEME_BANKING_GREEN); // Default to Banking Green
    }

    public static void setSelectedTheme(Context context, int theme) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        prefs.edit().putInt(KEY_THEME, theme).apply();
    }

    public static void applyTheme(Activity activity) {
        int theme = getSelectedTheme(activity);
        switch (theme) {
            case THEME_NEON_DARK:
                activity.setTheme(R.style.AppTheme_NeonDark);
                break;
            case THEME_INDIGO_PRO:
                activity.setTheme(R.style.AppTheme_IndigoPro);
                break;
            case THEME_BANKING_GREEN:
            default:
                activity.setTheme(R.style.AppTheme_BankingGreen);
                break;
        }
    }

    public static int getThemeStyleRes(Context context) {
        int theme = getSelectedTheme(context);
        switch (theme) {
            case THEME_NEON_DARK:
                return R.style.AppTheme_NeonDark;
            case THEME_INDIGO_PRO:
                return R.style.AppTheme_IndigoPro;
            case THEME_BANKING_GREEN:
            default:
                return R.style.AppTheme_BankingGreen;
        }
    }

    public static boolean isDarkTheme(Context context) {
        int theme = getSelectedTheme(context);
        return theme == THEME_NEON_DARK || theme == THEME_INDIGO_PRO;
    }

    /** Resolve a theme attribute (e.g. R.attr.bg_screen) to its actual color value. */
    public static int getThemeColor(Context context, int attrRes) {
        TypedValue typedValue = new TypedValue();
        context.getTheme().resolveAttribute(attrRes, typedValue, true);
        return typedValue.data;
    }
}
