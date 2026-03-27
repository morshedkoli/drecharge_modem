package com.dRecharge.modem.server;

import androidx.annotation.NonNull;

import com.dRecharge.modem.apimodel.GetPendingModel;
import com.dRecharge.modem.apimodel.InsertMessageModel;
import com.dRecharge.modem.helper.Session;
import com.dRecharge.modem.retrofitapi.ApiClient;
import com.dRecharge.modem.retrofitapi.RetrofitInterface;
import com.dRecharge.modem.service.ServiceRequest;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class ModemServerRepository {
    public interface PendingRequestsCallback {
        void onPendingRequest(ServiceRequest request);

        void onServiceStopped();

        void onFailure(Throwable throwable);

        void onComplete();
    }

    public interface MessageInsertCallback {
        void onSuccess(InsertMessageModel response);

        void onFailure(Throwable throwable);
    }

    private final RetrofitInterface api;

    public ModemServerRepository(RetrofitInterface api) {
        this.api = api;
    }

    public static ModemServerRepository fromSession(Session session) {
        return new ModemServerRepository(ApiClient.create(session.getData(Session.API_DOMAIN_LINK)).getRestApi());
    }

    public static ModemServerRepository fromDomain(String domain) {
        return new ModemServerRepository(ApiClient.create(domain).getRestApi());
    }

    public void fetchPendingRequests(String serviceName, String companyCodes, String simNumber, String simSlotId,
                                     String simBalance, PendingRequestsCallback callback) {
        List<String> companies = splitCompanyCodes(companyCodes);
        if (companies.isEmpty()) {
            callback.onComplete();
            return;
        }

        AtomicInteger remaining = new AtomicInteger(companies.size());
        for (String company : companies) {
            api.getPendingApi(serviceName, company, simNumber, simSlotId, simBalance)
                    .enqueue(new Callback<GetPendingModel>() {
                        @Override
                        public void onResponse(@NonNull Call<GetPendingModel> call, @NonNull Response<GetPendingModel> response) {
                            try {
                                if (response.isSuccessful() && response.body() != null) {
                                    ServiceRequest request = ServiceRequest.from(response.body());
                                    if (request.hasPendingRequest()) {
                                        callback.onPendingRequest(request);
                                    } else if (request.shouldStopService()) {
                                        callback.onServiceStopped();
                                    }
                                }
                            } finally {
                                finishOne(remaining, callback);
                            }
                        }

                        @Override
                        public void onFailure(@NonNull Call<GetPendingModel> call, @NonNull Throwable t) {
                            try {
                                callback.onFailure(t);
                            } finally {
                                finishOne(remaining, callback);
                            }
                        }
                    });
        }
    }

    public void insertMessage(String message, String op, String status, String pcode, String phone,
                              String simNumber, String port, String serviceId,
                              MessageInsertCallback callback) {
        api.op_message_insert(message, op, status, pcode, phone, simNumber, port, serviceId)
                .enqueue(new Callback<InsertMessageModel>() {
                    @Override
                    public void onResponse(@NonNull Call<InsertMessageModel> call, @NonNull Response<InsertMessageModel> response) {
                        if (response.isSuccessful() && response.body() != null) {
                            callback.onSuccess(response.body());
                            return;
                        }
                        callback.onFailure(new IllegalStateException("Server returned an empty response"));
                    }

                    @Override
                    public void onFailure(@NonNull Call<InsertMessageModel> call, @NonNull Throwable t) {
                        callback.onFailure(t);
                    }
                });
    }

    private void finishOne(AtomicInteger remaining, PendingRequestsCallback callback) {
        if (remaining.decrementAndGet() == 0) {
            callback.onComplete();
        }
    }

    private List<String> splitCompanyCodes(String companyCodes) {
        List<String> companies = new ArrayList<>();
        if (companyCodes == null || companyCodes.trim().isEmpty()) {
            return companies;
        }

        String[] values = companyCodes.split(",");
        for (String value : values) {
            String company = value == null ? "" : value.trim();
            if (!company.isEmpty()) {
                companies.add(company);
            }
        }

        return companies;
    }
}
