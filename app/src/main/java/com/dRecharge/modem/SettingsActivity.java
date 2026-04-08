package com.dRecharge.modem;

import android.app.AlertDialog;
import android.app.TimePickerDialog;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.widget.SwitchCompat;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.dRecharge.modem.helper.ServiceConfig;
import com.dRecharge.modem.helper.Session;
import com.dRecharge.modem.helper.ThemeManager;
import com.dRecharge.modem.server.ServerConfig;

public class SettingsActivity extends AppCompatActivity {

    private static final String[] ALL_SERVICES = {
            "Grameen",
            "Skitto",
            "Robi",
            "Airtel",
            "Banglalink",
            "Taletalk",
            "bKash-Load",
            "Nagad-Load",
            "bKash-Agent-SIM",
            "bKash-Personal-SIM",
            "Roket-Agent-SIM",
            "Roket-Personal-SIM",
            "Nagad-Agent-SIM",
            "Nagad-Personal-SIM"
    };

    private Session session;
    private LinearLayout serviceListContainer;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        ThemeManager.applyTheme(this);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle("Settings");
        }

        session = new Session(this);
        serviceListContainer = findViewById(R.id.serviceListContainer);

        setupDomainSection();
        buildServiceList();
    }

    private void setupDomainSection() {
        TextView domainValueTv = findViewById(R.id.domainValueTv);
        Button domainEditBtn = findViewById(R.id.domainEditBtn);

        refreshDomainDisplay(domainValueTv);

        domainEditBtn.setOnClickListener(v -> showDomainEditDialog(domainValueTv));
    }

    private void refreshDomainDisplay(TextView domainValueTv) {
        String current = session.getData(Session.API_DOMAIN_LINK);
        if (current != null && !current.isEmpty()) {
            domainValueTv.setText(current);
            domainValueTv.setTextColor(ThemeManager.getThemeColor(this, android.R.attr.textColorPrimary));
        } else {
            domainValueTv.setText("Not configured");
            domainValueTv.setTextColor(ContextCompat.getColor(this, R.color.text_hint));
        }
    }

    private void showDomainEditDialog(TextView domainValueTv) {
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_domain_edit, null);

        EditText domainEt = dialogView.findViewById(R.id.dialogDomainEt);
        Button cancelBtn = dialogView.findViewById(R.id.dialogDomainCancelBtn);
        Button saveBtn = dialogView.findViewById(R.id.dialogDomainSaveBtn);

        String current = session.getData(Session.API_DOMAIN_LINK);
        if (current != null && !current.isEmpty()) {
            domainEt.setText(current);
            domainEt.setSelection(current.length());
        }

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setView(dialogView)
                .setCancelable(true)
                .create();

        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        }

        cancelBtn.setOnClickListener(v -> dialog.dismiss());

        saveBtn.setOnClickListener(v -> {
            String input = domainEt.getText().toString().trim();
            if (input.isEmpty()) {
                Toast.makeText(this, "Please enter a domain", Toast.LENGTH_SHORT).show();
                return;
            }
            String domain = ServerConfig.sanitizeDomain(input);
            if (domain.isEmpty()) {
                Toast.makeText(this, "Invalid domain", Toast.LENGTH_SHORT).show();
                return;
            }
            session.setData(Session.API_DOMAIN_LINK, domain);
            session.setBooleanData(Session.IS_DOMAIN_VALIED, true);
            session.clearSubscriptionState();
            dialog.dismiss();
            restartApp("Domain saved");
        });

        dialog.show();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_settings, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
            return true;
        }
        if (item.getItemId() == R.id.action_interval) {
            showIntervalDialog();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void showIntervalDialog() {
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_interval_edit, null);

        EditText intervalEt      = dialogView.findViewById(R.id.dialogIntervalEt);
        Button saveBtn           = dialogView.findViewById(R.id.dialogIntervalSaveBtn);
        Button exitBtn           = dialogView.findViewById(R.id.dialogExitAppBtn);
        RadioButton rbNeonDark   = dialogView.findViewById(R.id.rbNeonDark);
        RadioButton rbBankingGreen = dialogView.findViewById(R.id.rbBankingGreen);
        RadioButton rbIndigoPro  = dialogView.findViewById(R.id.rbIndigoPro);
        View cardNeonDark        = dialogView.findViewById(R.id.cardNeonDark);
        View cardBankingGreen    = dialogView.findViewById(R.id.cardBankingGreen);
        View cardIndigoPro       = dialogView.findViewById(R.id.cardIndigoPro);

        // Populate interval
        String current = session.getData(Session.TIME_INTERVAL);
        if (current != null && !current.isEmpty()) {
            intervalEt.setText(current);
            intervalEt.setSelection(current.length());
        }

        // Helper: uncheck all then check the chosen one
        // (RadioGroup.getCheckedRadioButtonId() doesn't work for nested RadioButtons)
        Runnable selectNeon    = () -> { rbNeonDark.setChecked(true);    rbBankingGreen.setChecked(false); rbIndigoPro.setChecked(false); };
        Runnable selectGreen   = () -> { rbBankingGreen.setChecked(true); rbNeonDark.setChecked(false);    rbIndigoPro.setChecked(false); };
        Runnable selectIndigo  = () -> { rbIndigoPro.setChecked(true);   rbNeonDark.setChecked(false);     rbBankingGreen.setChecked(false); };

        // Pre-select current theme
        switch (ThemeManager.getSelectedTheme(this)) {
            case ThemeManager.THEME_NEON_DARK:   selectNeon.run();   break;
            case ThemeManager.THEME_INDIGO_PRO:  selectIndigo.run(); break;
            default:                             selectGreen.run();  break;
        }

        // Tap anywhere on a card to select that theme
        cardNeonDark.setOnClickListener(v    -> selectNeon.run());
        cardBankingGreen.setOnClickListener(v -> selectGreen.run());
        cardIndigoPro.setOnClickListener(v   -> selectIndigo.run());
        rbNeonDark.setOnClickListener(v      -> selectNeon.run());
        rbBankingGreen.setOnClickListener(v  -> selectGreen.run());
        rbIndigoPro.setOnClickListener(v     -> selectIndigo.run());

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setView(dialogView)
                .setCancelable(true)
                .create();

        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        }

        exitBtn.setOnClickListener(v -> {
            dialog.dismiss();
            finishAffinity();
        });

        saveBtn.setOnClickListener(v -> {
            // Validate interval
            String input = intervalEt.getText().toString().trim();
            if (input.isEmpty()) {
                Toast.makeText(this, "Please enter a value", Toast.LENGTH_SHORT).show();
                return;
            }
            int seconds;
            try {
                seconds = Integer.parseInt(input);
            } catch (NumberFormatException e) {
                Toast.makeText(this, "Invalid number", Toast.LENGTH_SHORT).show();
                return;
            }
            if (seconds < 5) {
                Toast.makeText(this, "Minimum interval is 5 seconds", Toast.LENGTH_SHORT).show();
                return;
            }
            session.setData(Session.TIME_INTERVAL, String.valueOf(seconds));

            // Save theme — read isChecked() directly since RadioButtons are nested in Cards
            int selectedTheme;
            if (rbNeonDark.isChecked())        selectedTheme = ThemeManager.THEME_NEON_DARK;
            else if (rbIndigoPro.isChecked())  selectedTheme = ThemeManager.THEME_INDIGO_PRO;
            else                               selectedTheme = ThemeManager.THEME_BANKING_GREEN;
            boolean themeChanged = selectedTheme != ThemeManager.getSelectedTheme(this);
            ThemeManager.setSelectedTheme(this, selectedTheme);

            dialog.dismiss();
            String msg = themeChanged
                    ? "Theme: " + ThemeManager.THEME_NAMES[selectedTheme] + " · Interval: " + seconds + "s"
                    : "Interval set to " + seconds + "s";
            restartApp(msg);
        });

        dialog.show();
    }

    private void buildServiceList() {
        serviceListContainer.removeAllViews();
        for (String name : ALL_SERVICES) {
            ServiceConfig cfg = session.getServiceConfig(name);
            View row = LayoutInflater.from(this).inflate(R.layout.item_service, serviceListContainer, false);

            ((TextView) row.findViewById(R.id.svcName)).setText(name);

            TextView pinTv = row.findViewById(R.id.svcPin);
            if (cfg.pin == null || cfg.pin.isEmpty()) {
                pinTv.setText("No PIN");
                pinTv.setTextColor(ContextCompat.getColor(this, R.color.inactive_grey));
            } else {
                pinTv.setText("****");
                pinTv.setTextColor(ContextCompat.getColor(this, R.color.active_green));
            }

            ((TextView) row.findViewById(R.id.svcSim)).setText("SIM " + cfg.sim);

            TextView statusTv = row.findViewById(R.id.svcStatus);
            if (cfg.active) {
                statusTv.setText("Active");
                statusTv.setTextColor(ContextCompat.getColor(this, R.color.active_green));
            } else {
                statusTv.setText("Inactive");
                statusTv.setTextColor(ContextCompat.getColor(this, R.color.inactive_grey));
            }

            // Display USSD code (user's custom or default)
            TextView ussdCodeTv = row.findViewById(R.id.svcUssdCode);
            String ussdCode = (cfg.dialCode1 != null && !cfg.dialCode1.isEmpty()) 
                    ? cfg.dialCode1 
                    : getDefaultDialCode1(name);
            ussdCodeTv.setText(ussdCode);

            TextView scheduleTv = row.findViewById(R.id.svcSchedule);
            if (cfg.scheduleEnabled && !cfg.scheduleStart.isEmpty() && !cfg.scheduleEnd.isEmpty()) {
                scheduleTv.setText("⏰  " + cfg.scheduleStart + " – " + cfg.scheduleEnd);
                scheduleTv.setVisibility(View.VISIBLE);
            } else {
                scheduleTv.setVisibility(View.GONE);
            }

            row.findViewById(R.id.svcEdit).setOnClickListener(v -> showEditDialog(name));

            serviceListContainer.addView(row);
        }
    }

    private void showEditDialog(String name) {
        ServiceConfig current = session.getServiceConfig(name);

        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_service_edit, null);

        TextView title          = dialogView.findViewById(R.id.dialogServiceTitle);
        RadioButton activeRb   = dialogView.findViewById(R.id.dialogActiveRb);
        RadioButton inactiveRb = dialogView.findViewById(R.id.dialogInactiveRb);
        RadioButton sim1Rb     = dialogView.findViewById(R.id.dialogSim1Rb);
        RadioButton sim2Rb     = dialogView.findViewById(R.id.dialogSim2Rb);
        EditText numberEt      = dialogView.findViewById(R.id.dialogNumberEt);
        EditText pinEt         = dialogView.findViewById(R.id.dialogPinEt);
        EditText dialCode1Et   = dialogView.findViewById(R.id.dialogDialCode1Et);
        EditText dialCode0Et   = dialogView.findViewById(R.id.dialogDialCode0Et);
        SwitchCompat scheduleSw       = dialogView.findViewById(R.id.dialogScheduleSw);
        LinearLayout scheduleTimeCont = dialogView.findViewById(R.id.scheduleTimeContainer);
        TextView scheduleStartTv      = dialogView.findViewById(R.id.dialogScheduleStartTv);
        TextView scheduleEndTv        = dialogView.findViewById(R.id.dialogScheduleEndTv);
        Button cancelBtn       = dialogView.findViewById(R.id.dialogCancelBtn);
        Button updateBtn       = dialogView.findViewById(R.id.dialogUpdateBtn);

        title.setText(name);

        if (current.active) activeRb.setChecked(true);
        else inactiveRb.setChecked(true);

        if (current.sim == 2) sim2Rb.setChecked(true);
        else sim1Rb.setChecked(true);

        numberEt.setText(current.number);
        pinEt.setText(current.pin);

        String defaultCode1 = getDefaultDialCode1(name);
        String defaultCode0 = getDefaultDialCode0(name);

        dialCode1Et.setHint(defaultCode1);
        dialCode0Et.setHint(defaultCode0.isEmpty() ? "(same as Type 1 if blank)" : defaultCode0);

        dialCode1Et.setText(current.dialCode1 != null && !current.dialCode1.isEmpty() ? current.dialCode1 : defaultCode1);
        if (current.dialCode0 != null && !current.dialCode0.isEmpty()) {
            dialCode0Et.setText(current.dialCode0);
        } else {
            dialCode0Et.setText(defaultCode0);
        }

        // Schedule
        scheduleSw.setChecked(current.scheduleEnabled);
        scheduleTimeCont.setVisibility(current.scheduleEnabled ? View.VISIBLE : View.GONE);
        if (!current.scheduleStart.isEmpty()) scheduleStartTv.setText(current.scheduleStart);
        if (!current.scheduleEnd.isEmpty())   scheduleEndTv.setText(current.scheduleEnd);

        scheduleSw.setOnCheckedChangeListener((btn, checked) ->
                scheduleTimeCont.setVisibility(checked ? View.VISIBLE : View.GONE));

        scheduleStartTv.setOnClickListener(v -> showTimePicker(scheduleStartTv));
        scheduleEndTv.setOnClickListener(v -> showTimePicker(scheduleEndTv));

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setView(dialogView)
                .setCancelable(true)
                .create();

        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        }

        cancelBtn.setOnClickListener(v -> dialog.dismiss());

        updateBtn.setOnClickListener(v -> {
            ServiceConfig updated = new ServiceConfig(name);
            updated.active          = activeRb.isChecked();
            updated.sim             = sim1Rb.isChecked() ? 1 : 2;
            updated.number          = numberEt.getText().toString().trim();
            updated.pin             = pinEt.getText().toString().trim();
            updated.dialCode1       = dialCode1Et.getText().toString().trim();
            updated.dialCode0       = dialCode0Et.getText().toString().trim();
            updated.scheduleEnabled = scheduleSw.isChecked();
            updated.scheduleStart   = scheduleStartTv.getText().toString().trim();
            updated.scheduleEnd     = scheduleEndTv.getText().toString().trim();
            if (updated.scheduleEnabled
                    && (updated.scheduleStart.isEmpty() || updated.scheduleEnd.isEmpty())) {
                Toast.makeText(this, "Please set both Enable At and Disable At times", Toast.LENGTH_SHORT).show();
                return;
            }
            session.saveServiceConfig(updated);
            dialog.dismiss();
            restartApp("Settings saved");
        });

        dialog.show();
    }

    /** Returns the default USSD template hint for type-1 (commission/default) dial. */
    private String getDefaultDialCode1(String service) {
        switch (service) {
            case "Grameen":           return "*444*{PHONE}*{AMOUNT}*0*{PIN}#";
            case "Skitto":            return "*666*{PHONE}*{AMOUNT}*{PIN}#";
            case "Robi":              return "*8383*2*{PHONE}*{AMOUNT}*{PIN}#";
            case "Airtel":            return "*444*1*{PHONE}*{AMOUNT}*{PIN}#";
            case "Banglalink":        return "*555*{PHONE}*{AMOUNT}*0*{PIN}#";
            case "Taletalk":          return "*250*{PHONE}*{AMOUNT}*{PIN}#";
            case "bKash-Load":        return "*247*1*{PHONE}*{AMOUNT}*{PIN}#";
            case "bKash-Agent-SIM":   return "*247*2*{PHONE}*{AMOUNT}*{PIN}#";
            case "bKash-Personal-SIM": return "*247*1*{PHONE}*{AMOUNT}*{PIN}#";
            case "Roket-Agent-SIM":   return "*322*2*{PHONE}*{AMOUNT}*{PIN}#";
            case "Roket-Personal-SIM": return "*322*1*{PHONE}*{AMOUNT}*{PIN}#";
            case "Nagad-Load":        return "*167*1*{PHONE}*{AMOUNT}*{PIN}#";
            case "Nagad-Agent-SIM":   return "*167*2*{PHONE}*{AMOUNT}*{PIN}#";
            case "Nagad-Personal-SIM": return "*167*1*{PHONE}*{AMOUNT}*{PIN}#";
            default:                  return "{PHONE}*{AMOUNT}*{PIN}#";
        }
    }

    /** Opens a 12-hour (AM/PM) TimePickerDialog and writes the chosen time into targetTv. */
    private void showTimePicker(TextView targetTv) {
        int initHour = 0, initMin = 0;
        String existing = targetTv.getText().toString().trim();
        if (!existing.isEmpty()) {
            try {
                java.text.SimpleDateFormat sdf =
                        new java.text.SimpleDateFormat("hh:mm a", java.util.Locale.US);
                java.util.Calendar cal = java.util.Calendar.getInstance();
                cal.setTime(sdf.parse(existing));
                initHour = cal.get(java.util.Calendar.HOUR_OF_DAY);
                initMin  = cal.get(java.util.Calendar.MINUTE);
            } catch (Exception ignored) {}
        }
        new TimePickerDialog(this, (view, hour, minute) -> {
            // TimePickerDialog always returns hour in 0-23 internally; convert to AM/PM display
            String amPm = hour < 12 ? "AM" : "PM";
            int h12 = hour % 12;
            if (h12 == 0) h12 = 12;
            String time = String.format(java.util.Locale.US, "%02d:%02d %s", h12, minute, amPm);
            targetTv.setText(time);
        }, initHour, initMin, false).show();
    }

    private void restartApp(String toastMessage) {
        Toast.makeText(this, toastMessage + " — restarting…", Toast.LENGTH_SHORT).show();
        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            Intent intent = new Intent(getApplicationContext(), MainActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(intent);
            finishAffinity();
        }, 700);
    }

    /** Returns the default USSD template hint for type-0 (prepaid) dial. */
    private String getDefaultDialCode0(String service) {
        switch (service) {
            case "Robi":       return "*8383*3*{PHONE}*{AMOUNT}*{PIN}#";
            case "Airtel":     return "*444*10*{PHONE}*{AMOUNT}*{PIN}#";
            case "Banglalink": return "*566*{PHONE}*{AMOUNT}*{PIN}#";
            default:           return "";
        }
    }
}
