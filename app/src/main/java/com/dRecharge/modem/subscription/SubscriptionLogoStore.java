package com.dRecharge.modem.subscription;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.Log;

import androidx.annotation.Nullable;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public final class SubscriptionLogoStore {
    private static final String TAG = "SUBSCRIPTION_LOGO";
    private static final String BASE_DOMAIN_URL = "https://drecharge.com";
    private static final String BASE_URL = BASE_DOMAIN_URL + "/subscription/domain_logo/";
    private static final String CACHE_DIR = "subscription_logo";
    private static final int CONNECT_TIMEOUT_MS = 10000;
    private static final int READ_TIMEOUT_MS = 15000;

    public interface LogoCallback {
        void onComplete(@Nullable Bitmap bitmap);
    }

    private SubscriptionLogoStore() {
    }

    @Nullable
    public static Bitmap loadCachedLogo(Context context, String logoName) {
        String normalized = normalizeLogoName(logoName);
        if (normalized.isEmpty()) {
            return null;
        }

        File cacheFile = getCacheFile(context, normalized);
        if (!cacheFile.exists()) {
            return null;
        }

        return BitmapFactory.decodeFile(cacheFile.getAbsolutePath());
    }

    public static void syncLogoAsync(Context context, String logoName, @Nullable LogoCallback callback) {
        Context appContext = context.getApplicationContext();
        String normalized = normalizeLogoName(logoName);
        if (normalized.isEmpty()) {
            clearCache(appContext);
            notifyCallback(callback, null);
            return;
        }

        Bitmap cached = loadCachedLogo(appContext, normalized);
        if (cached != null) {
            pruneCache(appContext, normalized);
            notifyCallback(callback, cached);
            return;
        }

        new Thread(() -> {
            Bitmap downloaded = downloadAndCache(appContext, normalized);
            notifyCallback(callback, downloaded);
        }, "subscription-logo-sync").start();
    }

    public static String buildLogoUrl(String logoName) {
        String normalized = normalizeLogoName(logoName);
        if (normalized.isEmpty()) {
            return "";
        }
        if (normalized.startsWith("http://") || normalized.startsWith("https://")) {
            return normalized;
        }
        if (normalized.startsWith("/")) {
            return BASE_DOMAIN_URL + normalized;
        }
        return BASE_URL + normalized;
    }

    private static void notifyCallback(@Nullable LogoCallback callback, @Nullable Bitmap bitmap) {
        if (callback != null) {
            callback.onComplete(bitmap);
        }
    }

    @Nullable
    private static Bitmap downloadAndCache(Context context, String logoName) {
        HttpURLConnection connection = null;
        InputStream inputStream = null;

        try {
            URL url = new URL(buildLogoUrl(logoName));
            connection = (HttpURLConnection) url.openConnection();
            connection.setConnectTimeout(CONNECT_TIMEOUT_MS);
            connection.setReadTimeout(READ_TIMEOUT_MS);
            connection.setUseCaches(true);

            int responseCode = connection.getResponseCode();
            if (responseCode != HttpURLConnection.HTTP_OK) {
                Log.w(TAG, "Logo download failed with HTTP " + responseCode);
                return null;
            }

            inputStream = connection.getInputStream();
            byte[] data = readAllBytes(inputStream);
            if (data.length == 0) {
                return null;
            }

            Bitmap bitmap = BitmapFactory.decodeByteArray(data, 0, data.length);
            if (bitmap == null) {
                return null;
            }

            File cacheFile = getCacheFile(context, logoName);
            File parent = cacheFile.getParentFile();
            if (parent != null && !parent.exists() && !parent.mkdirs()) {
                Log.w(TAG, "Failed to create subscription logo cache directory");
                return bitmap;
            }

            try (FileOutputStream outputStream = new FileOutputStream(cacheFile, false)) {
                outputStream.write(data);
                outputStream.flush();
            }

            pruneCache(context, logoName);
            return bitmap;
        } catch (Exception exception) {
            return null;
        } finally {
            if (inputStream != null) {
                try {
                    inputStream.close();
                } catch (IOException ignored) {
                }
            }
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    private static byte[] readAllBytes(InputStream inputStream) throws IOException {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int read;
        while ((read = inputStream.read(buffer)) != -1) {
            outputStream.write(buffer, 0, read);
        }
        return outputStream.toByteArray();
    }

    private static void clearCache(Context context) {
        File cacheDir = getCacheDirectory(context);
        File[] files = cacheDir.listFiles();
        if (files == null) {
            return;
        }

        for (File file : files) {
            if (file != null && file.exists() && !file.delete()) {
                Log.w(TAG, "Failed to delete cached logo " + file.getName());
            }
        }
    }

    private static void pruneCache(Context context, String keepLogoName) {
        File keepFile = getCacheFile(context, keepLogoName);
        File cacheDir = getCacheDirectory(context);
        File[] files = cacheDir.listFiles();
        if (files == null) {
            return;
        }

        for (File file : files) {
            if (file == null || !file.exists() || file.equals(keepFile)) {
                continue;
            }
            if (!file.delete()) {
                Log.w(TAG, "Failed to delete stale cached logo " + file.getName());
            }
        }
    }

    private static File getCacheDirectory(Context context) {
        return new File(context.getFilesDir(), CACHE_DIR);
    }

    private static File getCacheFile(Context context, String logoName) {
        String extension = extractExtension(logoName);
        String fileName = "logo_" + sha1Hex(logoName) + extension;
        return new File(getCacheDirectory(context), fileName);
    }

    private static String extractExtension(String logoName) {
        String normalizedUrl = buildLogoUrl(logoName);
        int queryIndex = normalizedUrl.indexOf('?');
        String cleanPath = queryIndex >= 0 ? normalizedUrl.substring(0, queryIndex) : normalizedUrl;
        int dotIndex = cleanPath.lastIndexOf('.');
        if (dotIndex <= 0 || dotIndex == cleanPath.length() - 1) {
            return ".img";
        }
        return cleanPath.substring(dotIndex);
    }

    private static String sha1Hex(String value) {
        try {
            MessageDigest messageDigest = MessageDigest.getInstance("SHA-1");
            byte[] digest = messageDigest.digest(value.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            StringBuilder builder = new StringBuilder(digest.length * 2);
            for (byte b : digest) {
                builder.append(String.format("%02x", b));
            }
            return builder.toString();
        } catch (NoSuchAlgorithmException exception) {
            return Integer.toHexString(value.hashCode());
        }
    }

    private static String normalizeLogoName(String logoName) {
        if (logoName == null) {
            return "";
        }
        return logoName.trim();
    }
}
