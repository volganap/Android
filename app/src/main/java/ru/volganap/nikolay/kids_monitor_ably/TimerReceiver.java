package ru.volganap.nikolay.kids_monitor_ably;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.SystemClock;
import android.util.Log;

import java.util.Calendar;

import static android.content.Context.ALARM_SERVICE;

public class TimerReceiver implements KM_Constants {

    public void setTimerAction(Context context, long interval) {
        Log.d(LOG_TAG, "TimerReceiver is set up, interval = " + interval );
        if (interval == 0) {
            cancelTimerAction(context);
            return;
        }
        //int hour = calendar.get(Calendar.HOUR);
        Calendar calendar = Calendar.getInstance();
        calendar.setTimeInMillis(System.currentTimeMillis());
        calendar.set(Calendar.HOUR_OF_DAY, 8);
        calendar.set(Calendar.MINUTE, 30);

        AlarmManager alarmMgr = (AlarmManager)context.getSystemService(ALARM_SERVICE);
        Intent intent = new Intent(context, SmsBroadcastReceiver.class);
        intent.putExtra(TIMER_DELAY, interval);
        intent.setAction(ACTION_FROM_TIMER);
        PendingIntent alarmIntent = PendingIntent.getBroadcast(context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT);

        if (Build.VERSION.SDK_INT >= 23) {// Wakes up the device in Doze Mode
            alarmMgr.setExactAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP,SystemClock.elapsedRealtime() +
                    interval * 1000 * 60, alarmIntent);
        } else {// Wakes up the device in Idle Mode
            alarmMgr.setExact(AlarmManager.ELAPSED_REALTIME_WAKEUP,SystemClock.elapsedRealtime() +
                    interval * 1000 * 60, alarmIntent);
        }
    }

    public static void cancelTimerAction(Context context) {
        AlarmManager alarmMgr = (AlarmManager)context.getSystemService(ALARM_SERVICE);
        Intent intent = new Intent(context, SmsBroadcastReceiver.class);
        PendingIntent alarmIntent = PendingIntent.getBroadcast(context, 0, intent, PendingIntent.FLAG_CANCEL_CURRENT);
        if (alarmMgr != null) {
            alarmMgr.cancel(alarmIntent);
            Log.d(LOG_TAG, "TimerReceiver is canceled");
        }
    }
}

