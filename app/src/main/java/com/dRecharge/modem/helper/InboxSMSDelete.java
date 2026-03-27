package com.dRecharge.modem.helper;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.util.Log;

public class InboxSMSDelete {

    public static boolean deleteSMS(Context mContext)
    {
        boolean isDeleted = false;
        try {
            mContext.getContentResolver().delete(Uri.parse("content://sms/"), null, null);
            isDeleted = true;
        } catch (Exception ex) {
            isDeleted = false;
        }
        return isDeleted;
    }

    public static void deleteSMS_(Context context,String message,String number)
    {
        try {
            Uri uriSms = Uri.parse("content://sms/inbox");
            Cursor c = context.getContentResolver().query(
                    uriSms,
                    new String[] { "_id", "thread_id", "address", "person",
                            "date", "body" }, "read=0", null, null);

            if (c != null && c.moveToFirst()) {
                do {
                    long id = c.getLong(0);
                    long threadId = c.getLong(1);
                    String address = c.getString(2);
                    String body = c.getString(5);
                    String date = c.getString(3);
                    Log.e("log>>>",
                            "0--->" + c.getString(0) + " | 1---->" + c.getString(1)
                                    + " | 2---->" + c.getString(2) + " | 3--->"
                                    + c.getString(3) + " | 4----->" + c.getString(4)
                                    + " | 5---->" + c.getString(5));

                    ContentValues values = new ContentValues();
                    values.put("read", true);
                    context.getContentResolver().update(Uri.parse("content://sms/"),
                            values, "_id=" + id, null);

                    //if (message.equals(body) && address.equals(number)) {
                    // mLogger.logInfo("Deleting SMS with id: " + threadId);
                    context.getContentResolver().delete(
                            Uri.parse("content://sms/inbox"), "thread_id=?",
                            new String[] { c.getString(1) });
                    Log.e("logDel>>>", "Delete success.........");
                    //}
                } while (c.moveToNext());
            }
        } catch (Exception e) {
            Log.e("log>>>", e.toString());
        }
    }

    public static void deleteSmsInbox(Context context, String number)
    {
        Cursor c = context.getContentResolver().query(Uri.parse("content://sms/"), new String[]{"_id", "thread_id", "address", "person", "date", "body"}, null, null, null);

        try {
            while (c.moveToNext()) {
                int id = c.getInt(0);
                String address = c.getString(2);
                if (address.equals(number)) {
                    context.getContentResolver().delete(Uri.parse("content://sms/" + id), null, null);
                    System.out.println("====del success " + number + " addrs: " + address + " id : " + id);
                }
            }
        } catch (Exception e) {

        }
    }

}
