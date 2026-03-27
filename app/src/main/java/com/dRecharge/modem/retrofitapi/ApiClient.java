package com.dRecharge.modem.retrofitapi;

import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

import com.dRecharge.modem.server.ServerConfig;

public class ApiClient {
    private static ApiClient mApiClient;
    private static String cachedBaseUrl;

    private final Retrofit mRetrofit;

    private ApiClient(String baseUrl) {
        mRetrofit = new Retrofit.Builder()
                .baseUrl(baseUrl)
                .addConverterFactory(GsonConverterFactory.create()).build();
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
