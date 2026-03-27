package com.dRecharge.modem;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.databinding.DataBindingUtil;

import android.Manifest;
import android.content.ContentResolver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.provider.Settings;
import android.telephony.SubscriptionInfo;
import android.telephony.SubscriptionManager;
import android.text.Editable;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.Window;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import com.dRecharge.modem.apimodel.InsertMessageModel;
import com.dRecharge.modem.databinding.ActivityMainBinding;
import com.dRecharge.modem.helper.Constant;
import com.dRecharge.modem.helper.ServiceCatalog;
import com.dRecharge.modem.helper.Session;
import com.dRecharge.modem.receiver.SMSBReceiver;
import com.dRecharge.modem.server.ModemServerRepository;
import com.dRecharge.modem.server.ServerConfig;
import com.dRecharge.modem.service.ServiceRequest;
import com.dRecharge.modem.ussd.USSDApi;
import com.dRecharge.modem.ussd.USSDController;
import com.dRecharge.modem.ussd.USSDService;

import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Queue;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.CountDownLatch;

import javax.crypto.BadPaddingException;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;

import static com.dRecharge.modem.helper.Constant.API_SAVED_DOMAIN_LINK;
import static com.dRecharge.modem.helper.Constant.D_N;
import static com.dRecharge.modem.helper.Constant.getNextWord;
import static com.dRecharge.modem.helper.Constant.getSim1Bal;
import static com.dRecharge.modem.helper.Constant.getSim2Bal;
import static com.dRecharge.modem.helper.Constant.savedSim1Bal;
import static com.dRecharge.modem.helper.Constant.savedSim1Pin;
import static com.dRecharge.modem.helper.Constant.savedSim1Service;
import static com.dRecharge.modem.helper.Constant.savedSim1ServiceCode;
import static com.dRecharge.modem.helper.Constant.savedSim1ServiceName;
import static com.dRecharge.modem.helper.Constant.savedSim1Time;
import static com.dRecharge.modem.helper.Constant.savedSim2Bal;
import static com.dRecharge.modem.helper.Constant.savedSim2Pin;
import static com.dRecharge.modem.helper.Constant.savedSim2Service;
import static com.dRecharge.modem.helper.Constant.savedSim2ServiceCode;
import static com.dRecharge.modem.helper.Constant.savedSim2ServiceName;
import static com.dRecharge.modem.helper.Constant.savedSim2Time;
import static com.dRecharge.modem.helper.Constant.sim1;
import static com.dRecharge.modem.helper.Constant.sim1Id;
import static com.dRecharge.modem.helper.Constant.sim1Num;
import static com.dRecharge.modem.helper.Constant.sim2;
import static com.dRecharge.modem.helper.Constant.sim2Id;
import static com.dRecharge.modem.helper.Constant.sim2Num;
import static com.dRecharge.modem.helper.Constant.sim2Slot;
import static com.dRecharge.modem.helper.Session.SIM1_SERVICE_CODE;
import static com.dRecharge.modem.helper.Session.SIM2_SERVICE_CODE;

public class MainActivity extends AppCompatActivity {
    private ActivityMainBinding activityMainBinding;
    private HashMap<String, HashSet<String>> map;
    private Session session;
    private static MainActivity ins;
    private String sim_number, op_code, op;
    String TAG = "TAG_ACC";
    String dialCodeLoad = null;
    String dialCodeType = "1";
    LoadingDialog loadingDialog = new LoadingDialog(MainActivity.this);

    public static Context contextOfApplication;
    private USSDApi ussdApi;
    private ModemServerRepository serverRepository;

    private boolean _isWorkSim1 = false;
    private boolean _isWorkSim2 = false;

    // রিকোয়েস্ট কিউ সিস্টেম - প্রতিটি SIM এর জন্য আলাদা কিউ
    // Request Queue System - Separate queue for each SIM
    private Queue<RequestData> requestQueueSim1 = new LinkedList<>();
    private Queue<RequestData> requestQueueSim2 = new LinkedList<>();
    private boolean isProcessingSim1 = false; // SIM1 এর জন্য রিকোয়েস্ট প্রসেস হচ্ছে কিনা
    private boolean isProcessingSim2 = false; // SIM2 এর জন্য রিকোয়েস্ট প্রসেস হচ্ছে কিনা
    private boolean isFetchingPendingSim1 = false; // SIM1 এর জন্য getNewPending কল হচ্ছে কিনা (multiple requests prevent করতে)
    private boolean isFetchingPendingSim2 = false; // SIM2 এর জন্য getNewPending কল হচ্ছে কিনা (multiple requests prevent করতে)

    // Balance check tracking - Balance check শেষ হওয়ার পর request fetch করার জন্য
    private boolean isBalanceCheckCompleteSim1 = false; // SIM1 এর balance check সম্পূর্ণ হয়েছে কিনা
    private boolean isBalanceCheckCompleteSim2 = false; // SIM2 এর balance check সম্পূর্ণ হয়েছে কিনা
    private boolean isWaitingForBalanceCheckSim1 = false; // SIM1 এর জন্য balance check শেষ হওয়ার অপেক্ষা করছে কিনা
    private boolean isWaitingForBalanceCheckSim2 = false; // SIM2 এর জন্য balance check শেষ হওয়ার অপেক্ষা করছে কিনা

    // গ্লোবাল লক - একবারে শুধুমাত্র একটি SIM প্রসেস করবে
    // Global lock - Only one SIM will process at a time
    private boolean isAnySimProcessing = false;
    private int lastProcessedSimId = -1; // শেষ কোন SIM প্রসেস করেছে
    private long simProcessingDelay = 30000; // ডিফল্ট: 30 সেকেন্ড (30000ms) - একটি SIM শেষ হওয়ার পর আরেকটি SIM শুরু করার আগে অপেক্ষা

    Handler handler = new Handler();
    Timer simOneExe, simTwoExe, screenExe;
    Runnable simOneRunable, simTwoRunable, screenRunnable;
    TimerTask _simOneWorker, _simTwoWorker, _screenOn;


    int timeInterval = 30000; // ডিফল্ট: 30000 মিলিসেকেন্ড = 30 সেকেন্ড (অন্য কাজের জন্য ব্যবহৃত)

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        activityMainBinding = DataBindingUtil.setContentView(this, R.layout.activity_main);
        ins = this;
        contextOfApplication = getApplicationContext();
        findViewById(R.id.homeSettingsBtn).setOnClickListener(v -> openSettingsScreen());

        session = new Session(MainActivity.this);
        ussdApi = USSDController.getInstance(contextOfApplication);

        activityMainBinding.receivedRq1Tv.setVisibility(View.GONE);
        activityMainBinding.receivedRq2Tv.setVisibility(View.GONE);
        activityMainBinding.sendReq1Tv.setVisibility(View.GONE);
        activityMainBinding.sendReq2Tv.setVisibility(View.GONE);

