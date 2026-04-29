package com.dRecharge.modem;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.NotificationCompat;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.databinding.DataBindingUtil;

import android.Manifest;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkInfo;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.telephony.SubscriptionInfo;
import android.telephony.SubscriptionManager;
import android.text.Editable;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.Window;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import com.dRecharge.modem.apimodel.InsertMessageModel;
import com.dRecharge.modem.databinding.ActivityMainBinding;
import com.dRecharge.modem.helper.AppPermissionSupport;
import com.dRecharge.modem.helper.Constant;
import com.dRecharge.modem.helper.ServiceCatalog;
import com.dRecharge.modem.helper.ServiceConfig;
import com.dRecharge.modem.helper.Session;
import com.dRecharge.modem.helper.ThemeManager;
import com.dRecharge.modem.helper.UssdDialTemplateResolver;
import com.dRecharge.modem.licenseapimodel.DomainSubscriptionStatus;
import com.dRecharge.modem.licenseapimodel.SingleDomainResponse;
import com.dRecharge.modem.receiver.SMSBReceiver;
import com.dRecharge.modem.server.ModemServerRepository;
import com.dRecharge.modem.server.ServerConfig;
import com.dRecharge.modem.service.KeepAliveService;
import com.dRecharge.modem.service.ServiceRequest;
import com.dRecharge.modem.subscription.SubscriptionAccessPolicy;
import com.dRecharge.modem.subscription.SubscriptionCheckScheduler;
import com.dRecharge.modem.subscription.SubscriptionCheckSupport;
import com.dRecharge.modem.subscription.SubscriptionLogoStore;
import com.dRecharge.modem.subscription.SubscriptionRepository;
import com.dRecharge.modem.subscription.SubscriptionStatusEvaluator;
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
import java.util.Queue;
import java.util.Set;
import java.util.Timer;
import java.util.TimerTask;
import java.lang.ref.WeakReference;

import javax.crypto.BadPaddingException;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;

import static com.dRecharge.modem.helper.Constant.API_SAVED_DOMAIN_LINK;
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
import static com.dRecharge.modem.helper.Session.SIM1_SERVICE_CODE;
import static com.dRecharge.modem.helper.Session.SIM2_SERVICE_CODE;
import static com.dRecharge.modem.helper.Session.SUBSCRIPTION_CHECKED_AT;
import static com.dRecharge.modem.helper.Session.SUBSCRIPTION_DAYS_UNTIL_EXPIRY;
import static com.dRecharge.modem.helper.Session.SUBSCRIPTION_TRACKED;

public class MainActivity extends AppCompatActivity {
    private static final String DEFAULT_HOME_TITLE = "dRecharge";
    private static final long POLL_INITIAL_DELAY_MS = 1000L;
    private ActivityMainBinding activityMainBinding;
    private int appliedTheme;
    private HashMap<String, HashSet<String>> map;
    private Session session;
    private static WeakReference<MainActivity> insRef;
    /** True while the activity is between onResume and onStop — i.e. visible to the user. */
    private static volatile boolean isInForeground = false;
    private String sim_number, op_code, op;
    private String currentRequestPcode = "";
    private String currentRequestSender = "";
    String TAG = "TAG_ACC";
    String dialCodeLoad = null;
    String dialCodeType = "1";
    LoadingDialog loadingDialog;

    public static Context contextOfApplication;
    private USSDApi ussdApi;
    private ModemServerRepository serverRepository;
    private SubscriptionRepository subscriptionRepository;
    private boolean isSubscriptionValid = false;
    private boolean isSubscriptionCheckInProgress = false;
    private static final String SUBSCRIPTION_ALERT_CHANNEL_ID = "drecharge_subscription_alerts";
    private static final int SUBSCRIPTION_ALERT_NOTIFICATION_ID = 1002;
    private String lastSubscriptionAlertMessage = "";

    // রিকোয়েস্ট কিউ সিস্টেম - প্রতিটি SIM এর জন্য আলাদা কিউ
    // Request Queue System - Separate queue for each SIM
    private Queue<RequestData> requestQueueSim1 = new LinkedList<>();
    private Queue<RequestData> requestQueueSim2 = new LinkedList<>();
    private boolean isProcessingSim1 = false; // SIM1 এর জন্য রিকোয়েস্ট প্রসেস হচ্ছে কিনা
    private boolean isProcessingSim2 = false; // SIM2 এর জন্য রিকোয়েস্ট প্রসেস হচ্ছে কিনা
    private Set<String> isFetchingPendingSim1 = new HashSet<>(); // SIM1 এর জন্য fetch হচ্ছে এমন service names
    private Set<String> isFetchingPendingSim2 = new HashSet<>(); // SIM2 এর জন্য fetch হচ্ছে এমন service names

    // Balance check tracking - Balance check শেষ হওয়ার পর request fetch করার জন্য
    private boolean isWaitingForBalanceCheckSim1 = false; // SIM1 এর জন্য balance check শেষ হওয়ার অপেক্ষা করছে কিনা
    private boolean isWaitingForBalanceCheckSim2 = false; // SIM2 এর জন্য balance check শেষ হওয়ার অপেক্ষা করছে কিনা
    private boolean sim1Enabled = false;
    private boolean sim2Enabled = false;

    // গ্লোবাল লক - একবারে শুধুমাত্র একটি SIM প্রসেস করবে
    // Global lock - Only one SIM will process at a time
    private boolean isAnySimProcessing = false;
    private long simProcessingDelay = 30000; // ডিফল্ট: 30 সেকেন্ড (30000ms) - একটি SIM শেষ হওয়ার পর আরেকটি SIM শুরু করার আগে অপেক্ষা

    // Safety timeout - if a USSD chain fails mid-step and never calls onRequestCompleted(), this resets the lock
    private Runnable safetyTimeoutRunnable1 = null;
    private Runnable safetyTimeoutRunnable2 = null;
    private static final long REQUEST_SAFETY_TIMEOUT_MS = 60000; // 60s: covers USSD timeout(25s) + longest multi-step chain

    private final Handler handler = new Handler(Looper.getMainLooper());
    Timer simOneExe, simTwoExe;
    Runnable simOneRunable, simTwoRunable;
    TimerTask _simOneWorker, _simTwoWorker;


    int timeInterval = 60000; // ডিফল্ট: 60000 মিলিসেকেন্ড = 60 সেকেন্ড (অন্য কাজের জন্য ব্যবহৃত)

