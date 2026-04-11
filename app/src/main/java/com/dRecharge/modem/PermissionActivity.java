package com.dRecharge.modem;

import android.Manifest;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.PowerManager;
import android.provider.Settings;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import com.dRecharge.modem.helper.AppPermissionSupport;
import com.dRecharge.modem.helper.ThemeManager;
import com.dRecharge.modem.ussd.USSDService;

public class PermissionActivity extends AppCompatActivity {

    private static final int RC_PERMISSIONS = 201;

    private ImageView step1Icon, stepRestrictedIcon, step2Icon, step3Icon, step4Icon;
    private Button step1Btn, stepRestrictedBtn, step2Btn, step3Btn, step4Btn, continueBtn;
    private View stepRestrictedCard;

    // Tracks whether user clicked the unlock button and went to Settings
    private boolean restrictedUnlockPending = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        ThemeManager.applyTheme(this);
        super.onCreate(savedInstanceState);

        if (getSupportActionBar() != null) getSupportActionBar().hide();

        if (allSetupComplete()) {
            goToMain();
            return;
        }

        setContentView(R.layout.activity_permission_setup);

        step1Icon         = findViewById(R.id.step1Icon);
        step1Btn          = findViewById(R.id.step1Btn);
        stepRestrictedCard = findViewById(R.id.stepRestrictedCard);
        stepRestrictedIcon = findViewById(R.id.stepRestrictedIcon);
        stepRestrictedBtn  = findViewById(R.id.stepRestrictedBtn);
        step2Icon         = findViewById(R.id.step2Icon);
        step2Btn          = findViewById(R.id.step2Btn);
        step3Icon         = findViewById(R.id.step3Icon);
        step3Btn          = findViewById(R.id.step3Btn);
        step4Icon         = findViewById(R.id.step4Icon);
        step4Btn          = findViewById(R.id.step4Btn);
        continueBtn       = findViewById(R.id.continueBtn);

        // Hide restricted settings step on Android < 13 (not applicable)
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            stepRestrictedCard.setVisibility(View.GONE);
        }

        step1Btn.setOnClickListener(v -> requestRuntimePermissions());

        stepRestrictedBtn.setOnClickListener(v -> {
            restrictedUnlockPending = true;
            Toast.makeText(this,
                "Find \"dRecharge\" → tap ⋮ (top-right) → \"Allow restricted settings\"",
                Toast.LENGTH_LONG).show();
            startActivity(new Intent(Settings.ACTION_MANAGE_ALL_APPLICATIONS_SETTINGS));
        });

        step2Btn.setOnClickListener(v -> {
            Toast.makeText(this,
                "Find \"dRecharge\" in the list and toggle it ON", Toast.LENGTH_LONG).show();
            startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));
        });

        step3Btn.setOnClickListener(v -> {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                startActivity(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:" + getPackageName())));
            }
        });

        step4Btn.setOnClickListener(v -> {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                startActivity(new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                        Uri.parse("package:" + getPackageName())));
            }
        });

        continueBtn.setOnClickListener(v -> {
            if (criticalPermissionsGranted()) {
                goToMain();
            } else {
                Toast.makeText(this,
                    "Please grant App Permissions and enable Accessibility Service first",
                    Toast.LENGTH_LONG).show();
            }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (continueBtn != null) {
            if (restrictedUnlockPending) {
                restrictedUnlockPending = false;
                if (!isRestrictedSettingsUnlocked()) {
                    Toast.makeText(this,
                            "Restricted settings is still locked. Please finish that step to continue.",
                            Toast.LENGTH_LONG).show();
                }
            }
            updateUI();
            // Auto-proceed if all required steps are now complete — the user shouldn't
            // have to tap "Continue" after returning from the Accessibility Settings screen.
            if (allSetupComplete()) {
                goToMain();
            }
        }
    }

    private void updateUI() {
        boolean runtime      = hasRuntimePermissions();
        boolean restricted   = isRestrictedSettingsUnlocked();
        boolean accessibility = isAccessibilityEnabled();
        boolean overlay      = hasOverlayPermission();
        boolean battery      = isBatteryOptimizationIgnored();

        applyStepState(step1Icon, step1Btn, runtime);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            applyStepState(stepRestrictedIcon, stepRestrictedBtn, restricted);
        }
        applyStepState(step2Icon, step2Btn, accessibility);
        applyStepState(step3Icon, step3Btn, overlay);
        applyStepState(step4Icon, step4Btn, battery);

        boolean critical = criticalPermissionsGranted();
        continueBtn.setEnabled(critical);
        continueBtn.setAlpha(critical ? 1.0f : 0.45f);
    }

    private void applyStepState(ImageView icon, Button btn, boolean granted) {
        if (granted) {
            icon.setImageResource(R.drawable.ic_check_circle);
            icon.setColorFilter(Color.parseColor("#22C55E"));
            btn.setText("Granted");
            btn.setEnabled(false);
            btn.setAlpha(0.5f);
        } else {
            icon.setImageResource(R.drawable.ic_pending_circle);
            icon.setColorFilter(Color.parseColor("#94A3B8"));
            btn.setEnabled(true);
            btn.setAlpha(1.0f);
        }
    }

    private void requestRuntimePermissions() {
        java.util.List<String> needed = AppPermissionSupport.getMissingRuntimePermissions(this);
        if (needed.isEmpty()) {
            Toast.makeText(this, "All app permissions already granted", Toast.LENGTH_SHORT).show();
        } else {
            ActivityCompat.requestPermissions(this, needed.toArray(new String[0]), RC_PERMISSIONS);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == RC_PERMISSIONS) {
            updateUI();
        }
    }

    // ── Permission checks ──

    private boolean hasRuntimePermissions() {
        return AppPermissionSupport.hasAllRuntimePermissions(this);
    }

    private boolean isAccessibilityEnabled() {
        return com.dRecharge.modem.ussd.AccessibilityUtils.isAccessibilityFullyEnabled(this, USSDService.class);
    }

    private boolean hasOverlayPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            return Settings.canDrawOverlays(this);
        }
        return true;
    }

    private boolean isBatteryOptimizationIgnored() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
            return pm != null && pm.isIgnoringBatteryOptimizations(getPackageName());
        }
        return true;
    }

    private boolean isRestrictedSettingsUnlocked() {
        // Primary check via AppOps — works on stock Android 13+.
        if (AppPermissionSupport.isRestrictedSettingsUnlocked(this)) return true;
        // Fallback: Android 13+ will not allow an accessibility service to stay enabled
        // on a restricted app. So if accessibility is already running, the grant must
        // have been accepted at some point — even on ROMs where the AppOps query is
        // unreliable (MIUI, ColorOS, etc.).
        return isAccessibilityEnabled();
    }

    private boolean criticalPermissionsGranted() {
        return hasRuntimePermissions()
                && isRestrictedSettingsUnlocked()
                && isAccessibilityEnabled();
    }

    private boolean allSetupComplete() {
        return hasRuntimePermissions()
            && isRestrictedSettingsUnlocked()
            && isAccessibilityEnabled()
            && hasOverlayPermission()
            && isBatteryOptimizationIgnored();
    }

    private void goToMain() {
        startActivity(new Intent(this, MainActivity.class));
        finish();
    }
}
