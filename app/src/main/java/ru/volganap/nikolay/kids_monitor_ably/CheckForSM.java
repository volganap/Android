package ru.volganap.nikolay.kids_monitor_ably;

import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.BatteryManager;
import android.util.Log;

import java.util.Arrays;
import static ru.volganap.nikolay.kids_monitor_ably.KM_Constants.COMMAND_BASE;
import static ru.volganap.nikolay.kids_monitor_ably.KM_Constants.LOG_TAG;

public class CheckForSM {
    protected boolean allowedOperation(Context context, String sender, String message) {
        String allowedParentMsgs = COMMAND_BASE;                                             //getting allowed parent's SMS
        String[] allowedPhones = context.getResources().getStringArray(R.array.user_phone); //getting allowed parent's phones
        if (Arrays.asList(allowedPhones).contains(sender) && message.contains(allowedParentMsgs)) {
            return true;
        } else {
            Log.d(LOG_TAG, "CheckForSM - Sender :" + sender + ",  Message: " + message+ " InAppropriate!");
            return false;
        }
    }
    protected static String batteryLevel(Context context) {
        Intent intent  = context.registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
        int    level   = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, 0);
        int    scale   = intent.getIntExtra(BatteryManager.EXTRA_SCALE, 100);
        int    percent = (level*100)/scale;
        return String.valueOf(percent) + "%";
    }
}
