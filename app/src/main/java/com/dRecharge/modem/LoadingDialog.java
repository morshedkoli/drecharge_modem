package com.dRecharge.modem;

import android.app.Activity;
import android.app.AlertDialog;
import android.view.LayoutInflater;

class LoadingDialog {

    Activity activity;
    AlertDialog alertDialog;


    LoadingDialog(Activity myActivity){
        activity = myActivity;
    }

    void startLoadingDialog(){
        AlertDialog.Builder builder = new AlertDialog.Builder(activity);
        LayoutInflater inflater = activity.getLayoutInflater();
        builder.setView(inflater.inflate(R.layout.loading_dialog,null));
        alertDialog = builder.create();
        alertDialog.setCancelable(false);
        alertDialog.show();
    }

    void dismissLoadingDialog(){
        alertDialog.dismiss();
    }
}
