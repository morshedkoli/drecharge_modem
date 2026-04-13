package com.dRecharge.modem.service;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkInfo;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.PowerManager;
import androidx.core.content.ContextCompat;
import android.content.pm.PackageManager;

import androidx.core.app.NotificationCompat;

import com.dRecharge.modem.MainActivity;
import com.dRecharge.modem.R;
import com.dRecharge.modem.apimodel.InsertMessageModel;
import com.dRecharge.modem.helper.Constant;
import com.dRecharge.modem.helper.ServiceCatalog;
import com.dRecharge.modem.helper.ServiceConfig;
import com.dRecharge.modem.helper.Session;
import com.dRecharge.modem.helper.UssdDialTemplateResolver;
import com.dRecharge.modem.server.ModemServerRepository;
import com.dRecharge.modem.ussd.USSDApi;
import com.dRecharge.modem.ussd.USSDController;

import java.lang.ref.WeakReference;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;

public class KeepAliveService extends Service {

    private static final String CHANNEL_ID = "drecharge_keepalive";
    static final int NOTIFICATION_ID = 1001;

    /** Keeps the CPU awake so background USSD polling works even when the screen is off. */
    private PowerManager.WakeLock wakeLock;

    /** Full-colour logo bitmap — shown as the large icon in the notification card. */
    private static WeakReference<Bitmap> logoColorRef = new WeakReference<>(null);
    /** White/alpha-only logo bitmap — used as the small status-bar icon. */
    private static WeakReference<Bitmap> logoWhiteRef = new WeakReference<>(null);

    // =========================================================================
    // Background Polling Engine
    // Polls the server and processes USSD requests when MainActivity is not alive.
    // =========================================================================
    private final Handler bgHandler = new Handler(Looper.getMainLooper());
    private Runnable bgPollRunnable;

    private Session bgSession;
    private ModemServerRepository bgRepo;
    private USSDApi bgUssdApi;
    private HashMap<String, HashSet<String>> ussdMap;

    // Per-SIM background request queues
    private final Queue<BgRequest> bgQueueSim1 = new LinkedList<>();
    private final Queue<BgRequest> bgQueueSim2 = new LinkedList<>();
    private boolean bgProcessingSim1 = false;
    private boolean bgProcessingSim2 = false;
    private boolean bgAnyProcessing = false;

    private String bgCurrentPcode = "";
    private String bgCurrentSender = "";

    // SIM slot IDs — read from Session (set by MainActivity when it runs).
    private int bgSim1Id = 0;
    private int bgSim2Id = 1;

    private Runnable bgTimeoutSim1 = null;
    private Runnable bgTimeoutSim2 = null;
    private static final long BG_REQUEST_TIMEOUT_MS = 90_000L;   // 90 s safety timeout per request
    private static final long BG_PROCESSING_DELAY_MS = 30_000L;  // 30 s between consecutive requests
    private static final long BG_INITIAL_DELAY_MS = 15_000L;     // 15 s startup settle time

    // =========================================================================
    // Value object for queued background requests
    // =========================================================================
    private static final class BgRequest {
        final String sid, pcode, phone, amount, type, packageName, sender, simPin, service;
        final int simSlotId;
        final boolean powerLoad;

        BgRequest(String sid, String pcode, String phone, String amount, String type,
                  String packageName, String sender, boolean powerLoad,
                  int simSlotId, String simPin, String service) {
            this.sid = safe(sid);
            this.pcode = safe(pcode);
            this.phone = safe(phone);
            this.amount = safe(amount);
            this.type = safe(type);
            this.packageName = safe(packageName);
            this.sender = safe(sender);
            this.powerLoad = powerLoad;
            this.simSlotId = simSlotId;
            this.simPin = safe(simPin);
            this.service = safe(service);
        }

        private static String safe(String v) {
            return v == null ? "" : v;
        }
    }

