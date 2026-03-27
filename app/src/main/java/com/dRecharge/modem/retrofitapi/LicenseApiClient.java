package com.dRecharge.modem.retrofitapi;

import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

public class LicenseApiClient {
    String LICENSE_API_URL = "http://bappe.info/reload_portal/";

    private static LicenseApiClient mLicenseApiClient;

    private final Retrofit mRetrofit;

    public LicenseApiClient(){
        mRetrofit = new Retrofit.Builder()
                .baseUrl(LICENSE_API_URL)
                .addConverterFactory(GsonConverterFactory.create()).build();
    }

    public LicenseApiClient getmLicenseApiClient(){
        if (mLicenseApiClient==null){
            mLicenseApiClient = new LicenseApiClient();
        }
        return mLicenseApiClient;
    }

    public LicenseApiInterface getApi(){
        return mRetrofit.create(LicenseApiInterface.class);
    }

}
