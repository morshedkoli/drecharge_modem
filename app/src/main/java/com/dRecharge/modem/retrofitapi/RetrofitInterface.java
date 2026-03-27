package com.dRecharge.modem.retrofitapi;

import com.dRecharge.modem.apimodel.GetPendingModel;
import com.dRecharge.modem.apimodel.InsertMessageModel;

import retrofit2.Call;
import retrofit2.http.POST;
import retrofit2.http.Query;

public interface RetrofitInterface {

    @POST("dRechargeApi_Modem/get_new_request_pc")
    Call<GetPendingModel> getPendingApi(
            @Query("serviceName") String serviceName,
            @Query("pc_code") String company,
            @Query("simno") String simno,
            @Query("port") String port,
            @Query("simbal") String simbal
    );

    @POST("dRechargeApi_Modem/message_from_android")
    Call<InsertMessageModel> op_message_insert(
            @Query("msg") String msg,
            @Query("op") String op,
            @Query("st") String st,
            @Query("pcode") String pcode,
            @Query("phone") String phone,
            @Query("sim") String sim,
            @Query("port") String port,
            @Query("serviceid") String serviceid
    );

}
