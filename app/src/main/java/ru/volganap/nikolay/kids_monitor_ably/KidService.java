package ru.volganap.nikolay.kids_monitor_ably;

import android.annotation.SuppressLint;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.location.LocationManager;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.telephony.SmsManager;
import android.util.Log;
import android.app.Service;
import android.os.IBinder;

import androidx.core.app.NotificationCompat;

import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken;
import org.eclipse.paho.client.mqttv3.MqttCallbackExtended;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;

import static java.lang.Long.valueOf;

public class KidService extends Service implements KM_Constants {
    private SharedPreferences sharedPrefs;
    static SharedPreferences.Editor ed;
    BroadcastReceiver mainBroadcastReceiver;
    private NotificationManager notificationManager;

    private static final int NOTIFY_ID = 101;
    private static final String NOTIFY_CHANNEL_ID = "CHANNEL_ID";
    private String timer_delay;
    int attempt = 0;
    private MqttHelper mqttHelper;

    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(LOG_TAG, "Service: onCreate");
        sharedPrefs = getSharedPreferences(PREF_ACTIVITY, MODE_PRIVATE);
        ed = sharedPrefs.edit();
        notificationManager = (NotificationManager) this.getSystemService(this.NOTIFICATION_SERVICE);

        //Init MQTT;
        String id_mqtt = sharedPrefs.getString(KID_PHONE, "" ) + System.currentTimeMillis();;
        Log.d(LOG_TAG,"Service: MQTT Id: " + id_mqtt);
        mqttHelper = new MqttHelper(getApplicationContext(), KID_PHONE, id_mqtt);
        mqttHelper.mqttAndroidClient.setCallback(new MqttCallbackExtended() {
            @Override
            public void connectComplete(boolean b, String s) {
                Log.d(LOG_TAG,"Service: MQTT * "+"Connected: " + s);
            }

            @Override
            public void connectionLost(Throwable throwable) {
                Log.d(LOG_TAG,"Service: MQTT * "+"Connection lost");
            }

            @Override
            public void messageArrived(String topic, MqttMessage mqttMessage) throws Exception {
                Log.d(LOG_TAG,"Service: MQTT: messageArrived * "+ mqttMessage.toString());
                getCommand(mqttMessage.toString());
                //dataReceived.setText(mqttMessage.toString());
            }

            @Override
            public void deliveryComplete(IMqttDeliveryToken iMqttDeliveryToken) {

            }
        });
    }

    @SuppressLint("MissingPermission")
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.d(LOG_TAG, "Service: onStartCommand, starId = " + startId + ", flags = " + flags);
        //Send Foreground Notification
        sendNotification("KMService","");
        String td_shared = sharedPrefs.getString(TIMER_DELAY, "0");
        if (!td_shared.equals("0")) {           // timer is NOT set up
            new TimerReceiver().setTimerAction(this, valueOf(td_shared)); // The timer is going on to work;
        }

        //Init BroadcastReceiver
        IntentFilter filter = new IntentFilter();
        filter.addAction(ACTION_FROM_BR);
        filter.addAction(ACTION_FROM_OKHTTP);
        filter.addAction(ACTION_FROM_GEOPOS);
        mainBroadcastReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if (intent != null) {
                    String sender = intent.getStringExtra(SENDER);
                    String message = intent.getStringExtra(MESSAGE);
                    String action = intent.getAction();
                    Log.d(LOG_TAG, "Service: Get back with Sender: " + sender + ", action: " + action + ", command: " + message);
                    switch (action) {
                        case ACTION_FROM_OKHTTP:
                        case ACTION_FROM_GEOPOS:
                            if (!(sender.equals(TIMER_SENDER))) {
                                sendMessageToParent(sender, message);
                            }
                            break;
                        case ACTION_FROM_BR:
                            switch (sender) {
                                case TIMER_SENDER:
                                    getPosition(sender);
                                    break;
                                default:
                                    getCommand(message);
                                    break;
                            }
                            break;
                    }
                }
            }
        };
        registerReceiver(mainBroadcastReceiver, filter);
        return START_STICKY;
    }

    public void sendNotification(String Title,String Text) {

        Intent notificationIntent = new Intent(getApplicationContext(), MainActivity.class);
        //notificationIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK | Intent.FLAG_ACTIVITY_NEW_TASK);
        notificationIntent.setAction(Intent.ACTION_MAIN);
        PendingIntent contentIntent = PendingIntent.getActivity(getApplicationContext(), 0, notificationIntent, PendingIntent.FLAG_UPDATE_CURRENT);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(getApplicationContext(), NOTIFY_CHANNEL_ID)
                        .setOngoing(true)
                        .setSmallIcon(R.drawable.ic_stat_name)
                        .setWhen(System.currentTimeMillis())
                        .setContentIntent(contentIntent)
                        .setContentTitle(Title)
                        .setContentText(Text);

        createChannelIfNeeded(notificationManager);
        Notification notification = builder.build();
        startForeground(NOTIFY_ID, notification);
    }

    public static void createChannelIfNeeded(NotificationManager manager) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel notificationChannel = new NotificationChannel(NOTIFY_CHANNEL_ID, NOTIFY_CHANNEL_ID, NotificationManager.IMPORTANCE_DEFAULT);
            manager.createNotificationChannel(notificationChannel);
        }
    }

    // Find the location
    protected void getPosition (String sender) {
        if (isLocationEnabled()) {
            Log.d(LOG_TAG, "Service: getPosition call, Sender: " + sender);
            Handler handler = new Handler(Looper.getMainLooper());
            Runnable myRunnable = () -> {
                    Log.d(LOG_TAG, "Service: Handler: new FindGeoPos call");
                    int max_location_time = Integer.parseInt(sharedPrefs.getString(MAX_LOCATION_TIME, "0"));
                    String kid_phone = sharedPrefs.getString(KID_PHONE, "" );
                    try {
                        FindGeoPos.getInstance(getBaseContext(), sender, kid_phone, max_location_time);
                    } catch (Exception e) {
                        Log.d(LOG_TAG, "Service: Handler: FindGeoPos EXCEPTION: " + e.toString());
                    }
            };
            handler.post(myRunnable);
        } else {
            sendMessageToParent(sender, LOCATION_IS_TURNED_OFF);
            Log.d(LOG_TAG, "Service: Please turn on your location");
        }
    }

    // Get the command and the timer delay
    protected void getCommand (String command) {
        String [] split_mes = command.split(STA_SIGN);
        String td_command = split_mes[1];
        String sender = split_mes[2];
        String kid_phone = split_mes[3];

        // Check for an appropriate Parent number and command
        if (!new CheckForSM().allowedOperation(getBaseContext(), sender, split_mes[0]))  {
            return;
        }
        // Check for an appropriate Kid number
        if (!(kid_phone.equals(sharedPrefs.getString(KID_PHONE, "" )))) {
            return;
        }
        // Check for connection
        if (td_command.equals(COMMAND_CHECK_IT)) {  // get back with the Kid location
            sendMessageToParent(sender, CONFIRM_CONNECTION + STA_SIGN   +
                    " bat_lev: " + new CheckForSM().batteryLevel(getApplicationContext()));
            return;
        }
        // Find Kid's position
        if (td_command.equals(COMMAND_GET_POSITION)) {  // get back with the Kid location
            getPosition(sender);
            return;
        }
        timer_delay = sharedPrefs.getString(TIMER_DELAY, "");
        if (timer_delay.equals(td_command)) {           // no change in the command from a parent
            sendMessageToParent(sender, NO_CHANGE_STATE );
        } else {
            long interval = valueOf(td_command);
            ed.putString(TIMER_DELAY, td_command);
            ed.apply();
            new TimerReceiver().setTimerAction(this, interval); // The timer is set up or its delay has changed
            sendMessageToParent(sender, TIMER_IS_SETUP_STATE + STA_SIGN + String.valueOf(interval));
        }
    }

    private boolean isLocationEnabled() {
        LocationManager locationManager = (LocationManager)getSystemService(Context.LOCATION_SERVICE);
        return locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) ||
                locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER);
    }

    protected void sendMessageToParent (String sender, String message) {
        String kid_phone = sharedPrefs.getString(KID_PHONE, "" );
        if (attempt < 3) {
            try {
                message = message + STA_SIGN + kid_phone;
                MqttMessage mes = new MqttMessage();
                mes.setPayload(message.getBytes());
                mqttHelper.mqttAndroidClient.publish(PARENT_PHONE, mes);
                Log.d(LOG_TAG,"Service - MQTT - Message sent: " + message);
            } catch (MqttException e) {
                System.err.println("Error Publishing: " + e.getMessage());
                Log.d(LOG_TAG,"Service - MQTT - Message sending Error: " + e.getMessage());
                e.printStackTrace();
                attempt++;
                sendMessageToParent (sender, message);
            }
        } else {
            attempt = 0;
            sendSMSMessage(sender, message+ STA_SIGN + kid_phone);
        }
    }

    protected void sendSMSMessage (String phoneNo, String message) {
        try {
            SmsManager smsManager = SmsManager.getDefault();
            smsManager.sendTextMessage(phoneNo, null, message, null, null);
            Log.d(LOG_TAG, "Service: SMS sent to number: " + phoneNo + " with message: " + message);
        } catch (Exception e) {
            e.printStackTrace();
            Log.d(LOG_TAG, "Service: SMS failed. SMS Exception: " + e.toString());
        }
    }

    public void onDestroy() {
        super.onDestroy();
        Log.d(LOG_TAG, "Service: onDestroy");

        try {
            mqttHelper.mqttAndroidClient.unsubscribe(KID_PHONE);
        } catch (MqttException ex) {
            System.err.println("Exception whilst UNsubscribing");
            ex.printStackTrace();
        }

        unregisterReceiver(mainBroadcastReceiver);
        notificationManager.cancel(NOTIFY_ID);
        stopSelf();
    }

    @Override
    public IBinder onBind(Intent intent) {
        // TODO: Return the communication channel to the service.
        return null;
    }
}
