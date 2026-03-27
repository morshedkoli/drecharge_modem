package com.dRecharge.modem.retrofitapi;

import com.dRecharge.modem.licenseapimodel.CheckDomainLicense;

import retrofit2.Call;
import retrofit2.http.POST;
import retrofit2.http.Query;

public interface LicenseApiInterface {

    @POST("license_api")
    Call<CheckDomainLicense> domainLicense(@Query("domain") String domain,@Query("type") String type);
}
