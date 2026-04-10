package com.dRecharge.modem.receiver;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkInfo;

import com.dRecharge.modem.helper.Session;
import com.dRecharge.modem.licenseapimodel.DomainSubscriptionStatus;
import com.dRecharge.modem.server.ServerConfig;
import com.dRecharge.modem.subscription.SubscriptionCheckScheduler;
import com.dRecharge.modem.subscription.SubscriptionCheckSupport;
import com.dRecharge.modem.subscription.SubscriptionLogoStore;
import com.dRecharge.modem.subscription.SubscriptionRepository;

public class SubscriptionCheckReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        SubscriptionCheckScheduler.scheduleNextDailyCheck(context);

        Session session = new Session(context);
        String host = ServerConfig.normalizeSubscriptionDomain(session.getData(Session.API_DOMAIN_LINK));
        if (host.isEmpty() || SubscriptionCheckSupport.isSubscriptionCheckCurrent(session, host)
                || !isNetworkAvailable(context)) {
            return;
        }

        PendingResult pendingResult = goAsync();
        new SubscriptionRepository().checkDomain(host, new SubscriptionRepository.SubscriptionCallback() {
            @Override
            public void onSuccess(com.dRecharge.modem.licenseapimodel.SingleDomainResponse response) {
                try {
                    DomainSubscriptionStatus resolved = response == null ? null : response.resolveData();
                    if (!SubscriptionCheckSupport.storeSubscriptionState(session, host, response)) {
                        pendingResult.finish();
                        return;
                    }
                    SubscriptionLogoStore.syncLogoAsync(
                            context,
                            resolved == null ? "" : resolved.getDomainLogo(),
                            bitmap -> pendingResult.finish());
                    return;
                } catch (Exception exception) {
                    pendingResult.finish();
                }
            }

            @Override
            public void onFailure(Throwable throwable) {
                pendingResult.finish();
            }
        });
    }

    private boolean isNetworkAvailable(Context context) {
        ConnectivityManager connectivityManager =
                (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        if (connectivityManager == null) {
            return false;
        }

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
            Network network = connectivityManager.getActiveNetwork();
            if (network == null) {
                return false;
            }
            NetworkCapabilities capabilities = connectivityManager.getNetworkCapabilities(network);
            return capabilities != null && capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET);
        }

        NetworkInfo networkInfo = connectivityManager.getActiveNetworkInfo();
        return networkInfo != null && networkInfo.isConnected();
    }
}