        if (!isAccessServiceEnabled(getApplicationContext(), USSDService.class)) {
            startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));
        }

        init();
        simServiceSelect();
        sim1Setting();
        sim2Setting();
        getsSimServiceInfo();
        serviceOnOff();
        reloadHomeFromSession();


        screenExe = new Timer();
        screenExe.schedule(screenOn(), 0, 15000);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (activityMainBinding != null && session != null) {
            refreshServerRepository();
            getsSimServiceInfo();
            reloadHomeFromSession();
        }
    }

    private TimerTask screenOn() {
        _screenOn = new TimerTask() {
            @Override
            public void run() {
                handler.post(screenRunnable = new Runnable() {
                    @Override
                    public void run() {
                        Toast.makeText(MainActivity.this, "", Toast.LENGTH_SHORT).show();
                    }
                });
            }
        };
        return _screenOn;
    }

    public static MainActivity getMainActivityInstance() {
        return ins;
    }

    private void openSettingsScreen() {
        startActivity(new Intent(this, SettingsActivity.class));
    }

    private void reloadHomeFromSession() {
        updateSimConfigurationSummary();
        syncEnabledSwitchesFromSession();
    }

    private void updateSimConfigurationSummary() {
        String intervalText = defaultIfEmpty(session.getData(Session.TIME_INTERVAL), "30");

        savedSim1Pin = session.getData(Session.SIM1_PIN);
        savedSim1Time = session.getData(Session.SIM1_TIME);
        savedSim1Bal = defaultIfEmpty(session.getData(Session.SIM1_MIN_BAL), "0");
        savedSim1ServiceCode = session.getData(Session.SIM1_SERVICE_CODE);
        savedSim1Service = safeParseInt(session.getData(Session.SIM1_SERVICE), 0);
        savedSim1ServiceName = session.getData(Session.SIM1_SERVICE_NAME);
        sim1Num = defaultIfEmpty(session.getData(Session.SIM1_NUMBER), sim1Num);
        activityMainBinding.pin1Tv.setText("PIN: " + maskPin(savedSim1Pin));
        activityMainBinding.minIntrval1Tv.setText("Interval: " + intervalText + " seconds");
        activityMainBinding.minBal1Tv.setText("Balance Limit: " + savedSim1Bal);
        if (activityMainBinding.service1Sp.getAdapter() != null) {
            activityMainBinding.service1Sp.setSelection(ServiceCatalog.indexOf(savedSim1ServiceName));
        }

        savedSim2Pin = session.getData(Session.SIM2_PIN);
        savedSim2Time = session.getData(Session.SIM2_TIME);
        savedSim2Bal = defaultIfEmpty(session.getData(Session.SIM2_MIN_BAL), "0");
        savedSim2ServiceCode = session.getData(Session.SIM2_SERVICE_CODE);
        savedSim2Service = safeParseInt(session.getData(Session.SIM2_SERVICE), 0);
        savedSim2ServiceName = session.getData(Session.SIM2_SERVICE_NAME);
        sim2Num = defaultIfEmpty(session.getData(Session.SIM2_NUMBER), sim2Num);
        activityMainBinding.pin2Tv.setText("PIN: " + maskPin(savedSim2Pin));
        activityMainBinding.minIntrval2Tv.setText("Interval: " + intervalText + " seconds");
        activityMainBinding.minBal2Tv.setText("Balance Limit: " + savedSim2Bal);
        if (activityMainBinding.service2Sp.getAdapter() != null) {
            activityMainBinding.service2Sp.setSelection(ServiceCatalog.indexOf(savedSim2ServiceName));
        }

        updateSimLabels();
    }

    private void updateSimLabels() {
        activityMainBinding.sim1Tv.setText(buildSimLabel(sim1, sim1Num, sim1Id, savedSim1ServiceName));
        activityMainBinding.sim2Tv.setText(buildSimLabel(sim2, sim2Num, sim2Id, savedSim2ServiceName));

        boolean sim1Configured = session.isSim1Valid();
        activityMainBinding.Sim1Layout.setVisibility(sim1Configured ? View.VISIBLE : View.GONE);
        activityMainBinding.sim1Status.setVisibility(sim1Configured ? View.GONE : View.VISIBLE);

        boolean sim2Configured = session.isSim2Valid();
        activityMainBinding.simId2Layout.setVisibility((sim2Id >= 0 || sim2Configured) ? View.VISIBLE : View.GONE);
        activityMainBinding.Sim2Layout.setVisibility(sim2Configured ? View.VISIBLE : View.GONE);
        activityMainBinding.sim2Status.setVisibility(sim2Configured ? View.GONE : View.VISIBLE);
    }

    private void syncEnabledSwitchesFromSession() {
        activityMainBinding.status1Sw.setChecked(session.getBooleanData(Session.SIM1_ENABLED));
        activityMainBinding.status2Sw.setChecked(session.getBooleanData(Session.SIM2_ENABLED));
    }

    private String buildSimLabel(String carrier, String number, int id, String serviceName) {
        String safeCarrier = defaultIfEmpty(carrier, "SIM");
        String safeNumber = defaultIfEmpty(number, "Not set");
        String safeService = defaultIfEmpty(serviceName, "No service selected");
        return safeCarrier + " | " + safeNumber + " | Slot: " + id + " | " + safeService;
    }

    private String defaultIfEmpty(String value, String fallback) {
        return value == null || value.trim().isEmpty() ? fallback : value;
    }

    private int safeParseInt(String value, int fallback) {
        try {
            return Integer.parseInt(value);
        } catch (Exception ignored) {
            return fallback;
        }
    }

    private String maskPin(String pin) {
        if (pin == null || pin.trim().isEmpty()) {
            return "Not set";
        }
        return "****";
    }

    //region Settings And Service

    public boolean isAccessServiceEnabled(Context context, Class accessibilityServiceClass) {
        String prefString = Settings.Secure.getString(context.getContentResolver(), Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);

        return prefString != null && prefString.contains(context.getPackageName() + "/" + accessibilityServiceClass.getName());
    }

    /**
     * এই ফাংশনটি Timer-এর জন্য interval time (মিলিসেকেন্ডে) রিটার্ন করে
     *
     * কিভাবে কাজ করে:
     * 1. ডিফল্ট interval = 30 সেকেন্ড
     * 2. Session থেকে TIME_INTERVAL পড়ে (যদি সেট করা থাকে)
     * 3. যদি interval 1 সেকেন্ডের কম হয়, তাহলে 30 সেকেন্ড সেট করে
     * 4. সেকেন্ডকে মিলিসেকেন্ডে কনভার্ট করে রিটার্ন করে (interval * 1000)
     *
     * উদাহরণ:
     * - যদি interval = 30 হয়, তাহলে return = 30000 মিলিসেকেন্ড (30 সেকেন্ড)
     * - যদি interval = 60 হয়, তাহলে return = 60000 মিলিসেকেন্ড (60 সেকেন্ড)
     *
     * @return long - interval time মিলিসেকেন্ডে (সেকেন্ড * 1000)
     */
    private long  getTimerTime(){
        int interval = 30; // ডিফল্ট: 30 সেকেন্ড

        try {
            String timeStr = session.getData(Session.TIME_INTERVAL);
            if(timeStr == null || timeStr.trim().isEmpty()){
                timeStr = "30"; // যদি সেট না থাকে, 30 সেকেন্ড ব্যবহার করবে
            }
            interval = Integer.parseInt(timeStr);
            if(interval < 1){
                interval = 30; // যদি 1 সেকেন্ডের কম হয়, 30 সেকেন্ড সেট করবে
            }
        }catch(Exception e){
            Log.e("USD_TIMER", "Error:1 " + interval);

        }
        return interval * 1000L; // সেকেন্ডকে মিলিসেকেন্ডে কনভার্ট করে (30 * 1000 = 30000ms)
    }

    private void refreshServerRepository() {
        String savedDomain = session.getData(Session.API_DOMAIN_LINK);
        String normalizedDomain = ServerConfig.sanitizeDomain(savedDomain);
        if (normalizedDomain.isEmpty()) {
            serverRepository = null;
            API_SAVED_DOMAIN_LINK = "";
            return;
        }

        if (!normalizedDomain.equals(savedDomain)) {
            session.setData(Session.API_DOMAIN_LINK, normalizedDomain);
        }

        API_SAVED_DOMAIN_LINK = normalizedDomain;
        serverRepository = ModemServerRepository.fromDomain(normalizedDomain);
    }

    private void init() {






        try{
            addListenerOnSpinnerItemSelection();
            int MyVersion = Build.VERSION.SDK_INT;
            if (MyVersion > Build.VERSION_CODES.LOLLIPOP) {
                if (!checkIfAlreadyhavePermission()) {
                    requestForSpecificPermission();
                }
            }

            if (!session.isDomainValid()) {
                domainSetting();
                return;
            } else {
                refreshServerRepository();
            }

            // Session থেকে TIME_INTERVAL পড়ে timeInterval variable এ সেট করে
            if (!session.getData(Session.TIME_INTERVAL).equals("")) {
                // Session থেকে সেকেন্ডে আছে, তাই 1000 দিয়ে গুণ করে মিলিসেকেন্ডে কনভার্ট করে
                timeInterval = Integer.parseInt(session.getData(Session.TIME_INTERVAL)) * 1000;
            } else {
                // যদি সেট না থাকে, ডিফল্ট 30 সেকেন্ড (30000ms) ব্যবহার করবে
                timeInterval = 30000;
                activityMainBinding.minIntrval1Tv.setText("ইন্টারভাল: ডিফল্ট " + timeInterval / 1000 + " সেকেন্ড");
                activityMainBinding.minIntrval2Tv.setText("ইন্টারভাল: ডিফল্ট " + timeInterval / 1000 + " সেকেন্ড");
            }
        }catch (Exception e) {
            Log.e("USD_INFO", "Error:1 " + e.getMessage());
        }

        try{
            map = new HashMap<>();
            map.put("KEY_LOGIN", new HashSet<String>(Arrays.asList("running...", "waiting", "loading", "esperando")));
            map.put("KEY_ERROR", new HashSet<String>(Arrays.asList("problema", "problem", "error", "null")));

            if (session.isSim1Valid()) {
                sim1Id = Integer.parseInt(session.getData(Session.SIM1_ID));
                sim1Num = session.getData(Session.SIM1_NUMBER);
                savedSim1Pin = session.getData(Session.SIM1_PIN);
                savedSim1Time = session.getData(Session.SIM1_TIME);
                savedSim1Bal = session.getData(Session.SIM1_MIN_BAL);
                savedSim1ServiceCode = session.getData(SIM1_SERVICE_CODE);
                activityMainBinding.pin1Tv.setText(session.getData(Session.SIM1_PIN));

                // Interval time display - default 30 seconds if not set
                String timeIntervalStr = session.getData(Session.TIME_INTERVAL);
                if (timeIntervalStr == null || timeIntervalStr.trim().isEmpty()) {
                    activityMainBinding.minIntrval1Tv.setText("ইন্টারভাল: ডিফল্ট 30 সেকেন্ড");
                } else {
                    activityMainBinding.minIntrval1Tv.setText("ইন্টারভাল: " + timeIntervalStr + " সেকেন্ড");
                }
                activityMainBinding.minBal1Tv.setText("Balance Limit: " + session.getData(Session.SIM1_MIN_BAL));

                savedSim1Service = session.getData(Session.SIM1_SERVICE).isEmpty() ? 0 : Integer.parseInt(session.getData(Session.SIM1_SERVICE));
                savedSim1ServiceName = session.getData(Session.SIM1_SERVICE_NAME).isEmpty() ? "" : session.getData(Session.SIM1_SERVICE_NAME);
                Log.d("USD_INFO","savedSim1ServiceName "+ savedSim1ServiceName);
//            Toast.makeText()
                activityMainBinding.Sim1Layout.setVisibility(View.VISIBLE);
                activityMainBinding.sim1Status.setVisibility(View.GONE);
                activityMainBinding.sim1Tv.setText(sim1 + "  | " + sim1Num + " | Id: " + sim1Id);

                Log.d("USD_TIMER","timer init");


                simOneExe = new Timer();
                // Timer.schedule(কাজ, প্রথমবার কতক্ষণ পর শুরু হবে, কতক্ষণ পর পর কাজ করবে)
                // simOneSchedule() = SIM 1 এর জন্য কাজ
                // 1000 = প্রথমবার 1 সেকেন্ড (1000ms) পর শুরু হবে
                // getTimerTime() = এরপর প্রতি X সেকেন্ড পর পর কাজ করবে (যেমন: 30 সেকেন্ড = 30000ms)
                simOneExe.schedule(simOneSchedule(), 1000, getTimerTime());
//                simOneExe.schedule(simOneSchedule(), 1000, (interval * 1000L));
            }else{
                Log.d("USD_TIMER","SIM_DATA_NOT_FOUND");
            }
        }catch (Exception e) {
            Log.e("USD_TIMER", "Error:2 " + e.getMessage());
            Log.e("USD_TIMER", "Error: time " + getTimerTime());
        }

        try{
            if (session.isSim2Valid()) {
                sim2Id = Integer.parseInt(session.getData(Session.SIM2_ID));
                sim2Num = session.getData(Session.SIM2_NUMBER);
                savedSim2Pin = session.getData(Session.SIM2_PIN);
                savedSim2Time = session.getData(Session.SIM2_TIME);
                savedSim2Bal = session.getData(Session.SIM2_MIN_BAL);
                savedSim2ServiceCode = session.getData(SIM2_SERVICE_CODE);
                activityMainBinding.pin2Tv.setText(savedSim2Pin);
                // Interval time display - default 30 seconds if not set
                String timeIntervalStr2 = session.getData(Session.TIME_INTERVAL);
                if (timeIntervalStr2 == null || timeIntervalStr2.trim().isEmpty()) {
                    activityMainBinding.minIntrval2Tv.setText("ইন্টারভাল: ডিফল্ট 30 সেকেন্ড");
                } else {
                    activityMainBinding.minIntrval2Tv.setText("ইন্টারভাল: " + timeIntervalStr2 + " সেকেন্ড");
                }
                activityMainBinding.minBal2Tv.setText("Balance Limit: " + savedSim2Bal);
                savedSim2Service = session.getData(Session.SIM2_SERVICE).isEmpty() ? 0 : Integer.parseInt(session.getData(Session.SIM2_SERVICE));
                savedSim2ServiceName = session.getData(Session.SIM2_SERVICE_NAME).isEmpty() ? "" : session.getData(Session.SIM2_SERVICE_NAME);
                activityMainBinding.Sim2Layout.setVisibility(View.VISIBLE);
                activityMainBinding.sim2Status.setVisibility(View.GONE);
                activityMainBinding.sim2Tv.setText(sim2 + "  | " + sim2Num + " | Id: " + sim2Id);

                simTwoExe = new Timer();
                // Timer.schedule(কাজ, প্রথমবার কতক্ষণ পর শুরু হবে, কতক্ষণ পর পর কাজ করবে)
                // simTwoSchedule() = SIM 2 এর জন্য কাজ
                // 1000 = প্রথমবার 1 সেকেন্ড (1000ms) পর শুরু হবে
                // getTimerTime() = এরপর প্রতি X সেকেন্ড পর পর কাজ করবে (যেমন: 30 সেকেন্ড = 30000ms)
                simTwoExe.schedule(simTwoSchedule(), 1000, getTimerTime());
            }
        }catch (Exception e) {
            Log.e("USD_TIMER", "Error:3 " + e.getMessage());
            Log.e("USD_TIMER", "Error: time " + getTimerTime());

        }

    }

    private TimerTask simOneSchedule() {
        _simOneWorker = new TimerTask() {
            @Override
            public void run() {
                handler.post(simOneRunable = new Runnable() {
                    @Override
                    public void run() {
                        Log.d("USD_TIMER","timer is runing");

                        _isWorkSim1 = true;
                        System.out.println("=====SIM1----RUN:: " + _isWorkSim1 + " STS_SIM2:: " + _isWorkSim2);
                        getNewPending(savedSim1ServiceName, savedSim1ServiceCode, session.getData(Session.SIM1_NUMBER), getSim1Bal, String.valueOf(sim1Id), savedSim1Pin);
                    }
                });
            }
        };
        return _simOneWorker;
    }

    private TimerTask simTwoSchedule() {
        _simTwoWorker = new TimerTask() {
            @Override
            public void run() {
                handler.post(simTwoRunable = new Runnable() {
                    @Override
                    public void run() {
                        _isWorkSim2 = true;
                        System.out.println("=====SIM2----RUN:: " + _isWorkSim2 + " STS_SIM1:: " + _isWorkSim1);
                        getNewPending(savedSim2ServiceName, savedSim2ServiceCode, session.getData(Session.SIM2_NUMBER), getSim2Bal, String.valueOf(sim2Id), savedSim2Pin);
                    }
                });
            }
        };
        return _simTwoWorker;
    }

    public void updateSimBalanceTv(final String bal, final int slot) {
        try {
            if (slot == sim1Id) {
                double balanceDouble = Double.parseDouble(bal.replaceAll(",", ""));
                int finalBal = (int) balanceDouble;
                if (finalBal <= Integer.parseInt(savedSim1Bal)) {
                    activityMainBinding.status1Sw.setChecked(false);
                }
                if (finalBal >= Integer.parseInt(savedSim1Bal)) {
                    activityMainBinding.status1Sw.setChecked(true);
                }
                getSim1Bal = bal;
                activityMainBinding.getBal1Tv.setText("Balance: " + bal);

                // Balance check সম্পূর্ণ হয়েছে - যদি status button ON থাকে এবং balance check এর অপেক্ষায় থাকে, তাহলে 30 সেকেন্ড পর request fetch শুরু করুন
                // Balance check complete - if status button is ON and waiting for balance check, start fetching requests after 30 seconds
                isBalanceCheckCompleteSim1 = true;
                if (isWaitingForBalanceCheckSim1 && activityMainBinding.status1Sw.isChecked()) {
                    isWaitingForBalanceCheckSim1 = false;
                    // 30 সেকেন্ড অপেক্ষা করে প্রথম request fetch করুন
                    // Wait 30 seconds (using interval time) and then fetch first request
                    handler.postDelayed(new Runnable() {
                        @Override
                        public void run() {
                            // Status switch এখনো চালু আছে কিনা এবং queue খালি আছে কিনা চেক করুন
                            // Check if status switch is still ON and queue is empty
                            if (activityMainBinding.status1Sw.isChecked() && !isProcessingSim1 && requestQueueSim1.isEmpty()) {
                                getNewPending(savedSim1ServiceName, savedSim1ServiceCode, session.getData(Session.SIM1_NUMBER), getSim1Bal, String.valueOf(sim1Id), savedSim1Pin);
                            }
                        }
                    }, getTimerTime()); // Interval time ব্যবহার করুন (default 30 সেকেন্ড) / Use interval time (default 30 seconds)
                }
            }
            if (slot == sim2Id) {
                double balanceDouble = Double.parseDouble(bal.replaceAll(",", ""));
                int finalBal = (int) balanceDouble;
                if (finalBal <= Integer.parseInt(savedSim2Bal)) {
                    activityMainBinding.status2Sw.setChecked(false);
                }
                if (finalBal >= Integer.parseInt(savedSim2Bal)) {
                    activityMainBinding.status2Sw.setChecked(true);
                }
                getSim2Bal = bal;
                activityMainBinding.getBal2Tv.setText("Balance: " + bal);

                // Balance check সম্পূর্ণ হয়েছে - যদি status button ON থাকে এবং balance check এর অপেক্ষায় থাকে, তাহলে 30 সেকেন্ড পর request fetch শুরু করুন
                // Balance check complete - if status button is ON and waiting for balance check, start fetching requests after 30 seconds
                isBalanceCheckCompleteSim2 = true;
                if (isWaitingForBalanceCheckSim2 && activityMainBinding.status2Sw.isChecked()) {
                    isWaitingForBalanceCheckSim2 = false;
                    // 30 সেকেন্ড অপেক্ষা করে প্রথম request fetch করুন
                    // Wait 30 seconds (using interval time) and then fetch first request
                    handler.postDelayed(new Runnable() {
                        @Override
                        public void run() {
                            // Status switch এখনো চালু আছে কিনা এবং queue খালি আছে কিনা চেক করুন
                            // Check if status switch is still ON and queue is empty
                            if (activityMainBinding.status2Sw.isChecked() && !isProcessingSim2 && requestQueueSim2.isEmpty()) {
                                getNewPending(savedSim2ServiceName, savedSim2ServiceCode, session.getData(Session.SIM2_NUMBER), getSim2Bal, String.valueOf(sim2Id), savedSim2Pin);
                            }
                        }
                    }, getTimerTime()); // Interval time ব্যবহার করুন (default 30 সেকেন্ড) / Use interval time (default 30 seconds)
                }
            }
        } catch (Exception e) {
            System.out.println("====ED_TH: " + e);
        }
    }

    public void updateResultTv(final int slot, final String message) {
        MainActivity.this.runOnUiThread(new Runnable() {
            public void run() {
                SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault());
                String currentDateandTime = sdf.format(new Date());
                String newMessage = currentDateandTime + " :: " + message + "\n";
                if (slot == sim1Id) {
                    // Latest history at top - prepend instead of append
                    String currentText = activityMainBinding.sim1ResutlRv.getText().toString();
                    activityMainBinding.sim1ResutlRv.setText(newMessage + currentText);
                }
                if (slot == sim2Id) {
                    // Latest history at top - prepend instead of append
                    String currentText = activityMainBinding.sim2ResutlRv.getText().toString();
                    activityMainBinding.sim2ResutlRv.setText(newMessage + currentText);
                }
            }
        });
    }

    private boolean checkIfAlreadyhavePermission() {
        int result = ContextCompat.checkSelfPermission(this, Manifest.permission.GET_ACCOUNTS);
        boolean notificationsGranted = true;

        // Check POST_NOTIFICATIONS permission for Android 13+ (API 33+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notificationsGranted = ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED;
        }

        return result == PackageManager.PERMISSION_GRANTED && notificationsGranted;
    }

    private void requestForSpecificPermission() {
        List<String> permissions = new ArrayList<>();
        permissions.add(Manifest.permission.READ_CONTACTS);
        permissions.add(Manifest.permission.CALL_PHONE);
        permissions.add(Manifest.permission.SYSTEM_ALERT_WINDOW);
        permissions.add(Manifest.permission.READ_PHONE_STATE);
        permissions.add(Manifest.permission.GET_ACCOUNTS);
        permissions.add(Manifest.permission.RECEIVE_SMS);
        permissions.add(Manifest.permission.READ_SMS);
        permissions.add(Manifest.permission.READ_EXTERNAL_STORAGE);
        permissions.add(Manifest.permission.WRITE_SETTINGS);
        permissions.add(Manifest.permission.WRITE_SECURE_SETTINGS);
        permissions.add(Manifest.permission.WRITE_EXTERNAL_STORAGE);

        // Add POST_NOTIFICATIONS permission for Android 13+ (API 33+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.POST_NOTIFICATIONS);
        }

        ActivityCompat.requestPermissions(MainActivity.this, permissions.toArray(new String[0]), 101);
    }

    private void getsSimServiceInfo() {
        SubscriptionManager subscriptionManager = (SubscriptionManager) getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE);

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.READ_PHONE_STATE) != PackageManager.PERMISSION_GRANTED) {
            // TODO: Consider calling
            //   ActivityCompat#requestPermissions
            return;
        }
        List<SubscriptionInfo> subscriptionInfoList = subscriptionManager.getActiveSubscriptionInfoList();


        if (subscriptionInfoList != null) {
            if (subscriptionInfoList.size() == 1) {
                sim1 = subscriptionInfoList.get(0).getCarrierName().toString();
                sim1Id = subscriptionInfoList.get(0).getSimSlotIndex();
                if (subscriptionInfoList.get(0).getNumber() != null) {


                    sim1Num = subscriptionInfoList.get(0).getNumber();
                    sim1Num = sim1Num.replace("+88", "").isEmpty() ? session.getData(Session.SIM1_NUMBER) : sim1Num.replace("+88", "");
                    activityMainBinding.sim1Tv.setText(sim1 + "  | No:  " + sim1Num + " | Id: " + sim1Id);
                }
                session.setData(Session.SIM1_ID, String.valueOf(sim1Id));
                activityMainBinding.Sim1Layout.setVisibility(View.VISIBLE);
                activityMainBinding.sim1Status.setVisibility(View.GONE);
            } else {
                sim1 = subscriptionInfoList.get(0).getCarrierName().toString();
                sim2 = subscriptionInfoList.get(1).getCarrierName().toString();
                sim1Id = subscriptionInfoList.get(0).getSimSlotIndex();
                sim2Id = subscriptionInfoList.get(1).getSimSlotIndex();

                if (subscriptionInfoList.get(0).getNumber() != null) {
                    sim1Num = subscriptionInfoList.get(0).getNumber();
                    sim2Num = subscriptionInfoList.get(1).getNumber();

                    sim1Num = sim1Num.replace("+88", "").isEmpty() ? session.getData(Session.SIM1_NUMBER) : sim1Num.replace("+88", "");

                    sim2Num = sim2Num.replace("+88", "").isEmpty() ? session.getData(Session.SIM2_NUMBER) : sim2Num.replace("+88", "");

                    activityMainBinding.sim1Tv.setText(sim1 + "  | " + sim1Num + " | Id: " + sim1Id);
                    activityMainBinding.sim2Tv.setText(sim2 + "  | " + sim2Num + " | Id: " + sim2Id);
                }

                session.setData(Session.SIM1_ID, String.valueOf(sim1Id));
                session.setData(Session.SIM2_ID, String.valueOf(sim2Id));
                activityMainBinding.Sim1Layout.setVisibility(View.VISIBLE);
                activityMainBinding.sim1Status.setVisibility(View.GONE);

                activityMainBinding.Sim2Layout.setVisibility(View.VISIBLE);
                activityMainBinding.sim2Status.setVisibility(View.GONE);

                //System.out.println("===========SYSTEM:: num:: " + sim1 + " ====SIM2 " + sim2);
            }
        }
    }

    private void addListenerOnSpinnerItemSelection() {
        final List<String> serviceList = new ArrayList<>(ServiceCatalog.getServices());

        ArrayAdapter<String> serviceAdapter;
        serviceAdapter = new ArrayAdapter(MainActivity.this, android.R.layout.simple_spinner_item, serviceList);

        serviceAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);

        activityMainBinding.service1Sp.setAdapter(serviceAdapter);
        activityMainBinding.service2Sp.setAdapter(serviceAdapter);

    }

    private void domainSetting() {
        AlertDialog.Builder dBuilder = new AlertDialog.Builder(MainActivity.this);
        View dView = getLayoutInflater().inflate(R.layout.api_domain_link, null);
        dBuilder.setView(dView);
        final AlertDialog dialog = dBuilder.create();
        dialog.setCancelable(false);
        dialog.show();

        final EditText domainLink = dView.findViewById(R.id.apiLinkEt);

        Button domainOkBtn = dView.findViewById(R.id.domainOkBtn);
        final Button domainCancelBtn = dView.findViewById(R.id.domainCancelBtn);
        if (session.isDomainValid()) {
            domainLink.setText(session.getData(Session.API_DOMAIN_LINK));
        }
        domainCancelBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                closeNow();
            }
        });

        domainOkBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (!domainLink.getText().toString().equals("")) {
                    final String apiDomain = ServerConfig.sanitizeDomain(domainLink.getText().toString());
                    if (apiDomain.isEmpty()) {
                        Toast.makeText(MainActivity.this, "Please give your valid domain", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    loadingDialog.startLoadingDialog();
                    session.setData(Session.API_DOMAIN_LINK, apiDomain);
                    session.setBooleanData(Session.IS_DOMAIN_VALIED, true);
                    refreshServerRepository();
                    dialog.dismiss();
                    loadingDialog.dismissLoadingDialog();
                } else {
                    Toast.makeText(MainActivity.this, "Please give your valid domain", Toast.LENGTH_SHORT).show();
                }
            }
        });
    }

    private void sim1Setting() {
        activityMainBinding.sim1SettingImg.setOnClickListener(view -> openSettingsScreen());
    }

    private void sim2Setting() {
        activityMainBinding.sim2SettingImg.setOnClickListener(view -> openSettingsScreen());
    }

    private void simServiceSelect() {
        if (savedSim1Service != -1) {
            activityMainBinding.service1Sp.setSelection(savedSim1Service);
        }
        activityMainBinding.service1Sp.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> adapterView, View view, int i, long l) {
                Log.e("USD_SERVICE ::POSITION",adapterView.getItemAtPosition(i).toString());

                String serviceName = String.valueOf(adapterView.getItemAtPosition(i));
                if (!ServiceCatalog.SELECT_ONE.equals(serviceName)) {
                    int selectItem = adapterView.getSelectedItemPosition();
                    Log.d("USD_SERVICE", String.valueOf(selectItem));
                    Log.d("USD_SERVICE_NAME", serviceName);
                    session.setData(Session.SIM1_SERVICE, String.valueOf(selectItem));
                    session.setData(Session.SIM1_SERVICE_NAME, serviceName);
                    savedSim1Service = selectItem;
                    savedSim1ServiceName = serviceName;
                    Log.d("USD_SERVICE_NAME :2", savedSim1ServiceName);
                    session.setData(SIM1_SERVICE_CODE, ServiceCatalog.getCodeForService(savedSim1ServiceName));
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> adapterView) {

            }
        });

        if (savedSim2Service != -1) {
            activityMainBinding.service2Sp.setSelection(savedSim2Service);
        }
        activityMainBinding.service2Sp.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> adapterView, View view, int i, long l) {
                String serviceName = String.valueOf(adapterView.getItemAtPosition(i));
                if (!ServiceCatalog.SELECT_ONE.equals(serviceName)) {
                    int selectItem = adapterView.getSelectedItemPosition();
                    session.setData(Session.SIM2_SERVICE, String.valueOf(selectItem));
                    session.setData(Session.SIM2_SERVICE_NAME, serviceName);
                    savedSim2Service = selectItem;
                    savedSim2ServiceName = serviceName;
                    session.setData(SIM2_SERVICE_CODE, ServiceCatalog.getCodeForService(savedSim2ServiceName));
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> adapterView) {

            }
        });
    }

    private void serviceOnOff() {
        activityMainBinding.status1Sw.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton compoundButton, boolean isChecked) {
                if (isChecked) {
                    if (savedSim1Pin != null && savedSim1Service != 0) {
                        try {
                            // Balance check এর অপেক্ষায় আছে এই flag set করুন
                            // Set flag that we're waiting for balance check to complete
                            isWaitingForBalanceCheckSim1 = true;
                            isBalanceCheckCompleteSim1 = false;

                            // Balance check শুরু করুন
                            // Start balance check
                            queryForSetSimWithBalance(savedSim1ServiceName, sim1Num, savedSim1Pin, sim1Id);

                            // Balance check সম্পূর্ণ হলে updateSimBalanceTv method এ callback আসবে
                            // এবং সেখানে 30 সেকেন্ড অপেক্ষা করে প্রথম request fetch করা হবে
                            // Balance check complete callback will come in updateSimBalanceTv method
                            // and there it will wait 30 seconds (interval time) before fetching first request
                        } catch (Exception e) {
                            System.out.println("======BL_EXP: " + e);
                            isWaitingForBalanceCheckSim1 = false;
                        }
                    } else {
                        Toast.makeText(MainActivity.this, "Please Check the system settings", Toast.LENGTH_SHORT).show();
                        activityMainBinding.status1Sw.setChecked(false);
                        isWaitingForBalanceCheckSim1 = false;
                    }
                } else {
                    Toast.makeText(MainActivity.this, "SIM 1 USSD Service is off", Toast.LENGTH_SHORT).show();
                    isWaitingForBalanceCheckSim1 = false;
                }
            }
        });

        activityMainBinding.status2Sw.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton compoundButton, boolean isChecked) {
                if (isChecked) {
                    if (savedSim2Pin != null && savedSim2Service != 0) {
                        try {
                            // Balance check এর অপেক্ষায় আছে এই flag set করুন
                            // Set flag that we're waiting for balance check to complete
                            isWaitingForBalanceCheckSim2 = true;
                            isBalanceCheckCompleteSim2 = false;

                            // Balance check শুরু করুন
                            // Start balance check
                            queryForSetSimWithBalance(savedSim2ServiceName, sim2Num, savedSim2Pin, sim2Id);

                            // Balance check সম্পূর্ণ হলে updateSimBalanceTv method এ callback আসবে
                            // এবং সেখানে 30 সেকেন্ড অপেক্ষা করে প্রথম request fetch করা হবে
                            // Balance check complete callback will come in updateSimBalanceTv method
                            // and there it will wait 30 seconds (interval time) before fetching first request
                        } catch (Exception e) {
                            System.out.println("======BL_EXP: " + e);
                            isWaitingForBalanceCheckSim2 = false;
                        }
                    } else {
                        Toast.makeText(MainActivity.this, "Please Check the system settings", Toast.LENGTH_SHORT).show();
                        activityMainBinding.status2Sw.setChecked(false);
                        isWaitingForBalanceCheckSim2 = false;
                    }
                } else {
                    Toast.makeText(MainActivity.this, "SIM 2 USSD Service is off", Toast.LENGTH_SHORT).show();
                    isWaitingForBalanceCheckSim2 = false;
                }
            }
        });
    }
    //endregion Settings And Service

    //region System Override
    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        if (requestCode == 101) {
            if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                getsSimServiceInfo();
                Toast.makeText(MainActivity.this, "Thanks for give permission", Toast.LENGTH_SHORT).show();
            } else {
                closeNow();
                Toast.makeText(MainActivity.this, "Permission denied, app not work", Toast.LENGTH_SHORT).show();
            }
        } else {
            super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        }
    }

    private void closeNow() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
            finishAffinity();
        } else {
            finish();
        }
    }
    //endregion System Override

    //region Get New Pending Number
    public void getNewPending(String serviceName, String company, String simNumber, String simBal, String simSlotId, String simPin) {
        if (!(activityMainBinding.status1Sw.isChecked()) && Objects.equals(simSlotId, String.valueOf(sim1Id))) {
            return;
        }
        if (!(activityMainBinding.status2Sw.isChecked()) && Objects.equals(simSlotId, String.valueOf(sim2Id))) {
            return;
        }
        if (serverRepository == null) {
            Log.e("USD_SERVER", "Server repository is not initialized");
            return;
        }

        final int slotId;
        try {
            slotId = Integer.parseInt(simSlotId);
        } catch (NumberFormatException e) {
            Log.e("USD_SERVER", "Invalid SIM slot: " + simSlotId, e);
            return;
        }

        if (slotId == sim1Id) {
            if (isFetchingPendingSim1) {
                return;
            }
            isFetchingPendingSim1 = true;
        } else if (slotId == sim2Id) {
            if (isFetchingPendingSim2) {
                return;
            }
            isFetchingPendingSim2 = true;
        }
        serverRepository.fetchPendingRequests(serviceName, company, simNumber, simSlotId, simBal,
                new ModemServerRepository.PendingRequestsCallback() {
                    @Override
                    public void onPendingRequest(ServiceRequest request) {
                        updateResultTv(slotId, "New Request: " + request.getPhone() + " TK: " + request.getAmount());
                        String service = slotId == sim1Id ? savedSim1ServiceName : savedSim2ServiceName;

                        RequestData requestData = new RequestData(
                                request.getSid(),
                                request.getPcode(),
                                request.getPhone(),
                                request.getAmount(),
                                request.getType(),
                                request.getPackageName(),
                                request.isPowerLoad(),
                                slotId,
                                simPin,
                                service
                        );

                        if (slotId == sim1Id) {
                            requestQueueSim1.offer(requestData);
                        } else if (slotId == sim2Id) {
                            requestQueueSim2.offer(requestData);
                        }

                        processNextInQueue(slotId);
                    }

                    @Override
                    public void onServiceStopped() {
                        if (slotId == sim1Id) {
                            activityMainBinding.status1Sw.setChecked(false);
                            _isWorkSim1 = false;
                        } else if (slotId == sim2Id) {
                            activityMainBinding.status2Sw.setChecked(false);
                            _isWorkSim2 = false;
                        }
                    }

                    @Override
                    public void onFailure(Throwable throwable) {
                        System.out.println("====PENDING ERR " + throwable);
                    }

                    @Override
                    public void onComplete() {
                        if (slotId == sim1Id) {
                            isFetchingPendingSim1 = false;
                        } else if (slotId == sim2Id) {
                            isFetchingPendingSim2 = false;
                        }
                    }
                });
    }
    //endregion Get New Pending Number

    // রিকোয়েস্ট কিউ প্রসেস করার মেথড
    // Method to process request queue
    // এই মেথড নিশ্চিত করে যে রিকোয়েস্টগুলো যেভাবে আসে সেভাবেই প্রসেস হবে (FIFO)
    // This method ensures requests are processed in the order they arrive (FIFO)
    // এবং একবারে শুধুমাত্র একটি SIM প্রসেস করবে - একটি সম্পূর্ণ হওয়ার পর 30 সেকেন্ড অপেক্ষা করে আরেকটি শুরু হবে
    // And only one SIM will process at a time - after one completes, wait 30 seconds before starting another
    private synchronized void processNextInQueue(int simSlotId) {
        Queue<RequestData> queue;
        boolean isProcessing;

        // SIM স্লট অনুযায়ী কিউ এবং প্রসেসিং ফ্ল্যাগ সিলেক্ট করুন
        // Select queue and processing flag based on SIM slot
        if (simSlotId == sim1Id) {
            queue = requestQueueSim1;
            isProcessing = isProcessingSim1;
        } else if (simSlotId == sim2Id) {
            queue = requestQueueSim2;
            isProcessing = isProcessingSim2;
        } else {
            return;
        }

        // যদি এই SIM ইতিমধ্যে প্রসেস করছে, তাহলে কিছু করবেন না
        // If this SIM is already processing, do nothing
        if (isProcessing) {
            return;
        }

        // কিউ খালি হলে কিছু করবেন না
        // If queue is empty, do nothing
        if (queue.isEmpty()) {
            return;
        }

        // যদি অন্য SIM প্রসেস করছে, তাহলে অপেক্ষা করুন
        // If another SIM is processing, wait
        if (isAnySimProcessing) {
            // অন্য SIM প্রসেস করছে, তাই এখন কিছু করবেন না
            // Another SIM is processing, so do nothing now
            return;
        }

        // যদি অন্য SIM আগে প্রসেস করেছে, তাহলে onRequestCompleted() মেথডে 30 সেকেন্ড অপেক্ষা করা হবে
        // If another SIM processed before, 30 seconds wait will be handled in onRequestCompleted() method

        // গ্লোবাল লক সেট করুন
        // Set global lock
        isAnySimProcessing = true;
        lastProcessedSimId = simSlotId;

        // প্রসেসিং ফ্ল্যাগ সেট করুন (আগে চেক করার পর)
        // Set processing flag (after checking)
        if (simSlotId == sim1Id) {
            isProcessingSim1 = true;
        } else {
            isProcessingSim2 = true;
        }

        // কিউ থেকে প্রথম রিকোয়েস্ট নিন (FIFO - প্রথমে আসা প্রথমে যাবে)
        // Get first request from queue (FIFO - First In First Out)
        RequestData request = queue.poll();

        if (request == null) {
            // যদি রিকোয়েস্ট না থাকে, প্রসেসিং ফ্ল্যাগ রিসেট করুন
            // If no request, reset processing flag
            if (simSlotId == sim1Id) {
                isProcessingSim1 = false;
            } else {
                isProcessingSim2 = false;
            }
            isAnySimProcessing = false;
            return;
        }

        // রিকোয়েস্ট প্রসেস করুন (পুরোনো রিকোয়েস্ট আগে প্রসেস হবে)
        // Process the request (older requests will be processed first)
        processRequest(request);
    }

    // রিকোয়েস্ট প্রসেস করার মেথড
    // Method to process a request
    private void processRequest(RequestData request) {
        String service = request.service;
        String sid = request.sid;
        String pcode = request.pcode;
        // Financial request fields must be validated as-is. We normalize formatting,
        // but we never guess, auto-correct, or swap phone and amount values.
        String phone = normalizeBdPhone(request.phone);
        String amount = normalizeAmountValue(request.amount);
        String type = request.type;
        String package_name = request.package_name;
        boolean isPowerLoad = request.isPowerLoad;
        int simSlotId = request.simSlotId;
        String simPin = request.simPin;

        if (!looksLikeBdPhone(phone)) {
            Log.e("USD_INFO", "Rejected request due to invalid phone. service=" + service
                    + ", sid=" + sid + ", phone=" + request.phone + ", amount=" + request.amount);
            if (simSlotId == Constant.sim1Slot) sim_number = session.getData(Session.SIM1_NUMBER);
            if (simSlotId == Constant.sim2Slot) sim_number = session.getData(Session.SIM2_NUMBER);
            InsertNewPopUpMessage("Invalid phone number: " + request.phone, sid, "ValidationError", sim_number, simSlotId);
            onRequestCompleted(simSlotId);
            return;
        }

        if (!amount.matches("\\d+(\\.\\d+)?")) {
            Log.e("USD_INFO", "Rejected request due to invalid amount. service=" + service
                    + ", sid=" + sid + ", phone=" + request.phone + ", amount=" + request.amount);
            if (simSlotId == Constant.sim1Slot) sim_number = session.getData(Session.SIM1_NUMBER);
            if (simSlotId == Constant.sim2Slot) sim_number = session.getData(Session.SIM2_NUMBER);
            InsertNewPopUpMessage("Invalid amount: " + request.amount, sid, "ValidationError", sim_number, simSlotId);
            onRequestCompleted(simSlotId);
            return;
        }

        System.out.println("====PROCESS_REQ: service=" + service + " phone=" + phone + " amount=" + amount + " pcode=" + pcode);

        // সেবা অনুযায়ী প্রসেস করুন
        // Process according to service
        switch (service) {
            case "Grameen":
                if (isPowerLoad && !pcode.equals("SK"))
                    packageLoadSent("*444*", "*444*", sid, package_name, phone, amount, type, simSlotId, simPin, service);
                else
                    GrameenPhoneLoadSend(sid, pcode, phone, amount, type, simSlotId, simPin);
                break;
            case "Robi":
                if (isPowerLoad)
                    packageLoadSent("*888*", "*888*", sid, package_name, phone, amount, type, simSlotId, simPin, service);
                else
                    RobiLoadSent(sid, pcode, phone, amount, type, simSlotId, simPin);
                break;
            case "Airtel":
                if (isPowerLoad)
                    packageLoadSent("*888*", "*888*", sid, package_name, phone, amount, type, simSlotId, simPin, service);
                else
                    AirtelLoadSent(sid, pcode, phone, amount, type, simSlotId, simPin);
                break;
            case "bLink":
                if (isPowerLoad)
                    packageLoadSent("*555*", "*555*", sid, package_name, phone, amount, type, simSlotId, simPin, service);
                else
                    BanglalinkLoadSent(sid, pcode, phone, amount, type, simSlotId, simPin);
                break;
            case "Taletalk":
                TaletalkLoadSend(sid, pcode, phone, amount, type, simSlotId, simPin);
                break;
            case "bKash-Personal-SIM":
                if (pcode.equals("BKS"))
                    bKashSendMoney(sid, phone, amount, simSlotId, simPin);
                if (pcode.equals("BKA"))
                    bKashCashOut(sid, phone, amount, simSlotId, simPin);
                break;
            case "bKash-Agent-SIM":
                if (pcode.equals("BK")) {
                    System.out.println("====BK_CASHIN: phone=" + phone + " amount=" + amount + " (before call)");
                    bKashCashIn(sid, phone, amount, simSlotId, simPin);
                }
                break;
            case "Roket-Personal-SIM":
                if (pcode.equals("RKS"))
                    RoketSendMoney(sid, phone, amount, simSlotId, simPin);
                if (pcode.equals("RKA"))
                    RoketCashOut(sid, phone, amount, simSlotId, simPin);
                break;
            case "Roket-Agent-SIM":
                if (pcode.equals("RK"))
                    RoketCashIn(sid, phone, amount, simSlotId, simPin);
                break;
            case "Nagad-Personal-SIM":
                if (pcode.equals("NGA"))
                    NagadCashOut(sid, phone, amount, simSlotId, simPin);
                if (pcode.equals("NGS"))
                    NagadSendMoney(sid, phone, amount, simSlotId, simPin);
                break;
            case "Nagad-Agent-SIM":
                if (pcode.equals("NG"))
                    NagadCashIn(sid, phone, amount, simSlotId, simPin);
                break;
            case "bKash-Load":
                bKashLoad(sid, pcode, phone, amount, simSlotId, simPin);
                break;
            case "Nagad-Load":
                NagadLoad(sid, pcode, phone, amount, simSlotId, simPin);
                break;
            default:
                // যদি কোনো সেবা মিলে না, প্রসেসিং ফ্ল্যাগ রিসেট করুন এবং পরবর্তী রিকোয়েস্ট প্রসেস করুন
                // If no service matches, reset processing flag and process next request
                if (simSlotId == sim1Id) {
                    isProcessingSim1 = false;
                } else {
                    isProcessingSim2 = false;
                }
                processNextInQueue(simSlotId);
                break;
        }
    }

    // রিকোয়েস্ট সম্পন্ন হলে কল করুন - পরবর্তী রিকোয়েস্ট প্রসেস করার জন্য
    // Call this when request is completed - to process next request
    private void onRequestCompleted(int simSlotId) {
        // প্রসেসিং ফ্ল্যাগ রিসেট করুন
        // Reset processing flag
        if (simSlotId == sim1Id) {
            isProcessingSim1 = false;
        } else if (simSlotId == sim2Id) {
            isProcessingSim2 = false;
        }

        // একই SIM এর পরবর্তী রিকোয়েস্ট আছে কিনা চেক করুন
        // Check if there are more requests from the same SIM
        Queue<RequestData> currentSimQueue = (simSlotId == sim1Id) ? requestQueueSim1 : requestQueueSim2;
        boolean hasMoreRequestsFromSameSim = !currentSimQueue.isEmpty();

        if (hasMoreRequestsFromSameSim) {
            // একই SIM এর আরো রিকোয়েস্ট আছে - গ্লোবাল লক রিসেট করুন (30 সেকেন্ড অপেক্ষার আগে)
            // More requests from same SIM - reset global lock (before 30 second wait)
            isAnySimProcessing = false;

            // 30 সেকেন্ড অপেক্ষা করে পরবর্তী রিকোয়েস্ট প্রসেস করুন
            // Wait 30 seconds and then process next request
            handler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    // processNextInQueue method নিজেই সব check করবে, তাই সরাসরি call করুন
                    // processNextInQueue method will check everything itself, so call it directly
                    processNextInQueue(simSlotId);
                }
            }, simProcessingDelay); // 30 সেকেন্ড অপেক্ষা / Wait 30 seconds
        } else {
            // একই SIM এর আরো রিকোয়েস্ট নেই - গ্লোবাল লক রিসেট করুন
            // No more requests from same SIM - reset global lock
            isAnySimProcessing = false;

            // Interval time (default 30 সেকেন্ড) অপেক্ষা করে পরবর্তী pending number fetch করুন
            // Wait interval time (default 30 seconds) and then fetch next pending number
            handler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    // Status switch চেক করুন - শুধুমাত্র যদি ON থাকে তাহলে getNewPending কল করুন
                    // Check status switch - only call getNewPending if it's ON
                    if (simSlotId == sim1Id) {
                        if (activityMainBinding.status1Sw.isChecked()) {
                            getNewPending(savedSim1ServiceName, savedSim1ServiceCode, session.getData(Session.SIM1_NUMBER), getSim1Bal, String.valueOf(sim1Id), savedSim1Pin);
                        }
                    } else if (simSlotId == sim2Id) {
                        if (activityMainBinding.status2Sw.isChecked()) {
                            getNewPending(savedSim2ServiceName, savedSim2ServiceCode, session.getData(Session.SIM2_NUMBER), getSim2Bal, String.valueOf(sim2Id), savedSim2Pin);
                        }
                    }
                }
            }, getTimerTime()); // Interval time ব্যবহার করুন (default 30 সেকেন্ড) / Use interval time (default 30 seconds)
        }
    }

    private  void stopSimProgress(int simId){
        if (simId == sim1Id) _isWorkSim1 = false;
        if (simId == sim2Id) _isWorkSim2 = false;
    }

    //region All SIM Balance Query
    private void queryForSetSimWithBalance(String savedSimServiceName, String simNumber, String simPin, int simId) {
        if (simId == sim1Id) _isWorkSim1 = true;
        if (simId == sim2Id) _isWorkSim2 = true;
        Log.d("USD_INFO", savedSimServiceName );
        Log.d("USD_INFO", simNumber );
        Log.d("USD_INFO", simPin );
//        Log.d("USD_INFO_SIM", simId.t );
        switch (savedSimServiceName) {
            case "Grameen":
                getGrameenLoadBalance(simNumber, simPin, simId);
                break;
            case "Skitto":
            case "bLink":
                Toast.makeText(this, savedSimServiceName + " not found balance check ussd dial code", Toast.LENGTH_SHORT).show();
                break;
            case "Robi":
                getRobiEazyLoadBalance(simNumber, simPin, simId);
                break;
            case "Airtel":
                getAirtelLoadBalance(simNumber, simPin, simId);
                break;
            case "Taletalk":
                TaletalkSimBalance(simPin, simId);


                break;

            //region bKash Option
            case "bKash-Personal-SIM":
            case "bKash-Agent-SIM":
            case "bKash-Load":
                bKashBalanceCheck(simPin, simId);
                break;
            //endregion bKash Option

            //region Rocket Option
            case "Roket-Personal-SIM":
            case "Roket-Agent-SIM":
                RocketBalanceCheck(simPin, simId);
                break;
            //endregion Roket Option

            //region Nagad Option
            case "Nagad-Personal-SIM":
            case "Nagad-Agent-SIM":
            case "Nagad-Load":
                NagadBalanceCheck(simPin, simId);
                break;
            //endregion
            default:
                Toast.makeText(this, "No ussd dial code found", Toast.LENGTH_SHORT).show();
                stopSimProgress(simId);
                break;
        }
        // Note: stopSimProgress is NOT called here globally because balance check is async
        // Each balance check method will handle completion via callGetNewPendingAfterBalanceCheck
    }


    //endregion All SIM Balance Query

    // Helper method to call getNewPending after balance check completes
    // Balance check সম্পন্ন হওয়ার পর getNewPending কল করার helper method
    private void callGetNewPendingAfterBalanceCheck(int simId) {
        // Status switch চেক করুন - শুধুমাত্র যদি ON থাকে তাহলে getNewPending কল করুন
        // Check status switch - only call getNewPending if it's ON
        if (simId == sim1Id) {
            if (activityMainBinding.status1Sw.isChecked()) {
                handler.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        // Status switch এখনো চালু আছে কিনা এবং queue খালি আছে কিনা চেক করুন
                        // Check if status switch is still ON and queue is empty
                        if (activityMainBinding.status1Sw.isChecked() && !isProcessingSim1 && requestQueueSim1.isEmpty()) {
                            getNewPending(savedSim1ServiceName, savedSim1ServiceCode, session.getData(Session.SIM1_NUMBER), getSim1Bal, String.valueOf(sim1Id), savedSim1Pin);
                        }
                    }
                }, getTimerTime()); // Interval time ব্যবহার করুন (default 30 সেকেন্ড) / Use interval time (default 30 seconds)
            }
        } else if (simId == sim2Id) {
            if (activityMainBinding.status2Sw.isChecked()) {
                handler.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        // Status switch এখনো চালু আছে কিনা এবং queue খালি আছে কিনা চেক করুন
                        // Check if status switch is still ON and queue is empty
                        if (activityMainBinding.status2Sw.isChecked() && !isProcessingSim2 && requestQueueSim2.isEmpty()) {
                            getNewPending(savedSim2ServiceName, savedSim2ServiceCode, session.getData(Session.SIM2_NUMBER), getSim2Bal, String.valueOf(sim2Id), savedSim2Pin);
                        }
                    }
                }, getTimerTime()); // Interval time ব্যবহার করুন (default 30 সেকেন্ড) / Use interval time (default 30 seconds)
            }
        }
    }

    //All Operation and service's balance reload methods


    //region Robi
    private void getRobiEazyLoadBalance(String phoneNumber, String simPinCode, int simId) {
        String phone = phoneNumber.substring(1);
        String usssCod = "*8383*5*" + phone + "*" + simPinCode + "#";
        ussdApi.callUSSDInvoke(usssCod, simId, map, new USSDController.CallbackInvoke() {
            @Override
            public void responseInvoke(String message) {
                ussdApi.cancel();
                // Balance check সম্পন্ন - এখন getNewPending কল করুন
                // Balance check completed - now call getNewPending
                callGetNewPendingAfterBalanceCheck(simId);
            }

            @Override
            public void over(String message) {

            }
        });
        stopSimProgress(simId);
    }

    private void RobiLoadSent(String sid, String pcode, String phone, String amount, String type, int simSlotId, String simPin) {
        String phonecodedial = "";

        if (type.equals("1")) {
            phonecodedial = "*8383*2*" + phone + "*" + amount + "*" + simPin + "#";
        }
        if (type.equals("0")) {
            phonecodedial = "*8383*3*" + phone + "*" + amount + "*" + simPin + "#";
        }
        System.out.println("Phonecode " + phonecodedial);
        ussdApi.callUSSDInvoke(phonecodedial, simSlotId, map, new USSDController.CallbackInvoke() {
            @Override
            public void responseInvoke(String message) {
                ussdApi.cancel();
                InsertNewPopUpMessage(message.replaceAll(System.lineSeparator(), " "), sid, "FlashMessage", sim_number, simSlotId);
                // রিকোয়েস্ট সম্পন্ন - পরবর্তী রিকোয়েস্ট প্রসেস করুন
                // Request completed - process next request
                onRequestCompleted(simSlotId);
            }

            @Override
            public void over(String message) {
                Log.e("USSD_ERROR", "RobiLoadSent Error: " + message);
                Toast.makeText(MainActivity.this, "USSD Error: " + message, Toast.LENGTH_LONG).show();
                // Error হলে প্রসেসিং ফ্ল্যাগ রিসেট করুন এবং পরবর্তী রিকোয়েস্ট প্রসেস করুন
                // Reset processing flag on error and process next request
                onRequestCompleted(simSlotId);
            }
        });
    }
    //endregion Robi

    //region Airtel
    private void getAirtelLoadBalance(String phoneNumber, String simPinCode, int simId) {
        String usssCod = "*444*4*" + simPinCode + "#";
        ussdApi.callUSSDInvoke(usssCod, simId, map, new USSDController.CallbackInvoke() {
            @Override
            public void responseInvoke(String message) {
                ussdApi.cancel();
                // Balance check সম্পন্ন - এখন getNewPending কল করুন
                // Balance check completed - now call getNewPending
                callGetNewPendingAfterBalanceCheck(simId);
            }

            @Override
            public void over(String message) {

            }
        });
        stopSimProgress(simId);
    }

    private void AirtelLoadSent(String sid, String pcode, String phone, String amount, String type, int simSlotId, String simPin) {
        String phonecodedial = "";
        if (type.equals("1")) {
            phonecodedial = "*444*1*" + phone + "*" + amount + "*" + simPin + "#";
        }
        if (type.equals("0")) {
            phonecodedial = "*444*10*" + phone + "*" + amount + "*" + simPin + "#";
        }

        ussdApi.callUSSDInvoke(phonecodedial, simSlotId, map, new USSDController.CallbackInvoke() {
            @Override
            public void responseInvoke(String message) {
                ussdApi.cancel();
                InsertNewPopUpMessage(message.replaceAll(System.lineSeparator(), " "), sid, "FlashMessage", sim_number, simSlotId);
                // রিকোয়েস্ট সম্পন্ন - পরবর্তী রিকোয়েস্ট প্রসেস করুন
                // Request completed - process next request
                onRequestCompleted(simSlotId);
            }

            @Override
            public void over(String message) {
                Log.e("USSD_ERROR", "AirtelLoadSent Error: " + message);
                Toast.makeText(MainActivity.this, "USSD Error: " + message, Toast.LENGTH_LONG).show();
                // Error হলে প্রসেসিং ফ্ল্যাগ রিসেট করুন এবং পরবর্তী রিকোয়েস্ট প্রসেস করুন
                // Reset processing flag on error and process next request
                onRequestCompleted(simSlotId);
            }
        });
    }
    //endregion Airtel

    //region Banglalink
    private void getBanglalinkBalance(String phoneNumber, String simPincode, int simId) {

    }

    private void BanglalinkLoadSent(String sid, String pcode, String phone, String amount, String type, int simSlotId, String simPin) {
        String phonecodedial = "";

        if (type.equals("1")) {
            phonecodedial = "*555*" + phone + "*" + amount + "*0*" + simPin + "#";
        }
        if (type.equals("0")) {
            phonecodedial = "*566*" + phone + "*" + amount + "*" + simPin + "#";
        }

        ussdApi.callUSSDInvoke(phonecodedial, simSlotId, map, new USSDController.CallbackInvoke() {
            @Override
            public void responseInvoke(String message) {
                ussdApi.send("0", new USSDController.CallbackMessage() {
                    @Override
                    public void responseMessage(String message) {
                        ussdApi.cancel();
                        InsertNewPopUpMessage(message.replaceAll(System.lineSeparator(), " "), sid, "FlashMessage", sim_number, simSlotId);
                        // রিকোয়েস্ট সম্পন্ন - পরবর্তী রিকোয়েস্ট প্রসেস করুন
                        // Request completed - process next request
                        onRequestCompleted(simSlotId);
                    }
                });
            }

            @Override
            public void over(String message) {

            }
        });
    }
    //endregion Banglalink

    //region GP
    private void getGrameenLoadBalance(String phoneNumber, String simPinCode, int simId) {
        String usssCod = "*444#";
        ussdApi.callUSSDInvoke(usssCod, simId, map, new USSDController.CallbackInvoke() {
            @Override
            public void responseInvoke(String message) {
                ussdApi.cancel();
                // Balance check সম্পন্ন - এখন getNewPending কল করুন
                // Balance check completed - now call getNewPending
                callGetNewPendingAfterBalanceCheck(simId);
            }

            @Override
            public void over(String message) {

            }
        });
        stopSimProgress(simId);
    }

    private void GrameenPhoneLoadSend(String sid, String pcode, String phone, String amount, String type, int simSlotId, String simPin) {
        String phonecodedial = null;
        if (pcode.equals("SK")) {
            phonecodedial = "*666*" + phone + "*" + amount + "*" + simPin + "#";
        } else {
            phonecodedial = "*444*" + phone + "*" + amount + "*0*" + simPin + "#";
        }
        // GP: Replace 121*4# with 121*7#
        phonecodedial = phonecodedial.replace("121*4#", "121*7#").replace("*121*4#", "*121*7#");
        System.out.println("===phonecode " + phonecodedial);
        ussdApi.callUSSDInvoke(phonecodedial, simSlotId, map, new USSDController.CallbackInvoke() {
            @Override
            public void responseInvoke(String message) {
                ussdApi.cancel();
                InsertNewPopUpMessage(message.replaceAll(System.lineSeparator(), " "), sid, "FlashMessage", sim_number, simSlotId);
                // রিকোয়েস্ট সম্পন্ন - পরবর্তী রিকোয়েস্ট প্রসেস করুন
                // Request completed - process next request
                onRequestCompleted(simSlotId);
            }

            @Override
            public void over(String message) {
                Log.e("USSD_ERROR", "GrameenPhoneLoadSend Error: " + message);
                Toast.makeText(MainActivity.this, "USSD Error: " + message, Toast.LENGTH_LONG).show();
                // Error হলে প্রসেসিং ফ্ল্যাগ রিসেট করুন এবং পরবর্তী রিকোয়েস্ট প্রসেস করুন
                // Reset processing flag on error and process next request
                onRequestCompleted(simSlotId);
            }
        });
    }
    //endregion GP

    //region Taletalk
    private void TaletalkSimBalance(String simPinCode, int simId) {
        String usssCod = "*254*" + simPinCode + "#";
        ussdApi.callUSSDInvoke(usssCod, simId, map, new USSDController.CallbackInvoke() {
            @Override
            public void responseInvoke(String message) {
                ussdApi.cancel();
                // Balance check সম্পন্ন - এখন getNewPending কল করুন
                // Balance check completed - now call getNewPending
                callGetNewPendingAfterBalanceCheck(simId);
            }

            @Override
            public void over(String message) {

            }
        });
        stopSimProgress(simId);
    }

    private void TaletalkLoadSend(String sid, String pcode, String phone, String amount, String type, int simSlotId, String simPin) {
        String phonecodedial = "*250*" + phone + "*" + amount + "*" + simPin + "#";

        ussdApi.callUSSDInvoke(phonecodedial, simSlotId, map, new USSDController.CallbackInvoke() {
            @Override
            public void responseInvoke(String message) {
                ussdApi.cancel();
                // রিকোয়েস্ট সম্পন্ন - পরবর্তী রিকোয়েস্ট প্রসেস করুন
                // Request completed - process next request
                onRequestCompleted(simSlotId);
            }

            @Override
            public void over(String message) {

            }
        });
    }
    //endregion Taletalk

    //region bKash
    private void bKashBalanceCheck(String PinCode, int SimID) {
        String ServiceName = "";
        if (sim1Id == SimID) {
            ServiceName = savedSim1ServiceName;
        }
        if (sim2Id == SimID) {
            ServiceName = savedSim2ServiceName;
        }

        if (ServiceName.equals("bKash-Load") || ServiceName.equals("bKash-Agent-SIM") || ServiceName.equals("bKash-Personal-SIM")) {
            if (PinCode == null || PinCode.trim().isEmpty()) {
                Toast.makeText(this, "bKash: Settings থেকে PIN সেভ করুন", Toast.LENGTH_SHORT).show();
                callGetNewPendingAfterBalanceCheck(SimID);
                return;
            }
            String[] keyString = {"My bKash", "Check Balance", "Balance"};
            ussdApi.callUSSDInvoke("*247#", SimID, map, new USSDController.CallbackInvoke() {
                @Override
                public void responseInvoke(String message) {
                    Log.d("_USD_ERROR_",message);
                    if (!message.isEmpty()) {
                        ussdSendForBalance(message, keyString, PinCode, SimID, ussdApi);
                    }
                }

                @Override
                public void over(String message) {
                    Log.d("_USD_ERROR_"," Over: "+message);
                }
            });
        }
    }

    private void bKashSendMoney(String flexiId, String phone, String amount, int simSlotId, String simPin) {

        ussdApi.callUSSDInvoke("*247#", simSlotId, map, new USSDController.CallbackInvoke() {
            @Override
            public void responseInvoke(String message) {
                ussdApi.send("1", new USSDController.CallbackMessage() {
                    @Override
                    public void responseMessage(String message) {
                        ussdApi.send(phone, new USSDController.CallbackMessage() {
                            @Override
                            public void responseMessage(String message) {
                                ussdApi.send(amount, new USSDController.CallbackMessage() {
                                    @Override
                                    public void responseMessage(String message) {
                                        ussdApi.send(flexiId, new USSDController.CallbackMessage() {
                                            @Override
                                            public void responseMessage(String message) {
                                                ussdApi.send(simPin, new USSDController.CallbackMessage() {
                                                    @Override
                                                    public void responseMessage(String message) {
                                                        ussdApi.cancel();
                                                        if (simSlotId == Constant.sim1Slot) {
                                                            sim_number = session.getData(Session.SIM1_NUMBER);
                                                        }

                                                        if (simSlotId == Constant.sim2Slot) {
                                                            sim_number = session.getData(Session.SIM2_NUMBER);
                                                        }
                                                        InsertNewPopUpMessage(message.replaceAll(System.lineSeparator(), " "), flexiId, "FlashMessage", sim_number, simSlotId);
                                                        // রিকোয়েস্ট সম্পন্ন - পরবর্তী রিকোয়েস্ট প্রসেস করুন
                                                        // Request completed - process next request
                                                        onRequestCompleted(simSlotId);
                                                    }
                                                });
                                            }
                                        });
                                    }
                                });
                            }
                        });
                    }
                });
            }

            @Override
            public void over(String message) {

            }
        });
    }

    private void bKashCashOut(String flexiId, String phone, String amount, int simSlotId, String simPin) {
        ussdApi.callUSSDInvoke("*247#", simSlotId, map, new USSDController.CallbackInvoke() {
            @Override
            public void responseInvoke(String message) {
                ussdApi.send("5", new USSDController.CallbackMessage() {
                    @Override
                    public void responseMessage(String message) {
                        ussdApi.send("1", new USSDController.CallbackMessage() {
                            @Override
                            public void responseMessage(String message) {
                                ussdApi.send(phone, new USSDController.CallbackMessage() {
                                    @Override
                                    public void responseMessage(String message) {
                                        ussdApi.send(amount, new USSDController.CallbackMessage() {
                                            @Override
                                            public void responseMessage(String message) {
                                                ussdApi.send(simPin, new USSDController.CallbackMessage() {
                                                    @Override
                                                    public void responseMessage(String message) {
//                                                updateSimBalanceTv(Constant.getSimBalance(message), simSlotId);
                                                        ussdApi.cancel();
                                                        if (simSlotId == Constant.sim1Slot) {

                                                            sim_number = session.getData(Session.SIM1_NUMBER);
                                                        }

                                                        if (simSlotId == Constant.sim2Slot) {
                                                            sim_number = session.getData(Session.SIM2_NUMBER);
                                                        }
                                                        InsertNewPopUpMessage(message.replaceAll(System.lineSeparator(), " "), flexiId, "FlashMessage", sim_number, simSlotId);
                                                        // রিকোয়েস্ট সম্পন্ন - পরবর্তী রিকোয়েস্ট প্রসেস করুন
                                                        // Request completed - process next request
                                                        onRequestCompleted(simSlotId);
                                                    }
                                                });
                                            }
                                        });
                                    }
                                });
                            }
                        });
                    }
                });
            }

            @Override
            public void over(String message) {

            }
        });
    }

    private void bKashCashIn(String flexiId, String phone, String amount, int simSlotId, String simPin) {
        // phone = actual mobile number, amount = actual taka value (normalised by processRequest)
        // Flow: *247# -> 1 (Cash In) -> phone (number) -> amount (taka) -> PIN
        System.out.println("====BK_CASHIN_USSD: *247# -> 1 -> number=" + phone + " -> taka=" + amount + " -> PIN=" + simPin);
        ussdApi.callUSSDInvoke("*247#", simSlotId, map, new USSDController.CallbackInvoke() {
            @Override
            public void responseInvoke(String message) {
                System.out.println("====BK_STEP1: dial *247# response=" + message);
                // Step 1: Select Cash In (1)
                ussdApi.send("1", new USSDController.CallbackMessage() {
                    @Override
                    public void responseMessage(String message) {
                        System.out.println("====BK_STEP2: sent 1 (Cash In) response=" + message);
                        // Step 2: Enter mobile number
                        ussdApi.send(phone, new USSDController.CallbackMessage() {
                            @Override
                            public void responseMessage(String message) {
                                System.out.println("====BK_STEP3: sent number=" + phone + " response=" + message);
                                // Step 3: Enter amount/taka
                                ussdApi.send(amount, new USSDController.CallbackMessage() {
                                    @Override
                                    public void responseMessage(String message) {
                                        System.out.println("====BK_STEP4: sent taka=" + amount + " response=" + message);
                                        // Step 4: Enter PIN to complete
                                        ussdApi.send(simPin, new USSDController.CallbackMessage() {
                                            @Override
                                            public void responseMessage(String message) {
                                                System.out.println("====BK_STEP5: sent PIN, final response=" + message);
                                                String cleanMessage = message == null ? "" : message.replaceAll(System.lineSeparator(), " ").trim();
                                                ussdApi.cancel();
                                                if (simSlotId == Constant.sim1Slot) {
                                                    sim_number = session.getData(Session.SIM1_NUMBER);
                                                }
                                                if (simSlotId == Constant.sim2Slot) {
                                                    sim_number = session.getData(Session.SIM2_NUMBER);
                                                }
                                                InsertNewPopUpMessage(cleanMessage, flexiId, "FlashMessage", sim_number, simSlotId);
                                                onRequestCompleted(simSlotId);
                                            }
                                        });
                                    }
                                });
                            }
                        });
                    }
                });
            }

            @Override
            public void over(String message) {
                System.out.println("====BK_OVER: USSD session ended early. msg=" + message);
                // USSD session ended early - send result and complete request
                String cleanMessage = message == null ? "" : message.replaceAll(System.lineSeparator(), " ").trim();
                if (!cleanMessage.isEmpty()) {
                    if (simSlotId == Constant.sim1Slot) {
                        sim_number = session.getData(Session.SIM1_NUMBER);
                    }
                    if (simSlotId == Constant.sim2Slot) {
                        sim_number = session.getData(Session.SIM2_NUMBER);
                    }
                    InsertNewPopUpMessage(cleanMessage, flexiId, "FlashMessage", sim_number, simSlotId);
                }
                onRequestCompleted(simSlotId);
            }
        });
    }
    //endregion bKash

    //region Roket
    private void RocketBalanceCheck(String PinCode, int SimID) {
        String ServiceName = "";
        if (sim1Id == SimID) {
            ServiceName = savedSim1ServiceName;
        }
        if (sim2Id == SimID) {
            ServiceName = savedSim2ServiceName;
        }

        if (ServiceName.equals("Roket-Agent-SIM") || ServiceName.equals("Roket-Personal-SIM")) {
            if (PinCode == null || PinCode.trim().isEmpty()) {
                Toast.makeText(this, "Rocket: Settings থেকে PIN সেভ করুন", Toast.LENGTH_SHORT).show();
                callGetNewPendingAfterBalanceCheck(SimID);
                return;
            }
            String[] keyString = {"My Acc", "Balance", "Balance Enquiry"};
            ussdApi.callUSSDInvoke("*322#", SimID, map, new USSDController.CallbackInvoke() {
                @Override
                public void responseInvoke(String message) {
                    if (message.contains("Do you want to continue?") || message.contains("Resume session?")) {
                        ussdApi.send("2", new USSDController.CallbackMessage() {
                            @Override
                            public void responseMessage(String newMsg) {
                                if (!newMsg.isEmpty()) {
                                    ussdSendForBalance(newMsg, keyString, PinCode, SimID, ussdApi);
                                }
                            }
                        });
                    } else if (!message.isEmpty()) {
                        ussdSendForBalance(message, keyString, PinCode, SimID, ussdApi);
                    }
                }

                @Override
                public void over(String message) {

                }
            });
        }
    }

    private void RoketSendMoney(String flexiId, String phone, String amount, int simSlotId, String simPin) {

        ussdApi.callUSSDInvoke("*322#", simSlotId, map, new USSDController.CallbackInvoke() {
            @Override
            public void responseInvoke(String message) {
                if (message.contains("Do you want to continue?")) {
                    ussdApi.send("2", new USSDController.CallbackMessage() {
                        @Override
                        public void responseMessage(String message) {

                        }
                    });
                }
                ussdApi.send("2", new USSDController.CallbackMessage() {
                    @Override
                    public void responseMessage(String message) {
                        ussdApi.send(phone, new USSDController.CallbackMessage() {
                            @Override
                            public void responseMessage(String message) {
                                ussdApi.send(amount, new USSDController.CallbackMessage() {
                                    @Override
                                    public void responseMessage(String message) {
                                        ussdApi.send(simPin, new USSDController.CallbackMessage() {
                                            @Override
                                            public void responseMessage(String message) {
                                                if (message.contains("LogOut")) {
                                                    ussdApi.send("0", new USSDController.CallbackMessage() {
                                                        @Override
                                                        public void responseMessage(String message) {
                                                            ussdApi.cancel();
                                                        }
                                                    });
                                                }
                                                ussdApi.cancel();
                                                if (simSlotId == Constant.sim1Slot) {
                                                    sim_number = session.getData(Session.SIM1_NUMBER);
                                                }

                                                if (simSlotId == Constant.sim2Slot) {
                                                    sim_number = session.getData(Session.SIM2_NUMBER);
                                                }
                                                InsertNewPopUpMessage(message.replaceAll(System.lineSeparator(), " "), flexiId, "FlashMessage", sim_number, simSlotId);
                                                // রিকোয়েস্ট সম্পন্ন - পরবর্তী রিকোয়েস্ট প্রসেস করুন
                                                // Request completed - process next request
                                                onRequestCompleted(simSlotId);
                                            }
                                        });
                                    }
                                });
                            }
                        });
                    }
                });
            }

            @Override
            public void over(String message) {

            }
        });
    }

    private void RoketCashOut(String flexiId, String phone, String amount, int simSlotId, String simPin) {
        ussdApi.callUSSDInvoke("*322#", simSlotId, map, new USSDController.CallbackInvoke() {
            @Override
            public void responseInvoke(String message) {
                if (message.contains("Do you want to continue?")) {
                    ussdApi.send("2", new USSDController.CallbackMessage() {
                        @Override
                        public void responseMessage(String message) {

                        }
                    });
                }
                ussdApi.send("7", new USSDController.CallbackMessage() {
                    @Override
                    public void responseMessage(String message) {
                        ussdApi.send("1", new USSDController.CallbackMessage() {
                            @Override
                            public void responseMessage(String message) {
                                ussdApi.send(phone, new USSDController.CallbackMessage() {
                                    @Override
                                    public void responseMessage(String message) {
                                        ussdApi.send(amount, new USSDController.CallbackMessage() {
                                            @Override
                                            public void responseMessage(String message) {
                                                ussdApi.send(simPin, new USSDController.CallbackMessage() {
                                                    @Override
                                                    public void responseMessage(String message) {
                                                        if (message.contains("LogOut")) {
                                                            ussdApi.send("0", new USSDController.CallbackMessage() {
                                                                @Override
                                                                public void responseMessage(String message) {
                                                                    ussdApi.cancel();
                                                                }
                                                            });
                                                        }
                                                        ussdApi.cancel();
                                                        if (simSlotId == Constant.sim1Slot) {
                                                            sim_number = session.getData(Session.SIM1_NUMBER);
                                                        }

                                                        if (simSlotId == Constant.sim2Slot) {
                                                            sim_number = session.getData(Session.SIM2_NUMBER);
                                                        }
                                                        InsertNewPopUpMessage(message.replaceAll(System.lineSeparator(), " "), flexiId, "FlashMessage", sim_number, simSlotId);
                                                    }
                                                });
                                            }
                                        });
                                    }
                                });
                            }
                        });
                    }
                });
            }

            @Override
            public void over(String message) {

            }
        });
    }

    private void RoketCashIn(String flexiId, String phone, String amount, int simSlotId, String simPin) {
        ussdApi.callUSSDInvoke("*322#", simSlotId, map, new USSDController.CallbackInvoke() {
            @Override
            public void responseInvoke(String message) {
                ussdApi.send("1", new USSDController.CallbackMessage() {
                    @Override
                    public void responseMessage(String message) {
                        ussdApi.send(phone, new USSDController.CallbackMessage() {
                            @Override
                            public void responseMessage(String message) {
                                ussdApi.send(amount, new USSDController.CallbackMessage() {
                                    @Override
                                    public void responseMessage(String message) {
                                        ussdApi.send(simPin, new USSDController.CallbackMessage() {
                                            @Override
                                            public void responseMessage(String message) {
                                                ussdApi.cancel();
                                                if (simSlotId == Constant.sim1Slot) {
                                                    sim_number = session.getData(Session.SIM1_NUMBER);
                                                }

                                                if (simSlotId == Constant.sim2Slot) {
                                                    sim_number = session.getData(Session.SIM2_NUMBER);
                                                }
                                                InsertNewPopUpMessage(message.replaceAll(System.lineSeparator(), " "), flexiId, "FlashMessage", sim_number, simSlotId);
                                            }
                                        });
                                    }
                                });
                            }
                        });
                    }
                });
            }

            @Override
            public void over(String message) {
            }
        });
    }
    //endregion Rocket

    //region Nagad
    private void NagadBalanceCheck(String PinCode, int SimID) {
        String ServiceName = "";
        if (sim1Id == SimID) {
            ServiceName = savedSim1ServiceName;
        }
        if (sim2Id == SimID) {
            ServiceName = savedSim2ServiceName;
        }

        if (ServiceName.equals("Nagad-Load") || ServiceName.equals("Nagad-Agent-SIM") || ServiceName.equals("Nagad-Personal-SIM")) {
            if (PinCode == null || PinCode.trim().isEmpty()) {
                Toast.makeText(this, "Nagad: Settings থেকে PIN সেভ করুন", Toast.LENGTH_SHORT).show();
                callGetNewPendingAfterBalanceCheck(SimID);
                return;
            }
            String[] keyString = {"My Nagad", "Balance Enquiry", "Balance"};
            ussdApi.callUSSDInvoke("*167#", SimID, map, new USSDController.CallbackInvoke() {
                @Override
                public void responseInvoke(String message) {
                    if (message.contains("Do you want to continue?") || message.contains("Resume session?")) {
                        ussdApi.send("2", new USSDController.CallbackMessage() {
                            @Override
                            public void responseMessage(String newMsg) {
                                if (!newMsg.isEmpty()) {
                                    ussdSendForBalance(newMsg, keyString, PinCode, SimID, ussdApi);
                                }
                            }
                        });
                    } else if (!message.isEmpty()) {
                        ussdSendForBalance(message, keyString, PinCode, SimID, ussdApi);
                    }
                }

                @Override
                public void over(String message) {

                }
            });
        }
    }

    private void NagadSendMoney(String flexiId, String phone, String amount, int simSlotId, String simPin) {
        ussdApi.callUSSDInvoke("*167#", simSlotId, map, new USSDController.CallbackInvoke() {
            @Override
            public void responseInvoke(String message) {
                if (message.contains("Do you want to continue?")) {
                    ussdApi.send("2", new USSDController.CallbackMessage() {
                        @Override
                        public void responseMessage(String message) {
                            ussdApi.send("2", new USSDController.CallbackMessage() {
                                @Override
                                public void responseMessage(String message) {
                                    ussdApi.send(phone, new USSDController.CallbackMessage() {
                                        @Override
                                        public void responseMessage(String message) {
                                            ussdApi.send(amount, new USSDController.CallbackMessage() {
                                                @Override
                                                public void responseMessage(String message) {
                                                    ussdApi.send(phone, new USSDController.CallbackMessage() {
                                                        @Override
                                                        public void responseMessage(String message) {
                                                            ussdApi.send(simPin, new USSDController.CallbackMessage() {
                                                                @Override
                                                                public void responseMessage(String message) {
//                                                        updateSimBalanceTv(Constant.getSimBalance(message), simSlotId);
                                                                    ussdApi.cancel();
                                                                    if (simSlotId == Constant.sim1Slot) {
                                                                        sim_number = session.getData(Session.SIM1_NUMBER);
                                                                    }

                                                                    if (simSlotId == Constant.sim2Slot) {
                                                                        sim_number = session.getData(Session.SIM2_NUMBER);
                                                                    }
                                                                    InsertNewPopUpMessage(message.replaceAll(System.lineSeparator(), " "), flexiId, "FlashMessage", sim_number, simSlotId);
                                                                }
                                                            });
                                                        }
                                                    });
                                                }
                                            });
                                        }
                                    });
                                }
                            });
                        }
                    });
                } else {
                    ussdApi.send("2", new USSDController.CallbackMessage() {
                        @Override
                        public void responseMessage(String message) {
                            ussdApi.send(phone, new USSDController.CallbackMessage() {
                                @Override
                                public void responseMessage(String message) {
                                    ussdApi.send(amount, new USSDController.CallbackMessage() {
                                        @Override
                                        public void responseMessage(String message) {
                                            ussdApi.send(phone, new USSDController.CallbackMessage() {
                                                @Override
                                                public void responseMessage(String message) {
                                                    ussdApi.send(simPin, new USSDController.CallbackMessage() {
                                                        @Override
                                                        public void responseMessage(String message) {
//                                                        updateSimBalanceTv(Constant.getSimBalance(message), simSlotId);
                                                            ussdApi.cancel();
                                                            if (simSlotId == Constant.sim1Slot) {
                                                                sim_number = session.getData(Session.SIM1_NUMBER);
                                                            }

                                                            if (simSlotId == Constant.sim2Slot) {
                                                                sim_number = session.getData(Session.SIM2_NUMBER);
                                                            }
                                                            InsertNewPopUpMessage(message.replaceAll(System.lineSeparator(), " "), flexiId, "FlashMessage", sim_number, simSlotId);
                                                        }
                                                    });
                                                }
                                            });
                                        }
                                    });
                                }
                            });
                        }
                    });
                }
            }

            @Override
            public void over(String message) {
            }
        });
    }

    private void NagadCashOut(String flexiId, String phone, String amount, int simSlotId, String simPin) {
        ussdApi.callUSSDInvoke("*167#", simSlotId, map, new USSDController.CallbackInvoke() {
            @Override
            public void responseInvoke(String message) {
                if (message.contains("Do you want to continue?")) {
                    ussdApi.send("2", new USSDController.CallbackMessage() {
                        @Override
                        public void responseMessage(String message) {
                            ussdApi.send("1", new USSDController.CallbackMessage() {
                                @Override
                                public void responseMessage(String message) {
                                    ussdApi.send(phone, new USSDController.CallbackMessage() {
                                        @Override
                                        public void responseMessage(String message) {
                                            ussdApi.send(amount, new USSDController.CallbackMessage() {
                                                @Override
                                                public void responseMessage(String message) {
                                                    ussdApi.send(simPin, new USSDController.CallbackMessage() {
                                                        @Override
                                                        public void responseMessage(String message) {
//                                                updateSimBalanceTv(Constant.getSimBalance(message), simSlotId);
                                                            ussdApi.cancel();
                                                            if (simSlotId == Constant.sim1Slot) {
                                                                sim_number = session.getData(Session.SIM1_NUMBER);
                                                            }

                                                            if (simSlotId == Constant.sim2Slot) {
                                                                sim_number = session.getData(Session.SIM2_NUMBER);
                                                            }
                                                            InsertNewPopUpMessage(message.replaceAll(System.lineSeparator(), " "), flexiId, "FlashMessage", sim_number, simSlotId);
                                                        }
                                                    });
                                                }
                                            });
                                        }
                                    });
                                }
                            });
                        }
                    });
                } else {
                    ussdApi.send("1", new USSDController.CallbackMessage() {
                        @Override
                        public void responseMessage(String message) {
                            ussdApi.send(phone, new USSDController.CallbackMessage() {
                                @Override
                                public void responseMessage(String message) {
                                    ussdApi.send(amount, new USSDController.CallbackMessage() {
                                        @Override
                                        public void responseMessage(String message) {
                                            ussdApi.send(simPin, new USSDController.CallbackMessage() {
                                                @Override
                                                public void responseMessage(String message) {
//                                                updateSimBalanceTv(Constant.getSimBalance(message), simSlotId);
                                                    ussdApi.cancel();
                                                    if (simSlotId == Constant.sim1Slot) {
                                                        sim_number = session.getData(Session.SIM1_NUMBER);
                                                    }

                                                    if (simSlotId == Constant.sim2Slot) {
                                                        sim_number = session.getData(Session.SIM2_NUMBER);
                                                    }
                                                    InsertNewPopUpMessage(message.replaceAll(System.lineSeparator(), " "), flexiId, "FlashMessage", sim_number, simSlotId);
                                                }
                                            });
                                        }
                                    });
                                }
                            });
                        }
                    });
                }


            }

            @Override
            public void over(String message) {

            }
        });
    }

    private void NagadCashIn(String flexiId, String phone, String amount, int simSlotId, String simPin) {
        ussdApi.callUSSDInvoke("*167#", simSlotId, map, new USSDController.CallbackInvoke() {
            @Override
            public void responseInvoke(String message) {
                if (message.contains("Do you want to continue?")) {
                    ussdApi.send("2", new USSDController.CallbackMessage() {
                        @Override
                        public void responseMessage(String message) {
                            System.out.println("======SDDD: " + message);
                            ussdApi.send("1", new USSDController.CallbackMessage() {
                                @Override
                                public void responseMessage(String message) {
                                    ussdApi.send(phone, new USSDController.CallbackMessage() {
                                        @Override
                                        public void responseMessage(String message) {
                                            ussdApi.send(amount, new USSDController.CallbackMessage() {
                                                @Override
                                                public void responseMessage(String message) {
                                                    ussdApi.send(simPin, new USSDController.CallbackMessage() {
                                                        @Override
                                                        public void responseMessage(String message) {
//                                                    updateSimBalanceTv(Constant.getSimBalance(message), simSlotId);
                                                            updateResultTv(simSlotId, message.replaceAll(System.lineSeparator(), " "));

                                                            if (message.contains("LogOut")) {
                                                                ussdApi.send("0", new USSDController.CallbackMessage() {
                                                                    @Override
                                                                    public void responseMessage(String message) {
                                                                        ussdApi.cancel();
                                                                    }
                                                                });
                                                            }
                                                            ussdApi.cancel();
                                                            if (simSlotId == Constant.sim1Slot) {
                                                                sim_number = session.getData(Session.SIM1_NUMBER);
                                                            }

                                                            if (simSlotId == Constant.sim2Slot) {
                                                                sim_number = session.getData(Session.SIM2_NUMBER);
                                                            }
                                                            InsertNewPopUpMessage(message.replaceAll(System.lineSeparator(), " "), flexiId, "FlashMessage", sim_number, simSlotId);
                                                        }
                                                    });
                                                }
                                            });
                                        }
                                    });
                                }
                            });
                        }
                    });
                } else {
                    ussdApi.send("1", new USSDController.CallbackMessage() {
                        @Override
                        public void responseMessage(String message) {
                            ussdApi.send(phone, new USSDController.CallbackMessage() {
                                @Override
                                public void responseMessage(String message) {
                                    ussdApi.send(amount, new USSDController.CallbackMessage() {
                                        @Override
                                        public void responseMessage(String message) {
                                            ussdApi.send(simPin, new USSDController.CallbackMessage() {
                                                @Override
                                                public void responseMessage(String message) {
//                                                    updateSimBalanceTv(Constant.getSimBalance(message), simSlotId);
                                                    updateResultTv(simSlotId, message.replaceAll(System.lineSeparator(), " "));

                                                    if (message.contains("LogOut")) {
                                                        ussdApi.send("0", new USSDController.CallbackMessage() {
                                                            @Override
                                                            public void responseMessage(String message) {
                                                                ussdApi.cancel();
                                                            }
                                                        });
                                                    }
                                                    ussdApi.cancel();
                                                    if (simSlotId == Constant.sim1Slot) {

                                                        sim_number = session.getData(Session.SIM1_NUMBER);
                                                    }

                                                    if (simSlotId == Constant.sim2Slot) {

                                                        sim_number = session.getData(Session.SIM2_NUMBER);
                                                    }
                                                    InsertNewPopUpMessage(message.replaceAll(System.lineSeparator(), " "), flexiId, "FlashMessage", sim_number, simSlotId);
                                                }
                                            });
                                        }
                                    });
                                }
                            });
                        }
                    });
                }

            }

            @Override
            public void over(String message) {

            }
        });
    }
    //endregion Nagad

    //region bKashLoad
    private void bKashLoad(String flexiId, String pccode, String phone, String amount, int simSlotId, String simPin) {
        dialCodeLoad = null;
        switch (pccode) {
            case "GP":
            case "SK":
                dialCodeLoad = "4";
                break;
            case "RB":
                dialCodeLoad = "1";
                break;
            case "AT":
                dialCodeLoad = "2";
                break;
            case "BL":
                dialCodeLoad = "3";
                break;
            case "TT":
                dialCodeLoad = "5";
                break;
            default:
                dialCodeLoad = null;
                break;
        }
        if (pccode.equals("SK")) {
            dialCodeType = "3";
        } else {
            dialCodeType = "1";
        }
        if (dialCodeLoad != null) {
            ussdApi.callUSSDInvoke("*247#", simSlotId, map, new USSDController.CallbackInvoke() {
                @Override
                public void responseInvoke(String message) {
                    ussdApi.send("3", new USSDController.CallbackMessage() {
                        @Override
                        public void responseMessage(String message) {
                            ussdApi.send(dialCodeLoad, new USSDController.CallbackMessage() {
                                @Override
                                public void responseMessage(String message) {
                                    ussdApi.send(dialCodeType, new USSDController.CallbackMessage() {
                                        @Override
                                        public void responseMessage(String message) {
                                            ussdApi.send(phone, new USSDController.CallbackMessage() {
                                                @Override
                                                public void responseMessage(String message) {
                                                    ussdApi.send(amount, new USSDController.CallbackMessage() {
                                                        @Override
                                                        public void responseMessage(String message) {
                                                            ussdApi.send(simPin, new USSDController.CallbackMessage() {
                                                                @Override
                                                                public void responseMessage(String message) {
                                                                    ussdApi.cancel();
                                                                    if (simSlotId == Constant.sim1Slot) {
                                                                        sim_number = session.getData(Session.SIM1_NUMBER);
                                                                        op = session.getData(Session.SIM1_SERVICE_NAME);
                                                                    }

                                                                    if (simSlotId == Constant.sim2Slot) {
                                                                        sim_number = session.getData(Session.SIM2_NUMBER);
                                                                        op = session.getData(Session.SIM2_SERVICE_NAME);
                                                                    }
                                                                    InsertNewPopUpMessage(message.replaceAll(System.lineSeparator(), " "), flexiId, "bKashLoad", sim_number, simSlotId);
                                                                    // রিকোয়েস্ট সম্পন্ন - পরবর্তী রিকোয়েস্ট প্রসেস করুন
                                                                    // Request completed - process next request
                                                                    onRequestCompleted(simSlotId);
                                                                }
                                                            });
                                                        }
                                                    });
                                                }
                                            });
                                        }
                                    });
                                }
                            });
                        }
                    });
                }

                @Override
                public void over(String message) {

                }
            });
        }
    }

    //endregion bKashLoad

    // region NagadLoad
    private void NagadLoad(String flexiId, String pccode, String phone, String amount, int simSlotId, String simPin) {
        dialCodeLoad = null;
        switch (pccode) {
            case "GP":
            case "SK":
                dialCodeLoad = "3";
                break;
            case "RB":
                dialCodeLoad = "4";
                break;
            case "AT":
                dialCodeLoad = "2";
                break;
            case "BL":
                dialCodeLoad = "5";
                break;
            case "TT":
                dialCodeLoad = "1";
                break;
            default:
                dialCodeLoad = null;
                break;
        }
        if (pccode.equals("SK")) {
            dialCodeType = "3";
        } else {
            dialCodeType = "1";
        }
        if (dialCodeLoad != null) {
            ussdApi.callUSSDInvoke("*167#", simSlotId, map, new USSDController.CallbackInvoke() {
                @Override
                public void responseInvoke(String message) {
                    if (message.contains("Do you want to continue?")) {
                        ussdApi.send("2", new USSDController.CallbackMessage() {
                            @Override
                            public void responseMessage(String message) {
                                ussdApi.send("3", new USSDController.CallbackMessage() {
                                    @Override
                                    public void responseMessage(String message) {
                                        ussdApi.send(dialCodeLoad, new USSDController.CallbackMessage() {
                                            @Override
                                            public void responseMessage(String message) {
                                                ussdApi.send(dialCodeType, new USSDController.CallbackMessage() {
                                                    @Override
                                                    public void responseMessage(String message) {
                                                        ussdApi.send(phone, new USSDController.CallbackMessage() {
                                                            @Override
                                                            public void responseMessage(String message) {
                                                                ussdApi.send(amount, new USSDController.CallbackMessage() {
                                                                    @Override
                                                                    public void responseMessage(String message) {
                                                                        ussdApi.send(simPin, new USSDController.CallbackMessage() {
                                                                            @Override
                                                                            public void responseMessage(String message) {
                                                                                ussdApi.cancel();
                                                                                if (simSlotId == Constant.sim1Slot) {
                                                                                    sim_number = session.getData(Session.SIM1_NUMBER);
                                                                                    op = session.getData(Session.SIM1_SERVICE_NAME);
                                                                                }

                                                                                if (simSlotId == Constant.sim2Slot) {
                                                                                    sim_number = session.getData(Session.SIM2_NUMBER);
                                                                                    op = session.getData(Session.SIM2_SERVICE_NAME);
                                                                                }
                                                                                InsertNewPopUpMessage(message.replaceAll(System.lineSeparator(), " "), flexiId, "NagadLoad", sim_number, simSlotId);
                                                                                // রিকোয়েস্ট সম্পন্ন - পরবর্তী রিকোয়েস্ট প্রসেস করুন
                                                                                // Request completed - process next request
                                                                                onRequestCompleted(simSlotId);
                                                                            }
                                                                        });
                                                                    }
                                                                });
                                                            }
                                                        });
                                                    }
                                                });
                                            }
                                        });
                                    }
                                });
                            }
                        });
                    } else {
                        ussdApi.send("3", new USSDController.CallbackMessage() {
                            @Override
                            public void responseMessage(String message) {
                                ussdApi.send(dialCodeLoad, new USSDController.CallbackMessage() {
                                    @Override
                                    public void responseMessage(String message) {
                                        ussdApi.send(dialCodeType, new USSDController.CallbackMessage() {
                                            @Override
                                            public void responseMessage(String message) {
                                                ussdApi.send(phone, new USSDController.CallbackMessage() {
                                                    @Override
                                                    public void responseMessage(String message) {
                                                        ussdApi.send(amount, new USSDController.CallbackMessage() {
                                                            @Override
                                                            public void responseMessage(String message) {
                                                                ussdApi.send(simPin, new USSDController.CallbackMessage() {
                                                                    @Override
                                                                    public void responseMessage(String message) {
                                                                        ussdApi.cancel();
                                                                        if (simSlotId == Constant.sim1Slot) {
                                                                            sim_number = session.getData(Session.SIM1_NUMBER);
                                                                            op = session.getData(Session.SIM1_SERVICE_NAME);
                                                                        }

                                                                        if (simSlotId == Constant.sim2Slot) {
                                                                            sim_number = session.getData(Session.SIM2_NUMBER);
                                                                            op = session.getData(Session.SIM2_SERVICE_NAME);
                                                                        }
                                                                        InsertNewPopUpMessage(message.replaceAll(System.lineSeparator(), " "), flexiId, "NagadLoad", sim_number, simSlotId);
                                                                    }
                                                                });
                                                            }
                                                        });
                                                    }
                                                });
                                            }
                                        });
                                    }
                                });
                            }
                        });
                    }

                }

                @Override
                public void over(String message) {

                }
            });
        }
    }
    //endregion NagadLoad

    //region Mobile Banking Balance Check
    private void ussdSendForBalance(String msbody, String[] ussdMsg, String pinCode, int SimId, USSDApi ussdApi) {

        String sk = Constant.ussdCodeFindFromArray(msbody, ussdMsg);
        Log.d("_USD_ERROR_"," sk: "+sk);
        Log.d("_USD_ERROR_"," msbody: "+msbody);
        Log.d("_USD_ERROR_"," ussdMsg: "+ussdMsg);
        if (!sk.isEmpty()) {
            ussdApi.send(sk, new USSDController.CallbackMessage() {
                @Override
                public void responseMessage(String message) {
                    Log.d("_USD_ERROR_"," 1: "+message);
                    Log.d("_USD_ERROR_"," 2: "+ussdMsg);
                    if (!message.isEmpty()) {
                        ussdSendForBalance(message, ussdMsg, pinCode, SimId, ussdApi);
                    }
                }
            });
        } else {
            System.out.println("=====msd " + msbody);
            String lower = msbody.toLowerCase();
            if (lower.contains("pin") || lower.contains("enter") || lower.contains("pincode")) {
                // PIN prompt detected - send PIN to complete balance check
                if (pinCode == null || pinCode.trim().isEmpty()) {
                    Log.e("_USD_ERROR_", "PIN is empty, cannot complete balance check");
                    ussdApi.cancel();
                    callGetNewPendingAfterBalanceCheck(SimId);
                    return;
                }
                ussdApi.send(pinCode, new USSDController.CallbackMessage() {
                    @Override
                    public void responseMessage(String message) {
                        String cleanMsg = message == null ? "" : message.replaceAll(System.lineSeparator(), " ").trim();
                        // শুধুমাত্র "Enter PIN:" prompt হলে ignore (balance না থাকলে)
                        // Ignore if it's just another "Enter PIN" prompt without balance
                        String bal = Constant.getSimBalance(cleanMsg);
                        if ((cleanMsg.toLowerCase().contains("enter pin") || cleanMsg.toLowerCase().contains("enter pincode"))
                                && (bal == null || bal.trim().isEmpty())) {
                            return;
                        }
                        if (bal != null && !bal.trim().isEmpty()) {
                            updateSimBalanceTv(bal, SimId);
                        }
                        updateResultTv(SimId, message);

                        if (message.contains("LogOut")) {
                            ussdApi.send("0", new USSDController.CallbackMessage() {
                                @Override
                                public void responseMessage(String message) {
                                    ussdApi.cancel();
                                    callGetNewPendingAfterBalanceCheck(SimId);
                                }
                            });
                        } else {
                            ussdApi.cancel();
                            callGetNewPendingAfterBalanceCheck(SimId);
                        }
                    }
                });
            } else if (msbody.contains("Resume session?") || msbody.contains("Do you want to continue?")) {
                // Resume/Continue prompt - send "2" and retry
                ussdApi.send("2", new USSDController.CallbackMessage() {
                    @Override
                    public void responseMessage(String message) {
                        ussdSendForBalance(message, ussdMsg, pinCode, SimId, ussdApi);
                    }
                });
            } else {
                // Unknown response - no matching menu item, no PIN prompt, no resume
                // Cancel USSD and proceed to prevent SIM from getting stuck
                Log.e("_USD_ERROR_", "Balance check: unknown response, cancelling. msg=" + msbody);
                ussdApi.cancel();
                callGetNewPendingAfterBalanceCheck(SimId);
            }
        }
    }
    //endregion

    //region InsertNewPopUpMessage
    private void InsertNewPopUpMessage(String message, String st, String senderNum, String simNumber, int simSlot) {
        sim_number = "";
        if (serverRepository == null) {
            Log.e("USD_SERVER", "Server repository is not initialized");
            return;
        }

        serverRepository.insertMessage(message, null, st, null, senderNum, simNumber,
                String.valueOf(simSlot), null, new ModemServerRepository.MessageInsertCallback() {
                    @Override
                    public void onSuccess(InsertMessageModel response) {
                        if (response.hasStatus("1")) {
                            try {
                                updateResultTv(simSlot, response.getMsg());
                            } catch (Exception ignored) {
                            }
                        }
                    }

                    @Override
                    public void onFailure(Throwable throwable) {
                        Log.e("USD_SERVER", "Insert message failed", throwable);
                    }
                });
    }
    //endregion InsertNewPopUpMessage

    //region Get Sim Number
    private void getSimNumber(String Operator, int simSlot) {
        String DialCode = "";
        final String[] phoneNumber = {""};
        switch (Operator) {
            case "Grameen":
            case "Robi":
            case "Airtel":
                DialCode = "*2#";
                break;
            case "bLink":
                DialCode = "*511#";
                break;
            case "Taletalk":
                DialCode = "*551#";
                break;
        }
        ussdApi.callUSSDInvoke(DialCode, simSlot, map, new USSDController.CallbackInvoke() {
            @Override
            public void responseInvoke(String message) {
                phoneNumber[0] = getNextWord(message, ": 88").substring(0, 11);

                if (simSlot == sim1Id) {
                    sim1Num = phoneNumber[0];
                    session.setData(Session.SIM1_NUMBER, sim1Num);
                    activityMainBinding.sim1Tv.setText(sim1 + "  | " + sim1Num + " | ID: " + sim1Id);
                }
                if (simSlot == sim2Id) {
                    sim2Num = phoneNumber[0];
                    session.setData(Session.SIM2_NUMBER, sim2Num);
                    activityMainBinding.sim2Tv.setText(sim2 + "  | " + sim2Num + " | ID: " + sim2Id);
                }
            }

            @Override
            public void over(String message) {

            }
        });
    }
    //endregion Get Sim Number

    //region RecursiveUSSDDial
    private void packageLoadSent(String dialCodePre, String dialCodePost, String flexiId, String package_name, String phone, String amount, String type, int simSlotId, String simPin, String service) {
        System.out.println("======PKG_NAME: " + package_name);
        String phonecodedial = "";
        if (simSlotId == Constant.sim1Slot) {
            sim_number = session.getData(Session.SIM1_NUMBER);
        }

        if (simSlotId == Constant.sim2Slot) {
            sim_number = session.getData(Session.SIM2_NUMBER);
        }

        // GP/Grameen: Replace 222 with 444, and 121*4# with 121*7#
        if (service.equals("Grameen")) {
            dialCodePre = dialCodePre.replace("*222*", "*444*").replace("222", "444");
            dialCodePost = dialCodePost.replace("*222*", "*444*").replace("222", "444");
            dialCodePre = dialCodePre.replace("121*4#", "121*7#").replace("*121*4#", "*121*7#");
            dialCodePost = dialCodePost.replace("121*4#", "121*7#").replace("*121*4#", "*121*7#");
        }

        if (type.equals("1")) {
            phonecodedial = dialCodePre + phone + "*" + amount + "#";
        }
        if (type.equals("0")) {
            phonecodedial = dialCodePost + phone + "*" + amount + "#";
        }

        // GP/Grameen: Replace 121*4# with 121*7# in final phonecodedial
        if (service.equals("Grameen")) {
            phonecodedial = phonecodedial.replace("121*4#", "121*7#").replace("*121*4#", "*121*7#");
        }

        ussdApi.callUSSDInvoke(phonecodedial, simSlotId, map, new USSDController.CallbackInvoke() {
            @Override
            public void responseInvoke(String message) {
                if (service.equals("Grameen")) {
                    ussdApi.send("1", new USSDController.CallbackMessage() {
                        @Override
                        public void responseMessage(String message) {
                            if (!message.isEmpty()) {
                                if (message.contains(getString(R.string.robi_failed_msg)) || message.contains(getString(R.string.airtel_failed_msg)) || message.contains(getString(R.string.banglalink_failed_msg)) || message.contains(getString(R.string.grameen_failed_msg))) {
                                    InsertNewPopUpMessage(message.replaceAll(System.lineSeparator(), " "), flexiId, null, sim_number, simSlotId);
                                    // রিকোয়েস্ট সম্পন্ন - পরবর্তী রিকোয়েস্ট প্রসেস করুন
                                    // Request completed - process next request
                                    onRequestCompleted(simSlotId);
                                } else
                                    ussdSendForKey(message, package_name, simPin, simSlotId, ussdApi, flexiId, simSlotId);
                            }
                        }
                    });
                } else {
                    if (!message.isEmpty()) {
                        if (message.contains(getString(R.string.robi_failed_msg)) || message.contains(getString(R.string.airtel_failed_msg)) || message.contains(getString(R.string.banglalink_failed_msg)) || message.contains(getString(R.string.grameen_failed_msg))) {
                            InsertNewPopUpMessage(message.replaceAll(System.lineSeparator(), " "), flexiId, null, sim_number, simSlotId);
                            // রিকোয়েস্ট সম্পন্ন - পরবর্তী রিকোয়েস্ট প্রসেস করুন
                            // Request completed - process next request
                            onRequestCompleted(simSlotId);
                        } else
                            ussdSendForKey(message, package_name, simPin, simSlotId, ussdApi, flexiId, simSlotId);
                    }
                }
            }

            @Override
            public void over(String message) {

            }
        });
    }

    private void ussdSendForKey(String msgBody, String ussdMsg, String pinCode, int SimId, USSDApi ussdApis, String rechargeId, int simSlotId) {

        String sk = Constant.GetDialNumber(msgBody, ussdMsg);
        if (simSlotId == Constant.sim1Slot) {
            sim_number = sim1Num;
        }

        if (simSlotId == Constant.sim2Slot) {
            sim_number = sim2Num;
        }
        if (!sk.equals("")) {
            ussdApis.send(sk, new USSDController.CallbackMessage() {
                @Override
                public void responseMessage(String message) {
                    if (!message.isEmpty()) {
                        ussdSendForKey(message, ussdMsg, pinCode, SimId, ussdApis, rechargeId, simSlotId);
                    }
                }
            });
        } else {
            if (msgBody.contains("PIN") || msgBody.contains("pin") || msgBody.contains("pincode")) {
                ussdApis.send(pinCode, new USSDController.CallbackMessage() {
                    @Override
                    public void responseMessage(String message) {
                        updateSimBalanceTv(Constant.getSimBalance(message.replaceAll(System.lineSeparator(), " ")), SimId);
                        updateResultTv(SimId, message);
                        ussdApis.cancel();
                    }
                });
            } else if (msgBody.contains("Next") || msgBody.contains("NEXT")) {
                ussdApis.cancel();
                InsertNewPopUpMessage("No Package Found For This Number", rechargeId, null, sim_number, simSlotId);
                // রিকোয়েস্ট সম্পন্ন - পরবর্তী রিকোয়েস্ট প্রসেস করুন
                // Request completed - process next request
                onRequestCompleted(simSlotId);
            } else {
                ussdApis.cancel();
                InsertNewPopUpMessage(msgBody.replaceAll(System.lineSeparator(), " "), rechargeId, null, sim_number, simSlotId);
                // রিকোয়েস্ট সম্পন্ন - পরবর্তী রিকোয়েস্ট প্রসেস করুন
                // Request completed - process next request
                onRequestCompleted(simSlotId);
            }
        }
    }
    //endregion

    /**
     * এই ফাংশনটি interval time সেট করার জন্য Dialog দেখায়
     *
     * কিভাবে কাজ করে:
     * 1. User একটি Dialog দেখবে যেখানে সেকেন্ডে interval time ইনপুট দিতে পারবে
     * 2. Save করলে Session.TIME_INTERVAL এ সেভ হবে (সেকেন্ডে)
     * 3. timeInterval variable এ মিলিসেকেন্ডে সেভ হবে (সেকেন্ড * 1000)
     * 4. এই interval time SIM 1 এবং SIM 2 উভয় thread-এর জন্য ব্যবহৃত হবে
     *
     * উদাহরণ:
     * - User যদি 30 ইনপুট দেয়, তাহলে প্রতি 30 সেকেন্ড পর পর কাজ করবে
     * - User যদি 60 ইনপুট দেয়, তাহলে প্রতি 60 সেকেন্ড পর পর কাজ করবে
     */
    private void setThreadTimeout(String saveTime) {
        AlertDialog.Builder alert = new AlertDialog.Builder(this);
        final EditText timerEditText = new EditText(this);
        alert.setTitle("Set Thread Time Interval");
        alert.setMessage("Both sim thread are work by this time");
        timerEditText.setText(saveTime);
        alert.setView(timerEditText);

        alert.setPositiveButton("Save", new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int whichButton) {
                //What ever you want to do with the value
                Editable getTimer = timerEditText.getText();
                //OR
                String timeEditText = timerEditText.getText().toString();
                // Session এ সেকেন্ডে সেভ করছে (যেমন: "30")
                session.setData(Session.TIME_INTERVAL, timeEditText);
                // timeInterval variable এ মিলিসেকেন্ডে সেভ করছে (যেমন: 30 * 1000 = 30000)
                timeInterval = Integer.parseInt(timeEditText) * 1000;
                Toast.makeText(MainActivity.this, "Time Interval Set Successfully", Toast.LENGTH_SHORT).show();
            }
        });

        alert.setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int whichButton) {
                // what ever you want to do with No option.
            }
        });

        alert.show();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();

        if (id == R.id.timeInt) {
            if (session != null) {
                setThreadTimeout(session.getData(Session.TIME_INTERVAL));
            } else {
                Toast.makeText(this, "Session not initialized", Toast.LENGTH_SHORT).show();
            }
            return true;
        } else if (id == R.id.domainChange) {
            domainSetting();
            return true;
        } else if (id == R.id.exitApp) {
            Toast.makeText(this, "Exiting App...", Toast.LENGTH_SHORT).show();
            finishAffinity(); // Closes the app properly
            return true;
        } else {
            return super.onOptionsItemSelected(item);
        }
    }

    @Override
    public void finish() {
        super.finish();
        if (screenExe != null) {
            screenExe.cancel();
        }
        if (simOneExe != null) {
            simOneExe.cancel();
        }
        if (simTwoExe != null) {
            simTwoExe.cancel();
        }
        _isWorkSim1 = false;
        _isWorkSim2 = false;
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (screenExe != null) {
            screenExe.cancel();
        }
        if (simOneExe != null) {
            simOneExe.cancel();
        }
        if (simTwoExe != null) {
            simTwoExe.cancel();
        }
        _isWorkSim1 = false;
        _isWorkSim2 = false;
    }

    // রিকোয়েস্ট ডাটা ক্লাস - কিউতে রাখার জন্য
    // Request Data Class - For storing in queue
    //region Phone/Amount Helper Methods

    /**
     * BD ফোন নম্বর কিনা চেক করে (01xxxxxxxxx - 11 ডিজিট)
     * Checks if value is a BD phone number (01xxxxxxxxx - 11 digits)
     */
    private boolean looksLikeBdPhone(String value) {
        if (value == null || value.trim().isEmpty()) return false;
        return normalizeBdPhone(value).matches("01\\d{9}");
    }

    /**
     * +88 বা 88 prefix সরিয়ে normalize করে
     * Normalizes by removing +88 or 88 prefix
     */
    private String normalizeBdPhone(String value) {
        if (value == null) return "";
        String v = value.trim();
        boolean hadPlusPrefix = v.startsWith("+");
        v = v.replaceAll("[^\\d+]", "");
        if (hadPlusPrefix && v.startsWith("+88")) v = v.substring(3);
        else if (v.startsWith("88") && v.length() > 10) v = v.substring(2);
        v = v.replaceAll("\\D", "");
        // 10 digit without leading 0 (e.g. 1712345678 -> 01712345678)
        if (v.matches("1[3-9]\\d{8}")) v = "0" + v;
        return v;
    }

    private String normalizeAmountValue(String value) {
        if (value == null) {
            return "";
        }

        String normalized = value.trim();
        if (normalized.isEmpty()) {
            return "";
        }

        normalized = normalized
                .replace("TK", "")
                .replace("Tk", "")
                .replace("tk", "")
                .replace("BDT", "")
                .replace(",", "")
                .replaceAll("\\s+", "");

        normalized = normalized.replaceAll("[^0-9.]", "");

        if (normalized.startsWith(".")) {
            normalized = normalized.substring(1);
        }

        int dotIndex = normalized.indexOf('.');
        if (dotIndex >= 0) {
            String beforeDot = normalized.substring(0, dotIndex);
            String afterDot = normalized.substring(dotIndex + 1).replace(".", "");
            normalized = beforeDot + "." + afterDot;
        }

        return normalized;
    }

    //endregion Phone/Amount Helper Methods

    private static class RequestData {
        String sid;
        String pcode;
        String phone;
        String amount;
        String type;
        String package_name;
        boolean isPowerLoad;
        int simSlotId;
        String simPin;
        String service;

        RequestData(String sid, String pcode, String phone, String amount, String type,
                    String package_name, boolean isPowerLoad, int simSlotId, String simPin, String service) {
            this.sid = sid;
            this.pcode = pcode;
            this.phone = phone;
            this.amount = amount;
            this.type = type;
            this.package_name = package_name;
            this.isPowerLoad = isPowerLoad;
            this.simSlotId = simSlotId;
            this.simPin = simPin;
            this.service = service;
        }
    }
}
