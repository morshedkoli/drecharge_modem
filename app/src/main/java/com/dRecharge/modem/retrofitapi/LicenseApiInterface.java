package com.dRecharge.modem.retrofitapi;

import com.dRecharge.modem.licenseapimodel.BatchDomainResponse;
import com.dRecharge.modem.licenseapimodel.CheckDomainsRequest;
import com.dRecharge.modem.licenseapimodel.SingleDomainResponse;

import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.GET;
import retrofit2.http.POST;
import retrofit2.http.Query;

public interface LicenseApiInterface {
    @GET("api/v1/check-domain")
    Call<SingleDomainResponse> checkDomain(@Query("domain") String domain);

    @POST("api/v1/check-domain")
    Call<BatchDomainResponse> checkDomains(@Body CheckDomainsRequest body);
}