    // =========================================================================
    // Called from MainActivity after the logo has been downloaded.
    // =========================================================================
    public static void setLogoBitmaps(Bitmap color, Bitmap white) {
        logoColorRef = new WeakReference<>(color);
        logoWhiteRef = new WeakReference<>(white);
    }

    /** Re-posts the notification with the latest logo bitmaps. */
    public static void updateNotification(Context ctx) {
        NotificationManager nm = (NotificationManager) ctx.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm != null) {
            PendingIntent openApp = PendingIntent.getActivity(
                    ctx, 0,
                    new Intent(ctx, MainActivity.class)
                            .setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

            NotificationCompat.Builder builder = new NotificationCompat.Builder(ctx, CHANNEL_ID)
                    .setContentTitle("dRecharge Running")
                    .setContentText("USSD service is active")
                    .setContentIntent(openApp)
                    .setOngoing(true)
                    .setPriority(NotificationCompat.PRIORITY_MIN)
                    .setSilent(true);

            builder.setSmallIcon(R.drawable.ic_notification_drecharge);

            Bitmap largeLogo = getCachedBitmap(logoColorRef);
            if (largeLogo == null) largeLogo = getDefaultColorLogo(ctx);
            if (largeLogo != null) builder.setLargeIcon(largeLogo);

            nm.notify(NOTIFICATION_ID, builder.build());
        }
    }

