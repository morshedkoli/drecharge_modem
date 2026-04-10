package com.dRecharge.modem.receiver;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkInfo;
import android.telephony.SmsMessage;

import com.dRecharge.modem.MainActivity;
import com.dRecharge.modem.apimodel.InsertMessageModel;
import com.dRecharge.modem.helper.Constant;
import com.dRecharge.modem.helper.Session;
import com.dRecharge.modem.server.ModemServerRepository;

import java.util.Set;

import static com.dRecharge.modem.helper.Constant.sim1Id;
import static com.dRecharge.modem.helper.Constant.sim2Id;
import static com.dRecharge.modem.helper.Session.SIM1_SERVICE_CODE;
import static com.dRecharge.modem.helper.Session.SIM2_SERVICE_CODE;

public class SMSBReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        final Bundle bundle = intent.getExtras();
        if (bundle == null) return;

        Session session = new Session(context);
        if (!session.isDomainValid() || session.getData(Session.API_DOMAIN_LINK).trim().isEmpty()) {
            return;
        }
        if (!isNetworkAvailable(context)) {
            return;
        }

        ModemServerRepository serverRepository = ModemServerRepository.fromSession(session);

        try {
            // C5: Guard against null pdusObj
            final Object[] pdusObj = (Object[]) bundle.get("pdus");
            if (pdusObj == null || pdusObj.length == 0) return;

            String senderNum = "";
            String message;
            StringBuilder messageBuilder = new StringBuilder();
            int slot = -1;

            for (int i = 0; i < pdusObj.length; i++) {
                // C6: Guard against null SmsMessage
                SmsMessage currentMessage;
                Object currentPdu = pdusObj[i];
                if (!(currentPdu instanceof byte[])) {
                    continue;
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    String format = bundle.getString("format");
                    currentMessage = SmsMessage.createFromPdu((byte[]) currentPdu, format);
                } else {
                    currentMessage = SmsMessage.createFromPdu((byte[]) currentPdu);
                }
                if (currentMessage == null) continue;

                senderNum = currentMessage.getDisplayOriginatingAddress();
                if (senderNum == null) senderNum = "";

                // C6: Guard against null message body
                String body = currentMessage.getDisplayMessageBody();
                if (body == null) body = "";
                messageBuilder.append(body.replaceAll(System.lineSeparator(), " "));

                // Extract SIM slot from known bundle extras
                try {
                    Set<String> keySet = bundle.keySet();
                    for (String key : keySet) {
                        switch (key) {
                            case "slot":
                                slot = bundle.getInt("slot", -1); break;
                            case "simId":
                                slot = bundle.getInt("simId", -1); break;
                            case "simSlot":
                                slot = bundle.getInt("simSlot", -1); break;
                            case "slot_id":
                                slot = bundle.getInt("slot_id", -1); break;
                            case "simnum":
                                slot = bundle.getInt("simnum", -1); break;
                            case "slotId":
                                slot = bundle.getInt("slotId", -1); break;
                            case "slotIdx":
                                slot = bundle.getInt("slotIdx", -1); break;
                            case "android.telephony.extra.SLOT_INDEX":
                                slot = bundle.getInt("android.telephony.extra.SLOT_INDEX", -1); break;
                            case "phone":
                                slot = bundle.getInt("phone", -1); break;
                            default:
                                if (key.toLowerCase().contains("slot") || key.toLowerCase().contains("sim")) {
                                    String value = bundle.getString(key, "-1");
                                    if ("0".equals(value) || "1".equals(value) || "2".equals(value)) {
                                        slot = bundle.getInt(key, -1);
                                    }
                                }
                        }
                    }
                } catch (Exception ignored) {
                }
            }

            String sim_number = "";
            String op_code = "";
            String op = "";

            if (slot == sim1Id) {
                sim_number = session.getData(Session.SIM1_NUMBER);
                op_code = session.getData(SIM1_SERVICE_CODE);
                op = session.getData(Session.SIM1_SERVICE_NAME);
            } else if (slot == sim2Id) {
                sim_number = session.getData(Session.SIM2_NUMBER);
                op_code = session.getData(SIM2_SERVICE_CODE);
                op = session.getData(Session.SIM2_SERVICE_NAME);
            }

            message = messageBuilder.toString().replaceAll("\\s+", " ");

            // Update balance display if the SMS contains a balance string
            if (!message.contains("VAS")) {
                String balance = Constant.getSimBalance(message);
                if (!balance.isEmpty()) {
                    final int finalSlot = slot;
                    final String finalBalance = balance;
                    MainActivity mainActivity = MainActivity.getMainActivityInstance();
                    if (mainActivity != null && !mainActivity.isFinishing()
                            && !mainActivity.isDestroyed()) {
                        mainActivity.updateSimBalanceTv(finalBalance, finalSlot);
                    }
                }
            }

            final String finalSenderNum = senderNum;
            final String finalMessage   = message;
            final String finalOpCode    = op_code;
            final String finalOp        = op;
            final String finalSimNumber = sim_number;
            final int    finalSlot      = slot;

            serverRepository.insertMessage(
                    finalMessage,
                    finalOpCode,
                    "",
                    finalOpCode,
                    finalSenderNum,
                    finalSimNumber,
                    String.valueOf(finalSlot),
                    finalOp,
                    new ModemServerRepository.MessageInsertCallback() {
                        @Override
                        public void onSuccess(InsertMessageModel insertMessageModel) {
                            if (!insertMessageModel.hasStatus("1")) return;
                            MainActivity mainActivity = MainActivity.getMainActivityInstance();
                            if (mainActivity == null || mainActivity.isFinishing()
                                    || mainActivity.isDestroyed()) return;
                            try {
                                String simam = insertMessageModel.getSimam();
                                if (simam != null && !simam.isEmpty()) {
                                    mainActivity.updateSimBalanceTv(simam, finalSlot);
                                }
                                mainActivity.updateResultTv(finalSlot, insertMessageModel.getMsg());
                            } catch (Exception ignored) {
                            }
                        }

                        @Override
                        public void onFailure(Throwable throwable) {
                            // Silently ignore network failures in background SMS processing
                        }
                    });

        } catch (Exception ignored) {
        }
    }

    private boolean isNetworkAvailable(Context context) {
        ConnectivityManager connectivityManager =
                (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
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
}
