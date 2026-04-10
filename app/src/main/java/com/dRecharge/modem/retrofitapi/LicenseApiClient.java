package com.dRecharge.modem.retrofitapi;

import java.util.concurrent.TimeUnit;

import okhttp3.OkHttpClient;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

public class LicenseApiClient {
    private static final String LICENSE_API_URL = "https://drecharge.com/subscription/";
    private static final long CONNECT_TIMEOUT_SECONDS = 15L;
    private static final long READ_TIMEOUT_SECONDS = 30L;
    private static final long WRITE_TIMEOUT_SECONDS = 30L;

    private static LicenseApiClient mLicenseApiClient;

    private final Retrofit mRetrofit;

    private LicenseApiClient(){
        OkHttpClient okHttpClient = new OkHttpClient.Builder()
                .connectTimeout(CONNECT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .readTimeout(READ_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .writeTimeout(WRITE_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .build();

        mRetrofit = new Retrofit.Builder()
                .baseUrl(LICENSE_API_URL)
                .client(okHttpClient)
                .addConverterFactory(GsonConverterFactory.create())
                .build();
    }

    public static synchronized LicenseApiClient getInstance(){
        if (mLicenseApiClient==null){
            mLicenseApiClient = new LicenseApiClient();
        }
        return mLicenseApiClient;
    }

    public LicenseApiInterface getApi(){
        return mRetrofit.create(LicenseApiInterface.class);
    }

}
