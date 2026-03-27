package com.dRecharge.modem.receiver;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.telephony.SmsMessage;
import android.util.Log;

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
    private static final String SMS_RECEIVED = "android.provider.Telephony.SMS_RECEIVED";
    private static final String TAG = "=======SMSBroadcastReceiver";
    private int slot;
    private MainActivity mainActivity;
    private String sim_number, op_code, op;
    private ModemServerRepository serverRepository;
    String message;
    String senderNum;
    private Session session;

    @Override
    public void onReceive(Context context, Intent intent) {
        final Bundle bundle = intent.getExtras();
        mainActivity = MainActivity.getMainActivityInstance();
        session = new Session(context);
        if (!session.isDomainValid() || session.getData(Session.API_DOMAIN_LINK).trim().isEmpty()) {
            return;
        }
        serverRepository = ModemServerRepository.fromSession(session);
        try {

            if (bundle != null) {

                final Object[] pdusObj = (Object[]) bundle.get("pdus");
                StringBuilder messageBuilder = new StringBuilder();

                for (int i = 0; i < pdusObj.length; i++) {

                    SmsMessage currentMessage = SmsMessage.createFromPdu((byte[]) pdusObj[i]);
                    senderNum = currentMessage.getDisplayOriginatingAddress();
                    message = currentMessage.getDisplayMessageBody().replaceAll(System.lineSeparator(), " ");
                    messageBuilder.append(message);
                    try {
                        slot = -1;
                        if (bundle != null) {
                            Set<String> keySet = bundle.keySet();
                            for (String key : keySet) {
                                switch (key) {
                                    case "slot":
                                        slot = bundle.getInt("slot", -1);
                                        break;
                                    case "simId":
                                        slot = bundle.getInt("simId", -1);
                                        break;
                                    case "simSlot":
                                        slot = bundle.getInt("simSlot", -1);
                                        break;
                                    case "slot_id":
                                        slot = bundle.getInt("slot_id", -1);
                                        break;
                                    case "simnum":
                                        slot = bundle.getInt("simnum", -1);
                                        break;
                                    case "slotId":
                                        slot = bundle.getInt("slotId", -1);
                                        break;
                                    case "slotIdx":
                                        slot = bundle.getInt("slotIdx", -1);
                                        break;
                                    case "android.telephony.extra.SLOT_INDEX":
                                        slot = bundle.getInt("android.telephony.extra.SLOT_INDEX", -1);
                                        break;
                                    case "phone":
                                        slot = bundle.getInt("phone", -1);
                                        break;
                                    default:
                                        if (key.toLowerCase().contains("slot") | key.toLowerCase().contains("sim")) {
                                            String value = bundle.getString(key, "-1");
                                            if (value.equals("0") | value.equals("1") | value.equals("2")) {
                                                slot = bundle.getInt(key, -1);
                                            }
                                        }
                                }
                                //System.out.println("=====KEY:: " + keySet.toString());
                            }
                        }
                    } catch (Exception e) {
                        Log.d(TAG, "Exception=>" + e);
                    }
                }

                if (slot == sim1Id) {
                    sim_number = session.getData(Session.SIM1_NUMBER);
                    op_code = session.getData(SIM1_SERVICE_CODE);
                    op = session.getData(Session.SIM1_SERVICE_NAME);
                }

                if (slot == sim2Id) {
                    sim_number = session.getData(Session.SIM2_NUMBER);
                    op_code = session.getData(SIM2_SERVICE_CODE);
                    op = session.getData(Session.SIM2_SERVICE_NAME);
                }
                message = messageBuilder.toString().replaceAll("\\s+", " ");

                if (!message.contains("VAS")) {
                    if (!Constant.getSimBalance(message).isEmpty()) {
                        if (mainActivity != null) {
                            mainActivity.updateSimBalanceTv(Constant.getSimBalance(message.replaceAll(System.lineSeparator(), " ")), slot);
                        }
                    }
                }

                System.out.println("======MSG_BODY: " + message + " ====SLOT:: " + slot + " ====OP:: " + op_code + " ======SIM_NUM:" + sim_number + " =====SENDER: " + senderNum);
                serverRepository.insertMessage(
                        message,
                        op_code,
                        "",
                        op_code,
                        senderNum,
                        sim_number,
                        String.valueOf(slot),
                        op,
                        new ModemServerRepository.MessageInsertCallback() {
                            @Override
                            public void onSuccess(InsertMessageModel insertMessageModel) {
                                if (!insertMessageModel.hasStatus("1") || mainActivity == null) {
                                    return;
                                }

                                try {
                                    if (!insertMessageModel.getSimam().equals("")) {
                                        mainActivity.updateSimBalanceTv(insertMessageModel.getSimam(), slot);
                                    }
                                    mainActivity.updateResultTv(slot, insertMessageModel.getMsg());
                                } catch (Exception ignored) {
                                }
                            }

                            @Override
                            public void onFailure(Throwable throwable) {
                                System.out.println("======ERROR_MESG : " + throwable.getMessage());
                                System.out.println("======ERROR_RAW: " + throwable);
                            }
                        });
            }

        } catch (Exception e) {
            //Log.e("======SmsReceiver", "Exception smsReceiver:: " + e);
        }
    }

}
