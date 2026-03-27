package com.dRecharge.modem.ussd;

import android.Manifest;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.telecom.PhoneAccountHandle;
import android.telecom.TelecomManager;
import android.text.TextUtils;
import android.view.accessibility.AccessibilityManager;
import android.widget.Toast;

import androidx.core.content.ContextCompat;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;

public class USSDController implements USSDInterface, USSDApi {

    protected static USSDController instance;

    protected Context context;

    protected HashMap<String, HashSet<String>> map;

    protected CallbackInvoke callbackInvoke;

    protected CallbackMessage callbackMessage;

    protected static final String KEY_LOGIN = "KEY_LOGIN";

    protected static final String KEY_ERROR = "KEY_ERROR";

    protected Boolean isRunning = false;
    private static final long USSD_TIMEOUT_MS = 25000;
    private static final long USSD_STEP_DELAY_MS = 900;  // দ্রুত USSD - number/amount step (আগে 1500)
    private static final long USSD_PIN_DELAY_MS = 1400;  // দ্রুত PIN - dialog ready হওয়ার অপেক্ষা (আগে 2500)
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Object sessionLock = new Object();
    private int ussdSessionId = 0;

    private final USSDInterface ussdInterface;

    /**
     * auth eng! b@ppe
     * The Singleton building method
     *
     * @param context An activity that could call
     * @return An instance of USSDController
     */
    public static USSDController getInstance(Context context) {
        if (instance == null)
            instance = new USSDController(context);
        return instance;
    }

    private USSDController(Context context) {
        ussdInterface = this;
        this.context = context;
    }

    /**
     * Invoke a dial-up calling a ussd number
     *
     * @param ussdPhoneNumber ussd number
     * @param map             Map of Login and problem messages
     * @param callbackInvoke  a callback object from return answer
     */
    public void callUSSDInvoke(String ussdPhoneNumber, HashMap<String, HashSet<String>> map, CallbackInvoke callbackInvoke) {
        callUSSDInvoke(ussdPhoneNumber, 0, map, callbackInvoke);
    }

    /**
     * Invoke a dial-up calling a ussd number and
     * you had a overlay progress widget
     *
     * @param ussdPhoneNumber ussd number
     * @param map             Map of Login and problem messages
     * @param callbackInvoke  a callback object from return answer
     */
    public void callUSSDOverlayInvoke(String ussdPhoneNumber, HashMap<String, HashSet<String>> map, CallbackInvoke callbackInvoke) {
        callUSSDOverlayInvoke(ussdPhoneNumber, 0, map, callbackInvoke);
    }

    /**
     * Invoke a dial-up calling a ussd number
     *
     * @param ussdPhoneNumber ussd number
     * @param simSlot         simSlot number to use
     * @param map             Map of Login and problem messages
     * @param callbackInvoke  a callback object from return answer
     */
    public void callUSSDInvoke(String ussdPhoneNumber, int simSlot, HashMap<String, HashSet<String>> map, CallbackInvoke callbackInvoke) {
        prepareSession(map, callbackInvoke);
        if (verifyAccesibilityAccess(context)) {
            dialUp(ussdPhoneNumber, simSlot);
        } else {
            CallbackInvoke invokeCallback = consumeCallbackInvoke();
            if (invokeCallback != null) {
                invokeCallback.over("Check your accessibility");
            }
        }
    }

    /**
     * Invoke a dial-up calling a ussd number and
     * you had a overlay progress widget
     *
     * @param ussdPhoneNumber ussd number
     * @param simSlot         simSlot number to use
     * @param map             Map of Login and problem messages
     * @param callbackInvoke  a callback object from return answer
     */
    public void callUSSDOverlayInvoke(String ussdPhoneNumber, int simSlot, HashMap<String, HashSet<String>> map, CallbackInvoke callbackInvoke) {
        prepareSession(map, callbackInvoke);
        if (verifyAccesibilityAccess(context)) {
            dialUp(ussdPhoneNumber, simSlot);
        } else {
            CallbackInvoke invokeCallback = consumeCallbackInvoke();
            if (invokeCallback != null) {
                invokeCallback.over("Check your accessibility permission");
            }
        }
    }

