package com.dRecharge.modem;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.telephony.SubscriptionInfo;
import android.telephony.SubscriptionManager;
import android.text.TextUtils;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import com.dRecharge.modem.helper.ServiceCatalog;
import com.dRecharge.modem.helper.Session;
import com.dRecharge.modem.server.ServerConfig;

import java.util.List;

public class SettingsActivity extends AppCompatActivity {
    private Session session;

    private EditText domainEt;
    private EditText intervalEt;

    private LinearLayout sim1Card;
    private LinearLayout sim2Card;
    private TextView sim1Title;
    private TextView sim2Title;
    private Switch sim1EnabledSw;
    private Switch sim2EnabledSw;
    private Spinner sim1ServiceSp;
    private Spinner sim2ServiceSp;
    private EditText sim1NumberEt;
    private EditText sim2NumberEt;
    private EditText sim1PinEt;
    private EditText sim2PinEt;
    private EditText sim1MinBalanceEt;
    private EditText sim2MinBalanceEt;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        session = new Session(this);

        bindViews();
        setupServiceAdapters();
        loadSubscriptionInfo();
        loadSavedValues();
        setupActions();
    }

    private void bindViews() {
        domainEt = findViewById(R.id.settingsDomainEt);
        intervalEt = findViewById(R.id.settingsIntervalEt);
        sim1Card = findViewById(R.id.settingsSim1Card);
        sim2Card = findViewById(R.id.settingsSim2Card);
        sim1Title = findViewById(R.id.settingsSim1Title);
        sim2Title = findViewById(R.id.settingsSim2Title);
        sim1EnabledSw = findViewById(R.id.settingsSim1EnabledSw);
        sim2EnabledSw = findViewById(R.id.settingsSim2EnabledSw);
        sim1ServiceSp = findViewById(R.id.settingsSim1ServiceSp);
        sim2ServiceSp = findViewById(R.id.settingsSim2ServiceSp);
        sim1NumberEt = findViewById(R.id.settingsSim1NumberEt);
        sim2NumberEt = findViewById(R.id.settingsSim2NumberEt);
        sim1PinEt = findViewById(R.id.settingsSim1PinEt);
        sim2PinEt = findViewById(R.id.settingsSim2PinEt);
        sim1MinBalanceEt = findViewById(R.id.settingsSim1MinBalanceEt);
        sim2MinBalanceEt = findViewById(R.id.settingsSim2MinBalanceEt);
    }

    private void setupServiceAdapters() {
        ArrayAdapter<String> adapter = new ArrayAdapter<>(
                this,
                android.R.layout.simple_spinner_item,
                ServiceCatalog.getServices()
        );
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        sim1ServiceSp.setAdapter(adapter);
        sim2ServiceSp.setAdapter(adapter);
    }

    private void loadSubscriptionInfo() {
        SubscriptionManager subscriptionManager =
                (SubscriptionManager) getSystemService(TELEPHONY_SUBSCRIPTION_SERVICE);

        if (subscriptionManager == null) {
            sim2Card.setVisibility(View.GONE);
            return;
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M
                && ActivityCompat.checkSelfPermission(this, Manifest.permission.READ_PHONE_STATE)
                != PackageManager.PERMISSION_GRANTED) {
            sim2Card.setVisibility(View.GONE);
            return;
        }

        List<SubscriptionInfo> subscriptions = subscriptionManager.getActiveSubscriptionInfoList();
        if (subscriptions == null || subscriptions.isEmpty()) {
            sim2Card.setVisibility(View.GONE);
            return;
        }

        SubscriptionInfo sim1Info = subscriptions.get(0);
        session.setData(Session.SIM1_ID, String.valueOf(sim1Info.getSimSlotIndex()));
        sim1Title.setText(buildSimTitle("SIM 1", sim1Info));
        if (!TextUtils.isEmpty(sim1Info.getNumber()) && TextUtils.isEmpty(session.getData(Session.SIM1_NUMBER))) {
            sim1NumberEt.setText(normalizeBdPhone(sim1Info.getNumber()));
        }

        if (subscriptions.size() > 1) {
            SubscriptionInfo sim2Info = subscriptions.get(1);
            session.setData(Session.SIM2_ID, String.valueOf(sim2Info.getSimSlotIndex()));
            sim2Title.setText(buildSimTitle("SIM 2", sim2Info));
            if (!TextUtils.isEmpty(sim2Info.getNumber()) && TextUtils.isEmpty(session.getData(Session.SIM2_NUMBER))) {
                sim2NumberEt.setText(normalizeBdPhone(sim2Info.getNumber()));
            }
            sim2Card.setVisibility(View.VISIBLE);
        } else {
            sim2Card.setVisibility(View.GONE);
        }
    }

    private void loadSavedValues() {
        domainEt.setText(session.getData(Session.API_DOMAIN_LINK));
        intervalEt.setText(defaultIfEmpty(session.getData(Session.TIME_INTERVAL), "30"));

        sim1EnabledSw.setChecked(session.getBooleanData(Session.SIM1_ENABLED));
        sim1NumberEt.setText(session.getData(Session.SIM1_NUMBER));
        sim1PinEt.setText(session.getData(Session.SIM1_PIN));
        sim1MinBalanceEt.setText(defaultIfEmpty(session.getData(Session.SIM1_MIN_BAL), "0"));
        sim1ServiceSp.setSelection(ServiceCatalog.indexOf(session.getData(Session.SIM1_SERVICE_NAME)));

        sim2EnabledSw.setChecked(session.getBooleanData(Session.SIM2_ENABLED));
        sim2NumberEt.setText(session.getData(Session.SIM2_NUMBER));
        sim2PinEt.setText(session.getData(Session.SIM2_PIN));
        sim2MinBalanceEt.setText(defaultIfEmpty(session.getData(Session.SIM2_MIN_BAL), "0"));
        sim2ServiceSp.setSelection(ServiceCatalog.indexOf(session.getData(Session.SIM2_SERVICE_NAME)));
    }

    private void setupActions() {
        Button cancelBtn = findViewById(R.id.settingsCancelBtn);
        Button saveBtn = findViewById(R.id.settingsSaveBtn);

        sim1EnabledSw.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (isChecked && (TextUtils.isEmpty(sim1NumberEt.getText()) || TextUtils.isEmpty(sim1PinEt.getText()))) {
                Toast.makeText(this, "SIM 1 needs a number and PIN before enabling.", Toast.LENGTH_SHORT).show();
            }
        });

        sim2EnabledSw.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (isChecked && sim2Card.getVisibility() == View.VISIBLE
                    && (TextUtils.isEmpty(sim2NumberEt.getText()) || TextUtils.isEmpty(sim2PinEt.getText()))) {
                Toast.makeText(this, "SIM 2 needs a number and PIN before enabling.", Toast.LENGTH_SHORT).show();
            }
        });

        cancelBtn.setOnClickListener(v -> finish());
        saveBtn.setOnClickListener(v -> saveSettings());
    }

    private void saveSettings() {
        String domain = ServerConfig.sanitizeDomain(domainEt.getText().toString());
        String interval = defaultIfEmpty(intervalEt.getText().toString().trim(), "30");

        if (TextUtils.isEmpty(domain)) {
            Toast.makeText(this, "Please enter a valid server domain.", Toast.LENGTH_SHORT).show();
            return;
        }

        if (!isValidInterval(interval)) {
            Toast.makeText(this, "Polling interval must be a positive number.", Toast.LENGTH_SHORT).show();
            return;
        }

        if (!saveSimSettings(
                1,
                sim1EnabledSw,
                sim1ServiceSp,
                sim1NumberEt,
                sim1PinEt,
                sim1MinBalanceEt
        )) {
            return;
        }

        if (sim2Card.getVisibility() == View.VISIBLE && !saveSimSettings(
                2,
                sim2EnabledSw,
                sim2ServiceSp,
                sim2NumberEt,
                sim2PinEt,
                sim2MinBalanceEt
        )) {
            return;
        }

        session.setData(Session.API_DOMAIN_LINK, domain);
        session.setBooleanData(Session.IS_DOMAIN_VALIED, true);
        session.setData(Session.TIME_INTERVAL, interval);

        Toast.makeText(this, "Settings saved.", Toast.LENGTH_SHORT).show();
        finish();
    }

    private boolean saveSimSettings(int simIndex,
                                    Switch enabledSwitch,
                                    Spinner serviceSpinner,
                                    EditText numberEt,
                                    EditText pinEt,
                                    EditText minBalanceEt) {
        boolean enabled = enabledSwitch.isChecked();
        String serviceName = String.valueOf(serviceSpinner.getSelectedItem());
        String serviceCode = ServiceCatalog.getCodeForService(serviceName);
        String simNumber = normalizeBdPhone(numberEt.getText().toString());
        String pin = pinEt.getText().toString().trim();
        String minBalance = defaultIfEmpty(minBalanceEt.getText().toString().trim(), "0");

        if (enabled) {
            if (ServiceCatalog.SELECT_ONE.equals(serviceName)) {
                Toast.makeText(this, "Select a service for SIM " + simIndex + ".", Toast.LENGTH_SHORT).show();
                return false;
            }
            if (!simNumber.matches("01\\d{9}")) {
                Toast.makeText(this, "SIM " + simIndex + " number must be 11 digits.", Toast.LENGTH_SHORT).show();
                return false;
            }
            if (TextUtils.isEmpty(pin)) {
                Toast.makeText(this, "Enter a PIN for SIM " + simIndex + ".", Toast.LENGTH_SHORT).show();
                return false;
            }
        }

        boolean configured = !TextUtils.isEmpty(simNumber) || !TextUtils.isEmpty(pin) || !ServiceCatalog.SELECT_ONE.equals(serviceName);

        if (simIndex == 1) {
            session.SetSim1Info(configured, simNumber, pin, minBalance, "");
            session.setBooleanData(Session.SIM1_ENABLED, enabled);
            session.setData(Session.SIM1_SERVICE, String.valueOf(serviceSpinner.getSelectedItemPosition()));
            session.setData(Session.SIM1_SERVICE_NAME, ServiceCatalog.SELECT_ONE.equals(serviceName) ? "" : serviceName);
            session.setData(Session.SIM1_SERVICE_CODE, serviceCode);
        } else {
            session.SetSim2Info(configured, simNumber, pin, minBalance, "");
            session.setBooleanData(Session.SIM2_ENABLED, enabled);
            session.setData(Session.SIM2_SERVICE, String.valueOf(serviceSpinner.getSelectedItemPosition()));
            session.setData(Session.SIM2_SERVICE_NAME, ServiceCatalog.SELECT_ONE.equals(serviceName) ? "" : serviceName);
            session.setData(Session.SIM2_SERVICE_CODE, serviceCode);
        }

        return true;
    }

    private String buildSimTitle(String prefix, SubscriptionInfo subscriptionInfo) {
        String carrier = subscriptionInfo.getCarrierName() == null ? "" : subscriptionInfo.getCarrierName().toString();
        return prefix + (TextUtils.isEmpty(carrier) ? "" : " - " + carrier);
    }

    private String normalizeBdPhone(String value) {
        if (value == null) {
            return "";
        }

        String normalized = value.trim();
        boolean hadPlusPrefix = normalized.startsWith("+");
        normalized = normalized.replaceAll("[^\\d+]", "");
        if (hadPlusPrefix && normalized.startsWith("+88")) {
            normalized = normalized.substring(3);
        } else if (normalized.startsWith("88") && normalized.length() > 10) {
            normalized = normalized.substring(2);
        }
        normalized = normalized.replaceAll("\\D", "");
        if (normalized.matches("1[3-9]\\d{8}")) {
            normalized = "0" + normalized;
        }
        return normalized;
    }

    private String defaultIfEmpty(String value, String fallback) {
        return TextUtils.isEmpty(value) ? fallback : value;
    }

    private boolean isValidInterval(String value) {
        try {
            return Integer.parseInt(value) > 0;
        } catch (NumberFormatException ignored) {
            return false;
        }
    }
}
