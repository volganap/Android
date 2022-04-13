package ru.volganap.nikolay.kids_monitor_ably;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import static ru.volganap.nikolay.kids_monitor_ably.KM_Constants.LOG_TAG;

public class RestartReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        Intent intentService = new Intent(context, KidService.class);
        Log.d(LOG_TAG, "RestartReceiver is working");
        context.startService(intentService);
        throw new UnsupportedOperationException("Not yet implemented");
    }
}