    private void dialUp(String ussdPhoneNumber, int simSlot) {
        if (map == null || (!map.containsKey(KEY_ERROR) || !map.containsKey(KEY_LOGIN))) {
            CallbackInvoke invokeCallback = consumeCallbackInvoke();
            if (invokeCallback != null) {
                invokeCallback.over("Bad Mapping structure");
            }
            return;
        }
        if (ussdPhoneNumber.isEmpty()) {
            CallbackInvoke invokeCallback = consumeCallbackInvoke();
            if (invokeCallback != null) {
                invokeCallback.over("Bad ussd number");
            }
            return;
        }
        // Encode # for tel: URI - required for USSD codes on all Android versions
        String encodedUssd = ussdPhoneNumber.replace("#", Uri.encode("#"));
        Uri uriPhone = Uri.parse("tel:" + encodedUssd);
        if (uriPhone != null) {
            synchronized (sessionLock) {
                isRunning = true;
            }
        }
        boolean hasCallPermission = hasCallPermission();
        // Android 14+ (API 34): ACTION_CALL no longer auto-dials USSD/MMI - only default dialer can.
        // Use ACTION_DIAL so dialer opens with number pre-filled; user taps dial (Accessibility will handle).
        boolean useCallAction = hasCallPermission && Build.VERSION.SDK_INT < 34;
        Intent intent = getActionCallIntent(uriPhone, simSlot, useCallAction);
        try {
            this.context.startActivity(intent);
            if (!hasCallPermission && this.callbackInvoke != null) {
                this.callbackInvoke.over("CALL_PHONE permission required to auto-dial. Opened dialer.");
            } else if (Build.VERSION.SDK_INT >= 34 && this.callbackInvoke != null) {
                Toast.makeText(context, "Tap the dial button to continue", Toast.LENGTH_SHORT).show();
            }
            scheduleTimeout();
        } catch (SecurityException | ActivityNotFoundException e) {
            Intent fallbackIntent = getActionCallIntent(uriPhone, simSlot, false);
            this.context.startActivity(fallbackIntent);
            if (this.callbackInvoke != null) {
                this.callbackInvoke.over("Unable to auto-dial. Opened dialer instead.");
            }
            scheduleTimeout();
        }
    }

    /**
     * get action call Intent
     *
     * @param uri     parsed uri to call
     * @param simSlot simSlot number to use
     */
    private Intent getActionCallIntent(Uri uri, int simSlot, boolean useCallAction) {

        final String[] simSlotName = {
                "extra_asus_dial_use_dualsim",
                "com.android.phone.extra.slot",
                "slot",
                "simslot",
                "sim_slot",
                "subscription",
                "Subscription",
                "phone",
                "com.android.phone.DialingMode",
                "simSlot",
                "slot_id",
                "simId",
                "simnum",
                "phone_type",
                "slotId",
                "android.telephony.extra.SLOT_INDEX",
                "android.telephony.extra.SUBSCRIPTION_INDEX",
                "slotIdx"
        };

        Intent intent = new Intent(useCallAction ? Intent.ACTION_CALL : Intent.ACTION_DIAL, uri);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        intent.putExtra("com.android.phone.force.slot", true);
        intent.putExtra("Cdma_Supp", true);

        for (String s : simSlotName)
            intent.putExtra(s, simSlot);

        try {
            TelecomManager telecomManager = (TelecomManager) context.getSystemService(Context.TELECOM_SERVICE);
            if (telecomManager != null) {
                List<PhoneAccountHandle> phoneAccountHandleList = telecomManager.getCallCapablePhoneAccounts();
                if (phoneAccountHandleList != null && phoneAccountHandleList.size() > simSlot) {
                    intent.putExtra("android.telecom.extra.PHONE_ACCOUNT_HANDLE", phoneAccountHandleList.get(simSlot));
                }
            }
        } catch (SecurityException e) {
            // Ignore and let the call proceed without phone account selection.
        }

        return intent;
    }

    private boolean hasCallPermission() {
        return ContextCompat.checkSelfPermission(context, Manifest.permission.CALL_PHONE)
                == PackageManager.PERMISSION_GRANTED;
    }

