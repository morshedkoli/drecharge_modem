package com.dRecharge.modem.helper;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;

public class InboxSMSDelete {

    public static boolean deleteSMS(Context mContext) {
        try {
            mContext.getContentResolver().delete(Uri.parse("content://sms/"), null, null);
            return true;
        } catch (Exception ex) {
            return false;
        }
    }

    public static void deleteSMS_(Context context, String message, String number) {
        Uri uriSms = Uri.parse("content://sms/inbox");
        Cursor c = null;
        try {
            c = context.getContentResolver().query(
                    uriSms,
                    new String[]{"_id", "thread_id", "address", "person", "date", "body"},
                    "read=0", null, null);

            if (c == null || !c.moveToFirst()) return;

            do {
                long id = c.getLong(0);
                String threadIdStr = c.getString(1);

                ContentValues values = new ContentValues();
                values.put("read", true);
                context.getContentResolver().update(
                        Uri.parse("content://sms/"), values, "_id=" + id, null);

                context.getContentResolver().delete(
                        Uri.parse("content://sms/inbox"), "thread_id=?",
                        new String[]{threadIdStr});
            } while (c.moveToNext());

        } catch (Exception ignored) {
        } finally {
            if (c != null) {
                try { c.close(); } catch (Exception ignored) {}
            }
        }
    }

    public static void deleteSmsInbox(Context context, String number) {
        if (number == null || number.isEmpty()) return;
        Cursor c = null;
        try {
            c = context.getContentResolver().query(
                    Uri.parse("content://sms/"),
                    new String[]{"_id", "thread_id", "address", "person", "date", "body"},
                    null, null, null);

            // Guard: query() can return null (e.g. no READ_SMS permission, provider not available)
            if (c == null) return;

            while (c.moveToNext()) {
                int id = c.getInt(0);
                String address = c.getString(2);
                if (number.equals(address)) {
                    context.getContentResolver().delete(
                            Uri.parse("content://sms/" + id), null, null);
                }
            }
        } catch (Exception ignored) {
        } finally {
            if (c != null) {
                try { c.close(); } catch (Exception ignored) {}
            }
        }
    }
}
