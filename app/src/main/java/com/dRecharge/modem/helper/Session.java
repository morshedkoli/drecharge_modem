package com.dRecharge.modem.helper;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Shader;
import android.os.Environment;


import java.io.File;


public class Session {
    SharedPreferences pref;
    SharedPreferences.Editor editor;
    Context _context;
    int PRIVATE_MODE = 0;

    public static final String PREFER_NAME = "ReloadMobileModule";
    public static final String IS_DOMAIN_VALIED = "is_domain_valied";
    public static final String API_DOMAIN_LINK = "api_url";

    public static final String SIM1_INFO = "sim1Info";
    public static final String SIM2_INFO = "sim2Info";


    // SIM 1 SESSION DATA
    public static final String SIM1_ID = "sim1id";
    public static final String SIM1_NUMBER = "sim1Number";
    public static final String SIM1_PIN = "sim1Pin";
    public static final String SIM1_TIME = "sim1Time";
    public static final String SIM1_MIN_BAL = "sim1MinBal";
    public static final String SIM1_SERVICE_CODE = "sim1ServiceCode";
    public static final String SIM1_SERVICE = "sim1service";
    public static final String SIM1_SERVICE_NAME = "sim1servicename";
    public static final String SIM1_ENABLED = "sim1Enabled";

    // SIM 2 SESSION DATA
    public static final String SIM2_ID = "sim2id";
    public static final String SIM2_NUMBER = "sim2Number";
    public static final String SIM2_PIN = "sim2Pin";
    public static final String SIM2_TIME = "sim2Time";
    public static final String SIM2_MIN_BAL = "sim2MinBal";
    public static final String SIM2_SERVICE_CODE = "sim2ServiceCode";
    public static final String SIM2_SERVICE = "sim2service";
    public static final String SIM2_SERVICE_NAME = "sim2serviceame";
    public static final String SIM2_ENABLED = "sim2Enabled";

    //Common
    public static final String TIME_INTERVAL = "timeInterval";

    public Session(Context context) {
        this._context = context;
        pref = _context.getSharedPreferences(PREFER_NAME, PRIVATE_MODE);
        editor = pref.edit();
        editor.apply();
    }

    public String getData(String id) {
        return pref.getString(id, "");
    }

    public int getIntData(String id) {
        return pref.getInt(id,-1);
    }

    public boolean getBooleanData(String id) {
        return pref.getBoolean(id, false);
    }

    public void setData(String id, String val) {
        editor.putString(id, val);
        editor.commit();
        editor.apply();
    }

    public void setIntData(String id, int val) {
        editor.putInt(id, val);
        editor.commit();
        editor.apply();
    }

    public void  SetSim1Info(Boolean sim1Info, String sim1Num,String sim1Pin, String sim1MinBal, String sim1Time){
        editor.putBoolean(SIM1_INFO, sim1Info);
        editor.putString(SIM1_NUMBER, sim1Num);
        editor.putString(SIM1_PIN, sim1Pin);
        editor.putString(SIM1_MIN_BAL, sim1MinBal);
        editor.putString(SIM1_TIME, sim1Time);
        editor.commit();
        editor.apply();
    }

    public void  SetSim2Info(Boolean sim2Info,String sim2Num, String sim2Pin, String sim2MinBal, String sim2Time){
        editor.putBoolean(SIM2_INFO, sim2Info);
        editor.putString(SIM2_NUMBER, sim2Num);
        editor.putString(SIM2_PIN, sim2Pin);
        editor.putString(SIM2_MIN_BAL, sim2MinBal);
        editor.putString(SIM2_TIME, sim2Time);
        editor.commit();
        editor.apply();
    }

    public void setBooleanData(String id, Boolean val) {
        editor.putBoolean(id, val);
        editor.commit();
        editor.apply();
    }

//    public void createUserLoginSession(String id, String name, String mobile, String address, String status, String apikey) {
//        editor.putBoolean(IS_DOMAIN_VALIED, true);
//        editor.commit();
//    }


//    public boolean checkLogin() {
//        if (!this.isUserLoggedIn()) {
//            Intent i = new Intent(_context, LoginActivity.class);
//            i.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
//            i.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
//            _context.startActivity(i);
//            return true;
//        }
//        return false;
//    }

//    public void logoutUser(Activity activity) {
//
//        editor.clear();
//        editor.commit();
//
//        Intent i = new Intent(activity, LoginActivity.class);
//        i.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK);
//        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
//        activity.startActivity(i);
//        activity.finish();
//
//    }

    public boolean isDomainValid() {
        return pref.getBoolean(IS_DOMAIN_VALIED, false);
    }

    public boolean isSim1Valid() {
        return pref.getBoolean(SIM1_INFO, false);
    }

    public boolean isSim2Valid() {
        return pref.getBoolean(SIM2_INFO, false);
    }

    public Bitmap getRoundedBitmap(Bitmap bitmap) {
        Bitmap circleBitmap = Bitmap.createBitmap(bitmap.getWidth(), bitmap.getHeight(), Bitmap.Config.ARGB_8888);
        BitmapShader shader = new BitmapShader(bitmap, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP);
        Paint paint = new Paint();
        paint.setShader(shader);
        paint.setAntiAlias(true);
        Canvas c = new Canvas(circleBitmap);
        c.drawCircle(bitmap.getWidth() / 2, bitmap.getHeight() / 2, bitmap.getWidth() / 2, paint);
        return circleBitmap;
    }

    public static Bitmap getBitmapFromURL(String src) {
        File bitmapFile = new File(Environment.getExternalStorageDirectory() + "/" + src);
        Bitmap bitmap = BitmapFactory.decodeFile(String.valueOf(bitmapFile));
        return bitmap;
    }
}
