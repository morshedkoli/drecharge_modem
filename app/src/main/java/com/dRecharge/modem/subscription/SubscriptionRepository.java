package com.dRecharge.modem.subscription;

import androidx.annotation.NonNull;

import com.dRecharge.modem.licenseapimodel.ApiError;
import com.dRecharge.modem.licenseapimodel.DomainSubscriptionStatus;
import com.dRecharge.modem.licenseapimodel.SingleDomainResponse;
import com.google.gson.Gson;
import com.dRecharge.modem.retrofitapi.LicenseApiClient;
import com.dRecharge.modem.retrofitapi.LicenseApiInterface;

import java.io.IOException;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class SubscriptionRepository {
    private final Gson gson = new Gson();

    public interface SubscriptionCallback {
        void onSuccess(SingleDomainResponse response);

        void onFailure(Throwable throwable);
    }

    private final LicenseApiInterface api;

    public SubscriptionRepository() {
        api = LicenseApiClient.getInstance().getApi();
    }

    public void checkDomain(String domain, SubscriptionCallback callback) {
        api.checkDomain(domain).enqueue(new Callback<SingleDomainResponse>() {
            @Override
            public void onResponse(@NonNull Call<SingleDomainResponse> call,
                                   @NonNull Response<SingleDomainResponse> response) {
                if (response.isSuccessful() && response.body() != null) {
                    SingleDomainResponse body = response.body();
                    DomainSubscriptionStatus data = body.resolveData();
                    if (data != null) {
                        ApiError apiError = body.getError();
                        if (apiError != null && (data.getMessage() == null || data.getMessage().trim().isEmpty())) {
                            data.setMessage(apiError.getMessage());
                        }
                        callback.onSuccess(body);
                        return;
                    }

                    callback.onFailure(new IllegalStateException(extractApiMessage(body.getError(),
                            body.isSuccess()
                                    ? "Subscription server returned an empty response"
                                    : "Subscription request was not successful")));
                    return;
                }

                SingleDomainResponse errorBody = parseErrorBody(response);
                if (errorBody != null) {
                    DomainSubscriptionStatus fallbackData = errorBody.resolveData();
                    if (fallbackData != null) {
                        ApiError apiError = errorBody.getError();
                        if (apiError != null
                                && (fallbackData.getMessage() == null || fallbackData.getMessage().trim().isEmpty())) {
                            fallbackData.setMessage(apiError.getMessage());
                        }
                        callback.onSuccess(errorBody);
                        return;
                    }

                    callback.onFailure(new IllegalStateException(
                            extractApiMessage(errorBody.getError(),
                                    "Subscription request failed with HTTP " + response.code())));
                    return;
                }

                callback.onFailure(new IllegalStateException(
                        "Subscription request failed with HTTP " + response.code()));
            }

            @Override
            public void onFailure(@NonNull Call<SingleDomainResponse> call, @NonNull Throwable t) {
                callback.onFailure(t);
            }
        });
    }

    private String extractApiMessage(ApiError error, String fallback) {
        if (error == null) {
            return fallback;
        }

        String message = error.getMessage();
        if (message != null && !message.trim().isEmpty()) {
            return message.trim();
        }

        String code = error.getCode();
        if (code != null && !code.trim().isEmpty()) {
            return code.trim();
        }

        return fallback;
    }

    private SingleDomainResponse parseErrorBody(Response<SingleDomainResponse> response) {
        if (response.errorBody() == null) {
            return null;
        }

        try {
            String raw = response.errorBody().string();
            if (raw == null || raw.trim().isEmpty()) {
                return null;
            }
            return gson.fromJson(raw, SingleDomainResponse.class);
        } catch (IOException ignored) {
            return null;
        } catch (Exception ignored) {
            return null;
        }
    }
}
