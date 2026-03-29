package com.dRecharge.modem;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.PowerManager;
import android.provider.Settings;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.dRecharge.modem.ussd.USSDService;

import java.util.ArrayList;
import java.util.List;

public class PermissionActivity extends AppCompatActivity {

    private static final int RC_PERMISSIONS = 201;

    private ImageView step1Icon, step2Icon, step3Icon, step4Icon;
    private Button step1Btn, step2Btn, step3Btn, step4Btn, continueBtn;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (getSupportActionBar() != null) getSupportActionBar().hide();

        if (allSetupComplete()) {
            goToMain();
            return;
        }

        setContentView(R.layout.activity_permission_setup);

        step1Icon = findViewById(R.id.step1Icon);
        step1Btn  = findViewById(R.id.step1Btn);
        step2Icon = findViewById(R.id.step2Icon);
        step2Btn  = findViewById(R.id.step2Btn);
        step3Icon = findViewById(R.id.step3Icon);
        step3Btn  = findViewById(R.id.step3Btn);
        step4Icon = findViewById(R.id.step4Icon);
        step4Btn  = findViewById(R.id.step4Btn);
        continueBtn = findViewById(R.id.continueBtn);

        step1Btn.setOnClickListener(v -> requestRuntimePermissions());

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
            updateUI();
        }
    }

    private void updateUI() {
        boolean runtime      = hasRuntimePermissions();
        boolean accessibility = isAccessibilityEnabled();
        boolean overlay      = hasOverlayPermission();
        boolean battery      = isBatteryOptimizationIgnored();

        applyStepState(step1Icon, step1Btn, runtime);
        applyStepState(step2Icon, step2Btn, accessibility);
        applyStepState(step3Icon, step3Btn, overlay);
        applyStepState(step4Icon, step4Btn, battery);

        boolean critical = runtime && accessibility;
        continueBtn.setEnabled(critical);
        continueBtn.setAlpha(critical ? 1.0f : 0.45f);
    }

    private void applyStepState(ImageView icon, Button btn, boolean granted) {
        if (granted) {
            icon.setImageResource(R.drawable.ic_check_circle);
            icon.setColorFilter(Color.parseColor("#10B981"));
            btn.setText("Granted");
            btn.setEnabled(false);
            btn.setAlpha(0.5f);
        } else {
            icon.setImageResource(R.drawable.ic_pending_circle);
            icon.setColorFilter(Color.parseColor("#9CA3AF"));
            btn.setEnabled(true);
            btn.setAlpha(1.0f);
        }
    }

    private void requestRuntimePermissions() {
        List<String> needed = new ArrayList<>();
        String[] required = {
            Manifest.permission.CALL_PHONE,
            Manifest.permission.READ_PHONE_STATE,
            Manifest.permission.RECEIVE_SMS,
            Manifest.permission.READ_SMS,
            Manifest.permission.READ_CONTACTS,
            Manifest.permission.GET_ACCOUNTS,
        };
        for (String p : required) {
            if (ContextCompat.checkSelfPermission(this, p) != PackageManager.PERMISSION_GRANTED) {
                needed.add(p);
            }
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                    != PackageManager.PERMISSION_GRANTED) {
                needed.add(Manifest.permission.POST_NOTIFICATIONS);
            }
        }
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
        String[] required = {
            Manifest.permission.CALL_PHONE,
            Manifest.permission.READ_PHONE_STATE,
            Manifest.permission.RECEIVE_SMS,
            Manifest.permission.READ_SMS,
            Manifest.permission.READ_CONTACTS,
            Manifest.permission.GET_ACCOUNTS,
        };
        for (String p : required) {
            if (ContextCompat.checkSelfPermission(this, p) != PackageManager.PERMISSION_GRANTED) {
                return false;
            }
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            return ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                    == PackageManager.PERMISSION_GRANTED;
        }
        return true;
    }

    private boolean isAccessibilityEnabled() {
        String pref = Settings.Secure.getString(
            getContentResolver(), Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
        return pref != null && pref.contains(
            getPackageName() + "/" + USSDService.class.getName());
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

    private boolean criticalPermissionsGranted() {
        return hasRuntimePermissions() && isAccessibilityEnabled();
    }

    private boolean allSetupComplete() {
        return hasRuntimePermissions()
            && isAccessibilityEnabled()
            && hasOverlayPermission()
            && isBatteryOptimizationIgnored();
    }

    private void goToMain() {
        startActivity(new Intent(this, MainActivity.class));
        finish();
    }
}