    private void scheduleTimeout() {
        final int sessionId;
        synchronized (sessionLock) {
            sessionId = ussdSessionId;
        }
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                CallbackInvoke invokeCallback = null;
                synchronized (sessionLock) {
                    if (Boolean.TRUE.equals(isRunning) && ussdSessionId == sessionId) {
                        isRunning = false;
                        callbackMessage = null;
                        invokeCallback = callbackInvoke;
                        callbackInvoke = null;
                    }
                }
                if (invokeCallback != null) {
                    invokeCallback.over("USSD timeout. Please check dialer/permissions.");
                }
            }
        }, USSD_TIMEOUT_MS);
    }

    public void sendData(String text) {
        USSDService.send(text);
    }

    public void send(String text, CallbackMessage callbackMessage) {
        send(text, callbackMessage, USSD_STEP_DELAY_MS);
    }

    /** PIN পাঠানোর জন্য - dialog সম্পূর্ণ load হওয়ার জন্য বেশি delay */
    public void sendPin(String text, CallbackMessage callbackMessage) {
        send(text, callbackMessage, USSD_PIN_DELAY_MS);
    }

    public void send(String text, CallbackMessage callbackMessage, long delayMs) {
        final int sessionId;
        synchronized (sessionLock) {
            sessionId = ussdSessionId;
        }
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                synchronized (sessionLock) {
                    if (!Boolean.TRUE.equals(isRunning) || ussdSessionId != sessionId) {
                        return;
                    }
                    USSDController.this.callbackMessage = callbackMessage;
                }
                ussdInterface.sendData(text);
            }
        }, delayMs);
    }

    @Override
    public void cancel() {
        synchronized (sessionLock) {
            handler.removeCallbacksAndMessages(null);
            ussdSessionId++;
            isRunning = false;
            callbackInvoke = null;
            callbackMessage = null;
        }
        USSDService.cancel();
    }

    private void prepareSession(HashMap<String, HashSet<String>> map, CallbackInvoke callbackInvoke) {
        synchronized (sessionLock) {
            handler.removeCallbacksAndMessages(null);
            ussdSessionId++;
            this.map = map;
            this.callbackInvoke = callbackInvoke;
            this.callbackMessage = null;
            this.isRunning = false;
        }
    }

    protected CallbackInvoke consumeCallbackInvoke() {
        synchronized (sessionLock) {
            CallbackInvoke currentCallback = callbackInvoke;
            callbackInvoke = null;
            return currentCallback;
        }
    }

    protected CallbackMessage consumeCallbackMessage() {
        synchronized (sessionLock) {
            CallbackMessage currentCallback = callbackMessage;
            callbackMessage = null;
            return currentCallback;
        }
    }

    protected void stopRunning() {
        synchronized (sessionLock) {
            handler.removeCallbacksAndMessages(null);
            isRunning = false;
        }
    }

    public static boolean verifyAccesibilityAccess(Context context) {
        boolean isEnabled = USSDController.isAccessiblityServicesEnable(context);
        if (!isEnabled) {
            if (context instanceof Activity) {
                openSettingsAccessibility((Activity) context);
            } else {
                Toast.makeText(
                        context,
                        "Accessibility service is not enabled",
                        Toast.LENGTH_LONG
                ).show();
            }
        }
        return isEnabled;
    }


    private static void openSettingsAccessibility(final Activity activity) {
        AlertDialog.Builder alertDialogBuilder = new AlertDialog.Builder(activity);
        alertDialogBuilder.setTitle("USSD Accessibility permission");
        ApplicationInfo applicationInfo = activity.getApplicationInfo();
        int stringId = applicationInfo.labelRes;
        String name = applicationInfo.labelRes == 0 ?
                applicationInfo.nonLocalizedLabel.toString() : activity.getString(stringId);
        alertDialogBuilder
                .setMessage("You must enable accessibility permissions for the app '" + name + "'");
        alertDialogBuilder.setCancelable(true);
        alertDialogBuilder.setNeutralButton("YES", new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int id) {
                isAccessiblityServicesEnable(activity);
                //activity.startActivityForResult(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS), 1);
            }
        });
        AlertDialog alertDialog = alertDialogBuilder.create();
        if (alertDialog != null) {
            alertDialog.show();
        }
    }


    protected static boolean isAccessiblityServicesEnable(Context context) {
        AccessibilityManager am = (AccessibilityManager) context
                .getSystemService(Context.ACCESSIBILITY_SERVICE);

        if (am != null) {
            for (AccessibilityServiceInfo service : am.getInstalledAccessibilityServiceList()) {
                if (service.getId().contains(context.getPackageName())) {
                    return USSDController.isAccessibilitySettingsOn(context, service.getId());
                }
            }
        }
        return false;
    }

    protected static boolean isAccessibilitySettingsOn(Context context, final String service) {
        int accessibilityEnabled = 0;
        try {
            accessibilityEnabled = Settings.Secure.getInt(
                    context.getApplicationContext().getContentResolver(),
                    Settings.Secure.ACCESSIBILITY_ENABLED);
        } catch (Settings.SettingNotFoundException e) {
            //
        }
        if (accessibilityEnabled == 1) {
            String settingValue = Settings.Secure.getString(
                    context.getApplicationContext().getContentResolver(),
                    Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);

            if (settingValue != null) {
                TextUtils.SimpleStringSplitter splitter = new TextUtils.SimpleStringSplitter(':');
                splitter.setString(settingValue);

                while (splitter.hasNext()) {
                    String accessabilityService = splitter.next();
//                    return true;
                    if (!accessabilityService.equalsIgnoreCase(service)) {
                    }
                    return true;
                }
            }
        }
        return false;
    }

    public interface CallbackInvoke {
        void responseInvoke(String message);

        void over(String message);
    }

    public interface CallbackMessage {
        void responseMessage(String message);
    }

}