    // ── Countdown ──
    private final android.os.Handler countdownHandler = new android.os.Handler(android.os.Looper.getMainLooper());
    private final android.os.Handler subscriptionCheckHandler = new android.os.Handler(android.os.Looper.getMainLooper());
    private long nextFetchAtMs = 0;
    private final Runnable dailySubscriptionCheckRunnable = new Runnable() {
        @Override
        public void run() {
            refreshSubscriptionState(false);
            scheduleNextInAppSubscriptionCheck();
        }
    };
    private final Runnable countdownRunnable = new Runnable() {
        @Override
        public void run() {
            if (activityMainBinding == null) {
                return;
            }

            if (!isAnyServiceEnabled()) {
                stopCountdown();
                return;
            }

            if (nextFetchAtMs > 0) {
                long remainMs = Math.max(0, nextFetchAtMs - System.currentTimeMillis());
                int secs = (int) (remainMs / 1000);
                String display = String.format(java.util.Locale.US, "Next: %02d:%02d", secs / 60, secs % 60);
                activityMainBinding.countdownTv.setText(display);
            } else {
                activityMainBinding.countdownTv.setText("Next: 00:00");
            }
            countdownHandler.postDelayed(this, 500);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        ThemeManager.applyTheme(this);
        super.onCreate(savedInstanceState);

        if (getSupportActionBar() != null) getSupportActionBar().hide();

        activityMainBinding = DataBindingUtil.setContentView(this, R.layout.activity_main);
        appliedTheme = ThemeManager.getSelectedTheme(this);
        insRef = new WeakReference<>(this);
        contextOfApplication = getApplicationContext();
        session = new Session(MainActivity.this);
        loadingDialog = new LoadingDialog(MainActivity.this);
        ussdApi = USSDController.getInstance(contextOfApplication);
        subscriptionRepository = new SubscriptionRepository();

        activityMainBinding.homeSettingsBtn.setOnClickListener(v -> openSettingsScreen());
        activityMainBinding.powerBtn.setOnClickListener(v -> toggleService());
        activityMainBinding.subscriptionRefreshBtn.setOnClickListener(v -> refreshSubscriptionState(true));
        SubscriptionCheckScheduler.scheduleNextDailyCheck(this);

        updatePowerButtonState(false);

        if (!runtimePermissionsReady()) {
            // Runtime permissions were never granted (or were revoked) — must go through setup.
            startActivity(new Intent(this, PermissionActivity.class));
            finish();
            return;
        }
        if (!AppPermissionSupport.wasSetupCompletedBefore(this) && !isSetupComplete()) {
            // First-ever launch: all permissions including accessibility must be in place.
            startActivity(new Intent(this, PermissionActivity.class));
            finish();
            return;
        }

        startKeepAliveService();
        loadHeaderBranding();
        loadLogo();

        refreshSubscriptionState(false);
        scheduleNextInAppSubscriptionCheck();
        getsSimServiceInfo();
        init();
        reloadHomeFromSession();


        // Keep screen on and prevent phone lock while the app is in the foreground.
        getWindow().addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        keepScreenOnAndDismissKeyguard();
    }

    @Override
    protected void onResume() {
        super.onResume();
        isInForeground = true;
        // Runtime permissions can be revoked at any time by the user — always block.
        if (!runtimePermissionsReady()) {
            startActivity(new Intent(this, PermissionActivity.class));
            finish();
            return;
        }
        // If the user has never completed setup, enforce all requirements.
        if (!AppPermissionSupport.wasSetupCompletedBefore(this) && !isSetupComplete()) {
            startActivity(new Intent(this, PermissionActivity.class));
            finish();
            return;
        }
        // Setup was done before — accessibility service may have been auto-disabled by the OS
        // (common on MIUI, Samsung, after app updates, etc.).  Show a non-blocking dialog
        // instead of kicking the user through the full setup screen again.
        if (!isAccessServiceEnabled(getApplicationContext(), USSDService.class)) {
            showAccessibilityDisabledWarning();
        }
        int selectedTheme = ThemeManager.getSelectedTheme(this);
        if (selectedTheme != appliedTheme) {
            recreate();
            return;
        }
        if (activityMainBinding != null && session != null) {
            restartPollingTimers();
            refreshServerRepository();
            SubscriptionCheckScheduler.scheduleNextDailyCheck(this);
            refreshSubscriptionState(false);
            scheduleNextInAppSubscriptionCheck();
            getsSimServiceInfo();
            reloadHomeFromSession();
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        // Mark that the activity is no longer visible — this lets KeepAliveService
        // take over USSD polling immediately rather than waiting for the WeakReference
        // to be garbage collected.
        isInForeground = false;
    }

    @Override
    protected void onPause() {
        super.onPause();
        // Do NOT stop polling timers here — the foreground service keeps the process alive
        // and polling must continue while the screen is locked or the app is backgrounded.
        // Timers are stopped in onDestroy() instead.
        subscriptionCheckHandler.removeCallbacks(dailySubscriptionCheckRunnable);
    }

    public static MainActivity getMainActivityInstance() {
        return insRef != null ? insRef.get() : null;
    }

    /**
     * Returns true only when the activity is visible to the user (between onResume and onStop).
     * This is used by KeepAliveService to decide whether the activity or the service
     * should own USSD polling. Unlike getMainActivityInstance(), this is deterministic
     * and not subject to GC timing.
     */
    public static boolean isActivityInForeground() {
        return isInForeground;
    }

    private void openSettingsScreen() {
        startActivity(new Intent(this, SettingsActivity.class));
    }

    private void toggleService() {
        boolean enableAll = !isAnyServiceEnabled();
        setSim1Enabled(enableAll);
        setSim2Enabled(enableAll);
    }

    private void updatePowerButtonState(boolean isOn) {
        activityMainBinding.powerBtn.setColorFilter(isOn ? Color.parseColor("#22C55E") : Color.parseColor("#DC2626"));
    }

    private boolean isAnyServiceEnabled() {
        return sim1Enabled || sim2Enabled;
    }

    private boolean isSimEnabled(int simSlotId) {
        if (simSlotId == sim1Id) {
            return sim1Enabled;
        }
        if (simSlotId == sim2Id) {
            return sim2Enabled;
        }
        return false;
    }

    private void scheduleCountdown(long delayMs) {
        nextFetchAtMs = System.currentTimeMillis() + Math.max(0, delayMs);
        refreshCountdownState();
    }

    private void refreshCountdownState() {
        countdownHandler.removeCallbacks(countdownRunnable);
        if (isAnyServiceEnabled()) {
            countdownHandler.post(countdownRunnable);
        } else {
            stopCountdown();
        }
    }

    private void stopCountdown() {
        nextFetchAtMs = 0;
        countdownHandler.removeCallbacks(countdownRunnable);
        if (activityMainBinding != null) {
            activityMainBinding.countdownTv.setText("Next: 00:00");
        }
    }

    private void restartPollingTimers() {
        stopPollingTimers();
        if (session == null) {
            return;
        }

        boolean hasSim1Config = session.isSim1Valid() || !session.getActiveServicesForSim(1).isEmpty();
        if (hasSim1Config) {
            simOneExe = new Timer("sim1-poll", true);
            simOneExe.schedule(simOneSchedule(), POLL_INITIAL_DELAY_MS, getTimerTime());
        }

        boolean hasSim2Config = session.isSim2Valid() || !session.getActiveServicesForSim(2).isEmpty();
        if (hasSim2Config) {
            simTwoExe = new Timer("sim2-poll", true);
            simTwoExe.schedule(simTwoSchedule(), POLL_INITIAL_DELAY_MS, getTimerTime());
        }

        if (hasSim1Config || hasSim2Config) {
            nextFetchAtMs = System.currentTimeMillis() + POLL_INITIAL_DELAY_MS;
            refreshCountdownState();
        } else {
            stopCountdown();
        }
    }

    private void stopPollingTimers() {
        if (simOneExe != null) {
            simOneExe.cancel();
            simOneExe.purge();
            simOneExe = null;
        }
        if (simTwoExe != null) {
            simTwoExe.cancel();
            simTwoExe.purge();
            simTwoExe = null;
        }
        if (simOneRunable != null) {
            handler.removeCallbacks(simOneRunable);
            simOneRunable = null;
        }
        if (simTwoRunable != null) {
            handler.removeCallbacks(simTwoRunable);
            simTwoRunable = null;
        }
        _simOneWorker = null;
        _simTwoWorker = null;
    }

    private void reloadHomeFromSession() {
        updateSimConfigurationSummary();
        syncEnabledSwitchesFromSession();
        renderSubscriptionStatusFromSession();
    }

    private void updateSimConfigurationSummary() {
        savedSim1Pin = session.getData(Session.SIM1_PIN);
        savedSim1Time = session.getData(Session.SIM1_TIME);
        savedSim1Bal = defaultIfEmpty(session.getData(Session.SIM1_MIN_BAL), "0");
        savedSim1ServiceCode = session.getData(Session.SIM1_SERVICE_CODE);
        savedSim1Service = safeParseInt(session.getData(Session.SIM1_SERVICE), 0);
        savedSim1ServiceName = session.getData(Session.SIM1_SERVICE_NAME);
        sim1Num = defaultIfEmpty(session.getData(Session.SIM1_NUMBER), sim1Num);

        savedSim2Pin = session.getData(Session.SIM2_PIN);
        savedSim2Time = session.getData(Session.SIM2_TIME);
        savedSim2Bal = defaultIfEmpty(session.getData(Session.SIM2_MIN_BAL), "0");
        savedSim2ServiceCode = session.getData(Session.SIM2_SERVICE_CODE);
        savedSim2Service = safeParseInt(session.getData(Session.SIM2_SERVICE), 0);
        savedSim2ServiceName = session.getData(Session.SIM2_SERVICE_NAME);
        sim2Num = defaultIfEmpty(session.getData(Session.SIM2_NUMBER), sim2Num);

        updateSimLabels();
    }

    private void updateSimLabels() {
        // The premium home screen no longer stores operational state in hidden placeholder views.
    }

    private void syncEnabledSwitchesFromSession() {
        sim1Enabled = session.getBooleanData(Session.SIM1_ENABLED);
        sim2Enabled = session.getBooleanData(Session.SIM2_ENABLED);
        refreshPowerButton();
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

    //region Settings And Service

    private boolean isSetupComplete() {
        if (!AppPermissionSupport.hasAllRuntimePermissions(this)) {
            return false;
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (!isRestrictedSettingsUnlocked()) {
                return false;
            }
        }
        // Check accessibility service
        return isAccessServiceEnabled(getApplicationContext(), USSDService.class);
    }

    private boolean isRestrictedSettingsUnlocked() {
        if (AppPermissionSupport.isRestrictedSettingsUnlocked(this)) return true;
        // Fallback: once setup has been completed, restricted settings were unlocked at that
        // point and cannot be auto-revoked by the OS — treat them as still valid.
        // (Previously this fell back to the accessibility check, which caused false negatives
        // whenever Android auto-disabled the accessibility service.)
        if (AppPermissionSupport.wasSetupCompletedBefore(this)) return true;
        // For ROMs where AppOps is unreliable (MIUI, ColorOS), use accessibility as a proxy
        // only on a true first-time setup where we know nothing yet.
        return isAccessServiceEnabled(getApplicationContext(), USSDService.class);
    }

    /**
     * Returns true only when the runtime permissions that the app declares as required
     * (CALL_PHONE, READ_PHONE_STATE, RECEIVE_SMS, POST_NOTIFICATIONS on API 33+) are all
     * granted.  These are checked on every resume because the user can revoke them in
     * Settings at any time.
     */
    private boolean runtimePermissionsReady() {
        return AppPermissionSupport.hasAllRuntimePermissions(this);
    }

    /**
     * Shows a non-blocking dialog when the accessibility service has been auto-disabled
     * by the OS or an OEM power-management policy.  The user can re-enable it without
     * going through the full setup screen.
     */
    private void showAccessibilityDisabledWarning() {
        new AlertDialog.Builder(this)
                .setTitle("Accessibility Service Disabled")
                .setMessage("The USSD accessibility service was disabled (this can happen automatically after an app update or due to phone battery settings).\n\nUSSD operations won't work until you re-enable it.\n\nTap \"Enable\" to open Accessibility Settings, find \"dRecharge\" and turn it ON.")
                .setCancelable(true)
                .setPositiveButton("Enable Now", (d, w) ->
                        startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)))
                .setNegativeButton("Later", null)
                .show();
    }

    private void loadLogo() {
        String logoName = getActiveSubscriptionLogoName();
        if (logoName.isEmpty()) {
            applyDefaultLogo();
            return;
        }

        Bitmap cachedLogo = SubscriptionLogoStore.loadCachedLogo(this, logoName);
        if (cachedLogo != null) {
            applyLogoBitmaps(cachedLogo);
            return;
        }

        applyDefaultLogo();
        syncSubscriptionLogo(logoName);
    }

    private void loadHeaderBranding() {
        applyHeaderBranding(getActiveSubscriptionDisplayName());
    }

    /**
     * Converts every coloured pixel in the source bitmap to white while
     * preserving its alpha — exactly what Android needs for a status-bar icon.
     */
    private static android.graphics.Bitmap makeWhiteIcon(android.graphics.Bitmap source) {
        android.graphics.Bitmap result = android.graphics.Bitmap.createBitmap(
                source.getWidth(), source.getHeight(), android.graphics.Bitmap.Config.ARGB_8888);
        android.graphics.Canvas canvas = new android.graphics.Canvas(result);
        android.graphics.Paint paint = new android.graphics.Paint();
        // ColorMatrix that forces R=255, G=255, B=255 and keeps original alpha unchanged
        android.graphics.ColorMatrix cm = new android.graphics.ColorMatrix(new float[]{
                0, 0, 0, 0, 255,
                0, 0, 0, 0, 255,
                0, 0, 0, 0, 255,
                0, 0, 0, 1,   0
        });
        paint.setColorFilter(new android.graphics.ColorMatrixColorFilter(cm));
        canvas.drawBitmap(source, 0, 0, paint);
        return result;
    }

    private void applyDefaultLogo() {
        applyLogoBitmaps(null);
    }

    private void applyDefaultHeaderBranding() {
        if (activityMainBinding == null) {
            return;
        }

        activityMainBinding.homeTitleTv.setText(DEFAULT_HOME_TITLE);
    }

    private void applyHeaderBranding(String displayName) {
        if (activityMainBinding == null) {
            return;
        }

        String trimmed = displayName == null ? "" : displayName.trim();
        if (trimmed.isEmpty()) {
            applyDefaultHeaderBranding();
            return;
        }

        activityMainBinding.homeTitleTv.setText(trimmed);
    }

    private void applyLogoBitmaps(Bitmap homeLogoBitmap) {
        if (activityMainBinding != null) {
            if (homeLogoBitmap != null) {
                activityMainBinding.mainLogoImg.setVisibility(View.VISIBLE);
                activityMainBinding.mainLogoFallbackTv.setVisibility(View.GONE);
                activityMainBinding.mainLogoImg.setImageBitmap(homeLogoBitmap);
            } else {
                activityMainBinding.mainLogoImg.setImageDrawable(null);
                activityMainBinding.mainLogoImg.setVisibility(View.GONE);
                activityMainBinding.mainLogoFallbackTv.setVisibility(View.VISIBLE);
            }
        }

        Bitmap squareBmp = BitmapFactory.decodeResource(getResources(), R.drawable.app_logo_square);
        Bitmap whiteBmp = squareBmp == null ? null : makeWhiteIcon(squareBmp);
        Bitmap notificationLogo = homeLogoBitmap != null ? homeLogoBitmap : squareBmp;
        KeepAliveService.setLogoBitmaps(notificationLogo, whiteBmp);
        KeepAliveService.updateNotification(this);
    }

    private String getActiveSubscriptionLogoName() {
        if (session == null) {
            return "";
        }

        String host = extractDomainHost(session.getData(Session.API_DOMAIN_LINK));
        String lastCheckedDomain = session.getData(Session.SUBSCRIPTION_LAST_DOMAIN);
        if (host.isEmpty() || !host.equals(lastCheckedDomain)) {
            return "";
        }

        return session.getData(Session.SUBSCRIPTION_DOMAIN_LOGO).trim();
    }

    private String getActiveSubscriptionDisplayName() {
        if (session == null) {
            return "";
        }

        String host = extractDomainHost(session.getData(Session.API_DOMAIN_LINK));
        String lastCheckedDomain = session.getData(Session.SUBSCRIPTION_LAST_DOMAIN);
        if (host.isEmpty() || !host.equals(lastCheckedDomain)) {
            return "";
        }

        return session.getData(Session.SUBSCRIPTION_DISPLAY_NAME).trim();
    }

    private void syncSubscriptionLogo(String logoName) {
        SubscriptionLogoStore.syncLogoAsync(this, logoName, bitmap -> runOnUiThread(() -> {
            if (isFinishing() || (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1 && isDestroyed())) {
                return;
            }

            if (bitmap != null) {
                applyLogoBitmaps(bitmap);
            } else {
                applyDefaultLogo();
            }
        }));
    }

    private void startKeepAliveService() {
        Intent intent = new Intent(this, KeepAliveService.class);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent);
        } else {
            startService(intent);
        }
    }

    public boolean isAccessServiceEnabled(Context context, Class accessibilityServiceClass) {
        return com.dRecharge.modem.ussd.AccessibilityUtils.isAccessibilityFullyEnabled(context, accessibilityServiceClass);
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
        int interval = 60; // ডিফল্ট: 60 সেকেন্ড

        try {
            String timeStr = session.getData(Session.TIME_INTERVAL);
            if(timeStr == null || timeStr.trim().isEmpty()){
                timeStr = "60"; // যদি সেট না থাকে, 60 সেকেন্ড ব্যবহার করবে
            }
            interval = Integer.parseInt(timeStr);
            if(interval < 1){
                interval = 60; // যদি 1 সেকেন্ডের কম হয়, 60 সেকেন্ড সেট করবে
            }
        }catch(Exception e){
        }
        return interval * 1000L; // সেকেন্ডকে মিলিসেকেন্ডে কনভার্ট করে (30 * 1000 = 30000ms)
    }

    private void refreshServerRepository() {
        String savedDomain = session.getData(Session.API_DOMAIN_LINK);
        String normalizedDomain = ServerConfig.sanitizeDomain(savedDomain);
        if (normalizedDomain.isEmpty()) {
            serverRepository = null;
            API_SAVED_DOMAIN_LINK = "";
            isSubscriptionValid = false;
            return;
        }

        if (!normalizedDomain.equals(savedDomain)) {
            session.setData(Session.API_DOMAIN_LINK, normalizedDomain);
        }

        API_SAVED_DOMAIN_LINK = normalizedDomain;
        serverRepository = ModemServerRepository.fromDomain(normalizedDomain);
    }

    private void refreshSubscriptionState(boolean forceRefresh) {
        String normalizedDomain = ServerConfig.sanitizeDomain(session.getData(Session.API_DOMAIN_LINK));
        String host = extractDomainHost(normalizedDomain);

        if (host.isEmpty()) {
            isSubscriptionValid = false;
            session.clearSubscriptionState();
            loadHeaderBranding();
            loadLogo();
            renderSubscriptionStatusFromSession();
            return;
        }

        String lastCheckedDomain = session.getData(Session.SUBSCRIPTION_LAST_DOMAIN);
        if (!host.equals(lastCheckedDomain)) {
            session.clearSubscriptionState();
            loadHeaderBranding();
            loadLogo();
        }

        boolean hasCurrentCheck = isSubscriptionCheckCurrent(host);
        renderSubscriptionStatusFromSession();

        if (!forceRefresh && hasCurrentCheck) {
            isSubscriptionValid = canProcessRequests();
            return;
        }

        if (isSubscriptionCheckInProgress) {
            return;
        }

        if (!isNetworkAvailable()) {
            isSubscriptionValid = false;
            renderSubscriptionFailureState(host, "No internet connection");
            return;
        }

        isSubscriptionCheckInProgress = true;
        renderSubscriptionCheckingState(host);

        subscriptionRepository.checkDomain(host, new SubscriptionRepository.SubscriptionCallback() {
            @Override
            public void onSuccess(SingleDomainResponse response) {
                isSubscriptionCheckInProgress = false;
                storeSubscriptionState(host, response);
                renderSubscriptionStatusFromSession();
            }

            @Override
            public void onFailure(Throwable throwable) {
                isSubscriptionCheckInProgress = false;
                isSubscriptionValid = false;
                String failureMessage = defaultIfEmpty(throwable == null ? "" : throwable.getMessage(),
                        "Subscription check failed");
                renderSubscriptionFailureState(host, failureMessage);
            }
        });
    }

    private boolean isNetworkAvailable() {
        ConnectivityManager connectivityManager =
                (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        if (connectivityManager == null) {
            return false;
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Network network = connectivityManager.getActiveNetwork();
            if (network == null) {
                return false;
            }
            NetworkCapabilities capabilities = connectivityManager.getNetworkCapabilities(network);
            return capabilities != null && capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET);
        }

        NetworkInfo networkInfo = connectivityManager.getActiveNetworkInfo();
        return networkInfo != null && networkInfo.isConnected();
    }

    private void storeSubscriptionState(String host, SingleDomainResponse response) {
        DomainSubscriptionStatus resolved = response == null ? null : response.resolveData();
        if (resolved == null || !SubscriptionCheckSupport.storeSubscriptionState(session, host, response)) {
            return;
        }

        applyHeaderBranding(resolved.getDisplayName());
        syncSubscriptionLogo(resolved.getDomainLogo());

        SubscriptionStatusEvaluator.Evaluation evaluation = syncSubscriptionEvaluation();
        isSubscriptionValid = canProcessRequests(evaluation);
        if (!isSubscriptionValid) {
            disableRequestProcessing();
        }
    }

    private void renderSubscriptionStatusFromSession() {
        if (activityMainBinding == null || session == null) {
            return;
        }

        String host = extractDomainHost(session.getData(Session.API_DOMAIN_LINK));
        if (host.isEmpty()) {
            renderSubscriptionSetupState();
            isSubscriptionValid = false;
            return;
        }

        boolean tracked = session.getBooleanData(SUBSCRIPTION_TRACKED);
        boolean available = session.getBooleanData(Session.SUBSCRIPTION_AVAILABLE);
        boolean subscribed = session.getBooleanData(Session.SUBSCRIPTION_SUBSCRIBED);
        String subscriptionStatus = session.getData(Session.SUBSCRIPTION_STATUS);
        String subscriptionMessage = session.getData(Session.SUBSCRIPTION_MESSAGE);
        SubscriptionStatusEvaluator.Evaluation evaluation = syncSubscriptionEvaluation();
        Integer serverDaysUntilExpiry = getStoredDaysUntilExpiry();

        if (isSubscriptionCheckInProgress || !isSubscriptionCheckCurrent(host)) {
            renderSubscriptionCheckingState(host);
            isSubscriptionValid = false;
            return;
        }

        if (SubscriptionAccessPolicy.isExpired(evaluation, subscriptionStatus, subscriptionMessage)) {
            String expiredMessage = buildExpiredSubscriptionMessage(host, subscriptionStatus, evaluation);
            renderSubscriptionExpiredState(host, subscriptionStatus, evaluation);
            isSubscriptionValid = false;
            disableRequestProcessing();
            showSubscriptionExpiredNotification(expiredMessage);
            return;
        }

        clearSubscriptionExpiredNotification();
        if (shouldRenderInactiveSubscriptionState(tracked, available, subscribed, subscriptionStatus, subscriptionMessage)) {
            renderSubscriptionInactiveState(host, subscriptionStatus, subscriptionMessage);
            isSubscriptionValid = canProcessRequests(evaluation);
            return;
        }

        renderSubscriptionActiveState(host, subscriptionStatus, subscriptionMessage, serverDaysUntilExpiry, evaluation);

        isSubscriptionValid = true;
    }

    private void renderSubscriptionSetupState() {
        updateSubscriptionRefreshState(false);
        renderSubscriptionBadge(
                "SETUP",
                ThemeManager.getThemeColor(this, R.attr.colorInactiveBg),
                ThemeManager.getThemeColor(this, R.attr.colorInactiveGrey),
                "Add server domain",
                "Subscription check is disabled");
    }

    private void renderSubscriptionCheckingState(String host) {
        updateSubscriptionRefreshState(false);
        renderSubscriptionBadge(
                "CHECKING",
                ContextCompat.getColor(this, R.color.info_bg),
                ContextCompat.getColor(this, R.color.info_text),
                "Verifying subscription",
                host);
    }

    private void renderSubscriptionFailureState(String host, String failureMessage) {
        updateSubscriptionRefreshState(true);
        String cleanMessage = singleLine(failureMessage);
        renderSubscriptionBadge(
                "ERROR",
                ContextCompat.getColor(this, R.color.error_bg),
                ContextCompat.getColor(this, R.color.error_text),
                "Subscription check failed",
                joinSubscriptionMeta(host, cleanMessage));
    }

    private void renderSubscriptionExpiredState(String host,
                                                String status,
                                                SubscriptionStatusEvaluator.Evaluation evaluation) {
        updateSubscriptionRefreshState(true);
        String expiryLabel = evaluation.getDisplayDate();
        String detail = expiryLabel.isEmpty() ? "Subscription expired" : "Expired on " + expiryLabel;
        renderSubscriptionBadge(
                "EXPIRED",
                ContextCompat.getColor(this, R.color.error_bg),
                ContextCompat.getColor(this, R.color.error_text),
                detail,
                joinSubscriptionMeta(host, formatSubscriptionStatus(status)));
    }

    private void renderSubscriptionActiveState(String host,
                                               String status,
                                               String message,
                                               Integer serverDaysUntilExpiry,
                                               SubscriptionStatusEvaluator.Evaluation evaluation) {
        updateSubscriptionRefreshState(true);
        String expiryLabel = evaluation.getDisplayDate();
        String detail = "Subscription active";
        String meta = host;

        if (evaluation.expiresToday()) {
            detail = "Expires today";
            meta = joinSubscriptionMeta(host, expiryLabel.isEmpty() ? "" : "Expiry " + expiryLabel);
        } else if (serverDaysUntilExpiry != null && serverDaysUntilExpiry >= 0) {
            detail = serverDaysUntilExpiry + " day" + (serverDaysUntilExpiry == 1 ? "" : "s") + " left";
            meta = joinSubscriptionMeta(host, expiryLabel.isEmpty() ? "" : "Expires " + expiryLabel);
        } else if (evaluation.getDaysLeft() >= 0) {
            long daysLeft = evaluation.getDaysLeft();
            detail = daysLeft + " day" + (daysLeft == 1 ? "" : "s") + " left";
            meta = joinSubscriptionMeta(host, expiryLabel.isEmpty() ? "" : "Expires " + expiryLabel);
        } else if (evaluation.hasKnownExpiry() && !expiryLabel.isEmpty()) {
            detail = "Active until " + expiryLabel;
        } else if (!formatSubscriptionStatus(status).isEmpty()) {
            meta = joinSubscriptionMeta(host, formatSubscriptionStatus(status));
        } else if (!singleLine(message).isEmpty()) {
            meta = joinSubscriptionMeta(host, singleLine(message));
        }

        renderSubscriptionBadge(
                "ACTIVE",
                ThemeManager.getThemeColor(this, R.attr.colorActiveBg),
                ThemeManager.getThemeColor(this, R.attr.colorActiveGreen),
                detail,
                meta);
    }

    private void renderSubscriptionInactiveState(String host, String status, String message) {
        updateSubscriptionRefreshState(true);
        String normalizedStatus = normalizeSubscriptionText(status);
        String normalizedMessage = normalizeSubscriptionText(message);
        String badge = "INACTIVE";
        int badgeBackgroundColor = ThemeManager.getThemeColor(this, R.attr.colorInactiveBg);
        int badgeTextColor = ThemeManager.getThemeColor(this, R.attr.colorInactiveGrey);
        String detail = "Subscription inactive";

        if (isDomainNotFoundStatus(normalizedStatus, normalizedMessage)) {
            badge = "UNAVAILABLE";
            detail = "Domain not found";
        } else if (normalizedStatus.contains("pending") || normalizedMessage.contains("pending")) {
            badge = "PENDING";
            badgeBackgroundColor = ContextCompat.getColor(this, R.color.warning_bg);
            badgeTextColor = ContextCompat.getColor(this, R.color.warning_text);
            detail = "Awaiting activation";
        } else if (!singleLine(message).isEmpty()) {
            detail = singleLine(message);
        }

        String formattedStatus = formatSubscriptionStatus(status);
        String meta = joinSubscriptionMeta(host, formattedStatus);
        if (formattedStatus.isEmpty() && !singleLine(message).equals(detail)) {
            meta = joinSubscriptionMeta(host, singleLine(message));
        }

        renderSubscriptionBadge(
                badge,
                badgeBackgroundColor,
                badgeTextColor,
                detail,
                meta);
    }

    private void updateSubscriptionRefreshState(boolean enabled) {
        if (activityMainBinding == null) {
            return;
        }

        activityMainBinding.subscriptionRefreshBtn.setEnabled(enabled);
        activityMainBinding.subscriptionRefreshBtn.setAlpha(enabled ? 1.0f : 0.45f);
        activityMainBinding.subscriptionRefreshBtn.setColorFilter(
                ThemeManager.getThemeColor(this, enabled ? android.R.attr.colorPrimary : android.R.attr.textColorHint));
    }

    private boolean shouldRenderInactiveSubscriptionState(boolean tracked,
                                                          boolean available,
                                                          boolean subscribed,
                                                          String status,
                                                          String message) {
        String normalizedStatus = normalizeSubscriptionText(status);
        String normalizedMessage = normalizeSubscriptionText(message);

        return !tracked
                || !available
                || !subscribed
                || isDomainNotFoundStatus(normalizedStatus, normalizedMessage)
                || normalizedStatus.contains("inactive")
                || normalizedStatus.contains("pending")
                || normalizedMessage.contains("inactive")
                || normalizedMessage.contains("pending");
    }

    private void renderSubscriptionBadge(String badge,
                                         int badgeBackgroundColor,
                                         int badgeTextColor,
                                         String detail,
                                         String meta) {
        if (activityMainBinding == null) {
            return;
        }

        activityMainBinding.subscriptionBadgeTv.setText(defaultIfEmpty(badge, "STATUS"));
        activityMainBinding.subscriptionBadgeCard.setCardBackgroundColor(badgeBackgroundColor);
        activityMainBinding.subscriptionBadgeTv.setTextColor(badgeTextColor);
        activityMainBinding.subscriptionStatusTv.setText(
                defaultIfEmpty(detail, "Subscription status unavailable"));

        String metaText = singleLine(meta);
        if (metaText.isEmpty()) {
            activityMainBinding.subscriptionMetaTv.setVisibility(View.GONE);
            activityMainBinding.subscriptionMetaTv.setText("");
        } else {
            activityMainBinding.subscriptionMetaTv.setVisibility(View.VISIBLE);
            activityMainBinding.subscriptionMetaTv.setText(metaText);
        }
    }

    private String joinSubscriptionMeta(String first, String second) {
        String left = singleLine(first);
        String right = singleLine(second);

        if (left.isEmpty()) {
            return right;
        }
        if (right.isEmpty()) {
            return left;
        }
        return left + " \u2022 " + right;
    }

    private String singleLine(String value) {
        if (value == null) {
            return "";
        }

        return value.replaceAll("\\s+", " ").trim();
    }

    private SubscriptionStatusEvaluator.Evaluation syncSubscriptionEvaluation() {
        if (session == null) {
            return SubscriptionStatusEvaluator.evaluate(false, "");
        }

        boolean storedExpired = session.getBooleanData(Session.SUBSCRIPTION_EXPIRED);
        SubscriptionStatusEvaluator.Evaluation evaluation = SubscriptionStatusEvaluator.evaluate(
                storedExpired,
                session.getData(Session.SUBSCRIPTION_EXPIRES_AT));

        if (storedExpired != evaluation.isExpired()) {
            session.setBooleanData(Session.SUBSCRIPTION_EXPIRED, evaluation.isExpired());
        }

        return evaluation;
    }

    private String buildInactiveSubscriptionMessage(String host, String status, String message) {
        String normalizedStatus = normalizeSubscriptionText(status);
        String normalizedMessage = normalizeSubscriptionText(message);
        String statusSuffix = buildStatusSuffix(status);
        boolean tracked = session.getBooleanData(SUBSCRIPTION_TRACKED);

        if (!tracked || isDomainNotFoundStatus(normalizedStatus, normalizedMessage)) {
            return "Domain not found for " + host + statusSuffix + ". Contact admin.";
        }

        if (normalizedStatus.contains("pending") || normalizedMessage.contains("pending")) {
            return "Subscription pending for " + host + statusSuffix + ". Contact admin.";
        }

        if (!message.trim().isEmpty()) {
            return statusSuffix.isEmpty() ? message.trim() : message.trim() + statusSuffix;
        }

        return "Subscription inactive for " + host + statusSuffix + ". Contact admin.";
    }

    private boolean isDomainNotFoundStatus(String status, String message) {
        return status.contains("not found")
                || status.contains("not_found")
                || status.contains("domain_not_found")
                || status.contains("missing")
                || status.contains("unknown")
                || status.contains("unregistered")
                || message.contains("not found")
                || message.contains("not_found")
                || message.contains("domain not found")
                || message.contains("unknown domain")
                || message.contains("unregistered");
    }

    private String normalizeSubscriptionText(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.US);
    }

    private boolean canProcessRequests() {
        return canProcessRequests(syncSubscriptionEvaluation());
    }

    private boolean canProcessRequests(SubscriptionStatusEvaluator.Evaluation evaluation) {
        if (session == null) {
            return false;
        }

        String host = extractDomainHost(session.getData(Session.API_DOMAIN_LINK));
        return SubscriptionAccessPolicy.canProcessRequests(
                isSubscriptionCheckCurrent(host),
                evaluation,
                session.getData(Session.SUBSCRIPTION_STATUS),
                session.getData(Session.SUBSCRIPTION_MESSAGE));
    }

    private boolean ensureSubscriptionActive() {
        String host = extractDomainHost(session.getData(Session.API_DOMAIN_LINK));
        if (!host.isEmpty() && !isSubscriptionCheckCurrent(host) && !isSubscriptionCheckInProgress) {
            refreshSubscriptionState(false);
        }

        if (canProcessRequests()) {
            isSubscriptionValid = true;
            return true;
        }

        isSubscriptionValid = false;
        renderSubscriptionStatusFromSession();
        return false;
    }

    private void disableRequestProcessing() {
        requestQueueSim1.clear();
        requestQueueSim2.clear();
        isFetchingPendingSim1.clear();
        isFetchingPendingSim2.clear();
        isWaitingForBalanceCheckSim1 = false;
        isWaitingForBalanceCheckSim2 = false;
        isProcessingSim1 = false;
        isProcessingSim2 = false;
        isAnySimProcessing = false;
        sim1Enabled = false;
        sim2Enabled = false;
        session.setBooleanData(Session.SIM1_ENABLED, false);
        session.setBooleanData(Session.SIM2_ENABLED, false);
        refreshPowerButton();
    }

    private String extractDomainHost(String domain) {
        return ServerConfig.normalizeSubscriptionDomain(domain);
    }

    private boolean hasStoredSubscriptionState() {
        return SubscriptionCheckSupport.hasStoredSubscriptionState(session);
    }

    private String getCurrentSubscriptionCheckDate() {
        return SubscriptionCheckSupport.getCurrentCheckSlotKey();
    }

    private boolean isSubscriptionCheckCurrent(String host) {
        return SubscriptionCheckSupport.isSubscriptionCheckCurrent(session, host);
    }

    private void scheduleNextInAppSubscriptionCheck() {
        subscriptionCheckHandler.removeCallbacks(dailySubscriptionCheckRunnable);
        long delayMs = Math.max(1000L,
                SubscriptionCheckSupport.getNextScheduledCheckTimeMillis() - System.currentTimeMillis());
        subscriptionCheckHandler.postDelayed(dailySubscriptionCheckRunnable, delayMs);
    }

    private String getSubscriptionBlockedMessage() {
        String host = extractDomainHost(session.getData(Session.API_DOMAIN_LINK));
        if (host.isEmpty()) {
            return "Set your server domain first";
        }

        if (isSubscriptionCheckInProgress || !isSubscriptionCheckCurrent(host)) {
            return "Checking subscription for " + host + "...";
        }

        SubscriptionStatusEvaluator.Evaluation evaluation = syncSubscriptionEvaluation();
        if (SubscriptionAccessPolicy.isExpired(
                evaluation,
                session.getData(Session.SUBSCRIPTION_STATUS),
                session.getData(Session.SUBSCRIPTION_MESSAGE))) {
            return buildExpiredSubscriptionMessage(
                    host,
                    session.getData(Session.SUBSCRIPTION_STATUS),
                    evaluation);
        }

        return buildInactiveSubscriptionMessage(
                host,
                session.getData(Session.SUBSCRIPTION_STATUS),
                session.getData(Session.SUBSCRIPTION_MESSAGE));
    }

    private String buildActiveSubscriptionMessage(String host,
                                                  String status,
                                                  Integer serverDaysUntilExpiry,
                                                  SubscriptionStatusEvaluator.Evaluation evaluation) {
        StringBuilder builder = new StringBuilder("Subscription active for ").append(host);
        appendStatusSummary(builder, status);

        String expiryLabel = evaluation.getDisplayDate();
        if (evaluation.expiresToday()) {
            builder.append(" • expires today");
            if (!expiryLabel.isEmpty()) {
                builder.append(" (").append(expiryLabel).append(")");
            }
            return builder.toString();
        }

        if (serverDaysUntilExpiry != null && serverDaysUntilExpiry >= 0) {
            builder.append(" • expires on ");
            if (!expiryLabel.isEmpty()) {
                builder.append(expiryLabel).append(" • ");
            }
            builder.append(serverDaysUntilExpiry)
                    .append(" day")
                    .append(serverDaysUntilExpiry == 1 ? "" : "s")
                    .append(" left");
            return builder.toString();
        }

        if (evaluation.getDaysLeft() >= 0) {
            builder.append(" • expires on ");
            if (!expiryLabel.isEmpty()) {
                builder.append(expiryLabel).append(" • ");
            }
            builder.append(evaluation.getDaysLeft())
                    .append(" day")
                    .append(evaluation.getDaysLeft() == 1 ? "" : "s")
                    .append(" left");
            return builder.toString();
        }

        if (evaluation.hasKnownExpiry() && !expiryLabel.isEmpty()) {
            builder.append(" • expires on ").append(expiryLabel);
        }

        return builder.toString();
    }

    private void showSubscriptionExpiredNotification(String message) {
        String notificationMessage = defaultIfEmpty(message, "Subscription expired");
        if (notificationMessage.equals(lastSubscriptionAlertMessage)) {
            return;
        }

        createSubscriptionAlertChannel();

        PendingIntent openApp = PendingIntent.getActivity(
                this,
                1,
                new Intent(this, MainActivity.class)
                        .setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP),
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, SUBSCRIPTION_ALERT_CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notification_drecharge)
                .setContentTitle("Subscription expired")
                .setContentText(notificationMessage)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(notificationMessage))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .setContentIntent(openApp);

        NotificationManager notificationManager =
                (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (notificationManager != null) {
            notificationManager.notify(SUBSCRIPTION_ALERT_NOTIFICATION_ID, builder.build());
            lastSubscriptionAlertMessage = notificationMessage;
        }
    }

    private void clearSubscriptionExpiredNotification() {
        lastSubscriptionAlertMessage = "";
        NotificationManager notificationManager =
                (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (notificationManager != null) {
            notificationManager.cancel(SUBSCRIPTION_ALERT_NOTIFICATION_ID);
        }
    }

    private void createSubscriptionAlertChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return;
        }

        NotificationManager notificationManager =
                (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (notificationManager == null) {
            return;
        }

        NotificationChannel channel = new NotificationChannel(
                SUBSCRIPTION_ALERT_CHANNEL_ID,
                "Subscription Alerts",
                NotificationManager.IMPORTANCE_HIGH);
        channel.setDescription("Alerts when the app subscription has expired");
        notificationManager.createNotificationChannel(channel);
    }

    private String buildExpiredSubscriptionMessage(String host, String status, SubscriptionStatusEvaluator.Evaluation evaluation) {
        StringBuilder builder = new StringBuilder("Subscription expired for ").append(host);
        appendStatusSummary(builder, status);

        String expiryLabel = evaluation.getDisplayDate();
        if (!expiryLabel.isEmpty()) {
            builder.append(" • expired on ").append(expiryLabel);
        }

        return builder.toString();
    }

    private void appendStatusSummary(StringBuilder builder, String status) {
        String formattedStatus = formatSubscriptionStatus(status);
        if (!formattedStatus.isEmpty()) {
            builder.append(" • status: ").append(formattedStatus);
        }
    }

    private String buildStatusSuffix(String status) {
        String formattedStatus = formatSubscriptionStatus(status);
        return formattedStatus.isEmpty() ? "" : " • status: " + formattedStatus;
    }

    private String formatSubscriptionStatus(String status) {
        String normalizedStatus = normalizeSubscriptionText(status);
        if (normalizedStatus.isEmpty()) {
            return "";
        }

        return normalizedStatus.replace('_', ' ');
    }

    private Integer getStoredDaysUntilExpiry() {
        String rawValue = session.getData(SUBSCRIPTION_DAYS_UNTIL_EXPIRY);
        if (rawValue == null || rawValue.trim().isEmpty()) {
            return null;
        }

        try {
            return Integer.parseInt(rawValue.trim());
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private void init() {
        try{
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
            }
        } catch (Exception e) {
            // Domain/timer config failed — show feedback so the user knows
            Toast.makeText(this, "Config load error: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }

        try{
            map = new HashMap<>();
            map.put("KEY_LOGIN", new HashSet<String>(Arrays.asList("running...", "waiting", "loading", "esperando")));
            map.put("KEY_ERROR", new HashSet<String>(Arrays.asList("problema", "problem", "error", "null")));

            boolean hasSim1Config = session.isSim1Valid() || !session.getActiveServicesForSim(1).isEmpty();
            if (hasSim1Config) {
                if (session.isSim1Valid()) {
                    String storedId = session.getData(Session.SIM1_ID);
                    if (!storedId.isEmpty()) sim1Id = Integer.parseInt(storedId);
                    String storedNum = session.getData(Session.SIM1_NUMBER);
                    if (!storedNum.isEmpty()) sim1Num = storedNum;
                    savedSim1Pin = session.getData(Session.SIM1_PIN);
                    savedSim1Time = session.getData(Session.SIM1_TIME);
                    savedSim1Bal = session.getData(Session.SIM1_MIN_BAL);
                    savedSim1ServiceCode = session.getData(SIM1_SERVICE_CODE);
                    savedSim1Service = session.getData(Session.SIM1_SERVICE).isEmpty() ? 0 : Integer.parseInt(session.getData(Session.SIM1_SERVICE));
                    savedSim1ServiceName = session.getData(Session.SIM1_SERVICE_NAME);
                }
            }
        } catch (Exception e) {
            // SIM1 config load failed — continue with defaults from session
        }

        try{
            boolean hasSim2Config = session.isSim2Valid() || !session.getActiveServicesForSim(2).isEmpty();
            if (hasSim2Config) {
                if (session.isSim2Valid()) {
                    String storedId2 = session.getData(Session.SIM2_ID);
                    if (!storedId2.isEmpty()) sim2Id = Integer.parseInt(storedId2);
                    String storedNum2 = session.getData(Session.SIM2_NUMBER);
                    if (!storedNum2.isEmpty()) sim2Num = storedNum2;
                    savedSim2Pin = session.getData(Session.SIM2_PIN);
                    savedSim2Time = session.getData(Session.SIM2_TIME);
                    savedSim2Bal = session.getData(Session.SIM2_MIN_BAL);
                    savedSim2ServiceCode = session.getData(SIM2_SERVICE_CODE);
                    savedSim2Service = session.getData(Session.SIM2_SERVICE).isEmpty() ? 0 : Integer.parseInt(session.getData(Session.SIM2_SERVICE));
                    savedSim2ServiceName = session.getData(Session.SIM2_SERVICE_NAME);
                }
            }
        } catch (Exception e) {
            // SIM2 config load failed — continue with defaults from session
        }

        restartPollingTimers();

    }

    private TimerTask simOneSchedule() {
        _simOneWorker = new TimerTask() {
            @Override
            public void run() {
                handler.post(simOneRunable = new Runnable() {
                    @Override
                    public void run() {
                        if (!sim1Enabled) {
                            if (!isAnyServiceEnabled()) {
                                stopCountdown();
                            }
                            return;
                        }
                        scheduleCountdown(getTimerTime());
                        List<ServiceConfig> cfgs = session.getActiveServicesForSim(1);
                        if (!cfgs.isEmpty()) {
                            for (ServiceConfig cfg : cfgs) {
                                getNewPending(cfg.name, ServiceCatalog.getCodeForService(cfg.name), cfg.number, getSim1Bal, String.valueOf(sim1Id), cfg.pin);
                            }
                        } else {
                            getNewPending(savedSim1ServiceName, savedSim1ServiceCode, session.getData(Session.SIM1_NUMBER), getSim1Bal, String.valueOf(sim1Id), savedSim1Pin);
                        }
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
                        if (!sim2Enabled) {
                            if (!isAnyServiceEnabled()) {
                                stopCountdown();
                            }
                            return;
                        }
                        scheduleCountdown(getTimerTime());
                        List<ServiceConfig> cfgs = session.getActiveServicesForSim(2);
                        if (!cfgs.isEmpty()) {
                            for (ServiceConfig cfg : cfgs) {
                                getNewPending(cfg.name, ServiceCatalog.getCodeForService(cfg.name), cfg.number, getSim2Bal, String.valueOf(sim2Id), cfg.pin);
                            }
                        } else {
                            getNewPending(savedSim2ServiceName, savedSim2ServiceCode, session.getData(Session.SIM2_NUMBER), getSim2Bal, String.valueOf(sim2Id), savedSim2Pin);
                        }
                    }
                });
            }
        };
        return _simTwoWorker;
    }

    public void updateSimBalanceTv(final String bal, final int slot) {
        try {
            if (slot == sim1Id) {
                getSim1Bal = bal;

                // Balance check সম্পূর্ণ হয়েছে - যদি status button ON থাকে এবং balance check এর অপেক্ষায় থাকে, তাহলে 30 সেকেন্ড পর request fetch শুরু করুন
                // Balance check complete - if status button is ON and waiting for balance check, start fetching requests after 30 seconds
                if (isWaitingForBalanceCheckSim1 && sim1Enabled) {
                    isWaitingForBalanceCheckSim1 = false;
                    handler.postDelayed(new Runnable() {
                        @Override
                        public void run() {
                            if (sim1Enabled && !isProcessingSim1 && requestQueueSim1.isEmpty()) {
                                fetchPendingForSim(1);
                            }
                        }
                    }, getTimerTime());
                }
            }
            if (slot == sim2Id) {
                getSim2Bal = bal;

                // Balance check সম্পূর্ণ হয়েছে - যদি status button ON থাকে এবং balance check এর অপেক্ষায় থাকে, তাহলে 30 সেকেন্ড পর request fetch শুরু করুন
                // Balance check complete - if status button is ON and waiting for balance check, start fetching requests after 30 seconds
                if (isWaitingForBalanceCheckSim2 && sim2Enabled) {
                    isWaitingForBalanceCheckSim2 = false;
                    handler.postDelayed(new Runnable() {
                        @Override
                        public void run() {
                            if (sim2Enabled && !isProcessingSim2 && requestQueueSim2.isEmpty()) {
                                fetchPendingForSim(2);
                            }
                        }
                    }, getTimerTime());
                }
            }
        } catch (Exception e) {
        }
    }

    public void updateResultTv(final int slot, final String message) {
        MainActivity.this.runOnUiThread(new Runnable() {
            public void run() {
                SimpleDateFormat sdf = new SimpleDateFormat("hh:mm:ss a", Locale.getDefault());
                String currentDateandTime = sdf.format(new Date());
                String newMessage = currentDateandTime + " " + message + "\n";
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
        return AppPermissionSupport.hasAllRuntimePermissions(this);
    }

    private void requestForSpecificPermission() {
        List<String> permissions = AppPermissionSupport.getMissingRuntimePermissions(this);
        if (permissions.isEmpty()) {
            return;
        }
        ActivityCompat.requestPermissions(MainActivity.this, permissions.toArray(new String[0]), 101);
    }

    private void getsSimServiceInfo() {
        try {
            SubscriptionManager subscriptionManager = (SubscriptionManager) getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE);

            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.READ_PHONE_STATE) != PackageManager.PERMISSION_GRANTED) {
                return;
            }
            List<SubscriptionInfo> subscriptionInfoList = subscriptionManager.getActiveSubscriptionInfoList();


            if (subscriptionInfoList != null && !subscriptionInfoList.isEmpty()) {
                SubscriptionInfo info0 = subscriptionInfoList.get(0);
                if (info0 != null) {
                    CharSequence carrierName0 = info0.getCarrierName();
                    sim1 = carrierName0 != null ? carrierName0.toString() : "SIM";
                    sim1Id = info0.getSimSlotIndex();
                    if (info0.getNumber() != null) {
                        sim1Num = info0.getNumber();
                        sim1Num = sim1Num.replace("+88", "").isEmpty() ? session.getData(Session.SIM1_NUMBER) : sim1Num.replace("+88", "");
                    }
                    session.setData(Session.SIM1_ID, String.valueOf(sim1Id));
                }

                if (subscriptionInfoList.size() >= 2) {
                    SubscriptionInfo info1 = subscriptionInfoList.get(1);
                    if (info1 != null) {
                        CharSequence carrierName1 = info1.getCarrierName();
                        sim2 = carrierName1 != null ? carrierName1.toString() : "SIM";
                        sim2Id = info1.getSimSlotIndex();

                        if (info1.getNumber() != null) {
                            sim2Num = info1.getNumber();
                            sim2Num = sim2Num.replace("+88", "").isEmpty() ? session.getData(Session.SIM2_NUMBER) : sim2Num.replace("+88", "");
                        }

                        session.setData(Session.SIM2_ID, String.valueOf(sim2Id));
                    }
                }
            }
        } catch (SecurityException e) {
            // Some OEM ROMs throw SecurityException from getActiveSubscriptionInfoList()
            // even when READ_PHONE_STATE is granted (requires carrier privileges on some devices).
            // Fall back silently — SIM IDs will be loaded from session in init().
        } catch (Exception e) {
            // Guard against any unexpected device-specific crashes from SubscriptionManager.
        }
    }

    private void domainSetting() {
        AlertDialog.Builder dBuilder = new AlertDialog.Builder(MainActivity.this);
        View dView = getLayoutInflater().inflate(R.layout.api_domain_link, null);
        dBuilder.setView(dView);
        final AlertDialog dialog = dBuilder.create();
        dialog.setCancelable(false);
        dialog.show();
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        }

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
                    session.clearSubscriptionState();
                    refreshServerRepository();
                    refreshSubscriptionState(true);
                    dialog.dismiss();
                    loadingDialog.dismissLoadingDialog();
                } else {
                    Toast.makeText(MainActivity.this, "Please give your valid domain", Toast.LENGTH_SHORT).show();
                }
            }
        });
    }


    private void refreshPowerButton() {
        boolean anyOn = sim1Enabled || sim2Enabled;
        updatePowerButtonState(anyOn);
        refreshCountdownState();
    }

    private void setSim1Enabled(boolean enabled) {
        session.setBooleanData(Session.SIM1_ENABLED, enabled);
        if (enabled) {
            if (!ensureSubscriptionActive()) {
                sim1Enabled = false;
                session.setBooleanData(Session.SIM1_ENABLED, false);
                Toast.makeText(this, getSubscriptionBlockedMessage(), Toast.LENGTH_SHORT).show();
                refreshPowerButton();
                return;
            }
            List<ServiceConfig> sim1Cfgs = session.getActiveServicesForSim(1);
            boolean hasConfig = !sim1Cfgs.isEmpty() || (savedSim1Pin != null && !savedSim1Pin.isEmpty() && savedSim1Service != 0);
            if (!hasConfig) {
                sim1Enabled = false;
                session.setBooleanData(Session.SIM1_ENABLED, false);
                Toast.makeText(this, "Please Check the system settings", Toast.LENGTH_SHORT).show();
                refreshPowerButton();
                return;
            }
            sim1Enabled = true;
            scheduleCountdown(getTimerTime());
            callGetNewPendingAfterBalanceCheck(sim1Id);
        } else {
            sim1Enabled = false;
            isWaitingForBalanceCheckSim1 = false;
        }
        refreshPowerButton();
    }

    private void setSim2Enabled(boolean enabled) {
        session.setBooleanData(Session.SIM2_ENABLED, enabled);
        if (enabled) {
            if (!ensureSubscriptionActive()) {
                sim2Enabled = false;
                session.setBooleanData(Session.SIM2_ENABLED, false);
                Toast.makeText(this, getSubscriptionBlockedMessage(), Toast.LENGTH_SHORT).show();
                refreshPowerButton();
                return;
            }
            List<ServiceConfig> sim2Cfgs = session.getActiveServicesForSim(2);
            boolean hasConfig = !sim2Cfgs.isEmpty() || (savedSim2Pin != null && !savedSim2Pin.isEmpty() && savedSim2Service != 0);
            if (!hasConfig) {
                sim2Enabled = false;
                session.setBooleanData(Session.SIM2_ENABLED, false);
                Toast.makeText(this, "Please Check the system settings", Toast.LENGTH_SHORT).show();
                refreshPowerButton();
                return;
            }
            sim2Enabled = true;
            scheduleCountdown(getTimerTime());
            callGetNewPendingAfterBalanceCheck(sim2Id);
        } else {
            sim2Enabled = false;
            isWaitingForBalanceCheckSim2 = false;
        }
        refreshPowerButton();
    }
    //endregion Settings And Service

    //region System Override
    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        if (requestCode == 101) {
            boolean granted = grantResults.length > 0;
            for (int result : grantResults) {
                if (result != PackageManager.PERMISSION_GRANTED) {
                    granted = false;
                    break;
                }
            }

            if (granted) {
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
        if (!ensureSubscriptionActive()) {
            return;
        }
        if (!isNetworkAvailable()) {
            return;
        }
        if (serverRepository == null) {
            return;
        }

        final int slotId;
        try {
            slotId = Integer.parseInt(simSlotId);
        } catch (NumberFormatException e) {
            return;
        }

        if (!isSimEnabled(slotId)) {
            return;
        }

        final String fetchKey = serviceName == null ? "" : serviceName;
        if (slotId == sim1Id) {
            if (isFetchingPendingSim1.contains(fetchKey)) {
                return;
            }
            isFetchingPendingSim1.add(fetchKey);
        } else if (slotId == sim2Id) {
            if (isFetchingPendingSim2.contains(fetchKey)) {
                return;
            }
            isFetchingPendingSim2.add(fetchKey);
        }
        serverRepository.fetchPendingRequests(serviceName, company, simNumber, simSlotId, simBal,
                new ModemServerRepository.PendingRequestsCallback() {
                    @Override
                    public void onPendingRequest(ServiceRequest request) {
                        updateResultTv(slotId, "New Request: " + request.getPhone() + " TK: " + request.getAmount());

                        RequestData requestData = new RequestData(
                                request.getSid(),
                                request.getPcode(),
                                request.getPhone(),
                                request.getAmount(),
                                request.getType(),
                                request.getPackageName(),
                                request.getSender(),
                                request.isPowerLoad(),
                                slotId,
                                simPin,
                                serviceName
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
                        // Service stays ON until user manually turns it off
                    }

                    @Override
                    public void onFailure(Throwable throwable) {
                    }

                    @Override
                    public void onComplete() {
                        if (slotId == sim1Id) {
                            isFetchingPendingSim1.remove(fetchKey);
                        } else if (slotId == sim2Id) {
                            isFetchingPendingSim2.remove(fetchKey);
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
        if (!ensureSubscriptionActive()) {
            return;
        }
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
        scheduleSafetyTimeout(simSlotId);
        processRequest(request);
    }

    // রিকোয়েস্ট প্রসেস করার মেথড
    // Method to process a request
    private void processRequest(RequestData request) {
        if (!ensureSubscriptionActive()) {
            return;
        }
        String service = request.service;
        String sid = request.sid;
        String pcode = request.pcode;
        // Financial request fields must be validated as-is. We normalize formatting,
        // but we never guess, auto-correct, or swap phone and amount values.
        String phone = normalizeBdPhone(request.phone);
        String amount = normalizeAmountValue(request.amount);
        String type = request.type;
        String package_name = request.package_name;
        currentRequestPcode = request.pcode == null ? "" : request.pcode;
        boolean isPowerLoad = request.isPowerLoad;
        int simSlotId = request.simSlotId;
        String simPin = request.simPin;

        if (!looksLikeBdPhone(phone)) {
            if (simSlotId == sim1Id) sim_number = session.getData(Session.SIM1_NUMBER);
            if (simSlotId == sim2Id) sim_number = session.getData(Session.SIM2_NUMBER);
            InsertNewPopUpMessage("Invalid phone number: " + request.phone, sid, "ValidationError", sim_number, simSlotId);
            onRequestCompleted(simSlotId);
            return;
        }

        if (!amount.matches("\\d+(\\.\\d+)?")) {
            if (simSlotId == sim1Id) sim_number = session.getData(Session.SIM1_NUMBER);
            if (simSlotId == sim2Id) sim_number = session.getData(Session.SIM2_NUMBER);
            InsertNewPopUpMessage("Invalid amount: " + request.amount, sid, "ValidationError", sim_number, simSlotId);
            onRequestCompleted(simSlotId);
            return;
        }

        // Load per-service config — USSD dial templates may be customized by the user
        ServiceConfig svcCfg = session.getServiceConfig(service);
        currentRequestSender = (svcCfg.number != null && !svcCfg.number.trim().isEmpty())
                ? svcCfg.number.trim()
                : (request.sender == null ? "" : request.sender);

        // Honour the service's schedule: if the service is currently in its disabled window,
        // skip the request so it is not processed outside the configured time range.
        if (!Session.isScheduledActiveNow(svcCfg)) {
            updateResultTv(simSlotId, "Skipped [" + service + "] — outside scheduled hours");
            onRequestCompleted(simSlotId);
            return;
        }

        List<String> customSteps = UssdDialTemplateResolver.resolveSteps(svcCfg, type, phone, amount, simPin);
        // A user-enabled custom USSD template should fully replace the built-in
        // hardcoded dial path for every service type, including mobile banking.
        if (!customSteps.isEmpty()) {
            executeCustomUssd(customSteps, sid, simSlotId);
            return;
        }

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
            case "Banglalink":
                if (isPowerLoad)
                    packageLoadSent("*555*", "*555*", sid, package_name, phone, amount, type, simSlotId, simPin, service);
                else
                    BanglalinkLoadSent(sid, pcode, phone, amount, type, simSlotId, simPin);
                break;
            case "Skitto":
                if (isPowerLoad)
                    packageLoadSent("*444*", "*444*", sid, package_name, phone, amount, type, simSlotId, simPin, "Grameen");
                else
                    GrameenPhoneLoadSend(sid, pcode, phone, amount, type, simSlotId, simPin);
                break;
            case "Taletalk":
                TaletalkLoadSend(sid, pcode, phone, amount, type, simSlotId, simPin);
                break;
            case "bKash-Personal-SIM": {
                String ic = (svcCfg.customUssdEnabled && !svcCfg.dialCode1.isEmpty()) ? svcCfg.dialCode1 : "*247#";
                if (pcode.equals("BKS")) bKashSendMoney(sid, phone, amount, simSlotId, simPin, ic);
                if (pcode.equals("BKA")) bKashCashOut(sid, phone, amount, simSlotId, simPin, ic);
                break;
            }
            case "bKash-Agent-SIM": {
                String ic = (svcCfg.customUssdEnabled && !svcCfg.dialCode1.isEmpty()) ? svcCfg.dialCode1 : "*247#";
                if (pcode.equals("BK")) {
                    bKashCashIn(sid, phone, amount, simSlotId, simPin, ic);
                }
                break;
            }
            case "Roket-Personal-SIM": {
                String ic = (svcCfg.customUssdEnabled && !svcCfg.dialCode1.isEmpty()) ? svcCfg.dialCode1 : "*322#";
                if (pcode.equals("RKS")) RoketSendMoney(sid, phone, amount, simSlotId, simPin, ic);
                if (pcode.equals("RKA")) RoketCashOut(sid, phone, amount, simSlotId, simPin, ic);
                break;
            }
            case "Roket-Agent-SIM": {
                String ic = (svcCfg.customUssdEnabled && !svcCfg.dialCode1.isEmpty()) ? svcCfg.dialCode1 : "*322#";
                if (pcode.equals("RK")) RoketCashIn(sid, phone, amount, simSlotId, simPin, ic);
                break;
            }
            case "Nagad-Personal-SIM": {
                String ic = (svcCfg.customUssdEnabled && !svcCfg.dialCode1.isEmpty()) ? svcCfg.dialCode1 : "*167#";
                if (pcode.equals("NGA")) NagadCashOut(sid, phone, amount, simSlotId, simPin, ic);
                if (pcode.equals("NGS")) NagadSendMoney(sid, phone, amount, simSlotId, simPin, ic);
                break;
            }
            case "Nagad-Agent-SIM": {
                String ic = (svcCfg.customUssdEnabled && !svcCfg.dialCode1.isEmpty()) ? svcCfg.dialCode1 : "*167#";
                if (pcode.equals("NG")) NagadCashIn(sid, phone, amount, simSlotId, simPin, ic);
                break;
            }
            case "bKash-Load": {
                String ic = (svcCfg.customUssdEnabled && !svcCfg.dialCode1.isEmpty()) ? svcCfg.dialCode1 : "*247#";
                bKashLoad(sid, pcode, phone, amount, simSlotId, simPin, ic);
                break;
            }
            case "Nagad-Load": {
                String ic = (svcCfg.customUssdEnabled && !svcCfg.dialCode1.isEmpty()) ? svcCfg.dialCode1 : "*167#";
                NagadLoad(sid, pcode, phone, amount, simSlotId, simPin, ic);
                break;
            }
            default:
                // যদি কোনো সেবা মিলে না, প্রসেসিং ফ্ল্যাগ রিসেট করুন এবং পরবর্তী রিকোয়েস্ট প্রসেস করুন
                // If no service matches, reset processing flag and process next request
                // Use onRequestCompleted() which correctly resets BOTH per-SIM and global lock flags.
                // Previously, isAnySimProcessing was not reset here, causing a permanent processing lock.
                onRequestCompleted(simSlotId);
                break;
        }
    }

    // রিকোয়েস্ট সম্পন্ন হলে কল করুন - পরবর্তী রিকোয়েস্ট প্রসেস করার জন্য
    // Call this when request is completed - to process next request
    private synchronized void onRequestCompleted(int simSlotId) {
        cancelSafetyTimeout(simSlotId);
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

        // Reset global lock
        isAnySimProcessing = false;

        // If there are more queued requests for this SIM, process the next one after the delay
        if (hasMoreRequestsFromSameSim) {
            handler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    processNextInQueue(simSlotId);
                }
            }, simProcessingDelay);
        }
        // No balance check or server fetch here — the periodic timer handles all polling
    }

    /** Dials a single-step USSD code, posts the response, and completes the request. */
    private void singleStepUssd(String dialCode, String sid, int simSlotId) {
        if (simSlotId == sim1Id) sim_number = session.getData(Session.SIM1_NUMBER);
        else sim_number = session.getData(Session.SIM2_NUMBER);
        final String finalSimNum = sim_number;
        ussdApi.callUSSDInvoke(dialCode, simSlotId, map, new USSDController.CallbackInvoke() {
            @Override
            public void responseInvoke(String message) {
                ussdApi.cancel();
                InsertNewPopUpMessage(
                        message.replaceAll(System.lineSeparator(), " "), sid, "FlashMessage", finalSimNum, simSlotId);
                onRequestCompleted(simSlotId);
            }
            @Override
            public void over(String message) {
                onRequestCompleted(simSlotId);
            }
        });
    }

    /**
     * Routes a resolved custom USSD step list to the correct executor.
     * A single entry is dialed directly; two or more entries use the multi-step flow
     * (dial steps[0], then send steps[1..n] sequentially into the USSD dialog).
     */
    private void executeCustomUssd(List<String> steps, String sid, int simSlotId) {
        if (steps.size() == 1) {
            singleStepUssd(steps.get(0), sid, simSlotId);
        } else {
            multiStepUssd(steps, sid, simSlotId);
        }
    }

    /**
     * Dials steps.get(0) as the initial USSD code, then sends each remaining step
     * (steps[1..n]) as sequential inputs into the USSD dialog.
     * The final step's response is posted as the transaction result.
     *
     * Template example: {@code *247#-1-{PHONE}-{AMOUNT}-{PIN}}
     */
    private void multiStepUssd(List<String> steps, String sid, int simSlotId) {
        if (simSlotId == sim1Id) sim_number = session.getData(Session.SIM1_NUMBER);
        else sim_number = session.getData(Session.SIM2_NUMBER);
        final String finalSimNum = sim_number;
        final List<String> inputSteps = steps.subList(1, steps.size());
        ussdApi.callUSSDInvoke(steps.get(0), simSlotId, map, new USSDController.CallbackInvoke() {
            @Override
            public void responseInvoke(String message) {
                // First USSD menu received — start sending subsequent steps
                sendCustomStep(inputSteps, 0, sid, simSlotId, finalSimNum);
            }
            @Override
            public void over(String message) {
                onRequestCompleted(simSlotId);
            }
        });
    }

    /**
     * Sends steps.get(index) into the active USSD dialog, then recurses for the next step.
     * On the last step the final USSD response is captured and posted as the result.
     */
    private void sendCustomStep(List<String> steps, int index, String sid, int simSlotId, String simNum) {
        if (index >= steps.size()) {
            ussdApi.cancel();
            onRequestCompleted(simSlotId);
            return;
        }
        boolean isLast = (index == steps.size() - 1);
        ussdApi.send(steps.get(index), new USSDController.CallbackMessage() {
            @Override
            public void responseMessage(String message) {
                if (isLast) {
                    ussdApi.cancel();
                    InsertNewPopUpMessage(
                            message.replaceAll(System.lineSeparator(), " "), sid, "FlashMessage", simNum, simSlotId);
                    onRequestCompleted(simSlotId);
                } else {
                    sendCustomStep(steps, index + 1, sid, simSlotId, simNum);
                }
            }
        });
    }

    private void scheduleSafetyTimeout(final int simSlotId) {
        cancelSafetyTimeout(simSlotId);
        Runnable r = new Runnable() {
            @Override
            public void run() {
                onRequestCompleted(simSlotId);
            }
        };
        if (simSlotId == sim1Id) safetyTimeoutRunnable1 = r;
        else safetyTimeoutRunnable2 = r;
        handler.postDelayed(r, REQUEST_SAFETY_TIMEOUT_MS);
    }

    private void cancelSafetyTimeout(int simSlotId) {
        if (simSlotId == sim1Id && safetyTimeoutRunnable1 != null) {
            handler.removeCallbacks(safetyTimeoutRunnable1);
            safetyTimeoutRunnable1 = null;
        } else if (simSlotId == sim2Id && safetyTimeoutRunnable2 != null) {
            handler.removeCallbacks(safetyTimeoutRunnable2);
            safetyTimeoutRunnable2 = null;
        }
    }

    /** Fetches pending requests for all active services on the given SIM slot (1 or 2). */
    private void fetchPendingForSim(int simSlot) {
        if (!ensureSubscriptionActive()) {
            return;
        }
        if (!isNetworkAvailable()) {
            return;
        }
        updateResultTv(simSlot == 1 ? sim1Id : sim2Id, "Searching...");
        if (simSlot == 1) {
            List<ServiceConfig> cfgs = session.getActiveServicesForSim(1);
            if (!cfgs.isEmpty()) {
                for (ServiceConfig cfg : cfgs) {
                    getNewPending(cfg.name, ServiceCatalog.getCodeForService(cfg.name), cfg.number, getSim1Bal, String.valueOf(sim1Id), cfg.pin);
                }
            } else {
                getNewPending(savedSim1ServiceName, savedSim1ServiceCode, session.getData(Session.SIM1_NUMBER), getSim1Bal, String.valueOf(sim1Id), savedSim1Pin);
            }
        } else {
            List<ServiceConfig> cfgs = session.getActiveServicesForSim(2);
            if (!cfgs.isEmpty()) {
                for (ServiceConfig cfg : cfgs) {
                    getNewPending(cfg.name, ServiceCatalog.getCodeForService(cfg.name), cfg.number, getSim2Bal, String.valueOf(sim2Id), cfg.pin);
                }
            } else {
                getNewPending(savedSim2ServiceName, savedSim2ServiceCode, session.getData(Session.SIM2_NUMBER), getSim2Bal, String.valueOf(sim2Id), savedSim2Pin);
            }
        }
    }

    //region All SIM Balance Query
    private void queryForSetSimWithBalance(String savedSimServiceName, String simNumber, String simPin, int simId) {
        switch (savedSimServiceName) {
            case "Grameen":
                getGrameenLoadBalance(simNumber, simPin, simId);
                break;
            case "Skitto":
            case "Banglalink":
                Toast.makeText(this, savedSimServiceName + " not found balance check ussd dial code", Toast.LENGTH_SHORT).show();
                callGetNewPendingAfterBalanceCheck(simId);
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
                callGetNewPendingAfterBalanceCheck(simId);
                break;
        }
    }


    //endregion All SIM Balance Query

    private void callGetNewPendingAfterBalanceCheck(int simId) {
        if (!ensureSubscriptionActive()) {
            return;
        }
        if (simId == sim1Id) {
            if (sim1Enabled) {
                handler.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        if (sim1Enabled && !isProcessingSim1 && requestQueueSim1.isEmpty()) {
                            fetchPendingForSim(1);
                        }
                    }
                }, getTimerTime());
            }
        } else if (simId == sim2Id) {
            if (sim2Enabled) {
                handler.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        if (sim2Enabled && !isProcessingSim2 && requestQueueSim2.isEmpty()) {
                            fetchPendingForSim(2);
                        }
                    }
                }, getTimerTime());
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
                callGetNewPendingAfterBalanceCheck(simId);
            }
        });
    }

    private void RobiLoadSent(String sid, String pcode, String phone, String amount, String type, int simSlotId, String simPin) {
        String phonecodedial = "";

        if (type.equals("1")) {
            phonecodedial = "*8383*2*" + phone + "*" + amount + "*" + simPin + "#";
        }
        if (type.equals("0")) {
            phonecodedial = "*8383*3*" + phone + "*" + amount + "*" + simPin + "#";
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
                callGetNewPendingAfterBalanceCheck(simId);
            }
        });
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
                Toast.makeText(MainActivity.this, "USSD Error: " + message, Toast.LENGTH_LONG).show();
                // Error হলে প্রসেসিং ফ্ল্যাগ রিসেট করুন এবং পরবর্তী রিকোয়েস্ট প্রসেস করুন
                // Reset processing flag on error and process next request
                onRequestCompleted(simSlotId);
            }
        });
    }
    //endregion Airtel

    //region Banglalink
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
                onRequestCompleted(simSlotId);
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
                callGetNewPendingAfterBalanceCheck(simId);
            }
        });
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
                callGetNewPendingAfterBalanceCheck(simId);
            }
        });
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
                onRequestCompleted(simSlotId);
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
                    if (!message.isEmpty()) {
                        ussdSendForBalance(message, keyString, PinCode, SimID, ussdApi);
                    }
                }

                @Override
                public void over(String message) {
                    callGetNewPendingAfterBalanceCheck(SimID);
                }
            });
        }
    }

    private void bKashSendMoney(String flexiId, String phone, String amount, int simSlotId, String simPin, String initialCode) {

        ussdApi.callUSSDInvoke(initialCode, simSlotId, map, new USSDController.CallbackInvoke() {
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
                                                        if (simSlotId == sim1Id) {
                                                            sim_number = session.getData(Session.SIM1_NUMBER);
                                                        }

                                                        if (simSlotId == sim2Id) {
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
                onRequestCompleted(simSlotId);
            }
        });
    }

    private void bKashCashOut(String flexiId, String phone, String amount, int simSlotId, String simPin, String initialCode) {
        ussdApi.callUSSDInvoke(initialCode, simSlotId, map, new USSDController.CallbackInvoke() {
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
                                                        if (simSlotId == sim1Id) {

                                                            sim_number = session.getData(Session.SIM1_NUMBER);
                                                        }

                                                        if (simSlotId == sim2Id) {
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
                onRequestCompleted(simSlotId);
            }
        });
    }

    private void bKashCashIn(String flexiId, String phone, String amount, int simSlotId, String simPin, String initialCode) {
        // phone = actual mobile number, amount = actual taka value (normalised by processRequest)
        // Flow: *247# -> 1 (Cash In) -> phone (number) -> amount (taka) -> PIN
        ussdApi.callUSSDInvoke(initialCode, simSlotId, map, new USSDController.CallbackInvoke() {
            @Override
            public void responseInvoke(String message) {
                // Step 1: Select Cash In (1)
                ussdApi.send("1", new USSDController.CallbackMessage() {
                    @Override
                    public void responseMessage(String message) {
                        // Step 2: Enter mobile number
                        ussdApi.send(phone, new USSDController.CallbackMessage() {
                            @Override
                            public void responseMessage(String message) {
                                // Step 3: Enter amount/taka
                                ussdApi.send(amount, new USSDController.CallbackMessage() {
                                    @Override
                                    public void responseMessage(String message) {
                                        // Step 4: Enter PIN to complete
                                        ussdApi.send(simPin, new USSDController.CallbackMessage() {
                                            @Override
                                            public void responseMessage(String message) {
                                                String cleanMessage = message == null ? "" : message.replaceAll(System.lineSeparator(), " ").trim();
                                                ussdApi.cancel();
                                                if (simSlotId == sim1Id) {
                                                    sim_number = session.getData(Session.SIM1_NUMBER);
                                                }
                                                if (simSlotId == sim2Id) {
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
                // USSD session ended early - send result and complete request
                String cleanMessage = message == null ? "" : message.replaceAll(System.lineSeparator(), " ").trim();
                if (!cleanMessage.isEmpty()) {
                    if (simSlotId == sim1Id) {
                        sim_number = session.getData(Session.SIM1_NUMBER);
                    }
                    if (simSlotId == sim2Id) {
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
                    callGetNewPendingAfterBalanceCheck(SimID);
                }
            });
        }
    }

    private void RoketSendMoney(String flexiId, String phone, String amount, int simSlotId, String simPin, String initialCode) {

        ussdApi.callUSSDInvoke(initialCode, simSlotId, map, new USSDController.CallbackInvoke() {
            @Override
            public void responseInvoke(String message) {
                if (message.contains("Do you want to continue?")) {
                    ussdApi.send("2", new USSDController.CallbackMessage() {
                        @Override
                        public void responseMessage(String message) {
                            continueRoketSendMoney(flexiId, phone, amount, simSlotId, simPin);
                        }
                    });
                } else {
                    continueRoketSendMoney(flexiId, phone, amount, simSlotId, simPin);
                }
            }

            @Override
            public void over(String message) {
                onRequestCompleted(simSlotId);
            }
        });
    }

    private void RoketCashOut(String flexiId, String phone, String amount, int simSlotId, String simPin, String initialCode) {
        ussdApi.callUSSDInvoke(initialCode, simSlotId, map, new USSDController.CallbackInvoke() {
            @Override
            public void responseInvoke(String message) {
                if (message.contains("Do you want to continue?")) {
                    ussdApi.send("2", new USSDController.CallbackMessage() {
                        @Override
                        public void responseMessage(String message) {
                            continueRoketCashOut(flexiId, phone, amount, simSlotId, simPin);
                        }
                    });
                } else {
                    continueRoketCashOut(flexiId, phone, amount, simSlotId, simPin);
                }
            }

            @Override
            public void over(String message) {
                onRequestCompleted(simSlotId);
            }
        });
    }

    private void RoketCashIn(String flexiId, String phone, String amount, int simSlotId, String simPin, String initialCode) {
        ussdApi.callUSSDInvoke(initialCode, simSlotId, map, new USSDController.CallbackInvoke() {
            @Override
            public void responseInvoke(String message) {
                if (message.contains("Do you want to continue?") || message.contains("Resume session?")) {
                    ussdApi.send("2", new USSDController.CallbackMessage() {
                        @Override
                        public void responseMessage(String message) {
                            continueRoketCashIn(flexiId, phone, amount, simSlotId, simPin);
                        }
                    });
                } else {
                    continueRoketCashIn(flexiId, phone, amount, simSlotId, simPin);
                }
            }

            @Override
            public void over(String message) {
                onRequestCompleted(simSlotId);
            }
        });
    }

    private void continueRoketSendMoney(String flexiId, String phone, String amount, int simSlotId, String simPin) {
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
                                        if (simSlotId == sim1Id) {
                                            sim_number = session.getData(Session.SIM1_NUMBER);
                                        }

                                        if (simSlotId == sim2Id) {
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

    private void continueRoketCashOut(String flexiId, String phone, String amount, int simSlotId, String simPin) {
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
                                                if (simSlotId == sim1Id) {
                                                    sim_number = session.getData(Session.SIM1_NUMBER);
                                                }

                                                if (simSlotId == sim2Id) {
                                                    sim_number = session.getData(Session.SIM2_NUMBER);
                                                }
                                                InsertNewPopUpMessage(message.replaceAll(System.lineSeparator(), " "), flexiId, "FlashMessage", sim_number, simSlotId);
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

    private void continueRoketCashIn(String flexiId, String phone, String amount, int simSlotId, String simPin) {
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
                                        if (simSlotId == sim1Id) {
                                            sim_number = session.getData(Session.SIM1_NUMBER);
                                        }

                                        if (simSlotId == sim2Id) {
                                            sim_number = session.getData(Session.SIM2_NUMBER);
                                        }
                                        InsertNewPopUpMessage(message.replaceAll(System.lineSeparator(), " "), flexiId, "FlashMessage", sim_number, simSlotId);
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
                    callGetNewPendingAfterBalanceCheck(SimID);
                }
            });
        }
    }

    private void NagadSendMoney(String flexiId, String phone, String amount, int simSlotId, String simPin, String initialCode) {
        ussdApi.callUSSDInvoke(initialCode, simSlotId, map, new USSDController.CallbackInvoke() {
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
                                                                    if (simSlotId == sim1Id) {
                                                                        sim_number = session.getData(Session.SIM1_NUMBER);
                                                                    }

                                                                    if (simSlotId == sim2Id) {
                                                                        sim_number = session.getData(Session.SIM2_NUMBER);
                                                                    }
                                                                    InsertNewPopUpMessage(message.replaceAll(System.lineSeparator(), " "), flexiId, "FlashMessage", sim_number, simSlotId);
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
                                                            if (simSlotId == sim1Id) {
                                                                sim_number = session.getData(Session.SIM1_NUMBER);
                                                            }

                                                            if (simSlotId == sim2Id) {
                                                                sim_number = session.getData(Session.SIM2_NUMBER);
                                                            }
                                                            InsertNewPopUpMessage(message.replaceAll(System.lineSeparator(), " "), flexiId, "FlashMessage", sim_number, simSlotId);
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
            }

            @Override
            public void over(String message) {
                onRequestCompleted(simSlotId);
            }
        });
    }

    private void NagadCashOut(String flexiId, String phone, String amount, int simSlotId, String simPin, String initialCode) {
        ussdApi.callUSSDInvoke(initialCode, simSlotId, map, new USSDController.CallbackInvoke() {
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
                                                            if (simSlotId == sim1Id) {
                                                                sim_number = session.getData(Session.SIM1_NUMBER);
                                                            }

                                                            if (simSlotId == sim2Id) {
                                                                sim_number = session.getData(Session.SIM2_NUMBER);
                                                            }
                                                            InsertNewPopUpMessage(message.replaceAll(System.lineSeparator(), " "), flexiId, "FlashMessage", sim_number, simSlotId);
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
                                                    ussdApi.cancel();
                                                    if (simSlotId == sim1Id) {
                                                        sim_number = session.getData(Session.SIM1_NUMBER);
                                                    } else if (simSlotId == sim2Id) {
                                                        sim_number = session.getData(Session.SIM2_NUMBER);
                                                    }
                                                    InsertNewPopUpMessage(message.replaceAll(System.lineSeparator(), " "), flexiId, "FlashMessage", sim_number, simSlotId);
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
            }

            @Override
            public void over(String message) {
                onRequestCompleted(simSlotId);
            }
        });
    }

    private void NagadCashIn(String flexiId, String phone, String amount, int simSlotId, String simPin, String initialCode) {
        ussdApi.callUSSDInvoke(initialCode, simSlotId, map, new USSDController.CallbackInvoke() {
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
                                                            updateResultTv(simSlotId, message.replaceAll(System.lineSeparator(), " "));
                                                            ussdApi.cancel();
                                                            if (simSlotId == sim1Id) {
                                                                sim_number = session.getData(Session.SIM1_NUMBER);
                                                            } else if (simSlotId == sim2Id) {
                                                                sim_number = session.getData(Session.SIM2_NUMBER);
                                                            }
                                                            InsertNewPopUpMessage(message.replaceAll(System.lineSeparator(), " "), flexiId, "FlashMessage", sim_number, simSlotId);
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
                                                    updateResultTv(simSlotId, message.replaceAll(System.lineSeparator(), " "));
                                                    ussdApi.cancel();
                                                    if (simSlotId == sim1Id) {
                                                        sim_number = session.getData(Session.SIM1_NUMBER);
                                                    } else if (simSlotId == sim2Id) {
                                                        sim_number = session.getData(Session.SIM2_NUMBER);
                                                    }
                                                    InsertNewPopUpMessage(message.replaceAll(System.lineSeparator(), " "), flexiId, "FlashMessage", sim_number, simSlotId);
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
            }

            @Override
            public void over(String message) {
                onRequestCompleted(simSlotId);
            }
        });
    }
    //endregion Nagad

    //region bKashLoad
    private void bKashLoad(String flexiId, String pccode, String phone, String amount, int simSlotId, String simPin, String initialCode) {
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
            ussdApi.callUSSDInvoke(initialCode, simSlotId, map, new USSDController.CallbackInvoke() {
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
                                                                    if (simSlotId == sim1Id) {
                                                                        sim_number = session.getData(Session.SIM1_NUMBER);
                                                                        op = session.getData(Session.SIM1_SERVICE_NAME);
                                                                    }

                                                                    if (simSlotId == sim2Id) {
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
                    onRequestCompleted(simSlotId);
                }
            });
        }
    }

    //endregion bKashLoad

    // region NagadLoad
    private void NagadLoad(String flexiId, String pccode, String phone, String amount, int simSlotId, String simPin, String initialCode) {
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
            ussdApi.callUSSDInvoke(initialCode, simSlotId, map, new USSDController.CallbackInvoke() {
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
                                                                                if (simSlotId == sim1Id) {
                                                                                    sim_number = session.getData(Session.SIM1_NUMBER);
                                                                                    op = session.getData(Session.SIM1_SERVICE_NAME);
                                                                                }

                                                                                if (simSlotId == sim2Id) {
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
                                                                        if (simSlotId == sim1Id) {
                                                                            sim_number = session.getData(Session.SIM1_NUMBER);
                                                                            op = session.getData(Session.SIM1_SERVICE_NAME);
                                                                        }

                                                                        if (simSlotId == sim2Id) {
                                                                            sim_number = session.getData(Session.SIM2_NUMBER);
                                                                            op = session.getData(Session.SIM2_SERVICE_NAME);
                                                                        }
                                                                        InsertNewPopUpMessage(message.replaceAll(System.lineSeparator(), " "), flexiId, "NagadLoad", sim_number, simSlotId);
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
                }

                @Override
                public void over(String message) {
                    onRequestCompleted(simSlotId);
                }
            });
        }
    }
    //endregion NagadLoad

    //region Mobile Banking Balance Check
    private void ussdSendForBalance(String msbody, String[] ussdMsg, String pinCode, int SimId, USSDApi ussdApi) {

        String sk = Constant.ussdCodeFindFromArray(msbody, ussdMsg);
        if (!sk.isEmpty()) {
            ussdApi.send(sk, new USSDController.CallbackMessage() {
                @Override
                public void responseMessage(String message) {
                    if (!message.isEmpty()) {
                        ussdSendForBalance(message, ussdMsg, pinCode, SimId, ussdApi);
                    }
                }
            });
        } else {
            String lower = msbody.toLowerCase();
            if (lower.contains("pin") || lower.contains("enter") || lower.contains("pincode")) {
                // PIN prompt detected - send PIN to complete balance check
                if (pinCode == null || pinCode.trim().isEmpty()) {
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
                ussdApi.cancel();
                callGetNewPendingAfterBalanceCheck(SimId);
            }
        }
    }
    //endregion

    //region InsertNewPopUpMessage
    private void InsertNewPopUpMessage(String message, String st, String senderNum, String simNumber, int simSlot) {
        sim_number = "";
        if (!ensureSubscriptionActive()) {
            return;
        }
        if (serverRepository == null) {
            return;
        }

        // Capture fields now — they may change before the delayed call executes
        final String pcode = currentRequestPcode;
        final String sender = resolveRequestSender(senderNum);

        // On Android 11+ the data connection is briefly suspended during a USSD call.
        // Delay 1500 ms so the data network has time to restore before we hit the server.
        handler.postDelayed(() -> {
            if (serverRepository == null) return;
            if (!isNetworkAvailable()) return;
            serverRepository.insertMessage(message, null, st, pcode, sender, simNumber,
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
                        }
                    });
        }, 1500);
    }
    //endregion InsertNewPopUpMessage

    //region RecursiveUSSDDial
    private void packageLoadSent(String dialCodePre, String dialCodePost, String flexiId, String package_name, String phone, String amount, String type, int simSlotId, String simPin, String service) {
        String phonecodedial = "";
        if (simSlotId == sim1Id) {
            sim_number = session.getData(Session.SIM1_NUMBER);
        }

        if (simSlotId == sim2Id) {
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
                onRequestCompleted(simSlotId);
            }
        });
    }

    private void ussdSendForKey(String msgBody, String ussdMsg, String pinCode, int SimId, USSDApi ussdApis, String rechargeId, int simSlotId) {

        String sk = Constant.GetDialNumber(msgBody, ussdMsg);
        if (simSlotId == sim1Id) {
            sim_number = sim1Num;
        }

        if (simSlotId == sim2Id) {
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
                restartPollingTimers();
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
        stopPollingTimers();
        super.finish();
    }

    /**
     * Keeps the screen on and prevents the phone from locking while MainActivity is visible.
     * Uses the modern KeyguardManager API on Android O+ and the legacy window flag on older versions.
     */
    private void keepScreenOnAndDismissKeyguard() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true);
            setTurnScreenOn(true);
            android.app.KeyguardManager km =
                    (android.app.KeyguardManager) getSystemService(KEYGUARD_SERVICE);
            if (km != null) {
                km.requestDismissKeyguard(this, null);
            }
        } else {
            getWindow().addFlags(
                    android.view.WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED
                            | android.view.WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD
                            | android.view.WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        // Clear WeakReference so GC can reclaim this Activity immediately
        insRef = null;
        // Remove all pending handler callbacks to prevent leaks and use-after-destroy crashes
        handler.removeCallbacksAndMessages(null);
        countdownHandler.removeCallbacksAndMessages(null);
        subscriptionCheckHandler.removeCallbacksAndMessages(null);
        stopPollingTimers();
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

    private String resolveRequestSender(String senderNum) {
        String normalized = senderNum == null ? "" : senderNum.trim();
        if (normalized.isEmpty()
                || "FlashMessage".equalsIgnoreCase(normalized)
                || "ValidationError".equalsIgnoreCase(normalized)
                || "bKashLoad".equalsIgnoreCase(normalized)
                || "NagadLoad".equalsIgnoreCase(normalized)) {
            return currentRequestSender == null ? "" : currentRequestSender.trim();
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
        String sender;
        boolean isPowerLoad;
        int simSlotId;
        String simPin;
        String service;

        RequestData(String sid, String pcode, String phone, String amount, String type,
                    String package_name, String sender, boolean isPowerLoad, int simSlotId, String simPin, String service) {
            this.sid = sid;
            this.pcode = pcode;
            this.phone = phone;
            this.amount = amount;
            this.type = type;
            this.package_name = package_name;
            this.sender = sender;
            this.isPowerLoad = isPowerLoad;
            this.simSlotId = simSlotId;
            this.simPin = simPin;
            this.service = service;
        }
    }
}
