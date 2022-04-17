package ru.volganap.nikolay.kids_monitor_ably;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.provider.Telephony;
import android.telephony.SmsMessage;
import android.util.Log;
import java.util.Arrays;

public class SmsBroadcastReceiver extends BroadcastReceiver implements KM_Constants{
    private SharedPreferences sharedPrefs;

    @Override
    public void onReceive(Context context, Intent intent) {
        Log.d(LOG_TAG, "SmsBroadcastReceiver - onReceive works");
        // Check block of Intent: SMS received
        sharedPrefs = context.getSharedPreferences(PREF_ACTIVITY, context.MODE_PRIVATE);
        if (intent.getAction().equals(Telephony.Sms.Intents.SMS_RECEIVED_ACTION)) {
            Boolean user_mode = sharedPrefs.getBoolean(PREF_USER, false );     //getting a value of User Mode
            String[] allowedPhones = context.getResources().getStringArray(R.array.user_phone); //getting allowed parent's phones
            String allowedParentSms = COMMAND_BASE;    //getting allowed parent's SMS
            String sender, message;
            SmsMessage[] msgs = Telephony.Sms.Intents.getMessagesFromIntent(intent);
            SmsMessage smsMessage = msgs[0];
            sender = smsMessage.getDisplayOriginatingAddress();
            message = smsMessage.getMessageBody();
            if (user_mode){                                 //User is a Parent
                Log.d(LOG_TAG, "SmsBroadcastReceiver - User is a PARENT and get some message from a Kid");
                String kidPhone = sharedPrefs.getString(KID_PHONE, "" );
                if (kidPhone.contains(sender)) {
                    showKidsDataToParent(context, sender, message);    // show the data about KID
                    Log.d(LOG_TAG, "SmsBroadcastReceiver - Sender: " + sender + ",  Message: " + message);
                } else {   //SMS was NOT from a Parent
                    Log.d(LOG_TAG, "SMS was NOT from a KID or content was NOT appropriated");
                }
            } else {                                        //User is a Kid
                Log.d(LOG_TAG, "SmsBroadcastReceiver - User is a KID and get some message from a Parent");
                if (Arrays.asList(allowedPhones).contains(sender) && message.contains(allowedParentSms)) {
                    Log.d(LOG_TAG, "SmsBroadcastReceiver - Sender :" + sender + ",  Message: " + message);
                    callBackToKidService(context, sender, message);    // make an answer SMS to a parent
                } else {   //SMS was NOT from a Parent
                    Log.d(LOG_TAG, "SmsBroadcastReceiver - SMS was NOT from a PARENT or content was NOT allowed");
                }
            }
        }

        if (intent.getAction().equals(ACTION_FROM_TIMER)) {
            long interval = intent.getLongExtra(TIMER_DELAY, 0);
            Log.d(LOG_TAG, "SmsBroadcastReceiver - Timer Fires, Interval = " + interval );
            if (!sharedPrefs.getString(TIMER_DELAY, "0").equals("0")) {
                new TimerReceiver().setTimerAction(context, interval);
                callBackToKidService(context, TIMER_SENDER, "");
            }
        }
    }

    protected void callBackToKidService(Context context, String sender, String message) {
        Intent callback_Main = new Intent();
        callback_Main.setAction(ACTION_FROM_BR);
        callback_Main.putExtra(SENDER, sender);
        callback_Main.putExtra(MESSAGE, message);
        context.sendBroadcast(callback_Main);
    }

    protected void showKidsDataToParent(Context context, String sender,String message) {
        Log.d(LOG_TAG, "SmsBroadcastReceiver - Kid's message is: " + message);
        Intent intent = new Intent();
        intent.setAction(ACTION_FROM_OKHTTP);
        intent.putExtra(SENDER, sender);
        intent.putExtra(MESSAGE, message);
        context.sendBroadcast(intent);
    }
}
