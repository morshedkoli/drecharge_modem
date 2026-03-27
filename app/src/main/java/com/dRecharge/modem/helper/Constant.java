package com.dRecharge.modem.helper;


import android.os.Build;
import android.os.Bundle;
import android.util.Base64;

import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

public class Constant {

    public static String API_SAVED_DOMAIN_LINK = "";
    public static String savedSim1Pin = "";
    public static String savedSim1Time = "";
    public static String savedSim1Bal = "";
    public static String savedSim1ServiceCode = "";
    public static int savedSim1Service = -1;
    public static String savedSim1ServiceName = "";


    public static String savedSim2Pin = "";
    public static String savedSim2Time = "";
    public static String savedSim2Bal = "";
    public static String savedSim2ServiceCode = "";
    public static int savedSim2Service = -1;
    public static String savedSim2ServiceName = "";
    public static String getSim2ResponseKey = null;

    public static String sim1 = "";
    public static int sim1Id = -1;
    public static int sim1Slot = -1;
    public static String sim1Num = "";

    public static String sim2 = "";
    public static int sim2Id = -1;
    public static int sim2Slot = -1;
    public static String sim2Num = "";

    public static String getSim1Bal = "";
    public static String getSim2Bal = "";

    public static String getUssdResponse = "";
    public static String D_N = "8qZ2BSujg7ifS2ZiVz2xS03rNVf0ak8B";
    private static final String ALGORITHM = "Blowfish";
    private static final String MODE = "Blowfish/CBC/PKCS5Padding";
    private static final String IV = "abcdefgh";
    private static final String KEY= "USSD_TUNNEL";

    public static String getUSSDDialKey(String message, String SearchKey) {
        String getKey = null;
        String[] lines = new String[0];
        String[] sk = new String[0];
        String newLine = "";
        String digits = "";
        lines = message.split(System.lineSeparator());
        if (lines != null) {
            for (int i = 0; i < lines.length; i++) {
                if (lines[i].contains("|")) {
                    newLine = lines[i].substring(0, lines[i].indexOf("|"));
                } else {
                    newLine = lines[i];
                }
                digits = newLine.replaceAll("[^a-zA-Z0-9]", " ");
                System.out.println("====lines " + lines[i]);
                System.out.println("====digits " + digits);

                sk = digits.split(" ");
                for (int ii = 0; ii < sk.length; ii++) {
                    System.out.println("===AMOUNT_KEY " + sk[ii]);
                    if (sk[ii].equals(SearchKey + "tk") || sk[ii].equals(SearchKey + " tk") || sk[ii].equals(SearchKey + "TK") || sk[ii].equals(SearchKey + " TK") || sk[ii].equals(SearchKey + "Tk") || sk[ii].equals(SearchKey + " Tk") || sk[ii].equals("TK" + SearchKey) || sk[ii].equals("TK " + SearchKey)) {
                        getKey = sk[0].replaceAll("[^0-9]", "").equals("0") ? null : sk[0].replaceAll("[^0-9]", "");
                        System.out.println("===AMOUNT " + sk[ii]);
                    }
                }
            }
        }
        System.out.println("====GETKEY " + getKey);
        return getKey;
    }

    public static String ussdCodeFindFromArray(String message, String[] SearchKey) {
        String getKey = "";
        String[] lines = new String[0];
        String[] lines2 = new String[0];
        lines = message.split(System.lineSeparator());
        if (lines != null) {
            for (int i = 0; i < lines.length; i++) {
                for (int k = 0; k < SearchKey.length; k++) {
                    if (lines[i].contains(SearchKey[k])) {
                        lines2 = lines[i].split(" ");
                        for (int ii = 0; ii < lines2.length; ii++) {
                            getKey = lines2[0].replaceAll("[^0-9]", "");
                        }
                    }
                }
            }
        }
        System.out.println("====GETKEYs==: " + getKey);
        return getKey;
    }

