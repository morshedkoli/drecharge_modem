package com.dRecharge.modem;

import android.app.AlertDialog;
import android.graphics.Color;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.dRecharge.modem.helper.ServiceConfig;
import com.dRecharge.modem.helper.Session;
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
            domainValueTv.setTextColor(getResources().getColor(R.color.text_primary, getTheme()));
        } else {
            domainValueTv.setText("Not configured");
            domainValueTv.setTextColor(getResources().getColor(R.color.text_hint, getTheme()));
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
            refreshDomainDisplay(domainValueTv);
            dialog.dismiss();
            Toast.makeText(this, "Domain saved", Toast.LENGTH_SHORT).show();
        });

        dialog.show();
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
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
                pinTv.setTextColor(Color.parseColor("#AAAAAA"));
            } else {
                pinTv.setText("****");
                pinTv.setTextColor(Color.parseColor("#009688"));
            }

            ((TextView) row.findViewById(R.id.svcSim)).setText("SIM " + cfg.sim);

            TextView statusTv = row.findViewById(R.id.svcStatus);
            if (cfg.active) {
                statusTv.setText("Active");
                statusTv.setTextColor(Color.parseColor("#4CAF50"));
            } else {
                statusTv.setText("Inactive");
                statusTv.setTextColor(Color.parseColor("#AAAAAA"));
            }

            row.findViewById(R.id.svcEdit).setOnClickListener(v -> showEditDialog(name));

            serviceListContainer.addView(row);
        }
    }

    private void showEditDialog(String name) {
        ServiceConfig current = session.getServiceConfig(name);

        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_service_edit, null);

        TextView title = dialogView.findViewById(R.id.dialogServiceTitle);
        RadioButton activeRb = dialogView.findViewById(R.id.dialogActiveRb);
        RadioButton inactiveRb = dialogView.findViewById(R.id.dialogInactiveRb);
        RadioButton sim1Rb = dialogView.findViewById(R.id.dialogSim1Rb);
        RadioButton sim2Rb = dialogView.findViewById(R.id.dialogSim2Rb);
        EditText numberEt = dialogView.findViewById(R.id.dialogNumberEt);
        EditText pinEt = dialogView.findViewById(R.id.dialogPinEt);
        Button cancelBtn = dialogView.findViewById(R.id.dialogCancelBtn);
        Button updateBtn = dialogView.findViewById(R.id.dialogUpdateBtn);

        title.setText(name);

        if (current.active) activeRb.setChecked(true);
        else inactiveRb.setChecked(true);

        if (current.sim == 2) sim2Rb.setChecked(true);
        else sim1Rb.setChecked(true);

        numberEt.setText(current.number);
        pinEt.setText(current.pin);

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
            updated.active = activeRb.isChecked();
            updated.sim = sim1Rb.isChecked() ? 1 : 2;
            updated.number = numberEt.getText().toString().trim();
            updated.pin = pinEt.getText().toString().trim();
            session.saveServiceConfig(updated);
            dialog.dismiss();
            buildServiceList();
        });

        dialog.show();
    }
}
