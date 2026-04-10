package com.dRecharge.modem.retrofitapi;

import java.util.concurrent.TimeUnit;

import okhttp3.OkHttpClient;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

import com.dRecharge.modem.server.ServerConfig;

public class ApiClient {
    private static final long CONNECT_TIMEOUT_SECONDS = 15L;
    private static final long READ_TIMEOUT_SECONDS = 30L;
    private static final long WRITE_TIMEOUT_SECONDS = 30L;

    private static ApiClient mApiClient;
    private static String cachedBaseUrl;

    private final Retrofit mRetrofit;

    private ApiClient(String baseUrl) {
        OkHttpClient okHttpClient = new OkHttpClient.Builder()
                .connectTimeout(CONNECT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .readTimeout(READ_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .writeTimeout(WRITE_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .build();

        mRetrofit = new Retrofit.Builder()
                .baseUrl(baseUrl)
                .client(okHttpClient)
                .addConverterFactory(GsonConverterFactory.create())
                .build();
    }

    public static synchronized ApiClient create(String domainOrUrl) {
        String baseUrl = ServerConfig.buildBaseUrl(domainOrUrl);
        if (mApiClient == null || cachedBaseUrl == null || !cachedBaseUrl.equals(baseUrl)) {
            cachedBaseUrl = baseUrl;
            mApiClient = new ApiClient(baseUrl);
        }
        return mApiClient;
    }

    public RetrofitInterface getRestApi() {
        return mRetrofit.create(RetrofitInterface.class);
    }
}