    public static String GetDialNumber(String message, String SearchKey) {
        String getKey = "";
        String[] lines = new String[0];
        String[] lines2 = new String[0];
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            lines = message.split(System.lineSeparator());
        }
        if (lines != null) {
            for (int i = 0; i < lines.length; i++) {
                if (lines[i].contains(SearchKey)) {
                    lines2 = lines[i].split(" ");
                    for (int ii = 0; ii < lines2.length; ii++) {
                        getKey = lines2[0].replaceAll("[^0-9]", "");
                    }
                }
            }
        }
        System.out.println("====GETKEYd " + getKey);
        return getKey;
    }

    public static String GetStringInBetween(String strBegin, String strEnd, String strSource, boolean includeBegin, boolean includeEnd) {
        String[] array = new String[]
                {
                        "",
                        ""
                };
        int num = strSource.indexOf(strBegin);
        if (num != -1) {
            if (includeBegin) {
                num -= strBegin.length();
            }
            strSource = strSource.substring(num + strBegin.length());
            int num2 = strSource.indexOf(strEnd);
            if (num2 != -1) {
                if (includeEnd) {
                    num2 += strEnd.length();
                }
                array[0] = strSource.substring(0, num2);
                if (num2 + strEnd.length() < strSource.length()) {
                    array[1] = strSource.substring(num2 + strEnd.length());
                }
            }
        } else {
            array[1] = strSource;
        }

        String getVal = array[0].trim();

        return getVal;
    }

    public static String getSimBalance(String message) {
        String getBal = "";
        String[] msg = message.split(" ");
        // robi & airtel string
        if (getBal.equals("")) {
            getBal = GetStringInBetween("EasyLoad:", ".", message, false, false);
        }
        if (getBal.equals("")) {
            getBal = GetStringInBetween("new balance is ", " TAKA. ", message, false, false);
        }
        if (getBal.equals("")) {
            getBal = GetStringInBetween("new balance is", "TAKA.and", message, false, false);
        }
        if (getBal.equals("")) {
            getBal = GetStringInBetween("EasyLoad:", "TAKA.", message, false, false);
        }
        if (getBal.equals("")) {
            getBal = getNextWord(message,"EasyLoad:");
        }
        // gp message string
        if (getBal.equals("")) {
            getBal = GetStringInBetween("balance is TK", ".", message, false, false);
        }
        if (getBal.equals("")) {
            getBal = GetStringInBetween("balance is TK ", " ", message, false, false);
        }
        // banglalink message string
        if (getBal.equals("")) {
            getBal = GetStringInBetween("new balance is", "and", message, false, false);
        }
        if (getBal.equals("")) {
            getBal = GetStringInBetween("balance is TK ", " ", message, false, false);
        }
        // Taletalk
        if (getBal.equals("")) {
            getBal = GetStringInBetween("new balance is ", "Taka.", message, false, false);
        }
        // bkash personal
        if (getBal.equals("")) {
            getBal = GetStringInBetween("balance is Tk ", ". Your available", message, false, false);
        }
        // Roket personal
        if (getBal.equals("")) {
            getBal = GetStringInBetween("Available balance: Tk ", ". Do not", message, false, false);
        }
        // Nagad personal

        if (getBal.equals("")) {
            getBal = GetStringInBetween("Balance:  Tk", "", message, false, false);
        }
        if (getBal.equals("")) {
            getBal = GetStringInBetween("Bal: Tk", " ", message, false, false);
        }
//        if (getBal.equals("")){
//            Pattern p = Pattern.compile("(\\d+(?:\\.\\d+))");
//            Matcher m = p.matcher(message);
//            while(m.find()) {
//                double d = Double.parseDouble(m.group(1));
//                System.out.println("====DDS: "+d);
//                return String.valueOf(d);
//            }
//        }

        return getBal;
    }

    public static String getNextWord(String str, String word) {
        String[] strArr = str.split(word);
        if(strArr.length > 1) {
            strArr = strArr[1].trim().split(" ");
            return strArr[0];
        }
        return "";
    }

    public static String getDeviceName() {
        String manufacturer = Build.MANUFACTURER;
        String model = Build.MODEL;
        if (model.toLowerCase().startsWith(manufacturer.toLowerCase())) {
            return capitalize(model);
        } else {
            return capitalize(manufacturer) + " " + model;
        }
    }


    private static String capitalize(String s) {
        if (s == null || s.length() == 0) {
            return "";
        }
        char first = s.charAt(0);
        if (Character.isUpperCase(first)) {
            return s;
        } else {
            return Character.toUpperCase(first) + s.substring(1);
        }
    }



    public static  String enc(String value ) throws NoSuchPaddingException, NoSuchAlgorithmException, InvalidAlgorithmParameterException, InvalidKeyException, BadPaddingException, IllegalBlockSizeException {
        SecretKeySpec secretKeySpec = new SecretKeySpec(KEY.getBytes(), ALGORITHM);
        Cipher cipher = Cipher.getInstance(MODE);
        cipher.init(Cipher.ENCRYPT_MODE, secretKeySpec, new IvParameterSpec(IV.getBytes()));
        byte[] values = cipher.doFinal(value.getBytes());
        return Base64.encodeToString(values, Base64.DEFAULT);
    }

    public static  String decrypt(String value) throws NoSuchPaddingException, NoSuchAlgorithmException, InvalidAlgorithmParameterException, InvalidKeyException, BadPaddingException, IllegalBlockSizeException {
        byte[] values = Base64.decode(value, Base64.DEFAULT);
        SecretKeySpec secretKeySpec = new SecretKeySpec(KEY.getBytes(), ALGORITHM);
        Cipher cipher = Cipher.getInstance(MODE);
        cipher.init(Cipher.DECRYPT_MODE, secretKeySpec, new IvParameterSpec(IV.getBytes()));
        return new String(cipher.doFinal(values));
    }

    public static String bundle2string(Bundle bundle) {
        if (bundle == null) {
            return null;
        }
        String string = "Bundle{";
        for (String key : bundle.keySet()) {
            string += " " + key + " => " + bundle.get(key) + ";";
        }
        string += " }Bundle";
        return string;
    }

}
