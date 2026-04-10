package com.dRecharge.modem.helper;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Arrays;

import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;

/**
 * AES-128-ECB encryption helper.
 *
 * Fixes applied:
 *  1. Removed use of the removed "Crypto" SecureRandom provider (crashed on Android 7+).
 *  2. Fixed the HEX lookup table (was 44 chars → produced corrupted hex output).
 *  3. Derive a deterministic 128-bit key from the seed via SHA-256 instead of the
 *     deprecated SHA1PRNG/Crypto path.
 */
public class AESHelper {

    private static final String HEX = "0123456789ABCDEF";

    public static String encrypt(String seed, String cleartext) throws Exception {
        byte[] rawKey = getRawKey(seed.getBytes(StandardCharsets.UTF_8));
        byte[] result = encryptBytes(rawKey, cleartext.getBytes(StandardCharsets.UTF_8));
        return toHex(result);
    }

    public static String decrypt(String seed, String encrypted) throws Exception {
        byte[] rawKey = getRawKey(seed.getBytes(StandardCharsets.UTF_8));
        byte[] enc = toByte(encrypted);
        byte[] result = decryptBytes(rawKey, enc);
        return new String(result, StandardCharsets.UTF_8);
    }

    /**
     * Derives a 128-bit AES key from the seed using SHA-256.
     * Replaces the removed SHA1PRNG/"Crypto" provider that crashed on Android 7+.
     */
    private static byte[] getRawKey(byte[] seed) throws Exception {
        MessageDigest sha = MessageDigest.getInstance("SHA-256");
        byte[] key = sha.digest(seed);
        return Arrays.copyOf(key, 16); // 128-bit AES key
    }

    private static byte[] encryptBytes(byte[] raw, byte[] clear) throws Exception {
        SecretKeySpec skeySpec = new SecretKeySpec(raw, "AES");
        Cipher cipher = Cipher.getInstance("AES");
        cipher.init(Cipher.ENCRYPT_MODE, skeySpec);
        return cipher.doFinal(clear);
    }

    private static byte[] decryptBytes(byte[] raw, byte[] encrypted) throws Exception {
        SecretKeySpec skeySpec = new SecretKeySpec(raw, "AES");
        Cipher cipher = Cipher.getInstance("AES");
        cipher.init(Cipher.DECRYPT_MODE, skeySpec);
        return cipher.doFinal(encrypted);
    }

    public static String toHex(String txt) {
        return toHex(txt.getBytes(StandardCharsets.UTF_8));
    }

    public static String fromHex(String hex) {
        return new String(toByte(hex), StandardCharsets.UTF_8);
    }

    public static byte[] toByte(String hexString) {
        int len = hexString.length() / 2;
        byte[] result = new byte[len];
        for (int i = 0; i < len; i++) {
            result[i] = (byte) Integer.parseInt(hexString.substring(2 * i, 2 * i + 2), 16);
        }
        return result;
    }

    public static String toHex(byte[] buf) {
        if (buf == null) return "";
        StringBuilder result = new StringBuilder(2 * buf.length);
        for (byte b : buf) {
            result.append(HEX.charAt((b >> 4) & 0x0f));
            result.append(HEX.charAt(b & 0x0f));
        }
        return result.toString();
    }
}