    // =========================================================================
    // Service lifecycle
    // =========================================================================

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        startForeground(NOTIFICATION_ID, buildNotification());
        acquireWakeLock();
        startBgPolling();
        return START_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        stopBgPolling();
        releaseWakeLock();
    }

    @Override
    public void onTaskRemoved(Intent rootIntent) {
        super.onTaskRemoved(rootIntent);
        Intent restart = new Intent(getApplicationContext(), KeepAliveService.class);
        restart.setPackage(getPackageName());
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(restart);
        } else {
            startService(restart);
        }
    }

    // =========================================================================
    // Background Polling: start / stop
    // =========================================================================

    private void startBgPolling() {
        stopBgPolling();
        bgPollRunnable = new Runnable() {
            @Override
            public void run() {
                try {
                    doBgPoll();
                } catch (Exception ignored) {
                }
                long interval = getBgPollIntervalMs();
                bgHandler.postDelayed(this, interval);
            }
        };
        bgHandler.postDelayed(bgPollRunnable, BG_INITIAL_DELAY_MS);
    }

    private void stopBgPolling() {
        if (bgPollRunnable != null) {
            bgHandler.removeCallbacks(bgPollRunnable);
            bgPollRunnable = null;
        }
        bgCancelTimeout(bgSim1Id);
        bgCancelTimeout(bgSim2Id);
    }

    private long getBgPollIntervalMs() {
        try {
            Session s = getBgSession();
            if (s == null) return 60_000L;
            String val = s.getData(Session.TIME_INTERVAL);
            if (!val.isEmpty()) {
                int secs = Integer.parseInt(val);
                if (secs >= 10) return secs * 1000L;
            }
        } catch (Exception ignored) {
        }
        return 60_000L;
    }

    // =========================================================================
    // Background Polling: poll tick
    // Only runs when MainActivity is NOT alive (activity handles it when visible).
    // =========================================================================

    private void doBgPoll() {
        // If the activity is alive it owns the polling timers — skip to avoid duplicates.
        if (MainActivity.getMainActivityInstance() != null) return;

        Session s = getBgSession();
        if (s == null || !s.isDomainValid()) return;
        if (!isNetworkAvailable()) return;

        // Refresh repo in case domain setting changed.
        refreshBgRepo(s);

        // Read persisted SIM slot IDs.
        try {
            String id1 = s.getData(Session.SIM1_ID);
            if (!id1.isEmpty()) bgSim1Id = Integer.parseInt(id1);
            String id2 = s.getData(Session.SIM2_ID);
            if (!id2.isEmpty()) bgSim2Id = Integer.parseInt(id2);
        } catch (Exception ignored) {
        }

        boolean sim1On = s.getBooleanData(Session.SIM1_ENABLED);
        boolean sim2On = s.getBooleanData(Session.SIM2_ENABLED);

        if (sim1On) bgFetchForSim(1, bgSim1Id, s);
        if (sim2On) bgFetchForSim(2, bgSim2Id, s);
    }

    // =========================================================================
    // Background Polling: fetch pending for a SIM
    // =========================================================================

    private void bgFetchForSim(int simSlot, int simId, Session s) {
        List<ServiceConfig> cfgs = s.getActiveServicesForSim(simSlot);
        String balStr = simSlot == 1 ? Constant.getSim1Bal : Constant.getSim2Bal;

        if (cfgs.isEmpty()) {
            // Fallback to legacy single-service config stored directly in session.
            String svcName = s.getData(simSlot == 1 ? Session.SIM1_SERVICE_NAME : Session.SIM2_SERVICE_NAME);
            String svcCode = s.getData(simSlot == 1 ? Session.SIM1_SERVICE_CODE : Session.SIM2_SERVICE_CODE);
            String num = s.getData(simSlot == 1 ? Session.SIM1_NUMBER : Session.SIM2_NUMBER);
            String pin = s.getData(simSlot == 1 ? Session.SIM1_PIN : Session.SIM2_PIN);
            if (!svcName.isEmpty() || !svcCode.isEmpty()) {
                bgFetch(svcName, svcCode, num, balStr, simId, pin, svcName);
            }
        } else {
            for (ServiceConfig cfg : cfgs) {
                String company = ServiceCatalog.getCodeForService(cfg.name);
                // Guard: getCodeForService() returns null for unknown services — skip rather than
                // passing null to the API which would cause a NullPointerException downstream.
                if (company == null) company = "";
                bgFetch(cfg.name,
                        company,
                        cfg.number.isEmpty() ? s.getData(simSlot == 1 ? Session.SIM1_NUMBER : Session.SIM2_NUMBER) : cfg.number,
                        balStr, simId, cfg.pin, cfg.name);
            }
        }
    }

    private void bgFetch(String serviceName, String company, String simNumber,
                         String balance, int simId, String simPin, String serviceNameForQueue) {
        ModemServerRepository repo = getBgRepo();
        if (repo == null) return;

        repo.fetchPendingRequests(
                serviceName, company, simNumber, String.valueOf(simId), balance,
                new ModemServerRepository.PendingRequestsCallback() {
                    @Override
                    public void onPendingRequest(ServiceRequest request) {
                        BgRequest bgReq = new BgRequest(
                                request.getSid(), request.getPcode(), request.getPhone(),
                                request.getAmount(), request.getType(), request.getPackageName(),
                                request.getSender(), request.isPowerLoad(),
                                simId, simPin, serviceNameForQueue);

                        if (simId == bgSim1Id) bgQueueSim1.offer(bgReq);
                        else bgQueueSim2.offer(bgReq);

                        bgProcessNext(simId);
                    }

                    @Override
                    public void onServiceStopped() {
                    }

                    @Override
                    public void onFailure(Throwable t) {
                    }

                    @Override
                    public void onComplete() {
                    }
                });
    }

    // =========================================================================
    // Background Polling: request queue processing
    // =========================================================================

    private synchronized void bgProcessNext(int simId) {
        Queue<BgRequest> queue = (simId == bgSim1Id) ? bgQueueSim1 : bgQueueSim2;
        boolean isProcessing = (simId == bgSim1Id) ? bgProcessingSim1 : bgProcessingSim2;

        if (isProcessing || bgAnyProcessing || queue.isEmpty()) return;

        bgAnyProcessing = true;
        if (simId == bgSim1Id) bgProcessingSim1 = true;
        else bgProcessingSim2 = true;

        BgRequest req = queue.poll();
        if (req == null) {
            bgOnDone(simId);
            return;
        }

        bgScheduleTimeout(simId);
        bgProcessRequest(req);
    }

    private void bgProcessRequest(BgRequest req) {
        Session s = getBgSession();
        if (s == null) {
            bgOnDone(req.simSlotId);
            return;
        }

        String phone = normalizeBdPhone(req.phone);
        String amount = normalizeAmount(req.amount);
        String simNum = s.getData(req.simSlotId == bgSim1Id ? Session.SIM1_NUMBER : Session.SIM2_NUMBER);

        bgCurrentPcode = req.pcode;

        ServiceConfig svcCfg = s.getServiceConfig(req.service.isEmpty() ? "_unknown_" : req.service);
        bgCurrentSender = (svcCfg.number != null && !svcCfg.number.trim().isEmpty())
                ? svcCfg.number.trim()
                : req.sender;

        if (!looksLikeBdPhone(phone)) {
            bgInsertResult("Invalid phone: " + req.phone, req.sid, "ValidationError", simNum, req.simSlotId);
            bgOnDone(req.simSlotId);
            return;
        }
        if (!amount.matches("\\d+(\\.\\d+)?")) {
            bgInsertResult("Invalid amount: " + req.amount, req.sid, "ValidationError", simNum, req.simSlotId);
            bgOnDone(req.simSlotId);
            return;
        }
        if (!Session.isScheduledActiveNow(svcCfg)) {
            bgOnDone(req.simSlotId);
            return;
        }

        // Resolve USSD steps from custom template.
        List<String> steps = UssdDialTemplateResolver.resolveSteps(svcCfg, req.type, phone, amount, req.simPin);
        if (steps.isEmpty()) {
            // No custom USSD template configured — the built-in per-carrier logic lives in
            // MainActivity.  Release the lock so MainActivity can handle it when it opens.
            bgOnDone(req.simSlotId);
            return;
        }

        bgExecuteUssd(steps, req.sid, req.simSlotId, simNum);
    }

    // =========================================================================
    // Background Polling: USSD execution (mirrors MainActivity's executeCustomUssd)
    // =========================================================================

    private void bgExecuteUssd(List<String> steps, String sid, int simId, String simNum) {
        if (steps.size() == 1) {
            bgSingleStep(steps.get(0), sid, simId, simNum);
        } else {
            bgMultiStep(steps, sid, simId, simNum);
        }
    }

    private void bgSingleStep(String code, String sid, int simId, String simNum) {
        USSDApi api = getBgUssdApi();
        if (api == null) {
            bgOnDone(simId);
            return;
        }
        api.callUSSDInvoke(code, simId, buildUssdMap(), new USSDController.CallbackInvoke() {
            @Override
            public void responseInvoke(String message) {
                api.cancel();
                bgInsertResult(
                        message.replaceAll(System.lineSeparator(), " "),
                        sid, "FlashMessage", simNum, simId);
                bgOnDone(simId);
            }

            @Override
            public void over(String message) {
                bgOnDone(simId);
            }
        });
    }

    private void bgMultiStep(List<String> steps, String sid, int simId, String simNum) {
        USSDApi api = getBgUssdApi();
        if (api == null) {
            bgOnDone(simId);
            return;
        }
        List<String> inputSteps = steps.subList(1, steps.size());
        api.callUSSDInvoke(steps.get(0), simId, buildUssdMap(), new USSDController.CallbackInvoke() {
            @Override
            public void responseInvoke(String message) {
                bgSendStep(api, inputSteps, 0, sid, simId, simNum);
            }

            @Override
            public void over(String message) {
                bgOnDone(simId);
            }
        });
    }

    private void bgSendStep(USSDApi api, List<String> steps, int index,
                             String sid, int simId, String simNum) {
        if (index >= steps.size()) {
            api.cancel();
            bgOnDone(simId);
            return;
        }
        boolean isLast = (index == steps.size() - 1);
        api.send(steps.get(index), new USSDController.CallbackMessage() {
            @Override
            public void responseMessage(String message) {
                if (isLast) {
                    api.cancel();
                    bgInsertResult(
                            message.replaceAll(System.lineSeparator(), " "),
                            sid, "FlashMessage", simNum, simId);
                    bgOnDone(simId);
                } else {
                    bgSendStep(api, steps, index + 1, sid, simId, simNum);
                }
            }
        });
    }

    // =========================================================================
    // Background Polling: send result to server
    // =========================================================================

    private void bgInsertResult(String message, String sid, String senderNum,
                                 String simNum, int simSlot) {
        ModemServerRepository repo = getBgRepo();
        if (repo == null) return;
        String sender = resolveSender(senderNum);
        repo.insertMessage(message, null, sid, bgCurrentPcode, sender, simNum,
                String.valueOf(simSlot), null, new ModemServerRepository.MessageInsertCallback() {
                    @Override
                    public void onSuccess(InsertMessageModel response) {
                    }

                    @Override
                    public void onFailure(Throwable throwable) {
                    }
                });
    }

    private String resolveSender(String senderNum) {
        String n = senderNum == null ? "" : senderNum.trim();
        if (n.isEmpty()
                || "FlashMessage".equalsIgnoreCase(n)
                || "ValidationError".equalsIgnoreCase(n)) {
            return bgCurrentSender == null ? "" : bgCurrentSender.trim();
        }
        return n;
    }

    // =========================================================================
    // Background Polling: done / timeout helpers
    // =========================================================================

    private void bgOnDone(int simId) {
        bgCancelTimeout(simId);
        if (simId == bgSim1Id) bgProcessingSim1 = false;
        else bgProcessingSim2 = false;
        bgAnyProcessing = false;

        // Process next queued request for this SIM after the inter-request delay.
        bgHandler.postDelayed(() -> {
            if (simId == bgSim1Id && !bgQueueSim1.isEmpty()) bgProcessNext(bgSim1Id);
            else if (simId == bgSim2Id && !bgQueueSim2.isEmpty()) bgProcessNext(bgSim2Id);
        }, BG_PROCESSING_DELAY_MS);
    }

    private void bgScheduleTimeout(int simId) {
        bgCancelTimeout(simId);
        Runnable r = () -> bgOnDone(simId);
        if (simId == bgSim1Id) bgTimeoutSim1 = r;
        else bgTimeoutSim2 = r;
        bgHandler.postDelayed(r, BG_REQUEST_TIMEOUT_MS);
    }

    private void bgCancelTimeout(int simId) {
        if (simId == bgSim1Id && bgTimeoutSim1 != null) {
            bgHandler.removeCallbacks(bgTimeoutSim1);
            bgTimeoutSim1 = null;
        } else if (simId == bgSim2Id && bgTimeoutSim2 != null) {
            bgHandler.removeCallbacks(bgTimeoutSim2);
            bgTimeoutSim2 = null;
        }
    }

    // =========================================================================
    // Lazy-init helpers
    // =========================================================================

    private Session getBgSession() {
        if (bgSession == null) {
            try {
                bgSession = new Session(getApplicationContext());
            } catch (Exception e) {
                // EncryptedSharedPreferences can crash in a background service context
                // on some devices (e.g., corrupted keystore). Return null and let
                // callers handle it gracefully rather than crashing the foreground service.
                return null;
            }
        }
        return bgSession;
    }

    private void refreshBgRepo(Session s) {
        String domain = s.getData(Session.API_DOMAIN_LINK);
        if (!domain.isEmpty()) {
            bgRepo = ModemServerRepository.fromSession(s);
        }
    }

    private ModemServerRepository getBgRepo() {
        if (bgRepo == null) {
            Session s = getBgSession();
            if (s != null) {
                String domain = s.getData(Session.API_DOMAIN_LINK);
                if (!domain.isEmpty()) bgRepo = ModemServerRepository.fromSession(s);
            }
        }
        return bgRepo;
    }

    private USSDApi getBgUssdApi() {
        if (bgUssdApi == null) {
            bgUssdApi = USSDController.getInstance(getApplicationContext());
        }
        return bgUssdApi;
    }

    private HashMap<String, HashSet<String>> buildUssdMap() {
        if (ussdMap == null) {
            ussdMap = new HashMap<>();
            ussdMap.put("KEY_LOGIN",
                    new HashSet<>(Arrays.asList("running...", "waiting", "loading", "esperando")));
            ussdMap.put("KEY_ERROR",
                    new HashSet<>(Arrays.asList("problema", "problem", "error", "null")));
        }
        return ussdMap;
    }

    // =========================================================================
    // Phone / amount normalisation (mirrors MainActivity helpers)
    // =========================================================================

    private boolean looksLikeBdPhone(String value) {
        return value != null && normalizeBdPhone(value).matches("01\\d{9}");
    }

    private String normalizeBdPhone(String value) {
        if (value == null) return "";
        String v = value.trim();
        boolean hadPlus = v.startsWith("+");
        v = v.replaceAll("[^\\d+]", "");
        if (hadPlus && v.startsWith("+88")) v = v.substring(3);
        else if (v.startsWith("88") && v.length() > 10) v = v.substring(2);
        v = v.replaceAll("\\D", "");
        if (v.matches("1[3-9]\\d{8}")) v = "0" + v;
        return v;
    }

    private String normalizeAmount(String value) {
        if (value == null) return "";
        return value.trim()
                .replace("TK", "").replace("Tk", "").replace("tk", "")
                .replace("BDT", "").replace(",", "").trim();
    }

    // =========================================================================
    // Network check
    // =========================================================================

    private boolean isNetworkAvailable() {
        ConnectivityManager cm =
                (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        if (cm == null) return false;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Network net = cm.getActiveNetwork();
            if (net == null) return false;
            NetworkCapabilities caps = cm.getNetworkCapabilities(net);
            return caps != null && caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET);
        }
        NetworkInfo info = cm.getActiveNetworkInfo();
        return info != null && info.isConnected();
    }

    // =========================================================================
    // WakeLock helpers — keep CPU alive while service is running so background
    // USSD polling works even when the screen is off / phone is locked.
    // =========================================================================

    private void acquireWakeLock() {
        if (wakeLock != null && wakeLock.isHeld()) return;
        PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
        if (pm == null) return;
        wakeLock = pm.newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK,
                "dRecharge:KeepAliveWakeLock");
        wakeLock.setReferenceCounted(false);
        wakeLock.acquire();
    }

    private void releaseWakeLock() {
        if (wakeLock != null && wakeLock.isHeld()) {
            wakeLock.release();
        }
        wakeLock = null;
    }

    // =========================================================================
    // Notification helpers (unchanged from original)
    // =========================================================================

    private Notification buildNotification() {
        createChannel();

        PendingIntent openApp = PendingIntent.getActivity(
                this, 0,
                new Intent(this, MainActivity.class)
                        .setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("dRecharge Running")
                .setContentText("USSD service is active")
                .setContentIntent(openApp)
                .setOngoing(true)
                .setPriority(NotificationCompat.PRIORITY_MIN)
                .setSilent(true);

        builder.setSmallIcon(R.drawable.ic_notification_drecharge);

        Bitmap largeLogo = getCachedBitmap(logoColorRef);
        if (largeLogo == null) largeLogo = getDefaultColorLogo(this);
        if (largeLogo != null) builder.setLargeIcon(largeLogo);

        return builder.build();
    }

    private static Bitmap getCachedBitmap(WeakReference<Bitmap> bitmapReference) {
        return bitmapReference == null ? null : bitmapReference.get();
    }

    private static Bitmap getDefaultColorLogo(Context context) {
        return BitmapFactory.decodeResource(context.getResources(), R.drawable.app_logo_square);
    }

    private void createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "dRecharge Keep-Alive",
                    NotificationManager.IMPORTANCE_MIN);
            channel.setDescription("Keeps the USSD accessibility service active");
            channel.setShowBadge(false);
            channel.setSound(null, null);
            NotificationManager nm = getSystemService(NotificationManager.class);
            if (nm != null) nm.createNotificationChannel(channel);
        }
    }
}
